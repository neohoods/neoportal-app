package com.neohoods.portal.platform.assistant.services;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.services.matrix.space.MatrixMessageService;
import com.neohoods.portal.platform.spaces.entities.ReservationEntity;
import com.neohoods.portal.platform.spaces.entities.SpaceEntity;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing message templates for Matrix assistant agents.
 * Templates return Markdown format, which is automatically converted to HTML by MatrixMessageService.
 */
@Service
@Slf4j
public class MatrixMessageTemplateService {

    private final MessageSource messageSource;
    
    @Autowired(required = false)
    private MatrixMessageService matrixMessageService;
    
    public MatrixMessageTemplateService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Renders a template with variables
     * 
     * @param templateName Template name
     * @param variables Template variables
     * @param locale Locale for translations
     * @return Rendered Markdown template
     */
    public String renderTemplate(String templateName, Map<String, Object> variables, Locale locale) {
        // For now, use simple template rendering
        // In the future, could use a template engine like Mustache
        String template = getTemplate(templateName, locale);
        return replaceVariables(template, variables);
    }

    /**
     * Renders availability list template
     */
    public String renderAvailabilityList(List<SpaceEntity> spaces, Locale locale) {
        if (spaces == null || spaces.isEmpty()) {
            return getMessage("matrix.template.no_spaces_available", locale);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(getMessage("matrix.template.available_spaces", locale)).append("**\n\n");
        
        for (SpaceEntity space : spaces) {
            sb.append("• **").append(space.getName()).append("**");
            if (space.getDescription() != null && !space.getDescription().isEmpty()) {
                sb.append(" - ").append(space.getDescription());
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }

    /**
     * Renders space details template
     */
    public String renderSpaceDetails(SpaceEntity space, Locale locale) {
        if (space == null) {
            return getMessage("matrix.template.space_not_found", locale);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(space.getName()).append("**\n\n");
        
        if (space.getDescription() != null && !space.getDescription().isEmpty()) {
            sb.append(space.getDescription()).append("\n\n");
        }
        
        if (space.getTenantPrice() != null) {
            sb.append("**").append(getMessage("matrix.template.price", locale)).append(":** ")
                    .append(space.getTenantPrice()).append(" ").append(space.getCurrency()).append("\n");
        }
        
        if (space.getCapacity() != null && space.getCapacity() > 0) {
            sb.append("**").append(getMessage("matrix.template.capacity", locale)).append(":** ")
                    .append(space.getCapacity()).append("\n");
        }
        
        return sb.toString();
    }

    /**
     * Renders space selection template
     */
    public String renderSpaceSelection(List<SpaceEntity> spaces, Locale locale) {
        return renderAvailabilityList(spaces, locale);
    }

    /**
     * Renders reservation pending payment template
     */
    public String renderReservationPendingPayment(ReservationEntity reservation, String paymentLink, Locale locale) {
        if (reservation == null) {
            return getMessage("matrix.template.reservation_not_found", locale);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("✅ **").append(getMessage("matrix.template.reservation_created", locale)).append("**\n\n");
        
        if (reservation.getSpace() != null) {
            sb.append("**").append(getMessage("matrix.template.space", locale)).append(":** ")
                    .append(reservation.getSpace().getName()).append("\n");
        }
        
        if (reservation.getStartDate() != null && reservation.getEndDate() != null) {
            sb.append("**").append(getMessage("matrix.template.dates", locale)).append(":** ")
                    .append(formatDate(reservation.getStartDate(), locale))
                    .append(" - ")
                    .append(formatDate(reservation.getEndDate(), locale))
                    .append("\n");
        }
        
        // Note: ReservationEntity doesn't have startTime/endTime fields
        // Times are stored in the space availability or calculated from dates
        
        if (paymentLink != null && !paymentLink.isEmpty()) {
            sb.append("\n**").append(getMessage("matrix.template.payment_required", locale)).append("**\n");
            sb.append("[").append(getMessage("matrix.template.pay_now", locale)).append("](").append(paymentLink).append(")\n");
        }
        
        return sb.toString();
    }

    /**
     * Renders payment confirmation template
     */
    public String renderPaymentConfirmation(ReservationEntity reservation, Locale locale) {
        if (reservation == null) {
            return getMessage("matrix.template.reservation_not_found", locale);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("💳 **").append(getMessage("matrix.template.payment_confirmed", locale)).append("**\n\n");
        
        if (reservation.getSpace() != null) {
            sb.append("**").append(getMessage("matrix.template.space", locale)).append(":** ")
                    .append(reservation.getSpace().getName()).append("\n");
        }
        
        if (reservation.getStartDate() != null && reservation.getEndDate() != null) {
            sb.append("**").append(getMessage("matrix.template.dates", locale)).append(":** ")
                    .append(formatDate(reservation.getStartDate(), locale))
                    .append(" - ")
                    .append(formatDate(reservation.getEndDate(), locale))
                    .append("\n");
        }
        
        // Note: ReservationEntity doesn't have startTime/endTime fields
        // Times are stored in the space availability or calculated from dates
        
        sb.append("\n").append(getMessage("matrix.template.reservation_confirmed", locale));
        
        return sb.toString();
    }

    /**
     * Gets a template by name
     */
    private String getTemplate(String templateName, Locale locale) {
        // For now, return simple templates
        // In the future, could load from files or database
        return getMessage("matrix.template." + templateName, locale);
    }

    /**
     * Replaces variables in a template
     */
    private String replaceVariables(String template, Map<String, Object> variables) {
        if (template == null || variables == null) {
            return template;
        }
        
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(key, value);
        }
        
        return result;
    }

    /**
     * Gets a message from MessageSource
     */
    private String getMessage(String key, Locale locale) {
        try {
            return messageSource.getMessage(key, null, key, locale);
        } catch (Exception e) {
            log.warn("Message key not found: {}", key);
            return key;
        }
    }

    /**
     * Formats a date according to locale
     */
    private String formatDate(LocalDate date, Locale locale) {
        if (date == null) {
            return "";
        }
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale));
    }
}

