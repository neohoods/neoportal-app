package com.neohoods.portal.platform.services;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.entities.NewsletterEntity;
import com.neohoods.portal.platform.entities.NewsletterLogEntity;
import com.neohoods.portal.platform.entities.NewsletterLogEntity.NewsletterLogStatus;
import com.neohoods.portal.platform.entities.NewsletterStatus;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.model.Newsletter;
import com.neohoods.portal.platform.model.NewsletterAudience;
import com.neohoods.portal.platform.model.PaginatedNewslettersResponse;
import com.neohoods.portal.platform.repositories.NewsletterLogRepository;
import com.neohoods.portal.platform.repositories.NewsletterRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterService {
    private final NewsletterRepository newsletterRepository;
    private final NewsletterLogRepository newsletterLogRepository;
    private final UsersRepository usersRepository;
    private final MailService mailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String listToJson(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert list to JSON", e);
            return null;
        }
    }

    public Mono<Newsletter> createNewsletter(String title, String subject, String content, UUID createdBy,
            NewsletterAudience audience) {
        log.info("Creating newsletter: {} by user: {} with audience: {}", title, createdBy, audience.getType());

        NewsletterEntity entity = NewsletterEntity.builder()
                .id(UUID.randomUUID())
                .subject(subject)
                .content(content)
                .status(NewsletterStatus.DRAFT)
                .createdBy(createdBy)
                .audienceType(audience.getType().toString())
                .audienceUserTypes(listToJson(audience.getUserTypes()))
                .audienceUserIds(listToJson(audience.getUserIds()))
                .audienceExcludeUserIds(listToJson(audience.getExcludeUserIds()))
                .build();

        NewsletterEntity savedEntity = newsletterRepository.save(entity);
        log.info("Created newsletter: {} with ID: {}", savedEntity.getSubject(), savedEntity.getId());
        return Mono.just(savedEntity.toNewsletter());
    }

    public Mono<PaginatedNewslettersResponse> getNewslettersPaginated(int page, int pageSize) {
        log.info("Retrieving paginated newsletters - page: {}, pageSize: {}", page, pageSize);

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NewsletterEntity> newsletterPage = newsletterRepository.findAll(pageable);

        PaginatedNewslettersResponse response = new PaginatedNewslettersResponse();
        response.setTotalPages(newsletterPage.getTotalPages());
        response.setTotalItems((int) newsletterPage.getTotalElements());
        response.setCurrentPage(page);
        response.setItemsPerPage(pageSize);
        response.setNewsletters(newsletterPage.getContent().stream()
                .map(NewsletterEntity::toNewsletter)
                .toList());

        log.info("Retrieved {} newsletters out of {} total",
                newsletterPage.getNumberOfElements(), newsletterPage.getTotalElements());

        return Mono.just(response);
    }

    public Mono<Newsletter> getNewsletter(UUID newsletterId) {
        log.info("Retrieving newsletter: {}", newsletterId);
        return Mono.justOrEmpty(newsletterRepository.findById(newsletterId))
                .map(entity -> {
                    log.info("Found newsletter: {}", entity.getSubject());
                    return entity.toNewsletter();
                })
                .switchIfEmpty(Mono.error(new CodedException(
                        CodedError.NEWSLETTER_NOT_FOUND.getCode(),
                        CodedError.NEWSLETTER_NOT_FOUND.getDefaultMessage(),
                        Map.of("newsletterId", newsletterId),
                        CodedError.NEWSLETTER_NOT_FOUND.getDocumentationUrl())));
    }

    public Mono<Newsletter> updateNewsletter(UUID newsletterId, String title, String subject, String content,
            NewsletterAudience audience) {
        log.info("Updating newsletter: {} with audience: {}", newsletterId, audience.getType());

        NewsletterEntity existingEntity = newsletterRepository.findById(newsletterId)
                .orElseThrow(() -> new CodedException(
                        CodedError.NEWSLETTER_NOT_FOUND.getCode(),
                        CodedError.NEWSLETTER_NOT_FOUND.getDefaultMessage(),
                        Map.of("newsletterId", newsletterId),
                        CodedError.NEWSLETTER_NOT_FOUND.getDocumentationUrl()));

        if (existingEntity.getStatus() != NewsletterStatus.DRAFT) {
            throw new CodedException(
                    CodedError.INVALID_NEWSLETTER_STATUS.getCode(),
                    "Only newsletters in DRAFT status can be updated",
                    Map.of("newsletterId", newsletterId, "currentStatus", existingEntity.getStatus()),
                    CodedError.INVALID_NEWSLETTER_STATUS.getDocumentationUrl());
        }

        existingEntity.setSubject(subject);
        existingEntity.setContent(content);
        existingEntity.setAudienceType(audience.getType().toString());
        existingEntity.setAudienceUserTypes(listToJson(audience.getUserTypes()));
        existingEntity.setAudienceUserIds(listToJson(audience.getUserIds()));
        existingEntity.setAudienceExcludeUserIds(listToJson(audience.getExcludeUserIds()));

        NewsletterEntity updatedEntity = newsletterRepository.save(existingEntity);
        log.info("Updated newsletter: {}", updatedEntity.getSubject());
        return Mono.just(updatedEntity.toNewsletter());
    }

    public Mono<Void> deleteNewsletter(UUID newsletterId) {
        log.info("Deleting newsletter: {}", newsletterId);

        NewsletterEntity entity = newsletterRepository.findById(newsletterId)
                .orElseThrow(() -> new CodedException(
                        CodedError.NEWSLETTER_NOT_FOUND.getCode(),
                        CodedError.NEWSLETTER_NOT_FOUND.getDefaultMessage(),
                        Map.of("newsletterId", newsletterId),
                        CodedError.NEWSLETTER_NOT_FOUND.getDocumentationUrl()));

        if (entity.getStatus() != NewsletterStatus.DRAFT) {
            throw new CodedException(
                    CodedError.INVALID_NEWSLETTER_STATUS.getCode(),
                    "Only newsletters in DRAFT status can be deleted",
                    Map.of("newsletterId", newsletterId, "currentStatus", entity.getStatus()),
                    CodedError.INVALID_NEWSLETTER_STATUS.getDocumentationUrl());
        }

        newsletterRepository.delete(entity);
        log.info("Deleted newsletter: {}", entity.getSubject());
        return Mono.empty();
    }

    public Mono<Newsletter> scheduleNewsletter(UUID newsletterId, OffsetDateTime scheduledAt) {
        log.info("Scheduling newsletter: {} for: {}", newsletterId, scheduledAt);

        NewsletterEntity existingEntity = newsletterRepository.findById(newsletterId)
                .orElseThrow(() -> new CodedException(
                        CodedError.NEWSLETTER_NOT_FOUND.getCode(),
                        CodedError.NEWSLETTER_NOT_FOUND.getDefaultMessage(),
                        Map.of("newsletterId", newsletterId),
                        CodedError.NEWSLETTER_NOT_FOUND.getDocumentationUrl()));

        if (existingEntity.getStatus() != NewsletterStatus.DRAFT) {
            throw new CodedException(
                    CodedError.INVALID_NEWSLETTER_STATUS.getCode(),
                    "Only newsletters in DRAFT status can be scheduled",
                    Map.of("newsletterId", newsletterId, "currentStatus", existingEntity.getStatus()),
                    CodedError.INVALID_NEWSLETTER_STATUS.getDocumentationUrl());
        }

        existingEntity.setStatus(NewsletterStatus.SCHEDULED);
        existingEntity.setScheduledAt(scheduledAt);

        NewsletterEntity updatedEntity = newsletterRepository.save(existingEntity);
        log.info("Scheduled newsletter: {} for: {}", updatedEntity.getSubject(), updatedEntity.getScheduledAt());
        return Mono.just(updatedEntity.toNewsletter());
    }

    public Mono<Void> sendNewsletter(UUID newsletterId) {
        log.info("Sending newsletter: {}", newsletterId);

        NewsletterEntity newsletter = newsletterRepository.findById(newsletterId)
                .orElseThrow(() -> new CodedException(
                        CodedError.NEWSLETTER_NOT_FOUND.getCode(),
                        CodedError.NEWSLETTER_NOT_FOUND.getDefaultMessage(),
                        Map.of("newsletterId", newsletterId),
                        CodedError.NEWSLETTER_NOT_FOUND.getDocumentationUrl()));

        if (newsletter.getStatus() != NewsletterStatus.DRAFT && newsletter.getStatus() != NewsletterStatus.SCHEDULED) {
            throw new CodedException(
                    CodedError.INVALID_NEWSLETTER_STATUS.getCode(),
                    "Only newsletters in DRAFT or SCHEDULED status can be sent",
                    Map.of("newsletterId", newsletterId, "currentStatus", newsletter.getStatus()),
                    CodedError.INVALID_NEWSLETTER_STATUS.getDocumentationUrl());
        }

        // Find all users who want to receive newsletters (we might want to filter by
        // notification preferences later)
        List<UserEntity> allUsers = usersRepository.findAllWithProperties();

        if (allUsers.isEmpty()) {
            log.warn("No users found to send newsletter to");
            newsletter.setStatus(NewsletterStatus.SENT);
            newsletter.setSentAt(OffsetDateTime.now());
            newsletter.setRecipientCount(0);
            newsletterRepository.save(newsletter);
            return Mono.empty();
        }

        // Create initial log entries for all users
        List<NewsletterLogEntity> initialLogs = allUsers.stream()
                .map(user -> NewsletterLogEntity.builder()
                        .newsletterId(newsletter.getId())
                        .userId(user.getId())
                        .userEmail(user.getEmail())
                        .status(NewsletterLogStatus.PENDING)
                        .build())
                .toList();

        newsletterLogRepository.saveAll(initialLogs);
        log.info("Created {} pending log entries for newsletter: {}", initialLogs.size(), newsletter.getId());

        // Send newsletter to each user
        return Flux.fromIterable(allUsers)
                .flatMap(user -> {
                    try {
                        // Create base template variables for processing
                        List<MailService.TemplateVariable> baseVariables = List.of(
                                MailService.TemplateVariable.builder()
                                        .type(MailService.TemplateVariableType.RAW)
                                        .ref("firstName")
                                        .value(user.getFirstName())
                                        .build(),
                                MailService.TemplateVariable.builder()
                                        .type(MailService.TemplateVariableType.RAW)
                                        .ref("lastName")
                                        .value(user.getLastName())
                                        .build(),
                                MailService.TemplateVariable.builder()
                                        .type(MailService.TemplateVariableType.RAW)
                                        .ref("username")
                                        .value(user.getUsername())
                                        .build());

                        // Create extended variables for processing (including appName)
                        List<MailService.TemplateVariable> processingVariables = new ArrayList<>(baseVariables);
                        processingVariables.add(MailService.TemplateVariable.builder()
                                .type(MailService.TemplateVariableType.RAW)
                                .ref("appName")
                                .value("Terres de Laya") // TODO: Get from configuration
                                .build());

                        // Process newsletter content with template variables
                        String processedSubject = processTemplateVariables(newsletter.getSubject(),
                                processingVariables);
                        String processedContent = processTemplateVariables(newsletter.getContent(),
                                processingVariables);

                        // Create final template variables for the email
                        List<MailService.TemplateVariable> templateVariables = new ArrayList<>(baseVariables);
                        templateVariables.add(MailService.TemplateVariable.builder()
                                .type(MailService.TemplateVariableType.RAW)
                                .ref("newsletterTitle")
                                .value(processedSubject)
                                .build());
                        templateVariables.add(MailService.TemplateVariable.builder()
                                .type(MailService.TemplateVariableType.RAW)
                                .ref("newsletterContent")
                                .value(processedContent)
                                .build());

                        // Send email with newsletter template
                        mailService.sendTemplatedEmail(
                                user,
                                processedSubject,
                                "email/newsletter",
                                templateVariables,
                                user.getLocale());

                        // Update log entry to SENT
                        updateNewsletterLog(newsletter.getId(), user.getId(), NewsletterLogStatus.SENT, null);
                        log.debug("Sent newsletter to: {}", user.getEmail());
                        return Mono.empty();
                    } catch (Exception e) {
                        log.error("Failed to send newsletter to user: {}", user.getEmail(), e);
                        // Update log entry to FAILED
                        updateNewsletterLog(newsletter.getId(), user.getId(), NewsletterLogStatus.FAILED,
                                e.getMessage());
                        return Mono.empty(); // Continue with other users
                    }
                })
                .then()
                .doOnSuccess(v -> {
                    newsletter.setStatus(NewsletterStatus.SENT);
                    newsletter.setSentAt(OffsetDateTime.now());
                    newsletter.setRecipientCount(allUsers.size());
                    newsletterRepository.save(newsletter);
                    log.info("Successfully sent newsletter to {} users", allUsers.size());
                })
                .onErrorResume(error -> {
                    log.error("Failed to send newsletter: {}", newsletter.getSubject(), error);
                    // Optionally, update newsletter status to FAILED or log the error
                    return Mono.empty(); // Don't fail the entire operation
                });
    }

    public Mono<Void> testNewsletter(UUID newsletterId, UUID userId) {
        log.info("Sending test newsletter: {} to user: {}", newsletterId, userId);

        return Mono.zip(
                Mono.justOrEmpty(newsletterRepository.findById(newsletterId)),
                Mono.justOrEmpty(usersRepository.findById(userId)))
                .switchIfEmpty(Mono.error(new CodedException(
                        CodedError.NEWSLETTER_OR_USER_NOT_FOUND.getCode(),
                        CodedError.NEWSLETTER_OR_USER_NOT_FOUND.getDefaultMessage(),
                        Map.of("newsletterId", newsletterId, "userId", userId),
                        CodedError.NEWSLETTER_OR_USER_NOT_FOUND.getDocumentationUrl())))
                .flatMap(tuple -> {
                    NewsletterEntity newsletter = tuple.getT1();
                    UserEntity user = tuple.getT2();

                    try {
                        // Create base template variables for processing
                        List<MailService.TemplateVariable> baseVariables = List.of(
                                MailService.TemplateVariable.builder()
                                        .type(MailService.TemplateVariableType.RAW)
                                        .ref("firstName")
                                        .value(user.getFirstName())
                                        .build(),
                                MailService.TemplateVariable.builder()
                                        .type(MailService.TemplateVariableType.RAW)
                                        .ref("lastName")
                                        .value(user.getLastName())
                                        .build(),
                                MailService.TemplateVariable.builder()
                                        .type(MailService.TemplateVariableType.RAW)
                                        .ref("username")
                                        .value(user.getUsername())
                                        .build());

                        // Create extended variables for processing (including appName)
                        List<MailService.TemplateVariable> processingVariables = new ArrayList<>(baseVariables);
                        processingVariables.add(MailService.TemplateVariable.builder()
                                .type(MailService.TemplateVariableType.RAW)
                                .ref("appName")
                                .value("Terres de Laya") // TODO: Get from configuration
                                .build());

                        // Process newsletter content with template variables
                        String processedSubject = processTemplateVariables(newsletter.getSubject(),
                                processingVariables);
                        String processedContent = processTemplateVariables(newsletter.getContent(),
                                processingVariables);

                        // Create final template variables for the email
                        List<MailService.TemplateVariable> templateVariables = new ArrayList<>(baseVariables);
                        templateVariables.add(MailService.TemplateVariable.builder()
                                .type(MailService.TemplateVariableType.RAW)
                                .ref("newsletterTitle")
                                .value(processedSubject)
                                .build());
                        templateVariables.add(MailService.TemplateVariable.builder()
                                .type(MailService.TemplateVariableType.RAW)
                                .ref("newsletterContent")
                                .value(processedContent)
                                .build());

                        mailService.sendTemplatedEmail(
                                user,
                                "[TEST] " + processedSubject,
                                "email/newsletter",
                                templateVariables,
                                user.getLocale());
                        log.info("Test newsletter sent to: {}", user.getEmail());
                        return Mono.empty();
                    } catch (Exception e) {
                        log.error("Failed to send test newsletter to user: {}", user.getEmail(), e);
                        return Mono.error(new CodedException(
                                CodedError.EMAIL_SEND_FAILED.getCode(),
                                CodedError.EMAIL_SEND_FAILED.getDefaultMessage(),
                                Map.of("email", user.getEmail(), "newsletterId", newsletterId),
                                CodedError.EMAIL_SEND_FAILED.getDocumentationUrl()));
                    }
                });
    }

    public Flux<NewsletterLogEntity> getNewsletterLogs(UUID newsletterId) {
        log.info("Retrieving logs for newsletter: {}", newsletterId);
        return Flux.fromIterable(newsletterLogRepository.findByNewsletterIdOrderByCreatedAtDesc(newsletterId));
    }

    public Mono<Page<NewsletterLogEntity>> getNewsletterLogsPaginated(UUID newsletterId, Pageable pageable) {
        log.info("Retrieving paginated logs for newsletter: {}", newsletterId);
        return Mono.just(newsletterLogRepository.findByNewsletterIdOrderByCreatedAtDesc(newsletterId, pageable));
    }

    public Flux<NewsletterLogEntity> getNewsletterLogsByStatus(UUID newsletterId, NewsletterLogStatus status) {
        log.info("Retrieving logs for newsletter: {} with status: {}", newsletterId, status);
        return Flux.fromIterable(
                newsletterLogRepository.findByNewsletterIdAndStatusOrderByCreatedAtDesc(newsletterId, status));
    }

    public Mono<Map<String, Long>> getNewsletterStatistics(UUID newsletterId) {
        log.info("Getting statistics for newsletter: {}", newsletterId);

        long totalLogs = newsletterLogRepository.countByNewsletterId(newsletterId);
        long sentCount = newsletterLogRepository.countByNewsletterIdAndStatus(newsletterId, NewsletterLogStatus.SENT);
        long failedCount = newsletterLogRepository.countByNewsletterIdAndStatus(newsletterId,
                NewsletterLogStatus.FAILED);
        long pendingCount = newsletterLogRepository.countByNewsletterIdAndStatus(newsletterId,
                NewsletterLogStatus.PENDING);
        long bouncedCount = newsletterLogRepository.countByNewsletterIdAndStatus(newsletterId,
                NewsletterLogStatus.BOUNCED);

        Map<String, Long> stats = Map.of(
                "total", totalLogs,
                "sent", sentCount,
                "failed", failedCount,
                "pending", pendingCount,
                "bounced", bouncedCount);

        return Mono.just(stats);
    }

    private void updateNewsletterLog(UUID newsletterId, UUID userId, NewsletterLogStatus status, String errorMessage) {
        try {
            newsletterLogRepository.findByNewsletterIdOrderByCreatedAtDesc(newsletterId).stream()
                    .filter(log -> log.getUserId().equals(userId))
                    .findFirst()
                    .ifPresent(logEntry -> {
                        logEntry.setStatus(status);
                        logEntry.setErrorMessage(errorMessage);
                        if (status == NewsletterLogStatus.SENT) {
                            logEntry.setSentAt(OffsetDateTime.now());
                        }
                        newsletterLogRepository.save(logEntry);
                        log.debug("Updated newsletter log for user: {} to status: {}", userId, status);
                    });
        } catch (Exception e) {
            log.error("Failed to update newsletter log for user: {} and newsletter: {}", userId, newsletterId, e);
        }
    }

    /**
     * Process template variables in a string template
     */
    private String processTemplateVariables(String template, List<MailService.TemplateVariable> variables) {
        String result = template;

        // Create a map of variable references to values
        Map<String, String> variableMap = variables.stream()
                .collect(Collectors.toMap(
                        MailService.TemplateVariable::getRef,
                        v -> v.getValue() != null ? v.getValue().toString() : ""));

        // Replace {{variableName}} patterns
        for (Map.Entry<String, String> entry : variableMap.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue());
        }

        return result;
    }
}