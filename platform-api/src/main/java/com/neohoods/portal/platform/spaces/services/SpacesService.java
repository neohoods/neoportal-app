package com.neohoods.portal.platform.spaces.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceSettingsEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

@Service
@Transactional
public class SpacesService {

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SpaceSettingsService spaceSettingsService;

    @Transactional(readOnly = true)
    public boolean isSpaceAvailable(UUID spaceId, LocalDate startDate, LocalDate endDate) {
        // Pour l'instant, logique simple sans partage d'espaces
        return spaceRepository.isSpaceAvailable(spaceId, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<ReservationEntity> getSharedSpaceReservations(UUID spaceId, LocalDate startDate, LocalDate endDate) {
        // Pour l'instant, retourner liste vide
        return List.of();
    }

    @Transactional(readOnly = true)
    public SpaceEntity getSpaceById(UUID spaceId) {
        return spaceRepository.findById(spaceId)
                .orElseThrow(() -> new CodedErrorException(CodedError.SPACE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<SpaceEntity> getAllActiveSpaces() {
        return spaceRepository.findByStatus(SpaceStatusForEntity.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Page<SpaceEntity> getSpacesWithFilters(SpaceTypeForEntity entityType, SpaceStatusForEntity status,
            Pageable pageable) {
        // Use fetch join to load images to avoid LazyInitializationException
        List<SpaceEntity> spaces;
        if (entityType != null && status != null) {
            spaces = spaceRepository.findByTypeAndStatusWithImages(entityType, status);
        } else if (entityType != null) {
            spaces = spaceRepository.findActiveSpacesWithImagesAndFilters(entityType);
        } else if (status != null) {
            spaces = spaceRepository.findByStatusWithImages(status);
        } else {
            spaces = spaceRepository.findByStatusWithImages(SpaceStatusForEntity.ACTIVE);
        }

        // Initialize collections to avoid LazyInitializationException
        // Must do this BEFORE returning from transaction to ensure data is loaded
        spaces.forEach(space -> {
            // Force initialization of images collection by accessing it
            // The fetch join should have loaded it, but we ensure it's really loaded
            try {
                Hibernate.initialize(space.getImages());
                // Force load by iterating - this ensures data is truly loaded
                if (space.getImages() != null) {
                    for (var img : space.getImages()) {
                        // Touch each image to force load
                        img.getId();
                    }
                }
            } catch (Exception e) {
                // Collection may be empty - that's OK
            }
            // Initialize allowedDays collection
            try {
                Hibernate.initialize(space.getAllowedDays());
                if (space.getAllowedDays() != null) {
                    for (var day : space.getAllowedDays()) {
                        day.name(); // Force load
                    }
                }
            } catch (Exception e) {
                // Collection may be empty - that's OK
            }
            // Initialize cleaningDays collection
            try {
                Hibernate.initialize(space.getCleaningDays());
                if (space.getCleaningDays() != null) {
                    for (var day : space.getCleaningDays()) {
                        day.name(); // Force load
                    }
                }
            } catch (Exception e) {
                // Collection may be empty - that's OK
            }
        });

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), spaces.size());
        List<SpaceEntity> pageContent = spaces.size() > start ? spaces.subList(start, end) : List.of();
        return new PageImpl<>(pageContent, pageable, spaces.size());
    }

    @Transactional(readOnly = true)
    public Page<SpaceEntity> getAvailableSpacesWithFilters(SpaceTypeForEntity entityType, LocalDate startDate,
            LocalDate endDate, Pageable pageable) {
        // Use fetch join to load images to avoid LazyInitializationException
        List<SpaceEntity> spaces = spaceRepository.findAvailableSpacesWithImages(entityType, startDate, endDate);
        // Initialize allowedDays collection to avoid LazyInitializationException
        spaces.forEach(space -> {
            if (space.getAllowedDays() != null) {
                Hibernate.initialize(space.getAllowedDays());
            }
        });
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), spaces.size());
        List<SpaceEntity> pageContent = spaces.size() > start ? spaces.subList(start, end) : List.of();
        return new PageImpl<>(pageContent, pageable, spaces.size());
    }

    @Transactional(readOnly = true)
    public List<SpaceEntity> getActiveSpacesWithImagesAndFilters(SpaceTypeForEntity entityType, String search,
            Pageable pageable) {
        // Use fetch join to load images to avoid LazyInitializationException
        List<SpaceEntity> spaces = spaceRepository.findActiveSpacesWithImagesAndFilters(entityType);
        // Initialize allowedDays collection to avoid LazyInitializationException
        // Note: Cannot use fetch join for both images and allowedDays in same query
        // (MultipleBagFetchException)
        // So we initialize allowedDays separately after fetching
        spaces.forEach(space -> {
            if (space.getAllowedDays() != null) {
                Hibernate.initialize(space.getAllowedDays());
            }
        });
        return spaces;
    }

    @Transactional(readOnly = true)
    public SpaceEntity getSpaceByIdWithImages(UUID spaceId) {
        SpaceEntity space = spaceRepository.findByIdWithImages(spaceId)
                .orElseThrow(() -> new CodedErrorException(CodedError.SPACE_NOT_FOUND));

        // Initialize ALL collections to avoid LazyInitializationException
        // Must do this BEFORE returning from transaction

        // Initialize images (should be loaded by fetch join, but ensure)
        try {
            Hibernate.initialize(space.getImages());
            if (space.getImages() != null) {
                for (var img : space.getImages()) {
                    img.getId(); // Force load
                }
            }
        } catch (Exception e) {
            // Collection may be empty - that's OK
        }

        // Initialize allowedDays collection
        try {
            Hibernate.initialize(space.getAllowedDays());
            if (space.getAllowedDays() != null) {
                for (var day : space.getAllowedDays()) {
                    day.name(); // Force load
                }
            }
        } catch (Exception e) {
            // Collection may be empty - that's OK
        }

        // Initialize cleaningDays collection
        try {
            Hibernate.initialize(space.getCleaningDays());
            if (space.getCleaningDays() != null) {
                for (var day : space.getCleaningDays()) {
                    day.name(); // Force load
                }
            }
        } catch (Exception e) {
            // Collection may be empty - that's OK
        }

        return space;
    }

    public void validateUserCanReserveSpace(UUID spaceId, UUID userId, LocalDate startDate, LocalDate endDate) {
        // Validate date range
        if (endDate.isBefore(startDate)) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("spaceId", spaceId);
            variables.put("startDate", startDate);
            variables.put("endDate", endDate);
            throw new CodedErrorException(CodedError.SPACE_DURATION_TOO_SHORT, variables);
        }

        // Validate dates are not in the past
        LocalDate today = LocalDate.now();
        if (startDate.isBefore(today)) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("spaceId", spaceId);
            variables.put("startDate", startDate.toString());
            variables.put("endDate", endDate.toString());
            throw new CodedErrorException(CodedError.SPACE_NOT_AVAILABLE, variables);
        }

        // Get space for additional validations
        SpaceEntity space = getSpaceById(spaceId);

        // Check if space is active
        if (space.getStatus() != SpaceStatusForEntity.ACTIVE) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("spaceId", spaceId);
            variables.put("status", space.getStatus().toString());
            throw new CodedErrorException(CodedError.SPACE_INACTIVE, variables);
        }

        // Check minimum duration
        long requestedDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1; // +1 for inclusive
        if (space.getMinDurationDays() > 0 && requestedDays < space.getMinDurationDays()) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("spaceId", spaceId);
            variables.put("requestedDays", requestedDays);
            variables.put("minDurationDays", space.getMinDurationDays());
            throw new CodedErrorException(CodedError.SPACE_DURATION_TOO_SHORT, variables);
        }

        // Check maximum duration
        System.out.println("DEBUG: requestedDays=" + requestedDays + ", maxDurationDays=" + space.getMaxDurationDays());
        if (space.getMaxDurationDays() > 0 && requestedDays > space.getMaxDurationDays()) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("spaceId", spaceId);
            variables.put("requestedDays", requestedDays);
            variables.put("maxDurationDays", space.getMaxDurationDays());
            throw new CodedErrorException(CodedError.SPACE_DURATION_TOO_LONG, variables);
        }

        // Check space availability
        if (!isSpaceAvailable(spaceId, startDate, endDate)) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("spaceId", spaceId);
            variables.put("startDate", startDate.toString());
            variables.put("endDate", endDate.toString());
            throw new CodedErrorException(CodedError.SPACE_NOT_AVAILABLE, variables);
        }

        // Check annual quota
        if (space.getMaxAnnualReservations() > 0 &&
                space.getUsedAnnualReservations() >= space.getMaxAnnualReservations()) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("spaceId", spaceId);
            variables.put("usedReservations", space.getUsedAnnualReservations());
            variables.put("maxReservations", space.getMaxAnnualReservations());
            throw new CodedErrorException(CodedError.SPACE_ANNUAL_QUOTA_EXCEEDED, variables);
        }
    }

    public PriceCalculationResult calculatePriceBreakdown(UUID spaceId, LocalDate startDate, LocalDate endDate,
            boolean isOwner) {
        SpaceEntity space = getSpaceById(spaceId);
        BigDecimal pricePerDay = isOwner ? space.getOwnerPrice() : space.getTenantPrice();
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        BigDecimal basePrice = pricePerDay.multiply(BigDecimal.valueOf(days));

        // Si le prix est zéro, retourner BigDecimal.ZERO pour éviter les problèmes de
        // format
        BigDecimal finalBasePrice = basePrice.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : basePrice;

        // Get platform fee settings
        SpaceSettingsEntity settings = spaceSettingsService.getSpaceSettings();
        BigDecimal platformFeePercentage = settings.getPlatformFeePercentage();
        BigDecimal platformFixedFee = settings.getPlatformFixedFee();

        // Calculate platform fees
        // Platform fees only apply if there is a charge (basePrice > 0)
        BigDecimal platformFeeAmount = BigDecimal.ZERO;
        BigDecimal platformFixedFeeAmount = BigDecimal.ZERO;

        if (finalBasePrice.compareTo(BigDecimal.ZERO) > 0) {
            // Platform fee amount = percentage of base price (before cleaning fee and
            // deposit)
            if (platformFeePercentage != null && platformFeePercentage.compareTo(BigDecimal.ZERO) > 0) {
                platformFeeAmount = finalBasePrice.multiply(platformFeePercentage)
                        .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            }

            // Platform fixed fee is constant per transaction (only for paid reservations)
            if (platformFixedFee != null && platformFixedFee.compareTo(BigDecimal.ZERO) > 0) {
                platformFixedFeeAmount = platformFixedFee;
            }
        }

        // Calculate total price: basePrice + cleaningFee + deposit + platformFees
        BigDecimal totalAmount = finalBasePrice
                .add(space.getCleaningFee())
                .add(space.getDeposit())
                .add(platformFeeAmount)
                .add(platformFixedFeeAmount);

        BigDecimal finalTotalAmount = totalAmount.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : totalAmount;

        // Create PriceCalculationResult with calculated fees
        PriceCalculationResult result = new PriceCalculationResult(
                finalBasePrice, // basePrice
                space.getCleaningFee(), // cleaningFee
                platformFeeAmount, // platformFeeAmount (percentage-based)
                platformFixedFeeAmount, // platformFixedFeeAmount (fixed)
                space.getDeposit(), // deposit
                finalTotalAmount // totalPrice
        );
        return result;
    }

    public void incrementUsedAnnualReservations(UUID spaceId) {
        SpaceEntity space = getSpaceById(spaceId);
        space.setUsedAnnualReservations(space.getUsedAnnualReservations() + 1);
        spaceRepository.save(space);
    }

    public void decrementUsedAnnualReservations(UUID spaceId) {
        SpaceEntity space = getSpaceById(spaceId);
        space.setUsedAnnualReservations(Math.max(0, space.getUsedAnnualReservations() - 1));
        spaceRepository.save(space);
    }

    public SpaceEntity createSpace(SpaceEntity space) {
        return spaceRepository.save(space);
    }

    public SpaceEntity updateSpace(SpaceEntity space) {
        return spaceRepository.save(space);
    }

    public void deleteSpace(UUID spaceId) {
        spaceRepository.deleteById(spaceId);
    }
}