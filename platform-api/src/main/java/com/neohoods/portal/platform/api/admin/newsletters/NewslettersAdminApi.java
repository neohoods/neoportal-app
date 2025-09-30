package com.neohoods.portal.platform.api.admin.newsletters;

import java.util.UUID;

import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.NewslettersAdminApiApiDelegate;
import com.neohoods.portal.platform.model.Newsletter;
import com.neohoods.portal.platform.model.NewsletterLogEntry;
import com.neohoods.portal.platform.model.NewsletterLogsResponse;
import com.neohoods.portal.platform.model.NewsletterRequest;
import com.neohoods.portal.platform.model.NewsletterStatus;
import com.neohoods.portal.platform.model.PaginatedNewslettersResponse;
import com.neohoods.portal.platform.model.SendNewsletter200Response;
import com.neohoods.portal.platform.model.TestNewsletter200Response;
import com.neohoods.portal.platform.services.NewsletterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewslettersAdminApi implements NewslettersAdminApiApiDelegate {

    private final NewsletterService newsletterService;

    @Override
    public Mono<ResponseEntity<Newsletter>> createNewsletter(Mono<NewsletterRequest> createNewsletterRequest,
            ServerWebExchange exchange) {
        log.debug("Creating newsletter request received");
        return createNewsletterRequest
                .flatMap(request -> exchange.getPrincipal()
                        .map(principal -> UUID.fromString(principal.getName()))
                        .flatMap(createdBy -> {
                            log.debug("Creating newsletter for user: {}, subject: {}, scheduledAt: {}",
                                    createdBy, request.getSubject(), request.getScheduledAt());
                            // Create newsletter with scheduledAt directly
                            return newsletterService.createNewsletter(
                                    request.getSubject(), // title
                                    request.getSubject(), // subject
                                    request.getContent(),
                                    createdBy,
                                    request.getAudience(),
                                    request.getScheduledAt());
                        }))
                .map(newsletter -> {
                    log.info("Newsletter created successfully: {}", newsletter.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(newsletter);
                })
                .onErrorResume(e -> {
                    log.error("Error creating newsletter: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @Override
    public Mono<ResponseEntity<PaginatedNewslettersResponse>> getNewsletters(
            Integer page,
            Integer pageSize,
            NewsletterStatus status,
            ServerWebExchange exchange) {

        // Set default values
        int pageNum = page != null ? page : 1;
        int pageSizeNum = pageSize != null ? pageSize : 10;

        log.debug("Retrieving newsletters - page: {}, pageSize: {}, status: {}", pageNum, pageSizeNum, status);

        // TODO: Implement status filtering
        return newsletterService.getNewslettersPaginated(pageNum, pageSizeNum)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error retrieving paginated newsletters: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @Override
    public Mono<ResponseEntity<Newsletter>> getNewsletter(UUID newsletterId, ServerWebExchange exchange) {
        log.debug("Retrieving newsletter: {}", newsletterId);
        return newsletterService.getNewsletter(newsletterId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error retrieving newsletter {}: {}", newsletterId, e.getMessage(), e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @Override
    public Mono<ResponseEntity<Newsletter>> updateNewsletter(UUID newsletterId,
            Mono<NewsletterRequest> updateNewsletterRequest,
            ServerWebExchange exchange) {
        log.debug("Updating newsletter: {}", newsletterId);
        return updateNewsletterRequest
                .flatMap(request -> {
                    log.debug("Updating newsletter {} with subject: {}, scheduledAt: {}",
                            newsletterId, request.getSubject(), request.getScheduledAt());
                    // Update newsletter with scheduledAt directly
                    return newsletterService.updateNewsletter(
                            newsletterId,
                            request.getSubject(),
                            request.getContent(),
                            request.getAudience(),
                            request.getScheduledAt());
                })
                .map(newsletter -> {
                    log.info("Newsletter updated successfully: {}", newsletterId);
                    return ResponseEntity.ok(newsletter);
                })
                .onErrorResume(e -> {
                    log.error("Error updating newsletter {}: {}", newsletterId, e.getMessage(), e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteNewsletter(UUID newsletterId, ServerWebExchange exchange) {
        log.debug("Deleting newsletter: {}", newsletterId);
        return newsletterService.deleteNewsletter(newsletterId)
                .then(Mono.fromSupplier(() -> createVoidResponseEntity(HttpStatus.NO_CONTENT)))
                .onErrorResume(e -> {
                    log.error("Error deleting newsletter {}: {}", newsletterId, e.getMessage(), e);
                    return Mono.just(createVoidResponseEntity(HttpStatus.NOT_FOUND));
                });
    }

    @Override
    public Mono<ResponseEntity<SendNewsletter200Response>> sendNewsletter(UUID newsletterId,
            Mono<com.neohoods.portal.platform.model.SendNewsletterRequest> sendNewsletterRequest,
            ServerWebExchange exchange) {
        log.debug("Sending newsletter: {}", newsletterId);
        return sendNewsletterRequest
                .flatMap(request -> {
                    log.debug("Send newsletter request - scheduledAt: {}", request.getScheduledAt());
                    if (request.getScheduledAt() != null) {
                        // Schedule newsletter
                        log.debug("Scheduling newsletter {} for: {}", newsletterId, request.getScheduledAt());
                        return newsletterService.scheduleNewsletter(newsletterId, request.getScheduledAt())
                                .map(newsletter -> {
                                    log.info("Newsletter scheduled successfully: {}", newsletterId);
                                    SendNewsletter200Response response = new SendNewsletter200Response();
                                    response.setMessage("Newsletter scheduled successfully");
                                    if (newsletter.getScheduledAt() != null) {
                                        response.setScheduledAt(newsletter.getScheduledAt());
                                    }
                                    response.setRecipientCount(newsletter.getRecipientCount());
                                    return ResponseEntity.ok(response);
                                });
                    } else {
                        // Send immediately
                        log.debug("Sending newsletter {} immediately", newsletterId);
                        return newsletterService.sendNewsletter(newsletterId)
                                .then(Mono.fromSupplier(() -> {
                                    log.info("Newsletter sent successfully: {}", newsletterId);
                                    SendNewsletter200Response response = new SendNewsletter200Response();
                                    response.setMessage("Newsletter sent successfully");
                                    return ResponseEntity.ok(response);
                                }));
                    }
                })
                .switchIfEmpty(
                        // No request body - send immediately
                        Mono.fromRunnable(() -> log.debug("No request body provided, sending newsletter {} immediately",
                                newsletterId))
                                .then(newsletterService.sendNewsletter(newsletterId))
                                .then(Mono.fromSupplier(() -> {
                                    log.info("Newsletter sent immediately: {}", newsletterId);
                                    SendNewsletter200Response response = new SendNewsletter200Response();
                                    response.setMessage("Newsletter sent successfully");
                                    return ResponseEntity.ok(response);
                                })))
                .onErrorResume(e -> {
                    log.error("Error sending/scheduling newsletter {}: {}", newsletterId, e.getMessage(), e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @Override
    public Mono<ResponseEntity<TestNewsletter200Response>> testNewsletter(UUID newsletterId,
            ServerWebExchange exchange) {
        log.debug("Testing newsletter: {}", newsletterId);
        return exchange.getPrincipal()
                .map(principal -> UUID.fromString(principal.getName()))
                .flatMap(userId -> newsletterService.testNewsletter(newsletterId, userId))
                .then(Mono.fromSupplier(() -> {
                    log.info("Test newsletter sent successfully for ID: {}", newsletterId);
                    TestNewsletter200Response response = new TestNewsletter200Response();
                    return ResponseEntity.ok(response);
                }))
                .onErrorResume(e -> {
                    log.error("Error sending test newsletter {}: {}", newsletterId, e.getMessage(), e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    public Mono<ResponseEntity<NewsletterLogsResponse>> getNewsletterLogs(UUID newsletterId, Integer page,
            Integer pageSize, String level, ServerWebExchange exchange) {
        int pageNum = page != null ? page : 1;
        int pageSizeNum = pageSize != null ? pageSize : 10;

        log.debug("Retrieving newsletter logs for ID: {}, page: {}, pageSize: {}, level: {}",
                newsletterId, pageNum, pageSizeNum, level);

        Pageable pageable = PageRequest.of(pageNum - 1, pageSizeNum, Sort.by(Sort.Direction.DESC, "createdAt"));

        return newsletterService.getNewsletterLogsPaginated(newsletterId, pageable)
                .map(logPage -> {
                    NewsletterLogsResponse response = new NewsletterLogsResponse();
                    response.setTotalPages(logPage.getTotalPages());
                    response.setTotalItems((int) logPage.getTotalElements());
                    response.setCurrentPage(pageNum);
                    response.setItemsPerPage(pageSizeNum);
                    response.setLogs(logPage.getContent().stream()
                            .map(this::mapToNewsletterLogEntry)
                            .toList());
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Error retrieving newsletter logs for {}: {}", newsletterId, e.getMessage(), e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    private ResponseEntity<Void> createVoidResponseEntity(HttpStatus status) {
        return ResponseEntity.status(status).build();
    }

    private NewsletterLogEntry mapToNewsletterLogEntry(
            com.neohoods.portal.platform.entities.NewsletterLogEntity entity) {
        NewsletterLogEntry entry = new NewsletterLogEntry();
        entry.setId(entity.getId());
        entry.setNewsletterId(entity.getNewsletterId());
        entry.setTimestamp(entity.getCreatedAt());
        entry.setLevel(NewsletterLogEntry.LevelEnum.INFO);
        entry.setMessage(
                "Newsletter " + entity.getStatus().name().toLowerCase() + " for user " + entity.getUserEmail());
        if (entity.getErrorMessage() != null) {
            entry.setDetails(JsonNullable.of(entity.getErrorMessage()));
        }
        return entry;
    }
}
