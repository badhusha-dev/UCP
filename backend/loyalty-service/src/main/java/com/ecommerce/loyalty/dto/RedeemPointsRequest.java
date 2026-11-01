package com.ecommerce.loyalty.dto;

public class RedeemPointsRequest {
    private Integer points;
    private String orderId;
    private String description;

    // Getters and setters
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
