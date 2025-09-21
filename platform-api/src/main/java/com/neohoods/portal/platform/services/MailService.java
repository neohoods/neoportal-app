package com.neohoods.portal.platform.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.neohoods.portal.platform.entities.UserEntity;
import com.nimbusds.jose.util.Pair;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final SpringTemplateEngine templateEngine;
    private final MessageSource messageSource;
    private final RestTemplate restTemplate;

    @Value("${mailersend.api-key}")
    private String mailerSendApiKey;

    @Value("${mailersend.from.email}")
    private String mailerSendFromEmail;

    @Value("${mailersend.from.name}")
    private String mailerSendFromName;

    @Value("${neohoods.portal.email.template.logo-url}")
    private String logoUrl;

    @Value("${neohoods.portal.email.template.app-name}")
    private String appName;

    @Value("${neohoods.portal.email.template.leafs-url}")
    private String leafsUrl;

    @Value("${neohoods.portal.email.template.leafs-accent-url}")
    private String leafsAccentUrl;

    @Value("${neohoods.portal.email.template.notifications-url}")
    private String notificationsUrl;

    public void sendMail(String to, String subject, String htmlContent) {
        log.info("Sending email via MailerSend to: {}, subject: {}", to, subject);

        try {
            // Prepare the request body
            Map<String, Object> requestBody = Map.of(
                    "from", Map.of(
                            "email", mailerSendFromEmail,
                            "name", mailerSendFromName),
                    "to", List.of(Map.of(
                            "email", to,
                            "name", "")),
                    "subject", subject,
                    "html", htmlContent);

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(mailerSendApiKey);

            // Create request entity
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Send request
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.mailersend.com/v1/email",
                    HttpMethod.POST,
                    requestEntity,
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent successfully via MailerSend to: {}, status: {}", to, response.getStatusCode());
            } else {
                log.error("Failed to send email via MailerSend to: {}, status: {}, body: {}. " +
                        "User signup will continue, but email verification may need to be requested manually.",
                        to, response.getStatusCode(), response.getBody());
                // Don't throw exception - let signup continue
            }

        } catch (Exception ex) {
            log.error("Exception while sending email via MailerSend to: {}. " +
                    "User signup will continue, but email verification may need to be requested manually.", to, ex);
            // Don't throw exception - let signup continue
        }
    }

    public void sendTemplatedEmail(UserEntity user, String subject, String templateName,
            List<TemplateVariable> variables, Locale locale) {

        if (user.getEmail().endsWith("example.com")) {
            log.info("Skipping email notification for @example.com mails");
            return;
        }

        // Create a mutable copy of the variables list
        List<TemplateVariable> mutableVariables = new ArrayList<>(variables);

        mutableVariables.add(
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("logoUrl")
                        .value(logoUrl)
                        .build());
        mutableVariables.add(
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("appName")
                        .value(appName)
                        .build());
        mutableVariables.add(
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("leafsUrl")
                        .value(leafsUrl)
                        .build());
        mutableVariables.add(
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("leafsAccentUrl")
                        .value(leafsAccentUrl)
                        .build());
        mutableVariables.add(
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("notificationsUrl")
                        .value(notificationsUrl)
                        .build());

        Map<String, Object> variablesMap = mutableVariables.stream()
                .map(v -> {
                    switch (v.type) {
                        case TRANSLATABLE_TEXT -> {
                            return Pair.of(v.ref,
                                    messageSource.getMessage(v.translateKey, v.args, locale));
                        }
                        default -> {
                            return Pair.of(v.ref, v.value == null ? "" : v.value);
                        }
                    }
                })
                .collect(Collectors.toMap(
                        Pair::getLeft,
                        Pair::getRight));

        Context context = new Context(locale);
        context.setVariables(variablesMap);

        try {
            String htmlContent = templateEngine.process(templateName, context);
            log.debug("Template processed successfully");

            // Only translate subject if it looks like a translation key (contains dots or
            // underscores)
            String finalSubject;
            if (subject.contains(".") || subject.contains("_")) {
                try {
                    finalSubject = messageSource.getMessage(subject, null, locale);
                } catch (Exception e) {
                    // If translation fails, use the subject as-is
                    log.debug("Subject '{}' is not a translation key, using as-is", subject);
                    finalSubject = subject;
                }
            } else {
                finalSubject = subject;
            }

            sendMail(user.getEmail(), finalSubject, htmlContent);
        } catch (Exception e) {
            log.error("Failed to process template: {} for user: {}. " +
                    "User signup will continue, but email verification may need to be requested manually.",
                    templateName, user.getEmail(), e);
            // Don't throw exception - let signup continue
        }
    }

    @Data
    @Builder
    public static class TemplateVariable {
        public TemplateVariableType type;
        public String ref;
        public String translateKey;
        public Object value;
        public Object[] args;
    }

    public enum TemplateVariableType {
        TRANSLATABLE_TEXT,
        RAW
    }
}