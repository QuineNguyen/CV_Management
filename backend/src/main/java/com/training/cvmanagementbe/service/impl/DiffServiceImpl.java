package com.training.cvmanagementbe.service.impl;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.training.cvmanagementbe.dto.response.cvs.*;
import com.training.cvmanagementbe.enums.cvs.ChangeType;
import com.training.cvmanagementbe.enums.cvs.CvDiffField;
import com.training.cvmanagementbe.enums.cvs.CvSectionKey;
import com.training.cvmanagementbe.enums.cvs.DiffChunkType;
import com.training.cvmanagementbe.record.cvs.ContentDiff;
import com.training.cvmanagementbe.record.cvs.CvContent;
import com.training.cvmanagementbe.record.cvs.RepeatedEntry;
import com.training.cvmanagementbe.service.DiffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/*
 * Read-time diff for the version viewer: Any v_i against any v_j. ChangeLogGenerator only ever
 * sees v_{n-1} -> v_n at publish time, so its stored entries cannot answer this.
 *
 * - Structure first: Section -> item (matched by item_id, never by position) -> field.
 * - Text second: java-diff-utils runs only when both sides hold a value for the same field.
 * - Unchanged content is returned as well, so the viewer can lay the whole CV out side by side.
 */
@Service
@RequiredArgsConstructor
public class DiffServiceImpl implements DiffService {

    /*
     * Inline tokens: One CJK (Chinese, Japan, Korean) character, a run of other letters/digits, a run of whitespace, or any
     * other single character. Whole words read better than scattered letters; Japanese has no
     * spaces to split on, so it falls back to one character per token.
     */
    private static final Pattern TOKEN = Pattern.compile(
            "[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]"
                    + "|[\\p{L}\\p{M}\\p{N}_&&[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]]+"
                    + "|\\s+"
                    + "|.",
            Pattern.DOTALL);

    // Past this size an inline view stops helping; both values are shown whole instead.
    private static final int MAX_INLINE_TOKENS = 5_000;

    private final CvContentCodec codec;

    @Override
    public ContentDiff diff(CvContent oldContent, UUID oldAvatarImageId,
                            CvContent newContent, UUID newAvatarImageId) {
        Tally tally = new Tally();
        List<SectionDiff> sections = new ArrayList<>();

        for (CvSectionKey sectionKey : CvSectionKey.values()) {
            sections.add(sectionKey.repeated()
                    ? diffRepeated(sectionKey, oldContent, newContent, tally)
                    : diffSingle(sectionKey, oldContent, newContent, oldAvatarImageId, newAvatarImageId, tally));
        }
        return new ContentDiff(sections, tally.toStats());
    }

    // ---------- SINGLE sections: At most one item, itemId = null ----------

    private SectionDiff diffSingle(CvSectionKey sectionKey, CvContent oldContent, CvContent newContent,
                                   UUID oldAvatarImageId, UUID newAvatarImageId, Tally tally) {
        List<FieldDiff> fields = new ArrayList<>();

        // Read from a column, presented as the first personal_info field.
        if (sectionKey == CvSectionKey.PERSONAL_INFO) {
            FieldDiff avatar = compareField(CvDiffField.AVATAR_IMAGE_ID.getKey(),
                    asText(oldAvatarImageId), asText(newAvatarImageId), false);
            if (avatar != null) {
                fields.add(avatar);
            }
        }

        fields.addAll(diffFields(
                codec.readableFieldsOf(oldContent == null ? null : codec.singleSectionOf(oldContent, sectionKey)),
                codec.readableFieldsOf(codec.singleSectionOf(newContent, sectionKey))
        ));

        if (fields.isEmpty()) {
            return new SectionDiff(sectionKey.key(), 0, List.of());
        }

        // Each field of a SINGLE section counts as one unit in the stats.
        fields.forEach(field -> tally.count(field.changeType()));
        return new SectionDiff(sectionKey.key(),
                changeCount(fields.stream().map(FieldDiff::changeType)),
                List.of(new ItemDiff(null, rollUpSingle(fields), fields)));
    }

    // ---------- REPEATED sections: Matched by item_id ----------

    private SectionDiff diffRepeated(CvSectionKey sectionKey, CvContent oldContent, CvContent newContent,
                                     Tally tally) {
        Map<String, RepeatedEntry> oldItems = codec.itemsById(oldContent, sectionKey);
        Map<String, RepeatedEntry> newItems = codec.itemsById(newContent, sectionKey);

        List<ItemDiff> items = new ArrayList<>();
        for (String itemId : mergedOrder(oldItems.keySet(), newItems.keySet())) {
            RepeatedEntry before = oldItems.get(itemId);
            RepeatedEntry after = newItems.get(itemId);

            // A missing side reads as an empty record, so its fields come out ADDED or REMOVED.
            List<FieldDiff> fields = diffFields(codec.readableFieldsOf(before), codec.readableFieldsOf(after));
            ChangeType changeType = before == null ? ChangeType.ADDED
                    : after == null ? ChangeType.REMOVED
                    : rollUpExisting(fields);

            items.add(new ItemDiff(itemId, changeType, fields));
            tally.count(changeType);
        }
        return new SectionDiff(sectionKey.key(), changeCount(items.stream().map(ItemDiff::changeType)), items);
    }

    /*
     * New order, with each removed item slotted in right after the item it followed in the old
     * version - both columns then stay aligned row by row.
     */
    private List<String> mergedOrder(Set<String> oldIds, Set<String> newIds) {
        List<String> order = new ArrayList<>(newIds);
        String anchor = null;
        for (String itemId : oldIds) {
            if (!newIds.contains(itemId)) {
                order.add(anchor == null ? 0 : order.indexOf(anchor) + 1, itemId);
            }
            anchor = itemId;
        }
        return order;
    }

    // ---------- Fields ----------

    private List<FieldDiff> diffFields(Map<String, String> oldFields, Map<String, String> newFields) {
        Set<String> keys = new LinkedHashSet<>(newFields.keySet());
        keys.addAll(oldFields.keySet());
        keys.removeAll(CvDiffField.IGNORED);

        List<FieldDiff> result = new ArrayList<>();
        for (String fieldKey : keys) {
            FieldDiff field = compareField(fieldKey, oldFields.get(fieldKey), newFields.get(fieldKey), true);
            if (field != null) {
                result.add(field);
            }
        }
        return result;
    }

    // Null when the field is empty on both sides: There is nothing to show.
    private FieldDiff compareField(String fieldKey, String rawBefore, String rawAfter, boolean inline) {
        String before = normalise(fieldKey, rawBefore);
        String after = normalise(fieldKey, rawAfter);

        if (before == null && after == null) {
            return null;
        }
        if (Objects.equals(before, after)) {
            return new FieldDiff(fieldKey, ChangeType.UNCHANGED, before, after, null);
        }

        ChangeType changeType = classify(before, after);
        List<DiffChunk> chunks = inline && changeType == ChangeType.MODIFIED
                ? inlineDiff(before, after)
                : null;
        return new FieldDiff(fieldKey, changeType, before, after, chunks);
    }

    // ---------- Inline text diff ----------

    private List<DiffChunk> inlineDiff(String before, String after) {
        List<String> source = tokenize(before);
        List<String> target = tokenize(after);
        if (source.size() + target.size() > MAX_INLINE_TOKENS) {
            return null;
        }

        List<DiffChunk> chunks = new ArrayList<>();
        // includeEqualParts = true: Unchanged runs come back as EQUAL deltas, in order.
        for (AbstractDelta<String> delta : DiffUtils.diff(source, target, true).getDeltas()) {
            switch (delta.getType()) {
                case EQUAL -> append(chunks, DiffChunkType.EQUAL, delta.getSource().getLines());
                case DELETE -> append(chunks, DiffChunkType.DELETE, delta.getSource().getLines());
                case INSERT -> append(chunks, DiffChunkType.INSERT, delta.getTarget().getLines());
                case CHANGE -> {
                    append(chunks, DiffChunkType.DELETE, delta.getSource().getLines());
                    append(chunks, DiffChunkType.INSERT, delta.getTarget().getLines());
                }
            }
        }
        return chunks;
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    // Adjacent tokens of one type merge into one chunk, so the client renders a handful of spans.
    private void append(List<DiffChunk> chunks, DiffChunkType type, List<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        String text = String.join("", tokens);
        int last = chunks.size() - 1;
        if (last >= 0 && chunks.get(last).type() == type) {
            chunks.set(last, new DiffChunk(type, chunks.get(last).text() + text));
        } else {
            chunks.add(new DiffChunk(type, text));
        }
    }

    // ---------- Private helpers ----------

    private ChangeType classify(String before, String after) {
        if (before == null) {
            return ChangeType.ADDED;
        }
        return after == null ? ChangeType.REMOVED : ChangeType.MODIFIED;
    }

    // A SINGLE section is ADDED or REMOVED as a whole only when every field moved the same way.
    private ChangeType rollUpSingle(List<FieldDiff> fields) {
        Set<ChangeType> types = EnumSet.noneOf(ChangeType.class);
        fields.forEach(field -> types.add(field.changeType()));
        return types.size() == 1 ? types.iterator().next() : ChangeType.MODIFIED;
    }

    // An entry present on both sides is MODIFIED as soon as one field changed.
    private ChangeType rollUpExisting(List<FieldDiff> fields) {
        return fields.stream().anyMatch(field -> field.changeType() != ChangeType.UNCHANGED)
                ? ChangeType.MODIFIED
                : ChangeType.UNCHANGED;
    }

    private int changeCount(Stream<ChangeType> types) {
        return (int) types.filter(type -> type != ChangeType.UNCHANGED).count();
    }

    // Blank and absent read the same to a person; a false flag means nothing to report.
    private String normalise(String fieldKey, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        boolean falseFlag = CvDiffField.FLAGS.contains(fieldKey) && Boolean.FALSE.toString().equals(trimmed);
        return falseFlag ? null : trimmed;
    }

    private String asText(UUID id) {
        return id == null ? null : id.toString();
    }

    // Overview counts; UNCHANGED is tallied but never reported.
    private static final class Tally {

        private final Map<ChangeType, Integer> counts = new EnumMap<>(ChangeType.class);

        void count(ChangeType type) {
            counts.merge(type, 1, Integer::sum);
        }

        DiffStats toStats() {
            return new DiffStats(
                    counts.getOrDefault(ChangeType.ADDED, 0),
                    counts.getOrDefault(ChangeType.MODIFIED, 0),
                    counts.getOrDefault(ChangeType.REMOVED, 0)
            );
        }
    }
}
