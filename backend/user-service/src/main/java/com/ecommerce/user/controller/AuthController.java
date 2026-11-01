package com.ecommerce.user.controller;

import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.security.JwtUtil;
import com.ecommerce.user.service.RbacService;
import com.ecommerce.user.service.TenantAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication and authorization token generation")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final RbacService rbacService;
    private final TenantAccessService tenantAccessService;

    @Operation(summary = "Authenticate User", description = "Authenticates credentials and returns a signed JWT token.", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful, JWT token issued",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "403", description = "User account deactivated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Email and password are required"));
        }
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid email or password"));
        }

        if (!user.isActive()) {
            return ResponseEntity.status(403).body(new ErrorResponse("Account is deactivated"));
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole(), rbacService.permissionsFor(user.getRole()));
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @Operation(summary = "Authenticate into one client application", description = "Verifies tenant membership, application entitlement and the user's application grant before issuing a short-lived scoped token.", security = {})
    @PostMapping("/login/application")
    public ResponseEntity<?> loginToApplication(@RequestBody ApplicationLoginRequest request) {
        if (request.getEmail() == null || request.getPassword() == null
                || request.getApplicationCode() == null || request.getTenantSlug() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Email, password, application code and client slug are required"));
        }
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid email or password"));
        }
        if (!user.isActive()) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("Account is deactivated"));

        TenantAccessService.ApplicationAccess access = tenantAccessService.requireApplicationAccess(
                user.getId(), request.getApplicationCode(), request.getTenantSlug());
        String token = jwtUtil.generateApplicationToken(user.getId(), user.getEmail(), user.getName(), access.roleKey(),
                access.applicationCode(), access.tenantId(), access.permissions());
        return ResponseEntity.ok(new ApplicationAuthResponse(token, access.applicationCode(), access.tenantId().toString(),
                access.tenantSlug(), access.tenantName(), access.roleKey()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request.getEmail() == null || !request.getEmail().contains("@") ||
                request.getPassword() == null || request.getPassword().length() < 12 ||
                request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Provide a valid email, name, and password of at least 12 characters"));
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("An account with this email already exists"));
        }
        User user = new User();
        user.setEmail(email);
        user.setName(request.getName().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setActive(true);
        user = userRepository.save(user);
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole(), rbacService.permissionsFor(user.getRole()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token));
    }

    @Data
    @Schema(description = "Error response model")
    static class ErrorResponse {
        @Schema(description = "Error message", example = "Account is deactivated")
        private String error;
        public ErrorResponse(String error) { this.error = error; }
    }

    @Data
    @Schema(description = "User login credentials payload")
    static class AuthRequest {
        @Schema(description = "User email address", example = "admin@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        private String email;
        @Schema(description = "User password", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
        private String password;
    }

    @Data
    static class RegisterRequest {
        private String email;
        private String name;
        private String password;
    }

    @Data
    static class ApplicationLoginRequest {
        private String email;
        private String password;
        private String applicationCode;
        private String tenantSlug;
    }

    @Data
    @Schema(description = "JWT authentication response payload")
    static class AuthResponse {
        @Schema(description = "JWT Bearer access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        private String token;
        public AuthResponse(String token) { this.token = token; }
    }

    @Data
    static class ApplicationAuthResponse {
        private final String token;
        private final String applicationCode;
        private final String tenantId;
        private final String tenantSlug;
        private final String tenantName;
        private final String role;
    }
}
