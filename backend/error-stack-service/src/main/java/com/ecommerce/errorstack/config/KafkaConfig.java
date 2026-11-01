package com.ecommerce.errorstack.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String ERROR_EVENTS_TOPIC = "error-events";
    public static final String APPLICATION_ERRORS_TOPIC = "application-errors";

    @Bean
    public NewTopic errorEventsTopic() {
        return TopicBuilder.name(ERROR_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic applicationErrorsTopic() {
        return TopicBuilder.name(APPLICATION_ERRORS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
