package com.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Customer prompt for the verified AI catalog shopping assistant")
public class AiChatRequest {

    @NotBlank(message = "Question or prompt must not be blank")
    @Schema(description = "Customer question regarding product specifications, compatibility, stock, or policy",
            example = "Does the Mechanical Keyboard have RGB backlighting, and how many are in stock?",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    @Schema(description = "Optional target product ID context", example = "1")
    private Long productId;
}
