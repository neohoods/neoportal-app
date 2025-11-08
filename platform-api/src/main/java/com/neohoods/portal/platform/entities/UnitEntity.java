package com.neohoods.portal.platform.entities;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.neohoods.portal.platform.model.Unit;
import com.neohoods.portal.platform.model.UnitMember;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@Table(name = "units")
public class UnitEntity {
    @Id
    private UUID id;

    private String name;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<UnitMemberEntity> members;

    public Unit toUnit() {
        List<UnitMember> unitMembers = members != null
                ? members.stream().map(UnitMemberEntity::toUnitMember).collect(Collectors.toList())
                : List.of();

        return new Unit()
                .id(id)
                .name(name)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .members(unitMembers);
    }

    public static UnitEntity fromUnit(Unit unit) {
        return UnitEntity.builder()
                .id(unit.getId())
                .name(unit.getName())
                .createdAt(unit.getCreatedAt())
                .updatedAt(unit.getUpdatedAt())
                .build();
    }
}



