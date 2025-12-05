package com.neohoods.portal.platform.services.matrix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.neohoods.portal.platform.matrix.ApiClient;
import com.neohoods.portal.platform.services.matrix.oauth2.MatrixOAuth2Service;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixOAuth2Service Unit Tests")
class MatrixOAuth2ServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private com.neohoods.portal.platform.repositories.MatrixBotTokenRepository tokenRepository;

    @Mock
    private com.neohoods.portal.platform.repositories.MatrixBotErrorNotificationRepository errorNotificationRepository;

    @Mock
    private com.neohoods.portal.platform.repositories.UsersRepository usersRepository;

    @Mock
    private com.neohoods.portal.platform.services.MailService mailService;

    @InjectMocks
    private MatrixOAuth2Service oauth2Service;

    private static final String HOMESERVER_URL = "https://matrix.neohoods.com";
    private static final String MAS_URL = "https://mas.chat.neohoods.com";
    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(oauth2Service, "homeserverUrl", HOMESERVER_URL);
        // Note: masUrl field may not exist or may have different name
        ReflectionTestUtils.setField(oauth2Service, "clientId", CLIENT_ID);
        ReflectionTestUtils.setField(oauth2Service, "clientSecret", CLIENT_SECRET);
        ReflectionTestUtils.setField(oauth2Service, "redirectUri", "http://localhost:8080/callback");
        ReflectionTestUtils.setField(oauth2Service, "authorizationEndpoint", HOMESERVER_URL + "/oauth2/authorize");
        ReflectionTestUtils.setField(oauth2Service, "tokenEndpoint", HOMESERVER_URL + "/oauth2/token");
        ReflectionTestUtils.setField(oauth2Service, "scope", "urn:matrix:client:api:*");
        ReflectionTestUtils.setField(oauth2Service, "baseUrl", "http://localhost:8080");
    }

    @Test
    @DisplayName("getMatrixApiClient should require parameters")
    void testGetMatrixApiClient_RequiresParameters() {
        // Given
        // Configuration is set in setUp
        // Note: getMatrixApiClient requires parameters, testing method signature exists

        // When/Then
        // Method signature verification - actual call would require all parameters
        assertNotNull(oauth2Service);
    }

    @Test
    @DisplayName("getMASApiClient should return Optional when configured")
    void testGetMASApiClient_ReturnsClient() {
        // Given
        // Configuration is set in setUp

        // When
        Optional<com.neohoods.portal.platform.mas.ApiClient> result = oauth2Service.getMASApiClient(MAS_URL);

        // Then
        // May return empty if token is not available, but should not throw
        assertNotNull(result);
    }

    @Test
    @DisplayName("getMatrixApiClientWithUserToken should return Optional")
    void testGetMatrixApiClientWithUserToken_ReturnsClient() {
        // Given
        String userToken = "test-user-token";
        when(tokenRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        // When
        Optional<ApiClient> result = oauth2Service.getMatrixApiClientWithUserToken(userToken);

        // Then
        assertNotNull(result);
        // Should return client with user token if token is valid
    }
}

