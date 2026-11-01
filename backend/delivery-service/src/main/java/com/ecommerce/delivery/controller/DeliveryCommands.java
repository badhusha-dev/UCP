package com.ecommerce.delivery.controller;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DeliveryCommands {
    private DeliveryCommands() {}

    public record CreateDelivery(
            @NotBlank @Size(max = 128) String orderReference,
            @NotBlank @Pattern(regexp = "RETAIL|RESTAURANT") String businessType,
            Long recipientUserId,
            @NotBlank @Size(max = 160) String pickupName,
            @NotBlank @Size(max = 1000) String pickupAddress,
            @NotBlank @Size(max = 160) String dropoffName,
            @NotBlank @Size(max = 1000) String dropoffAddress,
            @NotBlank @Size(max = 24) String postalCode,
            @Size(max = 48) String requiredCapability,
            @NotNull @DecimalMin("0.0") BigDecimal weightKg,
            @NotNull @Min(1) @Max(500) Integer itemCount,
            OffsetDateTime scheduledFor) {}

    /**
     * Order-backed delivery. Recipient, order status and order total are read from Order Service, never taken from the caller.
     * Tenant, creator and initial status are likewise server-decided.
     */
    public record CreateFromOrder(
            @NotNull @Positive Long orderId,
            @NotBlank @Pattern(regexp = "RETAIL|RESTAURANT") String businessType,
            @NotBlank @Size(max = 160) String pickupName,
            @NotBlank @Size(max = 1000) String pickupAddress,
            @NotBlank @Size(max = 160) String dropoffName,
            @NotBlank @Size(max = 1000) String dropoffAddress,
            @NotBlank @Size(max = 24) String postalCode,
            @Size(max = 48) String requiredCapability,
            @NotNull @DecimalMin("0.0") BigDecimal weightKg,
            @NotNull @Min(1) @Max(500) Integer itemCount,
            OffsetDateTime scheduledFor) {}
    public record RuleDraft(@NotNull java.util.Map<String, Object> rules) {}
    public record RuleRollback(@NotNull @Min(1) Integer version) {}

    public record Readiness(@NotNull Boolean pickupReady, @NotNull Long expectedVersion) {}
    public record Transition(@NotBlank @Pattern(regexp = "CREATED|READY_FOR_DISPATCH|ASSIGNED|ARRIVED_AT_PICKUP|PICKED_UP|IN_TRANSIT|ARRIVED_AT_DROPOFF|DELIVERED|CANCELLED|PICKUP_FAILED|DELIVERY_FAILED|RETURN_TO_ORIGIN|RETURNED") String toStatus,
                            @Size(max = 1000) String reason, @NotNull Long expectedVersion) {}
    public record Assign(@NotNull UUID partnerId, @NotNull Long expectedVersion, @Size(max = 1000) String reason) {}
    public record AssignmentResponse(@NotNull Boolean accepted, @Size(max = 1000) String reason) {}
    public record PartnerProfile(@NotBlank @Size(max = 160) String displayName,
                                 @NotBlank @Size(max = 32) String vehicleType,
                                 @NotNull @DecimalMin("0.1") BigDecimal capacityKg,
                                 @NotNull @Min(1) @Max(20) Integer maxActiveDeliveries,
                                 @Size(max = 20) List<@NotBlank @Size(max = 48) String> capabilities,
                                 @Size(max = 50) List<@NotNull UUID> zoneIds) {}
    public record PartnerReview(@NotBlank @Pattern(regexp = "PENDING|APPROVED|REJECTED") String verificationStatus,
                                @NotBlank @Pattern(regexp = "NOT_SUBMITTED|SUBMITTED|REVIEW_REQUIRED|ACCEPTED|REJECTED") String documentStatus,
                                @NotNull Boolean active) {}
    public record Availability(@NotBlank @Pattern(regexp = "ONLINE|OFFLINE") String status) {}
    public record CreateZone(@NotBlank @Size(max = 48) String code, @NotBlank @Size(max = 120) String name,
                             @NotBlank @Pattern(regexp = "RETAIL|RESTAURANT|BOTH") String businessType,
                             @NotBlank @Size(max = 24) String postalPrefix, @NotNull Boolean active) {}
    public record Location(@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
                           @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
                           @DecimalMin("0.0") BigDecimal accuracyM) {}
    public record Proof(@NotBlank @Pattern(regexp = "OTP|PHOTO_REFERENCE|SIGNATURE_REFERENCE") String method,
                        @Pattern(regexp = "\\d{6}") String otp,
                        @Size(max = 160) String recipientName,
                        @Size(max = 500) String evidenceReference,
                        @Size(max = 1000) String note) {}
    public record Incident(@NotBlank @Pattern(regexp = "CUSTOMER_UNAVAILABLE|ADDRESS_ISSUE|VEHICLE_ISSUE|SAFETY|DAMAGED_ITEM|OTHER") String type,
                          @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity,
                          @NotBlank @Size(max = 2000) String description) {}
    public record IncidentResolution(@NotBlank @Size(max = 1000) String resolution) {}
    public record CreateTrip(@NotNull UUID partnerId, @NotEmpty @Size(max = 30) List<@NotNull UUID> deliveryIds) {}
    public record TripStatus(@NotBlank @Pattern(regexp = "ACTIVE|COMPLETED|CANCELLED") String status, @NotNull Long expectedVersion) {}
    public record ConfigValue(@NotBlank @Size(max = 64) String value) {}
    public record PricingPolicy(@NotBlank @Pattern(regexp = "RETAIL|RESTAURANT") String businessType,
                                @NotNull @DecimalMin("0.0") BigDecimal baseEarning,
                                @NotNull @DecimalMin("0.0") BigDecimal perItemEarning,
                                @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
                                @NotNull Boolean active) {}
}
