package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.AuthPath;
import com.training.cvmanagementbe.constant.AuthorityExpression;
import com.training.cvmanagementbe.dto.*;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Authentication", description = "Login, logout and password management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(AuthPath.LOGIN)
    @Operation(summary = "Login with username and password")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping(AuthPath.GOOGLE_LOGIN)
    @Operation(summary = "Login with Google OAuth2 token")
    public LoginResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request);
    }

    @PostMapping(AuthPath.LOGOUT)
    @Operation(summary = "Logout and revoke the current session")
    public ResponseEntity<Void> logout() {
        authService.logout(CurrentActor.requireUserId());
        return ResponseEntity.noContent().build();
    }

    // All sessions are revoked, so the frontend must force a re-login afterwards.
    @PostMapping(AuthPath.CHANGE_PASSWORD)
    @Operation(summary = "Change the current user's password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request, CurrentActor.requireUserId());
        return ResponseEntity.noContent().build();
    }

    // Returns the temporary password once for the admin dialog; email is sent in parallel
    @PostMapping(AuthPath.ADMIN_RESET_PASSWORD)
    @PreAuthorize(AuthorityExpression.ADMIN)
    @Operation(summary = "Reset a user's password (admin only)")
    public ResetPasswordResponse resetPassword(@PathVariable UUID userId) {
        return authService.resetPassword(userId);
    }
}
