package com.neohoods.portal.platform.entities;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "matrix_bot_error_notifications")
public class MatrixBotErrorNotificationEntity {

    @Id
    private UUID id;

    @Column(name = "last_notification_date", nullable = false, unique = true)
    private LocalDate lastNotificationDate;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}




























