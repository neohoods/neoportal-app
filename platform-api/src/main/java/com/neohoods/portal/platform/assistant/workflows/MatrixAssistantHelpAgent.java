package com.neohoods.portal.platform.assistant.workflows;

import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.assistant.services.MatrixAssistantAgentContextService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Agent dedicated to handling help questions.
 * Returns a pre-templated response translated in the user's locale.
 * No LLM call needed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.ai.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantHelpAgent {

    private final MessageSource messageSource;
    private final MatrixAssistantAgentContextService agentContextService;

    /**
     * Handles help questions and returns a pre-templated response
     * translated in the user's locale
     */
    public Mono<String> handleMessage(
            String userMessage,
            MatrixAssistantAuthContext authContext) {

        log.info("Help agent handling message: {} (user: {})",
                userMessage.substring(0, Math.min(50, userMessage.length())),
                authContext.getMatrixUserId());

        // Get locale from context or default to French
        Locale locale = getLocaleFromContext(authContext);

        // Build help message with translations
        StringBuilder helpMessage = new StringBuilder();
        helpMessage.append(messageSource.getMessage("matrix.help.intro", null, locale)).append("\n\n");

        // Emergency contacts
        helpMessage.append("- ").append(messageSource.getMessage("matrix.help.emergencyContacts", null, locale))
                .append("\n");

        // Community information
        helpMessage.append("- ").append(messageSource.getMessage("matrix.help.communityInfo", null, locale))
                .append(" ").append(messageSource.getMessage("matrix.help.requiresAuth", null, locale))
                .append("\n");

        // Community announcements
        helpMessage.append("- ").append(messageSource.getMessage("matrix.help.communityAnnouncements", null, locale))
                .append(" ").append(messageSource.getMessage("matrix.help.requiresAuth", null, locale))
                .append("\n");

        // Space reservations
        helpMessage.append("- ").append(messageSource.getMessage("matrix.help.spaceReservations", null, locale))
                .append(" ").append(messageSource.getMessage("matrix.help.requiresAuth", null, locale))
                .append("\n");

        helpMessage.append("\n");
        helpMessage.append(messageSource.getMessage("matrix.help.closing", null, locale));

        return Mono.just(helpMessage.toString());
    }

    /**
     * Gets locale from context or defaults to French
     */
    private Locale getLocaleFromContext(MatrixAssistantAuthContext authContext) {
        String roomId = authContext.getRoomId();
        if (roomId == null) {
            return Locale.FRENCH;
        }

        MatrixAssistantAgentContextService.AgentContext context = agentContextService.getContext(roomId);
        if (context == null) {
            return Locale.FRENCH;
        }

        String localeStr = context.getWorkflowStateValue("locale", String.class);
        if (localeStr == null || localeStr.isEmpty()) {
            return Locale.FRENCH;
        }

        return Locale.forLanguageTag(localeStr);
    }
}

