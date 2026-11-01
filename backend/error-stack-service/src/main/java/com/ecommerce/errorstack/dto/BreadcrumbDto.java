package com.ecommerce.errorstack.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreadcrumbDto {
    private OffsetDateTime timestamp;
    private String category; // e.g. "ui.click", "navigation", "http.request", "console"
    private String message;
    private String level;    // "info", "warning", "error"
    private Map<String, Object> data;
}
