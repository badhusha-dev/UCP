package com.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk catalog CSV import execution summary and error reporting")
public class CsvImportResultDto {

    @Schema(description = "Total lines processed from CSV", example = "25")
    private int totalRows;

    @Schema(description = "Count of products successfully imported or updated", example = "23")
    private int successfulImports;

    @Schema(description = "Count of rows rejected due to schema or validation violations", example = "2")
    private int failedRows;

    @Schema(description = "Detailed list of row-by-row error messages")
    private List<String> errorMessages;

    @Schema(description = "Execution mode: true for dry-run validation, false for persistent write", example = "false")
    private boolean dryRun;
}
