package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.CvContent;
import com.training.cvmanagementbe.repository.CvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/*
 * Keeps item ids from crossing profile boundaries.
 *
 * - An item_id is shared across the languages of one profile - that is what lets the
 * English CV know which entry of the Vietnamese master it corresponds to - and is never shared
 * across two profiles, even when one profile was copied from the other.
 * - Without this check the client decides identity: a payload carrying an id lifted from another
 * profile would make inline comments surface on the wrong CV and make the version diff match
 * unrelated entries. Both fail quietly, which is why the check belongs on the write path rather
 * than in a report.
 * - Ids the profile already owns are skipped, so a normal edit costs no queries at all and only
 * the entries just added are looked up.
 */
@Component
@RequiredArgsConstructor
public class CvItemIdGuard {

    private final CvRepository cvRepository;
    private final CvContentCodec codec;

    public void requireOwnedOrNew(UUID profileId, CvContent content) {
        Set<String> incoming = codec.itemIdsOf(content);
        if (incoming.isEmpty()) {
            return;
        }

        Set<String> owned = ownedItemIds(profileId);

        for (String itemId : incoming) {
            if (owned.contains(itemId)) {
                continue;
            }
            if (existsElseWhere(profileId, itemId)) {
                throw new ApiException.BusinessRuleException(ErrorCode.ITEM_ID_FOREIGN_TO_PROFILE);
            }
        }
    }

    // Ids already recorded anywhere in this profile - its versions and its draft.
    private Set<String> ownedItemIds(UUID profileId) {
        Set<String> owned = new HashSet<>();

        for (String snapshot : cvRepository.findContentSnapshotsByProfileId(profileId)) {
            owned.addAll(codec.itemIdsOf(codec.read(snapshot)));
        }
        return owned;
    }

    // Versions first: most ids that exist at all exist in a published version.
    private boolean existsElseWhere(UUID profileId, String itemId) {
        return cvRepository.countItemIdInVersionsOutsideProfile(profileId, itemId) > 0
                || cvRepository.countItemIdInDraftsOutsideProfile(profileId, itemId) > 0;
    }
}
