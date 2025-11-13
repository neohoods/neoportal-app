package com.neohoods.portal.platform.services;

import java.net.URI;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.NotificationSettingsEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.model.NotificationSettings;
import com.neohoods.portal.platform.repositories.NotificationSettingsRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.spaces.services.CalendarTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSettingsService {
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final UsersRepository usersRepository;
    private final CalendarTokenService calendarTokenService;

    @Value("${neohoods.portal.base-url}")
    private String baseUrl;

    public Mono<NotificationSettings> getNotificationSettings(UUID userId) {
        log.info("Getting notification settings for user: {}", userId);
        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationSettingsEntity entity = notificationSettingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(user));

        // Generate calendar URL for user
        String token = calendarTokenService.generateTokenForUser(userId);
        URI calendarUrl = URI.create(baseUrl + "/api/public/users/" + userId + "/calendar.ics?token=" + token);

        NotificationSettings settings = entity.toNotificationSettings();
        settings.setCalendarUrl(calendarUrl);

        return Mono.just(settings);
    }

    public Mono<NotificationSettings> updateNotificationSettings(UUID userId, NotificationSettings settings) {
        log.info("Updating notification settings for user: {}", userId);
        UserEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationSettingsEntity entity = notificationSettingsRepository.findByUserId(userId)
                .orElseGet(() -> NotificationSettingsEntity.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .build());

        entity.setEnableNotifications(settings.getEnableNotifications());
        entity.setNewsletterEnabled(settings.getNewsletterEnabled());
        NotificationSettingsEntity savedEntity = notificationSettingsRepository.save(entity);

        // Generate calendar URL for user
        String token = calendarTokenService.generateTokenForUser(userId);
        URI calendarUrl = URI.create(baseUrl + "/api/public/users/" + userId + "/calendar.ics?token=" + token);

        NotificationSettings result = savedEntity.toNotificationSettings();
        result.setCalendarUrl(calendarUrl);

        return Mono.just(result);
    }

    private NotificationSettingsEntity createDefaultSettings(UserEntity user) {
        NotificationSettingsEntity entity = NotificationSettingsEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .enableNotifications(true) // Enable notifications by default
                .newsletterEnabled(true) // Enable newsletters by default
                .build();
        return notificationSettingsRepository.save(entity);
    }
}