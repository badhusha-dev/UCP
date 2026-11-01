package com.ecommerce.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PricingRoutePermissionTest {
    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Autowired
    private WebTestClient client;

    @Test
    void catalogPermissionAloneDoesNotOpenPricingAdmin() {
        client.get().uri("/api/products/admin/pricing/dashboard")
                .header(HttpHeaders.AUTHORIZATION, bearer(List.of("catalog:manage")))
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void pricingReadPassesTheGatewayGate() {
        client.get().uri("/api/products/admin/pricing/dashboard")
                .header(HttpHeaders.AUTHORIZATION, bearer(List.of("pricing:read")))
                .exchange().expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
    }

    @Test
    void pricingReadDoesNotOpenCatalogAdmin() {
        client.get().uri("/api/products/admin")
                .header(HttpHeaders.AUTHORIZATION, bearer(List.of("pricing:read")))
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void storefrontAdminNeedsStorefrontPermission() {
        client.get().uri("/api/products/admin/storefront")
                .header(HttpHeaders.AUTHORIZATION, bearer(List.of("catalog:manage", "pricing:read")))
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.get().uri("/api/products/admin/storefront")
                .header(HttpHeaders.AUTHORIZATION, bearer(List.of("storefront:read")))
                .exchange().expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
    }

    @Test
    void publicStorefrontIsAnonymousButAdminIsNot() {
        client.get().uri("/api/products/admin/storefront").exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        client.get().uri("/api/products/public/storefront/demo/home").exchange().expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
    }

    @Test
    void orderAdminNeedsOrderPermissionNotLegacyOrdersManage() {
        client.get().uri("/api/orders/admin/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(List.of("orders:manage", "catalog:manage")))
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.get().uri("/api/orders/admin/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(List.of("order:view")))
                .exchange().expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
        client.get().uri("/api/orders/admin/orders").exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsConsoleNeedsReturnPermission() {
        client.get().uri("/api/orders/admin/rma/returns")
                .header(HttpHeaders.AUTHORIZATION, bearer(List.of("order:view", "orders:manage")))
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.get().uri("/api/orders/admin/rma/returns")
                .header(HttpHeaders.AUTHORIZATION, bearer(List.of("return:view")))
                .exchange().expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
        client.get().uri("/api/orders/admin/rma/returns").exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static String bearer(List<String> permissions) {
        String token = Jwts.builder().claim("id", 42L).claim("role", "ADMIN").claim("permissions", permissions)
                .setSubject("pricing-test@example.invalid").setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes()), SignatureAlgorithm.HS256).compact();
        return "Bearer " + token;
    }
}
