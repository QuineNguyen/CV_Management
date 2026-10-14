package com.training.cvmanagementbe.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.common.PdfFileNames;
import com.training.cvmanagementbe.dto.response.cvs.pdf.*;
import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.cvs.CvFieldKey;
import com.training.cvmanagementbe.enums.cvs.CvSectionKey;
import com.training.cvmanagementbe.enums.cvs.LifecycleStatus;
import com.training.cvmanagementbe.enums.pdf.*;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.cvs.CvContent;
import com.training.cvmanagementbe.record.cvs.PdfExportAuditDetail;
import com.training.cvmanagementbe.repository.*;
import com.training.cvmanagementbe.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PdfExportServiceImpl implements PdfExportService {

    private static final String SEPARATOR = " · ";
    private static final String LIST_SEPARATOR = ", ";
    private static final String RANGE = " - ";
    private static final int ISO_DATE_LENGTH = 10;
    // Name under which the template reads its model
    private static final String MODEL_VARIABLE = "cv";

    private final SpringTemplateEngine templateEngine = buildTemplateEngine();

    // Read once, on first use; a missing font fails the export loudly instead of printing boxes
    private final Map<PdfFont, byte[]> fontBytes = new ConcurrentHashMap<>();

    private final CvRepository cvRepository;
    private final CvVersionRepository cvVersionRepository;
    private final CvProfileRepository cvProfileRepository;
    private final UserRepository userRepository;
    private final CvSearchRepository cvSearchRepository;
    private final CvContentCodec codec;
    private final AvatarUrlResolver avatarUrlResolver;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CvPdfFile export(UUID cvId, UUID versionId) {
        // A deleted CV is not something to send to a customer
        Cv cv = cvRepository.findByIdAndLifecycleStatus(cvId, LifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ApiException.NotFoundException("cv", cvId));
        CvProfile profile = cvProfileRepository.findById(cv.getProfileId())
                .orElseThrow(() -> new ApiException.NotFoundException("cv profile", cv.getProfileId()));
        requireCanRead(profile.getEmployeeId());

        // A version of another CV reads as not found, as in diffVersions
        CvVersion version = cvVersionRepository.findByIdAndCvId(versionId, cvId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv version", versionId));

        PdfLocale locale = PdfLocale.of(cv.getLanguage());
        CvPdfContext context = toContext(
                codec.read(version.getContentJson()),
                locale,
                avatarUrlResolver.resolve(version.getAvatarImageId())
        );
        byte[] pdf = render(PdfTemplate.STANDARD_CV, context);

        // The account name, not the snapshot: It stays Latin even on a Japanese CV
        String employeeName = userRepository.findById(profile.getEmployeeId())
                .map(User::getFullName)
                .orElse(null);

        // Written last, so a failed render leaves no record of an export that never happened
        auditLogger.record(Action.EXPORT_PDF, TargetType.CV, cvId, null, new PdfExportAuditDetail(
                profile.getId(),
                cv.getLanguage(),
                version.getId(),
                version.getVersionNumber()
        ));

        return new CvPdfFile(
                PdfFileNames.of(employeeName, locale.getCode(), version.getVersionNumber()),
                pdf
        );
    }

    /*
     * Admin/HR read everything; a tech lead reads members of the teams they lead, resolved through
     * team membership rather than the profile's team; everyone reads their own.
     * Same rule as the CV search scope, so a search result always opens.
     */
    public void requireCanRead(UUID employeeId) {
        Role role = CurrentActor.requireRole();
        if (role == Role.ADMIN || role == Role.HR) {
            return;
        }
        UUID actorId = CurrentActor.requireUserId();
        if (actorId.equals(employeeId)) {
            return;
        }
        if (role == Role.TECH_LEAD && cvSearchRepository.isMemberOfLedTeam(actorId, employeeId)) {
            return;
        }
        // Reads are refused as out of scope; NOT_CV_OWNER would tell a reader they tried to edit
        throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
    }

    public CvPdfContext toContext(CvContent content, PdfLocale locale, String avatarUrl) {
        JsonNode root = objectMapper.valueToTree(content);
        PdfHeaderDto header = header(root.path(CvSectionKey.PERSONAL_INFO.key()), locale, avatarUrl);

        // Template order: sections 2-9
        List<PdfSectionDto> sections = Stream.of(
                textSection(root, locale),
                ratingSection(root, CvSectionKey.SKILLS, PdfLabel.SKILLS, locale, this::skill),
                entrySection(root, CvSectionKey.EXPERIENCE, PdfLabel.EXPERIENCE, locale, this::experience),
                entrySection(root, CvSectionKey.EDUCATION, PdfLabel.EDUCATION, locale, this::education),
                entrySection(root, CvSectionKey.CERTIFICATIONS, PdfLabel.CERTIFICATIONS, locale, this::certification),
                entrySection(root, CvSectionKey.PROJECTS, PdfLabel.PROJECTS, locale, this::project),
                ratingSection(root, CvSectionKey.LANGUAGES, PdfLabel.LANGUAGES, locale, this::language),
                entrySection(root, CvSectionKey.ADDITIONAL_INFO, PdfLabel.ADDITIONAL_INFO, locale, this::additionalInfo)
        )
                .filter(PdfSectionDto::hasContent)
                .toList();

        return new CvPdfContext(locale.getCode(), header.fullName(), header, sections);
    }

    // ---------- Section 1 ----------

    private PdfHeaderDto header(JsonNode info, PdfLocale locale, String avatarUrl) {
        List<PdfFieldDto> contacts = fields(
                field(PdfLabel.EMAIL, text(info, CvFieldKey.EMAIL), locale),
                field(PdfLabel.PHONE, text(info, CvFieldKey.PHONE), locale),
                field(PdfLabel.DATE_OF_BIRTH, date(info, CvFieldKey.DATE_OF_BIRTH, locale.fullDate()), locale),
                field(PdfLabel.ADDRESS, text(info, CvFieldKey.ADDRESS), locale)
        );
        return new PdfHeaderDto(
                text(info, CvFieldKey.FULL_NAME),
                text(info, CvFieldKey.POSITION),
                avatarUrl,
                contacts
        );
    }

    // ---------- Sections 2-9 ----------

    private PdfSectionDto textSection(JsonNode root, PdfLocale locale) {
        JsonNode objective = root.path(CvSectionKey.CAREER_OBJECTIVE.key());
        return PdfSectionDto.ofText(PdfLabel.CAREER_OBJECTIVE.text(locale), text(objective, CvFieldKey.CONTENT));
    }

    private PdfSectionDto ratingSection(JsonNode root, CvSectionKey key, PdfLabel title, PdfLocale locale,
                                        BiFunction<JsonNode, PdfLocale, PdfRatingDto> toRating) {
        List<PdfRatingDto> ratings = itemsOf(root, key)
                .map(item -> toRating.apply(item, locale))
                .filter(Objects::nonNull)
                .toList();
        return PdfSectionDto.ofRatings(title.text(locale), ratings);
    }

    private PdfSectionDto entrySection(JsonNode root, CvSectionKey key, PdfLabel title, PdfLocale locale,
                                       BiFunction<JsonNode, PdfLocale, PdfEntryDto> toEntry) {
        List<PdfEntryDto> entries = itemsOf(root, key)
                .map(item -> toEntry.apply(item, locale))
                .filter(Objects::nonNull)
                .toList();
        return PdfSectionDto.ofEntries(title.text(locale), entries);
    }

    // Array order is the display order, as in the editor
    private Stream<JsonNode> itemsOf(JsonNode root, CvSectionKey key) {
        JsonNode section = root.path(key.key());
        return section.isArray() ? StreamSupport.stream(section.spliterator(), false) : Stream.empty();
    }

    // ---------- Entries ----------

    private PdfRatingDto skill(JsonNode item, PdfLocale locale) {
        String code = text(item, CvFieldKey.PROFICIENCY);
        String level = code == null ? null : PdfSkillLevel.fromCode(code)
                .map(found -> found.text(locale))
                .orElseGet(() -> humanize(code));
        return rating(text(item, CvFieldKey.SKILL_NAME), level, text(item, CvFieldKey.NOTE));
    }

    private PdfRatingDto language(JsonNode item, PdfLocale locale) {
        String code = text(item, CvFieldKey.PROFICIENCY);
        String level = code == null ? null : PdfLanguageLevel.fromCode(code)
                .map(found -> found.text(locale))
                .orElseGet(() -> humanize(code));
        return rating(text(item, CvFieldKey.LANGUAGE_NAME), level, text(item, CvFieldKey.CERTIFICATION));
    }

    // Position as the headline, company beneath it
    private PdfEntryDto experience(JsonNode item, PdfLocale locale) {
        return entry(
                text(item, CvFieldKey.POSITION),
                text(item, CvFieldKey.COMPANY),
                period(item, locale),
                List.of(),
                text(item, CvFieldKey.DESCRIPTION)
        );
    }

    private PdfEntryDto education(JsonNode item, PdfLocale locale) {
        return entry(
                text(item, CvFieldKey.INSTITUTION),
                join(text(item, CvFieldKey.DEGREE), text(item, CvFieldKey.FIELD)),
                period(item, locale),
                List.of(),
                text(item, CvFieldKey.DESCRIPTION)
        );
    }

    // Issue date sits where a range would; expiry and credential follow as details
    private PdfEntryDto certification(JsonNode item, PdfLocale locale) {
        return entry(
                text(item, CvFieldKey.NAME),
                text(item, CvFieldKey.ISSUING_ORGANIZATION),
                date(item, CvFieldKey.ISSUE_DATE, locale.fullDate()),
                fields(
                        field(PdfLabel.EXPIRES_ON, date(item, CvFieldKey.EXPIRY_DATE, locale.fullDate()), locale),
                        field(PdfLabel.CREDENTIAL_ID, text(item, CvFieldKey.CREDENTIAL_ID), locale)
                ),
                null
        );
    }

    private PdfEntryDto project(JsonNode item, PdfLocale locale) {
        String teamSize = text(item, CvFieldKey.TEAM_SIZE);
        return entry(
                text(item, CvFieldKey.NAME),
                text(item, CvFieldKey.ROLE),
                period(item, locale),
                fields(
                        field(PdfLabel.TEAM_SIZE,
                                teamSize == null ? null : PdfLabel.TEAM_SIZE_VALUE.format(locale, teamSize), locale),
                        field(PdfLabel.TECHNOLOGIES, text(item, CvFieldKey.TECHNOLOGIES), locale)
                ),
                text(item, CvFieldKey.DESCRIPTION)
        );
    }

    private PdfEntryDto additionalInfo(JsonNode item, PdfLocale locale) {
        return entry(text(item, CvFieldKey.TITLE), null, null, List.of(), text(item, CvFieldKey.CONTENT));
    }

    // ---------- Builders ----------

    // A rating without a name is a blank placeholder, not content
    private PdfRatingDto rating(String name, String level, String note) {
        return name == null ? null : new PdfRatingDto(name, level, note);
    }

    // An entry with nothing in it is skipped; a missing headline is taken from the subtitle
    private PdfEntryDto entry(String title, String subtitle, String period,
                              List<PdfFieldDto> details, String description) {
        if (title == null && subtitle == null && period == null && details.isEmpty() && description == null) {
            return null;
        }
        if (title == null) {
            return new PdfEntryDto(subtitle, null, period, details, description);
        }
        return new PdfEntryDto(title, subtitle, period, details, description);
    }

    private PdfFieldDto field(PdfLabel label, String value, PdfLocale locale) {
        return value == null ? null : new PdfFieldDto(label.text(locale), value);
    }

    // Stream.of rather than List.of: Absent fields arrive as null and are dropped here
    private List<PdfFieldDto> fields(PdfFieldDto... fields) {
        return Stream.of(fields).filter(Objects::nonNull).toList();
    }

    // ---------- Values ----------

    // Trimmed text; a list (technologies) reads as "a, b"; blank reads as null
    private String text(JsonNode node, CvFieldKey key) {
        JsonNode value = node.path(key.getKey());
        if (value.isArray()) {
            String joined = StreamSupport.stream(value.spliterator(), false)
                    .map(JsonNode::asText)
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .collect(Collectors.joining(LIST_SEPARATOR));
            return joined.isEmpty() ? null : joined;
        }
        if (value.isMissingNode() || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    // ISO date in the CV's format; an unparseable value is printed as stored rather than dropped
    private String date(JsonNode node, CvFieldKey key, DateTimeFormatter format) {
        JsonNode value = node.path(key.getKey());
        // A LocalDate written as [yyyy, m, d] by a mapper with timestamps enabled
        if (value.isArray() && value.size() >= 3) {
            return LocalDate.of(value.get(0).asInt(), value.get(1).asInt(), value.get(2).asInt()).format(format);
        }
        String raw = text(node, key);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.length() > ISO_DATE_LENGTH ? raw.substring(0, ISO_DATE_LENGTH) : raw)
                    .format(format);
        } catch (DateTimeParseException e) {
            return raw;
        }
    }

    // "start - end"; an open end reads as "Present" and a lone end date stands alone
    private String period(JsonNode item, PdfLocale locale) {
        String start = date(item, CvFieldKey.START_DATE, locale.monthYear());
        String end = date(item, CvFieldKey.END_DATE, locale.monthYear());
        if (start == null) {
            return end;
        }
        return start + RANGE + (end == null ? PdfLabel.PRESENT.text(locale) : end);
    }

    private String join(String... parts) {
        String joined = Stream.of(parts)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(SEPARATOR));
        return joined.isEmpty() ? null : joined;
    }

    // A level code without a printed label: "PROFESSIONAL_WORKING" -> "Professional working"
    private String humanize(String code) {
        String spaced = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public byte[] render(PdfTemplate template, CvPdfContext model) {
        Context context = new Context();
        context.setVariable(MODEL_VARIABLE, model);
        String xhtml = templateEngine.process(template.getFileName(), context);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            // Images are data: URIs, so there is nothing relative to resolve
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }

    // Subset: Only the glyphs used are embedded, which keeps a Japanese CV at a few hundred KB
    private void registerFonts(PdfRendererBuilder builder) {
        for (PdfFont font : PdfFont.values()) {
            byte[] bytes = bytesOf(font);
            // Typed explicitly so the lambda cannot match another useFont overload
            FSSupplier<InputStream> source = () -> new ByteArrayInputStream(bytes);
            builder.useFont(
                    source,
                    font.getFamily().getCssName(),
                    font.getWeight(),
                    font.getStyle(),
                    true
            );
        }
    }

    private byte[] bytesOf(PdfFont font) {
        return fontBytes.computeIfAbsent(font, PdfExportServiceImpl::readFont);
    }

    private static byte[] readFont(PdfFont font) {
        try (InputStream in = new ClassPathResource(font.getClasspath()).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Font missing from the classpath: " + font.getClasspath(), e);
        }
    }

    private static SpringTemplateEngine buildTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(PdfTemplate.LOCATION);
        resolver.setSuffix(PdfTemplate.EXTENSION);
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
