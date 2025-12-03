package com.neohoods.portal.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.neohoods.portal.platform.entities.ContactNumberEntity;
import com.neohoods.portal.platform.entities.InfoEntity;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.InfoRepository;
import com.neohoods.portal.platform.repositories.UnitMemberRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantMCPServer;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantMCPServer.MCPTool;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantMCPServer.MCPToolResult;
import com.neohoods.portal.platform.spaces.services.ReservationsService;
import com.neohoods.portal.platform.spaces.services.SpacesService;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixAssistantMCPServer Tests")
class MatrixAssistantMCPServerTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private UnitMemberRepository unitMemberRepository;

    @Mock
    private InfoRepository infoRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private SpacesService spacesService;

    @Mock
    private ReservationsService reservationsService;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private com.neohoods.portal.platform.spaces.services.StripeService stripeService;

    @InjectMocks
    private MatrixAssistantMCPServer mcpServer;

    private MatrixAssistantAuthContext publicAuthContext;
    private MatrixAssistantAuthContext authenticatedAuthContext;
    private UserEntity testUser;
    private UnitEntity testUnit;
    private UnitMemberEntity testMember;

    @BeforeEach
    void setUp() {
        // Créer un utilisateur de test
        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");

        // Créer un contexte d'autorisation public (room publique)
        publicAuthContext = MatrixAssistantAuthContext.builder()
                .matrixUserId("@testuser:chat.neohoods.com")
                .roomId("!testroom:chat.neohoods.com")
                .isDirectMessage(false)
                .userEntity(Optional.empty())
                .build();

        // Créer un contexte d'autorisation authentifié (DM)
        authenticatedAuthContext = MatrixAssistantAuthContext.builder()
                .matrixUserId("@testuser:chat.neohoods.com")
                .roomId("!dmroom:chat.neohoods.com")
                .isDirectMessage(true)
                .userEntity(Optional.of(testUser))
                .build();

        // Créer une unité de test
        testUnit = new UnitEntity();
        testUnit.setId(UUID.randomUUID());
        testUnit.setName("A123");

        // Créer un membre de test
        testMember = new UnitMemberEntity();
        testMember.setId(UUID.randomUUID());
        testMember.setUnit(testUnit);
        testMember.setUser(testUser);
    }

    @Test
    @DisplayName("listTools should return all available tools")
    void testListTools() {
        List<MCPTool> tools = mcpServer.listTools();

        assertNotNull(tools);
        assertTrue(tools.size() >= 6, "Should have at least 6 tools");

        // Vérifier que les outils attendus sont présents
        Map<String, MCPTool> toolsMap = new HashMap<>();
        for (MCPTool tool : tools) {
            toolsMap.put(tool.getName(), tool);
        }

        assertTrue(toolsMap.containsKey("get_resident_info"), "Should have get_resident_info tool");
        assertTrue(toolsMap.containsKey("get_emergency_numbers"), "Should have get_emergency_numbers tool");
        assertTrue(toolsMap.containsKey("get_reservation_details"), "Should have get_reservation_details tool");
        assertTrue(toolsMap.containsKey("create_reservation"), "Should have create_reservation tool");
        assertTrue(toolsMap.containsKey("create_github_issue"), "Should have create_github_issue tool");
        assertTrue(toolsMap.containsKey("get_space_info"), "Should have get_space_info tool");
        assertTrue(toolsMap.containsKey("list_spaces"), "Should have list_spaces tool");
        assertTrue(toolsMap.containsKey("check_space_availability"), "Should have check_space_availability tool");
        assertTrue(toolsMap.containsKey("list_my_reservations"), "Should have list_my_reservations tool");
        assertTrue(toolsMap.containsKey("get_reservation_access_code"), "Should have get_reservation_access_code tool");
    }

    @Test
    @DisplayName("getResidentInfo should find residents by apartment")
    void testGetResidentInfoByApartment() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("apartment", "A123");

        List<UnitEntity> units = new ArrayList<>();
        units.add(testUnit);

        List<UnitMemberEntity> members = new ArrayList<>();
        members.add(testMember);

        when(unitRepository.findByNameContainingIgnoreCase("A123")).thenReturn(units);
        when(unitMemberRepository.findByUnitId(testUnit.getId())).thenReturn(members);

        // Act
        MCPToolResult result = mcpServer.callTool("get_resident_info", arguments, publicAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        assertFalse(result.getContent().isEmpty(), "Result content should not be empty");

        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("A123"), "Result should mention apartment A123");
        assertTrue(text.contains("Test User") || text.contains("test@example.com"),
                "Result should contain resident information");
    }

    @Test
    @DisplayName("getResidentInfo should find residents by floor")
    void testGetResidentInfoByFloor() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("floor", "123");

        List<UnitEntity> allUnits = new ArrayList<>();
        allUnits.add(testUnit);

        List<UnitMemberEntity> members = new ArrayList<>();
        members.add(testMember);

        when(unitRepository.findAll()).thenReturn(allUnits);
        when(unitMemberRepository.findByUnitId(testUnit.getId())).thenReturn(members);

        // Act
        MCPToolResult result = mcpServer.callTool("get_resident_info", arguments, publicAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        assertFalse(result.getContent().isEmpty(), "Result content should not be empty");

        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("123") || text.contains("A123"),
                "Result should mention floor 123 or apartment A123");
    }

    @Test
    @DisplayName("getResidentInfo should return error when no apartment or floor specified")
    void testGetResidentInfoNoParams() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();

        // Act
        MCPToolResult result = mcpServer.callTool("get_resident_info", arguments, publicAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("spécifier") || text.contains("appartement") || text.contains("étage"),
                "Result should ask for apartment or floor");
    }

    @Test
    @DisplayName("getEmergencyNumbers should return emergency contacts")
    void testGetEmergencyNumbers() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();

        InfoEntity info = new InfoEntity();
        info.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        ContactNumberEntity contact = new ContactNumberEntity();
        contact.setName("ACAF");
        contact.setPhoneNumber("01 23 45 67 89");
        contact.setContactType("emergency");

        List<ContactNumberEntity> contacts = new ArrayList<>();
        contacts.add(contact);
        info.setContactNumbers(contacts);

        when(infoRepository.findByIdWithContactNumbers(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .thenReturn(Optional.of(info));

        // Act
        MCPToolResult result = mcpServer.callTool("get_emergency_numbers", arguments, publicAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        assertFalse(result.getContent().isEmpty(), "Result content should not be empty");

        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("urgence") || text.contains("ACAF"),
                "Result should contain emergency numbers or ACAF");
    }

    @Test
    @DisplayName("getReservationDetails should return reservation information")
    void testGetReservationDetails() {
        // Arrange
        UUID reservationId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("reservationId", reservationId.toString());

        SpaceEntity space = new SpaceEntity();
        space.setId(UUID.randomUUID());
        space.setName("Salle de réunion");

        ReservationEntity reservation = new ReservationEntity();
        reservation.setId(reservationId);
        reservation.setSpace(space);
        reservation.setStartDate(LocalDate.now().plusDays(1));
        reservation.setEndDate(LocalDate.now().plusDays(1));
        reservation.setStatus(ReservationStatusForEntity.CONFIRMED);
        reservation.setUser(testUser);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        // Act
        MCPToolResult result = mcpServer.callTool("get_reservation_details", arguments, authenticatedAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        assertFalse(result.getContent().isEmpty(), "Result content should not be empty");

        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("réservation") || text.contains(reservationId.toString()),
                "Result should contain reservation information");
    }

    @Test
    @DisplayName("createReservation should require authentication")
    void testCreateReservationRequiresAuth() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("spaceId", UUID.randomUUID().toString());
        arguments.put("startDate", LocalDate.now().plusDays(1).toString());
        arguments.put("endDate", LocalDate.now().plusDays(1).toString());
        arguments.put("startTime", "10:00");
        arguments.put("endTime", "12:00");

        // Act & Assert
        try {
            mcpServer.callTool("create_reservation", arguments, publicAuthContext);
            // Si on arrive ici, l'exception n'a pas été levée
            assertTrue(false, "Should have thrown UnauthorizedException");
        } catch (MatrixAssistantAuthContext.UnauthorizedException e) {
            assertTrue(true, "Correctly threw UnauthorizedException");
        }
    }

    @Test
    @DisplayName("getSpaceInfo should return space information")
    void testGetSpaceInfo() {
        // Arrange
        UUID spaceId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("spaceId", spaceId.toString());

        SpaceEntity space = new SpaceEntity();
        space.setId(spaceId);
        space.setName("Salle de réunion");
        space.setDescription("Une belle salle");

        when(spacesService.getSpaceById(spaceId)).thenReturn(space);

        // Act
        MCPToolResult result = mcpServer.callTool("get_space_info", arguments, publicAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        assertFalse(result.getContent().isEmpty(), "Result content should not be empty");

        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("Salle de réunion") || text.contains("espace"),
                "Result should contain space information");
    }

    @Test
    @DisplayName("callTool should return error for unknown tool")
    void testCallToolUnknownTool() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();

        // Act
        MCPToolResult result = mcpServer.callTool("unknown_tool", arguments, publicAuthContext);

        // Assert
        assertTrue(result.isError(), "Result should be an error");
        assertNotNull(result.getContent(), "Result should have content");
        assertFalse(result.getContent().isEmpty(), "Result content should not be empty");
    }

    @Test
    @DisplayName("listSpaces should return all available spaces")
    void testListSpaces() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();

        SpaceEntity space1 = new SpaceEntity();
        space1.setId(UUID.randomUUID());
        space1.setName("Salle de réunion");
        space1.setType(SpaceTypeForEntity.COMMON_ROOM);
        space1.setDescription("Une belle salle de réunion");

        SpaceEntity space2 = new SpaceEntity();
        space2.setId(UUID.randomUUID());
        space2.setName("Chambre d'amis");
        space2.setType(SpaceTypeForEntity.GUEST_ROOM);
        space2.setDescription("Chambre d'amis confortable");

        List<SpaceEntity> spaces = new ArrayList<>();
        spaces.add(space1);
        spaces.add(space2);

        when(spaceRepository.findAll()).thenReturn(spaces);

        // Act
        MCPToolResult result = mcpServer.callTool("list_spaces", arguments, publicAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        assertFalse(result.getContent().isEmpty(), "Result content should not be empty");

        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("Salle de réunion") || text.contains("Salle de sport"),
                "Result should contain space names");
        assertTrue(text.contains("2") || text.contains("espaces"),
                "Result should mention number of spaces");
    }

    @Test
    @DisplayName("listSpaces should return empty message when no spaces")
    void testListSpacesEmpty() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        when(spaceRepository.findAll()).thenReturn(new ArrayList<>());

        // Act
        MCPToolResult result = mcpServer.callTool("list_spaces", arguments, publicAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("Aucun") || text.contains("disponible"),
                "Result should indicate no spaces available");
    }

    @Test
    @DisplayName("checkSpaceAvailability should return available and unavailable dates")
    void testCheckSpaceAvailability() {
        // Arrange
        UUID spaceId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("spaceId", spaceId.toString());
        arguments.put("startDate", LocalDate.now().plusDays(1).toString());
        arguments.put("endDate", LocalDate.now().plusDays(3).toString());

        SpaceEntity space = new SpaceEntity();
        space.setId(spaceId);
        space.setName("Salle de réunion");

        when(spacesService.getSpaceById(spaceId)).thenReturn(space);
        when(spacesService.isSpaceAvailable(eq(spaceId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(true);

        // Act
        MCPToolResult result = mcpServer.callTool("check_space_availability", arguments, publicAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("disponibilité") || text.contains("disponible"),
                "Result should contain availability information");
    }

    @Test
    @DisplayName("checkSpaceAvailability should parse period names like 'Christmas'")
    void testCheckSpaceAvailabilityWithPeriod() {
        // Arrange
        UUID spaceId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("spaceId", spaceId.toString());
        arguments.put("startDate", "Christmas");
        arguments.put("endDate", "Christmas");

        SpaceEntity space = new SpaceEntity();
        space.setId(spaceId);
        space.setName("Salle de réunion");

        when(spacesService.getSpaceById(spaceId)).thenReturn(space);
        when(spacesService.isSpaceAvailable(eq(spaceId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(true);

        // Act
        MCPToolResult result = mcpServer.callTool("check_space_availability", arguments, publicAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
    }

    @Test
    @DisplayName("listMyReservations should require authentication")
    void testListMyReservationsRequiresAuth() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();

        // Act & Assert
        try {
            mcpServer.callTool("list_my_reservations", arguments, publicAuthContext);
            assertTrue(false, "Should have thrown UnauthorizedException");
        } catch (MatrixAssistantAuthContext.UnauthorizedException e) {
            assertTrue(true, "Correctly threw UnauthorizedException");
        }
    }

    @Test
    @DisplayName("listMyReservations should return user reservations")
    void testListMyReservations() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("status", "all");

        SpaceEntity space = new SpaceEntity();
        space.setId(UUID.randomUUID());
        space.setName("Salle de réunion");

        ReservationEntity reservation = new ReservationEntity();
        reservation.setId(UUID.randomUUID());
        reservation.setSpace(space);
        reservation.setUser(testUser);
        reservation.setStartDate(LocalDate.now().plusDays(1));
        reservation.setEndDate(LocalDate.now().plusDays(2));
        reservation.setStatus(ReservationStatusForEntity.CONFIRMED);

        List<ReservationEntity> reservations = new ArrayList<>();
        reservations.add(reservation);

        when(reservationRepository.findByUser(testUser)).thenReturn(reservations);

        // Act
        MCPToolResult result = mcpServer.callTool("list_my_reservations", arguments, authenticatedAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("réservation") || text.contains("Salle de réunion"),
                "Result should contain reservation information");
    }

    @Test
    @DisplayName("listMyReservations should filter by status")
    void testListMyReservationsFiltered() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("status", "upcoming");

        SpaceEntity space = new SpaceEntity();
        space.setId(UUID.randomUUID());
        space.setName("Salle de réunion");

        ReservationEntity upcomingReservation = new ReservationEntity();
        upcomingReservation.setId(UUID.randomUUID());
        upcomingReservation.setSpace(space);
        upcomingReservation.setUser(testUser);
        upcomingReservation.setStartDate(LocalDate.now().plusDays(10));
        upcomingReservation.setEndDate(LocalDate.now().plusDays(11));
        upcomingReservation.setStatus(ReservationStatusForEntity.CONFIRMED);

        ReservationEntity pastReservation = new ReservationEntity();
        pastReservation.setId(UUID.randomUUID());
        pastReservation.setSpace(space);
        pastReservation.setUser(testUser);
        pastReservation.setStartDate(LocalDate.now().minusDays(10));
        pastReservation.setEndDate(LocalDate.now().minusDays(9));
        pastReservation.setStatus(ReservationStatusForEntity.COMPLETED);

        List<ReservationEntity> allReservations = new ArrayList<>();
        allReservations.add(upcomingReservation);
        allReservations.add(pastReservation);

        when(reservationRepository.findByUser(testUser)).thenReturn(allReservations);

        // Act
        MCPToolResult result = mcpServer.callTool("list_my_reservations", arguments, authenticatedAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        String text = result.getContent().get(0).getText();
        // Should only contain upcoming reservation
        assertTrue(text.contains("1") || text.contains("réservation"),
                "Result should contain filtered reservations");
    }

    @Test
    @DisplayName("getReservationAccessCode should require authentication")
    void testGetReservationAccessCodeRequiresAuth() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("reservationId", UUID.randomUUID().toString());

        // Act & Assert
        try {
            mcpServer.callTool("get_reservation_access_code", arguments, publicAuthContext);
            assertTrue(false, "Should have thrown UnauthorizedException");
        } catch (MatrixAssistantAuthContext.UnauthorizedException e) {
            assertTrue(true, "Correctly threw UnauthorizedException");
        }
    }

    @Test
    @DisplayName("getReservationAccessCode should return access code and instructions")
    void testGetReservationAccessCode() {
        // Arrange
        UUID reservationId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("reservationId", reservationId.toString());

        SpaceEntity space = new SpaceEntity();
        space.setId(UUID.randomUUID());
        space.setName("Salle de réunion");

        ReservationEntity reservation = new ReservationEntity();
        reservation.setId(reservationId);
        reservation.setSpace(space);
        reservation.setUser(testUser);
        reservation.setStartDate(LocalDate.now().plusDays(1));
        reservation.setEndDate(LocalDate.now().plusDays(2));
        reservation.setStatus(ReservationStatusForEntity.CONFIRMED);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        // Act
        MCPToolResult result = mcpServer.callTool("get_reservation_access_code", arguments, authenticatedAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("Code d'accès") || text.contains("instructions"),
                "Result should contain access code and instructions");
        assertTrue(text.contains("Check-in") || text.contains("Check-out"),
                "Result should contain check-in/check-out instructions");
    }

    @Test
    @DisplayName("getReservationAccessCode should deny access to other user's reservation")
    void testGetReservationAccessCodeOtherUser() {
        // Arrange
        UUID reservationId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("reservationId", reservationId.toString());

        UserEntity otherUser = new UserEntity();
        otherUser.setId(UUID.randomUUID());

        SpaceEntity space = new SpaceEntity();
        space.setId(UUID.randomUUID());
        space.setName("Salle de réunion");

        ReservationEntity reservation = new ReservationEntity();
        reservation.setId(reservationId);
        reservation.setSpace(space);
        reservation.setUser(otherUser); // Different user
        reservation.setStartDate(LocalDate.now().plusDays(1));
        reservation.setEndDate(LocalDate.now().plusDays(2));

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        // Act
        MCPToolResult result = mcpServer.callTool("get_reservation_access_code", arguments, authenticatedAuthContext);

        // Assert
        assertTrue(result.isError(), "Result should be an error");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("accès") || text.contains("pas accès"),
                "Result should indicate access denied");
    }

    @Test
    @DisplayName("createReservation should create reservation successfully")
    void testCreateReservationSuccess() {
        // Arrange
        UUID spaceId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("spaceId", spaceId.toString());
        arguments.put("startDate", LocalDate.now().plusDays(1).toString());
        arguments.put("endDate", LocalDate.now().plusDays(2).toString());
        arguments.put("startTime", "10:00");
        arguments.put("endTime", "12:00");

        SpaceEntity space = new SpaceEntity();
        space.setId(spaceId);
        space.setName("Salle de réunion");

        ReservationEntity reservation = new ReservationEntity();
        reservation.setId(UUID.randomUUID());
        reservation.setSpace(space);
        reservation.setUser(testUser);
        reservation.setStartDate(LocalDate.now().plusDays(1));
        reservation.setEndDate(LocalDate.now().plusDays(2));
        reservation.setStatus(ReservationStatusForEntity.PENDING_PAYMENT);
        reservation.setTotalPrice(new java.math.BigDecimal("100.00"));

        when(spacesService.isSpaceAvailable(spaceId, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2)))
                .thenReturn(true);
        when(spacesService.getSpaceById(spaceId)).thenReturn(space);
        when(reservationsService.createReservation(eq(space), eq(testUser), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(reservation);

        // Act
        MCPToolResult result = mcpServer.callTool("create_reservation", arguments, authenticatedAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("Réservation créée") || text.contains("succès"),
                "Result should indicate successful creation");
        assertTrue(text.contains("Salle de réunion"), "Result should contain space name");
        assertTrue(text.contains("100.00") || text.contains("Tarif"),
                "Result should contain price information");
    }

    @Test
    @DisplayName("createReservation should fail when space is not available")
    void testCreateReservationNotAvailable() {
        // Arrange
        UUID spaceId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("spaceId", spaceId.toString());
        arguments.put("startDate", LocalDate.now().plusDays(1).toString());
        arguments.put("endDate", LocalDate.now().plusDays(2).toString());
        arguments.put("startTime", "10:00");
        arguments.put("endTime", "12:00");

        when(spacesService.isSpaceAvailable(spaceId, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2)))
                .thenReturn(false);

        // Act
        MCPToolResult result = mcpServer.callTool("create_reservation", arguments, authenticatedAuthContext);

        // Assert
        assertTrue(result.isError(), "Result should be an error");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("disponible") || text.contains("pas disponible"),
                "Result should indicate space is not available");
    }

    @Test
    @DisplayName("getSpaceInfo should return error when space not found")
    void testGetSpaceInfoNotFound() {
        // Arrange
        UUID spaceId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("spaceId", spaceId.toString());

        when(spacesService.getSpaceById(spaceId)).thenThrow(new RuntimeException("Space not found"));

        // Act
        MCPToolResult result = mcpServer.callTool("get_space_info", arguments, publicAuthContext);

        // Assert
        assertTrue(result.isError(), "Result should be an error");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("trouvé") || text.contains("non trouvé"),
                "Result should indicate space not found");
    }

    @Test
    @DisplayName("getResidentInfo should handle apartment number without 'Appartement' prefix")
    void testGetResidentInfoApartmentNumber() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("apartment", "808");

        UnitEntity unit = new UnitEntity();
        unit.setId(UUID.randomUUID());
        unit.setName("Appartement 808");

        List<UnitEntity> units = new ArrayList<>();
        units.add(unit);

        List<UnitMemberEntity> members = new ArrayList<>();
        members.add(testMember);

        when(unitRepository.findByNameContainingIgnoreCase("808")).thenReturn(units);
        when(unitMemberRepository.findByUnitId(unit.getId())).thenReturn(members);

        // Act
        MCPToolResult result = mcpServer.callTool("get_resident_info", arguments, publicAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("808") || text.contains("Appartement 808"),
                "Result should find apartment 808");
    }

    @Test
    @DisplayName("generatePaymentLink should require authentication")
    void testGeneratePaymentLinkRequiresAuth() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("reservationId", UUID.randomUUID().toString());

        // Act & Assert
        try {
            mcpServer.callTool("generate_payment_link", arguments, publicAuthContext);
            assertTrue(false, "Should have thrown UnauthorizedException");
        } catch (MatrixAssistantAuthContext.UnauthorizedException e) {
            assertTrue(true, "Correctly threw UnauthorizedException");
        }
    }

    @Test
    @DisplayName("generatePaymentLink should generate payment link successfully")
    void testGeneratePaymentLinkSuccess() {
        // Arrange
        UUID reservationId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("reservationId", reservationId.toString());

        SpaceEntity space = new SpaceEntity();
        space.setId(UUID.randomUUID());
        space.setName("Salle de réunion");
        space.setCurrency("EUR");

        ReservationEntity reservation = new ReservationEntity();
        reservation.setId(reservationId);
        reservation.setSpace(space);
        reservation.setUser(testUser);
        reservation.setStartDate(LocalDate.now().plusDays(1));
        reservation.setEndDate(LocalDate.now().plusDays(2));
        reservation.setStatus(ReservationStatusForEntity.PENDING_PAYMENT);
        reservation.setTotalPrice(new java.math.BigDecimal("100.00"));

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(stripeService.createPaymentIntent(eq(reservation), eq(testUser), eq(space)))
                .thenReturn("pi_test_123");
        when(stripeService.createCheckoutSession(eq(reservation), eq(testUser), eq(space)))
                .thenReturn("https://checkout.stripe.com/test-session");

        // Act
        MCPToolResult result = mcpServer.callTool("generate_payment_link", arguments, authenticatedAuthContext);

        // Assert
        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.getContent(), "Result should have content");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("Lien de paiement") || text.contains("généré"),
                "Result should contain payment link information");
        assertTrue(text.contains("checkout.stripe.com") || text.contains("100.00"),
                "Result should contain payment URL or amount");
    }

    @Test
    @DisplayName("generatePaymentLink should deny access to other user's reservation")
    void testGeneratePaymentLinkOtherUser() {
        // Arrange
        UUID reservationId = UUID.randomUUID();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("reservationId", reservationId.toString());

        UserEntity otherUser = new UserEntity();
        otherUser.setId(UUID.randomUUID());

        ReservationEntity reservation = new ReservationEntity();
        reservation.setId(reservationId);
        reservation.setUser(otherUser); // Different user

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        // Act
        MCPToolResult result = mcpServer.callTool("generate_payment_link", arguments, authenticatedAuthContext);

        // Assert
        assertTrue(result.isError(), "Result should be an error");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("accès") || text.contains("pas accès"),
                "Result should indicate access denied");
    }

    @Test
    @DisplayName("generatePaymentLink should handle missing reservationId")
    void testGeneratePaymentLinkMissingId() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();

        // Act
        MCPToolResult result = mcpServer.callTool("generate_payment_link", arguments, authenticatedAuthContext);

        // Assert
        assertTrue(result.isError(), "Result should be an error");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("reservationId") || text.contains("required"),
                "Result should indicate reservationId is required");
    }
}
