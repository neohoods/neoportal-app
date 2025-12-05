package com.neohoods.portal.platform.services.matrix.mcp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;
import com.neohoods.portal.platform.spaces.services.SpacesService;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler for space-related MCP tools
 */
@Component
@Slf4j
public class MatrixMCPSpaceHandler extends MatrixMCPBaseHandler {

        private final SpacesService spacesService;
        private final SpaceRepository spaceRepository;

        public MatrixMCPSpaceHandler(
                        MessageSource messageSource,
                        UsersRepository usersRepository,
                        MatrixAssistantAdminCommandService adminCommandService,
                        SpacesService spacesService,
                        SpaceRepository spaceRepository) {
                super(messageSource, usersRepository, adminCommandService);
                this.spacesService = spacesService;
                this.spaceRepository = spaceRepository;
        }

        public MatrixMCPModels.MCPToolResult getSpaceInfo(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                String spaceIdStr = (String) arguments.get("spaceId");
                Locale locale = getLocaleFromAuthContext(authContext);

                if (spaceIdStr == null || spaceIdStr.isEmpty()) {
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.space.spaceIdRequired", locale))
                                                        .build()))
                                        .build();
                }

                try {
                        UUID spaceId = UUID.fromString(spaceIdStr);
                        SpaceEntity space = spacesService.getSpaceById(spaceId);

                        StringBuilder info = new StringBuilder();
                        info.append(translate("matrix.mcp.space.detailedInfo", locale)).append("\n\n");
                        info.append("📋 **").append(translate("matrix.mcp.space.generalInfo", locale)).append("**\n");
                        info.append("- ").append(translate("matrix.mcp.space.name", locale)).append(": ")
                                        .append(space.getName()).append("\n");
                        info.append("- ").append(translate("matrix.mcp.space.type", locale)).append(": ")
                                        .append(space.getType()).append("\n");
                        if (space.getDescription() != null) {
                                info.append("- ").append(translate("matrix.mcp.space.description", locale)).append(": ")
                                                .append(space.getDescription()).append("\n");
                        }
                        info.append("- ").append(translate("matrix.mcp.space.status", locale)).append(": ")
                                        .append(space.getStatus()).append("\n");
                        info.append("- ").append(translate("matrix.mcp.space.id", locale)).append(": ")
                                        .append(space.getId()).append("\n\n");

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(info.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.space.notFound", locale,
                                                                        spaceIdStr))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult listSpaces(MatrixAssistantAuthContext authContext) {
                Locale locale = getLocaleFromAuthContext(authContext);

                try {
                        List<SpaceEntity> spaces = spaceRepository.findAll();

                        if (spaces.isEmpty()) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.space.none", locale))
                                                                .build()))
                                                .build();
                        }

                        StringBuilder result = new StringBuilder();
                        result.append("📋 **").append(translate("matrix.mcp.space.available", locale, spaces.size()))
                                        .append("**\n\n");

                        for (SpaceEntity space : spaces) {
                                result.append("🏠 **").append(space.getName()).append("**\n");
                                result.append("   - ").append(translate("matrix.mcp.space.type", locale)).append(": ")
                                                .append(space.getType()).append("\n");
                                result.append("   - ").append(translate("matrix.mcp.space.id", locale)).append(": ")
                                                .append(space.getId()).append("\n");
                                if (space.getDescription() != null && !space.getDescription().isEmpty()) {
                                        String desc = space.getDescription();
                                        if (desc.length() > 100) {
                                                desc = desc.substring(0, 100) + "...";
                                        }
                                        result.append("   - ").append(translate("matrix.mcp.space.description", locale))
                                                        .append(": ").append(desc).append("\n");
                                }
                                result.append("   - ").append(translate("matrix.mcp.space.status", locale)).append(": ")
                                                .append(space.getStatus()).append("\n\n");
                        }

                        result.append("💡 ").append(translate("matrix.mcp.space.tip", locale));

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error listing spaces: {}", e.getMessage(), e);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.space.listError", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult checkSpaceAvailability(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                String spaceIdStr = (String) arguments.get("spaceId");
                String startDateStr = (String) arguments.get("startDate");
                String endDateStr = (String) arguments.get("endDate");
                Locale locale = getLocaleFromAuthContext(authContext);

                if (spaceIdStr == null || startDateStr == null || endDateStr == null) {
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.space.availability.paramsRequired",
                                                                        locale))
                                                        .build()))
                                        .build();
                }

                try {
                        UUID spaceId = UUID.fromString(spaceIdStr);
                        SpaceEntity space = spacesService.getSpaceById(spaceId);

                        // Parse dates - handle period names like "Christmas", "next week", etc.
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

                        if (startDate.isAfter(endDate)) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.space.availability.invalidDateRange",
                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        // Check availability for each day in the range
                        List<LocalDate> availableDates = new ArrayList<>();
                        List<LocalDate> unavailableDates = new ArrayList<>();

                        LocalDate currentDate = startDate;
                        while (!currentDate.isAfter(endDate)) {
                                boolean isAvailable = spacesService.isSpaceAvailable(spaceId, currentDate, currentDate);
                                if (isAvailable) {
                                        availableDates.add(currentDate);
                                } else {
                                        unavailableDates.add(currentDate);
                                }
                                currentDate = currentDate.plusDays(1);
                        }

                        StringBuilder result = new StringBuilder();
                        result.append("📅 **")
                                        .append(translate("matrix.mcp.space.availability.title", locale,
                                                        space.getName()))
                                        .append("**\n\n");
                        result.append(translate("matrix.mcp.space.availability.period", locale, startDate.toString(),
                                        endDate.toString()))
                                        .append("\n\n");

                        if (availableDates.isEmpty() && unavailableDates.isEmpty()) {
                                result.append(translate("matrix.mcp.space.availability.noInfo", locale)).append("\n");
                        } else {
                                result.append("✅ **")
                                                .append(translate("matrix.mcp.space.availability.available", locale,
                                                                availableDates.size()))
                                                .append(":**\n");
                                if (!availableDates.isEmpty()) {
                                        for (LocalDate date : availableDates) {
                                                result.append("   - ")
                                                                .append(date.format(DateTimeFormatter
                                                                                .ofPattern("dd/MM/yyyy")))
                                                                .append("\n");
                                        }
                                } else {
                                        result.append("   ")
                                                        .append(translate("matrix.mcp.space.availability.noneAvailable",
                                                                        locale))
                                                        .append("\n");
                                }

                                result.append("\n❌ **")
                                                .append(translate("matrix.mcp.space.availability.unavailable", locale,
                                                                unavailableDates.size()))
                                                .append(":**\n");
                                if (!unavailableDates.isEmpty()) {
                                        for (LocalDate date : unavailableDates) {
                                                result.append("   - ")
                                                                .append(date.format(DateTimeFormatter
                                                                                .ofPattern("dd/MM/yyyy")))
                                                                .append("\n");
                                        }
                                } else {
                                        result.append("   ").append(
                                                        translate("matrix.mcp.space.availability.allAvailable", locale))
                                                        .append("\n");
                                }
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error checking space availability: {}", e.getMessage(), e);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.space.availability.checkError",
                                                                        locale, e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        private LocalDate parseDateOrPeriod(String dateOrPeriod) {
                // Try to parse as ISO date first
                try {
                        return LocalDate.parse(dateOrPeriod);
                } catch (Exception e) {
                        // Try to parse period names
                        String lower = dateOrPeriod.toLowerCase();
                        LocalDate now = LocalDate.now();

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
}

