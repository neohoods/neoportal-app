package com.neohoods.portal.platform.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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

    @Value("${neohoods.portal.frontend-url}")
    private String frontendUrl;

    @Value("${neohoods.portal.mail.silent-mode:false}")
    private boolean mailSilentMode;

    @Value("${neohoods.portal.mail.whitelist:}")
    private String mailWhitelistString;

    /**
     * Get the mail whitelist as a list
     */
    private List<String> getMailWhitelist() {
        if (mailWhitelistString == null || mailWhitelistString.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(mailWhitelistString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Check if email should be sent based on silent mode and whitelist configuration
     */
    private boolean shouldSendEmail(String to) {
        // If silent mode is disabled, send all emails
        if (!mailSilentMode) {
            return true;
        }

        // Get whitelist
        List<String> whitelist = getMailWhitelist();

        // If silent mode is enabled but whitelist is empty, don't send any emails
        if (whitelist.isEmpty()) {
            log.debug("Mail silent mode enabled with empty whitelist - blocking email to: {}", to);
            return false;
        }

        // Check if email is in whitelist
        boolean isWhitelisted = whitelist.stream()
                .anyMatch(whitelistedEmail -> whitelistedEmail.equalsIgnoreCase(to));

        if (isWhitelisted) {
            log.debug("Email to {} is whitelisted - allowing send", to);
            return true;
        } else {
            log.info("Mail silent mode enabled - blocking email to: {} (not in whitelist)", to);
            return false;
        }
    }

    public void sendMail(String to, String subject, String htmlContent) {
        // Check if email should be sent
        if (!shouldSendEmail(to)) {
            log.info("Email blocked by silent mode: to={}, subject={}", to, subject);
            return;
        }

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
                log.info("Email sent successfully via MailerSend to: {}, status: {}", to,
                        response.getStatusCode());
            } else {
                log.error("Failed to send email via MailerSend to: {}, status: {}, body: {}. " +
                                "User signup will continue, but email verification may need to be requested manually.",
                        to, response.getStatusCode(), response.getBody());
                // Don't throw exception - let signup continue
            }

        } catch (Exception ex) {
            log.error("Exception while sending email via MailerSend to: {}. " +
                            "User signup will continue, but email verification may need to be requested manually.",
                    to, ex);
            // Don't throw exception - let signup continue
        }
    }

    public void sendTemplatedEmail(UserEntity user, String subject, String templateName,
                                   List<TemplateVariable> variables, Locale locale) {

        if (user.getEmail().endsWith("example.com")) {
            log.info("Skipping email notification for @example.com mails");
            return;
        }

        log.info("MailService - appName: '{}', logoUrl: '{}', locale: {}", appName, logoUrl, locale);

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

        // Add pre-translated team signature
        String teamSignature = messageSource.getMessage("email.newsletter.team_signature",
                new Object[]{appName},
                locale);
        mutableVariables.add(
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("teamSignature")
                        .value(teamSignature)
                        .build());

        Map<String, Object> variablesMap = mutableVariables.stream()
                .map(v -> {
                    switch (v.type) {
                        case TRANSLATABLE_TEXT -> {
                            return Pair.of(v.ref,
                                    messageSource.getMessage(v.translateKey, v.args,
                                            locale));
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

    public void sendReservationReminderEmail(UserEntity user, String spaceName,
                                             String startDate, String endDate, String accessCode, String spaceRules,
                                             String spaceInstructions, Locale locale) {

        List<TemplateVariable> variables = List.of(
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("spaceName")
                        .value(spaceName)
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("startDate")
                        .value(startDate)
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("endDate")
                        .value(endDate)
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("accessCode")
                        .value(accessCode)
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("spaceRules")
                        .value(spaceRules)
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("spaceInstructions")
                        .value(spaceInstructions)
                        .build());

        sendTemplatedEmail(user, "reservations.email.reminder.title",
                "email/reservation-reminder", variables, locale);
    }

    public void sendReservationConfirmationEmail(UserEntity user, String spaceName,
                                                 String startDate, String endDate, String accessCode, UUID reservationId, UUID spaceId,
                                                 Locale locale) {

        List<TemplateVariable> variables = List.of(
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("spaceName")
                        .value(spaceName != null ? spaceName : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("startDate")
                        .value(startDate != null ? startDate : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("endDate")
                        .value(endDate != null ? endDate : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("accessCode")
                        .value(accessCode != null ? accessCode : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("reservationUrl")
                        .value(frontendUrl + "/spaces/reservations/" + reservationId)
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("spaceUrl")
                        .value(frontendUrl + "/spaces/detail/" + spaceId)
                        .build());

        sendTemplatedEmail(user, "reservations.email.confirmation.title",
                "email/reservation-confirmation", variables, locale);
    }

    /**
     * Send templated email to an email address (not a user entity)
     */
    private void sendTemplatedEmailToAddress(String email, String subject, String templateName,
                                             List<TemplateVariable> variables, Locale locale) {
        try {
            Context context = new Context(locale);
            for (TemplateVariable variable : variables) {
                context.setVariable(variable.getRef(), variable.getValue());
            }

            String htmlContent = templateEngine.process(templateName, context);

            String finalSubject;
            if (subject != null && messageSource != null) {
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

            sendMail(email, finalSubject, htmlContent);
        } catch (Exception e) {
            log.error("Failed to process template: {} for email: {}", templateName, email, e);
        }
    }

    public void sendCleaningBookingConfirmationEmail(String email, String spaceName, String checkoutDate,
                                                     String cleaningDate, String cleaningTime, String guestName, String guestEmail,
                                                     String calendarUrl, Locale locale) {

        List<TemplateVariable> variables = new ArrayList<>();
        variables.add(TemplateVariable.builder()
                .type(TemplateVariableType.RAW)
                .ref("spaceName")
                .value(spaceName != null ? spaceName : "")
                .build());
        variables.add(TemplateVariable.builder()
                .type(TemplateVariableType.RAW)
                .ref("checkoutDate")
                .value(checkoutDate != null ? checkoutDate : "")
                .build());
        variables.add(TemplateVariable.builder()
                .type(TemplateVariableType.RAW)
                .ref("cleaningDate")
                .value(cleaningDate != null ? cleaningDate : "")
                .build());
        variables.add(TemplateVariable.builder()
                .type(TemplateVariableType.RAW)
                .ref("cleaningTime")
                .value(cleaningTime != null ? cleaningTime : "")
                .build());
        variables.add(TemplateVariable.builder()
                .type(TemplateVariableType.RAW)
                .ref("guestName")
                .value(guestName != null ? guestName : "")
                .build());
        variables.add(TemplateVariable.builder()
                .type(TemplateVariableType.RAW)
                .ref("guestEmail")
                .value(guestEmail != null ? guestEmail : "")
                .build());
        if (calendarUrl != null) {
            variables.add(TemplateVariable.builder()
                    .type(TemplateVariableType.RAW)
                    .ref("calendarUrl")
                    .value(calendarUrl)
                    .build());
        }

        sendTemplatedEmailToAddress(email, "cleaning.email.bookingConfirmation.subject",
                "email/cleaning-booking-confirmation", variables, locale);
    }

    public void sendCleaningReminderEmail(String email, String spaceName, String cleaningDate,
                                          String cleaningTime, String guestName, Locale locale) {

        List<TemplateVariable> variables = List.of(
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("spaceName")
                        .value(spaceName != null ? spaceName : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("cleaningDate")
                        .value(cleaningDate != null ? cleaningDate : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("cleaningTime")
                        .value(cleaningTime != null ? cleaningTime : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("guestName")
                        .value(guestName != null ? guestName : "")
                        .build());

        sendTemplatedEmailToAddress(email, "cleaning.email.reminder.subject",
                "email/cleaning-reminder", variables, locale);
    }

    public void sendCleaningCancellationEmail(String email, String spaceName, String checkoutDate,
                                              String cleaningDate, String cleaningTime, String guestName, String guestEmail,
                                              Locale locale) {

        List<TemplateVariable> variables = List.of(
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("spaceName")
                        .value(spaceName != null ? spaceName : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("checkoutDate")
                        .value(checkoutDate != null ? checkoutDate : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("cleaningDate")
                        .value(cleaningDate != null ? cleaningDate : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("cleaningTime")
                        .value(cleaningTime != null ? cleaningTime : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("guestName")
                        .value(guestName != null ? guestName : "")
                        .build(),
                TemplateVariable.builder()
                        .type(TemplateVariableType.RAW)
                        .ref("guestEmail")
                        .value(guestEmail != null ? guestEmail : "")
                        .build());

        sendTemplatedEmailToAddress(email, "cleaning.email.cancellation.subject",
                "email/cleaning-cancellation", variables, locale);
    }

    public enum TemplateVariableType {
        RAW,
        TRANSLATABLE_TEXT
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
}