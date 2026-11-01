package com.ecommerce.notification;

import com.ecommerce.notification.dto.OrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventCompatibilityTest {

    // A strict mapper (FAIL_ON_UNKNOWN_PROPERTIES on) is the worst case for a consumer.
    private final ObjectMapper strict = new ObjectMapper();

    @Test
    @DisplayName("An order event with tenantId parses, and the tenant is available to the consumer")
    void parsesEventWithTenant() throws Exception {
        String payload = "{\"eventType\":\"ORDER_CREATED\",\"orderId\":12,\"userId\":3,\"status\":\"CREATED\","
                + "\"totalAmount\":49.90,\"reason\":null,\"referenceId\":null,"
                + "\"tenantId\":\"aaaaaaaa-0000-4000-8000-00000000000a\"}";

        OrderEvent event = strict.readValue(payload, OrderEvent.class);

        assertThat(event.getOrderId()).isEqualTo(12L);
        assertThat(event.getTenantId()).isEqualTo("aaaaaaaa-0000-4000-8000-00000000000a");
    }

    @Test
    @DisplayName("An order event published before tenantId existed still parses")
    void parsesEventWithoutTenant() throws Exception {
        String payload = "{\"eventType\":\"ORDER_CREATED\",\"orderId\":12,\"userId\":3,\"status\":\"CREATED\"}";

        OrderEvent event = strict.readValue(payload, OrderEvent.class);

        assertThat(event.getTenantId()).isNull();
    }
}
