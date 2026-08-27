package com.training.cvmanagementbe.record;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.training.cvmanagementbe.enums.CvSectionKey;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * The whole content_json column of cv_drafts and cv_versions.
 *
 * - This is contract #1 of the system: a published version stores a full snapshot of this shape
 * and is never rewritten, so any change here must stay backward compatible when reading old rows.
 * @JsonIgnoreProperties makes a version written by an older build still deserializable.
 *
 * - Lists are normalised to empty rather than null, so callers never null-check a section.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CvContent(

        @JsonProperty("personal_info") PersonalInfo personalInfo,
        @JsonProperty("career_objective") CareerObjective careerObjective,
        @JsonProperty("skills") List<SkillEntry> skills,
        @JsonProperty("experience") List<ExperienceEntry> experience,
        @JsonProperty("education") List<EducationEntry> education,
        @JsonProperty("certifications") List<CertificationEntry> certifications,
        @JsonProperty("projects") List<ProjectEntry> projects,
        @JsonProperty("languages") List<LanguageEntry> languages,
        @JsonProperty("additional_info") List<AdditionalInfoEntry> additionalInfo
) {

    public CvContent {
        personalInfo = personalInfo == null ? PersonalInfo.empty() : personalInfo;
        careerObjective = careerObjective == null ? CareerObjective.empty() : careerObjective;
        skills = copyOf(skills);
        experience = copyOf(experience);
        education = copyOf(education);
        certifications = copyOf(certifications);
        projects = copyOf(projects);
        languages = copyOf(languages);
        additionalInfo = copyOf(additionalInfo);
    }

    // Skeleton for a brand-new draft: all 9 sections present, every repeated section empty.
    public static CvContent empty() {
        return new CvContent(null, null, null, null, null, null, null, null, null);
    }

    // Repeated entries of one section, used by diff, sync and validation without a switch at each site.
    public List<? extends RepeatedEntry> entriesOf(CvSectionKey sectionKey) {
        return switch (sectionKey) {
            case SKILLS -> skills;
            case EXPERIENCE -> experience;
            case EDUCATION -> education;
            case CERTIFICATIONS -> certifications;
            case PROJECTS -> projects;
            case LANGUAGES -> languages;
            case ADDITIONAL_INFO -> additionalInfo;
            case PERSONAL_INFO, CAREER_OBJECTIVE -> List.of();
        };
    }

    // Every repeated section keyed by its section, in template order.
    public Map<CvSectionKey, List<? extends RepeatedEntry>> repeatedSections() {
        return Arrays.stream(CvSectionKey.values())
                .filter(CvSectionKey::repeated)
                .collect(Collectors.toMap(
                        key -> key,
                        this::entriesOf,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    // Submit-time validation: personal info, skills and experience must be filled in.
    public boolean submittable() {
        return personalInfo.fullName() != null && !personalInfo.fullName().isBlank()
                && !skills.isEmpty()
                && !experience.isEmpty();
    }

    // Count of placeholders still awaiting translation; feeds untranslatedItemCount.
    public long untranslatedItemCount() {
        return repeatedSections().values().stream()
                .flatMap(List::stream)
                .filter(RepeatedEntry::untranslated)
                .count();
    }

    private static <T> List<T> copyOf(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

}
