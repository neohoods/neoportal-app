package com.neohoods.portal.platform.spaces.services;

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

        // Map access code information
        if (entity.getAccessCode() != null) {
            // Skip access code mapping for now - the API model doesn't have the right type
        }

        return dto;
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
