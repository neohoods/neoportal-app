package com.neohoods.portal.platform.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "unit_join_requests")
public class UnitJoinRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitEntity unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private UserEntity requestedBy;

    @Enumerated(EnumType.STRING)
    private UnitJoinRequestStatus status;

    @Column(name = "message")
    private String message;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by", nullable = true)
    private UserEntity respondedBy;

    public com.neohoods.portal.platform.model.UnitJoinRequest toUnitJoinRequest() {
        com.neohoods.portal.platform.model.UnitJoinRequest request = new com.neohoods.portal.platform.model.UnitJoinRequest();
        request.setId(id);
        request.setUnitId(unit != null ? unit.getId() : null);
        request.setUnit(unit != null ? unit.toUnit() : null);
        request.setRequestedById(requestedBy != null ? requestedBy.getId() : null);
        request.setRequestedBy(requestedBy != null ? requestedBy.toUser().build() : null);
        request.setStatus(com.neohoods.portal.platform.model.UnitJoinRequestStatus.fromValue(status.name()));
        request.setMessage(message);
        request.setCreatedAt(createdAt);
        request.setRespondedAt(respondedAt);
        request.setRespondedById(respondedBy != null ? respondedBy.getId() : null);
        request.setRespondedBy(respondedBy != null ? respondedBy.toUser().build() : null);
        return request;
    }
}

