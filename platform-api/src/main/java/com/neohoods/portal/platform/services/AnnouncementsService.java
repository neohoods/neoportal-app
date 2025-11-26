package com.neohoods.portal.platform.services;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.AnnouncementCategory;
import com.neohoods.portal.platform.entities.AnnouncementEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.model.Announcement;
import com.neohoods.portal.platform.model.PaginatedAnnouncementsResponse;
import com.neohoods.portal.platform.repositories.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementsService {
    private final AnnouncementRepository announcementRepository;
    private final NotificationsService notificationsService;

    public Mono<Announcement> createAnnouncement(String title, String content, AnnouncementCategory category) {
        log.info("Creating announcement: {} with category: {}", title, category);

        AnnouncementEntity entity = AnnouncementEntity.builder()
                .id(UUID.randomUUID())
                .title(title)
                .content(content)
                .category(category != null ? category : AnnouncementCategory.OTHER)
                .build();

        AnnouncementEntity savedEntity = announcementRepository.save(entity);
        log.info("Created announcement: {} with ID: {}", savedEntity.getTitle(), savedEntity.getId());

        // Send notifications to all users about the new announcement
        notificationsService.notifyUsersNewAnnouncement(savedEntity)
                .doOnError(error -> log.error("Failed to send notifications for new announcement: {}",
                        savedEntity.getTitle(), error))
                .onErrorResume(error -> Mono.empty()) // Don't fail announcement creation if
                // notifications fail
                .subscribe(); // Fire and forget - don't block announcement creation

        return Mono.just(savedEntity.toAnnouncement());
    }

    public Flux<Announcement> getAnnouncements() {
        log.info("Retrieving all announcements");
        return Flux.fromIterable(announcementRepository.findAllByOrderByCreatedAtDesc())
                .map(AnnouncementEntity::toAnnouncement)
                .doOnComplete(() -> log.info("Retrieved all announcements"));
    }

    public Mono<PaginatedAnnouncementsResponse> getAnnouncementsPaginated(
            int page,
            int pageSize) {

        log.info("Retrieving paginated announcements - page: {}, pageSize: {}", page, pageSize);

        // Create pageable object with default sorting by creation date descending
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Execute query
        Page<AnnouncementEntity> announcementPage = announcementRepository.findAll(pageable);

        // Convert to response
        PaginatedAnnouncementsResponse response = new PaginatedAnnouncementsResponse();
        response.setTotalPages(announcementPage.getTotalPages());
        response.setTotalItems((int) announcementPage.getTotalElements());
        response.setCurrentPage(page);
        response.setItemsPerPage(pageSize);
        response.setAnnouncements(announcementPage.getContent().stream()
                .map(AnnouncementEntity::toAnnouncement)
                .toList());

        log.info("Retrieved {} announcements out of {} total",
                announcementPage.getNumberOfElements(), announcementPage.getTotalElements());

        return Mono.just(response);
    }

    public Mono<Announcement> getAnnouncement(UUID announcementId) {
        log.info("Retrieving announcement: {}", announcementId);
        return Mono.justOrEmpty(announcementRepository.findById(announcementId))
                .map(entity -> {
                    log.info("Found announcement: {}", entity.getTitle());
                    return entity.toAnnouncement();
                })
                .switchIfEmpty(Mono.error(new CodedException(
                        CodedError.ANNOUNCEMENT_NOT_FOUND.getCode(),
                        CodedError.ANNOUNCEMENT_NOT_FOUND.getDefaultMessage(),
                        Map.of("announcementId", announcementId),
                        CodedError.ANNOUNCEMENT_NOT_FOUND.getDocumentationUrl())));
    }

    public Mono<Announcement> updateAnnouncement(UUID announcementId, String title, String content) {
        log.info("Updating announcement: {}", announcementId);

        AnnouncementEntity existingEntity = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new CodedException(
                        CodedError.ANNOUNCEMENT_NOT_FOUND.getCode(),
                        CodedError.ANNOUNCEMENT_NOT_FOUND.getDefaultMessage(),
                        Map.of("announcementId", announcementId),
                        CodedError.ANNOUNCEMENT_NOT_FOUND.getDocumentationUrl()));

        existingEntity.setTitle(title);
        existingEntity.setContent(content);
        // Note: updatedAt will be set automatically by @PreUpdate

        AnnouncementEntity updatedEntity = announcementRepository.save(existingEntity);
        log.info("Updated announcement: {}", updatedEntity.getTitle());
        return Mono.just(updatedEntity.toAnnouncement());
    }

    public Mono<Void> deleteAnnouncement(UUID announcementId) {
        log.info("Deleting announcement: {}", announcementId);

        AnnouncementEntity entity = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new CodedException(
                        CodedError.ANNOUNCEMENT_NOT_FOUND.getCode(),
                        CodedError.ANNOUNCEMENT_NOT_FOUND.getDefaultMessage(),
                        Map.of("announcementId", announcementId),
                        CodedError.ANNOUNCEMENT_NOT_FOUND.getDocumentationUrl()));

        announcementRepository.delete(entity);
        log.info("Deleted announcement: {}", entity.getTitle());
        return Mono.empty();
    }

    public Flux<Announcement> getAnnouncementsSince(OffsetDateTime since) {
        log.info("Retrieving announcements since: {}", since);
        List<AnnouncementEntity> announcements = announcementRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);
        List<Announcement> result = announcements.stream()
                .map(AnnouncementEntity::toAnnouncement)
                .collect(java.util.stream.Collectors.toList());
        log.info("Retrieved {} announcements since {}", result.size(), since);
        return Flux.fromIterable(result);
    }
}
