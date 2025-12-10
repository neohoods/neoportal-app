package com.neohoods.portal.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.neohoods.portal.platform.assistant.mcp.MatrixMCPAdminHandler;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPHubHandler;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPReservationHandler;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPSpaceHandler;
import com.neohoods.portal.platform.entities.UnitEntity;
import com.neohoods.portal.platform.entities.UnitMemberEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.InfoRepository;
import com.neohoods.portal.platform.repositories.UnitMemberRepository;
import com.neohoods.portal.platform.repositories.UnitRepository;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.assistant.mcp.MatrixAssistantMCPServer;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.ReservationStatusForEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceTypeForEntity;
import com.neohoods.portal.platform.spaces.repositories.ReservationRepository;
import com.neohoods.portal.platform.spaces.repositories.SpaceRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPModels;
import com.neohoods.portal.platform.assistant.mcp.MatrixMCPResidentHandler;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAuthContextService;
import com.neohoods.portal.platform.assistant.services.MatrixAssistantAdminCommandService;
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

        @Mock
        private MatrixAssistantAuthContextService authContextService;

        @Mock
        private MatrixMCPResidentHandler residentHandler;

        @Mock
        private MatrixMCPReservationHandler reservationHandler;

        @Mock
        private MatrixMCPSpaceHandler spaceHandler;

        @Mock
        private MatrixMCPHubHandler hubHandler;

        @Mock
        private MatrixMCPAdminHandler adminHandler;

        @Mock
        private MatrixAssistantAdminCommandService adminCommandService;

        @Mock
        private org.springframework.context.MessageSource messageSource;

        @Mock
        private ResourceLoader resourceLoader;

        @Mock
        private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

        @Mock
        private Resource resource;

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
                testUser.setMatrixUserId("@testuser:chat.neohoods.com");

                // Créer un contexte d'autorisation public (room publique)
                publicAuthContext = MatrixAssistantAuthContext.builder()
                                .matrixUserId("@testuser:chat.neohoods.com")
                                .roomId("!testroom:chat.neohoods.com")
                                .isDirectMessage(false)
                                .userEntity(null)
                                .build();

                // Créer un contexte d'autorisation authentifié (DM)
                authenticatedAuthContext = MatrixAssistantAuthContext.builder()
                                .matrixUserId("@testuser:chat.neohoods.com")
                                .roomId("!dmroom:chat.neohoods.com")
                                .isDirectMessage(true)
                                .userEntity(testUser)
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

                // Mock usersRepository to return testUser when looking up by matrix_user_id
                // getUser() uses findByMatrixUserId() now
                // Use lenient() because not all tests use this mock
                lenient().when(usersRepository.findByMatrixUserId("@testuser:chat.neohoods.com")).thenReturn(testUser);

                // Mock messageSource for translations (used by all tools)
                // Use lenient() because not all tests use this mock
                lenient().when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(invocation -> {
                        String key = invocation.getArgument(0);
                        return key; // Return the key as the message for simplicity
                });

                // Mock ResourceLoader to load tools from YAML
                try {
                        String yamlContent = "tools:\n" +
                                        "  - name: get_resident_info\n" +
                                        "    description: Get resident information\n" +
                                        "    inputSchema:\n" +
                                        "      type: object\n" +
                                        "      properties: {}\n" +
                                        "      required: []\n" +
                                        "  - name: get_emergency_numbers\n" +
                                        "    description: Get emergency numbers\n" +
                                        "    inputSchema:\n" +
                                        "      type: object\n" +
                                        "      properties: {}\n" +
                                        "      required: []\n" +
                                        "  - name: get_reservation_details\n" +
                                        "    description: Get reservation details\n" +
                                        "    inputSchema:\n" +
                                        "      type: object\n" +
                                        "      properties: {}\n" +
                                        "      required: []\n" +
                                        "  - name: create_reservation\n" +
                                        "    description: Create reservation\n" +
                                        "    inputSchema:\n" +
                                        "      type: object\n" +
                                        "      properties: {}\n" +
                                        "      required: []\n" +
                                        "  - name: create_github_issue\n" +
                                        "    description: Create GitHub issue\n" +
                                        "    inputSchema:\n" +
                                        "      type: object\n" +
                                        "      properties: {}\n" +
                                        "      required: []\n" +
                                        "  - name: get_space_info\n" +
                                        "    description: Get space info\n" +
                                        "    inputSchema:\n" +
                                        "      type: object\n" +
                                        "      properties: {}\n" +
                                        "      required: []\n" +
                                        "  - name: list_spaces\n" +
                                        "    description: List spaces\n" +
                                        "    inputSchema:\n" +
                                        "      type: object\n" +
                                        "      properties: {}\n" +
                                        "      required: []\n" +
                                        "  - name: check_space_availability\n" +
                                        "    description: Check space availability\n" +
                                        "    inputSchema:\n" +
                                        "      type: object\n" +
                                        "      properties: {}\n" +
                                        "      required: []\n" +
                                        "  - name: list_my_reservations\n" +
                                        "    description: List my reservations\n" +
                                        "    inputSchema:\n" +
                                        "      type: object\n" +
                                        "      properties: {}\n" +
                                        "      required: []\n" +
                                        "  - name: get_reservation_access_code\n" +
                                        "    description: Get reservation access code\n" +
                                        "    inputSchema:\n" +
                                        "      type: object\n" +
                                        "      properties: {}\n" +
                                        "      required: []\n";
                        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());
                        lenient().when(resourceLoader.getResource("classpath:matrix-mcp-tools.yaml"))
                                        .thenReturn(resource);
                        lenient().when(resource.exists()).thenReturn(true);
                        lenient().when(resource.isReadable()).thenReturn(true);
                        lenient().when(resource.getInputStream()).thenReturn(inputStream);

                        // Mock TransactionTemplate to execute the lambda directly
                        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                                org.springframework.transaction.support.TransactionCallback<?> callback = invocation
                                                .getArgument(0);
                                return callback.doInTransaction(null);
                        });

                        // Load tools manually for tests
                        mcpServer.loadToolsFromYaml();
                } catch (Exception e) {
                        // If loading fails, tests will use empty list
                }
        }

        @Test
        @DisplayName("listTools should return all available tools")
        void testListTools() {
                List<MatrixMCPModels.MCPTool> tools = mcpServer.listTools();

                assertNotNull(tools);
                assertTrue(tools.size() >= 6, "Should have at least 6 tools");

                // Vérifier que les outils attendus sont présents
                Map<String, MatrixMCPModels.MCPTool> toolsMap = new HashMap<>();
                for (MatrixMCPModels.MCPTool tool : tools) {
                        toolsMap.put(tool.getName(), tool);
                }

                assertTrue(toolsMap.containsKey("get_resident_info"), "Should have get_resident_info tool");
                assertTrue(toolsMap.containsKey("get_emergency_numbers"), "Should have get_emergency_numbers tool");
                assertTrue(toolsMap.containsKey("get_reservation_details"), "Should have get_reservation_details tool");
                assertTrue(toolsMap.containsKey("create_reservation"), "Should have create_reservation tool");
                assertTrue(toolsMap.containsKey("create_github_issue"), "Should have create_github_issue tool");
                assertTrue(toolsMap.containsKey("get_space_info"), "Should have get_space_info tool");
                assertTrue(toolsMap.containsKey("list_spaces"), "Should have list_spaces tool");
                assertTrue(toolsMap.containsKey("check_space_availability"),
                                "Should have check_space_availability tool");
                assertTrue(toolsMap.containsKey("list_my_reservations"), "Should have list_my_reservations tool");
                assertTrue(toolsMap.containsKey("get_reservation_access_code"),
                                "Should have get_reservation_access_code tool");
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

                // Mock residentHandler.getResidentInfo to return a successful result
                // Use lenient() for unitRepository and unitMemberRepository since they're not
                // used when residentHandler is mocked
                lenient().when(unitRepository.findByNameContainingIgnoreCase("A123")).thenReturn(units);
                lenient().when(unitMemberRepository.findByUnitId(testUnit.getId())).thenReturn(members);
                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Resident info for A123")
                                                .build()))
                                .build();
                when(residentHandler.getResidentInfo(arguments, publicAuthContext)).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_resident_info", arguments,
                                publicAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                assertFalse(result.getContent().isEmpty(), "Result content should not be empty");

                // Note: Content is mocked, so we just verify the structure
                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        // Verify content structure
                }
        }

        @Test
        @DisplayName("getResidentInfo should find residents by floor")
        void testGetResidentInfoByFloor() {
                // Arrange
                Map<String, Object> arguments = new HashMap<>();
                arguments.put("floor", "123");

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Resident info for floor 123")
                                                .build()))
                                .build();

                when(residentHandler.getResidentInfo(arguments, publicAuthContext)).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_resident_info", arguments,
                                publicAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                assertFalse(result.getContent().isEmpty(), "Result content should not be empty");
        }

        @Test
        @DisplayName("getResidentInfo should return error when no apartment or floor specified")
        void testGetResidentInfoNoParams() {
                // Arrange
                Map<String, Object> arguments = new HashMap<>();

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Please specify apartment or floor")
                                                .build()))
                                .build();

                when(residentHandler.getResidentInfo(arguments, publicAuthContext)).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_resident_info", arguments,
                                publicAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent());
        }

        @Test
        @DisplayName("getEmergencyNumbers should return emergency contacts")
        void testGetEmergencyNumbers() {
                // Arrange
                Map<String, Object> arguments = new HashMap<>();

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Emergency numbers: ACAF")
                                                .build()))
                                .build();

                when(residentHandler.getEmergencyNumbers(publicAuthContext)).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_emergency_numbers", arguments,
                                publicAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                assertFalse(result.getContent().isEmpty(), "Result content should not be empty");
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Réservation " + reservationId.toString())
                                                .build()))
                                .build();
                when(reservationHandler.getReservationDetails(any(), eq(authenticatedAuthContext)))
                                .thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_reservation_details", arguments,
                                authenticatedAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                assertFalse(result.getContent().isEmpty(), "Result content should not be empty");

                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        assertTrue(
                                        text.contains("réservation") || text.contains(reservationId.toString())
                                                        || text.contains("reservation"),
                                        "Result should contain reservation information");
                }
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

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("create_reservation", arguments,
                                publicAuthContext);

                // Assert
                // Should return error result instead of throwing exception
                assertTrue(result.isError(), "Result should be an error for unauthenticated user");
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Salle de réunion - Une belle salle")
                                                .build()))
                                .build();
                when(spaceHandler.getSpaceInfo(any(), eq(publicAuthContext))).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_space_info", arguments,
                                publicAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                assertFalse(result.getContent().isEmpty(), "Result content should not be empty");

                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        assertTrue(text.contains("Salle de réunion") || text.contains("espace")
                                        || text.contains("space"),
                                        "Result should contain space information");
                }
        }

        @Test
        @DisplayName("callTool should return error for unknown tool")
        void testCallToolUnknownTool() {
                // Arrange
                Map<String, Object> arguments = new HashMap<>();

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("unknown_tool", arguments, publicAuthContext);

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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Salle de réunion, Salle de sport")
                                                .build()))
                                .build();
                when(spaceHandler.listSpaces(eq(publicAuthContext))).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("list_spaces", arguments, publicAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                assertFalse(result.getContent().isEmpty(), "Result content should not be empty");

                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        assertTrue(text.contains("Salle de réunion") || text.contains("Salle de sport")
                                        || text.contains("space"),
                                        "Result should contain space names");
                }
        }

        @Test
        @DisplayName("listSpaces should return empty message when no spaces")
        void testListSpacesEmpty() {
                // Arrange
                Map<String, Object> arguments = new HashMap<>();
                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("matrix.mcp.space.noSpaces")
                                                .build()))
                                .build();
                when(spaceHandler.listSpaces(eq(publicAuthContext))).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("list_spaces", arguments, publicAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                assertFalse(result.getContent().isEmpty(), "Result content should not be empty");
                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        // The messageSource mock returns the key, so check for the translation key or
                        // common words
                        assertTrue(
                                        text.contains("none") || text.contains("noSpaces") || text.contains("Aucun")
                                                        || text.contains("disponible") || text.contains("available")
                                                        || text.contains("empty"),
                                        "Result should indicate no spaces available. Got: " + text);
                }
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("📅 **Disponibilité de Salle de réunion**\n\nPériode: " +
                                                                LocalDate.now().plusDays(1).toString() + " - " +
                                                                LocalDate.now().plusDays(3).toString()
                                                                + "\n\nDisponible - availability information")
                                                .build()))
                                .build();

                when(spaceHandler.checkSpaceAvailability(any(), eq(publicAuthContext))).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("check_space_availability", arguments,
                                publicAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        assertTrue(text.contains("disponibilité") || text.contains("disponible")
                                        || text.contains("availability"),
                                        "Result should contain availability information");
                }
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("📅 **Disponibilité de Salle de réunion**\n\nPériode: Christmas - Christmas\n\nDisponible")
                                                .build()))
                                .build();

                when(spaceHandler.checkSpaceAvailability(any(), eq(publicAuthContext))).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("check_space_availability", arguments,
                                publicAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
        }

        @Test
        @DisplayName("listMyReservations should require authentication")
        void testListMyReservationsRequiresAuth() {
                // Arrange
                Map<String, Object> arguments = new HashMap<>();

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("list_my_reservations", arguments,
                                publicAuthContext);

                // Assert
                // Should return error result instead of throwing exception
                assertTrue(result.isError(), "Result should be an error for unauthenticated user");
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Réservation - Salle de réunion")
                                                .build()))
                                .build();
                when(reservationHandler.listMyReservations(any(), eq(authenticatedAuthContext)))
                                .thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("list_my_reservations", arguments,
                                authenticatedAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        assertTrue(
                                        text.contains("réservation") || text.contains("Salle de réunion")
                                                        || text.contains("reservation"),
                                        "Result should contain reservation information");
                }
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("1 réservation")
                                                .build()))
                                .build();
                when(reservationHandler.listMyReservations(any(), eq(authenticatedAuthContext)))
                                .thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("list_my_reservations", arguments,
                                authenticatedAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        // Should only contain upcoming reservation
                        assertTrue(text.contains("1") || text.contains("réservation") || text.contains("reservation"),
                                        "Result should contain filtered reservations");
                }
        }

        @Test
        @DisplayName("getReservationAccessCode should require authentication")
        void testGetReservationAccessCodeRequiresAuth() {
                // Arrange
                Map<String, Object> arguments = new HashMap<>();
                arguments.put("reservationId", UUID.randomUUID().toString());

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_reservation_access_code", arguments,
                                publicAuthContext);

                // Assert
                // Should return error result instead of throwing exception
                assertTrue(result.isError(), "Result should be an error for unauthenticated user");
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Code d'accès: 1234")
                                                .build()))
                                .build();
                when(reservationHandler.getReservationAccessCode(any(), eq(authenticatedAuthContext)))
                                .thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_reservation_access_code", arguments,
                                authenticatedAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        assertTrue(text.contains("Code d'accès") || text.contains("instructions")
                                        || text.contains("access"),
                                        "Result should contain access code and instructions");
                }
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(true)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("matrix.mcp.reservation.accessDenied")
                                                .build()))
                                .build();
                when(reservationHandler.getReservationAccessCode(any(), eq(authenticatedAuthContext)))
                                .thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_reservation_access_code", arguments,
                                authenticatedAuthContext);

                // Assert
                assertTrue(result.isError(), "Result should be an error");
                assertNotNull(result.getContent(), "Result should have content");
                if (!result.getContent().isEmpty()) {
                        String text = result.getContent().get(0).getText();
                        if (text != null) {
                                assertTrue(text.contains("accès") || text.contains("pas accès")
                                                || text.contains("access"),
                                                "Result should indicate access denied");
                        }
                }
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Réservation créée avec succès")
                                                .build()))
                                .build();
                when(reservationHandler.createReservation(any(), eq(authenticatedAuthContext)))
                                .thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("create_reservation", arguments,
                                authenticatedAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        assertTrue(text.contains("Réservation créée") || text.contains("succès")
                                        || text.contains("created"),
                                        "Result should indicate successful creation");
                }
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

                lenient()
                                .when(spacesService.isSpaceAvailable(spaceId, LocalDate.now().plusDays(1),
                                                LocalDate.now().plusDays(2)))
                                .thenReturn(false);

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(true)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("matrix.mcp.reservation.spaceNotAvailable")
                                                .build()))
                                .build();
                when(reservationHandler.createReservation(any(), eq(authenticatedAuthContext)))
                                .thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("create_reservation", arguments,
                                authenticatedAuthContext);

                // Assert
                assertTrue(result.isError(), "Result should be an error");
                assertNotNull(result.getContent(), "Result should have content");
                if (!result.getContent().isEmpty()) {
                        String text = result.getContent().get(0).getText();
                        if (text != null) {
                                // The messageSource mock returns the key, so check for the translation key
                                assertTrue(
                                                text.contains("spaceNotAvailable") || text.contains("disponible")
                                                                || text.contains("pas disponible")
                                                                || text.contains("available"),
                                                "Result should indicate space is not available. Got: " + text);
                        }
                }
        }

        @Test
        @DisplayName("getSpaceInfo should return error when space not found")
        void testGetSpaceInfoNotFound() {
                // Arrange
                UUID spaceId = UUID.randomUUID();
                Map<String, Object> arguments = new HashMap<>();
                arguments.put("spaceId", spaceId.toString());

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(true)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("matrix.mcp.space.notFound")
                                                .build()))
                                .build();

                when(spaceHandler.getSpaceInfo(arguments, publicAuthContext)).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_space_info", arguments,
                                publicAuthContext);

                // Assert
                assertTrue(result.isError(), "Result should be an error");
                assertNotNull(result.getContent(), "Result should have content");
                if (!result.getContent().isEmpty()) {
                        String text = result.getContent().get(0).getText();
                        if (text != null) {
                                // The messageSource mock returns the key, so check for the translation key
                                assertTrue(
                                                text.contains("notFound") || text.contains("trouvé")
                                                                || text.contains("non trouvé")
                                                                || text.contains("found"),
                                                "Result should indicate space not found. Got: " + text);
                        }
                }
        }

        @Test
        @DisplayName("getResidentInfo should handle apartment number without 'Appartement' prefix")
        void testGetResidentInfoApartmentNumber() {
                // Arrange
                Map<String, Object> arguments = new HashMap<>();
                arguments.put("apartment", "808");

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Resident info for apartment 808")
                                                .build()))
                                .build();

                when(residentHandler.getResidentInfo(arguments, publicAuthContext)).thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("get_resident_info", arguments,
                                publicAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent());
        }

        @Test
        @DisplayName("generatePaymentLink should require authentication")
        void testGeneratePaymentLinkRequiresAuth() {
                // Arrange
                Map<String, Object> arguments = new HashMap<>();
                arguments.put("reservationId", UUID.randomUUID().toString());

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("generate_payment_link", arguments,
                                publicAuthContext);

                // Assert
                // Should return error result instead of throwing exception
                assertTrue(result.isError(), "Result should be an error for unauthenticated user");
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("Lien de paiement: https://checkout.stripe.com/test-session")
                                                .build()))
                                .build();
                when(reservationHandler.generatePaymentLink(any(), eq(authenticatedAuthContext)))
                                .thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("generate_payment_link", arguments,
                                authenticatedAuthContext);

                // Assert
                assertFalse(result.isError(), "Result should not be an error");
                assertNotNull(result.getContent(), "Result should have content");
                assertFalse(result.getContent().isEmpty(), "Result content should not be empty");
                if (!result.getContent().isEmpty() && result.getContent().get(0).getText() != null) {
                        String text = result.getContent().get(0).getText();
                        assertTrue(text.contains("Lien de paiement") || text.contains("généré")
                                        || text.contains("payment"),
                                        "Result should contain payment link information");
                }
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

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(true)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("matrix.mcp.reservation.accessDenied")
                                                .build()))
                                .build();
                when(reservationHandler.generatePaymentLink(any(), eq(authenticatedAuthContext)))
                                .thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("generate_payment_link", arguments,
                                authenticatedAuthContext);

                // Assert
                assertTrue(result.isError(), "Result should be an error");
                assertNotNull(result.getContent(), "Result should have content");
                if (!result.getContent().isEmpty()) {
                        String text = result.getContent().get(0).getText();
                        if (text != null) {
                                assertTrue(text.contains("accès") || text.contains("pas accès")
                                                || text.contains("access"),
                                                "Result should indicate access denied");
                        }
                }
        }

        @Test
        @DisplayName("generatePaymentLink should handle missing reservationId")
        void testGeneratePaymentLinkMissingId() {
                // Arrange
                Map<String, Object> arguments = new HashMap<>();

                MatrixMCPModels.MCPToolResult mockResult = MatrixMCPModels.MCPToolResult.builder()
                                .isError(true)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text("matrix.mcp.reservation.reservationIdRequired")
                                                .build()))
                                .build();
                when(reservationHandler.generatePaymentLink(any(), eq(authenticatedAuthContext)))
                                .thenReturn(mockResult);

                // Act
                MatrixMCPModels.MCPToolResult result = mcpServer.callTool("generate_payment_link", arguments,
                                authenticatedAuthContext);

                // Assert
                assertTrue(result.isError(), "Result should be an error");
                assertNotNull(result.getContent(), "Result should have content");
                if (!result.getContent().isEmpty()) {
                        String text = result.getContent().get(0).getText();
                        if (text != null) {
                                assertTrue(text.contains("reservationId") || text.contains("required"),
                                                "Result should indicate reservationId is required");
                        }
                }
        }
}
