package com.neohoods.portal.platform.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "newsletter_logs")
public class NewsletterLogEntity {
    @Id
    private UUID id;
    
    @Column(name = "newsletter_id", nullable = false)
    private UUID newsletterId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "user_email", nullable = false)
    private String userEmail;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NewsletterLogStatus status;
    
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;
    
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public enum NewsletterLogStatus {
        PENDING,
        SENT,
        FAILED,
        BOUNCED
    }
}
