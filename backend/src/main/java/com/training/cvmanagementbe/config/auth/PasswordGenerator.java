package com.training.cvmanagementbe.config.auth;

import com.training.cvmanagementbe.enums.PasswordCharset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Generate temporary passwords for the admin reset flow
@Component
public class PasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final int length;

    public PasswordGenerator(@Value("${security.password.temporary-length:12}") int length) {
        this.length = length;
    }

    public String generate() {
        String allPools = String.join("", allPools());
        List<Character> characters = new ArrayList<>(length);

        // Seed one character per class first, so the result always satisfies
        for (PasswordCharset charset : PasswordCharset.values()) {
            characters.add(pick(charset.pool()));
        }
        while (characters.size() < length) {
            characters.add(pick(allPools));
        }

        Collections.shuffle(characters, RANDOM);
        StringBuilder builder = new StringBuilder(characters.size());
        characters.forEach(builder::append);
        return builder.toString();
    }

    private List<String> allPools() {
        List<String> pools = new ArrayList<>();
        for (PasswordCharset charset : PasswordCharset.values()) {
            pools.add(charset.pool());
        }
        return pools;
    }

    private char pick(String pool) {
        return pool.charAt(RANDOM.nextInt(pool.length()));
    }
}
