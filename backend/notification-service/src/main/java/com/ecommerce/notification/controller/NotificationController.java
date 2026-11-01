package com.ecommerce.notification.controller;

import com.ecommerce.notification.model.DeadLetterEvent;
import com.ecommerce.notification.model.Notification;
import com.ecommerce.notification.repository.NotificationRepository;
import com.ecommerce.notification.service.DeadLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications & DLQ", description = "Endpoints for managing user alerts, notifications, and Dead Letter Queue (DLQ) operations")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final DeadLetterService deadLetterService;

    @Operation(summary = "Get User Notifications", description = "Retrieves all notifications for a specific user ordered by creation time descending.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notifications retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Notification.class))))
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getUserNotifications(
            @Parameter(description = "Recipient user identifier", example = "1", required = true)
            @PathVariable Long userId) {
        return ResponseEntity.ok(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Operation(summary = "Get Unread Notifications", description = "Retrieves only unread notifications for a specific user.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unread notifications retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Notification.class))))
    })
    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<List<Notification>> getUnreadUserNotifications(
            @Parameter(description = "Recipient user identifier", example = "1", required = true)
            @PathVariable Long userId) {
        return ResponseEntity.ok(notificationRepository.findByUserIdAndIsReadFalse(userId));
    }

    @Operation(summary = "Mark Notification as Read", description = "Sets read flag to true for a given notification ID.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification marked as read")
    })
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(description = "Notification ID", example = "301", required = true)
            @PathVariable Long id) {
        notificationRepository.findById(id).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Mark All Notifications as Read", description = "Marks all unread notifications for a user as read.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All user notifications marked as read")
    })
    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @Parameter(description = "Recipient user identifier", example = "1", required = true)
            @PathVariable Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete Notification", description = "Deletes a notification record by ID.",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification deleted successfully")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @Parameter(description = "Notification ID", example = "301", required = true)
            @PathVariable Long id) {
        notificationRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // ==================== Dead Letter Queue (DLQ) Endpoints ====================

    @Operation(summary = "List Dead Letter Events", description = "Inspects failed messages in Dead Letter Queue with optional status filter.", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dead letter events retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DeadLetterEvent.class))))
    })
    @GetMapping("/dlq")
    public ResponseEntity<List<DeadLetterEvent>> getDeadLetterEvents(
            @Parameter(description = "Filter by event status (FAILED, REPLAYED, RESOLVED)", example = "FAILED")
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(deadLetterService.getAllDeadLetters(status));
    }

    @Operation(summary = "Get Dead Letter Event by ID", description = "Retrieves raw payload and exception details for a DLQ event.", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dead letter event found",
                    content = @Content(schema = @Schema(implementation = DeadLetterEvent.class))),
            @ApiResponse(responseCode = "404", description = "Dead letter event not found")
    })
    @GetMapping("/dlq/{id}")
    public ResponseEntity<DeadLetterEvent> getDeadLetterEventById(
            @Parameter(description = "Dead letter event ID", example = "1", required = true)
            @PathVariable Long id) {
        return deadLetterService.getDeadLetterById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Replay Dead Letter Event", description = "Republishes a failed message from DLQ back to Kafka topic and updates status to REPLAYED.", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Event replayed successfully to Kafka",
                    content = @Content(schema = @Schema(implementation = DeadLetterEvent.class))),
            @ApiResponse(responseCode = "404", description = "Dead letter event not found"),
            @ApiResponse(responseCode = "500", description = "Replay error occurred")
    })
    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<?> replayDeadLetterEvent(
            @Parameter(description = "Dead letter event ID", example = "1", required = true)
            @PathVariable Long id) {
        try {
            DeadLetterEvent replayed = deadLetterService.replayDeadLetter(id);
            return ResponseEntity.ok(replayed);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
