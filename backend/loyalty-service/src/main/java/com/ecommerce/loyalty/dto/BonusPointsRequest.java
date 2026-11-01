package com.ecommerce.loyalty.dto;

public class BonusPointsRequest {
    private Integer points;
    private String reason;

    // Getters and setters
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
