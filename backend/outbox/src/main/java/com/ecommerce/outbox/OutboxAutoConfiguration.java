package com.ecommerce.outbox;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Wires the outbox into any service that has it on the classpath.
 *
 * <ul>
 *   <li>{@code ucp.outbox.initialize-schema=true} creates {@code outbox_events} at startup (tests; production uses
 *       the service's Flyway migration).</li>
 *   <li>{@code ucp.outbox.publisher.enabled=false} keeps events in the table without publishing them.</li>
 *   <li>{@code ucp.outbox.poll-interval-ms}, {@code batch-size}, {@code send-timeout}, {@code lock-duration} and
 *       {@code retention} tune the publisher.</li>
 * </ul>
 */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, KafkaAutoConfiguration.class})
@ConditionalOnBean(JdbcTemplate.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ucp.outbox", name = "initialize-schema", havingValue = "true")
    OutboxSchemaInitializer outboxSchemaInitializer(DataSource dataSource) {
        return new OutboxSchemaInitializer(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxRepository outboxRepository(JdbcTemplate jdbc, ObjectProvider<OutboxSchemaInitializer> schema) {
        schema.ifAvailable(initializer -> { }); // create the table before anything can write to it
        return new OutboxRepository(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxWriter outboxWriter(OutboxRepository repository) {
        return new OutboxWriter(repository, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(prefix = "ucp.outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
    @SuppressWarnings("unchecked")
    OutboxPublisher outboxPublisher(OutboxRepository repository, KafkaTemplate<?, ?> kafka,
                                    ObjectProvider<KafkaProperties> kafkaProperties,
                                    ObjectProvider<SslBundles> sslBundles,
                                    @Value("${ucp.outbox.batch-size:50}") int batchSize,
                                    @Value("${ucp.outbox.send-timeout:PT10S}") Duration sendTimeout,
                                    @Value("${ucp.outbox.lock-duration:PT60S}") Duration lockDuration,
                                    @Value("${ucp.outbox.retention:P7D}") Duration retention) {
        return new OutboxPublisher(repository, (KafkaTemplate<Object, Object>) kafka,
                rawTemplate(kafkaProperties.getIfAvailable(), sslBundles.getIfAvailable()), Clock.systemUTC(), batchSize,
                sendTimeout, lockDuration, retention);
    }

    /**
     * A string-serializer template built from the service's own spring.kafka settings, for RAW events. It is not a bean:
     * a second KafkaTemplate bean would make the services' own KafkaTemplate injection ambiguous. The publisher closes
     * its producer factory on shutdown.
     */
    static KafkaTemplate<String, String> rawTemplate(KafkaProperties properties, SslBundles sslBundles) {
        if (properties == null) return null;
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(sslBundles));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    static class OutboxSchemaInitializer implements InitializingBean {
        private final DataSource dataSource;

        OutboxSchemaInitializer(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void afterPropertiesSet() {
            new ResourceDatabasePopulator(new ClassPathResource("ucp-outbox/schema.sql")).execute(dataSource);
        }
    }
}
