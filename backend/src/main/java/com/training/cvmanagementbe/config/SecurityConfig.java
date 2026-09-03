package com.training.cvmanagementbe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.cvmanagementbe.config.auth.JwtAuthFilter;
import com.training.cvmanagementbe.dto.response.ApiResponse;
import com.training.cvmanagementbe.dto.response.ErrorDetail;
import com.training.cvmanagementbe.enums.ErrorCode;
import com.training.cvmanagementbe.enums.PublicEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Minimal security configuration.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthFilter jwtAuthFilter,
                                    RestAuthenticationEntryPoint authenticationEntryPoint,
                                    RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PublicEndpoint.patterns()).permitAll()
                .anyRequest().authenticated()
            )
            /*
             * Both handlers write the response envelope themselves. They run inside the filter
             * chain, before a handler method is selected, so neither GlobalExceptionHandler nor
             * ApiResponseAdvice ever sees these bodies.
             *
             * The split matters to the frontend: the entry point answers 401 (no usable token,
             * errorInterceptor redirects to login) while the denied handler answers 403
             * (authenticated but out of scope, errorInterceptor only shows a notice).
             */
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            // Disable default form login and HTTP basic popup
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
