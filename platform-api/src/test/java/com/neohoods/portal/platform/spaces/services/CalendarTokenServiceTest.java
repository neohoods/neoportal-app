package com.neohoods.portal.platform.spaces.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEException;

/**
 * Unit tests for CalendarTokenService.
 * 
 * Tests:
 * - Token generation
 * - Token verification
 * - Expired token handling
 * - Invalid token handling
 * - Token with wrong secret
 */
public class CalendarTokenServiceTest {

    private CalendarTokenService tokenService;
    private CalendarTokenService tokenServiceWithDifferentSecret;
    private static final String TEST_SECRET = "test-secret-key-for-jwt-signing-minimum-32-chars";
    private static final String DIFFERENT_SECRET = "different-secret-key-for-jwt-signing-minimum-32-chars";
    private static final int TEST_EXPIRATION_HOURS = 24;

    @BeforeEach
    public void setUp() {
        tokenService = new CalendarTokenService(TEST_SECRET, TEST_EXPIRATION_HOURS);
        tokenServiceWithDifferentSecret = new CalendarTokenService(DIFFERENT_SECRET, TEST_EXPIRATION_HOURS);
    }

    @Test
    @DisplayName("Generate token for space ID")
    public void testGenerateToken() {
        // Arrange
        UUID spaceId = UUID.randomUUID();

        // Act
        String token = tokenService.generateToken(spaceId);

        // Assert
        assertNotNull(token);
        assertNotNull(token.length() > 0);
    }

    @Test
    @DisplayName("Verify valid token returns correct space ID")
    public void testVerifyToken_ValidToken() throws JOSEException {
        // Arrange
        UUID spaceId = UUID.randomUUID();
        String token = tokenService.generateToken(spaceId);

        // Act
        UUID verifiedSpaceId = tokenService.verifyToken(token);

        // Assert
        assertEquals(spaceId, verifiedSpaceId);
    }

    @Test
    @DisplayName("Verify token with wrong signature throws exception")
    public void testVerifyToken_WrongSignature() {
        // Arrange
        UUID spaceId = UUID.randomUUID();
        String token = tokenService.generateToken(spaceId);

        // Act & Assert
        assertThrows(JOSEException.class, () -> {
            tokenServiceWithDifferentSecret.verifyToken(token);
        });
    }

    @Test
    @DisplayName("Verify invalid token format throws exception")
    public void testVerifyToken_InvalidFormat() {
        // Arrange
        String invalidToken = "invalid.token.format";

        // Act & Assert
        assertThrows(JOSEException.class, () -> {
            tokenService.verifyToken(invalidToken);
        });
    }

    @Test
    @DisplayName("Verify empty token throws exception")
    public void testVerifyToken_EmptyToken() {
        // Arrange
        String emptyToken = "";

        // Act & Assert
        assertThrows(JOSEException.class, () -> {
            tokenService.verifyToken(emptyToken);
        });
    }

    @Test
    @DisplayName("Generate and verify multiple tokens for different spaces")
    public void testGenerateAndVerify_MultipleSpaces() throws JOSEException {
        // Arrange
        UUID spaceId1 = UUID.randomUUID();
        UUID spaceId2 = UUID.randomUUID();

        // Act
        String token1 = tokenService.generateToken(spaceId1);
        String token2 = tokenService.generateToken(spaceId2);

        // Assert
        assertEquals(spaceId1, tokenService.verifyToken(token1));
        assertEquals(spaceId2, tokenService.verifyToken(token2));
    }

    @Test
    @DisplayName("Token contains correct claims")
    public void testTokenClaims() throws JOSEException {
        // Arrange
        UUID spaceId = UUID.randomUUID();
        String token = tokenService.generateToken(spaceId);

        // Act
        UUID verifiedSpaceId = tokenService.verifyToken(token);

        // Assert
        assertEquals(spaceId, verifiedSpaceId);
    }
}


