package com.training.cvmanagementbe.dto.request;

import com.training.cvmanagementbe.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Payload for creating a user account. Email and username are immutable afterwards.
@Schema(name = "CreateUserRequest", description = "Payload for creating a user account. Email and username are immutable afterwards")
public record CreateUserRequest(

        @NotBlank
        @Size(max = 200)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 3, max = 100)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$")
        String username,

        @NotNull
        Role role,

        @NotNull
        UUID primaryDepartmentId,

        @Past
        LocalDate dateOfBirth,

        @Size(max = 30)
        @Pattern(regexp = "^$|^[0-9+()\\s-]{6,30}$")
        String phoneNumber,

        @Size(max = 500)
        String address,

        // At least one team, exactly one flagged as primary
        @NotEmpty
        @Valid
        List<TeamAssignment> teams
) {
}
