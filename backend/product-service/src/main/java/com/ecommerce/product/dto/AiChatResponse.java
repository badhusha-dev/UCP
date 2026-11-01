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
@Schema(description = "Verified AI response synthesized strictly from database catalog data")
public class AiChatResponse {

    @Schema(description = "Accurate response text strictly based on verified inventory and catalog specifications",
            example = "Yes, the Mechanical Keyboard features per-key RGB backlighting with mechanical switches. There are currently 100 units in stock priced at $129.99.")
    private String answer;

    @Schema(description = "Ground-truth catalog product citations used to generate answer")
    private List<String> sourceProducts;

    @Schema(description = "Suggested follow-up catalog questions")
    private List<String> suggestedQuestions;

    @Schema(description = "Zero-hallucination compliance verification flag", example = "true")
    private boolean verifiedAgainstDatabase;
}
