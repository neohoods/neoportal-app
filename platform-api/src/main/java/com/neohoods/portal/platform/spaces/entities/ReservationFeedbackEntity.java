package com.neohoods.portal.platform.spaces.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "reservation_feedback")
public class ReservationFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    @NotNull
    private ReservationEntity reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull
    private com.neohoods.portal.platform.entities.UserEntity user;

    @NotNull
    @Min(1)
    @Max(5)
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Size(max = 1000)
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Min(1)
    @Max(5)
    @Column(name = "cleanliness")
    private Integer cleanliness;

    @Min(1)
    @Max(5)
    @Column(name = "communication")
    private Integer communication;

    @Min(1)
    @Max(5)
    @Column(name = "value")
    private Integer value;

    @NotNull
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    // Constructors
    public ReservationFeedbackEntity() {
        this.submittedAt = LocalDateTime.now();
    }

    public ReservationFeedbackEntity(ReservationEntity reservation, 
                                   com.neohoods.portal.platform.entities.UserEntity user,
                                   Integer rating, String comment) {
        this();
        this.reservation = reservation;
        this.user = user;
        this.rating = rating;
        this.comment = comment;
    }

    public ReservationFeedbackEntity(ReservationEntity reservation, 
                                   com.neohoods.portal.platform.entities.UserEntity user,
                                   Integer rating, String comment, 
                                   Integer cleanliness, Integer communication, Integer value) {
        this(reservation, user, rating, comment);
        this.cleanliness = cleanliness;
        this.communication = communication;
        this.value = value;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ReservationEntity getReservation() {
        return reservation;
    }

    public void setReservation(ReservationEntity reservation) {
        this.reservation = reservation;
    }

    public com.neohoods.portal.platform.entities.UserEntity getUser() {
        return user;
    }

    public void setUser(com.neohoods.portal.platform.entities.UserEntity user) {
        this.user = user;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getCleanliness() {
        return cleanliness;
    }

    public void setCleanliness(Integer cleanliness) {
        this.cleanliness = cleanliness;
    }

    public Integer getCommunication() {
        return communication;
    }

    public void setCommunication(Integer communication) {
        this.communication = communication;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}
