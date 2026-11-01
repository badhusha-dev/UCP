package com.ecommerce.product.service;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ProductVariant;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.repository.ProductVariantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAvailabilityProjectionConsumer {

    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    @KafkaListener(topics = "inventory.events", groupId = "product-stock-projection")
    @Transactional
    public void onStockEvent(ConsumerRecord<String, byte[]> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            String eventType = event.path("eventType").asText("");
            boolean stockEvent = eventType.startsWith("STOCK_");
            boolean metadataEvent = "INVENTORY_METADATA_UPDATED".equals(eventType);
            if (!stockEvent && !metadataEvent) return;

            JsonNode payload = event.path("payload");
            long productId = payload.path("productId").asLong(event.path("aggregateId").asLong());
            if (productId <= 0) return;

            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) return;
            if (stockEvent) {
                JsonNode availableNode = payload.get("availableQuantity");
                if (availableNode == null || !availableNode.canConvertToInt()) return;
                int available = Math.max(0, availableNode.asInt());
                product.setStockQuantity(available);
                for (ProductVariant variant : variantRepository.findByProductId(productId)) {
                    variant.setStockQuantity(available);
                }
            } else {
                JsonNode costNode = payload.get("costPrice");
                if (costNode != null && !costNode.isNull()) product.setCostPrice(new BigDecimal(costNode.asText()));
                JsonNode thresholdNode = payload.get("reorderThreshold");
                if (thresholdNode != null && thresholdNode.canConvertToInt()) product.setReorderThreshold(thresholdNode.asInt());
            }
            productRepository.save(product);
            if (stockEvent) variantRepository.flush();
        } catch (Exception exception) {
            log.warn("Could not update product stock projection from Kafka message {}: {}",
                    record.offset(), exception.getMessage());
        }
    }
}
