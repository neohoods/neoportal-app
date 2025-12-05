package com.neohoods.portal.platform.services.matrix.mcp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContext;
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

        public MatrixMCPReservationHandler(
                        MessageSource messageSource,
                        UsersRepository usersRepository,
                        MatrixAssistantAdminCommandService adminCommandService,
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
                UserEntity user = authContext.getAuthenticatedUser();
                Locale locale = getLocale(user);

                String spaceIdStr = (String) arguments.get("spaceId");
                String startDateStr = (String) arguments.get("startDate");
                String endDateStr = (String) arguments.get("endDate");

                try {
                        UUID spaceId = UUID.fromString(spaceIdStr);
                        LocalDate startDate = LocalDate.parse(startDateStr);
                        LocalDate endDate = LocalDate.parse(endDateStr);

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

                        // Get space
                        com.neohoods.portal.platform.spaces.entities.SpaceEntity space = spacesService.getSpaceById(spaceId);

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
                        recap.append("- ").append(translate("matrix.mcp.reservation.id", locale)).append(" ")
                                        .append(reservation.getId()).append("\n");
                        recap.append("- ").append(translate("matrix.mcp.reservation.status", locale)).append(" ")
                                        .append(getStatusDescription(reservation.getStatus(), locale)).append("\n\n");

                        if (reservation.getTotalPrice() != null) {
                                recap.append("💰 **").append(translate("matrix.mcp.reservation.totalPrice", locale))
                                                .append(": ").append(reservation.getTotalPrice())
                                                .append("€**\n\n");
                        } else {
                                recap.append("💰 **")
                                                .append(translate("matrix.mcp.reservation.priceCalculated", locale))
                                                .append("**\n\n");
                        }

                        recap.append("🔗 **").append(translate("matrix.mcp.reservation.nextSteps", locale))
                                        .append(":**\n");
                        recap.append(translate("matrix.mcp.reservation.paymentLinkWillBeGenerated", locale))
                                        .append("\n");
                        recap.append(translate("matrix.mcp.reservation.afterPayment", locale)).append("\n\n");
                        recap.append("💡 **").append(translate("matrix.mcp.reservation.tip", locale)).append(":** ")
                                        .append(translate("matrix.mcp.reservation.tipText", locale));

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

        public String getStatusDescription(ReservationStatusForEntity status, Locale locale) {
                String key = "matrix.mcp.reservation.status." + status.name().toLowerCase();
                return translate(key, locale);
        }
}

