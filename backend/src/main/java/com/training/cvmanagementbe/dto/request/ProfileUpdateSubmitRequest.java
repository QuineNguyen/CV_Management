package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/*
 * The five fields a user may change about themselves. Role, department, teams, email and username
 * stay admin-only and are deliberately absent - an employee cannot promote themselves by editing
 * a form.
 *
 * - Every field is nullable: null means "keep my current value". The service rejects a request
 * where nothing actually differs.
 */
@Schema(name = "ProfileUpdateSubmitRequest",
        description = "Fields the user wants changed. A null field means 'keep the current value'")
public record ProfileUpdateSubmitRequest(

        @Size(max = 200)
        String fullName,

        @Past
        LocalDate dateOfBirth,

        @Size(max = 30)
        @Pattern(regexp = "^$|^[0-9+()\\s-]{6,30}$", message = "Phone number format is not valid")
        String phoneNumber,

        @Size(max = 500)
        String address,

        UUID avatarImageId
) {
}
