package com.ecommerce.loyalty.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic loyaltyPointsEarnedTopic() {
        return TopicBuilder.name("loyalty.points.earned").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic loyaltyPointsRedeemedTopic() {
        return TopicBuilder.name("loyalty.points.redeemed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic loyaltyTierUpgradedTopic() {
        return TopicBuilder.name("loyalty.tier.upgraded").partitions(3).replicas(1).build();
    }
}
