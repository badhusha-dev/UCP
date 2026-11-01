package com.ecommerce.order.controller;

import com.ecommerce.order.dto.*;
import com.ecommerce.order.service.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/orders/admin/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Business Analytics", description = "Executive KPI engine, daily sales time-series, product performance, customer intelligence, alerts, and CSV exports")
@SecurityRequirement(name = "bearerAuth")
public class AdminAnalyticsController {

    private final AdminAnalyticsService analyticsService;

    @Operation(summary = "Get Executive KPI Overview", description = "Retrieves high-level commerce metrics with period-over-period delta comparisons (Manager/Admin only).")
    @GetMapping("/overview")
    public ResponseEntity<AdminOverviewAnalyticsDto> getOverview(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "LAST_30_DAYS") String timeRange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(analyticsService.getOverviewAnalytics(timeRange, startDate, endDate));
    }

    @Operation(summary = "Get Daily Sales & Orders Time Series", description = "Chronological daily data points for revenue, order volume, and status distribution (Manager/Admin only).")
    @GetMapping("/sales")
    public ResponseEntity<AdminSalesAnalyticsDto> getSales(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "LAST_30_DAYS") String timeRange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(analyticsService.getSalesAnalytics(timeRange, startDate, endDate));
    }

    @Operation(summary = "Get Product & Category Performance", description = "Top selling products by revenue and units, category breakdowns, and slow-moving items (Manager/Admin only).")
    @GetMapping("/products")
    public ResponseEntity<AdminProductAnalyticsDto> getProducts(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "LAST_30_DAYS") String timeRange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(analyticsService.getProductAnalytics(timeRange, startDate, endDate));
    }

    @Operation(summary = "Get Customer & Cohort Intelligence", description = "New vs returning customers, repeat purchase rates, and top customer spending rankings (Manager/Admin only).")
    @GetMapping("/customers")
    public ResponseEntity<AdminCustomerAnalyticsDto> getCustomers(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "LAST_30_DAYS") String timeRange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(analyticsService.getCustomerAnalytics(timeRange, startDate, endDate));
    }

    @Operation(summary = "Query Filtered Orders Table", description = "Server-side paginated, sorted, and filtered orders list for admin drill-down inspection (Manager/Admin only).")
    @GetMapping("/orders")
    public ResponseEntity<Page<AdminOrderRowDto>> getOrders(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        assertManagerOrAdmin(role);

        // Limit page size to prevent memory exhaustion
        if (size > 100) size = 100;
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return ResponseEntity.ok(analyticsService.getOrders(pageable, status, startDate, endDate, userId));
    }

    @Operation(summary = "Export Filtered Orders to RFC-4180 CSV", description = "Streams a spreadsheet-safe CSV report with formula-injection defenses (Manager/Admin only).")
    @GetMapping(value = "/reports/export", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String status) {
        assertManagerOrAdmin(role);

        String csvData = analyticsService.exportOrdersCsv(startDate, endDate, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ucp_orders_report_" + System.currentTimeMillis() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    @Operation(summary = "Get Operational Health & Anomaly Alerts", description = "Monitors delayed fulfillment, inactive carts, and high cancellation rate breaches (Manager/Admin only).")
    @GetMapping("/alerts")
    public ResponseEntity<AdminAlertsDto> getAlerts(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(analyticsService.getAlerts());
    }

    private void assertManagerOrAdmin(String role) {
        if (role == null || (!role.toUpperCase().contains("ADMIN") && !role.toUpperCase().contains("MANAGER"))) {
            log.warn("Security Alert: Unauthorized access attempt to Admin Analytics by role: {}", role);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Staff authorization (Manager or Admin) required.");
        }
    }
}
