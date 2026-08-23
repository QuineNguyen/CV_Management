package com.training.cvmanagementbe.dto.request;

import com.training.cvmanagementbe.enums.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Payload for updating a user. Email and username are intentionally absent (immmutable).
public record UpdateUserRequest(

        @NotBlank
        @Size(max = 200)
        String fullName,

        @NotNull
        Role role,

        @NotNull
        UUID primaryDepartmentId,

        @Past
        LocalDate dateOfbirth,

        @Size(max = 30)
        @Pattern(regexp = "^$|^[0-9+()\\s-]{6,30}$")
        String phoneNumber,

        @Size(max = 500)
        String address,

        @NotEmpty
        @Valid
        List<TeamAssignment> teams
) {
}
