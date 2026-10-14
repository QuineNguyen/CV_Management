package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.record.cvs.PdfLinkClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Component
public class PdfLinkSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_BYTES = 32;
    private static final String TOKEN_SEPARATOR = ".";
    private static final String CLAIM_SEPARATOR = ":";
    private static final int CLAIM_COUNT = 5;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final Duration ttl;

    // Lifetime is configurable as app.pdf-link.ttl (e.g. 15m, PT30M); 15 minutes when unset
    public PdfLinkSigner(@Value("${app.pdf-link.ttl:PT15M}") Duration ttl) {
        byte[] secret = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(secret);
        this.key = new SecretKeySpec(secret, ALGORITHM);
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }

    public String sign(PdfLinkClaims claims) {
        String claimText = String.join(CLAIM_SEPARATOR,
                claims.cvId().toString(),
                claims.versionId().toString(),
                claims.userId().toString(),
                claims.role().name(),
                String.valueOf(claims.expiresAt().getEpochSecond()));
        String payload = ENCODER.encodeToString(claimText.getBytes(StandardCharsets.US_ASCII));
        return payload + TOKEN_SEPARATOR + ENCODER.encodeToString(mac(payload));
    }

    // Empty for anything forged, malformed or expired; the caller never learns which
    public Optional<PdfLinkClaims> verify(String token, Instant now) {
        if (token == null) {
            return Optional.empty();
        }
        int separator = token.indexOf(TOKEN_SEPARATOR);
        if (separator <= 0 || separator != token.lastIndexOf(TOKEN_SEPARATOR)) {
            return Optional.empty();
        }
        String payload = token.substring(0, separator);
        try {
            byte[] signature = DECODER.decode(token.substring(separator + 1));
            // Constant-time compare, so the signature cannot be guessed byte by byte
            if (!MessageDigest.isEqual(signature, mac(payload))) {
                return Optional.empty();
            }
            String[] parts = new String(DECODER.decode(payload), StandardCharsets.US_ASCII).split(CLAIM_SEPARATOR);
            if (parts.length != CLAIM_COUNT) {
                return Optional.empty();
            }
            PdfLinkClaims claims = new PdfLinkClaims(
                    UUID.fromString(parts[0]),
                    UUID.fromString(parts[1]),
                    UUID.fromString(parts[2]),
                    Role.valueOf(parts[3]),
                    Instant.ofEpochSecond(Long.parseLong(parts[4]))
            );
            return now.isBefore(claims.expiresAt()) ? Optional.of(claims) : Optional.empty();
        } catch (IllegalArgumentException e) {
            // Bad base54, UUID, role or number - NumberFormatException is one of these too
            return Optional.empty();
        }
    }

    // A fresh Mac per call: Instances are not thread-safe and creating one is cheap
    private byte[] mac(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
