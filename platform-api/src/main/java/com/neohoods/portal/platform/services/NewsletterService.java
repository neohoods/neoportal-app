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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neohoods.portal.platform.entities.NewsletterEntity;
import com.neohoods.portal.platform.entities.NewsletterLogEntity;
import com.neohoods.portal.platform.entities.NewsletterLogEntity.NewsletterLogStatus;
import com.neohoods.portal.platform.entities.NewsletterStatus;
import com.neohoods.portal.platform.entities.NotificationSettingsEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.model.Newsletter;
import com.neohoods.portal.platform.model.NewsletterAudience;
import com.neohoods.portal.platform.model.PaginatedNewslettersResponse;
import com.neohoods.portal.platform.model.UserType;
import com.neohoods.portal.platform.repositories.NewsletterLogRepository;
import com.neohoods.portal.platform.repositories.NewsletterRepository;
import com.neohoods.portal.platform.repositories.NotificationSettingsRepository;
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
        private final NotificationSettingsRepository notificationSettingsRepository;
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

        private <T> List<T> jsonToList(String json, Class<T> clazz) {
                if (json == null || json.trim().isEmpty()) {
                        return List.of();
                }
                try {
                        return objectMapper.readValue(json,
                                        objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
                } catch (JsonProcessingException e) {
                        log.error("Error converting JSON to list", e);
                        return List.of();
                }
        }

        public Mono<Newsletter> createNewsletter(String title, String subject, String content, UUID createdBy,
                        NewsletterAudience audience, OffsetDateTime scheduledAt) {
                log.info("Creating newsletter: {} by user: {} with audience: {}", title, createdBy, audience.getType());

                NewsletterEntity entity = NewsletterEntity.builder()
                                .id(UUID.randomUUID())
                                .subject(subject)
                                .content(content)
                                .status(scheduledAt != null ? NewsletterStatus.SCHEDULED : NewsletterStatus.DRAFT)
                                .createdBy(createdBy)
                                .audienceType(audience.getType().toString())
                                .audienceUserTypes(listToJson(audience.getUserTypes()))
                                .audienceUserIds(listToJson(audience.getUserIds()))
                                .audienceExcludeUserIds(listToJson(audience.getExcludeUserIds()))
                                .scheduledAt(scheduledAt)
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

        public Mono<Newsletter> updateNewsletter(UUID newsletterId, String subject, String content,
                        NewsletterAudience audience, OffsetDateTime scheduledAt) {
                log.info("Updating newsletter: {} with audience: {}", newsletterId, audience.getType());

                NewsletterEntity existingEntity = newsletterRepository.findById(newsletterId)
                                .orElseThrow(() -> new CodedException(
                                                CodedError.NEWSLETTER_NOT_FOUND.getCode(),
                                                CodedError.NEWSLETTER_NOT_FOUND.getDefaultMessage(),
                                                Map.of("newsletterId", newsletterId),
                                                CodedError.NEWSLETTER_NOT_FOUND.getDocumentationUrl()));

                if (existingEntity.getStatus() != NewsletterStatus.DRAFT
                                && existingEntity.getStatus() != NewsletterStatus.SCHEDULED) {
                        throw new CodedException(
                                        CodedError.INVALID_NEWSLETTER_STATUS.getCode(),
                                        "Only newsletters in DRAFT or SCHEDULED status can be updated",
                                        Map.of("newsletterId", newsletterId, "currentStatus",
                                                        existingEntity.getStatus()),
                                        CodedError.INVALID_NEWSLETTER_STATUS.getDocumentationUrl());
                }

                existingEntity.setSubject(subject);
                existingEntity.setContent(content);
                existingEntity.setAudienceType(audience.getType().toString());
                existingEntity.setAudienceUserTypes(listToJson(audience.getUserTypes()));
                existingEntity.setAudienceUserIds(listToJson(audience.getUserIds()));
                existingEntity.setAudienceExcludeUserIds(listToJson(audience.getExcludeUserIds()));
                existingEntity.setScheduledAt(scheduledAt);

                // Update status based on scheduledAt
                if (scheduledAt != null) {
                        existingEntity.setStatus(NewsletterStatus.SCHEDULED);
                } else {
                        existingEntity.setStatus(NewsletterStatus.DRAFT);
                }

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

                if (entity.getStatus() == NewsletterStatus.SENT) {
                        throw new CodedException(
                                        CodedError.INVALID_NEWSLETTER_STATUS.getCode(),
                                        "Sent newsletters cannot be deleted",
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
                                        Map.of("newsletterId", newsletterId, "currentStatus",
                                                        existingEntity.getStatus()),
                                        CodedError.INVALID_NEWSLETTER_STATUS.getDocumentationUrl());
                }

                existingEntity.setStatus(NewsletterStatus.SCHEDULED);
                existingEntity.setScheduledAt(scheduledAt);

                NewsletterEntity updatedEntity = newsletterRepository.save(existingEntity);
                log.info("Scheduled newsletter: {} for: {}", updatedEntity.getSubject(),
                                updatedEntity.getScheduledAt());
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

                if (newsletter.getStatus() != NewsletterStatus.DRAFT
                                && newsletter.getStatus() != NewsletterStatus.SCHEDULED) {
                        throw new CodedException(
                                        CodedError.INVALID_NEWSLETTER_STATUS.getCode(),
                                        "Only newsletters in DRAFT or SCHEDULED status can be sent",
                                        Map.of("newsletterId", newsletterId, "currentStatus", newsletter.getStatus()),
                                        CodedError.INVALID_NEWSLETTER_STATUS.getDocumentationUrl());
                }

                // Parse audience configuration
                NewsletterAudience audience = parseAudienceFromNewsletter(newsletter);

                // Find users based on audience and newsletter preferences
                List<UserEntity> targetUsers = findTargetUsersForNewsletter(audience);

                if (targetUsers.isEmpty()) {
                        log.warn("No users found to send newsletter to (audience: {})", audience.getType());
                        newsletter.setStatus(NewsletterStatus.SENT);
                        newsletter.setSentAt(OffsetDateTime.now());
                        newsletter.setRecipientCount(0);
                        newsletterRepository.save(newsletter);
                        return Mono.empty();
                }

                log.info("Sending newsletter to {} users (audience: {})", targetUsers.size(), audience.getType());

                // Create initial log entries for target users
                List<NewsletterLogEntity> initialLogs = targetUsers.stream()
                                .map(user -> NewsletterLogEntity.builder()
                                                .newsletterId(newsletter.getId())
                                                .userId(user.getId())
                                                .userEmail(user.getEmail())
                                                .status(NewsletterLogStatus.PENDING)
                                                .build())
                                .toList();

                newsletterLogRepository.saveAll(initialLogs);
                log.info("Created {} pending log entries for newsletter: {}", initialLogs.size(), newsletter.getId());

                // Send newsletter to each target user
                return Flux.fromIterable(targetUsers)
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
                                                List<MailService.TemplateVariable> processingVariables = new ArrayList<>(
                                                                baseVariables);
                                                processingVariables.add(MailService.TemplateVariable.builder()
                                                                .type(MailService.TemplateVariableType.RAW)
                                                                .ref("appName")
                                                                .value("Terres de Laya") // TODO: Get from configuration
                                                                .build());

                                                // Process newsletter content with template variables
                                                String processedSubject = processTemplateVariables(
                                                                newsletter.getSubject(),
                                                                processingVariables);
                                                String processedContent = processTemplateVariables(
                                                                newsletter.getContent(),
                                                                processingVariables);

                                                // Create final template variables for the email
                                                List<MailService.TemplateVariable> templateVariables = new ArrayList<>(
                                                                baseVariables);
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
                                                updateNewsletterLog(newsletter.getId(), user.getId(),
                                                                NewsletterLogStatus.SENT, null);
                                                log.debug("Sent newsletter to: {}", user.getEmail());
                                                return Mono.empty();
                                        } catch (Exception e) {
                                                log.error("Failed to send newsletter to user: {}", user.getEmail(), e);
                                                // Update log entry to FAILED
                                                updateNewsletterLog(newsletter.getId(), user.getId(),
                                                                NewsletterLogStatus.FAILED,
                                                                e.getMessage());
                                                return Mono.empty(); // Continue with other users
                                        }
                                })
                                .then()
                                .doOnSuccess(v -> {
                                        newsletter.setStatus(NewsletterStatus.SENT);
                                        newsletter.setSentAt(OffsetDateTime.now());
                                        newsletter.setRecipientCount(targetUsers.size());
                                        newsletterRepository.save(newsletter);
                                        log.info("Successfully sent newsletter to {} users", targetUsers.size());
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
                                                List<MailService.TemplateVariable> processingVariables = new ArrayList<>(
                                                                baseVariables);
                                                processingVariables.add(MailService.TemplateVariable.builder()
                                                                .type(MailService.TemplateVariableType.RAW)
                                                                .ref("appName")
                                                                .value("Terres de Laya") // TODO: Get from configuration
                                                                .build());

                                                // Process newsletter content with template variables
                                                String processedSubject = processTemplateVariables(
                                                                newsletter.getSubject(),
                                                                processingVariables);
                                                String processedContent = processTemplateVariables(
                                                                newsletter.getContent(),
                                                                processingVariables);

                                                // Create final template variables for the email
                                                List<MailService.TemplateVariable> templateVariables = new ArrayList<>(
                                                                baseVariables);
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
                                                log.error("Failed to send test newsletter to user: {}", user.getEmail(),
                                                                e);
                                                return Mono.error(new CodedException(
                                                                CodedError.EMAIL_SEND_FAILED.getCode(),
                                                                CodedError.EMAIL_SEND_FAILED.getDefaultMessage(),
                                                                Map.of("email", user.getEmail(), "newsletterId",
                                                                                newsletterId),
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
                return Mono.just(
                                newsletterLogRepository.findByNewsletterIdOrderByCreatedAtDesc(newsletterId, pageable));
        }

        public Flux<NewsletterLogEntity> getNewsletterLogsByStatus(UUID newsletterId, NewsletterLogStatus status) {
                log.info("Retrieving logs for newsletter: {} with status: {}", newsletterId, status);
                return Flux.fromIterable(
                                newsletterLogRepository.findByNewsletterIdAndStatusOrderByCreatedAtDesc(newsletterId,
                                                status));
        }

        public Mono<Map<String, Long>> getNewsletterStatistics(UUID newsletterId) {
                log.info("Getting statistics for newsletter: {}", newsletterId);

                long totalLogs = newsletterLogRepository.countByNewsletterId(newsletterId);
                long sentCount = newsletterLogRepository.countByNewsletterIdAndStatus(newsletterId,
                                NewsletterLogStatus.SENT);
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

        private void updateNewsletterLog(UUID newsletterId, UUID userId, NewsletterLogStatus status,
                        String errorMessage) {
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
                                                log.debug("Updated newsletter log for user: {} to status: {}", userId,
                                                                status);
                                        });
                } catch (Exception e) {
                        log.error("Failed to update newsletter log for user: {} and newsletter: {}", userId,
                                        newsletterId, e);
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

        /**
         * Scheduled task to check for newsletters that need to be sent
         * Runs every minute to check for scheduled newsletters
         */
        @Scheduled(fixedRate = 60000) // Run every 60 seconds
        public void processScheduledNewsletters() {
                log.debug("Checking for scheduled newsletters to send");

                OffsetDateTime now = OffsetDateTime.now();
                List<NewsletterEntity> scheduledNewsletters = newsletterRepository
                                .findScheduledNewslettersReadyToSend(NewsletterStatus.SCHEDULED, now);

                if (scheduledNewsletters.isEmpty()) {
                        log.debug("No scheduled newsletters found to send");
                        return;
                }

                log.info("Found {} scheduled newsletters to send", scheduledNewsletters.size());

                for (NewsletterEntity newsletter : scheduledNewsletters) {
                        try {
                                log.info("Processing scheduled newsletter: {} (scheduled for: {})",
                                                newsletter.getSubject(), newsletter.getScheduledAt());

                                // Send the newsletter
                                sendNewsletter(newsletter.getId()).subscribe(
                                                success -> log.info("Successfully sent scheduled newsletter: {}",
                                                                newsletter.getSubject()),
                                                error -> log.error("Failed to send scheduled newsletter: {}",
                                                                newsletter.getSubject(), error));

                        } catch (Exception e) {
                                log.error("Error processing scheduled newsletter: {}", newsletter.getSubject(), e);
                        }
                }
        }

        /**
         * Parse audience configuration from newsletter entity
         */
        private NewsletterAudience parseAudienceFromNewsletter(NewsletterEntity newsletter) {
                try {
                        NewsletterAudience.TypeEnum audienceType = NewsletterAudience.TypeEnum
                                        .valueOf(newsletter.getAudienceType());

                        List<UserType> userTypes = jsonToList(newsletter.getAudienceUserTypes(), UserType.class);
                        List<UUID> userIds = jsonToList(newsletter.getAudienceUserIds(), UUID.class);
                        List<UUID> excludeUserIds = jsonToList(newsletter.getAudienceExcludeUserIds(), UUID.class);

                        return NewsletterAudience.builder()
                                        .type(audienceType)
                                        .userTypes(userTypes)
                                        .userIds(userIds)
                                        .excludeUserIds(excludeUserIds)
                                        .build();
                } catch (Exception e) {
                        log.error("Error parsing audience configuration for newsletter: {}", newsletter.getId(), e);
                        // Fallback to ALL users if parsing fails
                        return NewsletterAudience.builder()
                                        .type(NewsletterAudience.TypeEnum.ALL)
                                        .userTypes(List.of())
                                        .userIds(List.of())
                                        .excludeUserIds(List.of())
                                        .build();
                }
        }

        /**
         * Find target users based on audience configuration and newsletter preferences
         */
        private List<UserEntity> findTargetUsersForNewsletter(NewsletterAudience audience) {
                List<UserEntity> allUsers = usersRepository.findAllWithProperties();

                log.debug("Starting with {} total users", allUsers.size());

                // Apply audience filtering first (most restrictive)
                List<UserEntity> audienceFilteredUsers = switch (audience.getType()) {
                        case ALL -> allUsers;

                        case USER_TYPES -> allUsers.stream()
                                        .filter(user -> audience.getUserTypes()
                                                        .contains(user.getType().toOpenApiUserType()))
                                        .collect(Collectors.toList());

                        case SPECIFIC_USERS -> allUsers.stream()
                                        .filter(user -> audience.getUserIds().contains(user.getId()))
                                        .collect(Collectors.toList());
                };

                log.debug("After audience filtering: {} users", audienceFilteredUsers.size());

                // Apply exclusions
                List<UserEntity> exclusionFilteredUsers = audienceFilteredUsers;
                if (!audience.getExcludeUserIds().isEmpty()) {
                        exclusionFilteredUsers = audienceFilteredUsers.stream()
                                        .filter(user -> !audience.getExcludeUserIds().contains(user.getId()))
                                        .collect(Collectors.toList());
                }

                log.debug("After exclusions: {} users", exclusionFilteredUsers.size());

                // Now filter by newsletter preferences (on the smaller set)
                List<UUID> candidateUserIds = exclusionFilteredUsers.stream()
                                .map(UserEntity::getId)
                                .collect(Collectors.toList());

                List<UUID> userIdsWithNewsletterEnabled = notificationSettingsRepository
                                .findUserIdsWithNewsletterEnabled(candidateUserIds);

                // Ensure users have notification settings if they don't exist yet
                exclusionFilteredUsers.forEach(this::ensureUserHasNotificationSettings);

                // Final filter by newsletter preferences
                List<UserEntity> finalTargetUsers = exclusionFilteredUsers.stream()
                                .filter(user -> userIdsWithNewsletterEnabled.contains(user.getId()))
                                .collect(Collectors.toList());

                log.info("Final filtering: {} -> {} -> {} -> {} users (total -> audience -> exclusions -> newsletter preferences)",
                                allUsers.size(), audienceFilteredUsers.size(), exclusionFilteredUsers.size(),
                                finalTargetUsers.size());

                return finalTargetUsers;
        }

        /**
         * Ensure user has notification settings (create default if missing)
         */
        private void ensureUserHasNotificationSettings(UserEntity user) {
                if (notificationSettingsRepository.findByUserId(user.getId()) == null) {
                        // Create default notification settings with newsletter enabled
                        NotificationSettingsEntity defaultSettings = NotificationSettingsEntity.builder()
                                        .id(UUID.randomUUID())
                                        .user(user)
                                        .enableNotifications(true)
                                        .newsletterEnabled(true) // Default to enabled for new users
                                        .build();

                        notificationSettingsRepository.save(defaultSettings);
                        log.debug("Created default notification settings for user: {}", user.getEmail());
                }
        }
}