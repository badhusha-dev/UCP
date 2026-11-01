package com.ecommerce.order.service;

import com.ecommerce.order.dto.*;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.CustomerCartRepository;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final CustomerCartRepository customerCartRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional(readOnly = true)
    public AdminOverviewAnalyticsDto getOverviewAnalytics(String timeRange, LocalDateTime customStart, LocalDateTime customEnd) {
        TimeRangeBounds bounds = resolveBounds(timeRange, customStart, customEnd);
        TimeRangeBounds prevBounds = resolvePreviousBounds(bounds);

        PeriodMetrics curr = calculatePeriodMetrics(bounds.start, bounds.end);
        PeriodMetrics prev = calculatePeriodMetrics(prevBounds.start, prevBounds.end);

        long activeCarts = customerCartRepository.countByStatus("ACTIVE");

        return AdminOverviewAnalyticsDto.builder()
                .timeRange(bounds.label)
                .startDate(bounds.start)
                .endDate(bounds.end)
                .timezone("UTC")
                .grossMerchandiseValue(buildCard(curr.gmv, prev.gmv))
                .netSales(buildCard(curr.netSales, prev.netSales))
                .ordersPlaced(buildCard(curr.ordersPlaced, prev.ordersPlaced))
                .deliveredOrders(buildCard(curr.deliveredOrders, prev.deliveredOrders))
                .averageOrderValue(buildCard(curr.aov, prev.aov))
                .cancelledOrders(buildCard(curr.cancelledOrders, prev.cancelledOrders))
                .cancellationRate(buildCard(curr.cancellationRate, prev.cancellationRate))
                .refundAmount(buildCard(curr.refundAmount, prev.refundAmount))
                .refundRate(buildCard(curr.refundRate, prev.refundRate))
                .unitsSold(buildCard(curr.unitsSold, prev.unitsSold))
                .newCustomers(buildCard(curr.newCustomers, prev.newCustomers))
                .returningCustomers(buildCard(curr.returningCustomers, prev.returningCustomers))
                .lowStockCount(0L) // Enriched by frontend or gateway from product-service
                .activeCartsCount(activeCarts)
                .disclaimer("Calculated from primary database transaction ledger. Gross profit is withheld until wholesale product cost coverage is verified.")
                .build();
    }

    @Transactional(readOnly = true)
    public AdminSalesAnalyticsDto getSalesAnalytics(String timeRange, LocalDateTime customStart, LocalDateTime customEnd) {
        TimeRangeBounds bounds = resolveBounds(timeRange, customStart, customEnd);
        TimeRangeBounds prevBounds = resolvePreviousBounds(bounds);

        List<Order> currOrders = orderRepository.findByCreatedAtBetween(bounds.start, bounds.end);
        List<Order> prevOrders = orderRepository.findByCreatedAtBetween(prevBounds.start, prevBounds.end);

        Map<String, List<Order>> currByDate = currOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getCreatedAt().format(DATE_FORMATTER)));

        Map<String, List<Order>> prevByDate = prevOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getCreatedAt().format(DATE_FORMATTER)));

        List<AdminSalesAnalyticsDto.DailySalesPointDto> dailySeries = new ArrayList<>();
        LocalDate currDate = bounds.start.toLocalDate();
        LocalDate endDate = bounds.end.toLocalDate();
        long days = ChronoUnit.DAYS.between(currDate, endDate);
        if (days < 1) days = 1;

        LocalDate prevDate = prevBounds.start.toLocalDate();

        BigDecimal periodTotalGmv = BigDecimal.ZERO;
        BigDecimal periodTotalNet = BigDecimal.ZERO;
        long periodTotalOrders = currOrders.size();

        Map<String, Long> statusBreakdown = new HashMap<>();
        Map<String, BigDecimal> salesByStatus = new HashMap<>();

        for (Order o : currOrders) {
            String st = o.getStatus() != null ? o.getStatus().toUpperCase() : "UNKNOWN";
            statusBreakdown.put(st, statusBreakdown.getOrDefault(st, 0L) + 1);

            BigDecimal amt = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
            salesByStatus.put(st, salesByStatus.getOrDefault(st, BigDecimal.ZERO).add(amt));

            if (!"CANCELLED".equals(st)) {
                BigDecimal disc = o.getDiscountAmount() != null ? o.getDiscountAmount() : BigDecimal.ZERO;
                periodTotalGmv = periodTotalGmv.add(amt.add(disc));
                periodTotalNet = periodTotalNet.add(amt);
            }
        }

        for (int i = 0; i <= days; i++) {
            LocalDate d = currDate.plusDays(i);
            if (d.isAfter(endDate)) break;

            String dateStr = d.format(DATE_FORMATTER);
            List<Order> dayOrders = currByDate.getOrDefault(dateStr, Collections.emptyList());

            BigDecimal dayGmv = BigDecimal.ZERO;
            BigDecimal dayNet = BigDecimal.ZERO;
            long dayUnits = 0;

            for (Order o : dayOrders) {
                if (!"CANCELLED".equalsIgnoreCase(o.getStatus())) {
                    BigDecimal amt = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal disc = o.getDiscountAmount() != null ? o.getDiscountAmount() : BigDecimal.ZERO;
                    dayGmv = dayGmv.add(amt.add(disc));
                    dayNet = dayNet.add(amt);
                    if (o.getItems() != null) {
                        for (OrderItem item : o.getItems()) {
                            dayUnits += item.getQuantity() != null ? item.getQuantity() : 0;
                        }
                    }
                }
            }

            LocalDate compDate = prevDate.plusDays(i);
            String compDateStr = compDate.format(DATE_FORMATTER);
            List<Order> compOrders = prevByDate.getOrDefault(compDateStr, Collections.emptyList());
            BigDecimal compNet = BigDecimal.ZERO;
            for (Order co : compOrders) {
                if (!"CANCELLED".equalsIgnoreCase(co.getStatus())) {
                    compNet = compNet.add(co.getTotalAmount() != null ? co.getTotalAmount() : BigDecimal.ZERO);
                }
            }

            dailySeries.add(AdminSalesAnalyticsDto.DailySalesPointDto.builder()
                    .date(dateStr)
                    .gmv(dayGmv.setScale(2, RoundingMode.HALF_UP))
                    .netSales(dayNet.setScale(2, RoundingMode.HALF_UP))
                    .orderCount((long) dayOrders.size())
                    .unitsSold(dayUnits)
                    .comparisonSales(compNet.setScale(2, RoundingMode.HALF_UP))
                    .comparisonOrderCount((long) compOrders.size())
                    .build());
        }

        return AdminSalesAnalyticsDto.builder()
                .dailySeries(dailySeries)
                .statusBreakdown(statusBreakdown)
                .salesByStatus(salesByStatus)
                .periodTotalGmv(periodTotalGmv.setScale(2, RoundingMode.HALF_UP))
                .periodTotalNetSales(periodTotalNet.setScale(2, RoundingMode.HALF_UP))
                .periodTotalOrders(periodTotalOrders)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminProductAnalyticsDto getProductAnalytics(String timeRange, LocalDateTime customStart, LocalDateTime customEnd) {
        TimeRangeBounds bounds = resolveBounds(timeRange, customStart, customEnd);

        List<Object[]> byRevenueRaw = orderItemRepository.findTopSellingByRevenue(bounds.start, bounds.end);
        List<Object[]> byUnitsRaw = orderItemRepository.findTopSellingByUnits(bounds.start, bounds.end);

        List<AdminProductAnalyticsDto.ProductPerformanceDto> topByRevenue = mapProductPerformance(byRevenueRaw);
        List<AdminProductAnalyticsDto.ProductPerformanceDto> topByUnits = mapProductPerformance(byUnitsRaw);

        // Identify slow-moving items (reverse of top by units or units <= 2)
        List<AdminProductAnalyticsDto.ProductPerformanceDto> slowMoving = topByUnits.stream()
                .filter(p -> p.getUnitsSold() != null && p.getUnitsSold() <= 3)
                .collect(Collectors.toList());

        // Category performance aggregation
        Map<String, AdminProductAnalyticsDto.CategoryPerformanceDto> catMap = new HashMap<>();
        BigDecimal totalRevenueAll = BigDecimal.ZERO;

        for (AdminProductAnalyticsDto.ProductPerformanceDto p : topByRevenue) {
            String cat = guessCategory(p.getProductName());
            BigDecimal rev = p.getTotalRevenue() != null ? p.getTotalRevenue() : BigDecimal.ZERO;
            long units = p.getUnitsSold() != null ? p.getUnitsSold() : 0L;
            long ordCount = p.getOrderCount() != null ? p.getOrderCount() : 0L;

            totalRevenueAll = totalRevenueAll.add(rev);

            AdminProductAnalyticsDto.CategoryPerformanceDto existing = catMap.computeIfAbsent(cat, k ->
                    AdminProductAnalyticsDto.CategoryPerformanceDto.builder()
                            .category(k)
                            .unitsSold(0L)
                            .totalRevenue(BigDecimal.ZERO)
                            .orderCount(0L)
                            .revenuePercentage(0.0)
                            .build()
            );

            existing.setUnitsSold(existing.getUnitsSold() + units);
            existing.setTotalRevenue(existing.getTotalRevenue().add(rev));
            existing.setOrderCount(existing.getOrderCount() + ordCount);
        }

        // Calculate revenue percentages
        for (AdminProductAnalyticsDto.CategoryPerformanceDto c : catMap.values()) {
            if (totalRevenueAll.compareTo(BigDecimal.ZERO) > 0) {
                double pct = c.getTotalRevenue().divide(totalRevenueAll, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
                c.setRevenuePercentage(Math.round(pct * 10.0) / 10.0);
            }
        }

        return AdminProductAnalyticsDto.builder()
                .topProductsByRevenue(topByRevenue)
                .topProductsByUnits(topByUnits)
                .slowMovingProducts(slowMoving)
                .categoryPerformance(new ArrayList<>(catMap.values()))
                .build();
    }

    @Transactional(readOnly = true)
    public AdminCustomerAnalyticsDto getCustomerAnalytics(String timeRange, LocalDateTime customStart, LocalDateTime customEnd) {
        TimeRangeBounds bounds = resolveBounds(timeRange, customStart, customEnd);

        List<Order> currentOrders = orderRepository.findByCreatedAtBetween(bounds.start, bounds.end);
        Set<Long> uniqueUserIds = currentOrders.stream()
                .map(Order::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        long newCustomers = 0;
        long returningCustomers = 0;

        for (Long userId : uniqueUserIds) {
            long priorCount = orderRepository.findByUserId(userId).stream()
                    .filter(o -> o.getCreatedAt().isBefore(bounds.start) && !"CANCELLED".equals(o.getStatus()))
                    .count();
            if (priorCount == 0) {
                newCustomers++;
            } else {
                returningCustomers++;
            }
        }

        List<Object[]> customerSummaries = orderRepository.findCustomerOrderSummaries();
        long repeatCustomerCount = 0;
        List<AdminCustomerAnalyticsDto.CustomerSpendingDto> topCustomers = new ArrayList<>();

        for (Object[] row : customerSummaries) {
            if (row == null || row[0] == null) continue;
            Long userId = ((Number) row[0]).longValue();
            Long orderCount = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            BigDecimal totalSpent = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
            LocalDateTime firstOrder = (LocalDateTime) row[3];
            LocalDateTime lastOrder = (LocalDateTime) row[4];

            if (orderCount >= 2) {
                repeatCustomerCount++;
            }

            BigDecimal aov = orderCount > 0 ? totalSpent.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            if (topCustomers.size() < 10) {
                topCustomers.add(AdminCustomerAnalyticsDto.CustomerSpendingDto.builder()
                        .userId(userId)
                        .orderCount(orderCount)
                        .totalSpent(totalSpent.setScale(2, RoundingMode.HALF_UP))
                        .averageOrderValue(aov)
                        .firstOrderDate(firstOrder)
                        .lastOrderDate(lastOrder)
                        .build());
            }
        }

        double repeatRate = customerSummaries.isEmpty() ? 0.0 : ((double) repeatCustomerCount / customerSummaries.size()) * 100.0;
        double avgOrders = uniqueUserIds.isEmpty() ? 0.0 : ((double) currentOrders.size() / uniqueUserIds.size());

        return AdminCustomerAnalyticsDto.builder()
                .totalUniqueCustomers((long) uniqueUserIds.size())
                .newCustomers(newCustomers)
                .returningCustomers(returningCustomers)
                .repeatPurchaseRate(Math.round(repeatRate * 10.0) / 10.0)
                .averageOrdersPerCustomer(Math.round(avgOrders * 10.0) / 10.0)
                .topCustomers(topCustomers)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<AdminOrderRowDto> getOrders(Pageable pageable, String status, LocalDateTime start, LocalDateTime end, Long userId) {
        Specification<Order> spec = buildOrderFilterSpec(status, start, end, userId);
        Page<Order> page = orderRepository.findAll(spec, pageable);
        return page.map(this::mapToAdminOrderRow);
    }

    @Transactional(readOnly = true)
    public AdminAlertsDto getAlerts() {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        long delayedCount = orderRepository.countDelayedOrders(twentyFourHoursAgo);

        OffsetDateTime twoHoursAgo = OffsetDateTime.now().minusHours(2);
        long abandonedCarts = customerCartRepository.countByStatusAndUpdatedAtBefore("ACTIVE", twoHoursAgo);

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long recentTotal = orderRepository.countOrdersInPeriod(sevenDaysAgo, LocalDateTime.now());
        long recentCancelled = orderRepository.countCancelledOrdersInPeriod(sevenDaysAgo, LocalDateTime.now());

        double cancelRate = recentTotal > 0 ? ((double) recentCancelled / recentTotal) * 100.0 : 0.0;
        boolean highCancelAlert = cancelRate > 10.0;

        List<String> warnings = new ArrayList<>();
        if (delayedCount > 0) {
            warnings.add(delayedCount + " orders have been pending fulfillment for over 24 hours.");
        }
        if (abandonedCarts > 0) {
            warnings.add(abandonedCarts + " customer carts have been inactive for over 2 hours.");
        }
        if (highCancelAlert) {
            warnings.add("Order cancellation rate (" + Math.round(cancelRate * 10.0) / 10.0 + "%) exceeds the 10% threshold.");
        }

        return AdminAlertsDto.builder()
                .delayedFulfillmentCount(delayedCount)
                .abandonedCartsCount(abandonedCarts)
                .highCancellationRateAlert(highCancelAlert)
                .currentCancellationRate(Math.round(cancelRate * 10.0) / 10.0)
                .warnings(warnings)
                .build();
    }

    @Transactional(readOnly = true)
    public String exportOrdersCsv(LocalDateTime start, LocalDateTime end, String status) {
        Specification<Order> spec = buildOrderFilterSpec(status, start, end, null);
        List<Order> orders = orderRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

        StringBuilder csv = new StringBuilder();
        // RFC-4180 header
        csv.append("Order ID,Created At (UTC),Customer ID,Status,Total Amount,Discount Amount,Coupon Code,Carrier,Tracking Number,Item Count\r\n");

        for (Order o : orders) {
            csv.append(sanitizeForCsv(String.valueOf(o.getId()))).append(",");
            csv.append(sanitizeForCsv(o.getCreatedAt() != null ? o.getCreatedAt().toString() : "")).append(",");
            csv.append(sanitizeForCsv(String.valueOf(o.getUserId()))).append(",");
            csv.append(sanitizeForCsv(o.getStatus())).append(",");
            csv.append(sanitizeForCsv(o.getTotalAmount() != null ? o.getTotalAmount().toPlainString() : "0.00")).append(",");
            csv.append(sanitizeForCsv(o.getDiscountAmount() != null ? o.getDiscountAmount().toPlainString() : "0.00")).append(",");
            csv.append(sanitizeForCsv(o.getCouponCode() != null ? o.getCouponCode() : "")).append(",");
            csv.append(sanitizeForCsv(o.getCarrier() != null ? o.getCarrier() : "")).append(",");
            csv.append(sanitizeForCsv(o.getTrackingNumber() != null ? o.getTrackingNumber() : "")).append(",");
            csv.append(sanitizeForCsv(String.valueOf(o.getItems() != null ? o.getItems().size() : 0))).append("\r\n");
        }

        return csv.toString();
    }

    /**
     * OWASP CSV Injection Defense: Neutralize cells starting with =, +, -, @, \t, \r with leading single quote.
     */
    public static String sanitizeForCsv(String value) {
        if (value == null) return "\"\"";
        String trimmed = value.trim();
        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") ||
                trimmed.startsWith("@") || trimmed.startsWith("\t") || trimmed.startsWith("\r")) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private Specification<Order> buildOrderFilterSpec(String status, LocalDateTime start, LocalDateTime end, Long userId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.toUpperCase()));
            }
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // --- Helper Calculation Methods ---

    private PeriodMetrics calculatePeriodMetrics(LocalDateTime start, LocalDateTime end) {
        List<Order> orders = orderRepository.findByCreatedAtBetween(start, end);
        OffsetDateTime startOffset = start.atZone(ZoneId.of("UTC")).toOffsetDateTime();
        OffsetDateTime endOffset = end.atZone(ZoneId.of("UTC")).toOffsetDateTime();

        BigDecimal gmv = BigDecimal.ZERO;
        BigDecimal netSales = BigDecimal.ZERO;
        long delivered = 0;
        long cancelled = 0;
        long validOrderCount = 0;
        Set<Long> periodUserIds = new HashSet<>();

        for (Order o : orders) {
            String st = o.getStatus() != null ? o.getStatus().toUpperCase() : "UNKNOWN";
            if ("CANCELLED".equals(st)) {
                cancelled++;
            } else {
                validOrderCount++;
                BigDecimal amt = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal disc = o.getDiscountAmount() != null ? o.getDiscountAmount() : BigDecimal.ZERO;
                gmv = gmv.add(amt.add(disc));
                netSales = netSales.add(amt);

                if ("DELIVERED".equals(st) || "CONFIRMED".equals(st) || "COMPLETED".equals(st)) {
                    delivered++;
                }
                if (o.getUserId() != null) {
                    periodUserIds.add(o.getUserId());
                }
            }
        }

        BigDecimal refunds = orderReturnRepository.sumRefundsInPeriod(startOffset, endOffset);
        if (refunds == null) refunds = BigDecimal.ZERO;
        netSales = netSales.subtract(refunds);
        if (netSales.compareTo(BigDecimal.ZERO) < 0) netSales = BigDecimal.ZERO;

        Long unitsSold = orderItemRepository.sumUnitsSoldInPeriod(start, end);
        if (unitsSold == null) unitsSold = 0L;

        BigDecimal aov = validOrderCount > 0 ? netSales.divide(BigDecimal.valueOf(validOrderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        double cancelRate = !orders.isEmpty() ? ((double) cancelled / orders.size()) * 100.0 : 0.0;
        double refundRate = gmv.compareTo(BigDecimal.ZERO) > 0 ? (refunds.divide(gmv, 4, RoundingMode.HALF_UP).doubleValue()) * 100.0 : 0.0;

        long newCust = 0;
        long retCust = 0;
        for (Long uid : periodUserIds) {
            long priorOrders = orderRepository.findByUserId(uid).stream()
                    .filter(o -> o.getCreatedAt().isBefore(start) && !"CANCELLED".equals(o.getStatus()))
                    .count();
            if (priorOrders == 0) {
                newCust++;
            } else {
                retCust++;
            }
        }

        return new PeriodMetrics(
                gmv.setScale(2, RoundingMode.HALF_UP),
                netSales.setScale(2, RoundingMode.HALF_UP),
                (long) orders.size(),
                delivered,
                aov,
                cancelled,
                Math.round(cancelRate * 10.0) / 10.0,
                refunds.setScale(2, RoundingMode.HALF_UP),
                Math.round(refundRate * 10.0) / 10.0,
                unitsSold,
                newCust,
                retCust
        );
    }

    private <T extends Number> AdminOverviewAnalyticsDto.MetricCardDto<T> buildCard(T current, T previous) {
        double currVal = current != null ? current.doubleValue() : 0.0;
        double prevVal = previous != null ? previous.doubleValue() : 0.0;

        double delta;
        String trend;

        if (prevVal == 0.0 && currVal == 0.0) {
            delta = 0.0;
            trend = "FLAT";
        } else if (prevVal == 0.0) {
            delta = 100.0;
            trend = "UP";
        } else {
            delta = ((currVal - prevVal) / prevVal) * 100.0;
            delta = Math.round(delta * 10.0) / 10.0;
            if (delta > 0.05) trend = "UP";
            else if (delta < -0.05) trend = "DOWN";
            else trend = "FLAT";
        }

        return AdminOverviewAnalyticsDto.MetricCardDto.<T>builder()
                .current(current)
                .previous(previous)
                .deltaPercent(delta)
                .trend(trend)
                .build();
    }

    private List<AdminProductAnalyticsDto.ProductPerformanceDto> mapProductPerformance(List<Object[]> raw) {
        List<AdminProductAnalyticsDto.ProductPerformanceDto> list = new ArrayList<>();
        for (Object[] r : raw) {
            if (r == null || r[0] == null) continue;
            Long prodId = ((Number) r[0]).longValue();
            String name = r[1] != null ? (String) r[1] : "Product #" + prodId;
            Long units = r[2] != null ? ((Number) r[2]).longValue() : 0L;
            BigDecimal rev = r[3] != null ? (BigDecimal) r[3] : BigDecimal.ZERO;
            Long orders = r[4] != null ? ((Number) r[4]).longValue() : 0L;

            double avgPrice = units > 0 ? rev.divide(BigDecimal.valueOf(units), 2, RoundingMode.HALF_UP).doubleValue() : 0.0;

            list.add(AdminProductAnalyticsDto.ProductPerformanceDto.builder()
                    .productId(prodId)
                    .productName(name)
                    .unitsSold(units)
                    .totalRevenue(rev.setScale(2, RoundingMode.HALF_UP))
                    .orderCount(orders)
                    .averageSellingPrice(avgPrice)
                    .build());
        }
        return list;
    }

    private AdminOrderRowDto mapToAdminOrderRow(Order o) {
        List<AdminOrderRowDto.OrderItemSummaryDto> items = Collections.emptyList();
        if (o.getItems() != null) {
            items = o.getItems().stream().map(i -> AdminOrderRowDto.OrderItemSummaryDto.builder()
                    .productId(i.getProductId())
                    .productName(i.getProductName())
                    .price(i.getPrice())
                    .quantity(i.getQuantity())
                    .build()).collect(Collectors.toList());
        }

        return AdminOrderRowDto.builder()
                .id(o.getId())
                .userId(o.getUserId())
                .status(o.getStatus())
                .totalAmount(o.getTotalAmount())
                .discountAmount(o.getDiscountAmount())
                .couponCode(o.getCouponCode())
                .carrier(o.getCarrier())
                .trackingNumber(o.getTrackingNumber())
                .shippingStatus(o.getShippingStatus())
                .createdAt(o.getCreatedAt())
                .itemCount(items.size())
                .items(items)
                .build();
    }

    private String guessCategory(String name) {
        if (name == null) return "General";
        String lower = name.toLowerCase();
        if (lower.contains("headphone") || lower.contains("earbud") || lower.contains("audio")) return "Audio";
        if (lower.contains("keyboard") || lower.contains("mouse") || lower.contains("monitor")) return "Computer Peripherals";
        if (lower.contains("game") || lower.contains("rgb") || lower.contains("controller")) return "Gaming";
        return "Accessories";
    }

    private TimeRangeBounds resolveBounds(String timeRange, LocalDateTime customStart, LocalDateTime customEnd) {
        LocalDateTime now = LocalDateTime.now();
        if ("TODAY".equalsIgnoreCase(timeRange)) {
            LocalDateTime start = now.toLocalDate().atStartOfDay();
            return new TimeRangeBounds("TODAY", start, now);
        } else if ("YESTERDAY".equalsIgnoreCase(timeRange)) {
            LocalDate yest = now.toLocalDate().minusDays(1);
            return new TimeRangeBounds("YESTERDAY", yest.atStartOfDay(), yest.atTime(LocalTime.MAX));
        } else if ("LAST_7_DAYS".equalsIgnoreCase(timeRange)) {
            return new TimeRangeBounds("LAST_7_DAYS", now.minusDays(7), now);
        } else if ("MONTH_TO_DATE".equalsIgnoreCase(timeRange)) {
            LocalDateTime start = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
            return new TimeRangeBounds("MONTH_TO_DATE", start, now);
        } else if ("PREVIOUS_MONTH".equalsIgnoreCase(timeRange)) {
            LocalDate firstOfLastMonth = now.toLocalDate().minusMonths(1).withDayOfMonth(1);
            LocalDate lastOfLastMonth = firstOfLastMonth.plusMonths(1).minusDays(1);
            return new TimeRangeBounds("PREVIOUS_MONTH", firstOfLastMonth.atStartOfDay(), lastOfLastMonth.atTime(LocalTime.MAX));
        } else if ("ALL_TIME".equalsIgnoreCase(timeRange)) {
            return new TimeRangeBounds("ALL_TIME", now.minusYears(5), now);
        } else if ("CUSTOM".equalsIgnoreCase(timeRange) && customStart != null && customEnd != null) {
            // Cap at 365 days max for performance safety
            if (ChronoUnit.DAYS.between(customStart, customEnd) > 365) {
                customStart = customEnd.minusDays(365);
            }
            return new TimeRangeBounds("CUSTOM", customStart, customEnd);
        }
        // Default to LAST_30_DAYS
        return new TimeRangeBounds("LAST_30_DAYS", now.minusDays(30), now);
    }

    private TimeRangeBounds resolvePreviousBounds(TimeRangeBounds curr) {
        long durationNanos = ChronoUnit.NANOS.between(curr.start, curr.end);
        LocalDateTime prevEnd = curr.start;
        LocalDateTime prevStart = prevEnd.minusNanos(durationNanos);
        return new TimeRangeBounds(curr.label + "_PREV", prevStart, prevEnd);
    }

    private static class TimeRangeBounds {
        final String label;
        final LocalDateTime start;
        final LocalDateTime end;

        TimeRangeBounds(String label, LocalDateTime start, LocalDateTime end) {
            this.label = label;
            this.start = start;
            this.end = end;
        }
    }

    private static class PeriodMetrics {
        final BigDecimal gmv;
        final BigDecimal netSales;
        final Long ordersPlaced;
        final Long deliveredOrders;
        final BigDecimal aov;
        final Long cancelledOrders;
        final Double cancellationRate;
        final BigDecimal refundAmount;
        final Double refundRate;
        final Long unitsSold;
        final Long newCustomers;
        final Long returningCustomers;

        PeriodMetrics(BigDecimal gmv, BigDecimal netSales, Long ordersPlaced, Long deliveredOrders,
                      BigDecimal aov, Long cancelledOrders, Double cancellationRate,
                      BigDecimal refundAmount, Double refundRate, Long unitsSold,
                      Long newCustomers, Long returningCustomers) {
            this.gmv = gmv;
            this.netSales = netSales;
            this.ordersPlaced = ordersPlaced;
            this.deliveredOrders = deliveredOrders;
            this.aov = aov;
            this.cancelledOrders = cancelledOrders;
            this.cancellationRate = cancellationRate;
            this.refundAmount = refundAmount;
            this.refundRate = refundRate;
            this.unitsSold = unitsSold;
            this.newCustomers = newCustomers;
            this.returningCustomers = returningCustomers;
        }
    }
}
