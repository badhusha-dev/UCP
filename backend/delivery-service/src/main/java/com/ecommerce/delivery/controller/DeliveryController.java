package com.ecommerce.delivery.controller;

import com.ecommerce.delivery.security.DeliveryPrincipal;
import com.ecommerce.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import com.ecommerce.delivery.rules.DeliveryRuleRegistry;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {
    private final DeliveryService service;
    private final DeliveryRuleRegistry ruleRegistry;

    public DeliveryController(DeliveryService service, DeliveryRuleRegistry ruleRegistry) {
        this.service = service;
        this.ruleRegistry = ruleRegistry;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.dashboard(actor);
    }

    @GetMapping("/deliveries")
    public List<Map<String, Object>> deliveries(@AuthenticationPrincipal DeliveryPrincipal actor,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "100") int limit) {
        return service.listDeliveries(actor, status, limit);
    }

    @PostMapping("/deliveries")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createDelivery(@AuthenticationPrincipal DeliveryPrincipal actor,
                                               @Valid @RequestBody DeliveryCommands.CreateDelivery request,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.createDelivery(actor, request, idempotencyKey);
    }

    @GetMapping("/deliveries/{id}")
    public Map<String, Object> delivery(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id) {
        return service.getDelivery(actor, id);
    }

    @PostMapping("/deliveries/from-order")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createFromOrder(@AuthenticationPrincipal DeliveryPrincipal actor,
                                               @Valid @RequestBody DeliveryCommands.CreateFromOrder request,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.createDeliveryFromOrder(actor, request, idempotencyKey);
    }

    @GetMapping("/assignments/mine")
    public List<Map<String, Object>> myAssignments(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.myAssignments(actor);
    }

    @GetMapping("/my/deliveries")
    public List<Map<String, Object>> myDeliveries(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.myDeliveries(actor);
    }

    @GetMapping("/my/deliveries/{id}")
    public Map<String, Object> myDelivery(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id) {
        return service.myDelivery(actor, id);
    }

    @PostMapping("/my/deliveries/{id}/delivery-code")
    public ResponseEntity<Map<String, Object>> deliveryCode(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.issueDeliveryCode(actor, id));
    }

    @GetMapping("/rules")
    public Map<String, Object> rules(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return ruleRegistry.overview(actor);
    }

    @PutMapping("/rules/draft")
    public Map<String, Object> saveRulesDraft(@AuthenticationPrincipal DeliveryPrincipal actor, @Valid @RequestBody DeliveryCommands.RuleDraft request) {
        return ruleRegistry.saveDraft(actor, request.rules());
    }

    @PostMapping("/rules/draft/validate")
    public Map<String, Object> validateRulesDraft(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return ruleRegistry.validateDraft(actor);
    }

    @PostMapping("/rules/draft/publish")
    public Map<String, Object> publishRulesDraft(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return ruleRegistry.publishDraft(actor);
    }

    @PostMapping("/rules/rollback")
    public Map<String, Object> rollbackRules(@AuthenticationPrincipal DeliveryPrincipal actor, @Valid @RequestBody DeliveryCommands.RuleRollback request) {
        return ruleRegistry.rollback(actor, request.version());
    }

    @PutMapping("/deliveries/{id}/readiness")
    public Map<String, Object> readiness(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                         @Valid @RequestBody DeliveryCommands.Readiness request,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.setPickupReadiness(actor, id, request, idempotencyKey);
    }

    @PostMapping("/deliveries/{id}/transitions")
    public Map<String, Object> transition(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                         @Valid @RequestBody DeliveryCommands.Transition request,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.transition(actor, id, request, idempotencyKey);
    }

    @GetMapping("/dispatch/queue")
    public List<Map<String, Object>> dispatchQueue(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.dispatchQueue(actor);
    }

    @GetMapping("/dispatch/deliveries/{id}/candidates")
    public List<Map<String, Object>> candidates(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id) {
        return service.assignmentCandidates(actor, id);
    }

    @PostMapping("/dispatch/deliveries/{id}/assign")
    public Map<String, Object> assign(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                      @Valid @RequestBody DeliveryCommands.Assign request,
                                      @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.assign(actor, id, request, idempotencyKey);
    }

    @PostMapping("/assignments/{id}/response")
    public Map<String, Object> assignmentResponse(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                                  @Valid @RequestBody DeliveryCommands.AssignmentResponse request,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.respondToAssignment(actor, id, request, idempotencyKey);
    }

    @GetMapping("/partners")
    public List<Map<String, Object>> partners(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.listPartners(actor);
    }

    @GetMapping("/partners/me")
    public Map<String, Object> myPartnerProfile(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.myPartnerProfile(actor);
    }

    @PutMapping("/partners/me")
    public Map<String, Object> saveMyPartnerProfile(@AuthenticationPrincipal DeliveryPrincipal actor,
                                                     @Valid @RequestBody DeliveryCommands.PartnerProfile request) {
        return service.saveMyPartnerProfile(actor, request);
    }

    @PutMapping("/partners/me/availability")
    public Map<String, Object> availability(@AuthenticationPrincipal DeliveryPrincipal actor,
                                            @Valid @RequestBody DeliveryCommands.Availability request) {
        return service.setAvailability(actor, request);
    }

    @PatchMapping("/partners/{id}/review")
    public Map<String, Object> reviewPartner(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                             @Valid @RequestBody DeliveryCommands.PartnerReview request) {
        return service.reviewPartner(actor, id, request);
    }

    @GetMapping("/zones")
    public List<Map<String, Object>> zones(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.zones(actor);
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createZone(@AuthenticationPrincipal DeliveryPrincipal actor,
                                          @Valid @RequestBody DeliveryCommands.CreateZone request) {
        return service.saveZone(actor, null, request);
    }

    @PutMapping("/zones/{id}")
    public Map<String, Object> updateZone(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                          @Valid @RequestBody DeliveryCommands.CreateZone request) {
        return service.saveZone(actor, id, request);
    }

    @PostMapping("/deliveries/{id}/locations")
    public Map<String, Object> location(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                        @Valid @RequestBody DeliveryCommands.Location request) {
        return service.recordLocation(actor, id, request);
    }

    @PostMapping("/deliveries/{id}/proof")
    public Map<String, Object> proof(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                     @Valid @RequestBody DeliveryCommands.Proof request,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.recordProof(actor, id, request, idempotencyKey);
    }

    @GetMapping("/incidents")
    public List<Map<String, Object>> incidents(@AuthenticationPrincipal DeliveryPrincipal actor,
                                                @RequestParam(required = false) String status) {
        return service.incidents(actor, status);
    }

    @PostMapping("/deliveries/{id}/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> reportIncident(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                              @Valid @RequestBody DeliveryCommands.Incident request) {
        return service.reportIncident(actor, id, request);
    }

    @PatchMapping("/incidents/{id}/resolve")
    public Map<String, Object> resolveIncident(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                               @Valid @RequestBody DeliveryCommands.IncidentResolution request) {
        return service.resolveIncident(actor, id, request);
    }

    @GetMapping("/trips")
    public List<Map<String, Object>> trips(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.trips(actor);
    }

    @PostMapping("/trips")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTrip(@AuthenticationPrincipal DeliveryPrincipal actor,
                                          @Valid @RequestBody DeliveryCommands.CreateTrip request) {
        return service.createTrip(actor, request);
    }

    @PutMapping("/trips/{id}/status")
    public Map<String, Object> tripStatus(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable UUID id,
                                          @Valid @RequestBody DeliveryCommands.TripStatus request) {
        return service.updateTripStatus(actor, id, request.status(), request.expectedVersion());
    }

    @GetMapping("/pricing")
    public List<Map<String, Object>> pricing(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.pricing(actor);
    }

    @PutMapping("/pricing")
    public Map<String, Object> pricing(@AuthenticationPrincipal DeliveryPrincipal actor,
                                       @Valid @RequestBody DeliveryCommands.PricingPolicy request) {
        return service.savePricing(actor, request);
    }

    @GetMapping("/earnings")
    public Map<String, Object> earnings(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.earnings(actor);
    }

    @GetMapping("/reports")
    public Map<String, Object> reports(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.reports(actor);
    }

    @GetMapping("/configuration")
    public Map<String, String> configuration(@AuthenticationPrincipal DeliveryPrincipal actor) {
        return service.configuration(actor);
    }

    @PutMapping("/configuration/{key}")
    public Map<String, String> updateConfiguration(@AuthenticationPrincipal DeliveryPrincipal actor, @PathVariable String key,
                                                    @Valid @RequestBody DeliveryCommands.ConfigValue request) {
        return service.updateConfiguration(actor, key, request);
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@AuthenticationPrincipal DeliveryPrincipal actor,
                                            @RequestParam(defaultValue = "100") int limit) {
        return service.auditLog(actor, limit);
    }

    @GetMapping("/events")
    public List<Map<String, Object>> events(@AuthenticationPrincipal DeliveryPrincipal actor,
                                             @RequestParam(defaultValue = "100") int limit) {
        return service.outboxEvents(actor, limit);
    }
}
