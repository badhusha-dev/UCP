package com.ecommerce.order.service;

import com.ecommerce.order.dto.CouponValidationResponse;
import com.ecommerce.order.dto.ValidateCouponRequest;
import com.ecommerce.order.model.Coupon;
import com.ecommerce.order.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponValidationResponse validateCoupon(ValidateCouponRequest request) {
        if (request.getCouponCode() == null || request.getCouponCode().trim().isEmpty()) {
            return CouponValidationResponse.builder()
                    .valid(false)
                    .message("Coupon code cannot be empty")
                    .build();
        }

        BigDecimal orderAmount = request.getOrderAmount() != null ? request.getOrderAmount() : BigDecimal.ZERO;
        String code = request.getCouponCode().trim().toUpperCase();

        Optional<Coupon> opt = couponRepository.findByCodeIgnoreCase(code);
        if (opt.isEmpty()) {
            return CouponValidationResponse.builder()
                    .valid(false)
                    .couponCode(code)
                    .message("Coupon code '" + code + "' is invalid or does not exist")
                    .build();
        }

        Coupon coupon = opt.get();
        OffsetDateTime now = OffsetDateTime.now();

        if (Boolean.FALSE.equals(coupon.getActive())) {
            return CouponValidationResponse.builder()
                    .valid(false)
                    .couponCode(code)
                    .message("Coupon '" + code + "' is no longer active")
                    .build();
        }

        if (coupon.getStartDate() != null && now.isBefore(coupon.getStartDate())) {
            return CouponValidationResponse.builder()
                    .valid(false)
                    .couponCode(code)
                    .message("Coupon '" + code + "' is not yet valid")
                    .build();
        }

        if (coupon.getExpiryDate() != null && now.isAfter(coupon.getExpiryDate())) {
            return CouponValidationResponse.builder()
                    .valid(false)
                    .couponCode(code)
                    .message("Coupon '" + code + "' has expired")
                    .build();
        }

        if (coupon.getUsageLimit() != null && coupon.getTimesUsed() != null
                && coupon.getTimesUsed() >= coupon.getUsageLimit()) {
            return CouponValidationResponse.builder()
                    .valid(false)
                    .couponCode(code)
                    .message("Coupon '" + code + "' has reached its maximum usage limit")
                    .build();
        }

        if (coupon.getMinOrderAmount() != null && orderAmount.compareTo(coupon.getMinOrderAmount()) < 0) {
            return CouponValidationResponse.builder()
                    .valid(false)
                    .couponCode(code)
                    .message("Coupon '" + code + "' requires a minimum purchase amount of $" + coupon.getMinOrderAmount().setScale(2, RoundingMode.HALF_UP))
                    .build();
        }

        BigDecimal discountAmount;
        if ("PERCENTAGE".equalsIgnoreCase(coupon.getDiscountType())) {
            BigDecimal rate = coupon.getDiscountValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            discountAmount = orderAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null && discountAmount.compareTo(coupon.getMaxDiscount()) > 0) {
                discountAmount = coupon.getMaxDiscount().setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            discountAmount = coupon.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
            if (discountAmount.compareTo(orderAmount) > 0) {
                discountAmount = orderAmount;
            }
        }

        BigDecimal finalAmount = orderAmount.subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        return CouponValidationResponse.builder()
                .valid(true)
                .couponCode(code)
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .message("Coupon applied successfully! You saved $" + discountAmount)
                .build();
    }

    @Transactional
    public BigDecimal applyCouponToOrder(String couponCode, BigDecimal orderAmount) {
        if (couponCode == null || couponCode.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        ValidateCouponRequest req = new ValidateCouponRequest();
        req.setCouponCode(couponCode.trim());
        req.setOrderAmount(orderAmount);

        CouponValidationResponse res = validateCoupon(req);
        if (!res.isValid()) {
            log.warn("Coupon {} could not be applied: {}", couponCode, res.getMessage());
            return BigDecimal.ZERO;
        }

        // Increment times used
        couponRepository.findByCodeIgnoreCase(couponCode.trim().toUpperCase()).ifPresent(coupon -> {
            coupon.setTimesUsed((coupon.getTimesUsed() == null ? 0 : coupon.getTimesUsed()) + 1);
            couponRepository.save(coupon);
        });

        return res.getDiscountAmount();
    }

    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    public List<Coupon> getActiveCoupons() {
        return couponRepository.findByActiveTrue();
    }

    @Transactional
    public Coupon createCoupon(Coupon coupon) {
        if (coupon.getCode() == null || coupon.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Coupon code cannot be empty");
        }
        coupon.setCode(coupon.getCode().trim().toUpperCase());
        if (coupon.getDiscountType() == null) {
            coupon.setDiscountType("PERCENTAGE");
        }
        if (coupon.getDiscountValue() == null || coupon.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than zero");
        }
        if (coupon.getCreatedAt() == null) {
            coupon.setCreatedAt(OffsetDateTime.now());
        }
        if (coupon.getActive() == null) {
            coupon.setActive(true);
        }
        return couponRepository.save(coupon);
    }

    @Transactional
    public void deactivateCoupon(Long id) {
        couponRepository.findById(id).ifPresent(coupon -> {
            coupon.setActive(false);
            couponRepository.save(coupon);
        });
    }
}
