package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.JwtClaim;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    // Builds a token carrying {sub, role, userId}. The role travels in the payload.
    public String generateToken(User user) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(JwtClaim.ROLE.getKey(), user.getRole().name())
                .claim(JwtClaim.USER_ID.getKey(), user.getId().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    // Verifies signature and expiry, then returns the payload.
    // Empty means the token is unusable - malformed, tampered with, or expired.
    public Optional<Claims> extractClaims(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    // Server-side revocation: a token issued before the mark is dead.
    // Separates isTokenValid() from extractClaims() so that the revocation mark can be checked after the token is parsed.
    public boolean isTokenValid(Claims claims, User user) {
        Instant validFrom = user.getTokenValidFrom();
        if (validFrom == null) {
            return true; // No revocation mark, so valid
        }
        return !claims.getIssuedAt().toInstant().isBefore(validFrom.truncatedTo(ChronoUnit.SECONDS));
    }

    // Mark to store in users.token_valid_from when revoking.
    // Rounded up one second because `issuedAt` is truncated to seconds in the token.
    // otherwise, a token issued at the same second as the revocation mark would be considered valid.
    public Instant revocationMark() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
    }
}
