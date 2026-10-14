package com.training.cvmanagementbe.enums.cvs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
 * JSON field keys inside content_json, as declared by CV_SECTIONS on the frontend
 * Readers that walk the JSON tree use these instead of literals.
 */
@Getter
@RequiredArgsConstructor
public enum CvFieldKey {

    // personal_info
    FULL_NAME("full_name"),
    POSITION("position"),
    EMAIL("email"),
    PHONE("phone"),
    DATE_OF_BIRTH("date_of_birth"),
    ADDRESS("address"),

    // career_objective, additional_info
    CONTENT("content"),
    TITLE("title"),

    // skills
    SKILL_NAME("skill_name"),
    PROFICIENCY("proficiency"),
    NOTE("note"),

    // experience, education, projects
    COMPANY("company"),
    START_DATE("start_date"),
    END_DATE("end_date"),
    DESCRIPTION("description"),
    INSTITUTION("institution"),
    FIELD("field"),
    DEGREE("degree"),

    // certifications, projects
    NAME("name"),
    ISSUING_ORGANIZATION("issuing_organization"),
    ISSUE_DATE("issue_date"),
    EXPIRY_DATE("expiry_date"),
    CREDENTIAL_ID("credential_id"),
    ROLE("role"),
    TEAM_SIZE("team_size"),
    TECHNOLOGIES("technologies"),

    // Languages
    LANGUAGE_NAME("language_name"),
    CERTIFICATION("certification");

    private final String key;
}
