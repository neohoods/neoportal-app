package com.neohoods.portal.platform.spaces.services;

import java.math.BigDecimal;

/**
 * Result object for price calculation breakdown
 */
public class PriceCalculationResult {
    private BigDecimal totalDaysPrice; // Renamed from basePrice - total price for days/nights (unitPrice × numberOfDays)
    private BigDecimal unitPrice; // Price per unit (day/night)
    private long numberOfDays; // Number of days/nights (generic term)
    private BigDecimal subtotal; // totalDaysPrice + cleaningFee
    private BigDecimal cleaningFee;
    private BigDecimal platformFeeAmount;
    private BigDecimal platformFixedFeeAmount;
    private BigDecimal deposit;
    private BigDecimal totalPrice;

    public PriceCalculationResult(BigDecimal totalDaysPrice, BigDecimal unitPrice, long numberOfDays,
            BigDecimal subtotal, BigDecimal cleaningFee,
            BigDecimal platformFeeAmount, BigDecimal platformFixedFeeAmount,
            BigDecimal deposit, BigDecimal totalPrice) {
        this.totalDaysPrice = totalDaysPrice;
        this.unitPrice = unitPrice;
        this.numberOfDays = numberOfDays;
        this.subtotal = subtotal;
        this.cleaningFee = cleaningFee;
        this.platformFeeAmount = platformFeeAmount;
        this.platformFixedFeeAmount = platformFixedFeeAmount;
        this.deposit = deposit;
        this.totalPrice = totalPrice;
    }

    // Getters
    public BigDecimal getTotalDaysPrice() {
        return totalDaysPrice;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public long getNumberOfDays() {
        return numberOfDays;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
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
    public void setTotalDaysPrice(BigDecimal totalDaysPrice) {
        this.totalDaysPrice = totalDaysPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void setNumberOfDays(long numberOfDays) {
        this.numberOfDays = numberOfDays;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
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
