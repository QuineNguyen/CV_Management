package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.AccountStatus;
import com.training.cvmanagementbe.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    // Immutable after creation
    @Column(name = "email", nullable = false, unique = true, updatable = false)
    private String email;

    // Immutable after creation
    @Column(name = "username", nullable = false, unique = true, updatable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "primary_department_id", nullable = false)
    private UUID primaryDepartmentId;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "address")
    private String address;

    @Column(name = "avatar_image_id")
    private UUID avatarImageId;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    // Password path only - the Google path never touches this counter
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "token_valid_from", nullable = false)
    private Instant tokenValidFrom;

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
