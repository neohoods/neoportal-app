package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContextService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantService;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixAssistantAuthContextService Unit Tests")
class MatrixAssistantAuthContextServiceTest {

    @Mock
    private MatrixAssistantService matrixAssistantService;

    @Mock
    private UsersRepository usersRepository;

    @InjectMocks
    private MatrixAssistantAuthContextService authContextService;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
    }

    @Test
    @DisplayName("createAuthContext should create context with user entity when found")
    void testCreateAuthContext_UserFound() {
        // Given
        String matrixUserId = "@testuser:chat.neohoods.com";
        when(usersRepository.findByUsername("testuser")).thenReturn(testUser);

        // When
        MatrixAssistantAuthContext context = authContextService.createAuthContext(
                matrixUserId, "!room:chat.neohoods.com", false);

        // Then
        assertNotNull(context);
        assertEquals(matrixUserId, context.getMatrixUserId());
        assertTrue(context.getUserEntity().isPresent());
        assertEquals(testUser, context.getUserEntity().get());
    }

    @Test
    @DisplayName("createAuthContext should create context without user entity when not found")
    void testCreateAuthContext_UserNotFound() {
        // Given
        String matrixUserId = "@unknownuser:chat.neohoods.com";
        when(usersRepository.findByUsername("unknownuser")).thenReturn(null);

        // When
        MatrixAssistantAuthContext context = authContextService.createAuthContext(
                matrixUserId, "!room:chat.neohoods.com", false);

        // Then
        assertNotNull(context);
        assertEquals(matrixUserId, context.getMatrixUserId());
        assertFalse(context.getUserEntity().isPresent());
    }

    @Test
    @DisplayName("createAuthContext should handle null matrix user ID")
    void testCreateAuthContext_NullUserId() {
        // When
        MatrixAssistantAuthContext context = authContextService.createAuthContext(
                null, "!room:chat.neohoods.com", false);

        // Then
        assertNotNull(context);
        assertFalse(context.getUserEntity().isPresent());
    }

    @Test
    @DisplayName("createAuthContext should set isDirectMessage correctly")
    void testCreateAuthContext_IsDirectMessage() {
        // Given
        String matrixUserId = "@testuser:chat.neohoods.com";
        when(usersRepository.findByUsername("testuser")).thenReturn(testUser);

        // When
        MatrixAssistantAuthContext context = authContextService.createAuthContext(
                matrixUserId, "!room:chat.neohoods.com", true);

        // Then
        assertTrue(context.isDirectMessage());
    }

    @Test
    @DisplayName("createAuthContext should normalize username")
    void testCreateAuthContext_NormalizesUsername() {
        // Given
        String matrixUserId = "@Test-User.123:chat.neohoods.com";
        when(usersRepository.findByUsername("test_user_123")).thenReturn(testUser);

        // When
        MatrixAssistantAuthContext context = authContextService.createAuthContext(
                matrixUserId, "!room:chat.neohoods.com", false);

        // Then
        assertNotNull(context);
        verify(usersRepository).findByUsername("test_user_123");
    }
}

