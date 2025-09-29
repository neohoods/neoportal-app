package com.neohoods.portal.platform.entities;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.model.Newsletter;
import com.neohoods.portal.platform.model.NewsletterAudience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@Table(name = "newsletters")
public class NewsletterEntity {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Id
    private UUID id;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NewsletterStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(nullable = false)
    private UUID createdBy;

    private Integer recipientCount;

    @Column(name = "audience_type", nullable = false)
    private String audienceType = "ALL";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audience_user_types", columnDefinition = "jsonb")
    private String audienceUserTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audience_user_ids", columnDefinition = "jsonb")
    private String audienceUserIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audience_exclude_user_ids", columnDefinition = "jsonb")
    private String audienceExcludeUserIds;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = NewsletterStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Newsletter toNewsletter() {
        Newsletter newsletter = new Newsletter()
                .id(id)
                .subject(subject)
                .content(content)
                .status(com.neohoods.portal.platform.model.NewsletterStatus.valueOf(status.name()))
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .createdBy(createdBy)
                .recipientCount(recipientCount);

        if (scheduledAt != null) {
            newsletter.scheduledAt(scheduledAt);
        }
        if (sentAt != null) {
            newsletter.sentAt(sentAt);
        }

        // Map audience
        NewsletterAudience audience = new NewsletterAudience();
        audience.setType(NewsletterAudience.TypeEnum.valueOf(audienceType));

        if (audienceUserTypes != null) {
            try {
                List<String> userTypes = objectMapper.readValue(audienceUserTypes, new TypeReference<List<String>>() {
                });
                audience.setUserTypes(userTypes.stream()
                        .map(com.neohoods.portal.platform.model.UserType::valueOf)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("Failed to parse audienceUserTypes: {}", audienceUserTypes, e);
            }
        }

        if (audienceUserIds != null) {
            try {
                List<String> userIds = objectMapper.readValue(audienceUserIds, new TypeReference<List<String>>() {
                });
                audience.setUserIds(userIds.stream()
                        .map(UUID::fromString)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("Failed to parse audienceUserIds: {}", audienceUserIds, e);
            }
        }

        if (audienceExcludeUserIds != null) {
            try {
                List<String> excludeUserIds = objectMapper.readValue(audienceExcludeUserIds,
                        new TypeReference<List<String>>() {
                        });
                audience.setExcludeUserIds(excludeUserIds.stream()
                        .map(UUID::fromString)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("Failed to parse audienceExcludeUserIds: {}", audienceExcludeUserIds, e);
            }
        }

        newsletter.setAudience(audience);

        return newsletter;
    }

    public static NewsletterEntity fromNewsletter(Newsletter newsletter) {
        return NewsletterEntity.builder()
                .id(newsletter.getId() != null ? newsletter.getId() : UUID.randomUUID())
                .content(newsletter.getContent())
                .subject(newsletter.getSubject())
                .status(newsletter.getStatus() != null
                        ? NewsletterStatus.valueOf(newsletter.getStatus().name())
                        : NewsletterStatus.DRAFT)
                .createdAt(newsletter.getCreatedAt())
                .updatedAt(newsletter.getUpdatedAt())
                .scheduledAt(newsletter.getScheduledAt())
                .sentAt(newsletter.getSentAt())
                .createdBy(newsletter.getCreatedBy())
                .recipientCount(newsletter.getRecipientCount())
                .build();
    }
}