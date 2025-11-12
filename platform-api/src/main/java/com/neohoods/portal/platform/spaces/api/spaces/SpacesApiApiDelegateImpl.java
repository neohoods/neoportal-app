package com.neohoods.portal.platform.spaces.api.spaces;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.SpacesApiApiDelegate;
import com.neohoods.portal.platform.model.AvailabilityResponse;
import com.neohoods.portal.platform.model.CleaningSettings;
import com.neohoods.portal.platform.model.OccupancyCalendarDay;
import com.neohoods.portal.platform.model.OccupancyCalendarDayPublic;
import com.neohoods.portal.platform.model.PaginatedSpaces;
import com.neohoods.portal.platform.model.QuotaInfo;
import com.neohoods.portal.platform.model.Reservation;
import com.neohoods.portal.platform.model.Space;
import com.neohoods.portal.platform.model.SpaceImage;
import com.neohoods.portal.platform.model.SpaceOccupancyCalendarResponse;
import com.neohoods.portal.platform.model.SpacePricing;
import com.neohoods.portal.platform.model.SpaceRules;
import com.neohoods.portal.platform.model.SpaceType;
import com.neohoods.portal.platform.model.TimeRange;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceImageEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.services.ReservationMapper;
import com.neohoods.portal.platform.spaces.services.SpaceStatisticsService;
import com.neohoods.portal.platform.spaces.services.SpacesService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SpacesApiApiDelegateImpl implements SpacesApiApiDelegate {

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private SpaceStatisticsService spaceStatisticsService;

    @Override
    public Mono<ResponseEntity<PaginatedSpaces>> getSpaces(
            SpaceType type, Boolean available, LocalDate startDate, LocalDate endDate, String search, Integer page,
            Integer size, ServerWebExchange exchange) {
        // Create a Pageable for getting spaces with filters
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20);

        // Convert API type to entity type
        SpaceTypeForEntity entityType = type != null ? convertApiTypeToEntityType(type) : null;

        // Choose the appropriate method based on whether dates are provided
        Page<SpaceEntity> pageResult;
        if (startDate != null && endDate != null) {
            // Search for available spaces in the date range
            pageResult = spacesService.getAvailableSpacesWithFilters(entityType, startDate, endDate, pageable);
        } else {
            // Regular search without date filtering
            List<SpaceEntity> spaces = spacesService.getActiveSpacesWithImagesAndFilters(entityType, search, pageable);
            pageResult = new org.springframework.data.domain.PageImpl<>(spaces, pageable, spaces.size());
        }

        // Convert to API models
        List<Space> spaces = pageResult.getContent().stream()
                .map(this::convertToApiModel)
                .toList();

        PaginatedSpaces response = new PaginatedSpaces();
        response.setContent(spaces);
        response.setTotalElements(pageResult.getTotalElements());
        response.setTotalPages(pageResult.getTotalPages());
        response.setNumber(pageResult.getNumber());
        response.setSize(pageResult.getSize());
        response.setFirst(pageResult.isFirst());
        response.setLast(pageResult.isLast());
        response.setNumberOfElements(pageResult.getNumberOfElements());
        return Mono.just(ResponseEntity.ok(response));
    }

    @Override
    public Mono<ResponseEntity<Space>> getSpace(UUID spaceId, ServerWebExchange exchange) {
        // Get space from service with images
        SpaceEntity entity = spacesService.getSpaceByIdWithImages(spaceId);

        if (entity == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        Space space = convertToApiModel(entity);
        return Mono.just(ResponseEntity.ok(space));
    }

    @Override
    public Mono<ResponseEntity<AvailabilityResponse>> getSpaceAvailability(
            UUID spaceId, LocalDate startDate, LocalDate endDate, ServerWebExchange exchange) {
        // Check space availability
        SpaceEntity entity = spacesService.getSpaceById(spaceId);

        if (entity == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        // Check if space is available for the given date range
        boolean isAvailable = spacesService.isSpaceAvailable(spaceId, startDate, endDate);

        AvailabilityResponse response = new AvailabilityResponse();
        response.setAvailable(isAvailable);
        response.setPrice(entity.getTenantPrice().floatValue());
        response.setCurrency(entity.getCurrency());

        return Mono.just(ResponseEntity.ok(response));
    }

    @Override
    public Mono<ResponseEntity<SpaceOccupancyCalendarResponse>> getSpaceOccupancyCalendar(
            UUID spaceId, LocalDate startDate, LocalDate endDate, ServerWebExchange exchange) {
        // Get current authenticated user ID from security context
        return exchange.getPrincipal()
                .map(principal -> UUID.fromString(principal.getName()))
                .flatMap(currentUserId -> {
                    // Use default date range if not provided (same logic as admin endpoint)
                    LocalDate effectiveStartDate = startDate != null ? startDate
                            : LocalDate.of(LocalDate.now().getYear(), 1, 1);
                    LocalDate effectiveEndDate = endDate != null ? endDate
                            : LocalDate.of(LocalDate.now().getYear(), 12, 31);

                    // Get occupancy calendar from service
                    List<OccupancyCalendarDay> occupancyCalendar = spaceStatisticsService
                            .calculateOccupancyCalendarForUser(spaceId, currentUserId, effectiveStartDate,
                                    effectiveEndDate);

                    // Map to public API model
                    List<OccupancyCalendarDayPublic> publicCalendar = occupancyCalendar.stream()
                            .map(day -> {
                                OccupancyCalendarDayPublic publicDay = new OccupancyCalendarDayPublic();
                                publicDay.setDate(day.getDate());
                                publicDay.setIsOccupied(day.getIsOccupied());
                                // Convert reservationId from OccupancyCalendarDay (which is UUID) to
                                // OccupancyCalendarDayPublic (also UUID, nullable)
                                // The setter accepts @Nullable UUID, which will serialize as null in JSON
                                UUID reservationIdValue = day.getReservationId();
                                if (reservationIdValue != null) {
                                    publicDay.setReservationId(reservationIdValue);
                                } else {
                                    // Set to null (will serialize as null in JSON)
                                    publicDay.setReservationId(null);
                                }
                                return publicDay;
                            })
                            .toList();

                    // Build response
                    SpaceOccupancyCalendarResponse response = new SpaceOccupancyCalendarResponse();
                    response.setOccupancyCalendar(publicCalendar);

                    return Mono.<ResponseEntity<SpaceOccupancyCalendarResponse>>just(ResponseEntity.ok(response));
                })
                .switchIfEmpty(
                        Mono.<ResponseEntity<SpaceOccupancyCalendarResponse>>just(
                                ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build()));
    }

    @Override
    public Mono<ResponseEntity<Flux<Reservation>>> getSharedSpaceReservations(
            UUID spaceId, LocalDate startDate, LocalDate endDate, ServerWebExchange exchange) {
        try {
            // Get shared space reservations from service
            List<ReservationEntity> reservationEntities = spacesService
                    .getSharedSpaceReservations(spaceId, startDate, endDate);

            // Convert to API models
            List<Reservation> reservations = reservationEntities.stream()
                    .map(reservationMapper::toDto)
                    .toList();

            return Mono.just(ResponseEntity.ok(Flux.fromIterable(reservations)));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.notFound().build());
        }
    }

    // Helper methods
    private Space convertToApiModel(SpaceEntity entity) {
        Space space = new Space();
        space.setId(entity.getId());
        space.setName(entity.getName());
        space.setType(convertEntityTypeToApiType(entity.getType()));
        space.setDescription(entity.getDescription());
        space.setInstructions(entity.getInstructions());
        space.setCreatedAt(entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
        space.setUpdatedAt(entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC));

        // Set digital lock ID if present
        if (entity.getDigitalLockId() != null) {
            space.setDigitalLockId(entity.getDigitalLockId());
        }

        // Set access code enabled
        space.setAccessCodeEnabled(entity.getAccessCodeEnabled());

        // Map images - handle lazy initialization
        try {
            if (entity.getImages() != null && !entity.getImages().isEmpty()) {
                List<SpaceImage> spaceImages = entity.getImages().stream()
                        .map(this::convertImageToApiModel)
                        .toList();
                space.setImages(spaceImages);
            } else {
                space.setImages(new ArrayList<>());
            }
        } catch (org.hibernate.LazyInitializationException e) {
            // Collection not initialized - set empty list
            // This should not happen if methods with fetch join are used
            space.setImages(new ArrayList<>());
        }

        // Map pricing with correct API model properties
        if (entity.getTenantPrice() != null) {
            SpacePricing pricing = new SpacePricing();
            pricing.setOwnerPrice(entity.getOwnerPrice() != null ? entity.getOwnerPrice().floatValue() : 0.0f);
            pricing.setTenantPrice(entity.getTenantPrice().floatValue());
            pricing.setCleaningFee(entity.getCleaningFee() != null ? entity.getCleaningFee().floatValue() : 0.0f);
            pricing.setDeposit(entity.getDeposit() != null ? entity.getDeposit().floatValue() : 0.0f);
            pricing.setCurrency(entity.getCurrency());
            space.setPricing(pricing);
        }

        // Map rules with correct API model properties
        SpaceRules rules = new SpaceRules();
        rules.setMinDurationDays(entity.getMinDurationDays());
        rules.setMaxDurationDays(entity.getMaxDurationDays());

        // Convert DayOfWeek to AllowedDaysEnum - handle lazy initialization
        try {
            // Initialize allowedDays collection before accessing it
            org.hibernate.Hibernate.initialize(entity.getAllowedDays());
            if (entity.getAllowedDays() != null && !entity.getAllowedDays().isEmpty()) {
                List<SpaceRules.AllowedDaysEnum> allowedDays = entity.getAllowedDays()
                        .stream()
                        .map(this::convertDayOfWeekToAllowedDaysEnum)
                        .toList();
                rules.setAllowedDays(allowedDays);
            } else {
                rules.setAllowedDays(new ArrayList<>());
            }
        } catch (org.hibernate.LazyInitializationException e) {
            // Collection not initialized - set empty list
            // This should not happen if methods with fetch join are used
            rules.setAllowedDays(new ArrayList<>());
        }

        // Map allowed hours
        if (entity.getAllowedHoursStart() != null && entity.getAllowedHoursEnd() != null) {
            TimeRange allowedHours = new TimeRange();
            allowedHours.setStart(entity.getAllowedHoursStart());
            allowedHours.setEnd(entity.getAllowedHoursEnd());
            rules.setAllowedHours(allowedHours);
        }

        space.setRules(rules);

        // Set capacity
        space.setCapacity(entity.getCapacity());

        // Map quota with correct API model properties
        if (entity.getMaxAnnualReservations() != null && entity.getMaxAnnualReservations() > 0) {
            QuotaInfo quota = new QuotaInfo();
            quota.setMax(entity.getMaxAnnualReservations());
            quota.setPeriod(QuotaInfo.PeriodEnum.YEAR);
            space.setQuota(quota);
        }

        // Map cleaning settings (public API - no calendar URL)
        if (entity.getCleaningEnabled() != null && entity.getCleaningEnabled()) {
            CleaningSettings cleaningSettings = new CleaningSettings();
            cleaningSettings.setCleaningEnabled(entity.getCleaningEnabled());
            cleaningSettings.setCleaningEmail(entity.getCleaningEmail());
            cleaningSettings.setCleaningNotificationsEnabled(entity.getCleaningNotificationsEnabled());
            cleaningSettings.setCleaningCalendarEnabled(entity.getCleaningCalendarEnabled());
            cleaningSettings.setCleaningDaysAfterCheckout(entity.getCleaningDaysAfterCheckout());
            cleaningSettings.setCleaningHour(entity.getCleaningHour());
            // calendarUrl is not included in public API for security
            space.setCleaningSettings(cleaningSettings);
        }

        return space;
    }

    private SpaceType convertEntityTypeToApiType(SpaceTypeForEntity entityType) {
        return switch (entityType) {
            case GUEST_ROOM -> SpaceType.GUEST_ROOM;
            case COMMON_ROOM -> SpaceType.COMMON_ROOM;
            case COWORKING -> SpaceType.COWORKING;
            case PARKING -> SpaceType.PARKING;
        };
    }

    private SpaceTypeForEntity convertApiTypeToEntityType(SpaceType apiType) {
        return switch (apiType) {
            case GUEST_ROOM -> SpaceTypeForEntity.GUEST_ROOM;
            case COMMON_ROOM -> SpaceTypeForEntity.COMMON_ROOM;
            case COWORKING -> SpaceTypeForEntity.COWORKING;
            case PARKING -> SpaceTypeForEntity.PARKING;
        };
    }

    private SpaceImage convertImageToApiModel(SpaceImageEntity entity) {
        SpaceImage image = new SpaceImage();
        image.setId(entity.getId());

        // Set URL if present - simplified for now
        if (entity.getUrl() != null) {
            try {
                image.setUrl(java.net.URI.create(entity.getUrl()));
            } catch (Exception e) {
                // Set to null if URL is invalid
                image.setUrl(null);
            }
        } else {
            image.setUrl(null);
        }

        // Set alt text if present
        image.setAltText(entity.getAltText());

        image.setIsPrimary(entity.getIsPrimary());
        image.setOrderIndex(entity.getOrderIndex());
        image.setCreatedAt(entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
        image.setUpdatedAt(entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC));
        return image;
    }

    private SpaceRules.AllowedDaysEnum convertDayOfWeekToAllowedDaysEnum(
            java.time.DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> SpaceRules.AllowedDaysEnum.MONDAY;
            case TUESDAY -> SpaceRules.AllowedDaysEnum.TUESDAY;
            case WEDNESDAY -> SpaceRules.AllowedDaysEnum.WEDNESDAY;
            case THURSDAY -> SpaceRules.AllowedDaysEnum.THURSDAY;
            case FRIDAY -> SpaceRules.AllowedDaysEnum.FRIDAY;
            case SATURDAY -> SpaceRules.AllowedDaysEnum.SATURDAY;
            case SUNDAY -> SpaceRules.AllowedDaysEnum.SUNDAY;
        };
    }
}
