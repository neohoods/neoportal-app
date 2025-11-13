package com.neohoods.portal.platform.spaces.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.model.Reservation;
import com.neohoods.portal.platform.model.ReservationStatus;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;

@Component
public class ReservationMapper {

    public Reservation toDto(ReservationEntity entity) {
        if (entity == null) {
            return null;
        }

        Reservation dto = new Reservation();
        dto.setId(entity.getId());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setTotalPrice(entity.getTotalPrice().floatValue());
        dto.setStatus(convertStatus(entity.getStatus()));
        dto.setCreatedAt(entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
        dto.setUpdatedAt(entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC));

        // Map space information
        if (entity.getSpace() != null) {
            dto.setSpaceId(entity.getSpace().getId());
        }

        // Map user information
        if (entity.getUser() != null) {
            dto.setUserId(entity.getUser().getId());
        }

        // Map unit information
        if (entity.getUnit() != null) {
            dto.setUnitId(entity.getUnit().getId());
        }

        // Map access code information
        if (entity.getAccessCode() != null) {
            // Skip access code mapping for now - the API model doesn't have the right type
        }

        return dto;
    }

    /**
     * Calculate pricing details for a reservation
     * 
     * @param entity The reservation entity
     * @return An object with all pricing details: numberOfDays, unitPrice, totalDaysPrice, cleaningFee, subtotal, deposit, platformFeeAmount, platformFixedFeeAmount, totalPrice
     */
    public static PricingDetails calculatePricingDetails(ReservationEntity entity) {
        // Calculate number of days/nights (inclusive: endDate - startDate + 1)
        long numberOfDays = ChronoUnit.DAYS.between(entity.getStartDate(), entity.getEndDate()) + 1;

        // Calculate totalDaysPrice: totalPrice - cleaningFee - deposit - platformFees
        BigDecimal cleaningFee = entity.getSpace().getCleaningFee() != null ? entity.getSpace().getCleaningFee()
                : BigDecimal.ZERO;
        BigDecimal deposit = entity.getSpace().getDeposit() != null ? entity.getSpace().getDeposit()
                : BigDecimal.ZERO;
        BigDecimal platformFeeAmount = entity.getPlatformFeeAmount() != null ? entity.getPlatformFeeAmount()
                : BigDecimal.ZERO;
        BigDecimal platformFixedFeeAmount = entity.getPlatformFixedFeeAmount() != null
                ? entity.getPlatformFixedFeeAmount()
                : BigDecimal.ZERO;

        BigDecimal totalDaysPrice = entity.getTotalPrice()
                .subtract(cleaningFee)
                .subtract(deposit)
                .subtract(platformFeeAmount)
                .subtract(platformFixedFeeAmount);

        // Calculate unit price (price per day/night)
        BigDecimal unitPrice = BigDecimal.ZERO;
        if (numberOfDays > 0) {
            unitPrice = totalDaysPrice.divide(BigDecimal.valueOf(numberOfDays), 2, RoundingMode.HALF_UP);
        }

        // Calculate subtotal: totalDaysPrice + cleaningFee
        BigDecimal subtotal = totalDaysPrice.add(cleaningFee);

        return new PricingDetails(numberOfDays, unitPrice, totalDaysPrice, cleaningFee, subtotal, deposit,
                platformFeeAmount, platformFixedFeeAmount, entity.getTotalPrice());
    }

    /**
     * Helper class to hold pricing details
     */
    public static class PricingDetails {
        public final long numberOfDays;
        public final BigDecimal unitPrice;
        public final BigDecimal totalDaysPrice;
        public final BigDecimal cleaningFee;
        public final BigDecimal subtotal;
        public final BigDecimal deposit;
        public final BigDecimal platformFeeAmount;
        public final BigDecimal platformFixedFeeAmount;
        public final BigDecimal totalPrice;

        public PricingDetails(long numberOfDays, BigDecimal unitPrice, BigDecimal totalDaysPrice,
                BigDecimal cleaningFee, BigDecimal subtotal, BigDecimal deposit, BigDecimal platformFeeAmount,
                BigDecimal platformFixedFeeAmount, BigDecimal totalPrice) {
            this.numberOfDays = numberOfDays;
            this.unitPrice = unitPrice;
            this.totalDaysPrice = totalDaysPrice;
            this.cleaningFee = cleaningFee;
            this.subtotal = subtotal;
            this.deposit = deposit;
            this.platformFeeAmount = platformFeeAmount;
            this.platformFixedFeeAmount = platformFixedFeeAmount;
            this.totalPrice = totalPrice;
        }
    }

    private ReservationStatus convertStatus(
            ReservationStatusForEntity status) {
        return switch (status) {
            case PENDING_PAYMENT -> ReservationStatus.PENDING_PAYMENT;
            case PAYMENT_FAILED -> ReservationStatus.PAYMENT_FAILED;
            case EXPIRED -> ReservationStatus.EXPIRED;
            case CONFIRMED -> ReservationStatus.CONFIRMED;
            case ACTIVE -> ReservationStatus.ACTIVE;
            case CANCELLED -> ReservationStatus.CANCELLED;
            case COMPLETED -> ReservationStatus.COMPLETED;
            case REFUNDED -> ReservationStatus.REFUNDED;
        };
    }
}
