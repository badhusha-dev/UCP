package com.ecommerce.order.rma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

/**
 * Read-only lookups against the services that own the data: the Product service (category, current price) and the Stock service
 * (availability). Failures return empty results; callers decide whether that blocks the operation (it always blocks an exchange).
 */
@Slf4j
@Component
public class CatalogClient {
    public record ProductInfo(long id, String name, BigDecimal price, Long categoryId, boolean active) {}

    private final RestTemplate rest;
    private final ObjectMapper mapper;

    @Value("${services.product.url:http://localhost:8082}") private String productUrl;
    @Value("${services.stock.url:http://localhost:8090}") private String stockUrl;

    public CatalogClient(RestTemplate rest, ObjectMapper mapper) { this.rest = rest; this.mapper = mapper; }

    public Optional<ProductInfo> product(long id) {
        try {
            JsonNode n = mapper.readTree(rest.getForObject(productUrl + "/api/products/" + id, String.class));
            return Optional.of(new ProductInfo(id, n.path("name").asText(""), n.hasNonNull("price") ? n.get("price").decimalValue().setScale(2, java.math.RoundingMode.HALF_UP) : null,
                    n.path("category").hasNonNull("id") ? n.path("category").get("id").asLong() : null, n.path("active").asBoolean(true)));
        } catch (Exception e) { log.debug("Product lookup for {} failed: {}", id, e.getMessage()); return Optional.empty(); }
    }

    /** Category per product id; unknown products or an unreachable service simply produce no entry. */
    public Map<Long, Long> categories(Collection<Long> productIds) {
        Map<Long, Long> out = new HashMap<>();
        for (Long id : new LinkedHashSet<>(productIds)) product(id).ifPresent(p -> { if (p.categoryId() != null) out.put(id, p.categoryId()); });
        return out;
    }

    public OptionalInt availableStock(long productId) {
        try {
            JsonNode n = mapper.readTree(rest.getForObject(stockUrl + "/api/stock/" + productId, String.class));
            return n.hasNonNull("availableQuantity") ? OptionalInt.of(n.get("availableQuantity").asInt()) : OptionalInt.empty();
        } catch (Exception e) { log.debug("Stock lookup for {} failed: {}", productId, e.getMessage()); return OptionalInt.empty(); }
    }
}
