package com.ecommerce.errorstack.dto;

import com.ecommerce.errorstack.model.ErrorStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriageRequest {
    private ErrorStatus status;
    private String assignedTo;
}
