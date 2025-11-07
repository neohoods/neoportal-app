package com.neohoods.portal.platform.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.neohoods.portal.platform.model.UnitInvitation;

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
@Table(name = "unit_invitations")
public class UnitInvitationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitEntity unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_user_id", nullable = true)
    private UserEntity invitedUser;

    @Column(name = "invited_email")
    private String invitedEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    private UserEntity invitedBy;

    @Enumerated(EnumType.STRING)
    private UnitInvitationStatus status;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    public UnitInvitation toUnitInvitation() {
        UnitInvitation invitation = new UnitInvitation()
                .id(id)
                .unitId(unit.getId())
                .invitedUserId(invitedUser != null ? invitedUser.getId() : null)
                .invitedEmail(invitedEmail)
                .invitedBy(invitedBy.getId())
                .createdAt(createdAt)
                .respondedAt(respondedAt);
        if (status != null) {
            invitation.setStatus(UnitInvitation.StatusEnum.fromValue(status.name()));
        }
        return invitation;
    }
}

