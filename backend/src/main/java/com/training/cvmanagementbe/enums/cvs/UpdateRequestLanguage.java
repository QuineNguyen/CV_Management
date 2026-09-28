package com.training.cvmanagementbe.enums.cvs;

import java.util.List;

/*
 * Language picked on the create form. A stored request always holds exactly one language;
 * ALL only exists on the way in and fans out to one request per language.
 */
public enum UpdateRequestLanguage {

    VI, EN, JA, ALL;

    public List<Language> toLanguages() {
        return this == ALL
                ? List.of(Language.values())
                : List.of(Language.valueOf(name()));
    }

    public boolean isSingle() {
        return this != ALL;
    }
}
