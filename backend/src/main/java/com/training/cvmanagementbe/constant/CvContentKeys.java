package com.training.cvmanagementbe.constant;

/*
 * JSON keys inside content_json that code outside CvContentCodec must also know - the native
 * search reads them with JSON functions. Kept in one place so SQL and codec cannot drift.
 */
public final class CvContentKeys {

    // Catalogue reference of a skills entry
    public static final String SKILL_ID = "skill_id";

    // Typed name of a skills entry, as stored in content_json
    public static final String SKILL_NAME = "skill_name";

    private CvContentKeys() {

    }
}
