package com.neohoods.portal.platform.spaces.api.admin;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.SpacesAdminApiApiDelegate;
import com.neohoods.portal.platform.model.CleaningSettings;
import com.neohoods.portal.platform.model.PaginatedSpaces;
import com.neohoods.portal.platform.model.QuotaInfo;
import com.neohoods.portal.platform.model.Space;
import com.neohoods.portal.platform.model.SpaceImage;
import com.neohoods.portal.platform.model.SpacePricing;
import com.neohoods.portal.platform.model.SpaceRequest;
import com.neohoods.portal.platform.model.SpaceRules;
import com.neohoods.portal.platform.model.SpaceStatistics;
import com.neohoods.portal.platform.model.SpaceStatus;
import com.neohoods.portal.platform.model.SpaceType;
import com.neohoods.portal.platform.model.TimeRange;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceImageEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.services.CalendarTokenService;
import com.neohoods.portal.platform.spaces.services.SpaceStatisticsService;
import com.neohoods.portal.platform.spaces.services.SpacesService;

import reactor.core.publisher.Mono;

@Service
public class SpacesAdminApiApiDelegateImpl implements SpacesAdminApiApiDelegate {

    @Autowired
    private SpacesService spacesService;

    @Autowired
    private SpaceStatisticsService spaceStatisticsService;

    @Autowired
    private CalendarTokenService calendarTokenService;

    @org.springframework.beans.factory.annotation.Value("${neohoods.portal.base-url}")
    private String baseUrl;

    @Override
    public Mono<ResponseEntity<PaginatedSpaces>> getAdminSpaces(
            SpaceType type, SpaceStatus status, String search, Integer page, Integer size, ServerWebExchange exchange) {
        // Create a Pageable for getting spaces with filters
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20);

        // Convert API types to entity types
        SpaceTypeForEntity entityType = type != null ? convertApiTypeToEntityType(type) : null;
        SpaceStatusForEntity entityStatus = status != null ? convertApiStatusToEntityStatus(status) : null;

        // Use the new filtered query (without search)
        // Collections are already initialized in getSpacesWithFilters within
        // transaction
        Page<SpaceEntity> pageResult = spacesService.getSpacesWithFilters(entityType, entityStatus, pageable);

        // Convert to API models - collections should already be initialized
        // But add defensive checks in convertToApiModel in case
        List<Space> spaces = pageResult.getContent().stream()
                .map(this::convertToApiModel)
                .toList();

        PaginatedSpaces response = PaginatedSpaces.builder()
                .content(spaces)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .number(pageResult.getNumber())
                .size(pageResult.getSize())
                .first(pageResult.isFirst())
                .last(pageResult.isLast())
                .numberOfElements(pageResult.getNumberOfElements())
                .build();
        return Mono.just(ResponseEntity.ok(response));
    }

    @Override
    public Mono<ResponseEntity<Space>> createSpace(Mono<SpaceRequest> spaceRequest, ServerWebExchange exchange) {
        return spaceRequest.flatMap(request -> {
            SpaceEntity entity = convertRequestToEntity(request);
            SpaceEntity saved = spacesService.createSpace(entity);
            Space space = convertToApiModel(saved);
            return Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(space));
        });
    }

    @Override
    public Mono<ResponseEntity<Space>> getAdminSpace(UUID spaceId, ServerWebExchange exchange) {
        // Use getSpaceByIdWithImages - it already initializes all collections within
        // transaction
        SpaceEntity entity = spacesService.getSpaceByIdWithImages(spaceId);

        // Collections are already initialized in getSpaceByIdWithImages
        Space space = convertToApiModel(entity);
        return Mono.just(ResponseEntity.ok(space));
    }

    @Override
    public Mono<ResponseEntity<Space>> updateSpace(UUID spaceId, Mono<SpaceRequest> spaceRequest,
            ServerWebExchange exchange) {
        return spaceRequest.flatMap(request -> {
            SpaceEntity entity = spacesService.getSpaceById(spaceId);

            // Update entity with request data
            entity.setName(request.getName());
            entity.setType(convertApiTypeToEntityType(request.getType()));
            entity.setDescription(request.getDescription());

            // Update pricing information
            if (request.getPricing() != null) {
                SpacePricing pricing = request.getPricing();
                if (pricing.getTenantPrice() != null) {
                    entity.setTenantPrice(BigDecimal.valueOf(pricing.getTenantPrice()));
                }
                if (pricing.getOwnerPrice() != null) {
                    entity.setOwnerPrice(BigDecimal.valueOf(pricing.getOwnerPrice()));
                }
                if (pricing.getCleaningFee() != null) {
                    entity.setCleaningFee(BigDecimal.valueOf(pricing.getCleaningFee()));
                }
                if (pricing.getDeposit() != null) {
                    entity.setDeposit(BigDecimal.valueOf(pricing.getDeposit()));
                }
                if (pricing.getCurrency() != null) {
                    entity.setCurrency(pricing.getCurrency());
                }
            }

            // Update rules information
            if (request.getRules() != null) {
                SpaceRules rules = request.getRules();
                if (rules.getMinDurationDays() != null) {
                    entity.setMinDurationDays(rules.getMinDurationDays());
                }
                if (rules.getMaxDurationDays() != null) {
                    entity.setMaxDurationDays(rules.getMaxDurationDays());
                }
                if (rules.getRequiresApartmentAccess() != null) {
                    entity.setRequiresApartmentAccess(rules.getRequiresApartmentAccess());
                }

                // Convert AllowedDaysEnum to DayOfWeek
                if (rules.getAllowedDays() != null) {
                    List<DayOfWeek> allowedDays = rules.getAllowedDays().stream()
                            .map(day -> DayOfWeek.valueOf(day.getValue()))
                            .toList();
                    entity.setAllowedDays(allowedDays);
                }

                // Set allowed hours from TimeRange
                if (rules.getAllowedHours() != null) {
                    TimeRange timeRange = rules.getAllowedHours();
                    if (timeRange.getStart() != null) {
                        entity.setAllowedHoursStart(timeRange.getStart());
                    }
                    if (timeRange.getEnd() != null) {
                        entity.setAllowedHoursEnd(timeRange.getEnd());
                    }
                }

                // Convert CleaningDaysEnum to DayOfWeek
                if (rules.getCleaningDays() != null) {
                    List<DayOfWeek> cleaningDays = rules.getCleaningDays().stream()
                            .map(day -> DayOfWeek.valueOf(day.getValue()))
                            .toList();
                    entity.setCleaningDays(cleaningDays);
                }
            }

            // Update digital lock information
            if (request.getDigitalLockId() != null) {
                entity.setDigitalLockId(request.getDigitalLockId());
            }

            // Update status if provided
            if (request.getStatus() != null) {
                entity.setStatus(convertApiStatusToEntityStatus(request.getStatus()));
            }

            // Update cleaning settings
            if (request.getCleaningSettings() != null) {
                CleaningSettings cleaningSettings = request.getCleaningSettings();
                if (cleaningSettings.getCleaningEnabled() != null) {
                    entity.setCleaningEnabled(cleaningSettings.getCleaningEnabled());
                }
                if (cleaningSettings.getCleaningEmail() != null) {
                    entity.setCleaningEmail(cleaningSettings.getCleaningEmail());
                }
                if (cleaningSettings.getCleaningNotificationsEnabled() != null) {
                    entity.setCleaningNotificationsEnabled(cleaningSettings.getCleaningNotificationsEnabled());
                }
                if (cleaningSettings.getCleaningCalendarEnabled() != null) {
                    entity.setCleaningCalendarEnabled(cleaningSettings.getCleaningCalendarEnabled());
                }
                if (cleaningSettings.getCleaningDaysAfterCheckout() != null) {
                    entity.setCleaningDaysAfterCheckout(cleaningSettings.getCleaningDaysAfterCheckout());
                }
                if (cleaningSettings.getCleaningHour() != null) {
                    entity.setCleaningHour(cleaningSettings.getCleaningHour());
                }
            }

            SpaceEntity updated = spacesService.updateSpace(entity);
            Space space = convertToApiModel(updated);
            return Mono.just(ResponseEntity.ok(space));
        });
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteSpace(UUID spaceId, ServerWebExchange exchange) {
        spacesService.deleteSpace(spaceId);
        return Mono.just(ResponseEntity.noContent().<Void>build());
    }

    @Override
    public Mono<ResponseEntity<SpaceStatistics>> getSpaceStatistics(UUID spaceId, LocalDate startDate,
            LocalDate endDate, ServerWebExchange exchange) {
        // Use default date range if not provided
        LocalDate effectiveStartDate = startDate != null ? startDate : LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.of(LocalDate.now().getYear(), 12, 31);

        // Calculate real statistics using the service
        SpaceStatistics statistics = spaceStatisticsService.calculateSpaceStatistics(spaceId, effectiveStartDate,
                effectiveEndDate);

        return Mono.just(ResponseEntity.ok(statistics));
    }

    // Helper methods for conversion
    private Space convertToApiModel(SpaceEntity entity) {
        // Build pricing information
        SpacePricing pricing = null;
        if (entity.getTenantPrice() != null) {
            pricing = SpacePricing.builder()
                    .tenantPrice(entity.getTenantPrice().floatValue())
                    .ownerPrice(entity.getOwnerPrice() != null ? entity.getOwnerPrice().floatValue() : null)
                    .cleaningFee(entity.getCleaningFee() != null ? entity.getCleaningFee().floatValue() : null)
                    .deposit(entity.getDeposit() != null ? entity.getDeposit().floatValue() : null)
                    .currency(entity.getCurrency())
                    .build();
        }

        // Build rules information
        // Initialize allowedDays collection before accessing it
        List<SpaceRules.AllowedDaysEnum> allowedDaysList = null;
        try {
            org.hibernate.Hibernate.initialize(entity.getAllowedDays());
            if (entity.getAllowedDays() != null && !entity.getAllowedDays().isEmpty()) {
                allowedDaysList = entity.getAllowedDays().stream()
                        .map(day -> SpaceRules.AllowedDaysEnum.fromValue(day.name()))
                        .toList();
            }
        } catch (org.hibernate.LazyInitializationException e) {
            // Collection not initialized - set to null
            allowedDaysList = null;
        }

        // Initialize cleaningDays collection before accessing it
        List<SpaceRules.CleaningDaysEnum> cleaningDaysList = null;
        try {
            org.hibernate.Hibernate.initialize(entity.getCleaningDays());
            if (entity.getCleaningDays() != null && !entity.getCleaningDays().isEmpty()) {
                cleaningDaysList = entity.getCleaningDays().stream()
                        .map(day -> SpaceRules.CleaningDaysEnum.fromValue(day.name()))
                        .toList();
            }
        } catch (org.hibernate.LazyInitializationException e) {
            // Collection not initialized - set to null
            cleaningDaysList = null;
        }

        SpaceRules rules = SpaceRules.builder()
                .minDurationDays(entity.getMinDurationDays())
                .maxDurationDays(entity.getMaxDurationDays())
                .requiresApartmentAccess(entity.getRequiresApartmentAccess())
                .allowedDays(allowedDaysList)
                .allowedHours(entity.getAllowedHoursStart() != null && entity.getAllowedHoursEnd() != null
                        ? TimeRange.builder()
                                .start(entity.getAllowedHoursStart())
                                .end(entity.getAllowedHoursEnd())
                                .build()
                        : null)
                .cleaningDays(cleaningDaysList)
                .shareSpaceWith(entity.getShareSpaceWith())
                .build();

        // Build quota information
        QuotaInfo quota = null;
        if (entity.getMaxAnnualReservations() > 0) {
            quota = QuotaInfo.builder()
                    .max(entity.getMaxAnnualReservations())
                    .period(QuotaInfo.PeriodEnum.YEAR)
                    .build();
        }

        // Convert images - should already be initialized by service, but add defensive
        // check
        List<SpaceImage> images = new ArrayList<>();
        try {
            // Try to access images - if already initialized, this will work
            if (entity.getImages() != null) {
                // Force initialization if needed
                try {
                    org.hibernate.Hibernate.initialize(entity.getImages());
                } catch (org.hibernate.LazyInitializationException e) {
                    // Already initialized or session closed - try to iterate anyway
                }
                // Access the collection to force load
                int size = entity.getImages().size();
                if (size > 0) {
                    images = entity.getImages().stream()
                            .map(this::convertImageToApiModel)
                            .toList();
                }
            }
        } catch (org.hibernate.LazyInitializationException e) {
            // Collection not initialized - session closed before access
            // This should not happen if initialization was done in service transaction
            // Log warning in production, but return empty list for now
            images = new ArrayList<>();
        } catch (Exception e) {
            // Any other error - return empty list
            images = new ArrayList<>();
        }

        // Build cleaning settings
        // Always include cleaning settings if any cleaning-related field is set
        // This allows displaying calendar URL even if cleaningEnabled is false but
        // cleaningCalendarEnabled is true
        CleaningSettings cleaningSettings = null;
        if (entity.getCleaningEnabled() != null ||
                entity.getCleaningEmail() != null ||
                entity.getCleaningCalendarEnabled() != null ||
                entity.getCleaningNotificationsEnabled() != null) {
            cleaningSettings = CleaningSettings.builder()
                    .cleaningEnabled(entity.getCleaningEnabled() != null ? entity.getCleaningEnabled() : false)
                    .cleaningEmail(entity.getCleaningEmail())
                    .cleaningNotificationsEnabled(
                            entity.getCleaningNotificationsEnabled() != null ? entity.getCleaningNotificationsEnabled()
                                    : false)
                    .cleaningCalendarEnabled(
                            entity.getCleaningCalendarEnabled() != null ? entity.getCleaningCalendarEnabled() : false)
                    .cleaningDaysAfterCheckout(
                            entity.getCleaningDaysAfterCheckout() != null ? entity.getCleaningDaysAfterCheckout() : 0)
                    .cleaningHour(entity.getCleaningHour() != null ? entity.getCleaningHour() : "10:00")
                    .calendarUrl(entity.getCleaningCalendarEnabled() != null && entity.getCleaningCalendarEnabled()
                            ? java.net.URI
                                    .create(baseUrl + "/api/public/spaces/" + entity.getId() + "/calendar.ics?token="
                                            + calendarTokenService.generateToken(entity.getId(), "cleaning")
                                            + "&type=cleaning")
                            : null)
                    .build();
        }

        // Build the main Space object
        // Generate reservation calendar URL (always available for all spaces)
        java.net.URI reservationCalendarUrl = java.net.URI.create(
                baseUrl + "/api/public/spaces/" + entity.getId() + "/calendar.ics?token="
                        + calendarTokenService.generateToken(entity.getId(), "reservation")
                        + "&type=reservation");

        return Space.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(convertEntityTypeToApiType(entity.getType()))
                .status(convertEntityStatusToApiStatus(entity.getStatus()))
                .description(entity.getDescription())
                .instructions(entity.getInstructions())
                .pricing(pricing)
                .rules(rules)
                .images(images)
                .quota(quota)
                .digitalLockId(entity.getDigitalLockId())
                .accessCodeEnabled(entity.getAccessCodeEnabled())
                .cleaningSettings(cleaningSettings)
                .reservationCalendarUrl(reservationCalendarUrl)
                .createdAt(entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))
                .updatedAt(entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC))
                .build();
    }

    private SpaceEntity convertRequestToEntity(SpaceRequest request) {
        SpaceEntity entity = new SpaceEntity();
        entity.setName(request.getName());
        entity.setType(convertApiTypeToEntityType(request.getType()));
        entity.setDescription(request.getDescription());

        // Set pricing information
        if (request.getPricing() != null) {
            SpacePricing pricing = request.getPricing();
            if (pricing.getTenantPrice() != null) {
                entity.setTenantPrice(BigDecimal.valueOf(pricing.getTenantPrice()));
            }
            if (pricing.getOwnerPrice() != null) {
                entity.setOwnerPrice(BigDecimal.valueOf(pricing.getOwnerPrice()));
            }
            if (pricing.getCleaningFee() != null) {
                entity.setCleaningFee(BigDecimal.valueOf(pricing.getCleaningFee()));
            }
            if (pricing.getDeposit() != null) {
                entity.setDeposit(BigDecimal.valueOf(pricing.getDeposit()));
            }
            if (pricing.getCurrency() != null) {
                entity.setCurrency(pricing.getCurrency());
            }
        }

        // Set rules information
        if (request.getRules() != null) {
            SpaceRules rules = request.getRules();
            if (rules.getMinDurationDays() != null) {
                entity.setMinDurationDays(rules.getMinDurationDays());
            }
            if (rules.getMaxDurationDays() != null) {
                entity.setMaxDurationDays(rules.getMaxDurationDays());
            }
            if (rules.getRequiresApartmentAccess() != null) {
                entity.setRequiresApartmentAccess(rules.getRequiresApartmentAccess());
            }

            // Convert AllowedDaysEnum to DayOfWeek
            if (rules.getAllowedDays() != null) {
                List<DayOfWeek> allowedDays = rules.getAllowedDays().stream()
                        .map(day -> DayOfWeek.valueOf(day.getValue()))
                        .toList();
                entity.setAllowedDays(allowedDays);
            }

            // Set allowed hours from TimeRange
            if (rules.getAllowedHours() != null) {
                TimeRange timeRange = rules.getAllowedHours();
                if (timeRange.getStart() != null) {
                    entity.setAllowedHoursStart(timeRange.getStart());
                }
                if (timeRange.getEnd() != null) {
                    entity.setAllowedHoursEnd(timeRange.getEnd());
                }
            }

            // Convert CleaningDaysEnum to DayOfWeek
            if (rules.getCleaningDays() != null) {
                List<DayOfWeek> cleaningDays = rules.getCleaningDays().stream()
                        .map(day -> DayOfWeek.valueOf(day.getValue()))
                        .toList();
                entity.setCleaningDays(cleaningDays);
            }
        }

        // Set digital lock information
        if (request.getDigitalLockId() != null) {
            entity.setDigitalLockId(request.getDigitalLockId());
        }

        // Set cleaning settings
        if (request.getCleaningSettings() != null) {
            CleaningSettings cleaningSettings = request.getCleaningSettings();
            entity.setCleaningEnabled(cleaningSettings.getCleaningEnabled());
            entity.setCleaningEmail(cleaningSettings.getCleaningEmail());
            entity.setCleaningNotificationsEnabled(cleaningSettings.getCleaningNotificationsEnabled());
            entity.setCleaningCalendarEnabled(cleaningSettings.getCleaningCalendarEnabled());
            entity.setCleaningDaysAfterCheckout(cleaningSettings.getCleaningDaysAfterCheckout());
            entity.setCleaningHour(cleaningSettings.getCleaningHour());
        }

        return entity;
    }

    private com.neohoods.portal.platform.model.SpaceType convertEntityTypeToApiType(
            SpaceTypeForEntity entityType) {
        return switch (entityType) {
            case GUEST_ROOM -> SpaceType.GUEST_ROOM;
            case COMMON_ROOM -> SpaceType.COMMON_ROOM;
            case COWORKING -> SpaceType.COWORKING;
            case PARKING -> SpaceType.PARKING;
        };
    }

    private SpaceTypeForEntity convertApiTypeToEntityType(
            com.neohoods.portal.platform.model.SpaceType apiType) {
        return switch (apiType) {
            case GUEST_ROOM -> SpaceTypeForEntity.GUEST_ROOM;
            case COMMON_ROOM -> SpaceTypeForEntity.COMMON_ROOM;
            case COWORKING -> SpaceTypeForEntity.COWORKING;
            case PARKING -> SpaceTypeForEntity.PARKING;
        };
    }

    private SpaceStatus convertEntityStatusToApiStatus(
            SpaceStatusForEntity entityStatus) {
        return switch (entityStatus) {
            case ACTIVE -> SpaceStatus.ACTIVE;
            case INACTIVE -> SpaceStatus.INACTIVE;
            case MAINTENANCE -> SpaceStatus.MAINTENANCE;
            default -> SpaceStatus.ACTIVE;
        };
    }

    private SpaceStatusForEntity convertApiStatusToEntityStatus(SpaceStatus apiStatus) {
        return switch (apiStatus) {
            case ACTIVE -> SpaceStatusForEntity.ACTIVE;
            case INACTIVE -> SpaceStatusForEntity.INACTIVE;
            case MAINTENANCE -> SpaceStatusForEntity.MAINTENANCE;
        };
    }

    private SpaceImage convertImageToApiModel(SpaceImageEntity entity) {
        return SpaceImage.builder()
                .id(entity.getId())
                .spaceId(entity.getSpace().getId())
                .url(entity.getUrl() != null ? java.net.URI.create(entity.getUrl()) : null)
                .mimeType(entity.getMimeType())
                .fileName(entity.getFileName())
                .fileSize(entity.getFileSize())
                .altText(entity.getAltText())
                .isPrimary(entity.getIsPrimary())
                .orderIndex(entity.getOrderIndex())
                .createdAt(
                        entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC) : null)
                .updatedAt(
                        entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC) : null)
                .build();
    }
}
