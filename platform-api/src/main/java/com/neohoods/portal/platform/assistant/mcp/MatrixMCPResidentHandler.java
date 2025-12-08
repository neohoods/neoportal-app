package com.neohoods.portal.platform.assistant.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.entities.ContactNumberEntity;
import com.neohoods.portal.platform.entities.InfoEntity;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.InfoRepository;
import com.neohoods.portal.platform.repositories.UnitMemberRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;

/**
 * Handler for resident-related MCP tools
 */
@Component
public class MatrixMCPResidentHandler extends MatrixMCPBaseHandler {

        private final UnitRepository unitRepository;
        private final UnitMemberRepository unitMemberRepository;
        private final InfoRepository infoRepository;

        public MatrixMCPResidentHandler(
                        MessageSource messageSource,
                        UsersRepository usersRepository,
                        @Autowired(required = false) MatrixAssistantAdminCommandService adminCommandService,
                        UnitRepository unitRepository,
                        UnitMemberRepository unitMemberRepository,
                        InfoRepository infoRepository) {
                super(messageSource, usersRepository, adminCommandService);
                this.unitRepository = unitRepository;
                this.unitMemberRepository = unitMemberRepository;
                this.infoRepository = infoRepository;
        }

        public MatrixMCPModels.MCPToolResult getResidentInfo(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                String apartment = (String) arguments.get("apartment");
                String floor = (String) arguments.get("floor");
                Locale locale = getLocaleFromAuthContext(authContext);

                List<String> results = new ArrayList<>();

                // Special case: if both floor and apartment are provided, and apartment is just
                // a letter (A, B, C)
                // then search all apartments of that building on that floor (e.g., C + 6 =
                // C6XX)
                if (floor != null && !floor.isEmpty() && apartment != null && !apartment.isEmpty()) {
                        String building = apartment.trim().toUpperCase();
                        if (building.length() == 1 && building.matches("[ABC]")) {
                                String floorPattern = building + floor;
                                List<UnitEntity> units = unitRepository.findAll();
                                List<UnitEntity> floorUnits = units.stream()
                                                .filter(u -> {
                                                        if (u.getName() == null || u.getName().isEmpty()) {
                                                                return false;
                                                        }
                                                        String name = u.getName().toUpperCase();
                                                        return name.startsWith(floorPattern)
                                                                        && name.length() > floorPattern.length();
                                                })
                                                .collect(Collectors.toList());

                                if (floorUnits.isEmpty()) {
                                        results.add(translate("matrix.mcp.residentInfo.noFloorFound", locale, floor,
                                                        building));
                                } else {
                                        results.add(translate("matrix.mcp.residentInfo.residentsOfFloor", locale, floor,
                                                        building, floorUnits.size()));
                                        for (UnitEntity unit : floorUnits) {
                                                List<UnitMemberEntity> members = unitMemberRepository
                                                                .findByUnitId(unit.getId());
                                                if (!members.isEmpty()) {
                                                        results.add("\n" + translate(
                                                                        "matrix.mcp.residentInfo.apartment", locale,
                                                                        unit.getName()) + ":");
                                                        for (UnitMemberEntity member : members) {
                                                                UserEntity user = member.getUser();
                                                                if (user != null) {
                                                                        String firstName = user.getFirstName();
                                                                        String lastName = user.getLastName();
                                                                        String email = user.getEmail();
                                                                        String name = (firstName != null ? firstName
                                                                                        : "") +
                                                                                        " "
                                                                                        + (lastName != null ? lastName
                                                                                                        : "");
                                                                        results.add("  - " + name.trim()
                                                                                        + (email != null ? " (" + email
                                                                                                        + ")" : ""));
                                                                }
                                                        }
                                                }
                                        }
                                }

                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(String.join("\n", results))
                                                                .build()))
                                                .build();
                        }
                }

                if (apartment != null && !apartment.isEmpty()) {
                        String normalizedApartment = apartment.trim();
                        String searchPattern1 = normalizedApartment;
                        String searchPattern2 = "Appartement " + normalizedApartment;

                        List<UnitEntity> units = unitRepository.findByNameContainingIgnoreCase(normalizedApartment);
                        Optional<UnitEntity> unitOpt = units.stream()
                                        .filter(u -> {
                                                if (u.getName() == null)
                                                        return false;
                                                String name = u.getName();
                                                return name.equalsIgnoreCase(searchPattern1) ||
                                                                name.equalsIgnoreCase(searchPattern2) ||
                                                                name.equalsIgnoreCase(normalizedApartment) ||
                                                                (normalizedApartment.matches("\\d+")
                                                                                && name.contains(normalizedApartment));
                                        })
                                        .findFirst();
                        if (unitOpt.isPresent()) {
                                UnitEntity unit = unitOpt.get();
                                List<UnitMemberEntity> members = unitMemberRepository.findByUnitId(unit.getId());
                                if (members.isEmpty()) {
                                        results.add(translate("matrix.mcp.residentInfo.noResidents", locale,
                                                        apartment));
                                } else {
                                        results.add(translate("matrix.mcp.residentInfo.residentsOf", locale,
                                                        apartment));
                                        for (UnitMemberEntity member : members) {
                                                UserEntity user = member.getUser();
                                                if (user != null) {
                                                        String firstName = user.getFirstName();
                                                        String lastName = user.getLastName();
                                                        String email = user.getEmail();
                                                        String name = (firstName != null ? firstName : "") +
                                                                        " " + (lastName != null ? lastName : "");
                                                        results.add("- " + name.trim() + " ("
                                                                        + (email != null ? email : "") + ")");
                                                }
                                        }
                                }
                        } else {
                                results.add(translate("matrix.mcp.residentInfo.noApartmentFound", locale, apartment));
                        }
                } else if (floor != null && !floor.isEmpty()) {
                        List<UnitEntity> units = unitRepository.findAll();
                        List<UnitEntity> floorUnits = units.stream()
                                        .filter(u -> {
                                                if (u.getName() == null || u.getName().isEmpty()) {
                                                        return false;
                                                }
                                                String name = u.getName();
                                                if (name.matches("^[A-Z]" + floor + "\\d+$")) {
                                                        return true;
                                                }
                                                if (name.startsWith(floor) && name.length() > floor.length()) {
                                                        return true;
                                                }
                                                if (name.contains(floor)) {
                                                        return true;
                                                }
                                                return false;
                                        })
                                        .collect(Collectors.toList());

                        if (floorUnits.isEmpty()) {
                                results.add(translate("matrix.mcp.residentInfo.noFloorFoundSimple", locale, floor));
                        } else {
                                results.add(translate("matrix.mcp.residentInfo.residentsOfFloorSimple", locale, floor));
                                for (UnitEntity unit : floorUnits) {
                                        List<UnitMemberEntity> members = unitMemberRepository
                                                        .findByUnitId(unit.getId());
                                        for (UnitMemberEntity member : members) {
                                                UserEntity user = member.getUser();
                                                if (user != null) {
                                                        String firstName = user.getFirstName();
                                                        String lastName = user.getLastName();
                                                        String email = user.getEmail();
                                                        String name = (firstName != null ? firstName : "") +
                                                                        " " + (lastName != null ? lastName : "");
                                                        results.add("- " + unit.getName() + ": " + name.trim() + " ("
                                                                        + (email != null ? email : "") + ")");
                                                }
                                        }
                                }
                        }
                } else {
                        results.add(translate("matrix.mcp.residentInfo.specifyApartmentOrFloor", locale));
                }

                return MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text(String.join("\n", results))
                                                .build()))
                                .build();
        }

        public MatrixMCPModels.MCPToolResult getEmergencyNumbers(MatrixAssistantAuthContext authContext) {
                Locale locale = getLocaleFromAuthContext(authContext);
                InfoEntity info = infoRepository.findByIdWithContactNumbers(
                                UUID.fromString("00000000-0000-0000-0000-000000000001"))
                                .orElse(null);

                // Get all relevant contacts: emergency, maintenance, and syndic
                List<ContactNumberEntity> emergencyContacts = new ArrayList<>();
                List<ContactNumberEntity> maintenanceContacts = new ArrayList<>();
                List<ContactNumberEntity> syndicContacts = new ArrayList<>();

                if (info != null && info.getContactNumbers() != null) {
                        emergencyContacts = info.getContactNumbers().stream()
                                        .filter(c -> "emergency".equals(c.getContactType()))
                                        .collect(Collectors.toList());
                        maintenanceContacts = info.getContactNumbers().stream()
                                        .filter(c -> "maintenance".equals(c.getContactType()))
                                        .collect(Collectors.toList());
                        syndicContacts = info.getContactNumbers().stream()
                                        .filter(c -> "syndic".equals(c.getContactType()))
                                        .collect(Collectors.toList());
                }

                List<String> results = new ArrayList<>();
                results.add(translate("matrix.mcp.emergencyNumbers.title", locale));

                if (emergencyContacts.isEmpty()) {
                        results.add(translate("matrix.mcp.emergencyNumbers.none", locale));
                } else {
                        for (ContactNumberEntity contact : emergencyContacts) {
                                StringBuilder contactInfo = new StringBuilder();
                                contactInfo.append("- ").append(
                                                contact.getName() != null ? contact.getName() : contact.getType());
                                if (contact.getPhoneNumber() != null) {
                                        contactInfo.append(": ").append(contact.getPhoneNumber());
                                }
                                if (contact.getDescription() != null) {
                                        contactInfo.append(" (").append(contact.getDescription()).append(")");
                                }
                                // Add responsibility prominently - this is critical for matching problems to
                                // contacts
                                if (contact.getResponsibility() != null && !contact.getResponsibility().isEmpty()) {
                                        contactInfo.append(" - ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.responsibility",
                                                                        locale))
                                                        .append(": ").append(contact.getResponsibility());
                                }
                                // Add metadata if available
                                if (contact.getMetadata() != null && !contact.getMetadata().isEmpty()) {
                                        contactInfo.append(" - ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.metadata",
                                                                        locale))
                                                        .append(": ").append(contact.getMetadata());
                                }
                                // Add QR code URL if available
                                if (contact.getQrCodeUrl() != null && !contact.getQrCodeUrl().isEmpty()) {
                                        contactInfo.append(" - ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.qrCode",
                                                                        locale))
                                                        .append(": ").append(contact.getQrCodeUrl());
                                }
                                if (contact.getAddress() != null && !contact.getAddress().isEmpty()) {
                                        contactInfo.append(" - ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.address",
                                                                        locale))
                                                        .append(": ").append(contact.getAddress());
                                }
                                if (contact.getEmail() != null && !contact.getEmail().isEmpty()) {
                                        contactInfo.append(" - ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.email", locale))
                                                        .append(": ").append(contact.getEmail());
                                }
                                results.add(contactInfo.toString());
                        }
                }

                // Specific ACAF search
                List<ContactNumberEntity> acafContacts = emergencyContacts.stream()
                                .filter(c -> c.getName() != null && c.getName().toLowerCase().contains("acaf"))
                                .collect(Collectors.toList());

                if (!acafContacts.isEmpty()) {
                        results.add("\nACAF:");
                        for (ContactNumberEntity acaf : acafContacts) {
                                StringBuilder acafInfo = new StringBuilder();
                                if (acaf.getPhoneNumber() != null) {
                                        acafInfo.append("- ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.phone", locale))
                                                        .append(": ").append(acaf.getPhoneNumber());
                                } else {
                                        acafInfo.append("- ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.phone", locale))
                                                        .append(": ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.notAvailable",
                                                                        locale));
                                }
                                if (acaf.getAddress() != null && !acaf.getAddress().isEmpty()) {
                                        acafInfo.append("\n  ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.address",
                                                                        locale))
                                                        .append(": ").append(acaf.getAddress());
                                }
                                if (acaf.getEmail() != null && !acaf.getEmail().isEmpty()) {
                                        acafInfo.append("\n  ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.email", locale))
                                                        .append(": ").append(acaf.getEmail());
                                }
                                if (acaf.getResponsibility() != null && !acaf.getResponsibility().isEmpty()) {
                                        acafInfo.append("\n  ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.responsibility",
                                                                        locale))
                                                        .append(": ").append(acaf.getResponsibility());
                                }
                                if (acaf.getMetadata() != null && !acaf.getMetadata().isEmpty()) {
                                        acafInfo.append("\n  ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.metadata",
                                                                        locale))
                                                        .append(": ").append(acaf.getMetadata());
                                }
                                if (acaf.getQrCodeUrl() != null && !acaf.getQrCodeUrl().isEmpty()) {
                                        acafInfo.append("\n  ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.qrCode",
                                                                        locale))
                                                        .append(": ").append(acaf.getQrCodeUrl());
                                }
                                results.add(acafInfo.toString());
                        }
                }

                // Add maintenance contacts
                if (!maintenanceContacts.isEmpty()) {
                        results.add("\n" + translate("matrix.mcp.emergencyNumbers.maintenance", locale) + ":");
                        for (ContactNumberEntity contact : maintenanceContacts) {
                                StringBuilder contactInfo = new StringBuilder();
                                contactInfo.append("- ").append(
                                                contact.getName() != null ? contact.getName() : contact.getType());
                                if (contact.getPhoneNumber() != null) {
                                        contactInfo.append(": ").append(contact.getPhoneNumber());
                                }
                                if (contact.getDescription() != null) {
                                        contactInfo.append(" (").append(contact.getDescription()).append(")");
                                }
                                if (contact.getResponsibility() != null && !contact.getResponsibility().isEmpty()) {
                                        contactInfo.append(" - ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.responsibility",
                                                                        locale))
                                                        .append(": ").append(contact.getResponsibility());
                                }
                                if (contact.getEmail() != null && !contact.getEmail().isEmpty()) {
                                        contactInfo.append(" - ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.email", locale))
                                                        .append(": ").append(contact.getEmail());
                                }
                                if (contact.getAddress() != null && !contact.getAddress().isEmpty()) {
                                        contactInfo.append(" - ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.address",
                                                                        locale))
                                                        .append(": ").append(contact.getAddress());
                                }
                                results.add(contactInfo.toString());
                        }
                }

                // Add syndic contacts
                if (!syndicContacts.isEmpty()) {
                        results.add("\n" + translate("matrix.mcp.emergencyNumbers.syndic", locale) + ":");
                        for (ContactNumberEntity contact : syndicContacts) {
                                StringBuilder contactInfo = new StringBuilder();
                                contactInfo.append("- ").append(
                                                contact.getName() != null ? contact.getName() : contact.getType());
                                if (contact.getPhoneNumber() != null) {
                                        contactInfo.append(": ").append(contact.getPhoneNumber());
                                }
                                if (contact.getDescription() != null) {
                                        contactInfo.append(" (").append(contact.getDescription()).append(")");
                                }
                                if (contact.getEmail() != null && !contact.getEmail().isEmpty()) {
                                        contactInfo.append(" - ")
                                                        .append(translate("matrix.mcp.emergencyNumbers.email", locale))
                                                        .append(": ").append(contact.getEmail());
                                }
                                results.add(contactInfo.toString());
                        }
                }

                return MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text(String.join("\n", results))
                                                .build()))
                                .build();
        }
}
