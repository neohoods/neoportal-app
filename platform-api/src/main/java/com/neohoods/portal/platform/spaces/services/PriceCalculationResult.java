package com.neohoods.portal.platform.spaces.services;

import java.math.BigDecimal;

/**
 * Result object for price calculation breakdown
 */
public class PriceCalculationResult {
    private BigDecimal basePrice;
    private BigDecimal cleaningFee;
    private BigDecimal platformFeeAmount;
    private BigDecimal platformFixedFeeAmount;
    private BigDecimal deposit;
    private BigDecimal totalPrice;

    public PriceCalculationResult(BigDecimal basePrice, BigDecimal cleaningFee,
            BigDecimal platformFeeAmount, BigDecimal platformFixedFeeAmount,
            BigDecimal deposit, BigDecimal totalPrice) {
        this.basePrice = basePrice;
        this.cleaningFee = cleaningFee;
        this.platformFeeAmount = platformFeeAmount;
        this.platformFixedFeeAmount = platformFixedFeeAmount;
        this.deposit = deposit;
        this.totalPrice = totalPrice;
    }

    // Getters
    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public BigDecimal getCleaningFee() {
        return cleaningFee;
    }

    public BigDecimal getPlatformFeeAmount() {
        return platformFeeAmount;
    }

    public BigDecimal getPlatformFixedFeeAmount() {
        return platformFixedFeeAmount;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    // Setters
    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public void setCleaningFee(BigDecimal cleaningFee) {
        this.cleaningFee = cleaningFee;
    }

    public void setPlatformFeeAmount(BigDecimal platformFeeAmount) {
        this.platformFeeAmount = platformFeeAmount;
    }

    public void setPlatformFixedFeeAmount(BigDecimal platformFixedFeeAmount) {
        this.platformFixedFeeAmount = platformFixedFeeAmount;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
}
