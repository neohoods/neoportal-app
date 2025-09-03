package com.neohoods.portal.platform.services;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.ContactNumberEntity;
import com.neohoods.portal.platform.entities.DelegateEntity;
import com.neohoods.portal.platform.entities.InfoEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.model.Info;
import com.neohoods.portal.platform.repositories.InfoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class InfosService {
        private final InfoRepository infoRepository;

        // Well-known UUID for the community info record
        private static final UUID COMMUNITY_INFO_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

        public Mono<Info> getInfos() {
                log.info("Retrieving community infos");

                // First load the InfoEntity with delegates
                InfoEntity entityWithDelegates = infoRepository.findByIdWithDelegates(COMMUNITY_INFO_ID)
                                .orElseThrow(() -> new CodedException(
                                                CodedError.INFOS_NOT_FOUND.getCode(),
                                                CodedError.INFOS_NOT_FOUND.getDefaultMessage(),
                                                Map.of("infoId", COMMUNITY_INFO_ID),
                                                CodedError.INFOS_NOT_FOUND.getDocumentationUrl()));

                // Then load the same entity with contact numbers to populate that collection
                InfoEntity entityWithContactNumbers = infoRepository.findByIdWithContactNumbers(COMMUNITY_INFO_ID)
                                .orElse(entityWithDelegates); // Fall back to the delegates version if not found

                // Merge the collections - set contact numbers from the second query
                entityWithDelegates.setContactNumbers(entityWithContactNumbers.getContactNumbers());

                log.info("Found community infos with delegates and contact numbers");
                return Mono.just(entityWithDelegates.toInfo());
        }

        public Mono<Info> updateInfos(Info info) {
                log.info("Updating community infos");

                // Always use the well-known ID for community info
                info.setId(COMMUNITY_INFO_ID);

                // Load existing entity with delegates first
                InfoEntity existingEntityWithDelegates = infoRepository.findByIdWithDelegates(COMMUNITY_INFO_ID)
                                .orElseThrow(() -> new CodedException(
                                                CodedError.INFOS_NOT_FOUND.getCode(),
                                                CodedError.INFOS_NOT_FOUND.getDefaultMessage(),
                                                Map.of("infoId", COMMUNITY_INFO_ID),
                                                CodedError.INFOS_NOT_FOUND.getDocumentationUrl()));

                // Load existing entity with contact numbers
                InfoEntity existingEntityWithContactNumbers = infoRepository
                                .findByIdWithContactNumbers(COMMUNITY_INFO_ID)
                                .orElse(existingEntityWithDelegates);

                // Merge contact numbers into the entity with delegates
                existingEntityWithDelegates.setContactNumbers(existingEntityWithContactNumbers.getContactNumbers());

                // Update basic fields
                existingEntityWithDelegates.setNextAGDate(info.getNextAGDate());
                existingEntityWithDelegates.setRulesUrl(info.getRulesUrl());

                // Replace delegates collection
                if (info.getDelegates() != null) {
                        List<DelegateEntity> newDelegates = info.getDelegates().stream()
                                        .map(delegate -> DelegateEntity.fromDelegate(delegate,
                                                        existingEntityWithDelegates))
                                        .collect(Collectors.toList());
                        existingEntityWithDelegates.setDelegates(newDelegates);
                }

                // Replace contact numbers collection
                if (info.getContactNumbers() != null) {
                        List<ContactNumberEntity> newContactNumbers = new java.util.ArrayList<>();

                        // Add syndic contact
                        if (info.getContactNumbers().getSyndic() != null) {
                                newContactNumbers.add(ContactNumberEntity.fromContactNumber(
                                                info.getContactNumbers().getSyndic(), existingEntityWithDelegates,
                                                "syndic"));
                        }

                        // Add emergency contacts
                        if (info.getContactNumbers().getEmergency() != null) {
                                info.getContactNumbers().getEmergency().forEach(
                                                contact -> newContactNumbers.add(ContactNumberEntity.fromContactNumber(
                                                                contact, existingEntityWithDelegates, "emergency")));
                        }

                        existingEntityWithDelegates.setContactNumbers(newContactNumbers);
                }

                InfoEntity savedEntity = infoRepository.save(existingEntityWithDelegates);
                log.info("Updated community infos");

                // Reload the entity with relations to avoid lazy loading issues
                // Load with delegates first
                InfoEntity reloadedWithDelegates = infoRepository.findByIdWithDelegates(savedEntity.getId())
                                .orElseThrow(() -> new CodedException(
                                                CodedError.INFOS_NOT_FOUND.getCode(),
                                                CodedError.INFOS_NOT_FOUND.getDefaultMessage(),
                                                Map.of("infoId", savedEntity.getId()),
                                                CodedError.INFOS_NOT_FOUND.getDocumentationUrl()));

                // Load with contact numbers and merge
                InfoEntity reloadedWithContactNumbers = infoRepository.findByIdWithContactNumbers(savedEntity.getId())
                                .orElse(reloadedWithDelegates);
                reloadedWithDelegates.setContactNumbers(reloadedWithContactNumbers.getContactNumbers());

                return Mono.just(reloadedWithDelegates.toInfo());
        }
}
