package com.ecommerce.user.controller;

import com.ecommerce.user.dto.EarnPointsRequest;
import com.ecommerce.user.dto.LoyaltyAccountDto;
import com.ecommerce.user.dto.RedeemPointsRequest;
import com.ecommerce.user.dto.RedeemPointsResponse;
import com.ecommerce.user.model.CustomerAddress;
import com.ecommerce.user.model.CustomerLoyalty;
import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.AddressService;
import com.ecommerce.user.service.LoyaltyService;
import com.ecommerce.user.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Endpoints for user profile retrieval, updates, loyalty rewards, and addresses")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;
    private final LoyaltyService loyaltyService;
    private final AddressService addressService;
    private final RbacService rbacService;

    @Operation(summary = "Get User by ID", description = "Retrieves user details for a given unique identifier.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(
            @Parameter(description = "User unique ID", example = "1", required = true)
            @PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Update User Profile", description = "Updates user basic profile information like display name.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User profile updated successfully",
                    content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUserProfile(
            @Parameter(description = "User unique ID", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody UserProfileUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        return ResponseEntity.ok(userRepository.save(user));
    }

    @Operation(summary = "Get All Users", description = "Retrieves a complete list of all registered users (Admin access).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of users retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = User.class))))
    })
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @Operation(summary = "Update User Role", description = "Assigns a new role (ADMIN, MANAGER, USER) to a user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Role updated successfully",
                    content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}/role")
    public ResponseEntity<User> updateUserRole(
            @Parameter(description = "User unique ID", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody UserRoleUpdateRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String actorRole,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actorEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        return ResponseEntity.ok(rbacService.assignUserRole(user, request.getRole(), actorRole, actorId, actorEmail));
    }

    @Operation(summary = "Update User Status", description = "Activates or deactivates a user account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully",
                    content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}/status")
    public ResponseEntity<User> updateUserStatus(
            @Parameter(description = "User unique ID", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody UserStatusUpdateRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String actorRole,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actorEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        return ResponseEntity.ok(rbacService.updateUserStatus(user, request.isActive(), actorRole, actorId, actorEmail));
    }

    // ==========================================
    // CUSTOMER LOYALTY PROGRAM & REWARDS
    // ==========================================

    @Operation(summary = "Get Customer Loyalty Profile", description = "Retrieves points balance, membership tier, cash value, and reward history.")
    @GetMapping("/{id}/loyalty")
    public ResponseEntity<LoyaltyAccountDto> getLoyaltyProfile(
            @Parameter(description = "User unique ID", example = "2", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(loyaltyService.getLoyaltyProfile(id));
    }

    @Operation(summary = "Earn Loyalty Points", description = "Accrues loyalty reward points based on order purchase amount and tier multiplier.")
    @PostMapping("/{id}/loyalty/earn")
    public ResponseEntity<CustomerLoyalty> earnPoints(
            @Parameter(description = "User unique ID", example = "2", required = true)
            @PathVariable Long id,
            @RequestBody EarnPointsRequest request) {
        return ResponseEntity.ok(loyaltyService.earnPoints(id, request));
    }

    @Operation(summary = "Redeem Loyalty Points", description = "Redeems available points for immediate checkout monetary discount.")
    @PostMapping("/{id}/loyalty/redeem")
    public ResponseEntity<RedeemPointsResponse> redeemPoints(
            @Parameter(description = "User unique ID", example = "2", required = true)
            @PathVariable Long id,
            @RequestBody RedeemPointsRequest request) {
        return ResponseEntity.ok(loyaltyService.redeemPoints(id, request));
    }

    // ==========================================
    // CUSTOMER SAVED ADDRESS BOOK
    // ==========================================

    @Operation(summary = "List Saved Addresses", description = "Retrieves customer shipping and billing addresses.")
    @GetMapping("/{id}/addresses")
    public ResponseEntity<List<CustomerAddress>> getUserAddresses(
            @Parameter(description = "User unique ID", example = "2", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(addressService.getUserAddresses(id));
    }

    @Operation(summary = "Save New Address", description = "Adds a new address to customer address book.")
    @PostMapping("/{id}/addresses")
    public ResponseEntity<CustomerAddress> addAddress(
            @Parameter(description = "User unique ID", example = "2", required = true)
            @PathVariable Long id,
            @RequestBody CustomerAddress address) {
        return ResponseEntity.ok(addressService.addAddress(id, address));
    }

    @Operation(summary = "Update a saved address", description = "Edits a shipping or billing address owned by the customer.")
    @PutMapping("/{id}/addresses/{addressId}")
    public ResponseEntity<CustomerAddress> updateAddress(@PathVariable Long id, @PathVariable Long addressId,
            @RequestBody CustomerAddress address) {
        return ResponseEntity.ok(addressService.updateAddress(id, addressId, address));
    }

    @Operation(summary = "Delete Saved Address", description = "Removes an address from customer address book.")
    @DeleteMapping("/{id}/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @Parameter(description = "User unique ID", example = "2", required = true)
            @PathVariable Long id,
            @Parameter(description = "Address unique ID", example = "10", required = true)
            @PathVariable Long addressId) {
        addressService.deleteAddress(id, addressId);
        return ResponseEntity.noContent().build();
    }

    @Data
    @Schema(description = "Payload to update user name")
    static class UserProfileUpdateRequest {
        @Schema(description = "Updated display name", example = "Jane Doe")
        private String name;
    }

    @Data
    @Schema(description = "Payload to update user authorization role")
    static class UserRoleUpdateRequest {
        @Schema(description = "Assigned role", example = "ADMIN", allowableValues = {"ADMIN", "MANAGER", "USER"})
        private String role;
    }

    @Data
    @Schema(description = "Payload to update account activation status")
    static class UserStatusUpdateRequest {
        @Schema(description = "Account active flag", example = "false")
        private boolean active;
    }
}
