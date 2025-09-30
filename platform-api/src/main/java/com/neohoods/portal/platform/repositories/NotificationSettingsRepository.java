package com.neohoods.portal.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.neohoods.portal.platform.entities.NotificationSettingsEntity;

@Repository
public interface NotificationSettingsRepository extends JpaRepository<NotificationSettingsEntity, UUID> {

    /**
     * Find notification settings by user ID
     */
    Optional<NotificationSettingsEntity> findByUserId(UUID userId);

    /**
     * Check if user has newsletter enabled
     */
    boolean existsByUserIdAndNewsletterEnabledTrue(UUID userId);

    /**
     * Get all user IDs that have newsletter enabled
     */
    @Query("SELECT ns.user.id FROM NotificationSettingsEntity ns WHERE ns.newsletterEnabled = true")
    List<UUID> findUserIdsWithNewsletterEnabled();

    /**
     * Get all user IDs that have newsletter enabled from a specific list of user
     * IDs
     */
    @Query("SELECT ns.user.id FROM NotificationSettingsEntity ns WHERE ns.newsletterEnabled = true AND ns.user.id IN :userIds")
    List<UUID> findUserIdsWithNewsletterEnabled(@Param("userIds") List<UUID> userIds);

    /**
     * Get all user IDs that have notifications enabled from a specific list of user
     * IDs
     */
    @Query("SELECT ns.user.id FROM NotificationSettingsEntity ns WHERE ns.enableNotifications = true AND ns.user.id IN :userIds")
    List<UUID> findUserIdsWithNotificationsEnabled(@Param("userIds") List<UUID> userIds);

    /**
     * Get all notification settings for a list of user IDs
     */
    @Query("SELECT ns FROM NotificationSettingsEntity ns WHERE ns.user.id IN :userIds")
    List<NotificationSettingsEntity> findByUserIds(@Param("userIds") List<UUID> userIds);
}