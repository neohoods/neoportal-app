package com.neohoods.portal.platform.services.matrix.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UnitMemberRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler for admin-related MCP tools
 */
@Component
@Slf4j
public class MatrixMCPAdminHandler extends MatrixMCPBaseHandler {

        private final UsersRepository usersRepository;
        private final UnitRepository unitRepository;
        private final UnitMemberRepository unitMemberRepository;
        private final ReservationRepository reservationRepository;
        private final SpaceRepository spaceRepository;
        private final MatrixMCPReservationHandler reservationHandler;

        public MatrixMCPAdminHandler(
                        MessageSource messageSource,
                        UsersRepository usersRepository,
                        MatrixAssistantAdminCommandService adminCommandService,
                        UnitRepository unitRepository,
                        UnitMemberRepository unitMemberRepository,
                        ReservationRepository reservationRepository,
                        SpaceRepository spaceRepository,
                        MatrixMCPReservationHandler reservationHandler) {
                super(messageSource, usersRepository, adminCommandService);
                this.usersRepository = usersRepository;
                this.unitRepository = unitRepository;
                this.unitMemberRepository = unitMemberRepository;
                this.reservationRepository = reservationRepository;
                this.spaceRepository = spaceRepository;
                this.reservationHandler = reservationHandler;
        }

        public MatrixMCPModels.MCPToolResult adminGetUsers(MatrixAssistantAuthContext authContext) {
                try {
                        List<UserEntity> users = new ArrayList<>();
                        usersRepository.findAll().forEach(users::add);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        if (users == null || users.isEmpty()) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.admin.users.none", locale))
                                                                .build()))
                                                .build();
                        }

                        List<String> results = new ArrayList<>();
                        results.add(translate("matrix.mcp.admin.users.title", locale));
                        for (UserEntity user : users) {
                                StringBuilder userInfo = new StringBuilder("\n👤 ");
                                userInfo.append(translate("matrix.mcp.admin.users.id", locale)).append(": ")
                                                .append(user.getId());
                                if (user.getUsername() != null) {
                                        userInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.users.username", locale))
                                                        .append(": ").append(user.getUsername());
                                }
                                if (user.getFirstName() != null) {
                                        userInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.users.firstName", locale))
                                                        .append(": ").append(user.getFirstName());
                                }
                                if (user.getLastName() != null) {
                                        userInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.users.lastName", locale))
                                                        .append(": ").append(user.getLastName());
                                }
                                if (user.getEmail() != null) {
                                        userInfo.append(" - ").append(translate("matrix.mcp.admin.users.email", locale))
                                                        .append(": ").append(user.getEmail());
                                }
                                if (user.getType() != null) {
                                        userInfo.append(" - ").append(translate("matrix.mcp.admin.users.type", locale))
                                                        .append(": ").append(user.getType());
                                }
                                userInfo.append(" - ").append(translate("matrix.mcp.admin.users.disabled", locale))
                                                .append(": ").append(user.isDisabled());
                                results.add(userInfo.toString());
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(String.join("\n", results))
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting users (admin): {}", e.getMessage(), e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.admin.users.error", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult adminGetUnits(MatrixAssistantAuthContext authContext) {
                try {
                        List<UnitEntity> units = unitRepository.findAll();
                        Locale locale = getLocaleFromAuthContext(authContext);
                        if (units == null || units.isEmpty()) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.admin.units.none", locale))
                                                                .build()))
                                                .build();
                        }

                        List<String> results = new ArrayList<>();
                        results.add(translate("matrix.mcp.admin.units.title", locale));
                        for (UnitEntity unit : units) {
                                StringBuilder unitInfo = new StringBuilder("\n🏠 ");
                                unitInfo.append(translate("matrix.mcp.admin.units.id", locale)).append(": ")
                                                .append(unit.getId());
                                if (unit.getName() != null) {
                                        unitInfo.append(" - ").append(translate("matrix.mcp.admin.units.name", locale))
                                                        .append(": ").append(unit.getName());
                                }
                                List<UnitMemberEntity> members = unitMemberRepository.findByUnitId(unit.getId());
                                unitInfo.append(" - ").append(translate("matrix.mcp.admin.units.members", locale))
                                                .append(": ").append(members.size());
                                results.add(unitInfo.toString());
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(String.join("\n", results))
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting units (admin): {}", e.getMessage(), e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.admin.units.error", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult adminGetReservations(MatrixAssistantAuthContext authContext) {
                try {
                        List<ReservationEntity> reservations = reservationRepository.findAll();
                        Locale locale = getLocaleFromAuthContext(authContext);
                        if (reservations == null || reservations.isEmpty()) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.admin.reservations.none",
                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        List<String> results = new ArrayList<>();
                        results.add(translate("matrix.mcp.admin.reservations.title", locale));
                        for (ReservationEntity reservation : reservations) {
                                StringBuilder reservationInfo = new StringBuilder("\n📅 ");
                                reservationInfo.append(translate("matrix.mcp.admin.reservations.id", locale))
                                                .append(": ").append(reservation.getId());
                                if (reservation.getSpace() != null) {
                                        reservationInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.reservations.space",
                                                                        locale))
                                                        .append(": ").append(reservation.getSpace().getName());
                                }
                                if (reservation.getUser() != null) {
                                        reservationInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.reservations.user", locale))
                                                        .append(": ")
                                                        .append(reservation.getUser().getFirstName())
                                                        .append(" ").append(reservation.getUser().getLastName());
                                }
                                if (reservation.getStartDate() != null) {
                                        reservationInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.reservations.start",
                                                                        locale))
                                                        .append(": ").append(reservation.getStartDate());
                                }
                                if (reservation.getEndDate() != null) {
                                        reservationInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.reservations.end", locale))
                                                        .append(": ").append(reservation.getEndDate());
                                }
                                if (reservation.getStatus() != null) {
                                        reservationInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.reservations.status",
                                                                        locale))
                                                        .append(": ").append(reservation.getStatus());
                                }
                                results.add(reservationInfo.toString());
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(String.join("\n", results))
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting reservations (admin): {}", e.getMessage(), e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.admin.reservations.error", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult adminGetSpaces(MatrixAssistantAuthContext authContext) {
                try {
                        List<SpaceEntity> spaces = spaceRepository.findAll();
                        Locale locale = getLocaleFromAuthContext(authContext);
                        if (spaces == null || spaces.isEmpty()) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.admin.spaces.none", locale))
                                                                .build()))
                                                .build();
                        }

                        List<String> results = new ArrayList<>();
                        results.add(translate("matrix.mcp.admin.spaces.title", locale));
                        for (SpaceEntity space : spaces) {
                                StringBuilder spaceInfo = new StringBuilder("\n🏢 ");
                                spaceInfo.append(translate("matrix.mcp.admin.spaces.id", locale)).append(": ")
                                                .append(space.getId());
                                if (space.getName() != null) {
                                        spaceInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.spaces.name", locale))
                                                        .append(": ").append(space.getName());
                                }
                                if (space.getType() != null) {
                                        spaceInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.spaces.type", locale))
                                                        .append(": ").append(space.getType());
                                }
                                if (space.getDescription() != null) {
                                        spaceInfo.append(" - ")
                                                        .append(translate("matrix.mcp.admin.spaces.description",
                                                                        locale))
                                                        .append(": ").append(space.getDescription());
                                }
                                results.add(spaceInfo.toString());
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(String.join("\n", results))
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting spaces (admin): {}", e.getMessage(), e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.admin.spaces.error", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }
}

