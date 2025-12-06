package com.neohoods.portal.platform.services.matrix.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.Locale;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.dao.DataAccessException;
import org.hibernate.HibernateException;
import org.yaml.snakeyaml.Yaml;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.entities.UserType;

import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContext;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP (Model Context Protocol) server for Alfred Matrix assistant.
 * Implements MCP tools allowing the LLM to interact with the system.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "neohoods.portal.matrix.assistant.mcp.enabled", havingValue = "true", matchIfMissing = false)
public class MatrixAssistantMCPServer {

        private final UsersRepository usersRepository;
        private final MatrixAssistantAdminCommandService adminCommandService;
        private final MessageSource messageSource;
        private final MatrixAssistantAuthContextService authContextService;
        private final MatrixMCPResidentHandler residentHandler;
        private final MatrixMCPReservationHandler reservationHandler;
        private final MatrixMCPSpaceHandler spaceHandler;
        private final MatrixMCPHubHandler hubHandler;
        private final MatrixMCPAdminHandler adminHandler;
        private final ResourceLoader resourceLoader;
        private final TransactionTemplate transactionTemplate;

        public MatrixAssistantMCPServer(
                        UsersRepository usersRepository,
                        MessageSource messageSource,
                        MatrixAssistantAuthContextService authContextService,
                        MatrixMCPResidentHandler residentHandler,
                        MatrixMCPReservationHandler reservationHandler,
                        MatrixMCPSpaceHandler spaceHandler,
                        MatrixMCPHubHandler hubHandler,
                        MatrixMCPAdminHandler adminHandler,
                        ResourceLoader resourceLoader,
                        TransactionTemplate transactionTemplate,
                        @Autowired(required = false) MatrixAssistantAdminCommandService adminCommandService) {
                this.usersRepository = usersRepository;
                this.messageSource = messageSource;
                this.authContextService = authContextService;
                this.residentHandler = residentHandler;
                this.reservationHandler = reservationHandler;
                this.spaceHandler = spaceHandler;
                this.hubHandler = hubHandler;
                this.adminHandler = adminHandler;
                this.resourceLoader = resourceLoader;
                this.transactionTemplate = transactionTemplate;
                this.adminCommandService = adminCommandService;
        }

        @Value("${neohoods.portal.matrix.assistant.mcp.enabled}")
        private boolean mcpEnabled;

        private List<MatrixMCPModels.MCPTool> cachedTools = null;

        /**
         * Loads MCP tools from YAML resource file at application startup
         */
        @EventListener(ApplicationReadyEvent.class)
        public void loadToolsFromYaml() {
                log.info("Loading MCP tools from matrix-mcp-tools.yaml");
                try {
                        Resource resource = resourceLoader.getResource("classpath:matrix-mcp-tools.yaml");
                        if (resource.exists() && resource.isReadable()) {
                                try (InputStream is = resource.getInputStream()) {
                                        Yaml yaml = new Yaml();
                                        Map<String, Object> data;
                                        try {
                                                data = yaml.load(is);
                                        } catch (Exception e) {
                                                log.error("CRITICAL: Failed to parse matrix-mcp-tools.yaml. Application cannot start without valid MCP tools configuration.",
                                                                e);
                                                throw new IllegalStateException(
                                                                "Failed to load MCP tools from matrix-mcp-tools.yaml: "
                                                                                + e.getMessage(),
                                                                e);
                                        }

                                        @SuppressWarnings("unchecked")
                                        List<Map<String, Object>> toolsList = (List<Map<String, Object>>) data
                                                        .get("tools");

                                        if (toolsList != null) {
                                                cachedTools = new ArrayList<>();
                                                for (Map<String, Object> toolMap : toolsList) {
                                                        MatrixMCPModels.MCPTool tool = MatrixMCPModels.MCPTool.builder()
                                                                        .name((String) toolMap.get("name"))
                                                                        .description((String) toolMap
                                                                                        .get("description"))
                                                                        .inputSchema((Map<String, Object>) toolMap
                                                                                        .get("inputSchema"))
                                                                        .build();
                                                        cachedTools.add(tool);
                                                }
                                                log.info("Loaded {} MCP tools from matrix-mcp-tools.yaml",
                                                                cachedTools.size());
                                        } else {
                                                log.error("CRITICAL: No 'tools' key found in matrix-mcp-tools.yaml. Application cannot start without MCP tools.");
                                                throw new IllegalStateException(
                                                                "No 'tools' key found in matrix-mcp-tools.yaml");
                                        }
                                } catch (IOException e) {
                                        log.error("CRITICAL: Error reading matrix-mcp-tools.yaml. Application cannot start.",
                                                        e);
                                        throw new IllegalStateException(
                                                        "Failed to read matrix-mcp-tools.yaml: " + e.getMessage(), e);
                                }
                        } else {
                                log.error("CRITICAL: matrix-mcp-tools.yaml not found or not readable. Application cannot start without MCP tools.");
                                throw new IllegalStateException("matrix-mcp-tools.yaml not found or not readable");
                        }
                } catch (IllegalStateException e) {
                        // Re-throw IllegalStateException (our critical errors) - this will prevent boot
                        throw e;
                } catch (Exception e) {
                        log.error("CRITICAL: Error loading MCP tools from matrix-mcp-tools.yaml. Application cannot start.",
                                        e);
                        throw new IllegalStateException(
                                        "Failed to load MCP tools from matrix-mcp-tools.yaml: " + e.getMessage(), e);
                }
        }

        /**
         * Lists all available MCP tools
         */
        public List<MatrixMCPModels.MCPTool> listTools() {
                if (cachedTools == null) {
                        // Fallback: load tools if not already loaded
                        loadToolsFromYaml();
                }
                if (cachedTools == null || cachedTools.isEmpty()) {
                        // Fallback to empty list if loading failed
                        return new ArrayList<>();
                }
                return new ArrayList<>(cachedTools);
        }

        /**
         * Calls an MCP tool with authorization validation
         * 
         * Uses TransactionTemplate to programmatically control transaction boundaries
         * and avoid UnexpectedRollbackException when exceptions are caught and handled.
         * This keeps Hibernate session open when accessing lazy relations.
         */
        public MatrixMCPModels.MCPToolResult callTool(String toolName, Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                // Get trace ID and span ID from MDC for logs
                String traceId = MDC.get("traceId");
                String spanId = MDC.get("spanId");
                log.info("Calling MCP tool: {} with arguments: {} for user: {} [traceId={}, spanId={}]",
                                toolName, arguments, authContext.getMatrixUserId(),
                                traceId != null ? traceId : "N/A", spanId != null ? spanId : "N/A");

                // Use TransactionTemplate to programmatically control transaction
                // This allows us to catch exceptions without marking the transaction as
                // rollback-only
                try {
                        return transactionTemplate.execute(status -> {
                                try {
                                        // Get user entity and locale - only if needed
                                        UserEntity userEntity = null;
                                        Locale locale = Locale.ENGLISH;

                                        // Check if tool requires DM (for sensitive data)
                                        if (requiresDM(toolName)) {
                                                // Check if conversation is public (DM required) - check FIRST before trying to get user
                                                if (authContext.isConversationPublic()) {
                                                        return MatrixMCPModels.MCPToolResult.builder()
                                                                        .isError(true)
                                                                        .content(List.of(MatrixMCPModels.MCPContent
                                                                                        .builder()
                                                                                        .type("text")
                                                                                        .text(translate("matrix.mcp.error.requiresDM",
                                                                                                        locale))
                                                                                        .build()))
                                                                        .build();
                                                }
                                                
                                                // Get user entity - required for DM tools
                                                // Use authContext.getUserEntity() if available (from test context), otherwise try getUser()
                                                if (authContext.getUserEntity().isPresent()) {
                                                        userEntity = authContext.getUserEntity().get();
                                                        locale = getLocale(userEntity);
                                                } else {
                                                        try {
                                                                userEntity = getUser(authContext.getMatrixUserId());
                                                                locale = getLocale(userEntity);
                                                        } catch (IllegalStateException | IllegalArgumentException e) {
                                                                // User not found - return error with translation
                                                                return MatrixMCPModels.MCPToolResult.builder()
                                                                                .isError(true)
                                                                                .content(List.of(MatrixMCPModels.MCPContent
                                                                                                .builder()
                                                                                                .type("text")
                                                                                                .text(translate("matrix.mcp.error.requiresDM",
                                                                                                                locale))
                                                                                                .build()))
                                                                                .build();
                                                        }
                                                }
                                        } else {
                                                // For public tools, try to get user entity for locale, but don't fail
                                                // if not
                                                // found
                                                try {
                                                        userEntity = getUser(authContext.getMatrixUserId());
                                                        locale = getLocale(userEntity);
                                                } catch (Exception e) {
                                                        // User not found - use default locale (English)
                                                        log.debug("User not found for public tool, using default locale: {}",
                                                                        e.getMessage());
                                                }
                                        }
                                        // Note: get_infos, get_announcements, get_applications are public (no security
                                        // in OpenAPI)

                                        // Check admin permissions for admin tools
                                        if (toolName.startsWith("admin_")) {
                                                if (userEntity == null) {
                                                        // Try to get user for admin check
                                                        try {
                                                                userEntity = getUser(authContext.getMatrixUserId());
                                                                locale = getLocale(userEntity);
                                                        } catch (Exception e) {
                                                                return MatrixMCPModels.MCPToolResult.builder()
                                                                                .isError(true)
                                                                                .content(List.of(
                                                                                                MatrixMCPModels.MCPContent
                                                                                                                .builder()
                                                                                                                .type("text")
                                                                                                                .text(translate("matrix.mcp.error.requiresDM",
                                                                                                                                locale))
                                                                                                                .build()))
                                                                                .build();
                                                        }
                                                }

                                                if (!needAdminRole(userEntity)) {
                                                        return MatrixMCPModels.MCPToolResult.builder()
                                                                        .isError(true)
                                                                        .content(List.of(MatrixMCPModels.MCPContent
                                                                                        .builder()
                                                                                        .type("text")
                                                                                        .text(translate("matrix.mcp.error.adminOnly",
                                                                                                        locale))
                                                                                        .build()))
                                                                        .build();
                                                }
                                        }
                                        MatrixMCPModels.MCPToolResult result = switch (toolName) {
                                                case "get_resident_info" ->
                                                        residentHandler.getResidentInfo(arguments, authContext);
                                                case "get_emergency_numbers" ->
                                                        residentHandler.getEmergencyNumbers(authContext);
                                                case "get_reservation_details" ->
                                                        reservationHandler.getReservationDetails(arguments,
                                                                        authContext);
                                                case "create_reservation" ->
                                                        reservationHandler.createReservation(arguments, authContext);
                                                case "create_github_issue" -> createGithubIssue(arguments, authContext);
                                                case "get_space_info" ->
                                                        spaceHandler.getSpaceInfo(arguments, authContext);
                                                case "list_spaces" -> spaceHandler.listSpaces(authContext);
                                                case "check_space_availability" ->
                                                        spaceHandler.checkSpaceAvailability(arguments, authContext);
                                                case "list_my_reservations" ->
                                                        reservationHandler.listMyReservations(arguments, authContext);
                                                case "get_reservation_access_code" ->
                                                        reservationHandler.getReservationAccessCode(arguments,
                                                                        authContext);
                                                case "generate_payment_link" ->
                                                        reservationHandler.generatePaymentLink(arguments, authContext);
                                                // Hub endpoints
                                                case "get_infos" -> hubHandler.getInfos(authContext);
                                                case "get_announcements" ->
                                                        hubHandler.getAnnouncements(arguments, authContext);
                                                case "get_applications" -> hubHandler.getApplications(authContext);
                                                case "get_notifications" -> hubHandler.getNotifications(authContext);
                                                case "get_unread_notifications_count" ->
                                                        hubHandler.getUnreadNotificationsCount(authContext);
                                                case "get_users" -> hubHandler.getUsers(authContext);
                                                // Admin endpoints
                                                case "admin_get_users" -> adminHandler.adminGetUsers(authContext);
                                                case "admin_get_units" -> adminHandler.adminGetUnits(authContext);
                                                case "admin_get_reservations" ->
                                                        adminHandler.adminGetReservations(authContext);
                                                case "admin_get_spaces" -> adminHandler.adminGetSpaces(authContext);
                                                default ->
                                                        throw new IllegalArgumentException("Unknown tool: " + toolName);
                                        };

                                        // Log MCP response with trace ID and span ID (reuse variables already declared)
                                        if (result.isError()) {
                                                log.warn("MCP tool {} returned an error: {} [traceId={}, spanId={}]",
                                                                toolName,
                                                                result.getContent().isEmpty() ? "Unknown error"
                                                                                : result.getContent().get(0).getText(),
                                                                traceId != null ? traceId : "N/A",
                                                                spanId != null ? spanId : "N/A");
                                        } else {
                                                String resultText = result.getContent().stream()
                                                                .map(MatrixMCPModels.MCPContent::getText)
                                                                .filter(text -> text != null)
                                                                .collect(Collectors.joining("\n"));
                                                log.info("MCP tool {} succeeded. Response (first 500 chars): {} [traceId={}, spanId={}]",
                                                                toolName,
                                                                resultText.length() > 500
                                                                                ? resultText.substring(0, 500) + "..."
                                                                                : resultText,
                                                                traceId != null ? traceId : "N/A",
                                                                spanId != null ? spanId : "N/A");
                                        }

                                        return result;
                                } catch (Exception e) {
                                        // Reuse traceId and spanId variables already declared at the beginning of the
                                        // method
                                        log.error("Error calling MCP tool {}: {} [traceId={}, spanId={}]", toolName,
                                                        e.getMessage(),
                                                        traceId != null ? traceId : "N/A",
                                                        spanId != null ? spanId : "N/A", e);

                                        // For database/data access exceptions, we need to let them propagate
                                        // so the transaction can be rolled back properly
                                        if (e instanceof DataAccessException || e instanceof HibernateException) {
                                                // Re-throw to let TransactionTemplate handle rollback
                                                throw e;
                                        }

                                        // For other exceptions (validation, business logic, etc.), return error result
                                        // These don't require transaction rollback
                                        Locale locale = getLocaleFromAuthContext(authContext);
                                        String errorMessage = translate("matrix.mcp.error.generic", locale,
                                                        e.getMessage());
                                        return MatrixMCPModels.MCPToolResult.builder()
                                                        .isError(true)
                                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                        .type("text")
                                                                        .text(errorMessage)
                                                                        .build()))
                                                        .build();
                                }
                        });
                } catch (UnexpectedRollbackException e) {
                        // Handle transaction rollback exceptions
                        // This happens when a transaction was marked as rollback-only
                        // (e.g., due to a DataAccessException or HibernateException)
                        log.error("Transaction rollback error calling MCP tool {}: {} [traceId={}, spanId={}]", toolName,
                                        e.getMessage(),
                                        traceId != null ? traceId : "N/A", spanId != null ? spanId : "N/A", e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        String errorMessage = translate("matrix.mcp.error.generic", locale,
                                        "Database error occurred. Please try again.");
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(errorMessage)
                                                        .build()))
                                        .build();
                } catch (DataAccessException | HibernateException e) {
                        // Handle database exceptions that were re-thrown from TransactionTemplate
                        // These exceptions cause transaction rollback, which is expected
                        log.error("Database error calling MCP tool {}: {} [traceId={}, spanId={}]", toolName,
                                        e.getMessage(),
                                        traceId != null ? traceId : "N/A", spanId != null ? spanId : "N/A", e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        String errorMessage = translate("matrix.mcp.error.generic", locale, e.getMessage());
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(errorMessage)
                                                        .build()))
                                        .build();
                }
        }

        /**
         * Checks if a tool requires a Direct Message (DM) for privacy
         * Some sensitive tools require DM to protect user data
         */
        private boolean requiresDM(String toolName) {
                return "create_reservation".equals(toolName) ||
                                "create_github_issue".equals(toolName) ||
                                "list_my_reservations".equals(toolName) ||
                                "get_reservation_access_code".equals(toolName) ||
                                "generate_payment_link".equals(toolName) ||
                                "get_notifications".equals(toolName) ||
                                "get_unread_notifications_count".equals(toolName) ||
                                toolName.startsWith("admin_"); // Admin tools require DM
        }

        /**
         * Gets UserEntity from Matrix user ID. Throws exception if not found.
         * All Matrix users should have a corresponding UserEntity.
         */
        private UserEntity getUser(String matrixUserId) {
                if (matrixUserId == null || matrixUserId.isEmpty()) {
                        throw new IllegalArgumentException("Matrix user ID cannot be null or empty");
                }

                // Extract username from Matrix user ID (format: @username:server.com)
                String matrixUsername = extractUsernameFromMatrixUserId(matrixUserId);
                if (matrixUsername == null) {
                        throw new IllegalArgumentException("Invalid Matrix user ID format: " + matrixUserId);
                }

                // Normalize username (lowercase, special chars replaced by _)
                String normalizedUsername = matrixUsername.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                UserEntity user = usersRepository.findByUsername(normalizedUsername);

                if (user == null) {
                        throw new IllegalStateException("UserEntity not found for Matrix user ID: " + matrixUserId);
                }

                return user;
        }

        /**
         * Extracts username from Matrix user ID
         * 
         * @param matrixUserId Format: @username:server.com
         * @return username or null
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
         * Checks if user has admin role
         */
        private boolean needAdminRole(UserEntity userEntity) {
                // Check via Matrix admin config
                if (adminCommandService != null && adminCommandService.isAdminUser(userEntity.getUsername())) {
                        return true;
                }

                // Check via UserEntity type
                return userEntity.getType() == UserType.ADMIN;
        }

        /**
         * Checks if user is admin (via Matrix admin config or UserType.ADMIN)
         */
        private boolean isAdminUser(MatrixAssistantAuthContext authContext) {
                if (authContext.getUserEntity().isPresent()) {
                        return needAdminRole(authContext.getUserEntity().get());
                }

                // Fallback: check via Matrix admin config
                if (adminCommandService != null && adminCommandService.isAdminUser(authContext.getMatrixUserId())) {
                        return true;
                }

                return false;
        }

        /**
         * Gets locale from UserEntity, defaults to English
         */
        private Locale getLocale(UserEntity userEntity) {
                return userEntity != null ? userEntity.getLocale() : Locale.ENGLISH;
        }

        /**
         * Gets locale from auth context
         */
        private Locale getLocaleFromAuthContext(MatrixAssistantAuthContext authContext) {
                if (authContext.getUserEntity().isPresent()) {
                        return getLocale(authContext.getUserEntity().get());
                }
                return Locale.ENGLISH;
        }

        /**
         * Gets translated message
         */
        private String translate(String key, Locale locale, Object... args) {
                try {
                        return messageSource.getMessage(key, args, locale);
                } catch (Exception e) {
                        log.warn("Translation key not found: {}, using key as fallback", key);
                        return key;
                }
        }

        /**
         * Gets translated message using auth context locale
         */
        private String translate(String key, MatrixAssistantAuthContext authContext, Object... args) {
                Locale locale = getLocaleFromAuthContext(authContext);
                return translate(key, locale, args);
        }

        private MatrixMCPModels.MCPToolResult createGithubIssue(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                // Future feature: GitHub issue creation will be implemented when needed
                Locale locale = getLocaleFromAuthContext(authContext);
                return MatrixMCPModels.MCPToolResult.builder()
                                .isError(false)
                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                .type("text")
                                                .text(translate("matrix.mcp.githubIssue.notImplemented", locale))
                                                .build()))
                                .build();
        }

}
