package com.training.cvmanagementbe.config.auth;

import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.PublicEndpoint;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    // Public endpoints skip the filter entirely; no token, no database hit.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return Arrays.stream(PublicEndpoint.patterns())
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Leave both contexts empty on any failure; the entry point turns that into a 401.
        Optional<User> authenticated = readBearerToken(request)
                .flatMap(jwtService::extractClaims)
                .flatMap(this::resolvedValidUser);

        authenticated.ifPresent(user -> {
            populateSecurityContext(user);
            // AuditLogger and JPA auditing both read the actor from here.
            CurrentActor.set(user.getId(), user.getRole());
        });

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat pools threads; a leaked actor would be attributed to the next request on this thread.
            CurrentActor.clear();
        }
    }

    private Optional<String> readBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()).trim());
    }

    private Optional<User> resolvedValidUser(Claims claims) {
        return userRepository.findByUsername(claims.getSubject())
                // Revoked by logout / role change / deactivation / password change
                .filter(user -> jwtService.isTokenValid(claims,user))
                // INACTIVE is rejected on both sign-in paths
                .filter(User::isActive);
    }

    private void populateSecurityContext(User user) {
        var authorities = List.of(new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + user.getRole().name()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities)
        );
    }
}
