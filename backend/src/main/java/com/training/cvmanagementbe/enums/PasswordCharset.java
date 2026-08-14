package com.training.cvmanagementbe.enums;

import java.util.regex.Pattern;

/**
 * `pool` is used for generating password automatically, `pattern` for validating.
 * Ambiguous characters (I, l, 1, O, 0) are excluded from pools only,
 * so generated temporary passwords stay readable when typed by hand.
 */
public enum PasswordCharset {

    UPPERCASE("ABCDEFGHJKLMNPQRSTUVWXYZ", "[A-Z]"),
    LOWERCASE("abcdefghijkmnopqrstuvwxyz", "[a-z]"),
    DIGIT("23456789", "[0-9]"),
    SPECIAL("!@#$%^&*()-_=+", "[^A-Za-z0-9]");

    private final String pool;
    private final Pattern pattern;

    PasswordCharset(String pool, String regex) {
        this.pool = pool;
        this.pattern = Pattern.compile(regex);
    }

    public String pool() {
        return pool;
    }

    /** True when the password contains at least one character of this class. */
    public boolean isPresentIn(String password) {
        return pattern.matcher(password).find();
    }
}
