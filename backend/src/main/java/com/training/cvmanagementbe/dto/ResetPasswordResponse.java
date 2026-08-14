package com.training.cvmanagementbe.dto;

// Shown once in the admin dialog; also delivered in the email to the user
public record ResetPasswordResponse(
        String temporaryPassword
) {
}
