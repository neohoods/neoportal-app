package com.neohoods.portal.platform.api.hub.announcements;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.AnnouncementsHubApiApiDelegate;
import com.neohoods.portal.platform.model.Announcement;
import com.neohoods.portal.platform.model.CreateAnnouncementRequest;
import com.neohoods.portal.platform.model.PaginatedAnnouncementsResponse;
import com.neohoods.portal.platform.model.UpdateAnnouncementRequest;
import com.neohoods.portal.platform.services.AnnouncementsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementsHubApi implements AnnouncementsHubApiApiDelegate {

    private final AnnouncementsService announcementsService;

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<Void>> createAnnouncement(Mono<CreateAnnouncementRequest> createAnnouncementRequest,
            ServerWebExchange exchange) {
        return (Mono) createAnnouncementRequest
                .flatMap(request -> announcementsService.createAnnouncement(
                        request.getTitle(),
                        request.getContent(),
                        request.getCategory() != null
                                ? com.neohoods.portal.platform.entities.AnnouncementCategory
                                        .fromOpenApiAnnouncementCategory(request.getCategory())
                                : com.neohoods.portal.platform.entities.AnnouncementCategory.OTHER))
                .then(Mono.fromSupplier(() -> {
                    log.info("Announcement created successfully");
                    return ResponseEntity.status(HttpStatus.CREATED).build();
                }))
                .onErrorReturn(ResponseEntity.badRequest().build());
    }

    @Override
    public Mono<ResponseEntity<PaginatedAnnouncementsResponse>> getAnnouncements(
            Integer page,
            Integer pageSize,
            ServerWebExchange exchange) {

        // Set default values
        int pageNum = page != null ? page : 1;
        int pageSizeNum = pageSize != null ? pageSize : 10;

        return announcementsService.getAnnouncementsPaginated(pageNum, pageSizeNum)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error retrieving paginated announcements", e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @Override
    public Mono<ResponseEntity<Announcement>> getAnnouncement(UUID announcementId, ServerWebExchange exchange) {
        return announcementsService.getAnnouncement(announcementId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error retrieving announcement: {}", announcementId, e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<Void>> updateAnnouncement(UUID announcementId,
            Mono<UpdateAnnouncementRequest> updateAnnouncementRequest,
            ServerWebExchange exchange) {
        return (Mono) updateAnnouncementRequest
                .flatMap(request -> announcementsService.updateAnnouncement(announcementId, request.getTitle(),
                        request.getContent()))
                .then(Mono.fromSupplier(() -> {
                    log.info("Announcement updated successfully: {}", announcementId);
                    return ResponseEntity.ok().build();
                }))
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<Void>> deleteAnnouncement(UUID announcementId, ServerWebExchange exchange) {
        return (Mono) announcementsService.deleteAnnouncement(announcementId)
                .then(Mono.fromSupplier(() -> {
                    log.info("Announcement deleted successfully: {}", announcementId);
                    return ResponseEntity.noContent().build();
                }))
                .onErrorReturn(ResponseEntity.notFound().build());
    }
}
