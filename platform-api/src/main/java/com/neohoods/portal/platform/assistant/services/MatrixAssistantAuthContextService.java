package com.neohoods.portal.platform.assistant.services;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
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
        UserEntity userEntity = resolveUser(matrixUserId);

        return MatrixAssistantAuthContext.builder()
                .matrixUserId(matrixUserId)
                .roomId(roomId)
                .isDirectMessage(isDirectMessage)
                .userEntity(userEntity)
                .build();
    }

    /**
     * Creates auth context from an already resolved user (e.g., decoded JWT)
     */
    public MatrixAssistantAuthContext createAuthContextFromUser(UserEntity userEntity, String matrixUserId,
            String roomId, boolean isDirectMessage) {
        if (userEntity == null) {
            throw new MatrixAssistantAuthContext.UnauthorizedException("User not found");
        }
        return MatrixAssistantAuthContext.builder()
                .matrixUserId(matrixUserId)
                .roomId(roomId)
                .isDirectMessage(isDirectMessage)
                .userEntity(userEntity)
                .build();
    }

    private UserEntity resolveUser(String matrixUserId) {
        if (matrixUserId == null || matrixUserId.isEmpty()) {
            throw new MatrixAssistantAuthContext.UnauthorizedException("Matrix user ID is required");
        }

        // Find user directly by matrix_user_id column
        UserEntity foundUser = usersRepository.findByMatrixUserId(matrixUserId);
        if (foundUser == null) {
            log.warn("User not found in local database for Matrix user ID: {}. " +
                    "The user may need to be synchronized from Matrix to the local database. " +
                    "Check if the Matrix user sync bot has run successfully.",
                    matrixUserId);
            throw new MatrixAssistantAuthContext.UnauthorizedException(
                    String.format("User not found in local database for Matrix user: %s. " +
                            "The user may need to be synchronized. Please contact an administrator.",
                            matrixUserId));
        }

        return foundUser;
    }
}
