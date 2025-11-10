package com.neohoods.portal.platform.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.neohoods.portal.platform.model.UnitMember;

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
@Table(name = "unit_members")
public class UnitMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitEntity unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    private UnitMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "residence_role", nullable = false)
    @Builder.Default
    private ResidenceRole residenceRole = ResidenceRole.TENANT;

    @Column(name = "joined_at")
    @Builder.Default
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    public UnitMember toUnitMember() {
        UnitMember member = new UnitMember()
                .userId(user.getId())
                .user(user.toUser().build())
                .joinedAt(joinedAt);
        if (role != null) {
            member.setRole(UnitMember.RoleEnum.fromValue(role.name()));
        }
        // Set residenceRole (now required, uses UnitMemberResidenceRole directly)
        // Both enums have the same names, so we can use fromValue with the enum name
        com.neohoods.portal.platform.model.UnitMemberResidenceRole apiResidenceRole;
        if (residenceRole != null) {
            try {
                apiResidenceRole = com.neohoods.portal.platform.model.UnitMemberResidenceRole.fromValue(residenceRole.name());
            } catch (IllegalArgumentException e) {
                // Fallback: try direct enum value match
                apiResidenceRole = com.neohoods.portal.platform.model.UnitMemberResidenceRole.valueOf(residenceRole.name());
            }
        } else {
            // Default to TENANT if somehow null (should not happen due to @Builder.Default)
            apiResidenceRole = com.neohoods.portal.platform.model.UnitMemberResidenceRole.TENANT;
        }
        member.setResidenceRole(apiResidenceRole);
        return member;
    }
}

