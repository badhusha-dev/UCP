package com.ecommerce.errorstack.dto;

import com.ecommerce.errorstack.model.ErrorSeverity;
import com.ecommerce.errorstack.model.ErrorStatus;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorGroupDto {
    private Long id;
    private String fingerprint;
    private String serviceName;
    private String environment;
    private String errorType;
    private String message;
    private ErrorSeverity severity;
    private ErrorStatus status;
    private String assignedTo;
    private Long occurrencesCount;
    private OffsetDateTime firstSeen;
    private OffsetDateTime lastSeen;
}
