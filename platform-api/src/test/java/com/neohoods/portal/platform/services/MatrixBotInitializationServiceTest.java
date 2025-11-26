package com.neohoods.portal.platform.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
 * Unit tests for MatrixBotInitializationService
 * 
 * These tests help validate the initialization logic and allow for quick
 * iteration during development.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Matrix Bot Initialization Service Tests")
class MatrixBotInitializationServiceTest {

    @Mock
    private MatrixBotService matrixBotService;

    @Mock
    private MatrixOAuth2Service oauth2Service;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    @InjectMocks
    private MatrixBotInitializationService initializationService;

    private static final String SPACE_ID = "!YenniyNVsUoBCLHtZS:chat.neohoods.com";
    private static final String BOT_USER_ID = "@bot:chat.neohoods.com";
    private static final String HOMESERVER_URL = "https://matrix.neohoods.com";

    @BeforeEach
    void setUp() {
        // Set up configuration values using reflection
        ReflectionTestUtils.setField(initializationService, "spaceId", SPACE_ID);
        ReflectionTestUtils.setField(initializationService, "userCreationEnabled", true);
        ReflectionTestUtils.setField(initializationService, "itRoomName", "IT");
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
        verify(matrixBotService, never()).checkSpaceExists(anyString());
        verify(matrixBotService, never()).getBotUserId();
    }

    @Test
    @DisplayName("Should skip initialization when space ID is not configured")
    void testInitializationSkippedWhenNoSpaceId() {
        // Given
        ReflectionTestUtils.setField(initializationService, "spaceId", "");

        // When
        initializationService.initializeBotManually();

        // Then
        verify(matrixBotService, never()).checkSpaceExists(anyString());
    }

    @Test
    @DisplayName("Should skip initialization when space does not exist")
    void testInitializationSkippedWhenSpaceDoesNotExist() {
        // Given
        when(matrixBotService.checkSpaceExists(SPACE_ID)).thenReturn(false);

        // When
        initializationService.initializeBotManually();

        // Then
        verify(matrixBotService).checkSpaceExists(SPACE_ID);
        verify(matrixBotService, never()).getBotUserId();
    }

    @Test
    @DisplayName("Should skip initialization when bot user ID cannot be retrieved")
    void testInitializationSkippedWhenBotUserIdNotFound() {
        // Given
        when(matrixBotService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixBotService.getBotUserId()).thenReturn(Optional.empty());

        // When
        initializationService.initializeBotManually();

        // Then
        verify(matrixBotService).checkSpaceExists(SPACE_ID);
        verify(matrixBotService).getBotUserId();
        verify(matrixBotService, never()).getExistingRoomsInSpace(anyString());
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

        when(matrixBotService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixBotService.getBotUserId()).thenReturn(Optional.of(BOT_USER_ID));
        when(matrixBotService.disableRateLimitForUser(BOT_USER_ID)).thenReturn(true);
        when(matrixBotService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAll()).thenReturn(new ArrayList<>());
        when(matrixBotService.getRoomIdByName(eq("IT"), eq(SPACE_ID))).thenReturn(Optional.of("!itRoom:chat.neohoods.com"));
        when(matrixBotService.sendMessage(anyString(), anyString())).thenReturn(true);

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

        when(matrixBotService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixBotService.getBotUserId()).thenReturn(Optional.of(BOT_USER_ID));
        when(matrixBotService.disableRateLimitForUser(BOT_USER_ID)).thenReturn(true);
        when(matrixBotService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAll()).thenReturn(new ArrayList<>());
        when(matrixBotService.createRoomInSpace(anyString(), anyString(), anyString(), eq(SPACE_ID), anyBoolean()))
                .thenReturn(Optional.of("!roomId:chat.neohoods.com"));

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        verify(matrixBotService).createRoomInSpace(eq("General"), eq("General discussion"), anyString(), eq(SPACE_ID),
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

        when(matrixBotService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixBotService.getBotUserId()).thenReturn(Optional.of(BOT_USER_ID));
        when(matrixBotService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(existingRooms);
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAll()).thenReturn(new ArrayList<>());

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        verify(matrixBotService, never()).createRoomInSpace(eq("General"), anyString(), anyString(), eq(SPACE_ID),
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

        when(matrixBotService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixBotService.getBotUserId()).thenReturn(Optional.of(BOT_USER_ID));
        when(matrixBotService.disableRateLimitForUser(BOT_USER_ID)).thenReturn(true);
        when(matrixBotService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAll()).thenReturn(Arrays.asList(user1, user2));
        when(matrixBotService.createRoomInSpace(anyString(), anyString(), anyString(), eq(SPACE_ID), anyBoolean()))
                .thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixBotService.createMatrixUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("@john_doe:chat.neohoods.com"))
                .thenReturn(Optional.of("@jane_smith:chat.neohoods.com"));
        when(matrixBotService.updateMatrixUserProfile(anyString(), any(UserEntity.class))).thenReturn(true);
        when(matrixBotService.getRoomIdByName(anyString(), eq(SPACE_ID))).thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixBotService.inviteUserToRoomWithNotifications(anyString(), anyString(), anyBoolean()))
                .thenReturn(true);
        when(matrixBotService.sendMessage(anyString(), anyString())).thenReturn(true);

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        verify(matrixBotService, times(2)).createMatrixUser(anyString(), anyString(), anyString(), anyString());
        verify(matrixBotService, times(2)).updateMatrixUserProfile(anyString(), any(UserEntity.class));
    }

    @Test
    @DisplayName("Should skip user creation when user creation is disabled")
    void testSkipUserCreationWhenDisabled() throws Exception {
        // Given
        ReflectionTestUtils.setField(initializationService, "userCreationEnabled", false);
        String yamlContent = """
                rooms:
                  - name: "General"
                    description: "General discussion"
                    auto-join: true
                """;
        InputStream inputStream = new ByteArrayInputStream(yamlContent.getBytes());

        UserEntity user = createTestUser("john.doe", "John", "Doe", "john.doe@example.com", UserType.TENANT, "A701");

        when(matrixBotService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixBotService.getBotUserId()).thenReturn(Optional.of(BOT_USER_ID));
        when(matrixBotService.disableRateLimitForUser(BOT_USER_ID)).thenReturn(true);
        when(matrixBotService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAll()).thenReturn(Arrays.asList(user));

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        verify(matrixBotService, never()).createMatrixUser(anyString(), anyString(), anyString(), anyString());
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

        when(matrixBotService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixBotService.getBotUserId()).thenReturn(Optional.of(BOT_USER_ID));
        when(matrixBotService.disableRateLimitForUser(BOT_USER_ID)).thenReturn(true);
        when(matrixBotService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAll()).thenReturn(Arrays.asList(owner, tenantA, tenantB));
        when(matrixBotService.createMatrixUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("@user:chat.neohoods.com"));
        when(matrixBotService.updateMatrixUserProfile(anyString(), any(UserEntity.class))).thenReturn(true);
        when(matrixBotService.getRoomIdByName(anyString(), eq(SPACE_ID))).thenReturn(Optional.of("!roomId:chat.neohoods.com"));
        when(matrixBotService.inviteUserToRoomWithNotifications(anyString(), anyString(), anyBoolean()))
                .thenReturn(true);

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        // Owner should be invited to Proprio room
        verify(matrixBotService).inviteUserToRoomWithNotifications(eq("@owner:chat.neohoods.com"),
                eq("!roomId:chat.neohoods.com"), eq(false));
        // Tenant A should be invited to BatimentA
        verify(matrixBotService).inviteUserToRoomWithNotifications(eq("@tenant_a:chat.neohoods.com"),
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

        when(matrixBotService.checkSpaceExists(SPACE_ID)).thenReturn(true);
        when(matrixBotService.getBotUserId()).thenReturn(Optional.of(BOT_USER_ID));
        when(matrixBotService.disableRateLimitForUser(BOT_USER_ID)).thenReturn(true);
        when(matrixBotService.getExistingRoomsInSpace(SPACE_ID)).thenReturn(new HashMap<>());
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream()).thenReturn(inputStream);
        when(usersRepository.findAll()).thenReturn(new ArrayList<>());
        when(matrixBotService.getRoomIdByName(eq("IT"), eq(SPACE_ID))).thenReturn(Optional.of("!itRoom:chat.neohoods.com"));
        when(matrixBotService.sendMessage(anyString(), anyString())).thenReturn(true);

        // When
        assertDoesNotThrow(() -> initializationService.initializeBotManually());

        // Then
        verify(matrixBotService).sendMessage(eq("!itRoom:chat.neohoods.com"), anyString());
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

