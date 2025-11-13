package com.neohoods.portal.platform.spaces.services;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Result object for price calculation breakdown
 */
@Data
@AllArgsConstructor
public class PriceCalculationResult {
    /**
     * Total price for days/nights (unitPrice × numberOfDays)
     * Renamed from basePrice
     */
    private BigDecimal totalDaysPrice;

    /**
     * Price per unit (day/night)
     */
    private BigDecimal unitPrice;

    /**
     * Number of days/nights (generic term)
     */
    private long numberOfDays;

    /**
     * Subtotal: totalDaysPrice + cleaningFee
     */
    private BigDecimal subtotal;

    private BigDecimal cleaningFee;
    private BigDecimal platformFeeAmount;
    private BigDecimal platformFixedFeeAmount;
    private BigDecimal deposit;
    private BigDecimal totalPrice;
}
