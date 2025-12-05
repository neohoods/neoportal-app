package com.neohoods.portal.platform.services.matrix.mcp;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;

import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.services.matrix.assistant.MatrixAssistantAuthContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Base handler for MCP tools with common utilities
 */
@Slf4j
public abstract class MatrixMCPBaseHandler {

        protected final MessageSource messageSource;
        protected final UsersRepository usersRepository;
        protected final MatrixAssistantAdminCommandService adminCommandService;

        protected MatrixMCPBaseHandler(MessageSource messageSource, UsersRepository usersRepository,
                        @Autowired(required = false) MatrixAssistantAdminCommandService adminCommandService) {
                this.messageSource = messageSource;
                this.usersRepository = usersRepository;
                this.adminCommandService = adminCommandService;
        }

        /**
         * Gets UserEntity from Matrix user ID. Throws exception if not found.
         */
        protected UserEntity getUser(String matrixUserId) {
                if (matrixUserId == null || matrixUserId.isEmpty()) {
                        throw new IllegalArgumentException("Matrix user ID cannot be null or empty");
                }

                String matrixUsername = extractUsernameFromMatrixUserId(matrixUserId);
                if (matrixUsername == null) {
                        throw new IllegalArgumentException("Invalid Matrix user ID format: " + matrixUserId);
                }

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
        protected String extractUsernameFromMatrixUserId(String matrixUserId) {
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
        protected boolean needAdminRole(UserEntity userEntity) {
                if (adminCommandService != null && adminCommandService.isAdminUser(userEntity.getUsername())) {
                        return true;
                }
                return userEntity.getType() == com.neohoods.portal.platform.entities.UserType.ADMIN;
        }

        /**
         * Gets locale from UserEntity, defaults to English
         */
        protected Locale getLocale(UserEntity userEntity) {
                return userEntity != null ? userEntity.getLocale() : Locale.ENGLISH;
        }

        /**
         * Gets locale from auth context
         */
        protected Locale getLocaleFromAuthContext(MatrixAssistantAuthContext authContext) {
                if (authContext.getUserEntity().isPresent()) {
                        return getLocale(authContext.getUserEntity().get());
                }
                return Locale.ENGLISH;
        }

        /**
         * Gets translated message
         */
        protected String translate(String key, Locale locale, Object... args) {
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
        protected String translate(String key, MatrixAssistantAuthContext authContext, Object... args) {
                Locale locale = getLocaleFromAuthContext(authContext);
                return translate(key, locale, args);
        }
}
