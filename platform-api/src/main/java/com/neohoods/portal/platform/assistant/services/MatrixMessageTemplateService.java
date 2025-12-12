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
 * Templates return Markdown format, which is automatically converted to HTML by
 * MatrixMessageService.
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
     * @param variables    Template variables
     * @param locale       Locale for translations
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
            sb.append("[").append(getMessage("matrix.template.pay_now", locale)).append("](").append(paymentLink)
                    .append(")\n");
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
     * Formats a response message with available spaces list in HTML format.
     * Takes the LLM response (without space list) and adds a formatted HTML list of
     * available spaces.
     * 
     * @param llmResponse     The response from LLM (should not contain space
     *                        numbers)
     * @param availableSpaces Map of space numbers to UUIDs (e.g., {"7": "uuid-7",
     *                        "23": "uuid-23"})
     * @param locale          Locale for translations
     * @return Formatted response with HTML list of available spaces
     */
    public String formatResponseWithAvailableSpacesList(String llmResponse, Map<String, String> availableSpaces,
            Locale locale) {
        if (llmResponse == null) {
            llmResponse = "";
        }

        // Extract space numbers and sort them numerically (needed for cleaning)
        List<String> spaceNumbers = new java.util.ArrayList<>();
        if (availableSpaces != null && !availableSpaces.isEmpty()) {
            spaceNumbers = new java.util.ArrayList<>(availableSpaces.keySet());
            spaceNumbers.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                } catch (NumberFormatException e) {
                    return a.compareTo(b);
                }
            });
        }

        // CRITICAL: Remove any list of spaces that the LLM might have generated
        // The backend will generate the list automatically from availableSpaces map
        String cleanResponse = removeSpaceListFromResponse(llmResponse, spaceNumbers);
        cleanResponse = cleanResponse.trim();

        if (cleanResponse.isEmpty()) {
            cleanResponse = getMessage("matrix.reservation.chooseSpace.whichSpaceQuestion", locale);
        }

        // If no available spaces, return the cleaned response as-is
        if (availableSpaces == null || availableSpaces.isEmpty()) {
            return cleanResponse;
        }

        // Build the formatted response with HTML list
        StringBuilder sb = new StringBuilder();

        // Add the LLM response (question)
        sb.append(cleanResponse);

        // Add spacing
        sb.append("\n\n");

        // Add prefix for available spaces
        String prefix = getMessage("matrix.reservation.chooseSpace.availableSpacesPrefix", locale);
        sb.append("**").append(prefix).append("**\n\n");

        // Add HTML-formatted list of spaces
        // Format: <ul><li>Place de parking N°7</li><li>Place de parking
        // N°23</li>...</ul>
        sb.append("<ul>");
        for (String number : spaceNumbers) {
            sb.append("<li>Place de parking N°").append(number).append("</li>");
        }
        sb.append("</ul>");

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

    /**
     * Removes any list of spaces from the LLM response to avoid duplication.
     * The backend will generate the list automatically from availableSpaces map.
     */
    private String removeSpaceListFromResponse(String response, List<String> spaceNumbers) {
        if (response == null || response.isEmpty() || spaceNumbers == null || spaceNumbers.isEmpty()) {
            return response;
        }

        String lowerResponse = response.toLowerCase();

        // Check if response contains space list patterns
        boolean hasList = false;

        // Pattern 1: Check for comma-separated lists (e.g., "7, 23, 45, 67")
        String commaPattern = String.join("|", spaceNumbers);
        if (lowerResponse.matches(".*\\b(" + commaPattern + ")\\s*,\\s*\\d+.*")) {
            hasList = true;
        }

        // Pattern 2: Check for bulleted lists or explicit mentions
        if (!hasList) {
            // Check for incomplete bullet patterns like "- Place " without number
            if (lowerResponse.contains("- place") || lowerResponse.contains("- place de parking")) {
                hasList = true;
            }
            // Check for explicit space number mentions
            if (!hasList) {
                for (String number : spaceNumbers) {
                    if (lowerResponse.contains("place " + number) || lowerResponse.contains("n°" + number)
                            || lowerResponse.contains("n° " + number) || lowerResponse.contains("parking n°" + number)
                            || lowerResponse.matches(".*\\b" + number + "\\b.*")) {
                        hasList = true;
                        break;
                    }
                }
            }
        }

        if (!hasList) {
            return response;
        }

        // Remove common list patterns
        // Pattern 1: Comma-separated lists (e.g., "Voici les places disponibles : 7,
        // 23, 45, 67")
        // Also handles patterns like "7, 23, 34, 45, 56, 67, 78, 89, 91, 102 et 231012"
        String numbersPattern = String.join("|", spaceNumbers);
        // Match "Voici les places disponibles :" followed by any combination of
        // numbers, commas, "et"
        response = response.replaceAll(
                "(?i)(Voici\\s+les?\\s+places?\\s+(de\\s+)?parking\\s+disponibles?\\s*:?\\s*)" +
                        "(" + numbersPattern + "\\s*[,]?\\s*)*(et\\s+)?" + numbersPattern + ".*",
                "$1");
        // Also remove standalone number lists that might remain
        response = response.replaceAll(
                "(" + numbersPattern + "\\s*[,]?\\s*)+(et\\s+)?" + numbersPattern + "\\s*\\.?",
                "");

        // Pattern 2: Lines starting with "- Place" (with or without number, with or
        // without "de parking")
        // This catches incomplete patterns like "- Place " without number
        response = response.replaceAll("(?m)^\\s*-\\s*Place\\s+(de\\s+)?parking\\s+N°\\d+.*$", "");
        response = response.replaceAll("(?m)^\\s*-\\s*Place\\s+\\d+.*$", ""); // Pattern: "- Place 7"
        response = response.replaceAll("(?m)^\\s*-\\s*Place\\s+(de\\s+)?parking\\s*$", ""); // Pattern: "- Place " or "-
                                                                                            // Place de parking "
        response = response.replaceAll("(?m)^\\s*-\\s*Place\\s*$", ""); // Pattern: "- Place" alone

        // Pattern 3: Lines containing "N°X" in list format
        for (String number : spaceNumbers) {
            response = response.replaceAll("(?m)^.*N°\\s*" + number + ".*$", "");
            // Also remove standalone numbers that match space numbers
            response = response.replaceAll("\\b" + number + "\\b(?=\\s*[,.]|$)", "");
        }

        // Pattern 4: Remove "Voici les places disponibles :" or similar prefixes (with
        // optional newline)
        response = response
                .replaceAll("(?i)(Voici\\s+les?\\s+places?\\s+(de\\s+)?parking\\s+disponibles?\\s*:?\\s*\\n?)", "");
        response = response.replaceAll("(?i)(Places?\\s+(de\\s+)?parking\\s+disponibles?\\s*:?\\s*\\n?)", "");

        // Pattern 5: Remove any remaining list-like patterns with "Place de parking N°"
        // followed by numbers
        // This catches patterns like "Place de parking N°7Place de parking N°23" (no
        // separators)
        for (String number : spaceNumbers) {
            response = response.replaceAll("Place\\s+de\\s+parking\\s+N°" + number + "\\s*", "");
            response = response.replaceAll("Place\\s+N°" + number + "\\s*", "");
        }

        // Clean up multiple consecutive newlines and trailing commas
        response = response.replaceAll("\\n{3,}", "\n\n");
        response = response.replaceAll(",\\s*,+", ","); // Remove duplicate commas
        response = response.replaceAll(",\\s*\\.", "."); // Remove comma before period
        // Remove patterns like ", , , , , et 1012" (leftover from partial cleaning)
        response = response.replaceAll(",\\s*,+\\s*et\\s+\\d+", "");
        response = response.replaceAll(",\\s*,\\s*,\\s*,+", ""); // Remove multiple commas
        // Remove "et" followed by a number if it's a leftover from list cleaning
        for (String number : spaceNumbers) {
            response = response.replaceAll("\\s+et\\s+" + number + "\\s*\\.?", "");
        }
        // Remove any remaining "Voici les places disponibles :" prefixes that might be
        // empty
        response = response.replaceAll("(?i)Voici\\s+les?\\s+places?\\s+(de\\s+)?parking\\s+disponibles?\\s*:?\\s*$",
                "");
        // Remove patterns like ": , , , , , et 1012" (colon followed by commas and
        // "et")
        response = response.replaceAll(":\\s*,\\s*,+\\s*(et\\s+\\d+)?", ":");
        // Remove any remaining patterns with just commas and "et"
        response = response.replaceAll(",\\s*,+\\s*et\\s+\\d+", "");
        // Remove patterns like "Voici les places disponibles : , , , , , et 1012"
        response = response.replaceAll(
                "(?i)Voici\\s+les?\\s+places?\\s+(de\\s+)?parking\\s+disponibles?\\s*:?\\s*,\\s*,+\\s*(et\\s+\\d+)?",
                "");
        // Final cleanup: remove any remaining "Voici les places disponibles :" even if
        // followed by commas
        response = response.replaceAll(
                "(?i)Voici\\s+les?\\s+places?\\s+(de\\s+)?parking\\s+disponibles?\\s*:?\\s*,\\s*,+",
                "");

        // Trim and clean up
        return response.trim();
    }
}
