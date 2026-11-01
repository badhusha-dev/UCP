package com.ecommerce.delivery.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class DeliverySecurityAndProofTest extends DeliveryApiTestSupport {
    private record Trip(String deliveryId, String partnerToken, String partnerId, JsonNode atDropoff) {}

    private String zone() { return "Z-" + UUID.randomUUID().toString().substring(0, 8); }

    /** Drives a delivery for recipient 7 up to ARRIVED_AT_DROPOFF with partner {@code partnerUser}. */
    private Trip tripToDropoff(UUID tenant, long adminUser, long partnerUser) throws Exception {
        String partnerId = onboardPartner(tenant, partnerUser, adminUser, zone());
        JsonNode ready = readyDelivery(tenant, adminUser, 7);
        JsonNode state = assignAndAccept(tenant, adminUser, partnerUser, ready, partnerId);
        String partner = partner(tenant, partnerUser);
        for (String next : List.of("ARRIVED_AT_PICKUP", "PICKED_UP", "IN_TRANSIT", "ARRIVED_AT_DROPOFF")) {
            MvcResult moved = transition(tenant, partner, state, next, null);
            assertThat(status(moved)).as(next).isEqualTo(200);
            state = body(moved);
        }
        return new Trip(ready.get("id").asText(), partner, partnerId, state);
    }

    private String issueCode(UUID tenant, String deliveryId, long recipientUser) throws Exception {
        MvcResult result = postJson("/api/delivery/my/deliveries/" + deliveryId + "/delivery-code", recipient(tenant, recipientUser), Map.of());
        assertThat(status(result)).isEqualTo(200);
        return body(result).get("code").asText();
    }

    private MvcResult proof(String partnerToken, String deliveryId, Map<String, Object> body) throws Exception {
        return call(post("/api/delivery/deliveries/" + deliveryId + "/proof"), partnerToken, body, UUID.randomUUID().toString());
    }

    // ---- tenant isolation -------------------------------------------------------------------------------------

    @Test
    void anotherTenantCannotReadChangeOrDispatchADeliveryEvenWithForgedHeaders() throws Exception {
        JsonNode delivery = readyDelivery(tenantA, 10, 7);
        String id = delivery.get("id").asText();
        String foreign = dispatcher(tenantB, 20);

        assertThat(status(getJson("/api/delivery/deliveries/" + id, foreign))).isEqualTo(404);
        assertThat(status(transition(tenantB, foreign, delivery, "CANCELLED", "cross-tenant"))).isEqualTo(404);
        assertThat(status(postJson("/api/delivery/dispatch/deliveries/" + id + "/assign", foreign,
                Map.of("partnerId", UUID.randomUUID().toString(), "expectedVersion", delivery.get("version").asLong())))).isEqualTo(404);
        assertThat(status(proof(partner(tenantB, 900), id, Map.of("method", "OTP", "otp", "123456")))).isEqualTo(404);
        assertThat(body(getJson("/api/delivery/deliveries", foreign))).isEmpty();

        // Forged tenant headers and query parameters carry no authority; the tenant comes only from the verified token.
        MvcResult forged = mvc.perform(get("/api/delivery/deliveries/" + id + "?tenantId=" + tenantA)
                .header("Authorization", "Bearer " + foreign).header("X-Tenant-ID", tenantA.toString())).andReturn();
        assertThat(status(forged)).isEqualTo(404);
        MvcResult forgedList = mvc.perform(get("/api/delivery/deliveries")
                .header("Authorization", "Bearer " + foreign).header("X-Tenant-ID", tenantA.toString())).andReturn();
        assertThat(body(forgedList)).isEmpty();
        assertThat(currentDelivery(id, dispatcher(tenantA, 10)).get("status").asText()).isEqualTo("READY_FOR_DISPATCH");
    }

    @Test
    void forgedStaleRevokedAndUnavailableCredentialsAreRejected() throws Exception {
        String valid = dispatcher(tenantA, 10);
        assertThat(status(getJson("/api/delivery/deliveries", valid))).isEqualTo(200);

        assertThat(mvc.perform(get("/api/delivery/deliveries")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(status(getJson("/api/delivery/deliveries", valid + "x"))).isEqualTo(401);     // tampered signature

        String expired = Jwts.builder().setIssuer("universal-commerce").setAudience("commerce-api").setSubject("a@example.test")
                .setId(UUID.randomUUID().toString()).claim("id", 10L).claim("tenant_id", tenantA.toString()).claim("tenant_role", "DELIVERY_ADMIN")
                .claim("application_code", "DELIVERY_OPS").claim("permissions", List.of("delivery:read"))
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        assertThat(status(getJson("/api/delivery/deliveries", expired))).isEqualTo(401);

        String otherApp = Jwts.builder().setIssuer("universal-commerce").setAudience("commerce-api").setSubject("a@example.test")
                .setId(UUID.randomUUID().toString()).claim("id", 10L).claim("tenant_id", tenantA.toString()).claim("tenant_role", "WAREHOUSE")
                .claim("application_code", "INVENTORY").claim("permissions", List.of("delivery:read"))
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        assertThat(status(getJson("/api/delivery/deliveries", otherApp))).isEqualTo(401);

        // Identity service says the grant was revoked / the session is stale / it is down.
        doThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "revoked", null, null, null)).when(authorizationClient).requireCurrentGrant(valid);
        assertThat(status(getJson("/api/delivery/deliveries", valid))).isEqualTo(403);
        doThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "stale", null, null, null)).when(authorizationClient).requireCurrentGrant(valid);
        assertThat(status(getJson("/api/delivery/deliveries", valid))).isEqualTo(401);
        doThrow(new com.ecommerce.delivery.security.DeliveryAuthorizationClient.AuthorizationUnavailableException()).when(authorizationClient).requireCurrentGrant(valid);
        assertThat(status(getJson("/api/delivery/deliveries", valid))).isEqualTo(503);
    }

    // ---- partner ownership ------------------------------------------------------------------------------------

    @Test
    void partnersSeeAndChangeOnlyTheirOwnProfileOffersAndAssignments() throws Exception {
        Trip first = tripToDropoff(tenantA, 10, 601);
        String otherPartnerId = onboardPartner(tenantA, 602, 10, zone());
        String second = partner(tenantA, 602);

        assertThat(status(getJson("/api/delivery/deliveries/" + first.deliveryId(), second))).isEqualTo(404);
        assertThat(body(getJson("/api/delivery/deliveries", second))).isEmpty();
        assertThat(status(transition(tenantA, second, first.atDropoff(), "DELIVERED", null))).isEqualTo(404);
        assertThat(status(proof(second, first.deliveryId(), Map.of("method", "OTP", "otp", "123456")))).isEqualTo(404);
        assertThat(body(getJson("/api/delivery/assignments/mine", second))).isEmpty();
        assertThat(body(getJson("/api/delivery/assignments/mine", first.partnerToken()))).hasSize(1);
        assertThat(body(getJson("/api/delivery/partners/me", second)).get("id").asText()).isEqualTo(otherPartnerId);
        JsonNode visiblePartners = body(getJson("/api/delivery/partners", second));
        assertThat(visiblePartners).hasSize(1);
        assertThat(visiblePartners.get(0).get("id").asText()).isEqualTo(otherPartnerId);

        // Another partner cannot answer someone else's offer.
        String firstAssignment = jdbc.queryForObject("SELECT id::text FROM delivery_assignment WHERE tenant_id=? AND partner_id=?::uuid", String.class, tenantA, first.partnerId());
        assertThat(status(postJson("/api/delivery/assignments/" + firstAssignment + "/response", second, Map.of("accepted", false, "reason", "x")))).isEqualTo(404);
    }

    @Test
    void actorsMayOnlyRequestTransitionsTheirRoleOwns() throws Exception {
        String partnerId = onboardPartner(tenantA, 603, 10, zone());
        JsonNode ready = readyDelivery(tenantA, 10, 7);
        JsonNode assigned = assignAndAccept(tenantA, 10, 603, ready, partnerId);
        String partner = partner(tenantA, 603);
        String admin = dispatcher(tenantA, 10);

        assertThat(status(transition(tenantA, partner, assigned, "CANCELLED", "no"))).isEqualTo(403);          // partners cannot cancel
        assertThat(status(transition(tenantA, partner, assigned, "READY_FOR_DISPATCH", "no"))).isEqualTo(403);
        assertThat(status(transition(tenantA, admin, assigned, "ARRIVED_AT_PICKUP", null))).isEqualTo(403);    // dispatchers cannot fake field events
        assertThat(status(transition(tenantA, admin, assigned, "ASSIGNED", null))).isEqualTo(409);
        assertThat(status(postJson("/api/delivery/deliveries/from-order", partner, fromOrderRequest(newOrder(7, "PLACED"))))).isEqualTo(403);
        assertThat(status(postJson("/api/delivery/dispatch/deliveries/" + ready.get("id").asText() + "/assign", partner,
                Map.of("partnerId", partnerId, "expectedVersion", 1)))).isEqualTo(403);
        assertThat(status(transition(tenantA, recipient(tenantA, 7), assigned, "CANCELLED", "x"))).isEqualTo(403);
    }

    @Test
    void invalidTransitionsAreRejectedServerSide() throws Exception {
        String admin = dispatcher(tenantA, 10);
        MvcResult created = postJson("/api/delivery/deliveries/from-order", admin, fromOrderRequest(newOrder(7, "PLACED")));
        JsonNode delivery = body(created);
        assertThat(status(transition(tenantA, admin, delivery, "RETURNED", "x"))).isEqualTo(409);
        assertThat(status(transition(tenantA, admin, delivery, "READY_FOR_DISPATCH", null))).isEqualTo(200);
        assertThat(status(postJson("/api/delivery/deliveries/" + delivery.get("id").asText() + "/transitions", admin,
                Map.of("toStatus", "FLYING", "expectedVersion", 1)))).isEqualTo(400);
        // Stale version -> conflict, nothing changes.
        assertThat(status(transition(tenantA, admin, delivery, "CANCELLED", "stale"))).isEqualTo(409);
        assertThat(currentDelivery(delivery.get("id").asText(), admin).get("status").asText()).isEqualTo("READY_FOR_DISPATCH");
    }

    // ---- idempotency and concurrency ---------------------------------------------------------------------------

    @Test
    void duplicateRequestsWithTheSameKeyHaveOneEffectAndMismatchedPayloadsConflict() throws Exception {
        String admin = dispatcher(tenantA, 10);
        long order = newOrder(7, "PLACED");
        String key = UUID.randomUUID().toString();
        MvcResult first = call(post("/api/delivery/deliveries/from-order"), admin, fromOrderRequest(order), key);
        MvcResult replay = call(post("/api/delivery/deliveries/from-order"), admin, fromOrderRequest(order), key);
        assertThat(status(first)).isEqualTo(201);
        assertThat(body(replay).get("id").asText()).isEqualTo(body(first).get("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery WHERE order_id=?", Integer.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_status_history WHERE tenant_id=?", Integer.class, tenantA)).isEqualTo(1);

        Map<String, Object> different = new LinkedHashMap<>(fromOrderRequest(order));
        different.put("itemCount", 9);
        assertThat(status(call(post("/api/delivery/deliveries/from-order"), admin, different, key))).isEqualTo(409);
        assertThat(status(call(post("/api/delivery/deliveries/from-order"), admin, fromOrderRequest(newOrder(7, "PLACED")), null))).isEqualTo(400); // key required

        // Replayed transition does not add history twice.
        JsonNode created = body(first);
        String transitionKey = UUID.randomUUID().toString();
        Map<String, Object> toReady = Map.of("toStatus", "READY_FOR_DISPATCH", "expectedVersion", created.get("version").asLong());
        String path = "/api/delivery/deliveries/" + created.get("id").asText() + "/transitions";
        assertThat(status(call(post(path), admin, toReady, transitionKey))).isEqualTo(200);
        assertThat(status(call(post(path), admin, toReady, transitionKey))).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_status_history WHERE tenant_id=? AND to_status='READY_FOR_DISPATCH'", Integer.class, tenantA)).isEqualTo(1);
    }

    @Test
    void parallelDuplicateCreatesProduceExactlyOneDelivery() throws Exception {
        String admin = dispatcher(tenantA, 10);
        long order = newOrder(7, "PLACED");
        String key = UUID.randomUUID().toString();
        List<Integer> statuses = runInParallel(5, i -> status(call(post("/api/delivery/deliveries/from-order"), admin, fromOrderRequest(order), key)));
        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).contains(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery WHERE order_id=?", Integer.class, order)).isEqualTo(1);
    }

    @Test
    void concurrentAssignmentsToDifferentPartnersLetExactlyOneWin() throws Exception {
        String partnerOne = onboardPartner(tenantA, 611, 10, zone());
        String partnerTwo = onboardPartner(tenantA, 612, 11, zone());
        JsonNode ready = readyDelivery(tenantA, 10, 7);
        long version = ready.get("version").asLong();
        String path = "/api/delivery/dispatch/deliveries/" + ready.get("id").asText() + "/assign";
        List<String> partners = List.of(partnerOne, partnerTwo);
        List<Integer> statuses = runInParallel(2, i -> {
            try {
                return status(call(post(path), dispatcher(tenantA, 10 + i), Map.of("partnerId", partners.get(i), "expectedVersion", version), UUID.randomUUID().toString()));
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_assignment WHERE tenant_id=? AND delivery_id=?::uuid AND active=TRUE", Integer.class, tenantA, ready.get("id").asText())).isEqualTo(1);
    }

    private interface IndexedTask<T> { T run(int index) throws Exception; }

    private <T> List<T> runInParallel(int count, IndexedTask<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> { start.await(); return task.run(index); }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(60, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- proof of delivery -------------------------------------------------------------------------------------

    @Test
    void clientSideSuccessFlagsAndLegacyMethodsAreNeverAccepted() throws Exception {
        Trip trip = tripToDropoff(tenantA, 10, 621);
        issueCode(tenantA, trip.deliveryId(), 7);

        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), Map.of("method", "PIN_CONFIRMED")))).isEqualTo(400);
        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), Map.of("method", "RECIPIENT_NAME", "recipientName", "Me")))).isEqualTo(400);
        Map<String, Object> forged = new LinkedHashMap<>(Map.of("method", "OTP", "verified", true, "otpVerified", true));
        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), forged))).isEqualTo(400);   // OTP value is missing
        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), Map.of("method", "OTP", "otp", "12ab56")))).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_proof WHERE delivery_id=?::uuid", Integer.class, trip.deliveryId())).isZero();
        assertThat(status(transition(tenantA, trip.partnerToken(), trip.atDropoff(), "DELIVERED", null))).isEqualTo(409);
    }

    @Test
    void wrongExpiredAndExhaustedCodesFailClosedEvenIfTheRightCodeFollows() throws Exception {
        Trip trip = tripToDropoff(tenantA, 10, 622);
        String code = issueCode(tenantA, trip.deliveryId(), 7);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), Map.of("method", "OTP", "otp", wrong)))).isEqualTo(403);
        }
        // Failed attempts were committed even though the requests were rejected, so the limit really bites.
        assertThat(jdbc.queryForObject("SELECT attempts FROM delivery_otp WHERE delivery_id=?::uuid", Integer.class, trip.deliveryId())).isEqualTo(5);
        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), Map.of("method", "OTP", "otp", code)))).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_proof WHERE delivery_id=?::uuid", Integer.class, trip.deliveryId())).isZero();

        // Re-issuing is the recipient's decision and resets the counter; an expired code is still refused.
        String fresh = issueCode(tenantA, trip.deliveryId(), 7);
        jdbc.update("UPDATE delivery_otp SET expires_at=TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00+00:00' WHERE delivery_id=?::uuid", trip.deliveryId());
        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), Map.of("method", "OTP", "otp", fresh)))).isEqualTo(403);
        assertThat(status(transition(tenantA, trip.partnerToken(), trip.atDropoff(), "DELIVERED", null))).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_audit_event WHERE tenant_id=? AND action='PROOF_OTP_REJECTED'", Integer.class, tenantA)).isGreaterThanOrEqualTo(5);
    }

    @Test
    void aCodeWorksOnceAndCannotBeUsedAnotherWayOrByAnotherDelivery() throws Exception {
        Trip trip = tripToDropoff(tenantA, 10, 623);
        Trip other = tripToDropoff(tenantA, 10, 624);
        String code = issueCode(tenantA, trip.deliveryId(), 7);
        issueCode(tenantA, other.deliveryId(), 7);

        // A code is bound to its delivery: it does not verify another one.
        assertThat(status(proof(other.partnerToken(), other.deliveryId(), Map.of("method", "OTP", "otp", code)))).isIn(403);
        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), Map.of("method", "OTP", "otp", code)))).isEqualTo(200);
        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), Map.of("method", "OTP", "otp", code)))).isEqualTo(409); // single use
        assertThat(status(postJson("/api/delivery/my/deliveries/" + trip.deliveryId() + "/delivery-code", recipient(tenantA, 7), Map.of()))).isEqualTo(409);
    }

    @Test
    void proofOnlyAtDropoffAndOnlyMethodsThePublishedRulesEnable() throws Exception {
        String partnerId = onboardPartner(tenantA, 625, 10, zone());
        JsonNode ready = readyDelivery(tenantA, 10, 7);
        JsonNode assigned = assignAndAccept(tenantA, 10, 625, ready, partnerId);
        String partner = partner(tenantA, 625);
        assertThat(status(proof(partner, ready.get("id").asText(), Map.of("method", "OTP", "otp", "123456")))).isEqualTo(409);   // not at drop-off yet

        Trip trip = tripToDropoff(tenantA, 10, 626);
        Map<String, Object> photo = Map.of("method", "PHOTO_REFERENCE", "evidenceReference", "evidence/2026/photo-123.jpg");
        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), photo))).isEqualTo(422);                // OTP-only by default

        String admin = dispatcher(tenantA, 10);
        call(put("/api/delivery/rules/draft"), admin, Map.of("rules", Map.of("proof.accepted_methods", List.of("OTP", "PHOTO_REFERENCE"))), null);
        assertThat(status(postJson("/api/delivery/rules/draft/publish", admin, Map.of()))).isEqualTo(200);
        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), Map.of("method", "PHOTO_REFERENCE", "evidenceReference", "x")))).isEqualTo(400); // not a usable reference
        assertThat(status(proof(trip.partnerToken(), trip.deliveryId(), photo))).isEqualTo(200);
        assertThat(status(transition(tenantA, trip.partnerToken(), currentDelivery(trip.deliveryId(), trip.partnerToken()), "DELIVERED", null))).isEqualTo(200);
        assertThat(assigned.get("status").asText()).isEqualTo("ASSIGNED");
    }

    // ---- recipient tracking ------------------------------------------------------------------------------------

    @Test
    void customersTrackOnlyTheirOwnEligibleDeliveries() throws Exception {
        String admin = dispatcher(tenantA, 10);
        JsonNode draft = body(postJson("/api/delivery/deliveries/from-order", admin, fromOrderRequest(newOrder(7, "PLACED"))));
        JsonNode ready = readyDelivery(tenantA, 10, 7);
        String readyId = ready.get("id").asText();

        assertThat(status(getJson("/api/delivery/my/deliveries/" + draft.get("id").asText(), recipient(tenantA, 7)))).isEqualTo(404);   // still a draft
        JsonNode mine = body(getJson("/api/delivery/my/deliveries", recipient(tenantA, 7)));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("id").asText()).isEqualTo(readyId);

        JsonNode view = body(getJson("/api/delivery/my/deliveries/" + readyId, recipient(tenantA, 7)));
        assertThat(view.has("pickupAddress")).isFalse();
        assertThat(view.has("assignedPartnerId")).isFalse();
        assertThat(view.has("history")).isTrue();

        assertThat(status(getJson("/api/delivery/my/deliveries/" + readyId, recipient(tenantA, 8)))).isEqualTo(404);          // another customer
        assertThat(body(getJson("/api/delivery/my/deliveries", recipient(tenantA, 8)))).isEmpty();
        assertThat(status(getJson("/api/delivery/my/deliveries/" + readyId, recipient(tenantB, 7)))).isEqualTo(404);          // same user id, other tenant
        assertThat(status(getJson("/api/delivery/my/deliveries", partner(tenantA, 501)))).isEqualTo(403);                     // not a recipient grant
        assertThat(status(getJson("/api/delivery/deliveries/" + readyId, recipient(tenantA, 7)))).isEqualTo(403);              // no ops access
        assertThat(status(postJson("/api/delivery/my/deliveries/" + readyId + "/delivery-code", recipient(tenantA, 7), Map.of()))).isEqualTo(409); // not picked up
        assertThat(status(postJson("/api/delivery/my/deliveries/" + readyId + "/delivery-code", recipient(tenantA, 8), Map.of()))).isEqualTo(404);
    }

    // ---- rule registry -----------------------------------------------------------------------------------------

    @Test
    void rulesAreDraftedValidatedPublishedAndRolledBackWithoutExecutableContent() throws Exception {
        String admin = dispatcher(tenantA, 10);
        assertThat(status(getJson("/api/delivery/rules", token(30, tenantA, "DELIVERY_ANALYST", Set.of("delivery:read"))))).isEqualTo(403);

        call(put("/api/delivery/rules/draft"), admin, Map.of("rules", Map.of("proof.otp_ttl_minutes", 30, "run.script", "System.exit(1)")), null);
        JsonNode invalid = body(postJson("/api/delivery/rules/draft/validate", admin, Map.of()));
        assertThat(invalid.get("valid").asBoolean()).isFalse();
        assertThat(invalid.get("errors").toString()).contains("Unknown rule 'run.script'");
        assertThat(status(postJson("/api/delivery/rules/draft/publish", admin, Map.of()))).isEqualTo(422);

        call(put("/api/delivery/rules/draft"), admin, Map.of("rules", Map.of("proof.otp_ttl_minutes", 100000)), null);
        assertThat(body(postJson("/api/delivery/rules/draft/validate", admin, Map.of())).get("valid").asBoolean()).isFalse();
        call(put("/api/delivery/rules/draft"), admin, Map.of("rules", Map.of("proof.accepted_methods", List.of("TELEPATHY"))), null);
        assertThat(body(postJson("/api/delivery/rules/draft/validate", admin, Map.of())).get("valid").asBoolean()).isFalse();

        call(put("/api/delivery/rules/draft"), admin, Map.of("rules", Map.of("proof.otp_ttl_minutes", 30)), null);
        assertThat(status(postJson("/api/delivery/rules/draft/publish", admin, Map.of()))).isEqualTo(200);
        call(put("/api/delivery/rules/draft"), admin, Map.of("rules", Map.of("proof.otp_ttl_minutes", 60)), null);
        assertThat(status(postJson("/api/delivery/rules/draft/publish", admin, Map.of()))).isEqualTo(200);
        assertThat(body(getJson("/api/delivery/rules", admin)).get("effective").get("proof.otp_ttl_minutes").asInt()).isEqualTo(60);

        assertThat(status(postJson("/api/delivery/rules/rollback", admin, Map.of("version", 1)))).isEqualTo(200);
        JsonNode overview = body(getJson("/api/delivery/rules", admin));
        assertThat(overview.get("effective").get("proof.otp_ttl_minutes").asInt()).isEqualTo(30);
        assertThat(overview.get("published").get("version").asInt()).isEqualTo(3);                       // history is appended, never rewritten
        assertThat(overview.get("published").get("rollbackOf").asInt()).isEqualTo(1);
        assertThat(status(postJson("/api/delivery/rules/rollback", admin, Map.of("version", 99)))).isEqualTo(404);

        // Rules are tenant-scoped.
        assertThat(body(getJson("/api/delivery/rules", dispatcher(tenantB, 20))).get("effective").get("proof.otp_ttl_minutes").asInt()).isEqualTo(120);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_audit_event WHERE tenant_id=? AND action IN ('RULES_PUBLISH','RULES_ROLLBACK')", Integer.class, tenantA)).isEqualTo(3);
    }
}
