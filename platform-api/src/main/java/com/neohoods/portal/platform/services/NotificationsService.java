package com.neohoods.portal.platform.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.NotificationEntity;
import com.neohoods.portal.platform.entities.NotificationType;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.model.GetUnreadNotificationsCount200Response;
import com.neohoods.portal.platform.model.Notification;
import com.neohoods.portal.platform.repositories.NotificationRepository;
import com.neohoods.portal.platform.services.MailService.TemplateVariable;
import com.neohoods.portal.platform.services.MailService.TemplateVariableType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationsService {

    public static final String PLATFORM_AUTHOR = "Platform";
    private final NotificationRepository notificationRepository;
    private final MailService mailService;
    private final MessageSource messageSource;

    @Value("${neohoods.portal.frontend-url}")
    private String frontendUrl;

    public Mono<Void> acknowledgeNotifications(UUID userId, Flux<Notification> notifications) {
        return notifications.collectList()
                .flatMap(notifList -> {
                    try {
                        notifList.forEach(notification -> {
                            NotificationEntity entity = notificationRepository
                                    .findById(notification.getId())
                                    .orElseThrow(() -> new CodedException(
                                            CodedError.INTERNAL_ERROR.getCode(),
                                            "Notification not found",
                                            Map.of("notificationId", notification.getId()),
                                            CodedError.INTERNAL_ERROR.getDocumentationUrl()));
                            entity.setAlreadyRead(true);
                            notificationRepository.save(entity);
                            log.debug("Marked notification {} as read", notification.getId());
                        });
                        log.info("Successfully acknowledged {} notifications", notifList.size());
                        return Mono.empty();
                    } catch (Exception e) {
                        log.error("Failed to acknowledge notifications", e);
                        if (e instanceof CodedException) {
                            return Mono.error(e);
                        }
                        return Mono.error(new CodedException(
                                CodedError.INTERNAL_ERROR.getCode(),
                                "Failed to acknowledge notifications",
                                Map.of("userId", userId, "error", e.getMessage()),
                                CodedError.INTERNAL_ERROR.getDocumentationUrl(),
                                e));
                    }
                });
    }

    public Flux<Notification> getNotifications(UUID userId) {
        log.info("Retrieving notifications for user {}", userId);
        try {
            return Flux.fromIterable(notificationRepository.findAllByUserId(userId))
                    .flatMap(entity -> {
                        // Fetch the image with order = 0
                        Notification notification = entity.toNotification().build();
                        return Mono.just(notification);

                    });
        } catch (Exception e) {
            log.error("Failed to retrieve notifications", e);
            return Flux.error(new CodedException(
                    CodedError.INTERNAL_ERROR.getCode(),
                    "Failed to retrieve notifications",
                    Map.of("userId", userId, "error", e.getMessage()),
                    CodedError.INTERNAL_ERROR.getDocumentationUrl(),
                    e));
        }
    }

    public Mono<GetUnreadNotificationsCount200Response> getUnreadNotificationsCount(UUID userId) {
        log.info("Getting unread notifications count for user {}", userId);
        try {
            long count = notificationRepository.countByUserIdAndAlreadyReadFalse(userId);
            return Mono.just(new GetUnreadNotificationsCount200Response().count((int) count));
        } catch (Exception e) {
            log.error("Failed to get unread notifications count", e);
            return Mono.error(new CodedException(
                    CodedError.INTERNAL_ERROR.getCode(),
                    "Failed to get unread notifications count",
                    Map.of("userId", userId, "error", e.getMessage()),
                    CodedError.INTERNAL_ERROR.getDocumentationUrl(),
                    e));
        }
    }

    public Mono<Void> sendNotifications(UserEntity user, NotificationEntity notification) {
        return this.sendNotifications(user, notification, user.getLocale());
    }

    public Mono<Void> sendNotifications(UserEntity user, NotificationEntity notification, Locale locale) {
        try {
            notification.setId(UUID.randomUUID());
            notification.setUserId(user.getId());
            notificationRepository.save(notification);

            // Create template variables for the email
            List<TemplateVariable> templateVariables = getTemplateVariables(notification.getType(), notification);

            templateVariables.add(
                    TemplateVariable.builder()
                            .type(TemplateVariableType.RAW)
                            .ref("username")
                            .value(user.getUsername())
                            .build());

            mailService.sendTemplatedEmail(
                    user,
                    "notification." + notification.getType().name().toLowerCase() + ".email.title",
                    "email/" + notification.getType().getEmailTemplate(),
                    templateVariables,
                    locale);
            log.info("Successfully send {} notification", notification);
            return Mono.empty();
        } catch (Exception e) {
            log.error("Failed to send notification", e);
            return Mono.error(new CodedException(
                    CodedError.INTERNAL_ERROR.getCode(),
                    "Failed to send notification",
                    Map.of("userId", user.getId(), "error", e.getMessage()),
                    CodedError.INTERNAL_ERROR.getDocumentationUrl(),
                    e));
        }
    }

    private Object[] getMessageArgs(NotificationEntity entity) {
        // Extract arguments from the notification payload based on type
        switch (entity.getType()) {
            default:
                return new Object[] {};
        }
    }

    private List<TemplateVariable> getTemplateVariables(NotificationType type, NotificationEntity notification) {
        List<TemplateVariable> variables = new ArrayList<>();
        return variables;
    }
}