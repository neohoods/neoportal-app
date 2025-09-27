package com.neohoods.portal.platform.api.admin.emailtemplates;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.EmailTemplatesAdminApiApiDelegate;
import com.neohoods.portal.platform.model.EmailTemplate;
import com.neohoods.portal.platform.model.EmailTemplateRequest;
import com.neohoods.portal.platform.model.EmailTemplateType;
import com.neohoods.portal.platform.model.TestEmailTemplate200Response;
import com.neohoods.portal.platform.model.TestEmailTemplateRequest;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.EmailTemplateService;
import com.neohoods.portal.platform.services.MailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplatesAdminApi implements EmailTemplatesAdminApiApiDelegate {

    private final EmailTemplateService emailTemplateService;
    private final MailService mailService;
    private final UsersRepository usersRepository;

    @Override
    public Mono<ResponseEntity<EmailTemplate>> createEmailTemplate(
            Mono<EmailTemplateRequest> createEmailTemplateRequest,
            ServerWebExchange exchange) {
        return createEmailTemplateRequest
                .flatMap(request -> exchange.getPrincipal()
                        .map(principal -> UUID.fromString(principal.getName()))
                        .flatMap(createdBy -> emailTemplateService.createEmailTemplate(request, createdBy)))
                .map(template -> {
                    log.info("Email template created successfully: {}", template.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(template);
                })
                .onErrorReturn(ResponseEntity.badRequest().build());
    }

    @Override
    public Mono<ResponseEntity<EmailTemplate>> getEmailTemplate(UUID templateId, ServerWebExchange exchange) {
        return emailTemplateService.getEmailTemplate(templateId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error retrieving email template: {}", templateId, e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @Override
    public Mono<ResponseEntity<reactor.core.publisher.Flux<EmailTemplate>>> getEmailTemplates(EmailTemplateType type,
            ServerWebExchange exchange) {
        String typeValue = type != null ? type.getValue() : null;

        return emailTemplateService.getEmailTemplates(typeValue)
                .map(templates -> ResponseEntity.ok(reactor.core.publisher.Flux.fromIterable(templates)))
                .onErrorResume(e -> {
                    log.error("Error retrieving email templates", e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @Override
    public Mono<ResponseEntity<EmailTemplate>> updateEmailTemplate(UUID templateId,
            Mono<EmailTemplateRequest> updateEmailTemplateRequest,
            ServerWebExchange exchange) {
        return updateEmailTemplateRequest
                .flatMap(request -> emailTemplateService.updateEmailTemplate(templateId, request))
                .map(template -> {
                    log.info("Email template updated successfully: {}", templateId);
                    return ResponseEntity.ok(template);
                })
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteEmailTemplate(UUID templateId, ServerWebExchange exchange) {
        return emailTemplateService.deleteEmailTemplate(templateId)
                .then(Mono.fromSupplier(() -> createVoidResponseEntity(HttpStatus.NO_CONTENT)))
                .onErrorResume(e -> {
                    log.error("Error deleting email template: {}", templateId, e);
                    return Mono.just(createVoidResponseEntity(HttpStatus.NOT_FOUND));
                });
    }

    @Override
    public Mono<ResponseEntity<TestEmailTemplate200Response>> testEmailTemplate(UUID templateId,
            Mono<TestEmailTemplateRequest> testEmailTemplateRequest,
            ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(principal -> UUID.fromString(principal.getName()))
                .flatMap(userId -> emailTemplateService.testEmailTemplate(templateId, userId, mailService,
                        usersRepository)
                        .then(Mono.fromCallable(() -> usersRepository.findById(userId)))
                        .map(user -> {
                            log.info("Test email sent successfully for template ID: {}", templateId);
                            TestEmailTemplate200Response response = new TestEmailTemplate200Response();
                            response.setMessage("Test email sent successfully");

                            if (user.isPresent()) {
                                response.setSentTo(user.get().getEmail());
                            }

                            return ResponseEntity.ok(response);
                        }))
                .onErrorResume(e -> {
                    log.error("Error sending test email for template: {}", templateId, e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    private ResponseEntity<Void> createVoidResponseEntity(HttpStatus status) {
        return ResponseEntity.status(status).build();
    }
}