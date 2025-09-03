package com.neohoods.portal.platform.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.neohoods.portal.platform.model.Announcement;

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

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "announcements")
public class AnnouncementEntity {
    @Id
    private UUID id;

    private String title;
    private String content;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private AnnouncementCategory category;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Announcement toAnnouncement() {
        return new Announcement()
                .id(id)
                .title(title)
                .content(content)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .category(category != null ? category.toOpenApiAnnouncementCategory() : null);
    }

    public static AnnouncementEntity fromAnnouncement(Announcement announcement) {
        return AnnouncementEntity.builder()
                .id(announcement.getId() != null ? announcement.getId() : UUID.randomUUID())
                .title(announcement.getTitle())
                .content(announcement.getContent())
                .createdAt(announcement.getCreatedAt())
                .updatedAt(announcement.getUpdatedAt())
                .category(announcement.getCategory() != null
                        ? AnnouncementCategory.fromOpenApiAnnouncementCategory(announcement.getCategory())
                        : AnnouncementCategory.OTHER)
                .build();
    }
}
