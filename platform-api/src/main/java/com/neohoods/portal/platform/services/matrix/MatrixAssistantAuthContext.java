package com.neohoods.portal.platform.services.matrix;

import java.util.Optional;

import com.neohoods.portal.platform.entities.UserEntity;

import lombok.Builder;
import lombok.Getter;

/**
 * Contexte d'autorisation pour l'assistant Alfred Matrix.
 * Encapsule les informations nécessaires pour valider les permissions
 * et déterminer la visibilité des réponses.
 */
@Getter
@Builder
public class MatrixAssistantAuthContext {
    
    /**
     * Matrix user ID du sender (ex: @user:chat.neohoods.com)
     */
    private final String matrixUserId;
    
    /**
     * Room ID Matrix où le message a été envoyé
     */
    private final String roomId;
    
    /**
     * true si c'est un DM (Direct Message), false si c'est une room publique
     */
    private final boolean isDirectMessage;
    
    /**
     * UserEntity correspondant au Matrix user ID (si trouvé)
     */
    private final Optional<UserEntity> userEntity;
    
    /**
     * true si l'utilisateur est authentifié (DM + userEntity présent)
     */
    public boolean isAuthenticated() {
        return isDirectMessage && userEntity.isPresent();
    }
    
    /**
     * true si la réponse doit être publique (visible par tous dans la room)
     */
    public boolean isPublicResponse() {
        return !isDirectMessage;
    }
    
    /**
     * true si la réponse doit être privée (DM uniquement)
     */
    public boolean isPrivateResponse() {
        return isDirectMessage;
    }
    
    /**
     * Récupère le UserEntity ou lance une exception si non authentifié
     * 
     * @return UserEntity
     * @throws UnauthorizedException si l'utilisateur n'est pas authentifié
     */
    public UserEntity getAuthenticatedUser() {
        if (!isAuthenticated()) {
            throw new UnauthorizedException("Action requires authentication (DM only)");
        }
        return userEntity.orElseThrow(() -> new UnauthorizedException("User not found"));
    }
    
    /**
     * Exception levée quand une action nécessite une authentification
     */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}












