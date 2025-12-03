package com.neohoods.portal.platform.services.matrix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service pour gérer les commandes admin du bot Matrix.
 * Framework extensible pour ajouter de nouvelles commandes admin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantAdminCommandService {

    private final MatrixAssistantService matrixAssistantService;

    @Value("${neohoods.portal.matrix.mas.admin-users:}")
    private String adminUsersConfig;

    /**
     * Interface pour une commande admin
     */
    @FunctionalInterface
    public interface AdminCommand {
        /**
         * Exécute la commande admin
         * 
         * @param sender      Matrix user ID du sender
         * @param messageBody Message content
         * @return Response message if command was handled, null otherwise
         */
        String execute(String sender, String messageBody);
    }

    /**
     * Liste des commandes admin disponibles
     * Key: description de la commande (pour le prompt), Value: fonction d'exécution
     */
    private final Map<String, AdminCommand> adminCommands = new HashMap<>();

    /**
     * Liste des descriptions des commandes (pour le prompt système)
     */
    private final List<String> adminCommandDescriptions = new ArrayList<>();

    /**
     * Initialise les commandes admin disponibles au démarrage
     */
    @PostConstruct
    public void initializeCommands() {
        // Commande: Update bot avatar
        registerCommand(
                "Mettre à jour l'avatar du bot",
                (sender, messageBody) -> {
                    log.info("Admin command: update bot avatar from user {}", sender);
                    boolean updated = matrixAssistantService.updateBotAvatar();
                    if (updated) {
                        return "✅ Avatar mis à jour avec succès !";
                    } else {
                        return "❌ Échec de la mise à jour de l'avatar. Vérifiez les logs pour plus de détails.";
                    }
                });

        log.info("Initialized {} admin commands", adminCommands.size());
    }

    /**
     * Enregistre une nouvelle commande admin
     * 
     * @param description Description de la commande (pour le prompt)
     * @param command     Fonction d'exécution de la commande
     */
    public void registerCommand(String description, AdminCommand command) {
        adminCommandDescriptions.add(description);
        // Créer des patterns de détection pour la commande
        String normalizedDesc = description.toLowerCase();
        adminCommands.put(normalizedDesc, command);

        // Ajouter des patterns communs pour "mettre à jour avatar"
        if (normalizedDesc.contains("avatar")) {
            adminCommands.put("met à jour ton avatar", command);
            adminCommands.put("met a jour ton avatar", command);
            adminCommands.put("mets à jour ton avatar", command);
            adminCommands.put("mets a jour ton avatar", command);
            adminCommands.put("update avatar", command);
            adminCommands.put("update ton avatar", command);
            adminCommands.put("met à jour avatar", command);
            adminCommands.put("mets à jour avatar", command);
        }
    }

    /**
     * Gère une commande admin si le message en contient une
     * 
     * @param sender      Matrix user ID du sender
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

        String normalizedMessage = messageBody.toLowerCase().trim();

        // Try to match against registered commands
        // Priority: exact match > contains match
        String exactMatch = null;
        String containsMatch = null;

        for (Map.Entry<String, AdminCommand> entry : adminCommands.entrySet()) {
            String pattern = entry.getKey();
            if (normalizedMessage.equals(pattern)) {
                exactMatch = pattern;
                break; // Exact match has highest priority
            } else if (normalizedMessage.contains(pattern)) {
                if (containsMatch == null || pattern.length() > containsMatch.length()) {
                    // Prefer longer/more specific patterns
                    containsMatch = pattern;
                }
            }
        }

        // Execute command if matched
        if (exactMatch != null) {
            log.info("Admin command matched (exact): '{}' from user {}", exactMatch, sender);
            return adminCommands.get(exactMatch).execute(sender, messageBody);
        } else if (containsMatch != null) {
            log.info("Admin command matched (contains): '{}' from user {}", containsMatch, sender);
            return adminCommands.get(containsMatch).execute(sender, messageBody);
        }

        return null; // Not a recognized command
    }

    /**
     * Vérifie si un Matrix user ID est un admin user
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
     * Retourne la liste des descriptions des commandes admin (pour le prompt
     * système)
     * 
     * @return Liste des descriptions
     */
    public List<String> getAdminCommandDescriptions() {
        return new ArrayList<>(adminCommandDescriptions);
    }

    /**
     * Retourne la liste des commandes admin disponibles (pour le prompt système)
     * Format: "description: pattern1, pattern2, ..."
     * 
     * @return Liste formatée des commandes
     */
    public String getAdminCommandsForPrompt() {
        if (adminCommandDescriptions.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Commandes admin disponibles (réservées aux administrateurs):\n");
        for (String description : adminCommandDescriptions) {
            sb.append("- ").append(description).append("\n");
        }
        return sb.toString();
    }
}
