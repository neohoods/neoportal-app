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

import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceSettingsEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class SpacesService {

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SpaceSettingsService spaceSettingsService;

    @Autowired
    private com.neohoods.portal.platform.services.UnitsService unitsService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public boolean isSpaceAvailable(UUID spaceId, LocalDate startDate, LocalDate endDate) {
        log.debug("Checking availability for space {} from {} to {}", spaceId, startDate, endDate);
        boolean available = spaceRepository.isSpaceAvailable(spaceId, startDate, endDate);
        log.debug("Space {} is {} from {} to {}", spaceId, available ? "available" : "not available", startDate,
                endDate);
        return available;
    }

    @Transactional(readOnly = true)
    public List<ReservationEntity> getSharedSpaceReservations(UUID spaceId, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting shared space reservations for space {} from {} to {}", spaceId, startDate, endDate);

        SpaceEntity space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> {
                    log.error("Space {} not found when getting shared space reservations", spaceId);
                    return new CodedErrorException(CodedError.SPACE_NOT_FOUND, "spaceId", spaceId.toString());
                });

        List<UUID> sharedSpaceIds = space.getShareSpaceWith();
        if (sharedSpaceIds == null || sharedSpaceIds.isEmpty()) {
            log.debug("Space {} has no shared spaces, returning empty list", spaceId);
            return List.of();
        }

        log.debug("Space {} shares with {} space(s), fetching reservations", spaceId, sharedSpaceIds.size());
        List<ReservationEntity> reservations = reservationRepository.findSharedSpaceReservations(
                sharedSpaceIds, startDate, endDate);
        log.debug("Found {} shared space reservation(s) for space {} in date range", reservations.size(), spaceId);
        return reservations;
    }

    @Transactional(readOnly = true)
    public SpaceEntity getSpaceById(UUID spaceId) {
        return spaceRepository.findById(spaceId).orElseThrow(() -> new CodedErrorException(CodedError.SPACE_NOT_FOUND));
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
        log.debug("Getting active spaces with images and filters - type: {}, search: {}, page: {}, size: {}",
                entityType, search, pageable.getPageNumber(), pageable.getPageSize());

        // First, get paginated results with filters (but without images to avoid
        // MultipleBagFetchException)
        Page<SpaceEntity> pageResult = spaceRepository.findActiveSpacesWithFilters(entityType, search, pageable);
        List<SpaceEntity> spaces = pageResult.getContent();

        log.debug("Found {} space(s) matching filters", spaces.size());

        // Initialize images collection for each space to avoid
        // LazyInitializationException
        // Note: Cannot use fetch join for both images and allowedDays in same query
        // (MultipleBagFetchException)
        // So we initialize collections separately after fetching
        spaces.forEach(space -> {
            try {
                Hibernate.initialize(space.getImages());
                if (space.getImages() != null) {
                    space.getImages().forEach(img -> img.getId()); // Force load
                }
            } catch (Exception e) {
                log.warn("Failed to initialize images for space {}: {}", space.getId(), e.getMessage());
            }

            // Initialize allowedDays collection
            if (space.getAllowedDays() != null) {
                Hibernate.initialize(space.getAllowedDays());
            }
        });

        log.debug("Initialized images and allowedDays for {} space(s)", spaces.size());
        return spaces;
    }

    @Transactional(readOnly = true)
    public Page<SpaceEntity> getActiveSpacesWithFiltersPaginated(SpaceTypeForEntity entityType, String search,
            Pageable pageable) {
        // Use paginated repository method to get correct total count
        Page<SpaceEntity> pageResult = spaceRepository.findActiveSpacesWithFilters(entityType, search, pageable);

        // Initialize images and allowedDays collections for the paginated results
        // to avoid LazyInitializationException
        pageResult.getContent().forEach(space -> {
            // Initialize images collection
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
                if (space.getAllowedDays() != null) {
                    Hibernate.initialize(space.getAllowedDays());
                    for (var day : space.getAllowedDays()) {
                        day.name(); // Force load
                    }
                }
            } catch (Exception e) {
                // Collection may be empty - that's OK
            }
        });

        return pageResult;
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

        // Check unit membership requirement based on space type
        // COMMON_ROOM, COWORKING, and GUEST_ROOM require unit membership because they
        // are spaces reserved for residents of a specific unit
        // PARKING is the only space type that doesn't require unit membership
        // (can be reserved by anyone, including external users)
        boolean requiresUnit = space.getType() == SpaceTypeForEntity.COMMON_ROOM ||
                space.getType() == SpaceTypeForEntity.COWORKING ||
                space.getType() == SpaceTypeForEntity.GUEST_ROOM;

        UnitEntity primaryUnit = null;
        if (requiresUnit) {
            // Check that user has a primary unit set
            try {
                primaryUnit = unitsService.getPrimaryUnitForUser(userId).block();
            } catch (CodedErrorException e) {
                if (e.getError() == CodedError.USER_NO_PRIMARY_UNIT
                        || e.getError() == CodedError.USER_NOT_TENANT_OR_OWNER) {
                    Map<String, Object> variables = new HashMap<>();
                    variables.put("spaceId", spaceId);
                    variables.put("userId", userId);
                    throw new CodedErrorException(CodedError.USER_NO_PRIMARY_UNIT, variables);
                }
                throw e;
            }
        }

        // Check annual quota per unit (if unit exists)
        if (space.getMaxAnnualReservations() > 0 && primaryUnit != null) {
            int currentYear = LocalDate.now().getYear();
            Long unitReservationsCount = reservationRepository.countReservationsByUnitAndYear(primaryUnit.getId(),
                    currentYear);
            if (unitReservationsCount >= space.getMaxAnnualReservations()) {
                Map<String, Object> variables = new HashMap<>();
                variables.put("spaceId", spaceId);
                variables.put("unitId", primaryUnit.getId());
                throw new CodedErrorException(CodedError.SPACE_ANNUAL_QUOTA_EXCEEDED, variables);
            }
        } else if (space.getMaxAnnualReservations() > 0 && primaryUnit == null) {
            // If space has quota but user has no unit, check global space quota as fallback
            if (space.getUsedAnnualReservations() >= space.getMaxAnnualReservations()) {
                Map<String, Object> variables = new HashMap<>();
                variables.put("spaceId", spaceId);
                variables.put("usedReservations", space.getUsedAnnualReservations());
                variables.put("maxReservations", space.getMaxAnnualReservations());
                throw new CodedErrorException(CodedError.SPACE_ANNUAL_QUOTA_EXCEEDED, variables);
            }
        }
    }

    public PriceCalculationResult calculatePriceBreakdown(UUID spaceId, LocalDate startDate, LocalDate endDate,
            boolean isOwner) {
        SpaceEntity space = getSpaceById(spaceId);
        BigDecimal pricePerDay = isOwner ? space.getOwnerPrice() : space.getTenantPrice();
        long numberOfDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        BigDecimal totalDaysPrice = pricePerDay.multiply(BigDecimal.valueOf(numberOfDays));

        // If price is zero, return BigDecimal.ZERO to avoid formatting issues
        BigDecimal finalTotalDaysPrice = totalDaysPrice.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : totalDaysPrice;

        // Calculate unit price (price per day/night)
        BigDecimal unitPrice = BigDecimal.ZERO;
        if (numberOfDays > 0) {
            unitPrice = finalTotalDaysPrice.divide(BigDecimal.valueOf(numberOfDays), 2, java.math.RoundingMode.HALF_UP);
        }

        // Get platform fee settings
        SpaceSettingsEntity settings = spaceSettingsService.getSpaceSettings();
        BigDecimal platformFeePercentage = settings.getPlatformFeePercentage();
        BigDecimal platformFixedFee = settings.getPlatformFixedFee();

        // Calculate base price with cleaning fee (platform fees are calculated on thistotal)
        BigDecimal basePriceWithCleaning = finalTotalDaysPrice.add(space.getCleaningFee());

        // Calculate subtotal: totalDaysPrice + cleaningFee
        BigDecimal subtotal = finalTotalDaysPrice.add(space.getCleaningFee());

        // Calculate platform fees
        // Platform fees apply on basePrice + cleaningFee (if total > 0)
        BigDecimal platformFeeAmount = BigDecimal.ZERO;
        BigDecimal platformFixedFeeAmount = BigDecimal.ZERO;

        if (basePriceWithCleaning.compareTo(BigDecimal.ZERO) > 0) {
            // Platform fee amount = percentage of (base price + cleaning fee)
            // Round up to 1 decimal place (no cents) - always round up
            if (platformFeePercentage != null && platformFeePercentage.compareTo(BigDecimal.ZERO) > 0) {
                platformFeeAmount = basePriceWithCleaning.multiply(platformFeePercentage)
                        .divide(BigDecimal.valueOf(100), 1, java.math.RoundingMode.CEILING);
            }

            // Platform fixed fee is constant per transaction (only for paid reservations)
            // Round up to 1 decimal place (no cents) - always round up
            if (platformFixedFee != null && platformFixedFee.compareTo(BigDecimal.ZERO) > 0) {
                platformFixedFeeAmount = platformFixedFee.setScale(1, java.math.RoundingMode.CEILING);
            }
        }

        // Calculate total price: basePrice + cleaningFee + deposit + platformFees
        BigDecimal totalAmount = basePriceWithCleaning
                .add(space.getDeposit())
                .add(platformFeeAmount)
                .add(platformFixedFeeAmount);

        BigDecimal finalTotalAmount = totalAmount.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : totalAmount;

        // Create PriceCalculationResult with calculated fees
        PriceCalculationResult result = new PriceCalculationResult(
                finalTotalDaysPrice, // totalDaysPrice
                unitPrice, // unitPrice
                numberOfDays, // numberOfDays
                subtotal, // subtotal
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