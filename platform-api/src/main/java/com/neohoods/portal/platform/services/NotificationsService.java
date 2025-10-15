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

    @Value("${neohoods.portal.email.template.app-name}")
    private String appName;

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

    public void createNotification(UUID userId, NotificationType type, String title, String message,
            Map<String, Object> payload) {
        NotificationEntity notification = new NotificationEntity();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setType(type);

        // Add title and message to payload
        Map<String, Object> fullPayload = new java.util.HashMap<>(payload);
        fullPayload.put("title", title);
        fullPayload.put("message", message);
        notification.setPayload(fullPayload);

        notification.setAlreadyRead(false);
        notification.setAuthor(PLATFORM_AUTHOR);
        notification.setDate(java.time.Instant.now());
        notificationRepository.save(notification);
    }

    public Mono<Void> sendNotifications(UserEntity user, NotificationEntity notification, Locale locale) {
        try {
            notification.setId(UUID.randomUUID());
            notification.setUserId(user.getId());
            notificationRepository.save(notification);

            // Create template variables for the email
            List<TemplateVariable> templateVariables = getTemplateVariables(notification.getType(), notification,
                    locale);

            templateVariables.add(
                    TemplateVariable.builder()
                            .type(TemplateVariableType.RAW)
                            .ref("username")
                            .value(user.getUsername())
                            .build());

            // Create dynamic subject for NEW_ANNOUNCEMENT notifications
            String emailSubject;
            if (notification.getType() == NotificationType.NEW_ANNOUNCEMENT && notification.getPayload() != null) {
                Map<String, Object> payload = notification.getPayload();
                String announcementTitle = payload.get("announcementTitle") != null
                        ? payload.get("announcementTitle").toString()
                        : messageSource.getMessage("notification.new_announcement.email.default_title", null, locale);
                String category = payload.get("announcementCategory") != null
                        ? payload.get("announcementCategory").toString()
                        : "OTHER";
                String categoryIcon = getCategoryIcon(category);
                emailSubject = "[" + appName + "] " + categoryIcon + " " + announcementTitle;
            } else {
                // Use translation key for other notification types
                emailSubject = "notification." + notification.getType().name().toLowerCase() + ".email.title";
            }

            mailService.sendTemplatedEmail(
                    user,
                    emailSubject,
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
                    "newUserId", newUser.getId().toString(),
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

    private List<TemplateVariable> getTemplateVariables(NotificationType type, NotificationEntity notification,
            Locale locale) {
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
                        String category = payload.get("announcementCategory").toString();

                        // Translate category to user's locale
                        String translatedCategory = getTranslatedCategory(category, locale);
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("announcementCategory")
                                .value(translatedCategory)
                                .build());

                        // Add category icon
                        String categoryIcon = getCategoryIcon(category);
                        variables.add(TemplateVariable.builder()
                                .type(TemplateVariableType.RAW)
                                .ref("announcementCategoryIcon")
                                .value(categoryIcon)
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

    /**
     * Get the appropriate emoji icon for announcement category
     * Maps to the same categories as the frontend UIAnnouncementCategory enum
     */
    private String getCategoryIcon(String category) {
        return switch (category.toUpperCase()) {
            case "COMMUNITY_EVENT" -> "📅"; // @tui.calendar-heart
            case "LOST_AND_FOUND" -> "🔍"; // @tui.search-x
            case "SAFETY_ALERT" -> "⚠️"; // @tui.triangle-alert
            case "MAINTENANCE_NOTICE" -> "🔧"; // @tui.construction
            case "SOCIAL_GATHERING" -> "👥"; // @tui.users
            case "OTHER" -> "📢"; // @tui.message-circle
            default -> "📢";
        };
    }

    /**
     * Get the translated category name for the given locale
     */
    private String getTranslatedCategory(String category, Locale locale) {
        String key = "announcement-category." + category.toUpperCase();
        return messageSource.getMessage(key, null, category, locale);
    }

    /**
     * Send notification for reservation events
     */
    public void sendReservationNotification(UUID reservationId, String eventType, String spaceName,
            UserEntity user, UserEntity adminUser) {

        // Send notification to user
        createNotification(
                user.getId(),
                NotificationType.RESERVATION,
                getReservationNotificationTitle(eventType, spaceName),
                getReservationNotificationMessage(eventType, spaceName),
                Map.of(
                        "reservationId", reservationId.toString(),
                        "eventType", eventType,
                        "spaceName", spaceName));

        // Send notification to admin
        if (adminUser != null) {
            createNotification(
                    adminUser.getId(),
                    NotificationType.RESERVATION,
                    getReservationNotificationTitle(eventType, spaceName),
                    getReservationNotificationMessage(eventType, spaceName),
                    Map.of(
                            "reservationId", reservationId.toString(),
                            "eventType", eventType,
                            "spaceName", spaceName,
                            "userName", user.getFirstName() + " " + user.getLastName()));
        }
    }

    /**
     * Send notification for reservation creation
     */
    public void notifyReservationCreated(UUID reservationId, String spaceName, UserEntity user, UserEntity adminUser) {
        sendReservationNotification(reservationId, "CREATED", spaceName, user, adminUser);
    }

    /**
     * Send notification for reservation confirmation
     */
    public void notifyReservationConfirmed(UUID reservationId, String spaceName, UserEntity user,
            UserEntity adminUser) {
        sendReservationNotification(reservationId, "CONFIRMED", spaceName, user, adminUser);
    }

    /**
     * Send notification for reservation cancellation
     */
    public void notifyReservationCancelled(UUID reservationId, String spaceName, UserEntity user,
            UserEntity adminUser) {
        sendReservationNotification(reservationId, "CANCELLED", spaceName, user, adminUser);
    }

    /**
     * Send notification for payment received
     */
    public void notifyPaymentReceived(UUID reservationId, String spaceName, UserEntity user, UserEntity adminUser) {
        sendReservationNotification(reservationId, "PAYMENT_RECEIVED", spaceName, user, adminUser);
    }

    /**
     * Send notification for access code generated
     */
    public void notifyAccessCodeGenerated(UUID reservationId, String spaceName, UserEntity user) {
        createNotification(
                user.getId(),
                NotificationType.RESERVATION,
                getReservationNotificationTitle("ACCESS_CODE_GENERATED", spaceName),
                getReservationNotificationMessage("ACCESS_CODE_GENERATED", spaceName),
                Map.of(
                        "reservationId", reservationId.toString(),
                        "eventType", "ACCESS_CODE_GENERATED",
                        "spaceName", spaceName));
    }

    private String getReservationNotificationTitle(String eventType, String spaceName) {
        return switch (eventType) {
            case "CREATED" -> "Nouvelle réservation";
            case "CONFIRMED" -> "Réservation confirmée";
            case "CANCELLED" -> "Réservation annulée";
            case "PAYMENT_RECEIVED" -> "Paiement reçu";
            case "ACCESS_CODE_GENERATED" -> "Code d'accès généré";
            default -> "Mise à jour de réservation";
        };
    }

    private String getReservationNotificationMessage(String eventType, String spaceName) {
        return switch (eventType) {
            case "CREATED" -> "Une nouvelle réservation a été créée pour " + spaceName;
            case "CONFIRMED" -> "Votre réservation pour " + spaceName + " a été confirmée";
            case "CANCELLED" -> "La réservation pour " + spaceName + " a été annulée";
            case "PAYMENT_RECEIVED" -> "Le paiement pour " + spaceName + " a été reçu";
            case "ACCESS_CODE_GENERATED" -> "Un code d'accès a été généré pour " + spaceName;
            default -> "Mise à jour de la réservation pour " + spaceName;
        };
    }
}