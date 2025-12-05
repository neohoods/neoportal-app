package com.neohoods.portal.platform.services.matrix.assistant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing admin commands for the Matrix bot.
 * Extensible framework for adding new admin commands.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantAdminCommandService {

    private final MatrixAssistantService matrixAssistantService;
    private final MessageSource messageSource;
    private final UsersRepository usersRepository;

    @Value("${neohoods.portal.matrix.mas.admin-users}")
    private String adminUsersConfig;

    /**
     * Interface for an admin command
     */
    @FunctionalInterface
    public interface AdminCommand {
        /**
         * Executes the admin command
         * 
         * @param sender      Matrix user ID of the sender
         * @param messageBody Message content
         * @param locale      User locale for translations
         * @return Response message if command was handled, null otherwise
         */
        String execute(String sender, String messageBody, Locale locale);
    }

    /**
     * List of available admin commands
     * Key: command description (for the prompt), Value: execution function
     */
    private final Map<String, AdminCommand> adminCommands = new HashMap<>();

    /**
     * List of command descriptions (for the system prompt)
     */
    private final List<String> adminCommandDescriptions = new ArrayList<>();

    /**
     * Initializes available admin commands at startup
     */
    @PostConstruct
    public void initializeCommands() {
        // Command: Update bot avatar
        registerCommand(
                "matrix.admin.command.updateAvatar.description",
                (sender, messageBody, locale) -> {
                    log.info("Admin command: update bot avatar from user {}", sender);
                    boolean updated = matrixAssistantService.updateBotAvatar();
                    if (updated) {
                        return messageSource.getMessage("matrix.admin.command.updateAvatar.success", null, locale);
                    } else {
                        return messageSource.getMessage("matrix.admin.command.updateAvatar.failure", null, locale);
                    }
                });

        log.info("Initialized {} admin commands", adminCommands.size());
    }

    /**
     * Registers a new admin command
     * 
     * @param descriptionKey Translation key for command description (for prompt)
     * @param command        Command execution function
     */
    public void registerCommand(String descriptionKey, AdminCommand command) {
        adminCommandDescriptions.add(descriptionKey);

        // Use the description key as the internal command identifier
        // The LLM will interpret user messages in any language based on the system prompt
        adminCommands.put(descriptionKey, command);
    }

    /**
     * Handles an admin command if the message contains one.
     * Uses simple keyword matching - the LLM will interpret commands in any language
     * based on the system prompt descriptions.
     * 
     * @param sender      Matrix user ID of the sender
     * @param messageBody Message content
     * @return Response message if command was handled, null otherwise
     */
    public String handleAdminCommand(String sender, String messageBody) {
        if (sender == null || messageBody == null) {
            return null;
        }

        // Check if user is admin
        if (!isAdminUser(sender)) {
            return null; // Not an admin, not a command
        }

        // Get user locale for translations
        Locale locale = getLocaleForUser(sender);

        String normalizedMessage = messageBody.toLowerCase().trim();

        // Simple keyword-based matching - extracts the main action word from English descriptions
        // The LLM in the system prompt will interpret commands in any language
        for (String descriptionKey : adminCommandDescriptions) {
            String description = messageSource.getMessage(descriptionKey, null, Locale.ENGLISH);
            String normalizedDesc = description.toLowerCase();
            
            // Extract significant keywords (skip common words)
            String[] words = normalizedDesc.split("\\s+");
            for (String word : words) {
                // Skip common/stop words
                if (word.length() < 3 || 
                    word.equals("the") || word.equals("bot") || 
                    word.equals("your") || word.equals("you") ||
                    word.equals("to") || word.equals("a") || word.equals("an")) {
                    continue;
                }
                
                // If message contains a significant keyword from the command, consider it a match
                // The LLM will handle proper interpretation through the system prompt
                if (normalizedMessage.contains(word)) {
                    log.info("Admin command matched (keyword '{}'): '{}' from user {}", word, descriptionKey, sender);
                    return adminCommands.get(descriptionKey).execute(sender, messageBody, locale);
                }
            }
        }

        return null; // Not a recognized command
    }

    /**
     * Gets locale for a Matrix user
     */
    private Locale getLocaleForUser(String matrixUserId) {
        try {
            // Extract username from Matrix user ID
            String username = extractUsernameFromMatrixUserId(matrixUserId);
            if (username != null) {
                String normalizedUsername = username.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                UserEntity user = usersRepository.findByUsername(normalizedUsername);
                if (user != null) {
                    return user.getLocale();
                }
            }
        } catch (Exception e) {
            log.debug("Could not get locale for user {}, using default", matrixUserId);
        }
        return Locale.ENGLISH;
    }

    /**
     * Extracts username from Matrix user ID
     */
    private String extractUsernameFromMatrixUserId(String matrixUserId) {
        if (matrixUserId == null || !matrixUserId.startsWith("@")) {
            return null;
        }
        int colonIndex = matrixUserId.indexOf(':');
        if (colonIndex > 0) {
            return matrixUserId.substring(1, colonIndex);
        }
        return matrixUserId.substring(1);
    }

    /**
     * Checks if a Matrix user ID is an admin user
     * 
     * @param matrixUserId Matrix user ID
     * @return true if user is admin
     */
    public boolean isAdminUser(String matrixUserId) {
        if (matrixUserId == null || matrixUserId.isEmpty() || adminUsersConfig == null || adminUsersConfig.isEmpty()) {
            return false;
        }

        // Parse admin users from config (comma-separated list)
        String[] adminUserIds = adminUsersConfig.split(",");
        for (String adminUserId : adminUserIds) {
            String trimmed = adminUserId.trim();
            if (trimmed.equalsIgnoreCase(matrixUserId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the list of admin command descriptions (for system prompt)
     * 
     * @param locale User locale for translations
     * @return List of descriptions
     */
    public List<String> getAdminCommandDescriptions(Locale locale) {
        List<String> translated = new ArrayList<>();
        for (String key : adminCommandDescriptions) {
            translated.add(messageSource.getMessage(key, null, locale));
        }
        return translated;
    }

    /**
     * Returns the list of available admin commands (for system prompt)
     * Format: "description: pattern1, pattern2, ..."
     * 
     * @param locale User locale for translations
     * @return Formatted list of commands
     */
    public String getAdminCommandsForPrompt(Locale locale) {
        if (adminCommandDescriptions.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(messageSource.getMessage("matrix.admin.commands.header", null, locale)).append("\n");
        for (String descriptionKey : adminCommandDescriptions) {
            String description = messageSource.getMessage(descriptionKey, null, locale);
            sb.append("- ").append(description).append("\n");
        }
        return sb.toString();
    }
}
