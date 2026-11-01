package com.ecommerce.inventory.service;

import com.ecommerce.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventoryProducerServiceTest {

    @Test
    @DisplayName("Inventory events go to the outbox as RAW JSON on inventory.events, keyed by product id")
    void recordsRawEventInOutbox() throws Exception {
        OutboxWriter outbox = mock(OutboxWriter.class);
        new InventoryProducerService(outbox).publishInventoryEvent("STOCK_ADJUSTED", 5L, Map.of("quantity", 12));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).appendRaw(eq("inventory.events"), eq("5"), eq("STOCK_ADJUSTED"), payload.capture());
        JsonNode json = new ObjectMapper().readTree(payload.getValue());
        assertThat(json.get("eventType").asText()).isEqualTo("STOCK_ADJUSTED");
        assertThat(json.get("aggregateId").asText()).isEqualTo("5");
        assertThat(json.get("payload").get("quantity").asInt()).isEqualTo(12);
        assertThat(json.get("eventId").asText()).isNotBlank();
    }

    @Test
    @DisplayName("The RAW JSON is byte-for-byte what JsonSerializer sent before the outbox")
    void wireJsonMatchesJsonSerializer() throws Exception {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("unitCost", new BigDecimal("10.50"));
        details.put("quantity", 7L);
        details.put("receivedAt", LocalDateTime.of(2026, 11, 1, 10, 30));
        details.put("syncedAt", Instant.parse("2026-11-01T05:00:00Z"));
        details.put("locations", List.of(Map.of("code", "A-1", "onHand", 3)));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", "STOCK_RECEIVED");
        event.put("aggregateId", "5");
        event.put("payload", details);

        try (JsonSerializer<Object> serializer = new JsonSerializer<>()) {
            assertThat(InventoryProducerService.wireJson(event).getBytes(StandardCharsets.UTF_8))
                    .isEqualTo(serializer.serialize("inventory.events", event));
        }
    }
}
