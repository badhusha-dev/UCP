package com.ecommerce.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
@Tag(name = "Circuit Breaker Fallbacks", description = "Resilience4j Reactive Gateway Circuit Breaker fallback responses")
public class FallbackController {

    @Operation(summary = "User Service Fallback", description = "Returned when user-service route circuit breaker is open or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "User Service is currently unavailable."))
    @RequestMapping("/user")
    public Mono<ResponseEntity<String>> userServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("User Service is currently unavailable. Please try again later."));
    }

    @Operation(summary = "Product Service Fallback", description = "Returned when product-service route circuit breaker is open or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Product Service is currently unavailable."))
    @RequestMapping("/product")
    public Mono<ResponseEntity<String>> productServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Product Service is currently unavailable. Please try again later."));
    }

    @Operation(summary = "Order Service Fallback", description = "Returned when order-service route circuit breaker is open or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Order Service is currently unavailable."))
    @RequestMapping("/order")
    public Mono<ResponseEntity<String>> orderServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Order Service is currently unavailable. Please try again later."));
    }

    @Operation(summary = "Delivery Service Fallback", description = "Returned when delivery-service is unavailable or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Delivery Service is currently unavailable."))
    @RequestMapping("/delivery")
    public Mono<ResponseEntity<String>> deliveryServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Delivery Service is currently unavailable. Please try again later."));
    }

    @Operation(summary = "Notification Service Fallback", description = "Returned when notification-service route circuit breaker is open or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Notification Service is currently unavailable."))
    @RequestMapping("/notification")
    public Mono<ResponseEntity<String>> notificationServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Notification Service is currently unavailable. Please try again later."));
    }

    @Operation(summary = "Inventory Service Fallback", description = "Returned when inventory-service route circuit breaker is open or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Inventory Service is currently unavailable."))
    @RequestMapping("/inventory")
    public Mono<ResponseEntity<String>> inventoryServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Inventory Service is currently unavailable. Please try again later."));
    }

    @Operation(summary = "Cart Service Fallback", description = "Returned when cart-service route circuit breaker is open or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Cart Service is currently unavailable."))
    @RequestMapping("/cart")
    public Mono<ResponseEntity<String>> cartServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Cart Service is currently unavailable. Please try again later."));
    }

    @Operation(summary = "Loyalty Service Fallback", description = "Returned when loyalty-service route circuit breaker is open or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Loyalty Service is currently unavailable."))
    @RequestMapping("/loyalty")
    public Mono<ResponseEntity<String>> loyaltyServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Loyalty Service is currently unavailable. Please try again later."));
    }

    @Operation(summary = "Accounting Service Fallback", description = "Returned when accounting-service route circuit breaker is open or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Accounting Service is currently unavailable."))
    @RequestMapping("/accounting")
    public Mono<ResponseEntity<String>> accountingServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Accounting Service is currently unavailable. Please try again later."));
    }

    @Operation(summary = "Analytics Service Fallback", description = "Returned when analytics-service route circuit breaker is open or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "Analytics Service is currently unavailable."))
    @RequestMapping("/analytics")
    public Mono<ResponseEntity<String>> analyticsServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Analytics Service is currently unavailable. Please try again later."));
    }

    @Operation(summary = "CRM Service Fallback", description = "Returned when crm-service is unavailable or calls time out.", security = {})
    @ApiResponses(@ApiResponse(responseCode = "503", description = "CRM Service is currently unavailable."))
    @RequestMapping("/crm")
    public Mono<ResponseEntity<String>> crmServiceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("CRM Service is currently unavailable. Please try again later."));
    }
}
