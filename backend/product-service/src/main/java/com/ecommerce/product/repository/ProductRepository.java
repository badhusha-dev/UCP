package com.ecommerce.product.repository;

import com.ecommerce.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByActiveTrue();

    java.util.Optional<Product> findByIdAndTenantId(Long id, java.util.UUID tenantId);

    boolean existsByTenantIdAndSkuIgnoreCase(java.util.UUID tenantId, String sku);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND " +
           "(:active IS NULL OR p.active = :active) AND " +
           "(:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> searchAdmin(@Param("tenantId") java.util.UUID tenantId, @Param("query") String query,
                              @Param("active") Boolean active, Pageable pageable);

    List<Product> findByIdInAndActiveTrue(List<Long> ids);

    java.util.Optional<Product> findByIdAndActiveTrue(Long id);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
           "(:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> searchProducts(
            @Param("query") String query,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            Pageable pageable
    );

    List<Product> findByIdIn(List<Long> ids);

    @Query("SELECT p FROM Product p WHERE p.stockQuantity <= p.reorderThreshold AND p.stockQuantity > 0")
    List<Product> findLowStockProducts();

    @Query("SELECT p FROM Product p WHERE p.stockQuantity = 0")
    List<Product> findOutOfStockProducts();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.stockQuantity <= p.reorderThreshold AND p.stockQuantity > 0")
    long countLowStockProducts();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.stockQuantity = 0")
    long countOutOfStockProducts();
}
