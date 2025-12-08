package com.neohoods.portal.platform.assistant.mcp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.SpacesService;
import com.neohoods.portal.platform.spaces.services.StripeService;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler for reservation-related MCP tools
 */
@Component
@Slf4j
public class MatrixMCPReservationHandler extends MatrixMCPBaseHandler {

        private final ReservationsService reservationsService;
        private final SpacesService spacesService;
        private final ReservationRepository reservationRepository;
        private final StripeService stripeService;

        @Value("${neohoods.portal.frontend-url}")
        private String frontendUrl;

        @PersistenceContext
        private EntityManager entityManager;

        public MatrixMCPReservationHandler(
                        MessageSource messageSource,
                        UsersRepository usersRepository,
                        @Autowired(required = false) MatrixAssistantAdminCommandService adminCommandService,
                        ReservationsService reservationsService,
                        SpacesService spacesService,
                        ReservationRepository reservationRepository,
                        StripeService stripeService) {
                super(messageSource, usersRepository, adminCommandService);
                this.reservationsService = reservationsService;
                this.spacesService = spacesService;
                this.reservationRepository = reservationRepository;
                this.stripeService = stripeService;
        }

        public MatrixMCPModels.MCPToolResult createReservation(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                // Get user from auth context
                UserEntity userFromContext = authContext.getAuthenticatedUser();
                UserEntity user = null;

                // First, try to get user from current persistence context (fastest)
                try {
                        user = entityManager.find(UserEntity.class, userFromContext.getId());
                } catch (Exception e) {
                        log.debug("User {} not found in current persistence context: {}", userFromContext.getId(),
                                        e.getMessage());
                }

                // If not found, try to reload from database
                if (user == null) {
                        user = usersRepository.findById(userFromContext.getId()).orElse(null);
                }

                // If still not found, try by username
                if (user == null && userFromContext.getUsername() != null) {
                        user = usersRepository.findByUsername(userFromContext.getUsername());
                }

                // If still not found, use merge() to attach the detached entity to the current
                // session
                // This works in tests where user is created in a different transaction
                if (user == null) {
                        log.debug("User {} not found in database, merging detached entity into current session",
                                        userFromContext.getId());
                        try {
                                // merge() will attach the entity to the current session
                                // It works even if the entity is from a different transaction
                                user = entityManager.merge(userFromContext);
                                log.debug("Successfully merged user {} into current session", userFromContext.getId());

                                // Ensure primaryUnit is loaded and attached to the session
                                // This prevents foreign key constraint violations
                                if (user.getPrimaryUnit() != null) {
                                        UUID primaryUnitId = user.getPrimaryUnit().getId();
                                        UnitEntity primaryUnit = entityManager.find(
                                                        com.neohoods.portal.platform.entities.UnitEntity.class,
                                                        primaryUnitId);
                                        if (primaryUnit != null) {
                                                user.setPrimaryUnit(primaryUnit);
                                                log.info("✅ Loaded and attached primaryUnit {} to user {} in session",
                                                                primaryUnit.getId(), user.getId());
                                        } else {
                                                log.warn("⚠️ PrimaryUnit {} not found in database for user {}, clearing it",
                                                                primaryUnitId, user.getId());
                                                user.setPrimaryUnit(null);
                                        }
                                } else {
                                        log.warn("⚠️ User {} has no primaryUnit set", user.getId());
                                }
                        } catch (Exception e) {
                                log.error("Error merging user into persistence context: {}", e.getMessage(), e);
                                throw new IllegalStateException(
                                                "User not found in database and cannot be attached to session: " +
                                                                userFromContext.getId() + ". Error: " + e.getMessage());
                        }
                } else {
                        // If user was found, ensure primaryUnit is loaded and attached
                        if (user.getPrimaryUnit() != null) {
                                UUID primaryUnitId = user.getPrimaryUnit().getId();
                                UnitEntity primaryUnit = entityManager.find(
                                                com.neohoods.portal.platform.entities.UnitEntity.class,
                                                primaryUnitId);
                                if (primaryUnit != null) {
                                        user.setPrimaryUnit(primaryUnit);
                                        log.info("✅ Loaded and attached primaryUnit {} to user {} in session",
                                                        primaryUnit.getId(), user.getId());
                                } else {
                                        log.warn("⚠️ PrimaryUnit {} not found in database for user {}, clearing it",
                                                        primaryUnitId, user.getId());
                                        user.setPrimaryUnit(null);
                                }
                        } else {
                                log.warn("⚠️ User {} has no primaryUnit set", user.getId());
                        }
                }
                // User is now attached to the current Hibernate session with primaryUnit loaded
                Locale locale = getLocale(user);

                String spaceIdStr = (String) arguments.get("spaceId");
                String startDateStr = (String) arguments.get("startDate");
                String endDateStr = (String) arguments.get("endDate");

                try {
                        // Validate UUID format first
                        if (spaceIdStr == null || spaceIdStr.isEmpty()) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.reservation.spaceIdRequired",
                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        // Check if it's a valid UUID format (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
                        if (!spaceIdStr.matches(
                                        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                                log.warn("Error creating reservation: Invalid UUID format '{}' (expected format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)",
                                                spaceIdStr);
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.reservation.invalidUuidFormat",
                                                                                locale, spaceIdStr)
                                                                                + ". "
                                                                                + translate("matrix.mcp.error.doNotRetry",
                                                                                                locale)
                                                                                + " "
                                                                                + translate("matrix.mcp.reservation.useListSpaces",
                                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        UUID spaceId = UUID.fromString(spaceIdStr);
                        LocalDate startDate = parseDateOrPeriod(startDateStr);
                        LocalDate endDate = parseDateOrPeriod(endDateStr);

                        if (startDate == null || endDate == null) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.space.availability.parseError",
                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        // Get space first to verify it exists
                        com.neohoods.portal.platform.spaces.entities.SpaceEntity space;
                        try {
                                space = spacesService.getSpaceById(spaceId);
                        } catch (com.neohoods.portal.platform.exceptions.CodedErrorException e) {
                                if (e.getMessage() != null && e.getMessage().contains("Space not found")) {
                                        log.warn("Error creating reservation: Space not found for ID {}", spaceIdStr);
                                        return MatrixMCPModels.MCPToolResult.builder()
                                                        .isError(true)
                                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                        .type("text")
                                                                        .text(translate("matrix.mcp.space.notFound",
                                                                                        locale, spaceIdStr)
                                                                                        + ". "
                                                                                        + translate("matrix.mcp.error.doNotRetry",
                                                                                                        locale))
                                                                        .build()))
                                                        .build();
                                }
                                throw e; // Re-throw other CodedErrorExceptions
                        }

                        // Check availability
                        if (!spacesService.isSpaceAvailable(spaceId, startDate, endDate)) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.reservation.spaceNotAvailable",
                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        // Create reservation via ReservationsService
                        ReservationEntity reservation = reservationsService.createReservation(
                                        space,
                                        user,
                                        startDate,
                                        endDate);

                        // Calculate number of nights
                        long nights = ChronoUnit.DAYS.between(startDate, endDate);

                        // Build summary
                        StringBuilder recap = new StringBuilder();
                        recap.append("✅ **").append(translate("matrix.mcp.reservation.createdSuccess", locale))
                                        .append("**\n\n");
                        recap.append("📋 **").append(translate("matrix.mcp.reservation.summary", locale))
                                        .append(":**\n");
                        recap.append("- ").append(translate("matrix.mcp.reservation.space", locale)).append(" ")
                                        .append(space.getName()).append("\n");
                        recap.append("- ").append(translate("matrix.mcp.reservation.from", locale)).append(" ")
                                        .append(startDate.format(
                                                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                                        .append(" ").append(translate("matrix.mcp.reservation.to", locale)).append(" ")
                                        .append(endDate.format(
                                                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                                        .append("\n");
                        recap.append("- ").append(translate("matrix.mcp.reservation.nights", locale)).append(" ")
                                        .append(nights).append("\n");
                        recap.append("- ").append(translate("matrix.mcp.reservation.id", locale)).append(": #")
                                        .append(reservation.getId().toString().substring(0, 8)).append("\n");
                        recap.append("- ").append(translate("matrix.mcp.reservation.status", locale)).append(" ")
                                        .append(getStatusDescription(reservation.getStatus(), locale)).append("\n");

                        // Add reservation link
                        String reservationUrl = frontendUrl + "/spaces/reservations/" + reservation.getId();
                        recap.append("\n🔗 **").append(translate("matrix.mcp.reservation.viewReservation", locale))
                                        .append(":** ").append(reservationUrl).append("\n");

                        // Check if space is free (parking spaces are typically free)
                        boolean isFree = (reservation.getTotalPrice() == null || reservation.getTotalPrice().compareTo(java.math.BigDecimal.ZERO) == 0)
                                        || (space.getTenantPrice() != null && space.getTenantPrice().compareTo(java.math.BigDecimal.ZERO) == 0);

                        if (!isFree && reservation.getTotalPrice() != null) {
                                recap.append("\n💰 **").append(translate("matrix.mcp.reservation.totalPrice", locale))
                                                .append(": ").append(reservation.getTotalPrice())
                                                .append("€**\n");
                        }

                        // Only mention payment if space is not free
                        if (!isFree) {
                                recap.append("\n🔗 **").append(translate("matrix.mcp.reservation.nextSteps", locale))
                                                .append(":**\n");
                                recap.append(translate("matrix.mcp.reservation.paymentLinkWillBeGenerated", locale))
                                                .append("\n");
                                recap.append(translate("matrix.mcp.reservation.afterPayment", locale)).append("\n\n");
                                recap.append("💡 **").append(translate("matrix.mcp.reservation.tip", locale)).append(":** ")
                                                .append(translate("matrix.mcp.reservation.tipText", locale));
                        } else {
                                // For free spaces, just confirm the reservation
                                recap.append("\n✅ ").append(translate("matrix.mcp.reservation.freeSpaceConfirmed", locale));
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(recap.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error creating reservation: {}", e.getMessage(), e);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.reservation.createError", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult listMyReservations(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                Locale locale = getLocaleFromAuthContext(authContext);

                try {
                        UserEntity user = authContext.getAuthenticatedUser();
                        String statusFilter = (String) arguments.getOrDefault("status", "all");

                        List<ReservationEntity> reservations = reservationRepository.findByUser(user);

                        // Filter by status
                        LocalDate now = LocalDate.now();
                        List<ReservationEntity> filtered = reservations.stream()
                                        .filter(r -> {
                                                if ("all".equals(statusFilter))
                                                        return true;
                                                if ("current".equals(statusFilter)) {
                                                        return (r.getStartDate().isBefore(now)
                                                                        || r.getStartDate().isEqual(now)) &&
                                                                        (r.getEndDate().isAfter(now)
                                                                                        || r.getEndDate().isEqual(now));
                                                }
                                                if ("upcoming".equals(statusFilter)) {
                                                        return r.getStartDate().isAfter(now);
                                                }
                                                if ("past".equals(statusFilter)) {
                                                        return r.getEndDate().isBefore(now);
                                                }
                                                return true;
                                        })
                                        .collect(Collectors.toList());

                        if (filtered.isEmpty()) {
                                String statusText = "all".equals(statusFilter) ? "" : statusFilter;
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.reservations.none", locale,
                                                                                statusText))
                                                                .build()))
                                                .build();
                        }

                        StringBuilder result = new StringBuilder();
                        result.append("📋 **")
                                        .append(translate("matrix.mcp.reservations.yourReservations", locale,
                                                        filtered.size()))
                                        .append("**\n\n");

                        for (ReservationEntity reservation : filtered) {
                                com.neohoods.portal.platform.spaces.entities.SpaceEntity space = reservation.getSpace();
                                result.append("🏠 **").append(space != null ? space.getName()
                                                : translate("matrix.mcp.reservations.spaceUnknown", locale))
                                                .append("**\n");
                                result.append("   - ").append(translate("matrix.mcp.reservation.id", locale))
                                                .append(": ")
                                                .append(reservation.getId()).append("\n");
                                result.append("   - ").append(translate("matrix.mcp.reservation.from", locale))
                                                .append(" ")
                                                .append(reservation.getStartDate()).append(" ")
                                                .append(translate("matrix.mcp.reservation.to", locale)).append(" ")
                                                .append(reservation.getEndDate()).append("\n");
                                result.append("   - ").append(translate("matrix.mcp.reservation.status", locale))
                                                .append(": ")
                                                .append(reservation.getStatus()).append("\n");
                                if (reservation.getStatus() != null) {
                                        result.append("   - ")
                                                        .append(getStatusDescription(reservation.getStatus(), locale))
                                                        .append("\n");
                                }
                                result.append("\n");
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error listing user reservations: {}", e.getMessage(), e);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.reservations.listError", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult getReservationDetails(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                Locale locale = getLocaleFromAuthContext(authContext);
                String reservationIdStr = (String) arguments.get("reservationId");
                if (reservationIdStr == null || reservationIdStr.isEmpty()) {
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.reservation.reservationIdRequired",
                                                                        locale))
                                                        .build()))
                                        .build();
                }

                try {
                        UUID reservationId = UUID.fromString(reservationIdStr);
                        ReservationEntity reservation = reservationRepository.findById(reservationId)
                                        .orElseThrow(() -> new IllegalArgumentException(translate(
                                                        "matrix.mcp.reservation.notFound", locale)));

                        // Verify the reservation belongs to the authenticated user or user is admin
                        UserEntity user = authContext.getAuthenticatedUser();
                        boolean isAdmin = needAdminRole(user);
                        if (!isAdmin && !reservation.getUser().getId().equals(user.getId())) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.reservation.noAccess",
                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        StringBuilder result = new StringBuilder();
                        result.append("📋 **").append(translate("matrix.mcp.reservation.details", locale))
                                        .append("**\n\n");
                        result.append(translate("matrix.mcp.reservation.id", locale)).append(": ")
                                        .append(reservation.getId()).append("\n");
                        result.append(translate("matrix.mcp.reservation.space", locale)).append(": ")
                                        .append(reservation.getSpace().getName()).append("\n");
                        result.append(translate("matrix.mcp.reservation.from", locale)).append(" ")
                                        .append(reservation.getStartDate()).append(" ")
                                        .append(translate("matrix.mcp.reservation.to", locale)).append(" ")
                                        .append(reservation.getEndDate()).append("\n");
                        result.append(translate("matrix.mcp.reservation.status", locale)).append(": ")
                                        .append(getStatusDescription(reservation.getStatus(), locale)).append("\n");
                        if (reservation.getTotalPrice() != null) {
                                result.append(translate("matrix.mcp.reservation.totalPrice", locale)).append(": ")
                                                .append(reservation.getTotalPrice()).append("€\n");
                        }
                        if (reservation.getStatus() == ReservationStatusForEntity.CANCELLED
                                        && reservation.getCancellationReason() != null) {
                                result.append("\n")
                                                .append(translate("matrix.mcp.reservation.cancellationReason", locale))
                                                .append(": ").append(reservation.getCancellationReason()).append("\n");
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting reservation details: {}", e.getMessage(), e);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.reservation.detailsError", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult getReservationAccessCode(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                Locale locale = getLocaleFromAuthContext(authContext);
                String reservationIdStr = (String) arguments.get("reservationId");
                if (reservationIdStr == null || reservationIdStr.isEmpty()) {
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.reservations.accessCode.reservationIdRequired",
                                                                        locale))
                                                        .build()))
                                        .build();
                }

                try {
                        UUID reservationId = UUID.fromString(reservationIdStr);
                        ReservationEntity reservation = reservationRepository.findById(reservationId)
                                        .orElseThrow(() -> new IllegalArgumentException(translate(
                                                        "matrix.mcp.reservations.accessCode.notFound", locale)));

                        // Verify the reservation belongs to the authenticated user
                        UserEntity user = authContext.getAuthenticatedUser();
                        if (!reservation.getUser().getId().equals(user.getId())) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.reservations.accessCode.noAccess",
                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        StringBuilder result = new StringBuilder();
                        result.append("🔑 **").append(translate("matrix.mcp.reservations.accessCode.title", locale))
                                        .append("**\n\n");
                        result.append(translate("matrix.mcp.reservations.accessCode.reservation", locale)).append(": ")
                                        .append(reservation.getId()).append("\n");
                        result.append(translate("matrix.mcp.reservation.space", locale)).append(": ")
                                        .append(reservation.getSpace().getName()).append("\n");
                        result.append(translate("matrix.mcp.reservation.from", locale)).append(" ")
                                        .append(reservation.getStartDate()).append(" ")
                                        .append(translate("matrix.mcp.reservation.to", locale)).append(" ")
                                        .append(reservation.getEndDate()).append("\n\n");

                        // Future improvement: Get actual access code from reservation when available
                        result.append("🔐 **").append(translate("matrix.mcp.reservations.accessCode.code", locale))
                                        .append("**: ")
                                        .append(translate("matrix.mcp.reservations.accessCode.toGenerate", locale))
                                        .append("\n\n");

                        result.append("📋 **")
                                        .append(translate("matrix.mcp.reservations.accessCode.instructions", locale))
                                        .append("**\n");
                        result.append("**").append(translate("matrix.mcp.reservations.accessCode.checkin", locale))
                                        .append(":**\n");
                        result.append("- ").append(translate("matrix.mcp.reservations.accessCode.checkinTime", locale))
                                        .append("\n");
                        result.append("- ").append(translate("matrix.mcp.reservations.accessCode.checkinCode", locale))
                                        .append("\n\n");

                        result.append("**").append(translate("matrix.mcp.reservations.accessCode.checkout", locale))
                                        .append(":**\n");
                        result.append("- ").append(translate("matrix.mcp.reservations.accessCode.checkoutTime", locale))
                                        .append("\n");
                        result.append("- ").append(translate("matrix.mcp.reservations.accessCode.checkoutKeys", locale))
                                        .append("\n\n");

                        result.append("**").append(translate("matrix.mcp.reservations.accessCode.sheets", locale))
                                        .append(":**\n");
                        result.append("- ")
                                        .append(translate("matrix.mcp.reservations.accessCode.sheetsProvided", locale))
                                        .append("\n");
                        result.append("- ").append(translate("matrix.mcp.reservations.accessCode.towels", locale))
                                        .append("\n\n");

                        result.append("**").append(translate("matrix.mcp.reservations.accessCode.problem", locale))
                                        .append(":**\n");
                        result.append("- ")
                                        .append(translate("matrix.mcp.reservations.accessCode.contactSupport", locale))
                                        .append("\n");
                        result.append("- ").append(
                                        translate("matrix.mcp.reservations.accessCode.emergencyNumbers", locale))
                                        .append("\n");

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting reservation access code: {}", e.getMessage(), e);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.reservations.accessCode.error",
                                                                        locale, e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult generatePaymentLink(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                Locale locale = getLocaleFromAuthContext(authContext);
                String reservationIdStr = (String) arguments.get("reservationId");
                if (reservationIdStr == null || reservationIdStr.isEmpty()) {
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.payment.reservationIdRequired",
                                                                        locale))
                                                        .build()))
                                        .build();
                }

                try {
                        UUID reservationId = UUID.fromString(reservationIdStr);
                        ReservationEntity reservation = reservationRepository.findById(reservationId)
                                        .orElseThrow(() -> new IllegalArgumentException(translate(
                                                        "matrix.mcp.reservations.accessCode.notFound", locale)));

                        // Verify the reservation belongs to the authenticated user
                        UserEntity user = authContext.getAuthenticatedUser();
                        if (!reservation.getUser().getId().equals(user.getId())) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.reservations.accessCode.noAccess",
                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        // Check if payment link already exists and payment is still pending
                        if (reservation.getStripeSessionId() != null && !reservation.getStripeSessionId().isEmpty() &&
                                        reservation.getStatus() == ReservationStatusForEntity.PENDING_PAYMENT) {
                                // Session exists, but we can't retrieve the URL from Stripe without making an
                                // API call
                                // For now, we'll create a new session if needed
                                // In production, you might want to retrieve the session from Stripe to get the
                                // URL
                                log.debug("Reservation {} already has a Stripe session, but creating new one for URL",
                                                reservationId);
                        }

                        // Create PaymentIntent first if not exists
                        if (reservation.getStripePaymentIntentId() == null
                                        || reservation.getStripePaymentIntentId().isEmpty()) {
                                try {
                                        String paymentIntentId = stripeService.createPaymentIntent(
                                                        reservation,
                                                        user,
                                                        reservation.getSpace());
                                        reservation.setStripePaymentIntentId(paymentIntentId);
                                        reservationRepository.save(reservation);
                                } catch (Exception e) {
                                        log.error("Error creating payment intent: {}", e.getMessage(), e);
                                        return MatrixMCPModels.MCPToolResult.builder()
                                                        .isError(true)
                                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                        .type("text")
                                                                        .text(translate("matrix.mcp.payment.createIntentError",
                                                                                        locale, e.getMessage()))
                                                                        .build()))
                                                        .build();
                                }
                        }

                        // Create checkout session
                        String checkoutUrl;
                        try {
                                checkoutUrl = stripeService.createCheckoutSession(
                                                reservation,
                                                user,
                                                reservation.getSpace());
                                // Save the updated reservation with session ID
                                reservationRepository.save(reservation);
                        } catch (Exception e) {
                                log.error("Error creating checkout session: {}", e.getMessage(), e);
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.payment.createLinkError",
                                                                                locale, e.getMessage()))
                                                                .build()))
                                                .build();
                        }

                        StringBuilder result = new StringBuilder();
                        result.append("💳 **").append(translate("matrix.mcp.payment.linkGenerated", locale))
                                        .append("**\n\n");
                        result.append("📋 **").append(translate("matrix.mcp.reservation.id", locale)).append(":** ")
                                        .append(reservation.getId()).append("\n");
                        result.append("🏠 **").append(translate("matrix.mcp.reservation.space", locale)).append(":** ")
                                        .append(reservation.getSpace().getName()).append("\n");
                        if (reservation.getTotalPrice() != null) {
                                result.append("💰 **").append(translate("matrix.mcp.payment.amount", locale))
                                                .append(":** ")
                                                .append(reservation.getTotalPrice()).append("€\n\n");
                        }
                        result.append("🔗 **").append(translate("matrix.mcp.payment.link", locale)).append(":** ")
                                        .append(checkoutUrl).append("\n\n");
                        result.append("⏰ **").append(translate("matrix.mcp.payment.important", locale)).append(":**\n");
                        result.append(translate("matrix.mcp.payment.afterPayment", locale)).append("\n\n");
                        result.append("💡 **").append(translate("matrix.mcp.payment.afterPaymentTip", locale))
                                        .append("**");

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error generating payment link: {}", e.getMessage(), e);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.payment.generateError", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        /**
         * Parse a date string or period name (e.g., "tomorrow", "demain", "2024-12-07")
         * Similar to MatrixMCPSpaceHandler.parseDateOrPeriod
         */
        private LocalDate parseDateOrPeriod(String dateOrPeriod) {
                if (dateOrPeriod == null || dateOrPeriod.trim().isEmpty()) {
                        return null;
                }

                // Try to parse as ISO date first
                try {
                        return LocalDate.parse(dateOrPeriod);
                } catch (Exception e) {
                        // Try to parse period names
                        String lower = dateOrPeriod.toLowerCase().trim();
                        LocalDate now = LocalDate.now();

                        // Handle "tomorrow" and "demain"
                        if (lower.equals("tomorrow") || lower.equals("demain")) {
                                return now.plusDays(1);
                        }

                        // Handle "today" and "aujourd'hui"
                        if (lower.equals("today") || lower.equals("aujourd'hui") || lower.equals("aujourd hui")) {
                                return now;
                        }

                        // Handle "yesterday" and "hier"
                        if (lower.equals("yesterday") || lower.equals("hier")) {
                                return now.minusDays(1);
                        }

                        if (lower.contains("noel") || lower.contains("christmas")) {
                                // Christmas is December 24-25
                                int year = now.getYear();
                                if (now.isAfter(LocalDate.of(year, 12, 24))) {
                                        year++; // Next year if we're past Christmas
                                }
                                if (lower.contains("24") || lower.contains("eve")) {
                                        return LocalDate.of(year, 12, 24);
                                }
                                return LocalDate.of(year, 12, 25);
                        } else if (lower.contains("semaine prochaine") || lower.contains("next week")) {
                                return now.plusWeeks(1);
                        } else if (lower.contains("semaine") || lower.contains("week")) {
                                return now.plusWeeks(1);
                        } else if (lower.contains("mois prochain") || lower.contains("next month")) {
                                return now.plusMonths(1);
                        }

                        return null;
                }
        }

        public String getStatusDescription(ReservationStatusForEntity status, Locale locale) {
                String key = "matrix.mcp.reservation.status." + status.name().toLowerCase();
                return translate(key, locale);
        }
}
