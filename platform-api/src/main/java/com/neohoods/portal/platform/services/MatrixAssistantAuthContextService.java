package com.neohoods.portal.platform.services;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service pour créer et gérer les contextes d'autorisation Matrix.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatrixAssistantAuthContextService {

    private final MatrixAssistantService matrixAssistantService;
    private final UsersRepository usersRepository;

    /**
     * Crée un contexte d'autorisation depuis les informations Matrix
     */
    public MatrixAssistantAuthContext createAuthContext(String matrixUserId, String roomId, boolean isDirectMessage) {
        // Mapper Matrix User ID → UserEntity
        Optional<UserEntity> userEntity = Optional.empty();

        if (matrixUserId != null && !matrixUserId.isEmpty()) {
            // Extraire le username du Matrix user ID (format: @username:server.com)
            String matrixUsername = extractUsernameFromMatrixUserId(matrixUserId);
            if (matrixUsername != null) {
                // Le username Matrix peut être différent du username dans la DB
                // (normalisé: lowercase, caractères spéciaux remplacés par _)
                // Chercher dans la DB avec le username normalisé
                String normalizedUsername = matrixUsername.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                UserEntity foundUser = usersRepository.findByUsername(normalizedUsername);
                userEntity = foundUser != null ? Optional.of(foundUser) : Optional.empty();

                if (userEntity.isEmpty()) {
                    log.debug("User not found by username: {}, trying to find via Matrix", normalizedUsername);
                    // TODO: Améliorer en utilisant MatrixAssistantService pour trouver via MAS/Auth0
                    // Pour l'instant, on retourne empty si pas trouvé
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
     * Extrait le username d'un Matrix user ID
     * 
     * @param matrixUserId Format: @username:server.com
     * @return username ou null
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
