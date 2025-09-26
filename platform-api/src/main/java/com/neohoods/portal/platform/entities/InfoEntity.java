package com.neohoods.portal.platform.entities;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.neohoods.portal.platform.model.Info;
import com.neohoods.portal.platform.model.InfoContactNumbers;

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
        private LocalDate nextAGDate;

        @Column(name = "rules_url")
        private String rulesUrl;

        @OneToMany(mappedBy = "info", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
        private List<DelegateEntity> delegates;

        @OneToMany(mappedBy = "info", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
        private List<ContactNumberEntity> contactNumbers;

        public Info toInfo() {
                InfoContactNumbers infoContactNumbers = new InfoContactNumbers();

                // Get syndic contact numbers
                List<ContactNumberEntity> syndicContacts = contactNumbers.stream()
                                .filter(c -> "syndic".equals(c.getContactType()))
                                .collect(Collectors.toList());
                infoContactNumbers.setSyndic(syndicContacts.stream()
                                .map(ContactNumberEntity::toContactNumber)
                                .collect(Collectors.toList()));

                // Get emergency contact numbers
                List<ContactNumberEntity> emergencyContacts = contactNumbers.stream()
                                .filter(c -> "emergency".equals(c.getContactType()))
                                .collect(Collectors.toList());
                infoContactNumbers.setEmergency(emergencyContacts.stream()
                                .map(ContactNumberEntity::toContactNumber)
                                .collect(Collectors.toList()));

                return new Info()
                                .id(id)
                                .nextAGDate(nextAGDate)
                                .rulesUrl(rulesUrl)
                                .delegates(delegates != null
                                                ? delegates.stream().map(DelegateEntity::toDelegate)
                                                                .collect(Collectors.toList())
                                                : List.of())
                                .contactNumbers(infoContactNumbers);
        }

        public static InfoEntity fromInfo(Info info) {
                InfoEntity entity = InfoEntity.builder()
                                .id(info.getId() != null ? info.getId() : UUID.randomUUID())
                                .nextAGDate(info.getNextAGDate())
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
                        List<ContactNumberEntity> contacts = new java.util.ArrayList<>();

                        // Add syndic contacts
                        if (info.getContactNumbers().getSyndic() != null) {
                                info.getContactNumbers().getSyndic().forEach(
                                                contact -> contacts.add(ContactNumberEntity.fromContactNumber(contact,
                                                                entity, "syndic")));
                        }

                        // Add emergency contacts
                        if (info.getContactNumbers().getEmergency() != null) {
                                info.getContactNumbers().getEmergency().forEach(
                                                contact -> contacts.add(ContactNumberEntity.fromContactNumber(contact,
                                                                entity, "emergency")));
                        }

                        entity.setContactNumbers(contacts);
                }

                return entity;
        }
}
