package com.ecommerce.product.dto;

import com.ecommerce.product.model.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Structured response from natural language query parsing and catalog execution")
public class NaturalLanguageSearchResponse {

    @Schema(description = "Original natural language user query", example = "mechanical keyboard under $150 in stock")
    private String originalQuery;

    @Schema(description = "Structured search filters extracted by natural language parser")
    private Map<String, Object> extractedFilters;

    @Schema(description = "Keyword term extracted", example = "keyboard")
    private String extractedKeyword;

    @Schema(description = "Maximum price constraint extracted", example = "150.00")
    private BigDecimal maxPrice;

    @Schema(description = "In-stock constraint extracted", example = "true")
    private Boolean inStockOnly;

    @Schema(description = "Products matching the parsed criteria")
    private List<Product> matchingProducts;

    @Schema(description = "Match count", example = "1")
    private Integer totalMatches;
}
