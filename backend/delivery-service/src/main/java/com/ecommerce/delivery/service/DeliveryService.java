package com.ecommerce.delivery.service;

import com.ecommerce.delivery.controller.DeliveryCommands;
import com.ecommerce.delivery.order.DeliveryOrderClient;
import com.ecommerce.delivery.rules.DeliveryRuleRegistry;
import com.ecommerce.delivery.rules.DeliveryRules;
import com.ecommerce.delivery.security.DeliveryPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DeliveryService {
    /** Delivery states Order Service cares about; it decides what, if anything, each one changes on the order. */
    private static final Set<String> ORDER_SYNC_STATUSES = Set.of("PICKED_UP", "IN_TRANSIT", "DELIVERED", "DELIVERY_FAILED", "RETURNED", "CANCELLED");
    private static final int MAX_CODE_ISSUES = 5;
    private static final Set<String> TRACKABLE_STATUSES = Set.of("READY_FOR_DISPATCH", "ASSIGNED", "ARRIVED_AT_PICKUP", "PICKED_UP", "IN_TRANSIT",
            "ARRIVED_AT_DROPOFF", "DELIVERED", "CANCELLED", "PICKUP_FAILED", "DELIVERY_FAILED", "RETURN_TO_ORIGIN", "RETURNED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DeliveryRuleRegistry rules;
    private final DeliveryProofCodes codes;
    private final DeliveryOrderClient orders;
    private final DeliveryOtpAttempts otpAttempts;

    public DeliveryService(JdbcTemplate jdbc, ObjectMapper mapper, DeliveryRuleRegistry rules, DeliveryProofCodes codes,
                           DeliveryOrderClient orders, DeliveryOtpAttempts otpAttempts) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.rules = rules;
        this.codes = codes;
        this.orders = orders;
        this.otpAttempts = otpAttempts;
    }

    public Map<String, Object> dashboard(DeliveryPrincipal actor) {
        requirePermission(actor, "delivery:read");
        UUID tenant = actor.tenantId();
        boolean partner = isPartner(actor);
        Map<String, Object> summary = jdbc.queryForMap("""
                SELECT count(*) total,
                       count(*) FILTER (WHERE status='READY_FOR_DISPATCH' AND pickup_ready) ready,
                       count(*) FILTER (WHERE status IN ('ASSIGNED','ARRIVED_AT_PICKUP','PICKED_UP','IN_TRANSIT','ARRIVED_AT_DROPOFF')) active,
                       count(*) FILTER (WHERE status='DELIVERED' AND delivered_at >= CURRENT_DATE) delivered_today,
                       count(*) FILTER (WHERE status='DELIVERY_FAILED' OR status='PICKUP_FAILED') exceptions
                FROM delivery d WHERE tenant_id=? AND (?=FALSE OR EXISTS (
                    SELECT 1 FROM delivery_partner_profile own WHERE own.id=d.assigned_partner_id
                    AND own.tenant_id=d.tenant_id AND own.user_id=?))
                """, tenant, partner, actor.userId());
        Map<String, Object> result = camelize(summary);
        result.put("availablePartners", partner ? jdbc.queryForObject("""
                SELECT count(*) FROM delivery_partner_profile
                WHERE tenant_id=? AND user_id=? AND active=TRUE AND verification_status='APPROVED' AND availability_status='ONLINE'
                """, Integer.class, tenant, actor.userId()) : jdbc.queryForObject("""
                SELECT count(*) FROM delivery_partner_profile
                WHERE tenant_id=? AND active=TRUE AND verification_status='APPROVED' AND availability_status='ONLINE'
                """, Integer.class, tenant));
        result.put("pendingIncidents", jdbc.queryForObject("""
                SELECT count(*) FROM delivery_incident i JOIN delivery d ON d.id=i.delivery_id AND d.tenant_id=i.tenant_id
                WHERE i.tenant_id=? AND i.status<>'RESOLVED' AND (?=FALSE OR EXISTS (
                    SELECT 1 FROM delivery_partner_profile own WHERE own.id=d.assigned_partner_id
                    AND own.tenant_id=d.tenant_id AND own.user_id=?))
                """, Integer.class, tenant, partner, actor.userId()));
        result.put("recentDeliveries", listDeliveries(actor, null, 8));
        return result;
    }

    public List<Map<String, Object>> listDeliveries(DeliveryPrincipal actor, String requestedStatus, int requestedLimit) {
        requirePermission(actor, "delivery:read");
        String status = normalizeOptional(requestedStatus);
        if (status != null && !DeliveryLifecycle.states().contains(status)) badRequest("Unknown delivery status.");
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        boolean partner = isPartner(actor);
        String sql = """
                SELECT d.id::text AS "id",d.order_reference AS "orderReference",d.business_type AS "businessType",
                       d.pickup_name AS "pickupName",d.pickup_address AS "pickupAddress",d.dropoff_name AS "dropoffName",
                       d.dropoff_address AS "dropoffAddress",d.postal_code AS "postalCode",d.required_capability AS "requiredCapability",
                       d.weight_kg AS "weightKg",d.item_count AS "itemCount",d.scheduled_for AS "scheduledFor",
                       d.pickup_ready AS "pickupReady",d.status,d.assigned_partner_id::text AS "assignedPartnerId",
                       p.display_name AS "partnerName",a.status AS "assignmentStatus",d.version,d.created_at AS "createdAt",d.updated_at AS "updatedAt",d.delivered_at AS "deliveredAt"
                FROM delivery d LEFT JOIN delivery_partner_profile p ON p.id=d.assigned_partner_id AND p.tenant_id=d.tenant_id
                LEFT JOIN delivery_assignment a ON a.tenant_id=d.tenant_id AND a.delivery_id=d.id AND a.active=TRUE
                WHERE d.tenant_id=? AND (CAST(? AS VARCHAR) IS NULL OR d.status=?)
                """ + (partner ? " AND EXISTS (SELECT 1 FROM delivery_partner_profile own WHERE own.id=d.assigned_partner_id AND own.tenant_id=d.tenant_id AND own.user_id=?) " : "")
                + " ORDER BY d.created_at DESC LIMIT ?";
        if (partner) return jdbc.queryForList(sql, actor.tenantId(), status, status, actor.userId(), limit);
        return jdbc.queryForList(sql, actor.tenantId(), status, status, limit);
    }

    public Map<String, Object> getDelivery(DeliveryPrincipal actor, UUID id) {
        requirePermission(actor, "delivery:read");
        Map<String, Object> delivery = deliveryRow(actor.tenantId(), id);
        assertResourceOwnership(actor, delivery);
        delivery.put("history", jdbc.queryForList("""
                SELECT from_status AS "fromStatus",to_status AS "toStatus",reason,actor_email AS "actorEmail",
                       actor_role AS "actorRole",created_at AS "createdAt"
                FROM delivery_status_history WHERE tenant_id=? AND delivery_id=? ORDER BY created_at,id
                """, actor.tenantId(), id));
        delivery.put("assignments", jdbc.queryForList("""
                SELECT a.id::text AS "id",p.display_name AS "partnerName",p.email AS "partnerEmail",
                       a.status,a.reason,a.offered_at AS "offeredAt",a.responded_at AS "respondedAt"
                FROM delivery_assignment a JOIN delivery_partner_profile p ON p.id=a.partner_id AND p.tenant_id=a.tenant_id
                WHERE a.tenant_id=? AND a.delivery_id=? ORDER BY a.offered_at
                """, actor.tenantId(), id));
        delivery.put("proof", jdbc.query("""
                SELECT method,recipient_name AS "recipientName",evidence_reference AS "evidenceReference",note,
                       captured_by AS "capturedBy",captured_at AS "capturedAt"
                FROM delivery_proof WHERE tenant_id=? AND delivery_id=?
                """, rs -> rs.next() ? mapCurrentRow(rs) : null, actor.tenantId(), id));
        if (actor.hasPermission("delivery:tracking")) {
            delivery.put("latestLocation", jdbc.query("""
                    SELECT latitude,longitude,accuracy_m AS "accuracyM",recorded_at AS "recordedAt"
                    FROM delivery_location_update WHERE tenant_id=? AND delivery_id=?
                    ORDER BY recorded_at DESC LIMIT 1
                    """, rs -> rs.next() ? mapCurrentRow(rs) : null, actor.tenantId(), id));
        }
        return delivery;
    }

    @Transactional
    public Map<String, Object> createDelivery(DeliveryPrincipal actor, DeliveryCommands.CreateDelivery request, String idempotencyKey) {
        requirePermission(actor, "delivery:manage");
        String key = requireIdempotencyKey(idempotencyKey);
        String payloadHash = hash(request);
        Optional<UUID> replay = replay(actor.tenantId(), key, "DELIVERY_CREATE", payloadHash);
        if (replay.isPresent()) return getDelivery(actor, replay.get());
        String orderReference = request.orderReference().trim();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO delivery(id,tenant_id,order_reference,recipient_user_id,business_type,pickup_name,pickup_address,
                        dropoff_name,dropoff_address,postal_code,required_capability,weight_kg,item_count,scheduled_for,created_by)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, id, actor.tenantId(), orderReference, request.recipientUserId(), request.businessType(),
                    clean(request.pickupName()), clean(request.pickupAddress()), clean(request.dropoffName()), clean(request.dropoffAddress()),
                    request.postalCode().trim().toUpperCase(Locale.ROOT), normalizeOptional(request.requiredCapability()),
                    request.weightKg(), request.itemCount(), request.scheduledFor(), actor.userId());
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A delivery already exists for this tenant and order reference.");
        }
        insertHistory(actor, id, null, "CREATED", "Delivery created from external order reference.", key);
        audit(actor, "DELIVERY_CREATE", "DELIVERY", id.toString(), Map.of("orderReference", orderReference, "businessType", request.businessType()));
        enqueueEvent(actor.tenantId(), id, "DELIVERY_CREATED", Map.of("orderReference", orderReference, "businessType", request.businessType()));
        saveIdempotency(actor.tenantId(), key, "DELIVERY_CREATE", payloadHash, id);
        return getDelivery(actor, id);
    }

    @Transactional
    public Map<String, Object> setPickupReadiness(DeliveryPrincipal actor, UUID id, DeliveryCommands.Readiness request, String idempotencyKey) {
        requirePermission(actor, "delivery:manage");
        String key = requireIdempotencyKey(idempotencyKey);
        String hash = hash(request);
        Optional<UUID> replay = replay(actor.tenantId(), key, "DELIVERY_READINESS", hash);
        if (replay.isPresent()) return getDelivery(actor, replay.get());
        Map<String, Object> row = deliveryRow(actor.tenantId(), id);
        String from = String.valueOf(row.get("status"));
        String to = Boolean.TRUE.equals(request.pickupReady()) ? "READY_FOR_DISPATCH" : "CREATED";
        if (!Set.of("CREATED", "READY_FOR_DISPATCH").contains(from)) conflict("Pickup readiness cannot change after assignment.");
        if (from.equals(to) && Boolean.valueOf(String.valueOf(row.get("pickupReady"))).equals(request.pickupReady())) {
            saveIdempotency(actor.tenantId(), key, "DELIVERY_READINESS", hash, id);
            return getDelivery(actor, id);
        }
        long changed = jdbc.update("""
                UPDATE delivery SET pickup_ready=?,status=?,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND version=? AND status IN ('CREATED','READY_FOR_DISPATCH')
                """, request.pickupReady(), to, actor.tenantId(), id, request.expectedVersion());
        if (changed == 0) conflict("Delivery changed concurrently. Refresh it and try again.");
        insertHistory(actor, id, from, to, "Pickup readiness updated.", key);
        audit(actor, "PICKUP_READINESS", "DELIVERY", id.toString(), Map.of("pickupReady", request.pickupReady()));
        enqueueEvent(actor.tenantId(), id, "DELIVERY_STATUS_CHANGED", Map.of("fromStatus", from, "toStatus", to));
        saveIdempotency(actor.tenantId(), key, "DELIVERY_READINESS", hash, id);
        return getDelivery(actor, id);
    }

    @Transactional
    public Map<String, Object> transition(DeliveryPrincipal actor, UUID id, DeliveryCommands.Transition request, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        String hash = hash(request);
        Optional<UUID> replay = replay(actor.tenantId(), key, "DELIVERY_TRANSITION", hash);
        if (replay.isPresent()) return getDelivery(actor, replay.get());
        Map<String, Object> row = deliveryRow(actor.tenantId(), id);
        assertResourceOwnership(actor, row);
        String from = String.valueOf(row.get("status"));
        String to = request.toStatus().trim().toUpperCase(Locale.ROOT);
        if (!DeliveryLifecycle.allows(from, to)) conflict("Invalid Delivery transition: " + from + " → " + to + ".");
        if ("ASSIGNED".equals(to)) badRequest("Use the dispatch assignment operation to assign a partner.");
        if (DeliveryLifecycle.requiresReason(to) && !hasText(request.reason())) badRequest("A reason is required for this transition.");
        requireTransitionPermission(actor, to);
        if (!DeliveryLifecycle.actorMayTarget(isPartner(actor), to)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This role cannot move a delivery to " + to + ".");
        }
        if ("DELIVERED".equals(to)) {
            Integer proofCount = jdbc.queryForObject("SELECT count(*) FROM delivery_proof WHERE tenant_id=? AND delivery_id=? AND verified=TRUE", Integer.class, actor.tenantId(), id);
            if (proofCount == null || proofCount == 0) conflict("Record verified proof of delivery before completing the delivery.");
        }
        if (Set.of("ARRIVED_AT_PICKUP", "PICKED_UP", "IN_TRANSIT", "ARRIVED_AT_DROPOFF", "DELIVERED", "DELIVERY_FAILED", "RETURN_TO_ORIGIN", "RETURNED").contains(to)
                && "ASSIGNED".equals(from)) ensureAcceptedAssignment(actor.tenantId(), id);
        long changed = jdbc.update("""
                UPDATE delivery SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP,
                    delivered_at=CASE WHEN ?='DELIVERED' THEN CURRENT_TIMESTAMP ELSE delivered_at END,
                    cancel_reason=CASE WHEN ?='CANCELLED' THEN ? ELSE cancel_reason END,
                    assigned_partner_id=CASE WHEN ?='READY_FOR_DISPATCH' THEN NULL ELSE assigned_partner_id END
                WHERE tenant_id=? AND id=? AND version=? AND status=?
                """, to, to, to, request.reason(), to, actor.tenantId(), id, request.expectedVersion(), from);
        if (changed == 0) conflict("Delivery changed concurrently. Refresh it and try again.");
        if ("READY_FOR_DISPATCH".equals(to)) closeActiveAssignment(actor.tenantId(), id, "REASSIGNED", request.reason());
        if (Set.of("DELIVERED", "CANCELLED", "RETURNED").contains(to)) closeActiveAssignment(actor.tenantId(), id,
                "DELIVERED".equals(to) ? "COMPLETED" : "REASSIGNED", request.reason());
        insertHistory(actor, id, from, to, request.reason(), key);
        audit(actor, "DELIVERY_TRANSITION", "DELIVERY", id.toString(), Map.of("fromStatus", from, "toStatus", to, "reason", safe(request.reason())));
        enqueueEvent(actor.tenantId(), id, "DELIVERY_STATUS_CHANGED", Map.of("orderReference", row.get("orderReference"), "fromStatus", from, "toStatus", to, "reason", safe(request.reason())));
        if ("DELIVERED".equals(to)) createEarning(actor.tenantId(), id, row);
        enqueueOrderSync(actor.tenantId(), id, row.get("orderId"), to);
        saveIdempotency(actor.tenantId(), key, "DELIVERY_TRANSITION", hash, id);
        return getDelivery(actor, id);
    }

    /**
     * Creates a delivery for an order that Order Service confirms exists and is eligible under the tenant's published rules.
     * The recipient and order total come from Order Service; the caller supplies only logistics details.
     */
    @Transactional
    public Map<String, Object> createDeliveryFromOrder(DeliveryPrincipal actor, DeliveryCommands.CreateFromOrder request, String idempotencyKey) {
        requirePermission(actor, "delivery:manage");
        String key = requireIdempotencyKey(idempotencyKey);
        String payloadHash = hash(request);
        Optional<UUID> replay = replay(actor.tenantId(), key, "DELIVERY_CREATE_FROM_ORDER", payloadHash);
        if (replay.isPresent()) return getDelivery(actor, replay.get());
        Integer claimed = jdbc.queryForObject("SELECT count(*) FROM delivery WHERE order_id=?", Integer.class, request.orderId());
        if (claimed != null && claimed > 0) conflict("A delivery already exists for this order.");
        DeliveryOrderClient.OrderSnapshot order;
        try {
            order = orders.findOrder(request.orderId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        } catch (DeliveryOrderClient.OrderServiceUnavailableException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Order Service is unavailable. Try again shortly.");
        }
        Set<String> eligible = DeliveryRules.stringSetValue(rules.active(actor.tenantId()), DeliveryRules.ORDER_ELIGIBLE_STATUSES);
        if (order.userId() == null || order.userId() <= 0 || !eligible.contains(String.valueOf(order.status()).toUpperCase(Locale.ROOT))) {
            conflict("The order is not eligible for delivery.");
        }
        UUID id = UUID.randomUUID();
        String orderReference = "ORD-" + order.id();
        try {
            jdbc.update("""
                    INSERT INTO delivery(id,tenant_id,order_reference,recipient_user_id,business_type,pickup_name,pickup_address,
                        dropoff_name,dropoff_address,postal_code,required_capability,weight_kg,item_count,scheduled_for,created_by,order_id,order_total)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, id, actor.tenantId(), orderReference, order.userId(), request.businessType(),
                    clean(request.pickupName()), clean(request.pickupAddress()), clean(request.dropoffName()), clean(request.dropoffAddress()),
                    request.postalCode().trim().toUpperCase(Locale.ROOT), normalizeOptional(request.requiredCapability()),
                    request.weightKg(), request.itemCount(), request.scheduledFor(), actor.userId(), order.id(), order.totalAmount());
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A delivery already exists for this order.");
        }
        insertHistory(actor, id, null, "CREATED", "Delivery created from order.", key);
        audit(actor, "DELIVERY_CREATE", "DELIVERY", id.toString(), Map.of("orderId", order.id(), "businessType", request.businessType()));
        enqueueEvent(actor.tenantId(), id, "DELIVERY_CREATED", Map.of("orderReference", orderReference, "businessType", request.businessType()));
        saveIdempotency(actor.tenantId(), key, "DELIVERY_CREATE_FROM_ORDER", payloadHash, id);
        return getDelivery(actor, id);
    }

    /** Partners see only their own offers and assignments, with no customer address until the offer is accepted. */
    public List<Map<String, Object>> myAssignments(DeliveryPrincipal actor) {
        requirePermission(actor, "delivery:partner:self");
        UUID partnerId = ownPartnerIdOrNull(actor);
        if (partnerId == null) return List.of();
        return jdbc.queryForList("""
                SELECT a.id::text AS "id",a.delivery_id::text AS "deliveryId",a.status,a.offered_at AS "offeredAt",a.responded_at AS "respondedAt",
                       d.order_reference AS "orderReference",d.business_type AS "businessType",d.postal_code AS "postalCode",
                       d.pickup_name AS "pickupName",d.status AS "deliveryStatus"
                FROM delivery_assignment a JOIN delivery d ON d.id=a.delivery_id AND d.tenant_id=a.tenant_id
                WHERE a.tenant_id=? AND a.partner_id=? ORDER BY a.offered_at DESC LIMIT 100
                """, actor.tenantId(), partnerId);
    }

    /** Recipients track only deliveries addressed to them, and only after the delivery has left the draft stage. */
    public List<Map<String, Object>> myDeliveries(DeliveryPrincipal actor) {
        requireRecipient(actor);
        return jdbc.queryForList("""
                SELECT id::text AS "id",order_id AS "orderId",status,scheduled_for AS "scheduledFor",created_at AS "createdAt",delivered_at AS "deliveredAt"
                FROM delivery WHERE tenant_id=? AND recipient_user_id=? AND status<>'CREATED' ORDER BY created_at DESC LIMIT 50
                """, actor.tenantId(), actor.userId());
    }

    public Map<String, Object> myDelivery(DeliveryPrincipal actor, UUID id) {
        Map<String, Object> delivery = ownRecipientDelivery(actor, id);
        String status = String.valueOf(delivery.get("status"));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", delivery.get("id"));
        view.put("orderId", delivery.get("orderId"));
        view.put("status", status);
        view.put("scheduledFor", delivery.get("scheduledFor"));
        view.put("deliveredAt", delivery.get("deliveredAt"));
        view.put("history", jdbc.queryForList("""
                SELECT to_status AS "status",created_at AS "at" FROM delivery_status_history
                WHERE tenant_id=? AND delivery_id=? ORDER BY created_at,id
                """, actor.tenantId(), id));
        if (Set.of("IN_TRANSIT", "ARRIVED_AT_DROPOFF").contains(status)) {
            view.put("latestLocation", jdbc.query("""
                    SELECT latitude,longitude,recorded_at AS "recordedAt" FROM delivery_location_update
                    WHERE tenant_id=? AND delivery_id=? ORDER BY recorded_at DESC LIMIT 1
                    """, rs -> rs.next() ? mapCurrentRow(rs) : null, actor.tenantId(), id));
        }
        return view;
    }

    /**
     * Issues (or rotates) the one-time handover code for the authenticated recipient. The plaintext exists only in this
     * response; the database keeps a keyed hash. Rotation is capped and resets the attempt counter.
     */
    @Transactional
    public Map<String, Object> issueDeliveryCode(DeliveryPrincipal actor, UUID id) {
        Map<String, Object> delivery = ownRecipientDelivery(actor, id);
        if (!Set.of("PICKED_UP", "IN_TRANSIT", "ARRIVED_AT_DROPOFF").contains(String.valueOf(delivery.get("status")))) {
            conflict("A delivery code is available once the order has been picked up.");
        }
        int ttl = DeliveryRules.intValue(rules.active(actor.tenantId()), DeliveryRules.OTP_TTL_MINUTES);
        String code = codes.newCode();
        String hashed = codes.hashCode(id, code);
        java.sql.Timestamp expires = java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(ttl * 60L));
        List<Map<String, Object>> current = jdbc.queryForList("SELECT issue_count,consumed_at FROM delivery_otp WHERE tenant_id=? AND delivery_id=?", actor.tenantId(), id);
        if (current.isEmpty()) {
            jdbc.update("INSERT INTO delivery_otp(delivery_id,tenant_id,code_hash,expires_at) VALUES(?,?,?,?)", id, actor.tenantId(), hashed, expires);
        } else {
            if (current.get(0).get("consumed_at") != null) conflict("The delivery code has already been used.");
            if (((Number) current.get(0).get("issue_count")).intValue() >= MAX_CODE_ISSUES) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many delivery code requests.");
            }
            jdbc.update("UPDATE delivery_otp SET code_hash=?,expires_at=?,attempts=0,issue_count=issue_count+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND delivery_id=?",
                    hashed, expires, actor.tenantId(), id);
        }
        audit(actor, "DELIVERY_CODE_ISSUED", "DELIVERY", id.toString(), Map.of("ttlMinutes", ttl));
        return Map.of("deliveryId", id.toString(), "code", code, "expiresAt", expires.toInstant().toString());
    }

    private Map<String, Object> ownRecipientDelivery(DeliveryPrincipal actor, UUID id) {
        requireRecipient(actor);
        Map<String, Object> delivery = deliveryRow(actor.tenantId(), id);
        Object recipient = delivery.get("recipientUserId");
        String status = String.valueOf(delivery.get("status"));
        // Wrong owner, draft stage and unknown IDs are indistinguishable to the caller.
        if (!(recipient instanceof Number number) || number.longValue() != actor.userId() || !TRACKABLE_STATUSES.contains(status)) notFound();
        return delivery;
    }

    private void requireRecipient(DeliveryPrincipal actor) {
        requirePermission(actor, "delivery:track:own");
    }

    public List<Map<String, Object>> dispatchQueue(DeliveryPrincipal actor) {
        requirePermission(actor, "delivery:dispatch");
        return jdbc.queryForList("""
                SELECT id::text AS "id",order_reference AS "orderReference",business_type AS "businessType",
                       pickup_name AS "pickupName",dropoff_name AS "dropoffName",postal_code AS "postalCode",
                       required_capability AS "requiredCapability",weight_kg AS "weightKg",item_count AS "itemCount",
                       scheduled_for AS "scheduledFor",version
                FROM delivery WHERE tenant_id=? AND status='READY_FOR_DISPATCH' AND pickup_ready=TRUE
                  AND (scheduled_for IS NULL OR scheduled_for<=CURRENT_TIMESTAMP)
                ORDER BY scheduled_for NULLS FIRST,created_at LIMIT 100
                """, actor.tenantId());
    }

    public List<Map<String, Object>> assignmentCandidates(DeliveryPrincipal actor, UUID deliveryId) {
        requirePermission(actor, "delivery:dispatch");
        Map<String, Object> delivery = deliveryRow(actor.tenantId(), deliveryId);
        if (!"READY_FOR_DISPATCH".equals(delivery.get("status")) || !Boolean.TRUE.equals(delivery.get("pickupReady"))) {
            conflict("Only pickup-ready deliveries can be dispatched.");
        }
        return candidateRows(actor.tenantId(), deliveryId);
    }

    @Transactional
    public Map<String, Object> assign(DeliveryPrincipal actor, UUID deliveryId, DeliveryCommands.Assign request, String idempotencyKey) {
        requirePermission(actor, "delivery:dispatch");
        String key = requireIdempotencyKey(idempotencyKey);
        String hash = hash(Map.of("partnerId", request.partnerId(), "expectedVersion", request.expectedVersion(), "reason", safe(request.reason())));
        Optional<UUID> replay = replay(actor.tenantId(), key, "DELIVERY_ASSIGN", hash);
        if (replay.isPresent()) return getDelivery(actor, replay.get());
        Map<String, Object> delivery = deliveryRow(actor.tenantId(), deliveryId);
        if (!"READY_FOR_DISPATCH".equals(delivery.get("status")) || !Boolean.TRUE.equals(delivery.get("pickupReady"))) conflict("Delivery is not ready for dispatch.");
        List<Map<String, Object>> candidates = candidateRows(actor.tenantId(), deliveryId);
        boolean eligible = candidates.stream().anyMatch(candidate -> request.partnerId().toString().equals(candidate.get("id")));
        if (!eligible) conflict("Partner is not eligible for this delivery. Check zone, capability, availability and capacity.");
        boolean reassignment = jdbc.queryForObject("SELECT count(*) FROM delivery_assignment WHERE tenant_id=? AND delivery_id=?", Integer.class, actor.tenantId(), deliveryId) > 0;
        if (reassignment && !hasText(request.reason())) badRequest("A reason is required when reassigning a delivery.");
        UUID assignmentId = UUID.randomUUID();
        jdbc.update("UPDATE delivery_assignment SET active=FALSE,status='REASSIGNED',responded_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND delivery_id=? AND active=TRUE",
                actor.tenantId(), deliveryId);
        int changed = jdbc.update("""
                UPDATE delivery SET status='ASSIGNED',assigned_partner_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND version=? AND status='READY_FOR_DISPATCH'
                """, request.partnerId(), actor.tenantId(), deliveryId, request.expectedVersion());
        if (changed == 0) conflict("Delivery changed concurrently. Refresh the dispatch board and try again.");
        jdbc.update("""
                INSERT INTO delivery_assignment(id,tenant_id,delivery_id,partner_id,assigned_by,status,reason)
                VALUES(?,?,?,?,?,'OFFERED',?)
                """, assignmentId, actor.tenantId(), deliveryId, request.partnerId(), actor.userId(), safe(request.reason()));
        insertHistory(actor, deliveryId, "READY_FOR_DISPATCH", "ASSIGNED", reassignment ? request.reason() : "Partner offer sent.", key);
        audit(actor, reassignment ? "DELIVERY_REASSIGN" : "DELIVERY_ASSIGN", "DELIVERY", deliveryId.toString(),
                Map.of("partnerId", request.partnerId().toString(), "reason", safe(request.reason()), "assignmentId", assignmentId.toString()));
        enqueueEvent(actor.tenantId(), deliveryId, "DELIVERY_ASSIGNMENT_OFFERED", Map.of("orderReference", delivery.get("orderReference"), "partnerId", request.partnerId().toString()));
        saveIdempotency(actor.tenantId(), key, "DELIVERY_ASSIGN", hash, deliveryId);
        return getDelivery(actor, deliveryId);
    }

    @Transactional
    public Map<String, Object> respondToAssignment(DeliveryPrincipal actor, UUID assignmentId, DeliveryCommands.AssignmentResponse request, String idempotencyKey) {
        requirePermission(actor, "delivery:partner:self");
        UUID partnerId = ownPartnerId(actor);
        Map<String, Object> assignment = jdbc.query("""
                SELECT id::text AS "id",delivery_id::text AS "deliveryId",status
                FROM delivery_assignment WHERE tenant_id=? AND id=? AND partner_id=? AND active=TRUE
                """, rs -> rs.next() ? mapCurrentRow(rs) : null, actor.tenantId(), assignmentId, partnerId);
        if (assignment == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found.");
        String key = requireIdempotencyKey(idempotencyKey);
        String hash = hash(request);
        UUID deliveryId = UUID.fromString(String.valueOf(assignment.get("deliveryId")));
        Optional<UUID> replay = replay(actor.tenantId(), key, "ASSIGNMENT_RESPONSE", hash);
        if (replay.isPresent()) return getDelivery(actor, replay.get());
        if (!"OFFERED".equals(assignment.get("status"))) conflict("This assignment offer is no longer pending.");
        String status = request.accepted() ? "ACCEPTED" : "REJECTED";
        if (!request.accepted() && !hasText(request.reason())) badRequest("A rejection reason is required.");
        jdbc.update("UPDATE delivery_assignment SET status=?,active=?,reason=COALESCE(?,reason),responded_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND status='OFFERED'",
                status, request.accepted(), safe(request.reason()), actor.tenantId(), assignmentId);
        if (request.accepted()) {
            jdbc.update("UPDATE delivery SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND status='ASSIGNED'",
                    actor.tenantId(), deliveryId);
        } else {
            jdbc.update("UPDATE delivery SET status='READY_FOR_DISPATCH',assigned_partner_id=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND status='ASSIGNED'",
                    actor.tenantId(), deliveryId);
            insertHistory(actor, deliveryId, "ASSIGNED", "READY_FOR_DISPATCH", request.reason(), key);
        }
        audit(actor, request.accepted() ? "ASSIGNMENT_ACCEPT" : "ASSIGNMENT_REJECT", "ASSIGNMENT", assignmentId.toString(),
                Map.of("deliveryId", deliveryId.toString(), "reason", safe(request.reason())));
        enqueueEvent(actor.tenantId(), deliveryId, request.accepted() ? "DELIVERY_ASSIGNMENT_ACCEPTED" : "DELIVERY_ASSIGNMENT_REJECTED",
                Map.of("assignmentId", assignmentId.toString(), "reason", safe(request.reason())));
        saveIdempotency(actor.tenantId(), key, "ASSIGNMENT_RESPONSE", hash, deliveryId);
        return getDelivery(actor, deliveryId);
    }

    private List<Map<String, Object>> candidateRows(UUID tenantId, UUID deliveryId) {
        return jdbc.queryForList("""
                WITH matches AS (
                    SELECT p.id,p.display_name AS "displayName",p.vehicle_type AS "vehicleType",p.capacity_kg AS "capacityKg",
                           p.max_active_deliveries AS "maxActiveDeliveries",z.code AS "zoneCode",
                           count(active.id)::integer AS "activeAssignments",
                           p.capacity_kg-d.weight_kg AS "capacitySlack",
                           row_number() OVER (PARTITION BY p.id ORDER BY length(z.postal_prefix) DESC,z.code) AS zone_rank
                    FROM delivery d
                    JOIN delivery_zone z ON z.tenant_id=d.tenant_id AND z.active=TRUE
                       AND z.business_type IN (d.business_type,'BOTH') AND d.postal_code LIKE z.postal_prefix || '%'
                    JOIN delivery_partner_zone pz ON pz.tenant_id=d.tenant_id AND pz.zone_id=z.id
                    JOIN delivery_partner_profile p ON p.id=pz.partner_id AND p.tenant_id=d.tenant_id
                    LEFT JOIN delivery active ON active.tenant_id=d.tenant_id AND active.assigned_partner_id=p.id
                       AND active.status IN ('ASSIGNED','ARRIVED_AT_PICKUP','PICKED_UP','IN_TRANSIT','ARRIVED_AT_DROPOFF')
                    WHERE d.tenant_id=? AND d.id=? AND p.active=TRUE AND p.verification_status='APPROVED'
                      AND p.document_status IN ('ACCEPTED','NOT_SUBMITTED') AND p.availability_status='ONLINE'
                      AND p.capacity_kg>=d.weight_kg
                      AND (d.required_capability IS NULL OR EXISTS (
                          SELECT 1 FROM delivery_partner_capability c WHERE c.tenant_id=d.tenant_id AND c.partner_id=p.id AND c.capability=d.required_capability))
                    GROUP BY p.id,p.display_name,p.vehicle_type,p.capacity_kg,p.max_active_deliveries,z.code,z.postal_prefix,d.weight_kg
                    HAVING count(active.id)<p.max_active_deliveries
                )
                SELECT id::text AS "id","displayName","vehicleType","capacityKg","maxActiveDeliveries","zoneCode",
                       "activeAssignments","capacitySlack"
                FROM matches WHERE zone_rank=1
                ORDER BY "activeAssignments","capacitySlack","displayName" LIMIT 100
                """, tenantId, deliveryId);
    }

    public Map<String, Object> myPartnerProfile(DeliveryPrincipal actor) {
        requirePermission(actor, "delivery:partner:self");
        return partnerRow(actor.tenantId(), ownPartnerIdOrNull(actor));
    }

    @Transactional
    public Map<String, Object> saveMyPartnerProfile(DeliveryPrincipal actor, DeliveryCommands.PartnerProfile request) {
        requirePermission(actor, "delivery:partner:self");
        UUID tenant = actor.tenantId();
        UUID partnerId = ownPartnerIdOrNull(actor);
        if (partnerId == null) {
            partnerId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO delivery_partner_profile(id,tenant_id,user_id,email,display_name,vehicle_type,capacity_kg,max_active_deliveries)
                    VALUES(?,?,?,?,?,?,?,?)
                    """, partnerId, tenant, actor.userId(), actor.email(), clean(request.displayName()),
                    normalizeToken(request.vehicleType(), 32), request.capacityKg(), request.maxActiveDeliveries());
        } else {
            jdbc.update("""
                    UPDATE delivery_partner_profile SET display_name=?,vehicle_type=?,capacity_kg=?,max_active_deliveries=?,
                        updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND user_id=?
                    """, clean(request.displayName()), normalizeToken(request.vehicleType(), 32), request.capacityKg(),
                    request.maxActiveDeliveries(), tenant, partnerId, actor.userId());
        }
        jdbc.update("DELETE FROM delivery_partner_capability WHERE tenant_id=? AND partner_id=?", tenant, partnerId);
        if (request.capabilities() != null) {
            for (String capability : request.capabilities()) {
                String value = normalizeToken(capability, 48);
                jdbc.update("INSERT INTO delivery_partner_capability(tenant_id,partner_id,capability) VALUES(?,?,?) ON CONFLICT DO NOTHING",
                        tenant, partnerId, value);
            }
        }
        jdbc.update("DELETE FROM delivery_partner_zone WHERE tenant_id=? AND partner_id=?", tenant, partnerId);
        if (request.zoneIds() != null) {
            for (UUID zoneId : request.zoneIds().stream().distinct().toList()) {
                int exists = jdbc.queryForObject("SELECT count(*) FROM delivery_zone WHERE tenant_id=? AND id=? AND active=TRUE", Integer.class, tenant, zoneId);
                if (exists == 0) badRequest("A selected delivery zone is unavailable.");
                jdbc.update("INSERT INTO delivery_partner_zone(tenant_id,partner_id,zone_id) VALUES(?,?,?)", tenant, partnerId, zoneId);
            }
        }
        audit(actor, "PARTNER_PROFILE_UPDATE", "PARTNER", partnerId.toString(), Map.of("vehicleType", normalizeToken(request.vehicleType(), 32)));
        return partnerRow(tenant, partnerId);
    }

    public List<Map<String, Object>> listPartners(DeliveryPrincipal actor) {
        boolean partner = isPartner(actor);
        requirePermission(actor, partner ? "delivery:partner:self" : "delivery:partners:read");
        if (partner) {
            UUID id = ownPartnerIdOrNull(actor);
            if (id == null) return List.of();
            return List.of(partnerRow(actor.tenantId(), id));
        }
        return jdbc.queryForList("""
                SELECT p.id::text AS "id",p.display_name AS "displayName",p.email,p.vehicle_type AS "vehicleType",
                       p.capacity_kg AS "capacityKg",p.max_active_deliveries AS "maxActiveDeliveries",
                       p.verification_status AS "verificationStatus",p.document_status AS "documentStatus",
                       p.active,p.availability_status AS "availabilityStatus",p.updated_at AS "updatedAt",
                       (SELECT count(*) FROM delivery d WHERE d.tenant_id=p.tenant_id AND d.assigned_partner_id=p.id
                           AND d.status IN ('ASSIGNED','ARRIVED_AT_PICKUP','PICKED_UP','IN_TRANSIT','ARRIVED_AT_DROPOFF')) AS "activeAssignments",
                       (SELECT string_agg(c.capability,', ' ORDER BY c.capability) FROM delivery_partner_capability c
                           WHERE c.tenant_id=p.tenant_id AND c.partner_id=p.id) AS capabilities
                FROM delivery_partner_profile p WHERE p.tenant_id=? ORDER BY p.display_name LIMIT 200
                """, actor.tenantId());
    }

    @Transactional
    public Map<String, Object> reviewPartner(DeliveryPrincipal actor, UUID partnerId, DeliveryCommands.PartnerReview request) {
        requirePermission(actor, "delivery:partners:manage");
        if (Boolean.TRUE.equals(request.active()) && (!"APPROVED".equals(request.verificationStatus()) || !"ACCEPTED".equals(request.documentStatus()))) {
            badRequest("A partner must have approved verification and accepted document status before activation.");
        }
        int changed = jdbc.update("""
                UPDATE delivery_partner_profile SET verification_status=?,document_status=?,active=?,
                    availability_status=CASE WHEN ? THEN availability_status ELSE 'OFFLINE' END,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=?
                """, request.verificationStatus(), request.documentStatus(), request.active(), request.active(), actor.tenantId(), partnerId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner not found.");
        audit(actor, "PARTNER_REVIEW", "PARTNER", partnerId.toString(), Map.of("verificationStatus", request.verificationStatus(),
                "documentStatus", request.documentStatus(), "active", request.active()));
        return partnerRow(actor.tenantId(), partnerId);
    }

    @Transactional
    public Map<String, Object> setAvailability(DeliveryPrincipal actor, DeliveryCommands.Availability request) {
        requirePermission(actor, "delivery:partner:self");
        UUID partnerId = ownPartnerId(actor);
        if ("ONLINE".equals(request.status())) {
            int eligible = jdbc.queryForObject("""
                    SELECT count(*) FROM delivery_partner_profile WHERE tenant_id=? AND id=? AND active=TRUE
                        AND verification_status='APPROVED' AND document_status='ACCEPTED'
                    """, Integer.class, actor.tenantId(), partnerId);
            if (eligible == 0) conflict("Partner activation and document review are required before going online.");
        }
        jdbc.update("UPDATE delivery_partner_profile SET availability_status=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",
                request.status(), actor.tenantId(), partnerId);
        audit(actor, "PARTNER_AVAILABILITY", "PARTNER", partnerId.toString(), Map.of("status", request.status()));
        return partnerRow(actor.tenantId(), partnerId);
    }

    public List<Map<String, Object>> zones(DeliveryPrincipal actor) {
        if (isPartner(actor)) {
            requirePermission(actor, "delivery:partner:self");
            UUID partner = ownPartnerIdOrNull(actor);
            if (partner == null) return jdbc.queryForList("""
                    SELECT id::text AS "id",code,name,business_type AS "businessType",postal_prefix AS "postalPrefix",active
                    FROM delivery_zone WHERE tenant_id=? AND active=TRUE ORDER BY name
                    """, actor.tenantId());
            return jdbc.queryForList("""
                    SELECT z.id::text AS "id",z.code,z.name,z.business_type AS "businessType",z.postal_prefix AS "postalPrefix",z.active
                FROM delivery_zone z JOIN delivery_partner_zone pz ON pz.zone_id=z.id AND pz.tenant_id=z.tenant_id
                    WHERE z.tenant_id=? AND pz.partner_id=? AND z.active=TRUE ORDER BY z.name
                    """, actor.tenantId(), partner);
        }
        requirePermission(actor, "delivery:read");
        return jdbc.queryForList("""
                SELECT id::text AS "id",code,name,business_type AS "businessType",postal_prefix AS "postalPrefix",active,
                       created_at AS "createdAt",updated_at AS "updatedAt"
                FROM delivery_zone WHERE tenant_id=? ORDER BY name
                """, actor.tenantId());
    }

    @Transactional
    public Map<String, Object> saveZone(DeliveryPrincipal actor, UUID zoneId, DeliveryCommands.CreateZone request) {
        requirePermission(actor, "delivery:config");
        UUID id = zoneId == null ? UUID.randomUUID() : zoneId;
        String code = normalizeToken(request.code(), 48);
        if (zoneId == null) {
            jdbc.update("""
                    INSERT INTO delivery_zone(id,tenant_id,code,name,business_type,postal_prefix,active)
                    VALUES(?,?,?,?,?,?,?)
                    """, id, actor.tenantId(), code, clean(request.name()), request.businessType(), normalizePostalPrefix(request.postalPrefix()), request.active());
        } else {
            int changed = jdbc.update("""
                    UPDATE delivery_zone SET code=?,name=?,business_type=?,postal_prefix=?,active=?,updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND id=?
                    """, code, clean(request.name()), request.businessType(), normalizePostalPrefix(request.postalPrefix()), request.active(), actor.tenantId(), id);
            if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery zone not found.");
        }
        audit(actor, zoneId == null ? "ZONE_CREATE" : "ZONE_UPDATE", "ZONE", id.toString(), Map.of("code", code, "active", request.active()));
        return jdbc.queryForMap("""
                SELECT id::text AS "id",code,name,business_type AS "businessType",postal_prefix AS "postalPrefix",active
                FROM delivery_zone WHERE tenant_id=? AND id=?
                """, actor.tenantId(), id);
    }

    public Map<String, String> configuration(DeliveryPrincipal actor) {
        requirePermission(actor, "delivery:config");
        Map<String, String> values = new TreeMap<>();
        jdbc.query("SELECT config_key,config_value FROM delivery_configuration WHERE tenant_id=? ORDER BY config_key",
                rs -> { values.put(rs.getString(1), rs.getString(2)); }, actor.tenantId());
        values.putIfAbsent("assignment_timeout_minutes", "5");
        values.putIfAbsent("location_retention_days", "7");
        values.putIfAbsent("proof_retention_days", "365");
        values.putIfAbsent("automatic_reassignment", "false");
        values.putIfAbsent("partner_documents_required", "true");
        return values;
    }

    @Transactional
    public Map<String, String> updateConfiguration(DeliveryPrincipal actor, String rawKey, DeliveryCommands.ConfigValue request) {
        requirePermission(actor, "delivery:config");
        String key = rawKey == null ? "" : rawKey.trim().toLowerCase(Locale.ROOT);
        String value = request.value() == null ? "" : request.value().trim();
        switch (key) {
            case "assignment_timeout_minutes" -> { if (!integerInRange(value, 1, 60)) badRequest("Assignment timeout must be between 1 and 60 minutes."); }
            case "location_retention_days" -> { if (!integerInRange(value, 1, 90)) badRequest("Location retention must be between 1 and 90 days."); }
            case "proof_retention_days" -> { if (!integerInRange(value, 30, 365)) badRequest("Proof metadata retention must be between 30 and 365 days."); }
            case "automatic_reassignment", "partner_documents_required" -> {
                if (!Set.of("true", "false").contains(value.toLowerCase(Locale.ROOT))) badRequest("Provide true or false.");
                value = value.toLowerCase(Locale.ROOT);
            }
            default -> badRequest("Configuration key is not editable.");
        }
        jdbc.update("""
                INSERT INTO delivery_configuration(tenant_id,config_key,config_value,updated_by)
                VALUES(?,?,?,?) ON CONFLICT (tenant_id,config_key) DO UPDATE
                SET config_value=EXCLUDED.config_value,updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP
                """, actor.tenantId(), key, value, actor.userId());
        audit(actor, "CONFIG_UPDATE", "CONFIGURATION", key, Map.of("value", value));
        return configuration(actor);
    }

    public List<Map<String, Object>> auditLog(DeliveryPrincipal actor, int requestedLimit) {
        requirePermission(actor, "delivery:audit");
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return jdbc.queryForList("""
                SELECT id,actor_user_id AS "actorUserId",actor_email AS "actorEmail",actor_role AS "actorRole",
                       action,resource_type AS "resourceType",resource_id AS "resourceId",details,created_at AS "createdAt"
                FROM delivery_audit_event WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT ?
                """, actor.tenantId(), limit);
    }

    @Transactional
    public Map<String, Object> recordLocation(DeliveryPrincipal actor, UUID deliveryId, DeliveryCommands.Location request) {
        requirePermission(actor, "delivery:tracking");
        Map<String, Object> delivery = deliveryRow(actor.tenantId(), deliveryId);
        assertResourceOwnership(actor, delivery);
        if (!Set.of("ASSIGNED", "ARRIVED_AT_PICKUP", "PICKED_UP", "IN_TRANSIT", "ARRIVED_AT_DROPOFF").contains(delivery.get("status"))) {
            conflict("Location updates are accepted only while a delivery is active.");
        }
        UUID partner = assignedPartner(actor, delivery);
        jdbc.update("""
                INSERT INTO delivery_location_update(tenant_id,delivery_id,partner_id,latitude,longitude,accuracy_m)
                VALUES(?,?,?,?,?,?)
                """, actor.tenantId(), deliveryId, partner, request.latitude(), request.longitude(), request.accuracyM());
        audit(actor, "LOCATION_UPDATE", "DELIVERY", deliveryId.toString(), Map.of("recorded", true));
        return jdbc.queryForMap("""
                SELECT latitude,longitude,accuracy_m AS "accuracyM",recorded_at AS "recordedAt"
                FROM delivery_location_update WHERE tenant_id=? AND delivery_id=? ORDER BY recorded_at DESC LIMIT 1
                """, actor.tenantId(), deliveryId);
    }

    /**
     * Records proof of delivery. Proof is trusted only after the server verified it: an OTP must match the hashed code issued
     * to the recipient (unexpired, unconsumed, within the attempt limit); evidence methods must be enabled by the tenant's
     * published rules. There is no caller-supplied "verified" flag, and any unmet condition rejects the request.
     */
    @Transactional(noRollbackFor = ProofRejectedException.class)
    public Map<String, Object> recordProof(DeliveryPrincipal actor, UUID deliveryId, DeliveryCommands.Proof request, String idempotencyKey) {
        requirePermission(actor, "delivery:proof");
        String key = requireIdempotencyKey(idempotencyKey);
        String proofHash = codes.fingerprint(deliveryId + "|" + request.method() + "|" + safe(request.otp()) + "|"
                + safe(request.evidenceReference()) + "|" + safe(request.recipientName()) + "|" + safe(request.note()));
        Map<String, Object> delivery = deliveryRow(actor.tenantId(), deliveryId);
        assertResourceOwnership(actor, delivery);
        Optional<UUID> replay = replay(actor.tenantId(), key, "DELIVERY_PROOF", proofHash);
        if (replay.isPresent()) return proofRow(actor.tenantId(), deliveryId);
        if (!"ARRIVED_AT_DROPOFF".equals(delivery.get("status"))) conflict("Proof can be captured only after arriving at the drop-off.");
        Map<String, Object> active = rules.active(actor.tenantId());
        if (!DeliveryRules.stringSetValue(active, DeliveryRules.PROOF_ACCEPTED_METHODS).contains(request.method())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "This proof method is not enabled for the tenant.");
        }
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM delivery_proof WHERE tenant_id=? AND delivery_id=?", Integer.class, actor.tenantId(), deliveryId);
        if (existing != null && existing > 0) conflict("Proof was already recorded for this delivery.");
        String storedMethod;
        if ("OTP".equals(request.method())) {
            verifyOtp(actor, deliveryId, request.otp(), DeliveryRules.intValue(active, DeliveryRules.OTP_MAX_ATTEMPTS));
            storedMethod = "PIN_CONFIRMED";
        } else {
            if (!hasText(request.evidenceReference()) || !request.evidenceReference().trim().matches("[A-Za-z0-9/_.:-]{8,200}")) {
                badRequest("Photo and signature methods require an evidence reference, not an uploaded binary payload.");
            }
            storedMethod = request.method();
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO delivery_proof(id,tenant_id,delivery_id,method,recipient_name,evidence_reference,note,captured_by,verified)
                VALUES(?,?,?,?,?,?,?,?,TRUE)
                """, id, actor.tenantId(), deliveryId, storedMethod, safe(request.recipientName()),
                safe(request.evidenceReference()), safe(request.note()), actor.userId());
        audit(actor, "PROOF_CAPTURE", "DELIVERY", deliveryId.toString(), Map.of("method", request.method()));
        enqueueEvent(actor.tenantId(), deliveryId, "DELIVERY_PROOF_CAPTURED", Map.of("method", request.method()));
        saveIdempotency(actor.tenantId(), key, "DELIVERY_PROOF", proofHash, deliveryId);
        return proofRow(actor.tenantId(), deliveryId);
    }

    /** Thrown after a failed attempt was committed separately; the surrounding request must not undo that bookkeeping. */
    public static class ProofRejectedException extends ResponseStatusException {
        public ProofRejectedException(HttpStatus status, String reason) { super(status, reason); }
    }

    private void verifyOtp(DeliveryPrincipal actor, UUID deliveryId, String suppliedCode, int maxAttempts) {
        if (!hasText(suppliedCode)) badRequest("The delivery code is required for this proof method.");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT code_hash,attempts,consumed_at,expires_at FROM delivery_otp WHERE tenant_id=? AND delivery_id=?
                """, actor.tenantId(), deliveryId);
        // Every failure below is deliberately generic so the response never reveals which condition failed.
        ProofRejectedException rejected = new ProofRejectedException(HttpStatus.FORBIDDEN, "The delivery code is invalid or expired.");
        if (rows.isEmpty()) throw rejected;
        Map<String, Object> otp = rows.get(0);
        if (otp.get("consumed_at") != null || ((Number) otp.get("attempts")).intValue() >= maxAttempts) throw rejected;
        Object expiry = otp.get("expires_at");
        java.time.Instant expires = expiry instanceof java.sql.Timestamp stamp ? stamp.toInstant()
                : expiry instanceof java.time.OffsetDateTime offset ? offset.toInstant() : java.time.Instant.EPOCH; // unknown type: fail closed
        if (!expires.isAfter(java.time.Instant.now())) throw rejected;
        if (!codes.matches(String.valueOf(otp.get("code_hash")), deliveryId, suppliedCode)) {
            otpAttempts.recordFailure(actor.tenantId(), deliveryId);
            audit(actor, "PROOF_OTP_REJECTED", "DELIVERY", deliveryId.toString(), Map.of("reason", "mismatch"));
            throw rejected;
        }
        int consumed = jdbc.update("UPDATE delivery_otp SET consumed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND delivery_id=? AND consumed_at IS NULL",
                actor.tenantId(), deliveryId);
        if (consumed == 0) throw rejected;
    }

    private Map<String, Object> proofRow(UUID tenantId, UUID deliveryId) {
        return jdbc.queryForMap("""
                SELECT method,recipient_name AS "recipientName",evidence_reference AS "evidenceReference",note,captured_at AS "capturedAt",verified
                FROM delivery_proof WHERE tenant_id=? AND delivery_id=?
                """, tenantId, deliveryId);
    }

    @Transactional
    public Map<String, Object> reportIncident(DeliveryPrincipal actor, UUID deliveryId, DeliveryCommands.Incident request) {
        requirePermission(actor, "delivery:incident");
        Map<String, Object> delivery = deliveryRow(actor.tenantId(), deliveryId);
        assertResourceOwnership(actor, delivery);
        UUID incidentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO delivery_incident(id,tenant_id,delivery_id,type,severity,description,reported_by)
                VALUES(?,?,?,?,?,?,?)
                """, incidentId, actor.tenantId(), deliveryId, request.type(), request.severity(), clean(request.description()), actor.userId());
        audit(actor, "INCIDENT_REPORT", "INCIDENT", incidentId.toString(), Map.of("deliveryId", deliveryId.toString(), "type", request.type(), "severity", request.severity()));
        enqueueEvent(actor.tenantId(), deliveryId, "DELIVERY_INCIDENT_REPORTED", Map.of("incidentId", incidentId.toString(), "type", request.type(), "severity", request.severity()));
        return incidentRow(actor.tenantId(), incidentId);
    }

    public List<Map<String, Object>> incidents(DeliveryPrincipal actor, String status) {
        boolean partner = isPartner(actor);
        requirePermission(actor, partner ? "delivery:partner:self" : "delivery:incident");
        String normalized = normalizeOptional(status);
        if (normalized != null && !Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED").contains(normalized)) badRequest("Unknown incident status.");
        String query = """
                SELECT i.id::text AS "id",i.delivery_id::text AS "deliveryId",d.order_reference AS "orderReference",
                       i.type,i.severity,i.description,i.status,i.reported_by AS "reportedBy",i.resolution,
                       i.created_at AS "createdAt",i.resolved_at AS "resolvedAt"
                FROM delivery_incident i JOIN delivery d ON d.id=i.delivery_id AND d.tenant_id=i.tenant_id
                WHERE i.tenant_id=? AND (CAST(? AS VARCHAR) IS NULL OR i.status=?)
                """ + (partner ? " AND EXISTS (SELECT 1 FROM delivery_partner_profile p WHERE p.tenant_id=d.tenant_id AND p.id=d.assigned_partner_id AND p.user_id=?) " : "")
                + " ORDER BY i.created_at DESC LIMIT 200";
        if (partner) return jdbc.queryForList(query, actor.tenantId(), normalized, normalized, actor.userId());
        return jdbc.queryForList(query, actor.tenantId(), normalized, normalized);
    }

    @Transactional
    public Map<String, Object> resolveIncident(DeliveryPrincipal actor, UUID incidentId, DeliveryCommands.IncidentResolution request) {
        requirePermission(actor, "delivery:incident");
        int changed = jdbc.update("""
                UPDATE delivery_incident SET status='RESOLVED',resolution=?,resolved_by=?,resolved_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND status<>'RESOLVED'
                """, clean(request.resolution()), actor.userId(), actor.tenantId(), incidentId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Open incident not found.");
        audit(actor, "INCIDENT_RESOLVE", "INCIDENT", incidentId.toString(), Map.of("resolution", request.resolution()));
        return incidentRow(actor.tenantId(), incidentId);
    }

    @Transactional
    public Map<String, Object> createTrip(DeliveryPrincipal actor, DeliveryCommands.CreateTrip request) {
        requirePermission(actor, "delivery:dispatch");
        if (request.deliveryIds().stream().distinct().count() != request.deliveryIds().size()) badRequest("A trip cannot contain duplicate deliveries.");
        int eligiblePartner = jdbc.queryForObject("SELECT count(*) FROM delivery_partner_profile WHERE tenant_id=? AND id=? AND active=TRUE AND verification_status='APPROVED'",
                Integer.class, actor.tenantId(), request.partnerId());
        if (eligiblePartner == 0) conflict("Trip partner is not active and approved.");
        for (UUID deliveryId : request.deliveryIds()) {
            int assigned = jdbc.queryForObject("""
                    SELECT count(*) FROM delivery d JOIN delivery_assignment a ON a.tenant_id=d.tenant_id AND a.delivery_id=d.id
                    WHERE d.tenant_id=? AND d.id=? AND d.assigned_partner_id=? AND d.status='ASSIGNED' AND a.status='ACCEPTED' AND a.active=TRUE
                    """, Integer.class, actor.tenantId(), deliveryId, request.partnerId());
            if (assigned == 0) conflict("Every trip stop must be an accepted assignment for the selected partner.");
        }
        UUID tripId = UUID.randomUUID();
        jdbc.update("INSERT INTO delivery_trip(id,tenant_id,partner_id,created_by) VALUES(?,?,?,?)", tripId, actor.tenantId(), request.partnerId(), actor.userId());
        int order = 1;
        for (UUID deliveryId : request.deliveryIds()) {
            jdbc.update("INSERT INTO delivery_trip_stop(tenant_id,trip_id,delivery_id,stop_order) VALUES(?,?,?,?)",
                    actor.tenantId(), tripId, deliveryId, order++);
        }
        audit(actor, "TRIP_CREATE", "TRIP", tripId.toString(), Map.of("partnerId", request.partnerId().toString(), "stopCount", request.deliveryIds().size()));
        return tripRow(actor, tripId);
    }

    public List<Map<String, Object>> trips(DeliveryPrincipal actor) {
        boolean partner = isPartner(actor);
        requirePermission(actor, partner ? "delivery:partner:self" : "delivery:read");
        UUID partnerId = partner ? ownPartnerIdOrNull(actor) : null;
        if (partner && partnerId == null) return List.of();
        String query = """
                SELECT t.id::text AS "id",t.status,t.partner_id::text AS "partnerId",p.display_name AS "partnerName",
                       t.version,t.created_at AS "createdAt",count(s.delivery_id)::integer AS "stopCount"
                FROM delivery_trip t JOIN delivery_partner_profile p ON p.id=t.partner_id AND p.tenant_id=t.tenant_id
                LEFT JOIN delivery_trip_stop s ON s.trip_id=t.id AND s.tenant_id=t.tenant_id
                WHERE t.tenant_id=? AND (?::uuid IS NULL OR t.partner_id=?)
                GROUP BY t.id,p.display_name ORDER BY t.created_at DESC LIMIT 100
                """;
        return jdbc.queryForList(query, actor.tenantId(), partnerId, partnerId);
    }

    @Transactional
    public Map<String, Object> updateTripStatus(DeliveryPrincipal actor, UUID tripId, String requestedStatus, long expectedVersion) {
        boolean partner = isPartner(actor);
        if (partner) requirePermission(actor, "delivery:partner:self"); else requirePermission(actor, "delivery:dispatch");
        String next = normalizeToken(requestedStatus, 16);
        if (!Set.of("ACTIVE", "COMPLETED", "CANCELLED").contains(next)) badRequest("Unknown trip status.");
        Map<String, Object> trip = tripRow(actor, tripId);
        String current = String.valueOf(trip.get("status"));
        if (partner && !ownPartnerId(actor).toString().equals(trip.get("partnerId"))) notFound();
        boolean allowed = ("PLANNED".equals(current) && Set.of("ACTIVE", "CANCELLED").contains(next))
                || ("ACTIVE".equals(current) && Set.of("COMPLETED", "CANCELLED").contains(next));
        if (!allowed) conflict("Invalid trip transition.");
        int changed = jdbc.update("UPDATE delivery_trip SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND version=? AND status=?",
                next, actor.tenantId(), tripId, expectedVersion, current);
        if (changed == 0) conflict("Trip changed concurrently. Refresh and try again.");
        audit(actor, "TRIP_STATUS", "TRIP", tripId.toString(), Map.of("fromStatus", current, "toStatus", next));
        return tripRow(actor, tripId);
    }

    public List<Map<String, Object>> pricing(DeliveryPrincipal actor) {
        requirePermission(actor, "delivery:config");
        return jdbc.queryForList("""
                SELECT id::text AS "id",business_type AS "businessType",base_earning AS "baseEarning",
                       per_item_earning AS "perItemEarning",currency,active,updated_at AS "updatedAt"
                FROM delivery_pricing_policy WHERE tenant_id=? ORDER BY business_type
                """, actor.tenantId());
    }

    @Transactional
    public Map<String, Object> savePricing(DeliveryPrincipal actor, DeliveryCommands.PricingPolicy request) {
        requirePermission(actor, "delivery:config");
        UUID id = jdbc.query("SELECT id FROM delivery_pricing_policy WHERE tenant_id=? AND business_type=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, actor.tenantId(), request.businessType());
        if (id == null) id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO delivery_pricing_policy(id,tenant_id,business_type,base_earning,per_item_earning,currency,active)
                VALUES(?,?,?,?,?,?,?) ON CONFLICT (tenant_id,business_type) DO UPDATE SET base_earning=EXCLUDED.base_earning,
                    per_item_earning=EXCLUDED.per_item_earning,currency=EXCLUDED.currency,active=EXCLUDED.active,updated_at=CURRENT_TIMESTAMP
                """, id, actor.tenantId(), request.businessType(), request.baseEarning(), request.perItemEarning(), request.currency(), request.active());
        audit(actor, "PRICING_POLICY_UPDATE", "PRICING_POLICY", id.toString(), Map.of("businessType", request.businessType(), "active", request.active()));
        return jdbc.queryForMap("""
                SELECT id::text AS "id",business_type AS "businessType",base_earning AS "baseEarning",per_item_earning AS "perItemEarning",currency,active
                FROM delivery_pricing_policy WHERE tenant_id=? AND id=?
                """, actor.tenantId(), id);
    }

    public Map<String, Object> earnings(DeliveryPrincipal actor) {
        boolean partner = isPartner(actor);
        requirePermission(actor, partner ? "delivery:partner:self" : "delivery:report");
        UUID partnerId = partner ? ownPartnerIdOrNull(actor) : null;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.id::text AS "id",e.delivery_id::text AS "deliveryId",d.order_reference AS "orderReference",
                       p.display_name AS "partnerName",e.amount,e.currency,e.settlement_status AS "settlementStatus",
                       e.settlement_reference AS "settlementReference",e.created_at AS "createdAt"
                FROM delivery_partner_earning e JOIN delivery d ON d.id=e.delivery_id AND d.tenant_id=e.tenant_id
                JOIN delivery_partner_profile p ON p.id=e.partner_id AND p.tenant_id=e.tenant_id
                WHERE e.tenant_id=? AND (?::uuid IS NULL OR e.partner_id=?)
                ORDER BY e.created_at DESC LIMIT 200
                """, actor.tenantId(), partnerId, partnerId);
        Map<String, Object> summary = jdbc.queryForMap("""
                SELECT count(*) AS "count",COALESCE(sum(amount),0) AS "total",COALESCE(max(currency),'USD') AS "currency",
                       count(*) FILTER (WHERE settlement_status='UNSETTLED') AS "unsettledCount"
                FROM delivery_partner_earning WHERE tenant_id=? AND (?::uuid IS NULL OR partner_id=?)
                """, actor.tenantId(), partnerId, partnerId);
        if (summary.isEmpty()) summary = new LinkedHashMap<>(Map.of("count", 0, "total", 0, "currency", "USD", "unsettledCount", 0));
        return Map.of("summary", camelize(summary), "earnings", rows, "payoutsEnabled", false);
    }

    public Map<String, Object> reports(DeliveryPrincipal actor) {
        requirePermission(actor, "delivery:report");
        List<Map<String, Object>> statuses = jdbc.queryForList("""
                SELECT status,count(*)::integer AS count FROM delivery WHERE tenant_id=? GROUP BY status ORDER BY status
                """, actor.tenantId());
        List<Map<String, Object>> businessTypes = jdbc.queryForList("""
                SELECT business_type AS "businessType",count(*)::integer AS count,
                       count(*) FILTER (WHERE status='DELIVERED')::integer AS delivered,
                       count(*) FILTER (WHERE status IN ('PICKUP_FAILED','DELIVERY_FAILED'))::integer AS exceptions
                FROM delivery WHERE tenant_id=? GROUP BY business_type ORDER BY business_type
                """, actor.tenantId());
        return Map.of("statuses", statuses, "businessTypes", businessTypes, "earnings", earnings(actor).get("summary"));
    }

    public List<Map<String, Object>> outboxEvents(DeliveryPrincipal actor, int requestedLimit) {
        requirePermission(actor, "delivery:audit");
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return jdbc.queryForList("""
                SELECT event_id::text AS "eventId",aggregate_id::text AS "aggregateId",event_type AS "eventType",
                       status,attempt_count AS "attemptCount",created_at AS "createdAt",published_at AS "publishedAt"
                FROM delivery_outbox WHERE tenant_id=? ORDER BY created_at DESC LIMIT ?
                """, actor.tenantId(), limit);
    }

    @Transactional
    public int expireAssignmentOffers() {
        List<Map<String, Object>> expired = jdbc.queryForList("""
                SELECT a.id,a.tenant_id,a.delivery_id,d.order_reference
                FROM delivery_assignment a JOIN delivery d ON d.id=a.delivery_id AND d.tenant_id=a.tenant_id
                WHERE a.active=TRUE AND a.status='OFFERED' AND a.offered_at < CURRENT_TIMESTAMP -
                    make_interval(mins => COALESCE((SELECT config_value::integer FROM delivery_configuration c
                        WHERE c.tenant_id=a.tenant_id AND c.config_key='assignment_timeout_minutes'),5))
                ORDER BY a.offered_at LIMIT 100 FOR UPDATE OF a SKIP LOCKED
                """);
        int count = 0;
        for (Map<String, Object> item : expired) {
            UUID tenant = (UUID) item.get("tenant_id");
            UUID deliveryId = (UUID) item.get("delivery_id");
            UUID assignmentId = (UUID) item.get("id");
            jdbc.update("UPDATE delivery_assignment SET active=FALSE,status='EXPIRED',responded_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND active=TRUE",
                    tenant, assignmentId);
            jdbc.update("UPDATE delivery SET status='READY_FOR_DISPATCH',assigned_partner_id=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND status='ASSIGNED'",
                    tenant, deliveryId);
            insertSystemHistory(tenant, deliveryId, "ASSIGNED", "READY_FOR_DISPATCH", "Partner offer timed out.");
            insertSystemAudit(tenant, "ASSIGNMENT_TIMEOUT", "ASSIGNMENT", assignmentId.toString(), "{" + "\"deliveryId\":\"" + deliveryId + "\"}");
            enqueueEvent(tenant, deliveryId, "DELIVERY_ASSIGNMENT_EXPIRED", Map.of("assignmentId", assignmentId.toString(), "orderReference", item.get("order_reference")));
            boolean auto = Boolean.parseBoolean(configValue(tenant, "automatic_reassignment", "false"));
            if (auto) autoAssign(tenant, deliveryId, item.get("order_reference"));
            count++;
        }
        return count;
    }

    private void autoAssign(UUID tenant, UUID deliveryId, Object orderReference) {
        List<Map<String, Object>> candidates = candidateRows(tenant, deliveryId);
        if (candidates.isEmpty()) return;
        UUID partner = UUID.fromString(String.valueOf(candidates.get(0).get("id")));
        UUID assignment = UUID.randomUUID();
        int updated = jdbc.update("UPDATE delivery SET status='ASSIGNED',assigned_partner_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND status='READY_FOR_DISPATCH'",
                partner, tenant, deliveryId);
        if (updated == 0) return;
        jdbc.update("INSERT INTO delivery_assignment(id,tenant_id,delivery_id,partner_id,assigned_by,status,reason) VALUES(?,?,?,?,0,'OFFERED','Automatic timeout reassignment')",
                assignment, tenant, deliveryId, partner);
        insertSystemHistory(tenant, deliveryId, "READY_FOR_DISPATCH", "ASSIGNED", "Automatic timeout reassignment.");
        insertSystemAudit(tenant, "AUTO_ASSIGN", "DELIVERY", deliveryId.toString(), "{" + "\"partnerId\":\"" + partner + "\"}");
        enqueueEvent(tenant, deliveryId, "DELIVERY_ASSIGNMENT_OFFERED", Map.of("orderReference", orderReference, "partnerId", partner.toString(), "automatic", true));
    }

    private void createEarning(UUID tenant, UUID deliveryId, Map<String, Object> delivery) {
        Object partnerValue = delivery.get("assignedPartnerId");
        if (partnerValue == null) return;
        UUID partner = UUID.fromString(String.valueOf(partnerValue));
        List<Map<String, Object>> policies = jdbc.queryForList("""
                SELECT base_earning,per_item_earning,currency FROM delivery_pricing_policy
                WHERE tenant_id=? AND business_type=? AND active=TRUE
                """, tenant, delivery.get("businessType"));
        if (policies.isEmpty()) return;
        Map<String, Object> policy = policies.get(0);
        java.math.BigDecimal base = (java.math.BigDecimal) policy.get("base_earning");
        java.math.BigDecimal perItem = (java.math.BigDecimal) policy.get("per_item_earning");
        int items = ((Number) delivery.get("itemCount")).intValue();
        java.math.BigDecimal amount = base.add(perItem.multiply(java.math.BigDecimal.valueOf(items)));
        jdbc.update("""
                INSERT INTO delivery_partner_earning(id,tenant_id,delivery_id,partner_id,amount,currency)
                VALUES(?,?,?,?,?,?) ON CONFLICT (delivery_id) DO NOTHING
                """, UUID.randomUUID(), tenant, deliveryId, partner, amount, policy.get("currency"));
    }

    private Map<String, Object> deliveryRow(UUID tenantId, UUID id) {
        try {
            return jdbc.queryForMap("""
                    SELECT id::text AS "id",tenant_id::text AS "tenantId",order_reference AS "orderReference",
                           recipient_user_id AS "recipientUserId",business_type AS "businessType",pickup_name AS "pickupName",
                           pickup_address AS "pickupAddress",dropoff_name AS "dropoffName",dropoff_address AS "dropoffAddress",
                           postal_code AS "postalCode",required_capability AS "requiredCapability",weight_kg AS "weightKg",
                           item_count AS "itemCount",scheduled_for AS "scheduledFor",pickup_ready AS "pickupReady",status,
                           assigned_partner_id::text AS "assignedPartnerId",version,created_by AS "createdBy",
                           cancel_reason AS "cancelReason",delivered_at AS "deliveredAt",created_at AS "createdAt",updated_at AS "updatedAt",
                           order_id AS "orderId",order_total AS "orderTotal",order_currency AS "orderCurrency"
                    FROM delivery WHERE tenant_id=? AND id=?
                    """, tenantId, id);
        } catch (EmptyResultDataAccessException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found.");
        }
    }

    private Map<String, Object> partnerRow(UUID tenantId, UUID id) {
        if (id == null) return null;
        try {
            Map<String, Object> profile = jdbc.queryForMap("""
                    SELECT p.id::text AS "id",p.display_name AS "displayName",p.email,p.vehicle_type AS "vehicleType",
                           p.capacity_kg AS "capacityKg",p.max_active_deliveries AS "maxActiveDeliveries",
                           p.verification_status AS "verificationStatus",p.document_status AS "documentStatus",
                           p.active,p.availability_status AS "availabilityStatus",p.updated_at AS "updatedAt",
                           (SELECT string_agg(c.capability,', ' ORDER BY c.capability) FROM delivery_partner_capability c
                               WHERE c.tenant_id=p.tenant_id AND c.partner_id=p.id) AS capabilities
                    FROM delivery_partner_profile p WHERE p.tenant_id=? AND p.id=?
                    """, tenantId, id);
            profile.put("zoneIds", jdbc.queryForList("SELECT zone_id::text FROM delivery_partner_zone WHERE tenant_id=? AND partner_id=? ORDER BY zone_id", String.class, tenantId, id));
            return profile;
        } catch (EmptyResultDataAccessException missing) {
            return null;
        }
    }

    private Map<String, Object> incidentRow(UUID tenantId, UUID id) {
        try {
            return jdbc.queryForMap("""
                    SELECT id::text AS "id",delivery_id::text AS "deliveryId",type,severity,description,status,
                           reported_by AS "reportedBy",resolution,created_at AS "createdAt",resolved_at AS "resolvedAt"
                    FROM delivery_incident WHERE tenant_id=? AND id=?
                    """, tenantId, id);
        } catch (EmptyResultDataAccessException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found.");
        }
    }

    private Map<String, Object> tripRow(DeliveryPrincipal actor, UUID tripId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id::text AS "id",t.status,t.partner_id::text AS "partnerId",p.display_name AS "partnerName",
                       t.version,t.created_at AS "createdAt",t.updated_at AS "updatedAt"
                FROM delivery_trip t JOIN delivery_partner_profile p ON p.id=t.partner_id AND p.tenant_id=t.tenant_id
                WHERE t.tenant_id=? AND t.id=?
                """, actor.tenantId(), tripId);
        if (rows.isEmpty()) notFound();
        Map<String, Object> result = rows.get(0);
        result.put("stops", jdbc.queryForList("""
                SELECT s.stop_order AS "stopOrder",d.id::text AS "deliveryId",d.order_reference AS "orderReference",
                       d.dropoff_name AS "dropoffName",d.dropoff_address AS "dropoffAddress",d.status
                FROM delivery_trip_stop s JOIN delivery d ON d.id=s.delivery_id AND d.tenant_id=s.tenant_id
                WHERE s.tenant_id=? AND s.trip_id=? ORDER BY s.stop_order
                """, actor.tenantId(), tripId));
        return result;
    }

    private UUID ownPartnerId(DeliveryPrincipal actor) {
        UUID id = ownPartnerIdOrNull(actor);
        if (id == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Create a Delivery Partner profile before using partner operations.");
        return id;
    }

    private UUID ownPartnerIdOrNull(DeliveryPrincipal actor) {
        List<UUID> ids = jdbc.query("SELECT id FROM delivery_partner_profile WHERE tenant_id=? AND user_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), actor.tenantId(), actor.userId());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void assertResourceOwnership(DeliveryPrincipal actor, Map<String, Object> delivery) {
        if (!isPartner(actor)) return;
        UUID own = ownPartnerIdOrNull(actor);
        String assigned = String.valueOf(delivery.get("assignedPartnerId"));
        if (own == null || !own.toString().equals(assigned)) notFound();
    }

    private UUID assignedPartner(DeliveryPrincipal actor, Map<String, Object> delivery) {
        if (isPartner(actor)) {
            UUID own = ownPartnerId(actor);
            if (!own.toString().equals(String.valueOf(delivery.get("assignedPartnerId")))) notFound();
            return own;
        }
        Object value = delivery.get("assignedPartnerId");
        if (value == null) conflict("Delivery has no assigned partner.");
        return UUID.fromString(String.valueOf(value));
    }

    private void ensureAcceptedAssignment(UUID tenant, UUID deliveryId) {
        Integer accepted = jdbc.queryForObject("""
                SELECT count(*) FROM delivery_assignment WHERE tenant_id=? AND delivery_id=? AND active=TRUE AND status='ACCEPTED'
                """, Integer.class, tenant, deliveryId);
        if (accepted == null || accepted == 0) conflict("The partner must accept the assignment before progressing the delivery.");
    }

    private void closeActiveAssignment(UUID tenant, UUID deliveryId, String status, String reason) {
        jdbc.update("UPDATE delivery_assignment SET active=FALSE,status=?,reason=COALESCE(?,reason),responded_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND delivery_id=? AND active=TRUE",
                status, safe(reason), tenant, deliveryId);
    }

    private void requireTransitionPermission(DeliveryPrincipal actor, String target) {
        if (isPartner(actor)) {
            requirePermission(actor, "delivery:partner:self");
            if (Set.of("CANCELLED", "READY_FOR_DISPATCH", "ASSIGNED").contains(target)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Partner role cannot perform this Delivery transition.");
            }
        } else {
            requirePermission(actor, "delivery:manage");
        }
    }

    private void insertHistory(DeliveryPrincipal actor, UUID deliveryId, String from, String to, String reason, String key) {
        jdbc.update("""
                INSERT INTO delivery_status_history(tenant_id,delivery_id,from_status,to_status,reason,actor_user_id,actor_email,actor_role,idempotency_key)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, actor.tenantId(), deliveryId, from, to, safe(reason), actor.userId(), actor.email(), actor.role(), key);
    }

    private void insertSystemHistory(UUID tenant, UUID delivery, String from, String to, String reason) {
        jdbc.update("""
                INSERT INTO delivery_status_history(tenant_id,delivery_id,from_status,to_status,reason,actor_user_id,actor_email,actor_role)
                VALUES(?,?,?,?,?,0,'delivery-service','SYSTEM')
                """, tenant, delivery, from, to, reason);
    }

    private void audit(DeliveryPrincipal actor, String action, String resourceType, String resourceId, Object details) {
        jdbc.update("""
                INSERT INTO delivery_audit_event(tenant_id,actor_user_id,actor_email,actor_role,action,resource_type,resource_id,details)
                VALUES(?,?,?,?,?,?,?,?)
                """, actor.tenantId(), actor.userId(), actor.email(), actor.role(), action, resourceType, resourceId, json(details));
    }

    private void insertSystemAudit(UUID tenant, String action, String resourceType, String resourceId, String details) {
        jdbc.update("""
                INSERT INTO delivery_audit_event(tenant_id,actor_user_id,actor_email,actor_role,action,resource_type,resource_id,details)
                VALUES(?,0,'delivery-service','SYSTEM',?,?,?,?)
                """, tenant, action, resourceType, resourceId, details);
    }

    private void enqueueEvent(UUID tenant, UUID aggregate, String eventType, Map<String, Object> details) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("eventVersion", 1);
        payload.put("tenantId", tenant.toString());
        payload.put("deliveryId", aggregate.toString());
        payload.put("eventType", eventType);
        payload.put("occurredAt", OffsetDateTime.now().toString());
        jdbc.query("SELECT order_reference,recipient_user_id FROM delivery WHERE tenant_id=? AND id=?", rs -> {
            if (rs.next()) {
                payload.put("orderReference", rs.getString("order_reference"));
                Long recipientId = rs.getObject("recipient_user_id", Long.class);
                if (recipientId != null) payload.put("recipientUserId", recipientId);
            }
        }, tenant, aggregate);
        payload.put("details", details);
        jdbc.update("INSERT INTO delivery_outbox(event_id,tenant_id,aggregate_id,event_type,payload) VALUES(?,?,?,?,?)",
                eventId, tenant, aggregate, eventType, json(payload));
    }

    private Optional<UUID> replay(UUID tenant, String key, String operation, String payloadHash) {
        try {
            Map<String, Object> prior = jdbc.queryForMap("""
                    SELECT operation,payload_hash,resource_id FROM delivery_idempotency
                    WHERE tenant_id=? AND idempotency_key=?
                    """, tenant, key);
            if (!operation.equals(prior.get("operation")) || !payloadHash.equals(prior.get("payload_hash"))) {
                conflict("Idempotency-Key was already used for a different operation or payload.");
            }
            return Optional.of(UUID.fromString(String.valueOf(prior.get("resource_id"))));
        } catch (EmptyResultDataAccessException missing) {
            return Optional.empty();
        }
    }

    private void saveIdempotency(UUID tenant, String key, String operation, String payloadHash, UUID resourceId) {
        try {
            jdbc.update("""
                    INSERT INTO delivery_idempotency(tenant_id,idempotency_key,operation,payload_hash,resource_id)
                    VALUES(?,?,?,?,?)
                    """, tenant, key, operation, payloadHash, resourceId);
        } catch (org.springframework.dao.DuplicateKeyException concurrent) {
            // A parallel request with the same key committed first; this transaction rolls back so the effect happens once.
            conflict("A request with this Idempotency-Key is already being processed. Retry to receive its result.");
        }
    }

    private void enqueueOrderSync(UUID tenant, UUID deliveryId, Object orderId, String status) {
        if (orderId == null || !ORDER_SYNC_STATUSES.contains(status)) return;
        jdbc.update("INSERT INTO delivery_order_sync(id,tenant_id,delivery_id,order_id,delivery_status) VALUES(?,?,?,?,?)",
                UUID.randomUUID(), tenant, deliveryId, ((Number) orderId).longValue(), status);
    }

    private String hash(Object value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not hash the idempotent request.", failure);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Could not serialize Delivery event.", failure); }
    }

    private String requireIdempotencyKey(String key) {
        if (!hasText(key) || key.trim().length() > 128) badRequest("Provide an Idempotency-Key of 1 to 128 characters.");
        return key.trim();
    }

    private void requirePermission(DeliveryPrincipal actor, String permission) {
        if (actor == null || !actor.hasPermission(permission)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Delivery permission required: " + permission);
    }

    private void assertResourceOwnership(DeliveryPrincipal actor, String assignedPartnerId) {
        if (isPartner(actor) && !ownPartnerId(actor).toString().equals(assignedPartnerId)) notFound();
    }

    private boolean isPartner(DeliveryPrincipal actor) {
        return actor != null && "DELIVERY_PARTNER".equalsIgnoreCase(actor.role());
    }

    private String configValue(UUID tenant, String key, String fallback) {
        List<String> values = jdbc.queryForList("SELECT config_value FROM delivery_configuration WHERE tenant_id=? AND config_key=?", String.class, tenant, key);
        return values.isEmpty() ? fallback : values.get(0);
    }

    private static Map<String, Object> mapCurrentRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        var metadata = rs.getMetaData();
        for (int column = 1; column <= metadata.getColumnCount(); column++) map.put(metadata.getColumnLabel(column), rs.getObject(column));
        return map;
    }

    private static Map<String, Object> camelize(Map<String, Object> input) {
        Map<String, Object> output = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            StringBuilder name = new StringBuilder();
            boolean upper = false;
            for (char ch : key.toCharArray()) {
                if (ch == '_') upper = true;
                else { name.append(upper ? Character.toUpperCase(ch) : ch); upper = false; }
            }
            output.put(name.toString(), value);
        });
        return output;
    }

    private static String clean(String value) {
        return value == null ? null : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalizeOptional(String value) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String normalizeToken(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > maxLength || !normalized.matches("[A-Z0-9_-]+")) badRequest("Value contains unsupported characters.");
        return normalized;
    }

    private static String normalizePostalPrefix(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 24 || !normalized.matches("[A-Z0-9_-]+")) badRequest("Postal prefix contains unsupported characters.");
        return normalized;
    }

    private static boolean integerInRange(String value, int min, int max) {
        try { int number = Integer.parseInt(value); return number >= min && number <= max; }
        catch (Exception ignored) { return false; }
    }

    private static void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static void notFound() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery resource not found."); }
}
