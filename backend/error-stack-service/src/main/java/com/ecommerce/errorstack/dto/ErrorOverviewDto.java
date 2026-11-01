package com.ecommerce.errorstack.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorOverviewDto {
    private Long totalOccurrences;
    private Long totalGroups;
    private Long newErrorsCount;
    private Long unresolvedCriticalCount;
    private Long last24HoursCount;
    private Map<String, Long> serviceBreakdown;
    private Map<String, Long> severityBreakdown;
    private Map<String, Long> statusBreakdown;
    private Map<String, Long> hourlyTrend24h;
    private List<ErrorGroupDto> recentGroups;
}
