package com.neohoods.portal.platform.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.NotificationEntity;
import com.neohoods.portal.platform.entities.NotificationSettingsEntity;
import com.neohoods.portal.platform.entities.NotificationType;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.model.GetUnreadNotificationsCount200Response;
import com.neohoods.portal.platform.model.Notification;
import com.neohoods.portal.platform.repositories.NotificationRepository;
import com.neohoods.portal.platform.repositories.NotificationSettingsRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
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
    private final UsersRepository usersRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
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
            log.error("Failed to send notification to user: {}. " +
                    "Notification will be skipped, but user operations will continue.",
                    user.getUsername(), e);
            return Mono.empty(); // Don't fail for notification errors
        }
    }

    /**
     * Send notification to all users when a new announcement is created
     */
    public Mono<Void> notifyUsersNewAnnouncement(
            com.neohoods.portal.platform.entities.AnnouncementEntity announcement) {
        log.info("Notifying all users about new announcement: {}", announcement.getTitle());

        try {
            // Find all users
            List<UserEntity> allUsers = usersRepository.findAllWithProperties();

            if (allUsers.isEmpty()) {
                log.warn("No users found to notify about new announcement");
                return Mono.empty();
            }

            // Filter users by notification preferences (optimized to avoid N+1 queries)
            List<UUID> userIds = allUsers.stream().map(UserEntity::getId).collect(Collectors.toList());

            // Get all notification settings for these users in one query
            List<NotificationSettingsEntity> allSettings = notificationSettingsRepository.findByUserIds(userIds);
            Map<UUID, Boolean> userNotificationSettings = allSettings.stream()
                    .collect(Collectors.toMap(
                            settings -> settings.getUser().getId(),
                            NotificationSettingsEntity::isEnableNotifications));

            // Users without settings are considered to have notifications enabled by
            // default
            List<UserEntity> usersWithNotificationsEnabled = allUsers.stream()
                    .filter(user -> userNotificationSettings.getOrDefault(user.getId(), true))
                    .collect(Collectors.toList());

            log.info("Filtered {} users with notifications enabled out of {} total users",
                    usersWithNotificationsEnabled.size(), allUsers.size());

            if (usersWithNotificationsEnabled.isEmpty()) {
                log.warn("No users with notifications enabled found for announcement: {}", announcement.getTitle());
                return Mono.empty();
            }

            // Create notification payload
            Map<String, Object> payload = Map.of(
                    "announcementId", announcement.getId().toString(),
                    "announcementTitle", announcement.getTitle(),
                    "announcementContent", announcement.getContent(),
                    "announcementCategory", announcement.getCategory().toString(),
                    "announcementDate", announcement.getCreatedAt().toString());

            // Create notification entity
            NotificationEntity notification = NotificationEntity.builder()
                    .type(NotificationType.NEW_ANNOUNCEMENT)
                    .author(PLATFORM_AUTHOR)
                    .date(java.time.Instant.now())
                    .alreadyRead(false)
                    .payload(payload)
                    .build();

            // Send notification to each user with notifications enabled
            return Flux.fromIterable(usersWithNotificationsEnabled)
                    .flatMap(user -> sendNotifications(user, notification)
                            .onErrorResume(error -> {
                                log.error(
                                        "Failed to send announcement notification to user: {} for announcement: {}. " +
                                                "Announcement creation will continue, but user notification failed.",
                                        user.getUsername(), announcement.getTitle(), error);
                                return Mono.empty(); // Continue with other users
                            }))
                    .then();

        } catch (Exception e) {
            log.error("Failed to notify users about new announcement: {}. " +
                    "Announcement creation will continue, but user notification failed.", announcement.getTitle(), e);
            return Mono.empty(); // Don't fail announcement creation for notification errors
        }
    }

    /**
     * Send notification to all admin users when a new user registers
     */
    public Mono<Void> notifyAdminsNewUser(UserEntity newUser) {
        log.info("Notifying admins about new user registration: {}", newUser.getUsername());

        try {
            // Find all admin users
            List<UserEntity> adminUsers = usersRepository.findByType(UserType.ADMIN);

            if (adminUsers.isEmpty()) {
                log.warn("No admin users found to notify about new user registration");
                return Mono.empty();
            }

            // Filter admin users by notification preferences (optimized to avoid N+1
            // queries)
            List<UUID> adminUserIds = adminUsers.stream().map(UserEntity::getId).collect(Collectors.toList());

            // Get all notification settings for these admins in one query
            List<NotificationSettingsEntity> adminSettings = notificationSettingsRepository.findByUserIds(adminUserIds);
            Map<UUID, Boolean> adminNotificationSettings = adminSettings.stream()
                    .collect(Collectors.toMap(
                            settings -> settings.getUser().getId(),
                            NotificationSettingsEntity::isEnableNotifications));

            // Admins without settings are considered to have notifications enabled by
            // default
            List<UserEntity> adminsWithNotificationsEnabled = adminUsers.stream()
                    .filter(admin -> adminNotificationSettings.getOrDefault(admin.getId(), true))
                    .collect(Collectors.toList());

            log.info("Filtered {} admins with notifications enabled out of {} total admins",
                    adminsWithNotificationsEnabled.size(), adminUsers.size());

            if (adminsWithNotificationsEnabled.isEmpty()) {
                log.warn("No admins with notifications enabled found for new user: {}", newUser.getUsername());
                return Mono.empty();
            }

            // Create notification payload
            Map<String, Object> payload = Map.of(
                    "newUserUsername", newUser.getUsername(),
                    "newUserEmail", newUser.getEmail(),
                    "newUserType", newUser.getType().toString(),
                    "newUserFirstName", newUser.getFirstName() != null ? newUser.getFirstName() : "",
                    "newUserLastName", newUser.getLastName() != null ? newUser.getLastName() : "");

            // Create notification entity
            NotificationEntity notification = NotificationEntity.builder()
                    .type(NotificationType.ADMIN_NEW_USER)
                    .author(PLATFORM_AUTHOR)
                    .date(java.time.Instant.now())
                    .alreadyRead(false)
                    .payload(payload)
                    .build();

            // Send notification to each admin with notifications enabled
            return Flux.fromIterable(adminsWithNotificationsEnabled)
                    .flatMap(admin -> sendNotifications(admin, notification)
                            .onErrorResume(error -> {
                                log.error("Failed to send notification to admin: {} for new user: {}. " +
                                        "User signup will continue, but admin notification failed.",
                                        admin.getUsername(), newUser.getUsername(), error);
                                return Mono.empty(); // Continue with other admins
                            }))
                    .then();

        } catch (Exception e) {
            log.error("Failed to notify admins about new user registration: {}. " +
                    "User signup will continue, but admin notification failed.", newUser.getUsername(), e);
            return Mono.empty(); // Don't fail signup for notification errors
        }
    }

    private List<TemplateVariable> getTemplateVariables(NotificationType type, NotificationEntity notification) {
        List<TemplateVariable> variables = new ArrayList<>();

        switch (type) {
            case NEW_ANNOUNCEMENT:
                // Add variables for new announcement notification
                if (notification.getPayload() != null) {
                    Map<String, Object> payload = notification.getPayload();

                    // Announcement ID
                    if (payload.containsKey("announcementId")) {
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("announcementId")
                                .value(payload.get("announcementId").toString())
                                .build());
                    }

                    // Announcement title
                    if (payload.containsKey("announcementTitle")) {
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("announcementTitle")
                                .value(payload.get("announcementTitle").toString())
                                .build());
                    }

                    // Announcement content (truncated for email)
                    if (payload.containsKey("announcementContent")) {
                        String content = payload.get("announcementContent").toString();
                        String truncatedContent = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("announcementContent")
                                .value(truncatedContent)
                                .build());
                    }

                    // Announcement category
                    if (payload.containsKey("announcementCategory")) {
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("announcementCategory")
                                .value(payload.get("announcementCategory").toString())
                                .build());
                    }

                    // Announcement date
                    if (payload.containsKey("announcementDate")) {
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("announcementDate")
                                .value(payload.get("announcementDate").toString())
                                .build());
                    }

                    // URL to view announcements (redirect to home page)
                    variables.add(TemplateVariable.builder()
                            .type(TemplateVariableType.RAW)
                            .ref("appUrl")
                            .value(frontendUrl)
                            .build());
                }
                break;
            case ADMIN_NEW_USER:
                // Add variables for admin new user notification
                if (notification.getPayload() != null) {
                    Map<String, Object> payload = notification.getPayload();

                    // New user username
                    if (payload.containsKey("newUserUsername")) {
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("newUserUsername")
                                .value(payload.get("newUserUsername").toString())
                                .build());
                    }

                    // New user email
                    if (payload.containsKey("newUserEmail")) {
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("newUserEmail")
                                .value(payload.get("newUserEmail").toString())
                                .build());
                    }

                    // New user type
                    if (payload.containsKey("newUserType")) {
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("newUserType")
                                .value(payload.get("newUserType").toString())
                                .build());
                    }

                    // New user first name
                    if (payload.containsKey("newUserFirstName")) {
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("newUserFirstName")
                                .value(payload.get("newUserFirstName").toString())
                                .build());
                    }

                    // New user last name
                    if (payload.containsKey("newUserLastName")) {
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("newUserLastName")
                                .value(payload.get("newUserLastName").toString())
                                .build());
                    }

                    // Admin URL for viewing users
                    variables.add(TemplateVariable.builder()
                            .type(TemplateVariableType.RAW)
                            .ref("adminUrl")
                            .value(frontendUrl + "/admin/users")
                            .build());
                }
                break;
            default:
                // No additional variables for other notification types
                break;
        }

        return variables;
    }
}