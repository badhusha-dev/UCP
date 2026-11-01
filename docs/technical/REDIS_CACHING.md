# Redis Distributed Caching Architecture

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Role & Placement of Redis

In modern e-commerce architectures, product read volume typically outpaces checkout write volume by a ratio greater than 20:1. The Universal Commerce Platform integrates **Redis 7** (Port 6379) as an in-memory L2 cache in front of PostgreSQL for `product-service`.

```
[ Client Request ] ──> [ API Gateway ] ──> [ Product Service ]
                                                   │
                                          ┌────────┴────────┐
                                          ▼                 ▼
                                    [ Redis L2 Cache ]  ( PostgreSQL )
                                      (Sub-millisecond)   (Source of Truth)
```

---

## 2. Spring Cache Implementation

### 2.1 Serialization Configuration
Cache values are serialized into human-readable JSON using `GenericJackson2JsonRedisSerializer` rather than Java binary serialization. This prevents deserialization vulnerabilities and classloader version drift.

### 2.2 Cache Annotations
- **Read Through**:
  ```java
  @Cacheable(value = "products", key = "#id", unless = "#result == null")
  public ProductDto getProductById(Long id) {
      return productRepository.findById(id).map(this::toDto)
          .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
  }
  ```
- **Eviction on Modification**:
  ```java
  @CacheEvict(value = "products", key = "#id")
  public ProductDto updateProductStock(Long id, int quantity) {
      // Updates stock in PostgreSQL, invalidates Redis entry
  }
  ```

---

## 3. TTL Policy & Memory Management

- **Default TTL**: Set to 3600 seconds (1 hour).
- **Eviction Policy**: `volatile-lru` (Evicts keys with an expire set using an LRU algorithm).
- **Max Memory**: Capped at 512MB in container configuration.

---

## 4. Verification & Health Monitoring

Verify Redis cache connectivity and hit rates:
```bash
# Ping Redis directly inside container
docker exec ecommerce-platform-redis-1 redis-cli ping
# Output: PONG

# Inspect cached keys
docker exec ecommerce-platform-redis-1 redis-cli keys "products::*"

# Monitor real-time commands
docker exec ecommerce-platform-redis-1 redis-cli monitor
```
