package com.neohoods.portal.platform.services.matrix.assistant;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for creating and managing Matrix authorization contexts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatrixAssistantAuthContextService {

    private final MatrixAssistantService matrixAssistantService;
    private final UsersRepository usersRepository;

    /**
     * Creates an authorization context from Matrix information
     */
    public MatrixAssistantAuthContext createAuthContext(String matrixUserId, String roomId, boolean isDirectMessage) {
        // Map Matrix User ID → UserEntity
        Optional<UserEntity> userEntity = Optional.empty();

        if (matrixUserId != null && !matrixUserId.isEmpty()) {
            // Extract username from Matrix user ID (format: @username:server.com)
            String matrixUsername = extractUsernameFromMatrixUserId(matrixUserId);
            if (matrixUsername != null) {
                // Matrix username may differ from DB username
                // (normalized: lowercase, special chars replaced by _)
                // Search in DB with normalized username
                String normalizedUsername = matrixUsername.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                UserEntity foundUser = usersRepository.findByUsername(normalizedUsername);
                userEntity = foundUser != null ? Optional.of(foundUser) : Optional.empty();

                if (userEntity.isEmpty()) {
                    log.debug("User not found by username: {}, trying to find via Matrix", normalizedUsername);
                    // Future improvement: Use MatrixAssistantService to find via MAS/Auth0 for better user lookup
                    // For now, return empty if not found
                }
            }
        }

        return MatrixAssistantAuthContext.builder()
                .matrixUserId(matrixUserId)
                .roomId(roomId)
                .isDirectMessage(isDirectMessage)
                .userEntity(userEntity)
                .build();
    }

    /**
     * Extracts username from a Matrix user ID
     * 
     * @param matrixUserId Format: @username:server.com
     * @return username or null
     */
    private String extractUsernameFromMatrixUserId(String matrixUserId) {
        if (matrixUserId == null || !matrixUserId.startsWith("@")) {
            return null;
        }
        int colonIndex = matrixUserId.indexOf(':');
        if (colonIndex > 0) {
            return matrixUserId.substring(1, colonIndex);
        }
        return matrixUserId.substring(1);
    }
}
