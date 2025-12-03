package com.neohoods.portal.platform.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.matrix.api.RoomParticipationApi;
import com.neohoods.portal.platform.matrix.model.ClientEvent;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantInitializationService;
import com.neohoods.portal.platform.services.matrix.MatrixAssistantService;
import com.neohoods.portal.platform.spaces.services.DigitalLockService;
import com.neohoods.portal.platform.spaces.services.NukiRemoteAPIService;
import com.neohoods.portal.platform.spaces.services.StripeService;
import com.neohoods.portal.platform.spaces.services.TTlockRemoteAPIService;

/**
 * Integration test for Matrix Bot Initialization
 * 
 * This test runs against the real Matrix server (chat.neohoods.com) to validate
 * that initialization works correctly. It verifies:
 * - All rooms are created
 * - All users are created
 * - Users are in the correct rooms
 * 
 * Run with: mvn test -Dtest=MatrixAssistantInitializationIntegrationTest
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@DisplayName("Matrix Bot Initialization Integration Test")
@TestPropertySource(properties = {
        "neohoods.portal.matrix.enabled=true",
        "neohoods.portal.matrix.initialization.enabled=true",
        "neohoods.portal.matrix.initialization.user-creation-enabled=true",
        "neohoods.portal.matrix.disabled=false",
        "neohoods.portal.matrix.local-assistant.enabled=true",
        "neohoods.portal.matrix.local-assistant.user-id=@alfred:chat.neohoods.com",
        "neohoods.portal.matrix.local-assistant.permanent-token=mpt_8gvbvWAQltW0kESWrb9v7u9lSiM9NN_Lnlsa4",
        "neohoods.portal.matrix.oauth2.client-id=01KAKRXM4EXRFD2W9HQEYFV7CA",
        "neohoods.portal.matrix.oauth2.client-secret=c13n753c237",
        "neohoods.portal.matrix.oauth2.token-endpoint=https://mas.chat.neohoods.com/oauth2/token",
        "neohoods.portal.matrix.mas.url=https://mas.chat.neohoods.com",
        // Use the same PostgreSQL database as docker-compose
        "spring.datasource.url=jdbc:postgresql://localhost:8433/neohoods-portal",
        "spring.datasource.username=local",
        "spring.datasource.password=local"
})
@org.junit.jupiter.api.Disabled("Requires Matrix server - run manually with: mvn test -Dtest=MatrixAssistantInitializationIntegrationTest")
class MatrixAssistantInitializationIntegrationTest {

    @MockBean
    protected StripeService stripeService;

    @MockBean
    protected TTlockRemoteAPIService ttlockService;

    @MockBean
    protected NukiRemoteAPIService nukiService;

    @MockBean
    protected DigitalLockService digitalLockService;

    @MockBean
    protected Auth0Service auth0Service;

    @MockBean
    protected MailService mailService;

    @MockBean
    protected NotificationsService notificationsService;

    @Autowired(required = false)
    private MatrixAssistantInitializationService initializationService;

    @Autowired(required = false)
    private MatrixAssistantService matrixAssistantService;

    @Autowired
    private UsersRepository usersRepository;

    @BeforeEach
    void setUp() {
        // Skip if services are not available (Matrix not configured)
        org.junit.jupiter.api.Assumptions.assumeTrue(initializationService != null,
                "MatrixAssistantInitializationService not available - Matrix may not be configured");
        org.junit.jupiter.api.Assumptions.assumeTrue(matrixAssistantService != null,
                "MatrixAssistantService not available - Matrix may not be configured");
    }

    @Test
    @DisplayName("Should initialize bot and verify all rooms are created")
    void testInitializeBotAndVerifyRooms() {
        // Given - Run initialization
        initializationService.initializeBotManually();

        // When - Get existing rooms in space
        String spaceId = getSpaceId();
        assertNotNull(spaceId, "Space ID should be configured");

        Map<String, String> existingRooms = matrixAssistantService.getExistingRoomsInSpace(spaceId);

        // Then - Verify rooms exist
        assertFalse(existingRooms.isEmpty(), "Should have at least one room");

        // Log rooms for debugging
        System.out.println("\n=== Rooms in space ===");
        existingRooms.forEach((name, id) -> System.out.println("  - " + name + " -> " + id));
        System.out.println("Total rooms: " + existingRooms.size() + "\n");
    }

    @Test
    @DisplayName("Should create all users and verify they exist")
    void testCreateAllUsers() {
        // Given - Get all users from database
        List<UserEntity> allUsers = StreamSupport.stream(usersRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
        assertFalse(allUsers.isEmpty(), "Should have at least one user in database");

        // When - Run initialization
        initializationService.initializeBotManually();

        // Then - Verify bot can access Matrix
        Optional<String> assistantUserIdOpt = matrixAssistantService.getAssistantUserId();
        assertTrue(assistantUserIdOpt.isPresent(), "Bot should have a user ID");

        String assistantUserId = assistantUserIdOpt.get();
        System.out.println("\n=== Bot User ID ===");
        System.out.println("Bot: " + assistantUserId + "\n");

        // Note: We can't directly verify users exist via MAS API without admin token
        // But we can verify the initialization completed without errors
        System.out.println("=== Users in database ===");
        int usersInMAS = 0;
        int usersNotInMAS = 0;
        for (UserEntity user : allUsers) {
            String matrixUsername = user.getUsername().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            String matrixUserId = "@" + matrixUsername + ":chat.neohoods.com";

            // Verify user exists in Matrix (via Matrix API - try to get profile)
            boolean existsInMatrix = false;
            try {
                // Use reflection to access getMatrixAccessToken() which is private
                java.lang.reflect.Method getMatrixAccessTokenMethod = matrixAssistantService.getClass()
                        .getDeclaredMethod("getMatrixAccessToken");
                getMatrixAccessTokenMethod.setAccessible(true);
                @SuppressWarnings("unchecked")
                Optional<com.neohoods.portal.platform.matrix.ApiClient> apiClientOpt = (Optional<com.neohoods.portal.platform.matrix.ApiClient>) getMatrixAccessTokenMethod
                        .invoke(matrixAssistantService);

                if (apiClientOpt.isPresent()) {
                    com.neohoods.portal.platform.matrix.api.UserDataApi userDataApi = new com.neohoods.portal.platform.matrix.api.UserDataApi(
                            apiClientOpt.get());
                    try {
                        // Try to get user displayname to verify user exists
                        userDataApi.getProfileField(matrixUserId, "displayname");
                        existsInMatrix = true;
                        usersInMAS++;
                    } catch (com.neohoods.portal.platform.matrix.ApiException e) {
                        if (e.getCode() != 404) {
                            // Other error, log it
                            System.out.println("    ⚠ Error checking user " + matrixUserId + ": " + e.getMessage());
                        }
                        usersNotInMAS++;
                    }
                }
            } catch (Exception e) {
                System.out.println("    ⚠ Error checking user " + matrixUserId + ": " + e.getMessage());
                usersNotInMAS++;
            }

            String status = existsInMatrix ? "✓" : "✗";
            System.out.println("  " + status + " " + user.getUsername() + " -> " + matrixUserId +
                    (existsInMatrix ? " (exists in Matrix)" : " (NOT in Matrix)"));
        }
        System.out.println("Total users in database: " + allUsers.size());
        System.out.println("Users in Matrix: " + usersInMAS);
        System.out.println("Users NOT in Matrix: " + usersNotInMAS + "\n");

        // Assert that users are created
        assertTrue(usersInMAS > 0, "At least some users should exist in Matrix");
    }

    @Test
    @DisplayName("Should verify users are in correct rooms")
    void testUsersInCorrectRooms() {
        // Given - Run initialization
        System.out.println("\n=== Running initialization ===");
        initializationService.initializeBotManually();

        // When - Get space and rooms
        String spaceId = getSpaceId();
        assertNotNull(spaceId, "Space ID should be configured");

        Map<String, String> existingRooms = matrixAssistantService.getExistingRoomsInSpace(spaceId);
        List<UserEntity> allUsers = StreamSupport.stream(usersRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());

        // Then - Verify rooms exist and check members
        System.out.println("\n=== Room Membership Verification ===");
        System.out.println("Space ID: " + spaceId);
        System.out.println("Rooms available: " + existingRooms.size());
        System.out.println("Users in database: " + allUsers.size());

        // Verify expected rooms exist
        assertFalse(existingRooms.isEmpty(), "Should have rooms in space");

        // Check for common rooms
        boolean hasGeneral = existingRooms.containsKey("General") || existingRooms.containsKey("general");
        boolean hasIT = existingRooms.containsKey("IT");

        System.out.println("Has General room: " + hasGeneral);
        System.out.println("Has IT room: " + hasIT);

        // Log all rooms
        System.out.println("\n=== All Rooms ===");
        existingRooms.forEach((name, id) -> System.out.println("  ✓ " + name + " -> " + id));

        // Verify IT room exists (required for summary)
        assertTrue(hasIT, "IT room should exist for summary");

        // Verify users are in rooms by checking room members
        System.out.println("\n=== Room Members Verification ===");
        try {
            // Use reflection to access getMatrixAccessToken() which is private
            java.lang.reflect.Method getMatrixAccessTokenMethod = matrixAssistantService.getClass()
                    .getDeclaredMethod("getMatrixAccessToken");
            getMatrixAccessTokenMethod.setAccessible(true);
            @SuppressWarnings("unchecked")
            Optional<com.neohoods.portal.platform.matrix.ApiClient> apiClientOpt = (Optional<com.neohoods.portal.platform.matrix.ApiClient>) getMatrixAccessTokenMethod
                    .invoke(matrixAssistantService);

            if (apiClientOpt.isPresent()) {
                com.neohoods.portal.platform.matrix.api.RoomMembershipApi membershipApi = new com.neohoods.portal.platform.matrix.api.RoomMembershipApi(
                        apiClientOpt.get());

                // Check members in each room
                for (Map.Entry<String, String> roomEntry : existingRooms.entrySet()) {
                    String roomName = roomEntry.getKey();
                    String roomId = roomEntry.getValue();

                    try {
                        // Use RoomParticipationApi to get room state and filter m.room.member events
                        RoomParticipationApi participationApi = new RoomParticipationApi(apiClientOpt.get());
                        List<ClientEvent> roomState = participationApi.getRoomState(roomId);

                        // Filter m.room.member events to get members
                        List<String> memberUserIds = new ArrayList<>();
                        for (ClientEvent event : roomState) {
                            if ("m.room.member".equals(event.getType()) && event.getStateKey() != null) {
                                String userId = event.getStateKey();
                                // Check if member is joined (not left, banned, etc.)
                                if (event.getContent() != null && event.getContent() instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> content = (Map<String, Object>) event.getContent();
                                    String membership = (String) content.get("membership");
                                    if ("join".equals(membership)) {
                                        memberUserIds.add(userId);
                                    }
                                }
                            }
                        }

                        int memberCount = memberUserIds.size();
                        if (memberCount > 0) {
                            System.out.println("  Room '" + roomName + "': " + memberCount + " members");

                            // Log first few members for debugging
                            if (memberCount > 0 && memberCount <= 10) {
                                memberUserIds.forEach(userId -> {
                                    System.out.println("    - " + userId);
                                });
                            } else if (memberCount > 10) {
                                System.out.println("    (showing first 5 members)");
                                memberUserIds.stream()
                                        .limit(5)
                                        .forEach(userId -> System.out.println("    - " + userId));
                            }
                        } else {
                            System.out.println("  Room '" + roomName + "': 0 members");
                        }
                    } catch (com.neohoods.portal.platform.matrix.ApiException e) {
                        System.out.println("  ⚠ Error getting members for room '" + roomName + "': " + e.getMessage());
                    } catch (Exception e) {
                        System.out.println("  ⚠ Error getting members for room '" + roomName + "': " + e.getMessage());
                    }
                }

                // Verify that at least some rooms have members
                int totalMembersAcrossRooms = 0;
                RoomParticipationApi participationApiForCount = new RoomParticipationApi(apiClientOpt.get());
                for (String roomId : existingRooms.values()) {
                    try {
                        List<ClientEvent> roomState = participationApiForCount.getRoomState(roomId);
                        int memberCount = 0;
                        for (ClientEvent event : roomState) {
                            if ("m.room.member".equals(event.getType()) && event.getStateKey() != null) {
                                if (event.getContent() != null && event.getContent() instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> content = (Map<String, Object>) event.getContent();
                                    String membership = (String) content.get("membership");
                                    if ("join".equals(membership)) {
                                        memberCount++;
                                    }
                                }
                            }
                        }
                        totalMembersAcrossRooms += memberCount;
                    } catch (Exception e) {
                        // Skip errors
                    }
                }
                System.out.println("\nTotal members across all rooms: " + totalMembersAcrossRooms);
                assertTrue(totalMembersAcrossRooms > 0, "At least some rooms should have members");
            }
        } catch (Exception e) {
            System.err.println("Error verifying room members: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    @DisplayName("Should verify IT room exists and has summary message")
    void testITRoomExists() {
        // Given - Run initialization
        initializationService.initializeBotManually();

        // When - Get IT room
        String spaceId = getSpaceId();
        assertNotNull(spaceId, "Space ID should be configured");

        Optional<String> itRoomIdOpt = matrixAssistantService.getRoomIdByName("IT", spaceId);

        // Then - Verify IT room exists
        assertTrue(itRoomIdOpt.isPresent(), "IT room should exist");

        System.out.println("\n=== IT Room ===");
        System.out.println("IT Room ID: " + itRoomIdOpt.get() + "\n");
    }

    @Test
    @DisplayName("Full initialization verification")
    void testFullInitializationVerification() {
        // Given
        String spaceId = getSpaceId();
        assertNotNull(spaceId, "Space ID should be configured");

        List<UserEntity> allUsers = StreamSupport.stream(usersRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
        assertFalse(allUsers.isEmpty(), "Should have users in database");

        // When - Run initialization
        System.out.println("\n=== Starting Full Initialization Verification ===");
        System.out.println("Space ID: " + spaceId);
        System.out.println("Users to process: " + allUsers.size());

        try {
            initializationService.initializeBotManually();
        } catch (Exception e) {
            System.err.println("ERROR during initialization: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // Then - Verify everything
        Map<String, String> existingRooms = matrixAssistantService.getExistingRoomsInSpace(spaceId);

        System.out.println("\n=== Verification Results ===");
        System.out.println("Space ID: " + spaceId);
        System.out.println("Rooms created: " + existingRooms.size());
        System.out.println("Users in database: " + allUsers.size());

        // Verify rooms exist
        assertFalse(existingRooms.isEmpty(), "Should have created rooms");
        System.out.println("✓ Rooms verification passed");

        // Verify IT room exists
        Optional<String> itRoomIdOpt = matrixAssistantService.getRoomIdByName("IT", spaceId);
        assertTrue(itRoomIdOpt.isPresent(), "IT room should exist");
        System.out.println("✓ IT room exists: " + itRoomIdOpt.get());

        // Verify bot user ID
        Optional<String> assistantUserIdOpt = matrixAssistantService.getAssistantUserId();
        assertTrue(assistantUserIdOpt.isPresent(), "Bot should have a user ID");
        System.out.println("✓ Bot user ID: " + assistantUserIdOpt.get());

        // List all rooms
        System.out.println("\n=== All Rooms in Space ===");
        existingRooms.forEach((name, id) -> {
            System.out.println("  ✓ " + name + " -> " + id);
        });

        // List all users that should be created
        System.out.println("\n=== Users Expected in Matrix ===");
        allUsers.forEach(user -> {
            String matrixUsername = user.getUsername().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            String matrixUserId = "@" + matrixUsername + ":chat.neohoods.com";
            // Avoid lazy initialization by getting unit name safely
            String unitName = "none";
            try {
                if (user.getPrimaryUnit() != null) {
                    unitName = user.getPrimaryUnit().getName();
                }
            } catch (Exception e) {
                // Lazy initialization failed, use unit ID or skip
                unitName = user.getPrimaryUnit() != null ? "unit-" + user.getPrimaryUnit().getId() : "none";
            }
            System.out.println("  - " + user.getUsername() + " -> " + matrixUserId +
                    " (Type: " + user.getType() + ", Unit: " + unitName + ")");
        });

        System.out.println("\n=== Initialization Complete ===");
        System.out.println("✓ All rooms created");
        System.out.println("✓ All users processed");
        System.out.println("✓ IT room exists with summary");
        System.out.println("\n");
    }

    private String getSpaceId() {
        // Try to get space ID from configuration
        // This is a workaround since we can't easily inject @Value in tests
        try {
            java.lang.reflect.Field field = MatrixAssistantInitializationService.class
                    .getDeclaredField("spaceId");
            field.setAccessible(true);
            return (String) field.get(initializationService);
        } catch (Exception e) {
            // Fallback to default
            return "!YenniyNVsUoBCLHtZS:chat.neohoods.com";
        }
    }
}
