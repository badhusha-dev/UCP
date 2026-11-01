package com.ecommerce.user.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
@Schema(description = "User entity representing registered account details")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Unique user identifier", example = "1")
    private Long id;

    @Column(unique = true, nullable = false)
    @Schema(description = "User primary email address", example = "john.doe@example.com")
    private String email;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "Encrypted user password (write-only)", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @Column(nullable = false)
    @Schema(description = "User role authorization level", example = "ADMIN", allowableValues = {"ADMIN", "MANAGER", "USER"})
    private String role; // ADMIN, MANAGER, USER

    @Column
    @Schema(description = "Full name of the user", example = "John Doe")
    private String name;

    @Column(nullable = false)
    @Schema(description = "Account active status flag", example = "true")
    private boolean active = true;
}
