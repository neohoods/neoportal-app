package com.neohoods.portal.platform.assistant.model;

import com.neohoods.portal.platform.entities.UserEntity;

import lombok.Builder;
import lombok.Getter;

/**
 * Authorization context for the Alfred Matrix assistant.
 * Encapsulates the information needed to validate permissions
 * and determine response visibility.
 */
@Getter
@Builder
public class MatrixAssistantAuthContext {

    /**
     * Matrix user ID of the sender (e.g., @user:chat.neohoods.com)
     */
    private final String matrixUserId;

    /**
     * Matrix room ID where the message was sent
     */
    private final String roomId;

    /**
     * true if this is a DM (Direct Message), false if it's a public room
     */
    private final boolean isDirectMessage;

    /**
     * UserEntity corresponding to the Matrix user ID (if found)
     */
    private final UserEntity userEntity;

    /**
     * true if user is attached in context
     */
    public boolean hasUser() {
        return userEntity != null;
    }

    /**
     * Backward-compatible access to user as Optional
     */
    public java.util.Optional<UserEntity> getUserEntity() {
        return java.util.Optional.ofNullable(userEntity);
    }

    /**
     * true if the response should be public (visible to everyone in the room)
     */
    public boolean isPublicResponse() {
        return !isDirectMessage;
    }

    /**
     * true if the conversation is public (public room, not a DM)
     */
    public boolean isConversationPublic() {
        return !isDirectMessage;
    }

    /**
     * true if the response should be private (DM only)
     */
    public boolean isPrivateResponse() {
        return isDirectMessage;
    }

    /**
     * Gets the UserEntity or throws an exception if not authenticated to the portal
     * 
     * @return UserEntity
     * @throws UnauthorizedException if the user is not authenticated to the portal
     */
    public UserEntity getAuthenticatedUser() {
        if (userEntity == null) {
            throw new UnauthorizedException("Action requires portal authentication");
        }
        return userEntity;
    }

    /**
     * Exception thrown when an action requires authentication
     */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
