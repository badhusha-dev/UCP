package com.ecommerce.user.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_address")
@Data
@Schema(description = "Customer saved shipping and billing address book entry")
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Schema(description = "User unique ID", example = "2")
    private Long userId;

    @Column(name = "address_type", nullable = false, length = 32)
    @Schema(description = "Address classification: SHIPPING or BILLING", example = "SHIPPING")
    private String addressType = "SHIPPING";

    @Column(nullable = false)
    @Schema(description = "Street address line", example = "742 Evergreen Terrace")
    private String street;

    @Column(nullable = false, length = 128)
    @Schema(description = "City", example = "Springfield")
    private String city;

    @Column(length = 128)
    @Schema(description = "State or province", example = "OR")
    private String state;

    @Column(name = "postal_code", nullable = false, length = 32)
    @Schema(description = "ZIP or Postal code", example = "97477")
    private String postalCode;

    @Column(nullable = false, length = 64)
    @Schema(description = "Country", example = "US")
    private String country = "US";

    @Column(name = "is_default", nullable = false)
    @Schema(description = "Whether this is the primary default address", example = "true")
    private Boolean isDefault = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
