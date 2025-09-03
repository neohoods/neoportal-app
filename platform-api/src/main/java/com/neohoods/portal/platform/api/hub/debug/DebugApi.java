package com.neohoods.portal.platform.api.hub.debug;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.DebugApiApiDelegate;
import com.neohoods.portal.platform.entities.NotificationEntity;
import com.neohoods.portal.platform.entities.NotificationType;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.model.DebugPostNotificationRequest;
import com.neohoods.portal.platform.model.Notification;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.NotificationsService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class DebugApi implements DebugApiApiDelegate {

    private final UsersRepository usersRepository;
    private final NotificationsService notificationService;

    @Override
    public Mono<ResponseEntity<Notification>> debugPostNotification(
            Mono<DebugPostNotificationRequest> debugPostNotificationRequest,
            ServerWebExchange exchange) {
        return debugPostNotificationRequest
                .map(request -> {
                    try {
                        UserEntity user = usersRepository.findById(request.getUserId())
                                .orElseThrow(() -> new IllegalArgumentException("User not found"));
                        Notification notification = request.getNotification();

                        // Set default values if not provided
                        if (notification.getAuthor() == null) {
                            notification.setAuthor("debug");
                        }
                        if (notification.getAlreadyRead() == null) {
                            notification.setAlreadyRead(false);
                        }
                        if (notification.getDate() == null) {
                            notification.setDate(OffsetDateTime.now(ZoneOffset.UTC));
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = (Map<String, Object>) notification.getPayload();

                        NotificationEntity notificationEntity = NotificationEntity.builder()
                                .author(notification.getAuthor())
                                .date(notification.getDate().toInstant())
                                .type(NotificationType.valueOf(notification.getType().name()))
                                .alreadyRead(notification.getAlreadyRead())
                                .payload(payload)
                                .build();

                        Locale locale = request.getLocale() != null ?  Locale.of(request.getLocale()) : user.getLocale();
                        notificationService.sendNotifications(user, notificationEntity, locale);

                        return notificationEntity.toNotification().build();
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to process notification", e);
                    }
                })
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(Exception.class,
                        e -> Mono.just(ResponseEntity.badRequest().build()));
    }
}
