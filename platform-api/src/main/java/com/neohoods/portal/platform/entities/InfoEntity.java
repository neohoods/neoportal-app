package com.neohoods.portal.platform.entities;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.neohoods.portal.platform.model.Info;

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
@Table(name = "infos")
public class InfoEntity {
        @Id
        private UUID id;

        @Column(name = "next_ag_date")
        private LocalDateTime nextAGDate;

        @Column(name = "rules_url")
        private String rulesUrl;

        @OneToMany(mappedBy = "info", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
        private List<DelegateEntity> delegates;

        @OneToMany(mappedBy = "info", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
        private List<ContactNumberEntity> contactNumbers;

        public Info toInfo() {
                return new Info()
                                .id(id)
                                .nextAGDate(nextAGDate != null ? nextAGDate.atOffset(ZoneOffset.UTC) : null)
                                .rulesUrl(rulesUrl)
                                .delegates(delegates != null
                                                ? delegates.stream().map(DelegateEntity::toDelegate)
                                                                .collect(Collectors.toList())
                                                : List.of())
                                .contactNumbers(contactNumbers != null
                                                ? contactNumbers.stream().map(ContactNumberEntity::toContactNumber)
                                                                .collect(Collectors.toList())
                                                : List.of());
        }

        public static InfoEntity fromInfo(Info info) {
                InfoEntity entity = InfoEntity.builder()
                                .id(info.getId() != null ? info.getId() : UUID.randomUUID())
                                .nextAGDate(info.getNextAGDate() != null ? info.getNextAGDate().toLocalDateTime()
                                                : null)
                                .rulesUrl(info.getRulesUrl())
                                .build();

                // Handle delegates
                if (info.getDelegates() != null) {
                        entity.setDelegates(info.getDelegates().stream()
                                        .map(delegate -> DelegateEntity.fromDelegate(delegate, entity))
                                        .collect(Collectors.toList()));
                }

                // Handle contact numbers
                if (info.getContactNumbers() != null) {
                        List<ContactNumberEntity> contacts = info.getContactNumbers().stream()
                                        .map(contact -> ContactNumberEntity.fromContactNumber(contact, entity,
                                                        contact.getContactType() != null
                                                                        ? contact.getContactType().getValue()
                                                                        : null))
                                        .collect(Collectors.toList());
                        entity.setContactNumbers(contacts);
                }

                return entity;
        }
}
