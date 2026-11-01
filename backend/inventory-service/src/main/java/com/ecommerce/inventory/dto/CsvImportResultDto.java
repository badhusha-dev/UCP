package com.ecommerce.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvImportResultDto {
    private int totalProcessed;
    private int updatedCount;
    private int createdCount;
    private int failedCount;
    private List<String> errors;
}
