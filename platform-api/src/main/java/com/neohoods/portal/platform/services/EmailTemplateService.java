package com.neohoods.portal.platform.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neohoods.portal.platform.entities.EmailTemplateEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.model.EmailTemplate;
import com.neohoods.portal.platform.model.EmailTemplateRequest;
import com.neohoods.portal.platform.repositories.EmailTemplateRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateService {

    private final EmailTemplateRepository emailTemplateRepository;

    public Mono<List<EmailTemplate>> getEmailTemplates(String type) {
        log.info("Retrieving email templates for type: {}", type);

        List<EmailTemplateEntity> entities;
        if (type != null) {
            entities = emailTemplateRepository.findByTypeOrderByCreatedAtDesc(type);
        } else {
            entities = emailTemplateRepository.findAll();
        }

        List<EmailTemplate> templates = entities.stream()
                .map(EmailTemplateEntity::toEmailTemplate)
                .toList();

        log.info("Found {} email templates", templates.size());
        return Mono.just(templates);
    }

    public Mono<EmailTemplate> getEmailTemplate(UUID templateId) {
        log.info("Retrieving email template: {}", templateId);

        return emailTemplateRepository.findById(templateId)
                .map(EmailTemplateEntity::toEmailTemplate)
                .map(Mono::just)
                .orElse(Mono.error(new RuntimeException("Email template not found: " + templateId)));
    }

    @Transactional
    public Mono<EmailTemplate> createEmailTemplate(EmailTemplateRequest request, UUID createdBy) {
        log.info("Creating email template: {} of type: {} by user: {}",
                request.getName(), request.getType(), createdBy);

        // If this template should be active, deactivate others of the same type
        if (Boolean.TRUE.equals(request.getIsActive())) {
            deactivateTemplatesOfType(request.getType().getValue());
        }

        EmailTemplateEntity entity = EmailTemplateEntity.builder()
                .id(UUID.randomUUID())
                .type(request.getType().getValue())
                .name(request.getName())
                .subject(request.getSubject())
                .content(request.getContent())
                .isActive(Boolean.TRUE.equals(request.getIsActive()))
                .createdBy(createdBy)
                .description(request.getDescription())
                .build();

        EmailTemplateEntity savedEntity = emailTemplateRepository.save(entity);
        log.info("Created email template: {} with ID: {}", savedEntity.getName(), savedEntity.getId());

        return Mono.just(savedEntity.toEmailTemplate());
    }

    @Transactional
    public Mono<EmailTemplate> updateEmailTemplate(UUID templateId, EmailTemplateRequest request) {
        log.info("Updating email template: {}", templateId);

        return emailTemplateRepository.findById(templateId)
                .map(entity -> {
                    // If this template should be active, deactivate others of the same type
                    if (Boolean.TRUE.equals(request.getIsActive())) {
                        deactivateTemplatesOfType(request.getType().getValue());
                    }

                    entity.setType(request.getType().getValue());
                    entity.setName(request.getName());
                    entity.setSubject(request.getSubject());
                    entity.setContent(request.getContent());
                    entity.setIsActive(Boolean.TRUE.equals(request.getIsActive()));
                    entity.setDescription(request.getDescription());

                    EmailTemplateEntity savedEntity = emailTemplateRepository.save(entity);
                    log.info("Updated email template: {} with ID: {}", savedEntity.getName(), savedEntity.getId());

                    return savedEntity.toEmailTemplate();
                })
                .map(Mono::just)
                .orElse(Mono.error(new RuntimeException("Email template not found: " + templateId)));
    }

    @Transactional
    public Mono<Void> deleteEmailTemplate(UUID templateId) {
        log.info("Deleting email template: {}", templateId);

        if (emailTemplateRepository.existsById(templateId)) {
            emailTemplateRepository.deleteById(templateId);
            log.info("Deleted email template: {}", templateId);
            return Mono.empty();
        } else {
            return Mono.error(new RuntimeException("Email template not found: " + templateId));
        }
    }

    public Mono<EmailTemplate> getActiveTemplateByType(String type) {
        log.info("Retrieving active email template for type: {}", type);

        return emailTemplateRepository.findActiveByType(type)
                .map(EmailTemplateEntity::toEmailTemplate)
                .map(Mono::just)
                .orElse(Mono.empty());
    }

    @Transactional
    public Mono<Void> testEmailTemplate(UUID templateId, UUID userId, MailService mailService,
                                        UsersRepository usersRepository) {
        log.info("Sending test email for template: {} to user: {}", templateId, userId);

        return emailTemplateRepository.findById(templateId)
                .map(entity -> {
                    // Get user for testing
                    UserEntity user = usersRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found: " + userId));

                    // Create base template variables for processing (appName is added automatically
                    // by MailService)
                    List<MailService.TemplateVariable> baseVariables = List.of(
                            MailService.TemplateVariable.builder()
                                    .type(MailService.TemplateVariableType.RAW)
                                    .ref("username")
                                    .value(user.getUsername())
                                    .build(),
                            MailService.TemplateVariable.builder()
                                    .type(MailService.TemplateVariableType.RAW)
                                    .ref("firstName")
                                    .value(user.getFirstName())
                                    .build(),
                            MailService.TemplateVariable.builder()
                                    .type(MailService.TemplateVariableType.RAW)
                                    .ref("lastName")
                                    .value(user.getLastName())
                                    .build());

                    // Create extended variables for processing (including appName)
                    List<MailService.TemplateVariable> processingVariables = new ArrayList<>(baseVariables);
                    processingVariables.add(MailService.TemplateVariable.builder()
                            .type(MailService.TemplateVariableType.RAW)
                            .ref("appName")
                            .value("Terres de Laya") // TODO: Get from configuration
                            .build());

                    // Process subject and content with template variables
                    String processedSubject = processTemplateVariables(entity.getSubject(), processingVariables);
                    String processedContent = processTemplateVariables(entity.getContent(), processingVariables);

                    // Create final template variables for the email
                    List<MailService.TemplateVariable> templateVariables = new ArrayList<>(baseVariables);
                    templateVariables.add(MailService.TemplateVariable.builder()
                            .type(MailService.TemplateVariableType.RAW)
                            .ref("content")
                            .value(processedContent)
                            .build());

                    try {
                        // Send test email using the template
                        mailService.sendTemplatedEmail(
                                user,
                                processedSubject,
                                "email/custom-template",
                                templateVariables,
                                user.getLocale());

                        log.info("Test email sent successfully for template: {}", templateId);
                        return Mono.<Void>empty();
                    } catch (Exception e) {
                        log.error("Failed to send test email for template: {}", templateId, e);
                        return Mono.<Void>error(e);
                    }
                })
                .orElse(Mono.error(new RuntimeException("Email template not found: " + templateId)));
    }

    private void deactivateTemplatesOfType(String type) {
        log.info("Deactivating all templates of type: {}", type);

        List<EmailTemplateEntity> templatesToDeactivate = emailTemplateRepository.findByTypeAndIsActive(type, true);
        templatesToDeactivate.forEach(template -> {
            template.setIsActive(false);
            emailTemplateRepository.save(template);
        });

        log.info("Deactivated {} templates of type: {}", templatesToDeactivate.size(), type);
    }

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