package com.neohoods.portal.platform.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.neohoods.portal.platform.model.EmailTemplate;
import com.neohoods.portal.platform.model.EmailTemplateType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "email_templates")
public class EmailTemplateEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(columnDefinition = "text")
    private String description;

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

    public EmailTemplate toEmailTemplate() {
        return EmailTemplate.builder()
                .id(this.id)
                .type(EmailTemplateType.fromValue(this.type))
                .name(this.name)
                .subject(this.subject)
                .content(this.content)
                .isActive(this.isActive)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .createdBy(this.createdBy)
                .description(this.description)
                .build();
    }

    public static EmailTemplateEntity fromEmailTemplate(EmailTemplate template) {
        return EmailTemplateEntity.builder()
                .id(template.getId())
                .type(template.getType().getValue())
                .name(template.getName())
                .subject(template.getSubject())
                .content(template.getContent())
                .isActive(template.getIsActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .createdBy(template.getCreatedBy())
                .description(template.getDescription())
                .build();
    }
}
