package com.neohoods.portal.platform.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.repositories.UsersRepository;

/**
 * Unit tests for MatrixAssistantInitializationService
 * 
 * These tests help validate the initialization logic and allow for quick
 * iteration during development.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Matrix Bot Initialization Service Tests")
class MatrixAssistantInitializationServiceTest {

    @Mock
    private MatrixAssistantService matrixAssistantService;

    @Mock
    private MatrixOAuth2Service oauth2Service;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    @InjectMocks
    private MatrixAssistantInitializationService initializationService;

    private static final String SPACE_ID = "!YenniyNVsUoBCLHtZS:chat.neohoods.com";
    private static final String BOT_USER_ID = "@alfred:chat.neohoods.com";
    private static final String HOMESERVER_URL = "https://matrix.neohoods.com";

    @BeforeEach
    void setUp() {
        // Set up configuration values using reflection
        ReflectionTestUtils.setField(initializationService, "spaceId", SPACE_ID);
        ReflectionTestUtils.setField(initializationService, "roomsConfigFile", "classpath:matrix-default-rooms.yaml");
        ReflectionTestUtils.setField(initializationService, "disabled", false);
        ReflectionTestUtils.setField(initializationService, "homeserverUrl", HOMESERVER_URL);
        ReflectionTestUtils.setField(initializationService, "adminUsersConfig", "");
    }

    @Test
    @DisplayName("Should skip initialization when bot is disabled")
    void testInitializationSkippedWhenDisabled() {
        // Given
        ReflectionTestUtils.setField(initializationService, "disabled", true);

        // When
        initializationService.initializeBotManually();

        // Then
        verify(matrixAssistantService, never()).checkSpaceExists(anyString());
    }

    @Test
    @DisplayName("Should skip initialization when space ID is not configured")
    void testInitializationSkippedWhenNoSpaceId() {
        // Given
        ReflectionTestUtils.setField(initializationService, "spaceId", "");

        // When
        initializationService.initializeBotManually();

        // Then
        verify(matrixAssistantService, never()).checkSpaceExists(anyString());
    }

    @Test
    @DisplayName("Should skip initialization when space does not exist")
    void testInitializationSkippedWhenSpaceDoesNotExist() {
        // Given
        when(matrixAssistantService.checkSpaceExists(SPACE_ID)).thenReturn(false);

        // When
        initializationService.initializeBotManually();

        // Then
        verify(matrixAssistantService).checkSpaceExists(SPACE_ID);
    }

    @Test
    @DisplayName("Should continue initialization even if bot user ID cannot be retrieved (getAssistantUserId no longer called)")
    void testInitializationContinuesWhenBotUserIdNotFound() {
        // Given
        // Note: getAssistantUserId() is no longer called in doInitializeBot()
        when(matrixAssistantService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixAssistantService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(usersRepository.findAllWithPrimaryUnit()).thenReturn(new ArrayList<>());

        // When
        initializationService.initializeBotManually();

        // Then
        verify(matrixAssistantService).checkSpaceExists(SPACE_ID);
        verify(matrixAssistantService, times(2)).getExistingRoomsInSpace(SPACE_ID); // Called in doInitializeBot and countPendingInvitations
        // getAssistantUserId() is no longer called in the initialization flow
    }

    @Test
    @DisplayName("Should load rooms configuration from YAML")
    void testLoadRoomsConfig() throws Exception {
        // Given
        String yamlContent = """
                rooms:
                  - name: "General"
                    description: "General discussion"
                    auto-join: true
                  - name: "Proprio"
                    description: "Owners room"
                    auto-join: false
                """;
        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());

        when(matrixAssistantService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixAssistantService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAllWithPrimaryUnit()).thenReturn(new ArrayList<>());
        when(matrixAssistantService.updateBotAvatar()).thenReturn(false);
        when(matrixAssistantService.updateBotDisplayName()).thenReturn(false);
        when(matrixAssistantService.getRoomIdByName(eq("IT"), eq(SPACE_ID))).thenReturn(Optional.of("!itRoom:chat.neohoods.com"));
        when(matrixAssistantService.sendMessage(anyString(), anyString())).thenReturn(true);

        // When
        initializationService.initializeBotManually();

        // Then
        verify(resourceLoader).getResource(anyString());
    }

    @Test
    @DisplayName("Should create rooms when they don't exist")
    void testCreateRooms() throws Exception {
        // Given
        String yamlContent = """
                rooms:
                  - name: "General"
                    description: "General discussion"
                    auto-join: true
                """;
        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());

        when(matrixAssistantService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        // getExistingRoomsInSpace is called 3 times: in doInitializeBot, createDefaultRooms, and countPendingInvitations
        when(matrixAssistantService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAllWithPrimaryUnit()).thenReturn(new ArrayList<>());
        when(matrixAssistantService.createRoomInSpace(anyString(), anyString(), anyString(), eq(SPACE_ID), anyBoolean()))
                .thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixAssistantService.updateBotAvatar()).thenReturn(false);
        when(matrixAssistantService.updateBotDisplayName()).thenReturn(false);
        when(matrixAssistantService.getRoomIdByName(eq("IT"), eq(SPACE_ID))).thenReturn(Optional.of("!itRoom:chat.neohoods.com"));
        when(matrixAssistantService.sendMessage(anyString(), anyString())).thenReturn(true);
        when(matrixAssistantService.getUserRoomMembership(anyString(), anyString())).thenReturn(Optional.empty());

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        verify(matrixAssistantService, times(3)).getExistingRoomsInSpace(SPACE_ID); // Called in doInitializeBot, createDefaultRooms, and countPendingInvitations
        verify(matrixAssistantService).createRoomInSpace(eq("General"), eq("General discussion"), any(), eq(SPACE_ID),
                eq(true));
    }

    @Test
    @DisplayName("Should skip creating rooms that already exist")
    void testSkipExistingRooms() throws Exception {
        // Given
        String yamlContent = """
                rooms:
                  - name: "General"
                    description: "General discussion"
                    auto-join: true
                """;
        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());

        Map<String, String> existingRooms = new HashMap<>();
        existingRooms.put("General", "!existingRoom:chat.neohoods.com");

        when(matrixAssistantService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixAssistantService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(existingRooms);
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAllWithPrimaryUnit()).thenReturn(new ArrayList<>());
        when(matrixAssistantService.updateBotAvatar()).thenReturn(false);
        when(matrixAssistantService.updateBotDisplayName()).thenReturn(false);
        when(matrixAssistantService.getRoomIdByName(eq("IT"), eq(SPACE_ID))).thenReturn(Optional.of("!itRoom:chat.neohoods.com"));
        when(matrixAssistantService.sendMessage(anyString(), anyString())).thenReturn(true);

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        verify(matrixAssistantService, never()).createRoomInSpace(eq("General"), anyString(), anyString(), eq(SPACE_ID),
                anyBoolean());
    }

    @Test
    @DisplayName("Should create users when user creation is enabled")
    void testCreateUsers() throws Exception {
        // Given
        String yamlContent = """
                rooms:
                  - name: "General"
                    description: "General discussion"
                    auto-join: true
                """;
        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());

        UserEntity user1 = createTestUser("john.doe", "John", "Doe", "john.doe@example.com", UserType.TENANT, "A701");
        UserEntity user2 = createTestUser("jane.smith", "Jane", "Smith", "jane.smith@example.com", UserType.OWNER,
                "B502");

        when(matrixAssistantService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixAssistantService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAllWithPrimaryUnit()).thenReturn(Arrays.asList(user1, user2));
        when(matrixAssistantService.createRoomInSpace(anyString(), anyString(), anyString(), eq(SPACE_ID), anyBoolean()))
                .thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixAssistantService.createMatrixUser(any(UserEntity.class)))
                .thenReturn(Optional.of("@john_doe:chat.neohoods.com"))
                .thenReturn(Optional.of("@jane_smith:chat.neohoods.com"));
        when(matrixAssistantService.updateMatrixUserProfile(anyString(), any(UserEntity.class))).thenReturn(true);
        when(matrixAssistantService.inviteUserToSpace(anyString())).thenReturn(true);
        when(matrixAssistantService.getRoomIdByName(anyString(), eq(SPACE_ID))).thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixAssistantService.inviteUserToRoomWithNotifications(anyString(), anyString(), anyBoolean()))
                .thenReturn(true);
        when(matrixAssistantService.updateBotAvatar()).thenReturn(false);
        when(matrixAssistantService.updateBotDisplayName()).thenReturn(false);
        when(matrixAssistantService.getRoomIdByName(eq("IT"), eq(SPACE_ID))).thenReturn(Optional.of("!itRoom:chat.neohoods.com"));
        when(matrixAssistantService.sendMessage(anyString(), anyString())).thenReturn(true);

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        verify(matrixAssistantService, times(2)).createMatrixUser(any(UserEntity.class));
        verify(matrixAssistantService, times(2)).updateMatrixUserProfile(anyString(), any(UserEntity.class));
    }

    @Test
    @DisplayName("Should always create users (userCreationEnabled was removed)")
    void testAlwaysCreateUsers() throws Exception {
        // Given
        // Note: userCreationEnabled field was removed - users are always created now
        String yamlContent = """
                rooms:
                  - name: "General"
                    description: "General discussion"
                    auto-join: true
                """;
        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());

        UserEntity user = createTestUser("john.doe", "John", "Doe", "john.doe@example.com", UserType.TENANT, "A701");

        when(matrixAssistantService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixAssistantService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAllWithPrimaryUnit()).thenReturn(Arrays.asList(user));
        when(matrixAssistantService.createRoomInSpace(anyString(), anyString(), anyString(), eq(SPACE_ID), anyBoolean()))
                .thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixAssistantService.createMatrixUser(any(UserEntity.class)))
                .thenReturn(Optional.of("@john_doe:chat.neohoods.com"));
        when(matrixAssistantService.updateMatrixUserProfile(anyString(), any(UserEntity.class))).thenReturn(true);
        when(matrixAssistantService.inviteUserToSpace(anyString())).thenReturn(true);
        when(matrixAssistantService.getRoomIdByName(anyString(), eq(SPACE_ID))).thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixAssistantService.inviteUserToRoomWithNotifications(anyString(), anyString(), anyBoolean()))
                .thenReturn(true);
        when(matrixAssistantService.updateBotAvatar()).thenReturn(false);
        when(matrixAssistantService.updateBotDisplayName()).thenReturn(false);
        when(matrixAssistantService.getRoomIdByName(eq("IT"), eq(SPACE_ID))).thenReturn(Optional.of("!itRoom:chat.neohoods.com"));
        when(matrixAssistantService.sendMessage(anyString(), anyString())).thenReturn(true);

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        verify(matrixAssistantService, times(1)).createMatrixUser(any(UserEntity.class));
    }

    @Test
    @DisplayName("Should invite users to appropriate rooms based on user type and unit")
    void testInviteUsersToRooms() throws Exception {
        // Given
        String yamlContent = """
                rooms:
                  - name: "General"
                    description: "General discussion"
                    auto-join: true
                  - name: "BatimentA"
                    description: "Building A"
                    auto-join: false
                  - name: "Proprio"
                    description: "Owners"
                    auto-join: false
                """;
        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());

        UserEntity owner = createTestUser("owner", "Owner", "User", "owner@example.com", UserType.OWNER, "A701");
        UserEntity tenantA = createTestUser("tenantA", "Tenant", "A", "tenanta@example.com", UserType.TENANT, "A702");
        UserEntity tenantB = createTestUser("tenantB", "Tenant", "B", "tenantb@example.com", UserType.TENANT, "B501");

        when(matrixAssistantService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixAssistantService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAllWithPrimaryUnit()).thenReturn(Arrays.asList(owner, tenantA, tenantB));
        when(matrixAssistantService.createRoomInSpace(anyString(), anyString(), anyString(), eq(SPACE_ID), anyBoolean()))
                .thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixAssistantService.createMatrixUser(any(UserEntity.class)))
                .thenReturn(Optional.of("@owner:chat.neohoods.com"))
                .thenReturn(Optional.of("@tenant_a:chat.neohoods.com"))
                .thenReturn(Optional.of("@tenant_b:chat.neohoods.com"));
        when(matrixAssistantService.updateMatrixUserProfile(anyString(), any(UserEntity.class))).thenReturn(true);
        when(matrixAssistantService.inviteUserToSpace(anyString())).thenReturn(true);
        when(matrixAssistantService.getRoomIdByName(eq("Proprio"), eq(SPACE_ID))).thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixAssistantService.getRoomIdByName(eq("BatimentA"), eq(SPACE_ID))).thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixAssistantService.getUserRoomMembership(anyString(), anyString())).thenReturn(Optional.empty());
        when(matrixAssistantService.parseBuildingFromUnitName(anyString())).thenReturn(Optional.of("A"));
        when(matrixAssistantService.inviteUserToRoomWithNotifications(anyString(), anyString(), anyBoolean()))
                .thenReturn(true);
        when(matrixAssistantService.updateBotAvatar()).thenReturn(false);
        when(matrixAssistantService.updateBotDisplayName()).thenReturn(false);
        when(matrixAssistantService.getRoomIdByName(eq("IT"), eq(SPACE_ID))).thenReturn(Optional.of("!itRoom:chat.neohoods.com"));
        when(matrixAssistantService.sendMessage(anyString(), anyString())).thenReturn(true);

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        // Owner should be invited to Proprio room (and possibly General if auto-join)
        verify(matrixAssistantService, atLeast(1)).inviteUserToRoomWithNotifications(eq("@owner:chat.neohoods.com"),
                eq("!roomId:chat.neohoods.com"), eq(false));
        // Tenant A should be invited to BatimentA (and possibly General if auto-join)
        verify(matrixAssistantService, atLeast(1)).inviteUserToRoomWithNotifications(eq("@tenant_a:chat.neohoods.com"),
                eq("!roomId:chat.neohoods.com"), eq(false));
    }

    @Test
    @DisplayName("Should send initialization summary to IT room")
    void testSendInitializationSummary() throws Exception {
        // Given
        String yamlContent = """
                rooms:
                  - name: "General"
                    description: "General discussion"
                    auto-join: true
                """;
        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());

        when(matrixAssistantService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixAssistantService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAllWithPrimaryUnit()).thenReturn(new ArrayList<>());
        when(matrixAssistantService.updateBotAvatar()).thenReturn(false);
        when(matrixAssistantService.updateBotDisplayName()).thenReturn(false);
        when(matrixAssistantService.getRoomIdByName(eq("IT"), eq(SPACE_ID))).thenReturn(Optional.of("!itRoom:chat.neohoods.com"));
        when(matrixAssistantService.sendMessage(anyString(), anyString())).thenReturn(true);

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        verify(matrixAssistantService).sendMessage(eq("!itRoom:chat.neohoods.com"), anyString());
    }

    // Helper method to create test users
    private UserEntity createTestUser(String username, String firstName, String lastName, String email,
            UserType userType, String primaryUnitName) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setType(userType);
        
        // Create a mock UnitEntity for primaryUnit
        com.neohoods.portal.platform.entities.UnitEntity primaryUnit = new com.neohoods.portal.platform.entities.UnitEntity();
        primaryUnit.setName(primaryUnitName);
        user.setPrimaryUnit(primaryUnit);
        
        return user;
    }
}

