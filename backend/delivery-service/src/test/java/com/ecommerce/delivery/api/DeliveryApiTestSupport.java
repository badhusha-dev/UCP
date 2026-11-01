package com.ecommerce.delivery.api;

import com.ecommerce.delivery.order.DeliveryOrderClient;
import com.ecommerce.delivery.security.DeliveryAuthorizationClient;
import com.ecommerce.delivery.service.DeliveryOutboxPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Drives the real controllers, security filter, services and SQL through MockMvc against an in-memory H2 database in
 * PostgreSQL mode. The schema is the real Flyway V1+V2 migration text (partial indexes cannot be created in H2 and are
 * skipped), so these tests prove behaviour but NOT that the migrations run on PostgreSQL.
 */
@SpringBootTest(properties = {
        "jwt.secret=" + DeliveryApiTestSupport.SECRET,
        "spring.datasource.url=jdbc:h2:mem:deliverytest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "delivery.assignment-scan-ms=3600000",
        "delivery.order-sync-poll-ms=3600000",
        "delivery.retention-cron=-",
        "delivery.otp.pepper=test-only-pepper-0123456789abcdef",
        "SERVICE_TO_SERVICE_TOKEN=test-service-token"
})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(DeliveryApiTestSupport.SchemaConfig.class)
public abstract class DeliveryApiTestSupport {
    static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    static final Set<String> DISPATCHER = Set.of("delivery:read", "delivery:manage", "delivery:dispatch", "delivery:partners:read",
            "delivery:partners:manage", "delivery:config", "delivery:audit", "delivery:report", "delivery:incident");
    static final Set<String> PARTNER = Set.of("delivery:read", "delivery:partner:self", "delivery:tracking", "delivery:proof", "delivery:incident");
    static final Set<String> RECIPIENT = Set.of("delivery:track:own");

    private static final AtomicLong ORDER_SEQUENCE = new AtomicLong(1_000);

    @TestConfiguration
    public static class SchemaConfig {
        /** Applies the real migrations to H2, skipping filtered (partial) indexes which H2 does not support. */
        @Bean
        org.springframework.beans.factory.InitializingBean deliverySchema(DataSource dataSource) {
            return () -> {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                Integer existing = jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name='delivery'", Integer.class);
                if (existing != null && existing > 0) return;
                for (String file : List.of("V1__delivery_management_schema.sql", "V2__order_link_otp_rules.sql")) {
                    String sql = new String(new ClassPathResource("db/migration/" + file).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    for (String statement : sql.split(";\\s*\\n")) {
                        String trimmed = statement.trim();
                        String withoutComments = trimmed.replaceAll("(?m)^--.*$", "").trim();
                        if (withoutComments.isEmpty()) continue;
                        if (withoutComments.matches("(?is)CREATE\\s+(UNIQUE\\s+)?INDEX.*\\sWHERE\\s.*")) continue;
                        if (withoutComments.matches("(?is)INSERT INTO delivery_configuration.*")) continue;
                        jdbc.execute(withoutComments.replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE"));
                    }
                }
            };
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @MockBean protected DeliveryOutboxPublisher outboxPublisher;
    @MockBean protected DeliveryAuthorizationClient authorizationClient;
    @MockBean protected DeliveryOrderClient orderClient;

    protected final Map<Long, DeliveryOrderClient.OrderSnapshot> orders = new ConcurrentHashMap<>();
    protected UUID tenantA;
    protected UUID tenantB;

    @BeforeEach
    void freshTenantsAndOrderStubs() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        orders.clear();
        reset(orderClient, authorizationClient);
        when(orderClient.findOrder(anyLong())).thenAnswer(call -> Optional.ofNullable(orders.get((Long) call.getArgument(0))));
    }

    protected long newOrder(long recipientUserId, String status) {
        long id = ORDER_SEQUENCE.incrementAndGet();
        orders.put(id, new DeliveryOrderClient.OrderSnapshot(id, recipientUserId, status, new java.math.BigDecimal("49.90")));
        return id;
    }

    protected String token(long userId, UUID tenant, String role, Set<String> permissions) {
        return Jwts.builder().setIssuer("universal-commerce").setAudience("commerce-api")
                .setSubject("user" + userId + "@example.test").setId(UUID.randomUUID().toString())
                .claim("id", userId).claim("tenant_id", tenant.toString()).claim("tenant_role", role)
                .claim("application_code", "DELIVERY_OPS").claim("permissions", List.copyOf(permissions))
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }

    protected String dispatcher(UUID tenant, long userId) { return token(userId, tenant, "DELIVERY_ADMIN", DISPATCHER); }
    protected String partner(UUID tenant, long userId) { return token(userId, tenant, "DELIVERY_PARTNER", PARTNER); }
    protected String recipient(UUID tenant, long userId) { return token(userId, tenant, "DELIVERY_RECIPIENT", RECIPIENT); }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    protected MvcResult call(MockHttpServletRequestBuilder builder, String token, Object body, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = authed(builder, token);
        if (body != null) request.content(body instanceof String s ? s : json.writeValueAsString(body));
        if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
        return mvc.perform(request).andReturn();
    }

    protected MvcResult getJson(String path, String token) throws Exception { return call(get(path), token, null, null); }
    protected MvcResult postJson(String path, String token, Object body) throws Exception { return call(post(path), token, body, UUID.randomUUID().toString()); }

    protected JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }

    protected int status(MvcResult result) { return result.getResponse().getStatus(); }

    protected Map<String, Object> fromOrderRequest(long orderId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("orderId", orderId);
        request.put("businessType", "RETAIL");
        request.put("pickupName", "Warehouse 1");
        request.put("pickupAddress", "1 Depot Road");
        request.put("dropoffName", "Customer");
        request.put("dropoffAddress", "2 Main Street");
        request.put("postalCode", "560001");
        request.put("weightKg", new java.math.BigDecimal("2.5"));
        request.put("itemCount", 2);
        return request;
    }

    /** Creates a zone and an approved, online partner for the user, returning the partner id. */
    protected String onboardPartner(UUID tenant, long partnerUserId, long adminUserId, String zoneCode) throws Exception {
        String admin = dispatcher(tenant, adminUserId);
        String zoneId = jdbc.queryForList("SELECT id::text FROM delivery_zone WHERE tenant_id=? AND code=?", String.class, tenant, zoneCode)
                .stream().findFirst().orElse(null);
        if (zoneId == null) {
            MvcResult zone = postJson("/api/delivery/zones", admin, Map.of("code", zoneCode, "name", "Zone " + zoneCode, "businessType", "BOTH",
                    "postalPrefix", "560", "active", true));
            if (status(zone) != 201) throw new AssertionError("zone: " + zone.getResponse().getContentAsString());
            zoneId = body(zone).get("id").asText();
        }
        String token = partner(tenant, partnerUserId);
        MvcResult profile = call(put("/api/delivery/partners/me"), token, Map.of("displayName", "Partner " + partnerUserId, "vehicleType", "BIKE",
                "capacityKg", new java.math.BigDecimal("30"), "maxActiveDeliveries", 3, "capabilities", List.of(), "zoneIds", List.of(zoneId)), null);
        if (status(profile) != 200) throw new AssertionError("profile: " + profile.getResponse().getContentAsString());
        String partnerId = body(profile).get("id").asText();
        MvcResult review = call(patch("/api/delivery/partners/" + partnerId + "/review"), admin,
                Map.of("verificationStatus", "APPROVED", "documentStatus", "ACCEPTED", "active", true), null);
        if (status(review) != 200) throw new AssertionError("review: " + review.getResponse().getContentAsString());
        MvcResult online = call(put("/api/delivery/partners/me/availability"), token, Map.of("status", "ONLINE"), null);
        if (status(online) != 200) throw new AssertionError("online: " + online.getResponse().getContentAsString());
        return partnerId;
    }

    /** Creates an order-backed delivery, marks it pickup-ready and returns {id, version}. */
    protected JsonNode readyDelivery(UUID tenant, long adminUserId, long recipientUserId) throws Exception {
        String admin = dispatcher(tenant, adminUserId);
        long orderId = newOrder(recipientUserId, "PLACED");
        MvcResult created = postJson("/api/delivery/deliveries/from-order", admin, fromOrderRequest(orderId));
        if (status(created) != 201) throw new AssertionError("create: " + created.getResponse().getContentAsString());
        JsonNode delivery = body(created);
        MvcResult ready = call(put("/api/delivery/deliveries/" + delivery.get("id").asText() + "/readiness"), admin,
                Map.of("pickupReady", true, "expectedVersion", delivery.get("version").asLong()), UUID.randomUUID().toString());
        if (status(ready) != 200) throw new AssertionError("ready: " + ready.getResponse().getContentAsString());
        return body(ready);
    }

    protected JsonNode assignAndAccept(UUID tenant, long adminUserId, long partnerUserId, JsonNode delivery, String partnerId) throws Exception {
        String admin = dispatcher(tenant, adminUserId);
        MvcResult assigned = postJson("/api/delivery/dispatch/deliveries/" + delivery.get("id").asText() + "/assign", admin,
                Map.of("partnerId", partnerId, "expectedVersion", delivery.get("version").asLong()));
        if (status(assigned) != 200) throw new AssertionError("assign: " + assigned.getResponse().getContentAsString());
        String assignmentId = body(assigned).get("assignments").get(0).get("id").asText();
        MvcResult accepted = postJson("/api/delivery/assignments/" + assignmentId + "/response", partner(tenant, partnerUserId), Map.of("accepted", true));
        if (status(accepted) != 200) throw new AssertionError("accept: " + accepted.getResponse().getContentAsString());
        return body(accepted);
    }

    protected MvcResult transition(UUID tenant, String bearer, JsonNode delivery, String to, String reason) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("toStatus", to);
        request.put("expectedVersion", delivery.get("version").asLong());
        if (reason != null) request.put("reason", reason);
        return postJson("/api/delivery/deliveries/" + delivery.get("id").asText() + "/transitions", bearer, request);
    }

    protected JsonNode currentDelivery(String id, String bearer) throws Exception {
        MvcResult result = getJson("/api/delivery/deliveries/" + id, bearer);
        if (status(result) != 200) throw new AssertionError("get: " + result.getResponse().getContentAsString());
        return body(result);
    }
}
