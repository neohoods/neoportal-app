package com.neohoods.portal.platform.assistant.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.assistant.model.NotificationRoutingType;
import com.neohoods.portal.platform.entities.NotificationEntity;
import com.neohoods.portal.platform.entities.NotificationType;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.matrix.space.MatrixMessageService;
import com.neohoods.portal.platform.services.matrix.space.MatrixRoomService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for routing Matrix notifications to appropriate rooms (DM or channels).
 * Determines the routing type based on notification type and sends messages accordingly.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixNotificationRouterService {

    private final MatrixMessageService matrixMessageService;
    
    @Autowired(required = false)
    private MatrixRoomService matrixRoomService;
    
    private final UsersRepository usersRepository;
    private final MessageSource messageSource;
    private final MatrixAssistantAgentContextService agentContextService;
    
    public MatrixNotificationRouterService(
            MatrixMessageService matrixMessageService,
            UsersRepository usersRepository,
            MessageSource messageSource,
            MatrixAssistantAgentContextService agentContextService) {
        this.matrixMessageService = matrixMessageService;
        this.usersRepository = usersRepository;
        this.messageSource = messageSource;
        this.agentContextService = agentContextService;
    }

    @Value("${neohoods.portal.matrix.space-id}")
    private String spaceId;

    /**
     * Sends a notification to the appropriate Matrix room
     * 
     * @param notification Notification entity
     * @param routingType Routing type
     * @param customRoomId Custom room ID (if routingType is CUSTOM_ROOM)
     */
    public void sendNotification(NotificationEntity notification, NotificationRoutingType routingType, String customRoomId) {
        if (notification == null) {
            log.warn("Cannot send notification: notification is null");
            return;
        }

        String message = buildNotificationMessage(notification);
        String targetRoomId = determineTargetRoom(notification, routingType, customRoomId);

        if (targetRoomId == null || targetRoomId.isEmpty()) {
            log.warn("Cannot send notification: no target room determined for notification type {}", notification.getType());
            return;
        }

        boolean sent = matrixMessageService.sendMessage(targetRoomId, message);
        if (sent) {
            log.info("Sent notification {} to room {} (routing: {})", notification.getType(), targetRoomId, routingType);
        } else {
            log.error("Failed to send notification {} to room {}", notification.getType(), targetRoomId);
        }
    }

    /**
     * Sends a reservation notification
     * Routes to DM if the reservation was created via agent, otherwise to DM
     * 
     * @param reservation Reservation entity
     * @param message Message to send
     */
    public void sendReservationNotification(ReservationEntity reservation, String message) {
        if (reservation == null) {
            log.warn("Cannot send reservation notification: reservation is null");
            return;
        }

        // For now, always send to DM
        // In the future, could check if reservation was created via agent and use agent context
        String matrixUserId = getMatrixUserIdForReservation(reservation);
        if (matrixUserId == null || matrixUserId.isEmpty()) {
            log.warn("Cannot send reservation notification: no Matrix user ID found for reservation {}", reservation.getId());
            return;
        }

        if (matrixRoomService == null) {
            log.warn("MatrixRoomService not available, cannot send reservation notification");
            return;
        }
        
        // Get or create DM room
        Optional<String> dmRoomId = matrixRoomService.findOrCreateDMRoom(matrixUserId);
        if (dmRoomId.isEmpty()) {
            log.warn("Cannot send reservation notification: failed to get/create DM room for user {}", matrixUserId);
            return;
        }

        boolean sent = matrixMessageService.sendMessage(dmRoomId.get(), message);
        if (sent) {
            log.info("Sent reservation notification to DM room {} for user {}", dmRoomId.get(), matrixUserId);
        } else {
            log.error("Failed to send reservation notification to DM room {}", dmRoomId.get());
        }
    }

    /**
     * Sends an announcement notification to the general room
     */
    public void sendAnnouncementNotification(String message, String roomName) {
        if (roomName == null || roomName.isEmpty()) {
            roomName = "general"; // Default room name
        }

        if (matrixRoomService == null) {
            log.warn("MatrixRoomService not available, cannot send announcement notification");
            return;
        }
        
        Optional<String> roomId = matrixRoomService.getRoomIdByName(roomName, spaceId);
        if (roomId.isEmpty()) {
            log.warn("Cannot send announcement notification: room '{}' not found in space {}", roomName, spaceId);
            return;
        }

        boolean sent = matrixMessageService.sendMessage(roomId.get(), message);
        if (sent) {
            log.info("Sent announcement notification to room {}", roomId.get());
        } else {
            log.error("Failed to send announcement notification to room {}", roomId.get());
        }
    }

    /**
     * Sends a technical notification to the IT room
     */
    public void sendTechnicalNotification(String message, NotificationType type) {
        Optional<String> itRoomId = matrixRoomService.getRoomIdByName("IT", spaceId);
        if (itRoomId.isEmpty()) {
            log.warn("Cannot send technical notification: IT room not found in space {}", spaceId);
            return;
        }

        boolean sent = matrixMessageService.sendMessage(itRoomId.get(), message);
        if (sent) {
            log.info("Sent technical notification {} to IT room {}", type, itRoomId.get());
        } else {
            log.error("Failed to send technical notification to IT room {}", itRoomId.get());
        }
    }

    /**
     * Determines the target room for a notification
     */
    private String determineTargetRoom(NotificationEntity notification, NotificationRoutingType routingType, String customRoomId) {
        if (matrixRoomService == null) {
            log.warn("MatrixRoomService not available, cannot determine target room");
            return null;
        }
        
        switch (routingType) {
            case DM:
                String matrixUserId = getMatrixUserIdForNotification(notification);
                if (matrixUserId == null || matrixUserId.isEmpty()) {
                    return null;
                }
                Optional<String> dmRoomId = matrixRoomService.findOrCreateDMRoom(matrixUserId);
                return dmRoomId.orElse(null);

            case GENERAL_ROOM:
                Optional<String> generalRoomId = matrixRoomService.getRoomIdByName("general", spaceId);
                return generalRoomId.orElse(null);

            case IT_ROOM:
                Optional<String> itRoomId = matrixRoomService.getRoomIdByName("IT", spaceId);
                return itRoomId.orElse(null);

            case UNIT_ROOM:
                // TODO: Implement unit room mapping
                log.warn("UNIT_ROOM routing not yet implemented");
                return null;

            case CUSTOM_ROOM:
                return customRoomId;

            default:
                log.warn("Unknown routing type: {}", routingType);
                return null;
        }
    }

    /**
     * Gets Matrix user ID for a notification
     */
    private String getMatrixUserIdForNotification(NotificationEntity notification) {
        if (notification.getUserId() == null) {
            return null;
        }

        return usersRepository.findById(notification.getUserId())
                .map(user -> user.getMatrixUserId())
                .orElse(null);
    }

    /**
     * Gets Matrix user ID for a reservation
     */
    private String getMatrixUserIdForReservation(ReservationEntity reservation) {
        if (reservation.getUser() == null || reservation.getUser().getId() == null) {
            return null;
        }

        return usersRepository.findById(reservation.getUser().getId())
                .map(user -> user.getMatrixUserId())
                .orElse(null);
    }

    /**
     * Builds a notification message from a notification entity
     */
    private String buildNotificationMessage(NotificationEntity notification) {
        // For now, return a simple message
        // In the future, could use templates based on notification type
        return "Notification: " + notification.getType().name();
    }
}

