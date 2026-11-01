package com.ecommerce.loyalty.dto;

import java.math.BigDecimal;

public class RedeemPointsResponse {
    private boolean success;
    private Integer pointsRedeemed;
    private BigDecimal discountAmount;
    private Integer remainingBalance;
    private String message;

    // Getters and setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Integer getPointsRedeemed() { return pointsRedeemed; }
    public void setPointsRedeemed(Integer pointsRedeemed) { this.pointsRedeemed = pointsRedeemed; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public Integer getRemainingBalance() { return remainingBalance; }
    public void setRemainingBalance(Integer remainingBalance) { this.remainingBalance = remainingBalance; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
