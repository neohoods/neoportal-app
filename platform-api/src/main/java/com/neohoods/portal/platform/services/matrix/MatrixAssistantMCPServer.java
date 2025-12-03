package com.neohoods.portal.platform.services.matrix;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.entities.ContactNumberEntity;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UnitMemberRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.repositories.InfoRepository;
import com.neohoods.portal.platform.entities.InfoEntity;
import com.neohoods.portal.platform.services.UnitsService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.SpacesService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serveur MCP (Model Context Protocol) pour l'assistant Alfred Matrix.
 * Implémente les outils MCP permettant au LLM d'interagir avec le système.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.mcp.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantMCPServer {

        private final ReservationsService reservationsService;
        private final SpacesService spacesService;
        private final UnitsService unitsService;
        private final ReservationRepository reservationRepository;
        private final SpaceRepository spaceRepository;
        private final UnitRepository unitRepository;
        private final UnitMemberRepository unitMemberRepository;
        private final UsersRepository usersRepository;
        private final InfoRepository infoRepository;
        private final com.neohoods.portal.platform.spaces.services.StripeService stripeService;

        @Value("${neohoods.portal.matrix.assistant.mcp.enabled:false}")
        private boolean mcpEnabled;

        /**
         * Liste tous les outils MCP disponibles
         */
        public List<MCPTool> listTools() {
                List<MCPTool> tools = new ArrayList<>();

                tools.add(MCPTool.builder()
                                .name("get_resident_info")
                                .description(
                                                "Get resident information for an apartment or floor. " +
                                                "The building has 3 buildings: A, B, and C. " +
                                                "Apartment numbers follow the format: [Building][Floor][Number] (e.g., A701 = Building A, 7th floor, apartment 01; C302 = Building C, 3rd floor, apartment 02). " +
                                                "When searching by floor, specify the building letter and floor number (e.g., '6' for 6th floor of building C = C6XX apartments). " +
                                                "Returns who lives in a specific apartment or on a specific floor of a building.")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                                "apartment",
                                                                Map.of("type", "string", "description",
                                                                                "Full apartment number in format [Building][Floor][Number] (e.g., A701, B302, C601). " +
                                                                                "Building can be A, B, or C. Floor is 1-9. Number is 01-99."),
                                                                "floor",
                                                                Map.of("type", "string", "description",
                                                                                "Floor number (e.g., '6' for 6th floor). " +
                                                                                "When used with building context (e.g., '6ème étage du bâtiment C'), " +
                                                                                "searches all apartments on that floor of that building (e.g., C601, C602, etc.). " +
                                                                                "If building is not specified, searches all buildings.")),
                                                "required", List.of()))
                                .build());

                tools.add(MCPTool.builder()
                                .name("get_emergency_numbers")
                                .description("Get emergency contact numbers (ACAF, etc.)")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of()))
                                .build());

                tools.add(MCPTool.builder()
                                .name("get_reservation_details")
                                .description("Get details of a reservation by ID. Can explain why a reservation failed.")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                                "reservationId",
                                                                Map.of("type", "string", "description",
                                                                                "Reservation UUID")),
                                                "required", List.of("reservationId")))
                                .build());

                tools.add(MCPTool.builder()
                                .name("create_reservation")
                                .description("Create a new space reservation. Requires authentication (DM only).")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                                "spaceId",
                                                                Map.of("type", "string", "description", "Space UUID"),
                                                                "startDate",
                                                                Map.of("type", "string", "description",
                                                                                "Start date (YYYY-MM-DD)"),
                                                                "endDate",
                                                                Map.of("type", "string", "description",
                                                                                "End date (YYYY-MM-DD)"),
                                                                "startTime",
                                                                Map.of("type", "string", "description",
                                                                                "Start time (HH:mm)"),
                                                                "endTime",
                                                                Map.of("type", "string", "description",
                                                                                "End time (HH:mm)")),
                                                "required",
                                                List.of("spaceId", "startDate", "endDate", "startTime", "endTime")))
                                .build());

                tools.add(MCPTool.builder()
                                .name("create_github_issue")
                                .description("Create a GitHub issue if justified. Requires authentication (DM only).")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                                "title",
                                                                Map.of("type", "string", "description", "Issue title"),
                                                                "body",
                                                                Map.of("type", "string", "description",
                                                                                "Issue description"),
                                                                "labels",
                                                                Map.of("type", "array", "items",
                                                                                Map.of("type", "string"), "description",
                                                                                "Issue labels")),
                                                "required", List.of("title", "body")))
                                .build());

                tools.add(MCPTool.builder()
                                .name("get_space_info")
                                .description("Get detailed information about a space (name, type, description, rules, pricing, etc.)")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                                "spaceId",
                                                                Map.of("type", "string", "description", "Space UUID")),
                                                "required", List.of("spaceId")))
                                .build());

                tools.add(MCPTool.builder()
                                .name("list_spaces")
                                .description("List all available reservable spaces. Use this when user asks about spaces, rooms, or what can be reserved.")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of()))
                                .build());

                tools.add(MCPTool.builder()
                                .name("check_space_availability")
                                .description("Check if a space is available for a given period. Can handle vague periods like 'Christmas' or 'next week'. Returns available and unavailable dates.")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                                "spaceId",
                                                                Map.of("type", "string", "description", "Space UUID"),
                                                                "startDate",
                                                                Map.of("type", "string", "description",
                                                                                "Start date (YYYY-MM-DD) or period name (e.g., 'Christmas', 'next week')"),
                                                                "endDate",
                                                                Map.of("type", "string", "description",
                                                                                "End date (YYYY-MM-DD) or period name")),
                                                "required", List.of("spaceId", "startDate", "endDate")))
                                .build());

                tools.add(MCPTool.builder()
                                .name("list_my_reservations")
                                .description("List current and upcoming reservations for the authenticated user. Requires authentication (DM only).")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                                "status",
                                                                Map.of("type", "string", "description",
                                                                                "Filter by status: 'current', 'upcoming', 'past', 'all' (default: 'all')")),
                                                "required", List.of()))
                                .build());

                tools.add(MCPTool.builder()
                                .name("get_reservation_access_code")
                                .description("Get access code and instructions for a reservation (check-in, check-out, sheets, etc.). Requires authentication (DM only).")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                                "reservationId",
                                                                Map.of("type", "string", "description",
                                                                                "Reservation UUID")),
                                                "required", List.of("reservationId")))
                                .build());

                tools.add(MCPTool.builder()
                                .name("generate_payment_link")
                                .description("Generate a Stripe payment link for a reservation. Requires authentication (DM only). Use this after creating a reservation to get the payment URL.")
                                .inputSchema(Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                                "reservationId",
                                                                Map.of("type", "string", "description",
                                                                                "Reservation UUID")),
                                                "required", List.of("reservationId")))
                                .build());

                return tools;
        }

        /**
         * Appelle un outil MCP avec validation d'autorisation
         * 
         * @Transactional pour maintenir la session Hibernate ouverte lors de l'accès
         *                aux relations lazy
         *                readOnly = false car createReservation modifie les données
         */
        @Transactional
        public MCPToolResult callTool(String toolName, Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                // Récupérer trace ID et span ID depuis MDC pour les logs
                String traceId = MDC.get("traceId");
                String spanId = MDC.get("spanId");
                log.info("Calling MCP tool: {} with arguments: {} for user: {} [traceId={}, spanId={}]", 
                                toolName, arguments, authContext.getMatrixUserId(), 
                                traceId != null ? traceId : "N/A", spanId != null ? spanId : "N/A");

                // Valider l'autorisation pour les outils sensibles
                if (requiresAuth(toolName) && !authContext.isAuthenticated()) {
                        throw new MatrixAssistantAuthContext.UnauthorizedException(
                                        "Tool " + toolName + " requires authentication (DM only)");
                }

                try {
                        MCPToolResult result = switch (toolName) {
                                case "get_resident_info" -> getResidentInfo(arguments);
                                case "get_emergency_numbers" -> getEmergencyNumbers();
                                case "get_reservation_details" -> getReservationDetails(arguments, authContext);
                                case "create_reservation" -> createReservation(arguments, authContext);
                                case "create_github_issue" -> createGithubIssue(arguments, authContext);
                                case "get_space_info" -> getSpaceInfo(arguments);
                                case "list_spaces" -> listSpaces();
                                case "check_space_availability" -> checkSpaceAvailability(arguments);
                                case "list_my_reservations" -> listMyReservations(arguments, authContext);
                                case "get_reservation_access_code" -> getReservationAccessCode(arguments, authContext);
                                case "generate_payment_link" -> generatePaymentLink(arguments, authContext);
                                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
                        };

                        // Logger la réponse MCP avec trace ID et span ID (réutiliser les variables déjà déclarées)
                        if (result.isError()) {
                                log.warn("MCP tool {} returned an error: {} [traceId={}, spanId={}]", toolName,
                                                result.getContent().isEmpty() ? "Unknown error"
                                                                : result.getContent().get(0).getText(),
                                                traceId != null ? traceId : "N/A", spanId != null ? spanId : "N/A");
                        } else {
                                String resultText = result.getContent().stream()
                                                .map(MCPContent::getText)
                                                .filter(text -> text != null)
                                                .collect(Collectors.joining("\n"));
                                log.info("MCP tool {} succeeded. Response (first 500 chars): {} [traceId={}, spanId={}]", toolName,
                                                resultText.length() > 500 ? resultText.substring(0, 500) + "..."
                                                                : resultText,
                                                traceId != null ? traceId : "N/A", spanId != null ? spanId : "N/A");
                        }

                        return result;
                } catch (Exception e) {
                        // Réutiliser les variables traceId et spanId déjà déclarées au début de la méthode
                        log.error("Error calling MCP tool {}: {} [traceId={}, spanId={}]", toolName, e.getMessage(), 
                                        traceId != null ? traceId : "N/A", spanId != null ? spanId : "N/A", e);
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("Error: " + e.getMessage())
                                                        .build()))
                                        .build();
                }
        }

        private boolean requiresAuth(String toolName) {
                return "create_reservation".equals(toolName) ||
                                "create_github_issue".equals(toolName) ||
                                "list_my_reservations".equals(toolName) ||
                                "get_reservation_access_code".equals(toolName) ||
                                "generate_payment_link".equals(toolName);
        }

        private MCPToolResult getResidentInfo(Map<String, Object> arguments) {
                String apartment = (String) arguments.get("apartment");
                String floor = (String) arguments.get("floor");

                List<String> results = new ArrayList<>();

                // Cas spécial : si on a à la fois floor et apartment, et que apartment est juste une lettre (A, B, C)
                // alors on cherche tous les appartements de ce bâtiment à cet étage (ex: C + 6 = C6XX)
                if (floor != null && !floor.isEmpty() && apartment != null && !apartment.isEmpty()) {
                        String building = apartment.trim().toUpperCase();
                        if (building.length() == 1 && building.matches("[ABC]")) {
                                // C'est un bâtiment, chercher tous les appartements de ce bâtiment à cet étage
                                String floorPattern = building + floor;
                                List<UnitEntity> units = unitRepository.findAll();
                                List<UnitEntity> floorUnits = units.stream()
                                                .filter(u -> {
                                                        if (u.getName() == null || u.getName().isEmpty()) {
                                                                return false;
                                                        }
                                                        String name = u.getName().toUpperCase();
                                                        // Chercher les appartements qui commencent par [Building][Floor]
                                                        // Ex: C6 pour C601, C602, etc.
                                                        return name.startsWith(floorPattern) && name.length() > floorPattern.length();
                                                })
                                                .collect(Collectors.toList());

                                if (floorUnits.isEmpty()) {
                                        results.add("Aucun appartement trouvé au " + floor + "ème étage du bâtiment " + building + ".");
                                } else {
                                        results.add("Résidents du " + floor + "ème étage du bâtiment " + building + " (" + floorUnits.size() + " appartement(s)):");
                                        for (UnitEntity unit : floorUnits) {
                                                List<UnitMemberEntity> members = unitMemberRepository.findByUnitId(unit.getId());
                                                if (!members.isEmpty()) {
                                                        results.add("\nAppartement " + unit.getName() + ":");
                                                        for (UnitMemberEntity member : members) {
                                                                UserEntity user = member.getUser();
                                                                if (user != null) {
                                                                        String firstName = user.getFirstName();
                                                                        String lastName = user.getLastName();
                                                                        String email = user.getEmail();
                                                                        String name = (firstName != null ? firstName : "") +
                                                                                        " " + (lastName != null ? lastName : "");
                                                                        results.add("  - " + name.trim() + (email != null ? " (" + email + ")" : ""));
                                                                }
                                                        }
                                                }
                                        }
                                }

                                return MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MCPContent.builder()
                                                                .type("text")
                                                                .text(String.join("\n", results))
                                                                .build()))
                                                .build();
                        }
                }

                if (apartment != null && !apartment.isEmpty()) {
                        // Recherche par appartement
                        // Normaliser la recherche : accepter "808" ou "Appartement 808"
                        String normalizedApartment = apartment.trim();
                        // Si l'utilisateur a entré juste un numéro, chercher aussi avec "Appartement "
                        // devant
                        String searchPattern1 = normalizedApartment;
                        String searchPattern2 = "Appartement " + normalizedApartment;

                        List<UnitEntity> units = unitRepository.findByNameContainingIgnoreCase(normalizedApartment);
                        Optional<UnitEntity> unitOpt = units.stream()
                                        .filter(u -> {
                                                if (u.getName() == null)
                                                        return false;
                                                String name = u.getName();
                                                // Accepter correspondance exacte ou avec "Appartement " devant
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
                                        results.add("L'appartement " + apartment
                                                        + " n'a pas de résidents enregistrés.");
                                } else {
                                        results.add("Résidents de l'appartement " + apartment + ":");
                                        for (UnitMemberEntity member : members) {
                                                // Accéder aux propriétés de l'utilisateur pendant que la session est
                                                // ouverte
                                                UserEntity user = member.getUser();
                                                // Forcer l'initialisation du proxy Hibernate en accédant à une
                                                // propriété
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
                                results.add("Appartement " + apartment + " non trouvé.");
                        }
                } else if (floor != null && !floor.isEmpty()) {
                        // Recherche par étage
                        // Format attendu: A123, B123, etc. (lettre + numéro d'étage + numéro
                        // d'appartement)
                        // On cherche les appartements qui commencent par une lettre suivie du numéro
                        // d'étage
                        List<UnitEntity> units = unitRepository.findAll();
                        List<UnitEntity> floorUnits = units.stream()
                                        .filter(u -> {
                                                if (u.getName() == null || u.getName().isEmpty()) {
                                                        return false;
                                                }
                                                // Pattern: lettre + floor + chiffres (ex: A123, A1234, B123, etc.)
                                                // On accepte aussi les cas où le nom commence directement par le numéro
                                                // d'étage
                                                String name = u.getName();
                                                // Cas 1: Format standard A123, B123
                                                if (name.matches("^[A-Z]" + floor + "\\d+$")) {
                                                        return true;
                                                }
                                                // Cas 2: Format alternatif où le nom commence par le numéro d'étage
                                                if (name.startsWith(floor) && name.length() > floor.length()) {
                                                        return true;
                                                }
                                                // Cas 3: Le numéro d'étage est contenu dans le nom (plus permissif)
                                                if (name.contains(floor)) {
                                                        return true;
                                                }
                                                return false;
                                        })
                                        .collect(Collectors.toList());

                        if (floorUnits.isEmpty()) {
                                results.add("Aucun appartement trouvé à l'étage " + floor + ".");
                        } else {
                                results.add("Résidents de l'étage " + floor + ":");
                                for (UnitEntity unit : floorUnits) {
                                        List<UnitMemberEntity> members = unitMemberRepository
                                                        .findByUnitId(unit.getId());
                                        for (UnitMemberEntity member : members) {
                                                // Accéder aux propriétés de l'utilisateur pendant que la session est
                                                // ouverte
                                                UserEntity user = member.getUser();
                                                // Forcer l'initialisation du proxy Hibernate en accédant à une
                                                // propriété
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
                        results.add("Veuillez spécifier un appartement ou un étage.");
                }

                return MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MCPContent.builder()
                                                .type("text")
                                                .text(String.join("\n", results))
                                                .build()))
                                .build();
        }

        private MCPToolResult getEmergencyNumbers() {
                // Get emergency contacts from InfoEntity
                InfoEntity info = infoRepository.findByIdWithContactNumbers(
                                UUID.fromString("00000000-0000-0000-0000-000000000001"))
                                .orElse(null);

                List<ContactNumberEntity> emergencyContacts = new ArrayList<>();
                if (info != null && info.getContactNumbers() != null) {
                        emergencyContacts = info.getContactNumbers().stream()
                                        .filter(c -> "emergency".equals(c.getContactType()))
                                        .collect(Collectors.toList());
                }

                List<String> results = new ArrayList<>();
                results.add("Numéros d'urgence:");

                if (emergencyContacts.isEmpty()) {
                        results.add("Aucun numéro d'urgence enregistré.");
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
                                results.add(contactInfo.toString());
                        }
                }

                // Recherche spécifique ACAF
                List<ContactNumberEntity> acafContacts = emergencyContacts.stream()
                                .filter(c -> c.getName() != null && c.getName().toLowerCase().contains("acaf"))
                                .collect(Collectors.toList());

                if (!acafContacts.isEmpty()) {
                        results.add("\nACAF:");
                        for (ContactNumberEntity acaf : acafContacts) {
                                results.add("- " + (acaf.getPhoneNumber() != null ? acaf.getPhoneNumber()
                                                : "Non disponible"));
                        }
                }

                return MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MCPContent.builder()
                                                .type("text")
                                                .text(String.join("\n", results))
                                                .build()))
                                .build();
        }

        private MCPToolResult getReservationDetails(Map<String, Object> arguments, MatrixAssistantAuthContext authContext) {
                String reservationIdStr = (String) arguments.get("reservationId");
                if (reservationIdStr == null || reservationIdStr.isEmpty()) {
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("reservationId is required")
                                                        .build()))
                                        .build();
                }

                try {
                        UUID reservationId = UUID.fromString(reservationIdStr);
                        Optional<ReservationEntity> reservationOpt = reservationRepository.findById(reservationId);

                        if (reservationOpt.isEmpty()) {
                                return MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MCPContent.builder()
                                                                .type("text")
                                                                .text("Réservation " + reservationIdStr
                                                                                + " non trouvée.")
                                                                .build()))
                                                .build();
                        }

                        ReservationEntity reservation = reservationOpt.get();

                        // Vérifier que l'utilisateur a accès à cette réservation
                        if (authContext.getUserEntity().isPresent()) {
                                UserEntity user = authContext.getUserEntity().get();
                                if (!reservation.getUser().getId().equals(user.getId())) {
                                        // Vérifier si l'utilisateur est admin
                                        if (!user.getType().name().equals("ADMIN")) {
                                                return MCPToolResult.builder()
                                                                .isError(true)
                                                                .content(List.of(MCPContent.builder()
                                                                                .type("text")
                                                                                .text("Vous n'avez pas accès à cette réservation.")
                                                                                .build()))
                                                                .build();
                                        }
                                }
                        }

                        StringBuilder details = new StringBuilder();
                        details.append("Détails de la réservation ").append(reservationIdStr).append(":\n");
                        details.append("- Espace: ").append(reservation.getSpace().getName()).append("\n");
                        details.append("- Date: ").append(reservation.getStartDate()).append(" à ")
                                        .append(reservation.getEndDate())
                                        .append("\n");
                        details.append("- Statut: ").append(reservation.getStatus()).append("\n");
                        if (reservation.getStatus().name().contains("FAILED")
                                        || reservation.getStatus().name().contains("CANCELLED")) {
                                details.append("\nRaison possible de l'échec:\n");
                                // Analyser pourquoi la réservation a échoué
                                if (reservation.getStatus().name().contains("CANCELLED")) {
                                        details.append("- La réservation a été annulée.\n");
                                } else {
                                        details.append("- Vérifiez la disponibilité de l'espace pour cette période.\n");
                                        details.append("- Vérifiez que vous avez les permissions nécessaires.\n");
                                }
                        }

                        return MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text(details.toString())
                                                        .build()))
                                        .build();
                } catch (IllegalArgumentException e) {
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("Invalid reservation ID format: " + reservationIdStr)
                                                        .build()))
                                        .build();
                }
        }

        private MCPToolResult createReservation(Map<String, Object> arguments, MatrixAssistantAuthContext authContext) {
                UserEntity user = authContext.getAuthenticatedUser();

                String spaceIdStr = (String) arguments.get("spaceId");
                String startDateStr = (String) arguments.get("startDate");
                String endDateStr = (String) arguments.get("endDate");

                try {
                        UUID spaceId = UUID.fromString(spaceIdStr);
                        LocalDate startDate = LocalDate.parse(startDateStr);
                        LocalDate endDate = LocalDate.parse(endDateStr);

                        // Vérifier la disponibilité
                        if (!spacesService.isSpaceAvailable(spaceId, startDate, endDate)) {
                                return MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MCPContent.builder()
                                                                .type("text")
                                                                .text("L'espace n'est pas disponible pour cette période.")
                                                                .build()))
                                                .build();
                        }

                        // Récupérer l'espace
                        SpaceEntity space = spacesService.getSpaceById(spaceId);

                        // Créer la réservation via ReservationsService
                        ReservationEntity reservation = reservationsService.createReservation(
                                        space,
                                        user,
                                        startDate,
                                        endDate);

                        // Calculer le nombre de nuits
                        long nights = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);

                        // Construire le récapitulatif
                        StringBuilder recap = new StringBuilder();
                        recap.append("✅ **Réservation créée avec succès!**\n\n");
                        recap.append("📋 **Récapitulatif:**\n");
                        recap.append("- Espace: ").append(space.getName()).append("\n");
                        recap.append("- Du ")
                                        .append(startDate.format(
                                                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                                        .append(" au ")
                                        .append(endDate.format(
                                                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                                        .append("\n");
                        recap.append("- Nombre de nuits: ").append(nights).append("\n");
                        recap.append("- ID réservation: ").append(reservation.getId()).append("\n");
                        recap.append("- Statut: ").append(getStatusDescription(reservation.getStatus())).append("\n\n");

                        if (reservation.getTotalPrice() != null) {
                                recap.append("💰 **Tarif total: ").append(reservation.getTotalPrice())
                                                .append("€**\n\n");
                        } else {
                                recap.append("💰 **Tarif**: Le tarif sera calculé lors de la génération du lien de paiement.\n\n");
                        }

                        recap.append("🔗 **Prochaines étapes:**\n");
                        recap.append("Un lien de paiement Stripe sera généré et vous sera envoyé prochainement.\n");
                        recap.append("Une fois le paiement effectué, votre réservation sera confirmée et vous recevrez le code d'accès.\n\n");
                        recap.append("💡 **Astuce:** Utilisez `get_reservation_access_code` avec l'ID de la réservation pour obtenir le code d'accès et les instructions une fois la réservation confirmée.");

                        return MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text(recap.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error creating reservation: {}", e.getMessage(), e);
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("Erreur lors de la création de la réservation: "
                                                                        + e.getMessage())
                                                        .build()))
                                        .build();
                }
        }

        private MCPToolResult createGithubIssue(Map<String, Object> arguments, MatrixAssistantAuthContext authContext) {
                // TODO: Implémenter la création d'issue GitHub
                // Pour l'instant, retourner un message indiquant que c'est en cours
                // d'implémentation
                return MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MCPContent.builder()
                                                .type("text")
                                                .text("La création d'issues GitHub n'est pas encore implémentée.")
                                                .build()))
                                .build();
        }

        private MCPToolResult getSpaceInfo(Map<String, Object> arguments) {
                String spaceIdStr = (String) arguments.get("spaceId");
                if (spaceIdStr == null || spaceIdStr.isEmpty()) {
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("spaceId is required")
                                                        .build()))
                                        .build();
                }

                try {
                        UUID spaceId = UUID.fromString(spaceIdStr);
                        SpaceEntity space = spacesService.getSpaceById(spaceId);

                        StringBuilder info = new StringBuilder();
                        info.append("Informations détaillées sur l'espace:\n\n");
                        info.append("📋 **Informations générales**\n");
                        info.append("- Nom: ").append(space.getName()).append("\n");
                        info.append("- Type: ").append(space.getType()).append("\n");
                        if (space.getDescription() != null) {
                                info.append("- Description: ").append(space.getDescription()).append("\n");
                        }
                        info.append("- Statut: ").append(space.getStatus()).append("\n");
                        info.append("- ID: ").append(space.getId()).append("\n\n");

                        // TODO: Ajouter tarifs, règles, capacité, équipements quand disponibles dans
                        // SpaceEntity
                        // if (space.getPricing() != null) {
                        // info.append("💰 **Tarification**\n");
                        // info.append("- Prix par nuit:
                        // ").append(space.getPricing().getPricePerNight()).append("€\n");
                        // }
                        // if (space.getRules() != null) {
                        // info.append("\n📜 **Règles**\n");
                        // info.append(space.getRules()).append("\n");
                        // }

                        return MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text(info.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("Espace non trouvé: " + spaceIdStr)
                                                        .build()))
                                        .build();
                }
        }

        private MCPToolResult listSpaces() {
                try {
                        List<SpaceEntity> spaces = spaceRepository.findAll();

                        if (spaces.isEmpty()) {
                                return MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MCPContent.builder()
                                                                .type("text")
                                                                .text("Aucun espace réservable disponible.")
                                                                .build()))
                                                .build();
                        }

                        StringBuilder result = new StringBuilder();
                        result.append("📋 **Espaces réservables disponibles** (").append(spaces.size()).append(")\n\n");

                        for (SpaceEntity space : spaces) {
                                result.append("🏠 **").append(space.getName()).append("**\n");
                                result.append("   - Type: ").append(space.getType()).append("\n");
                                result.append("   - ID: ").append(space.getId()).append("\n");
                                if (space.getDescription() != null && !space.getDescription().isEmpty()) {
                                        String desc = space.getDescription();
                                        if (desc.length() > 100) {
                                                desc = desc.substring(0, 100) + "...";
                                        }
                                        result.append("   - Description: ").append(desc).append("\n");
                                }
                                result.append("   - Statut: ").append(space.getStatus()).append("\n\n");
                        }

                        result.append("💡 Pour obtenir plus de détails sur un espace, utilisez get_space_info avec l'ID de l'espace.");

                        return MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error listing spaces: {}", e.getMessage(), e);
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("Erreur lors de la récupération des espaces: "
                                                                        + e.getMessage())
                                                        .build()))
                                        .build();
                }
        }

        private MCPToolResult checkSpaceAvailability(Map<String, Object> arguments) {
                String spaceIdStr = (String) arguments.get("spaceId");
                String startDateStr = (String) arguments.get("startDate");
                String endDateStr = (String) arguments.get("endDate");

                if (spaceIdStr == null || startDateStr == null || endDateStr == null) {
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("spaceId, startDate, and endDate are required")
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
                                return MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MCPContent.builder()
                                                                .type("text")
                                                                .text("Impossible de parser les dates. Format attendu: YYYY-MM-DD ou période (ex: 'Christmas', 'next week')")
                                                                .build()))
                                                .build();
                        }

                        if (startDate.isAfter(endDate)) {
                                return MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MCPContent.builder()
                                                                .type("text")
                                                                .text("La date de début doit être avant la date de fin")
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
                        result.append("📅 **Disponibilité de l'espace ").append(space.getName()).append("**\n\n");
                        result.append("Période: ").append(startDate).append(" au ").append(endDate).append("\n\n");

                        if (availableDates.isEmpty() && unavailableDates.isEmpty()) {
                                result.append("Aucune information de disponibilité pour cette période.\n");
                        } else {
                                result.append("✅ **Dates disponibles** (").append(availableDates.size()).append("):\n");
                                if (!availableDates.isEmpty()) {
                                        for (LocalDate date : availableDates) {
                                                result.append("   - ")
                                                                .append(date.format(java.time.format.DateTimeFormatter
                                                                                .ofPattern("dd/MM/yyyy")))
                                                                .append("\n");
                                        }
                                } else {
                                        result.append("   Aucune date disponible sur cette période.\n");
                                }

                                result.append("\n❌ **Dates non disponibles** (").append(unavailableDates.size())
                                                .append("):\n");
                                if (!unavailableDates.isEmpty()) {
                                        for (LocalDate date : unavailableDates) {
                                                result.append("   - ")
                                                                .append(date.format(java.time.format.DateTimeFormatter
                                                                                .ofPattern("dd/MM/yyyy")))
                                                                .append("\n");
                                        }
                                } else {
                                        result.append("   Toutes les dates sont disponibles !\n");
                                }
                        }

                        return MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error checking space availability: {}", e.getMessage(), e);
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("Erreur lors de la vérification de disponibilité: "
                                                                        + e.getMessage())
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

        private MCPToolResult listMyReservations(Map<String, Object> arguments, MatrixAssistantAuthContext authContext) {
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
                                return MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MCPContent.builder()
                                                                .type("text")
                                                                .text("Vous n'avez aucune réservation " +
                                                                                ("all".equals(statusFilter) ? ""
                                                                                                : statusFilter)
                                                                                + ".")
                                                                .build()))
                                                .build();
                        }

                        StringBuilder result = new StringBuilder();
                        result.append("📋 **Vos réservations** (").append(filtered.size()).append(")\n\n");

                        for (ReservationEntity reservation : filtered) {
                                SpaceEntity space = reservation.getSpace();
                                result.append("🏠 **").append(space != null ? space.getName() : "Espace inconnu")
                                                .append("**\n");
                                result.append("   - ID réservation: ").append(reservation.getId()).append("\n");
                                result.append("   - Du ").append(reservation.getStartDate()).append(" au ")
                                                .append(reservation.getEndDate()).append("\n");
                                result.append("   - Statut: ").append(reservation.getStatus()).append("\n");
                                if (reservation.getStatus() != null) {
                                        result.append("   - ").append(getStatusDescription(reservation.getStatus()))
                                                        .append("\n");
                                }
                                result.append("\n");
                        }

                        return MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error listing user reservations: {}", e.getMessage(), e);
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("Erreur lors de la récupération des réservations: "
                                                                        + e.getMessage())
                                                        .build()))
                                        .build();
                }
        }

        private MCPToolResult getReservationAccessCode(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                String reservationIdStr = (String) arguments.get("reservationId");
                if (reservationIdStr == null || reservationIdStr.isEmpty()) {
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("reservationId is required")
                                                        .build()))
                                        .build();
                }

                try {
                        UUID reservationId = UUID.fromString(reservationIdStr);
                        ReservationEntity reservation = reservationRepository.findById(reservationId)
                                        .orElseThrow(() -> new IllegalArgumentException("Réservation non trouvée"));

                        // Verify the reservation belongs to the authenticated user
                        UserEntity user = authContext.getAuthenticatedUser();
                        if (!reservation.getUser().getId().equals(user.getId())) {
                                return MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MCPContent.builder()
                                                                .type("text")
                                                                .text("Vous n'avez pas accès à cette réservation")
                                                                .build()))
                                                .build();
                        }

                        StringBuilder result = new StringBuilder();
                        result.append("🔑 **Code d'accès et instructions**\n\n");
                        result.append("Réservation: ").append(reservation.getId()).append("\n");
                        result.append("Espace: ").append(reservation.getSpace().getName()).append("\n");
                        result.append("Du ").append(reservation.getStartDate()).append(" au ")
                                        .append(reservation.getEndDate()).append("\n\n");

                        // TODO: Get actual access code from reservation when available
                        // if (reservation.getAccessCode() != null) {
                        // result.append("🔐 **Code d'accès**:
                        // ").append(reservation.getAccessCode()).append("\n\n");
                        // } else {
                        result.append("🔐 **Code d'accès**: À générer (fonctionnalité en cours de développement)\n\n");
                        // }

                        result.append("📋 **Instructions**\n");
                        result.append("**Check-in:**\n");
                        result.append("- Arrivée à partir de 15h00\n");
                        result.append("- Utilisez le code d'accès pour déverrouiller la porte\n\n");

                        result.append("**Check-out:**\n");
                        result.append("- Départ avant 11h00\n");
                        result.append("- Remettez les clés dans la boîte prévue à cet effet\n\n");

                        result.append("**Draps et linge:**\n");
                        result.append("- Les draps sont fournis et changés avant votre arrivée\n");
                        result.append("- Le linge de toilette est disponible dans la salle de bain\n\n");

                        result.append("**En cas de problème:**\n");
                        result.append("- Contactez le support via le chat\n");
                        result.append("- Numéro d'urgence: consultez get_emergency_numbers\n");

                        return MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting reservation access code: {}", e.getMessage(), e);
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("Erreur lors de la récupération du code d'accès: "
                                                                        + e.getMessage())
                                                        .build()))
                                        .build();
                }
        }

        private MCPToolResult generatePaymentLink(Map<String, Object> arguments, MatrixAssistantAuthContext authContext) {
                String reservationIdStr = (String) arguments.get("reservationId");
                if (reservationIdStr == null || reservationIdStr.isEmpty()) {
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("reservationId is required")
                                                        .build()))
                                        .build();
                }

                try {
                        UUID reservationId = UUID.fromString(reservationIdStr);
                        ReservationEntity reservation = reservationRepository.findById(reservationId)
                                        .orElseThrow(() -> new IllegalArgumentException("Réservation non trouvée"));

                        // Verify the reservation belongs to the authenticated user
                        UserEntity user = authContext.getAuthenticatedUser();
                        if (!reservation.getUser().getId().equals(user.getId())) {
                                return MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MCPContent.builder()
                                                                .type("text")
                                                                .text("Vous n'avez pas accès à cette réservation")
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
                                        return MCPToolResult.builder()
                                                        .isError(true)
                                                        .content(List.of(MCPContent.builder()
                                                                        .type("text")
                                                                        .text("Erreur lors de la création de l'intention de paiement: "
                                                                                        + e.getMessage())
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
                                return MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MCPContent.builder()
                                                                .type("text")
                                                                .text("Erreur lors de la création du lien de paiement: "
                                                                                + e.getMessage())
                                                                .build()))
                                                .build();
                        }

                        StringBuilder result = new StringBuilder();
                        result.append("💳 **Lien de paiement généré avec succès!**\n\n");
                        result.append("📋 **Réservation:** ").append(reservation.getId()).append("\n");
                        result.append("🏠 **Espace:** ").append(reservation.getSpace().getName()).append("\n");
                        if (reservation.getTotalPrice() != null) {
                                result.append("💰 **Montant:** ").append(reservation.getTotalPrice()).append("€\n\n");
                        }
                        result.append("🔗 **Lien de paiement:** ").append(checkoutUrl).append("\n\n");
                        result.append("⏰ **Important:** Ce lien est valide pendant 15 minutes.\n");
                        result.append("Une fois le paiement effectué, votre réservation sera automatiquement confirmée.\n\n");
                        result.append("💡 **Après le paiement:** Vous recevrez le code d'accès et les instructions via le chat.");

                        return MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text(result.toString())
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error generating payment link: {}", e.getMessage(), e);
                        return MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MCPContent.builder()
                                                        .type("text")
                                                        .text("Erreur lors de la génération du lien de paiement: "
                                                                        + e.getMessage())
                                                        .build()))
                                        .build();
                }
        }

        private String getStatusDescription(ReservationStatusForEntity status) {
                return switch (status) {
                        case PENDING_PAYMENT -> "⏳ En attente de paiement";
                        case PAYMENT_FAILED -> "❌ Paiement échoué";
                        case EXPIRED -> "⏰ Expirée";
                        case CONFIRMED -> "✅ Confirmée";
                        case ACTIVE -> "🟢 Active";
                        case COMPLETED -> "✅ Terminée";
                        case CANCELLED -> "❌ Annulée";
                        case REFUNDED -> "💰 Remboursée";
                };
        }

        /**
         * Modèles pour les outils MCP
         */
        @lombok.Data
        @lombok.Builder
        public static class MCPTool {
                private String name;
                private String description;
                private Map<String, Object> inputSchema;
        }

        @lombok.Data
        @lombok.Builder
        public static class MCPToolResult {
                private boolean isError;
                private List<MCPContent> content;
        }

        @lombok.Data
        @lombok.Builder
        public static class MCPContent {
                private String type; // "text" or "image"
                private String text;
                private String data; // base64 for images
                private String mimeType; // for images
        }
}
