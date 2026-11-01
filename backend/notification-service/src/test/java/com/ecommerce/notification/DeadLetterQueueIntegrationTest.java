package com.ecommerce.notification;

import com.ecommerce.notification.model.DeadLetterEvent;
import com.ecommerce.notification.repository.DeadLetterEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DeadLetterQueueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeadLetterEventRepository deadLetterEventRepository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        deadLetterEventRepository.deleteAll();
        when(kafkaTemplate.send(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("GET /api/notifications/dlq should return list of failed events")
    void testGetDeadLetters() throws Exception {
        DeadLetterEvent dle = DeadLetterEvent.builder()
                .topic("order-events")
                .partitionId(1)
                .offsetNumber(55L)
                .payload("{\"orderId\": 9999}")
                .exceptionMessage("Deserialization error")
                .status("FAILED")
                .build();
        deadLetterEventRepository.save(dle);

        mockMvc.perform(get("/api/notifications/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].topic").value("order-events"))
                .andExpect(jsonPath("$[0].status").value("FAILED"));
    }

    @Test
    @DisplayName("POST /api/notifications/dlq/{id}/replay should replay message and transition status to REPLAYED")
    void testReplayDeadLetter() throws Exception {
        DeadLetterEvent dle = DeadLetterEvent.builder()
                .topic("order-events")
                .partitionId(0)
                .offsetNumber(10L)
                .payload("{\"orderId\": 777,\"eventType\":\"ORDER_CREATED\"}")
                .exceptionMessage("Timeout connecting to downstream")
                .status("FAILED")
                .build();
        DeadLetterEvent saved = deadLetterEventRepository.save(dle);

        mockMvc.perform(post("/api/notifications/dlq/" + saved.getId() + "/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPLAYED"));

        DeadLetterEvent reloaded = deadLetterEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("REPLAYED");
    }
}
