package com.ecommerce.user.controller;

import com.ecommerce.user.security.JwtUtil;
import com.ecommerce.user.service.TenantAccessService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/internal/authorization")
@RequiredArgsConstructor
public class InternalAuthorizationController {
    private final JwtUtil jwtUtil;
    private final TenantAccessService tenantAccess;

    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToken;

    @PostMapping("/check")
    public ResponseEntity<Void> check(HttpServletRequest request) {
        requireServiceToken(request.getHeader("X-Service-Token"));
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        if (!jwtUtil.validateApplicationToken(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid application token.");

        Claims claims = jwtUtil.getClaims(token);
        try {
            long userId = ((Number) claims.get("id")).longValue();
            UUID tenantId = UUID.fromString(claims.get("tenant_id", String.class));
            String app = claims.get("application_code", String.class);
            String role = claims.get("tenant_role", String.class);
            if (!tenantAccess.isCurrentApplicationAccess(userId, tenantId, app, role))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Application access was revoked or is no longer available.");
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid application authorization context.");
        }
    }

    private void requireServiceToken(String supplied) {
        if (!StringUtils.hasText(serviceToken) || !StringUtils.hasText(supplied)
                || !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated service access is required.");
        }
    }
}
