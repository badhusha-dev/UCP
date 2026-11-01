package com.ecommerce.product.service;

import com.ecommerce.product.dto.InventoryAnalyticsDto;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ProductCategory;
import com.ecommerce.product.repository.ProductCategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAnalyticsService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public InventoryAnalyticsDto getInventoryAnalytics() {
        List<Product> products = productRepository.findAll();
        List<ProductCategory> categories = categoryRepository.findAll();

        long totalUnits = 0;
        long lowStockCount = 0;
        long outOfStockCount = 0;
        long productsWithCost = 0;
        BigDecimal totalValuation = BigDecimal.ZERO;

        List<InventoryAnalyticsDto.LowStockItemDto> lowStockList = new ArrayList<>();
        Map<Long, List<Product>> productsByCategory = new HashMap<>();

        for (Product p : products) {
            int stock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
            int threshold = p.getReorderThreshold() != null ? p.getReorderThreshold() : 10;
            totalUnits += stock;

            if (stock == 0) {
                outOfStockCount++;
                lowStockList.add(mapToLowStockDto(p));
            } else if (stock <= threshold) {
                lowStockCount++;
                lowStockList.add(mapToLowStockDto(p));
            }

            if (p.getCostPrice() != null) {
                productsWithCost++;
                BigDecimal unitCost = p.getCostPrice();
                totalValuation = totalValuation.add(unitCost.multiply(BigDecimal.valueOf(stock)));
            }

            Long catId = p.getCategory() != null ? p.getCategory().getId() : 0L;
            productsByCategory.computeIfAbsent(catId, k -> new ArrayList<>()).add(p);
        }

        // Build category breakdown
        List<InventoryAnalyticsDto.CategoryInventorySummaryDto> categorySummaries = new ArrayList<>();
        Map<Long, String> categoryNames = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));
        categoryNames.put(0L, "Uncategorized");

        for (Map.Entry<Long, List<Product>> entry : productsByCategory.entrySet()) {
            Long catId = entry.getKey();
            List<Product> catProducts = entry.getValue();
            long catUnits = catProducts.stream().mapToLong(p -> p.getStockQuantity() != null ? p.getStockQuantity() : 0).sum();
            BigDecimal catValuation = catProducts.stream()
                    .filter(p -> p.getCostPrice() != null && p.getStockQuantity() != null)
                    .map(p -> p.getCostPrice().multiply(BigDecimal.valueOf(p.getStockQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            categorySummaries.add(InventoryAnalyticsDto.CategoryInventorySummaryDto.builder()
                    .categoryId(catId)
                    .categoryName(categoryNames.getOrDefault(catId, "General"))
                    .productCount((long) catProducts.size())
                    .totalUnits(catUnits)
                    .valuation(catValuation.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        double coverage = products.isEmpty() ? 0.0 : ((double) productsWithCost / products.size()) * 100.0;

        return InventoryAnalyticsDto.builder()
                .totalProducts((long) products.size())
                .totalUnitsInStock(totalUnits)
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .stockValuation(totalValuation.setScale(2, RoundingMode.HALF_UP))
                .costCoveragePercent(Math.round(coverage * 10.0) / 10.0)
                .lowStockItems(lowStockList)
                .categoryBreakdown(categorySummaries)
                .build();
    }

    private InventoryAnalyticsDto.LowStockItemDto mapToLowStockDto(Product p) {
        return InventoryAnalyticsDto.LowStockItemDto.builder()
                .id(p.getId())
                .name(p.getName())
                .stockQuantity(p.getStockQuantity())
                .reorderThreshold(p.getReorderThreshold() != null ? p.getReorderThreshold() : 10)
                .price(p.getPrice())
                .costPrice(p.getCostPrice())
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : "General")
                .build();
    }
}
