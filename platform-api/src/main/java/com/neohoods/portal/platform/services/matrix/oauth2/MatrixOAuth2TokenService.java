package com.neohoods.portal.platform.services.matrix.oauth2;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.MatrixBotTokenEntity;
import com.neohoods.portal.platform.repositories.MatrixBotTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing OAuth2 tokens (access tokens, refresh tokens).
 * Handles token storage, retrieval, and validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatrixOAuth2TokenService {

    private final MatrixBotTokenRepository tokenRepository;

    /**
     * Get current access token from database
     */
    public Optional<String> getAccessToken() {
        Optional<MatrixBotTokenEntity> tokenOpt = tokenRepository.findFirstByOrderByCreatedAtDesc();
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        MatrixBotTokenEntity tokenEntity = tokenOpt.get();
        if (tokenEntity.getAccessToken() != null &&
                (tokenEntity.getExpiresAt() == null || tokenEntity.getExpiresAt().isAfter(OffsetDateTime.now()))) {
            return Optional.of(tokenEntity.getAccessToken());
        }

        return Optional.empty();
    }

    /**
     * Get current access token from database
     * Note: Refresh logic is handled by MatrixOAuth2Service
     */
    public Optional<String> getUserAccessToken() {
        return getAccessToken();
    }

    /**
     * Check if refresh token exists
     */
    public boolean hasRefreshToken() {
        return tokenRepository.findFirstByOrderByCreatedAtDesc()
                .map(token -> token.getRefreshToken() != null)
                .orElse(false);
    }

    /**
     * Check if access token exists and is valid
     */
    public boolean hasAccessToken() {
        Optional<MatrixBotTokenEntity> tokenOpt = tokenRepository.findFirstByOrderByCreatedAtDesc();
        if (tokenOpt.isEmpty()) {
            return false;
        }
        MatrixBotTokenEntity tokenEntity = tokenOpt.get();
        return tokenEntity.getAccessToken() != null &&
                (tokenEntity.getExpiresAt() == null || tokenEntity.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    /**
     * Save tokens to database
     */
    public void saveTokens(String accessToken, String refreshToken, Integer expiresIn) {
        Optional<MatrixBotTokenEntity> existingOpt = tokenRepository.findFirstByOrderByCreatedAtDesc();
        MatrixBotTokenEntity tokenEntity;

        if (existingOpt.isPresent()) {
            tokenEntity = existingOpt.get();
            tokenEntity.setAccessToken(accessToken);
            tokenEntity.setRefreshToken(refreshToken);
            if (expiresIn != null) {
                tokenEntity.setExpiresAt(OffsetDateTime.now().plusSeconds(expiresIn));
            }
        } else {
            tokenEntity = MatrixBotTokenEntity.builder()
                    .id(UUID.randomUUID())
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresAt(expiresIn != null ? OffsetDateTime.now().plusSeconds(expiresIn) : null)
                    .build();
        }

        tokenRepository.save(tokenEntity);
    }

    /**
     * Get the latest token entity
     */
    public Optional<MatrixBotTokenEntity> getLatestToken() {
        return tokenRepository.findFirstByOrderByCreatedAtDesc();
    }
}

