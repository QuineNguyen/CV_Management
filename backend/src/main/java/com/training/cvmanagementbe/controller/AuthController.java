package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.AuthPath;
import com.training.cvmanagementbe.dto.*;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(AuthPath.LOGIN)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping(AuthPath.GOOGLE_LOGIN)
    public LoginResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request);
    }

    @PostMapping(AuthPath.LOGOUT)
    public ResponseEntity<Void> logout() {
        authService.logout(CurrentActor.requireUserId());
        return ResponseEntity.noContent().build();
    }

    // All sessions are revoked, so the frontend must force a re-login afterwards.
    @PostMapping(AuthPath.CHANGE_PASSWORD)
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request, CurrentActor.requireUserId());
        return ResponseEntity.noContent().build();
    }

    // Returns the temporary password once for the admin dialog; email is sent in parallel
    @PostMapping(AuthPath.ADMIN_RESET_PASSWORD)
    @PreAuthorize("hasRole('ADMIN')")
    public ResetPasswordResponse resetPassword(@PathVariable UUID userId) {
        return authService.resetPassword(userId);
    }
}
