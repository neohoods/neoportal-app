package com.neohoods.portal.platform.exceptions;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CodedError {
        // Authentication errors (1000-1999)
        USER_NOT_FOUND("AUTH001", "User not found", "auth/user-not-found"),
        INVALID_CREDENTIALS("AUTH002", "Invalid username or password", "auth/invalid-credentials"),
        EMAIL_ALREADY_VERIFIED("AUTH003", "Email is already verified", "auth/email-already-verified"),
        INVALID_VERIFICATION_TOKEN("AUTH004", "Invalid or expired verification token",
                        "auth/invalid-verification-token"),
        USER_ALREADY_EXISTS("AUTH006", "User already exists", "auth/user-already-exists"),
        AUTH0_REGISTRATION_FAILED("AUTH007", "Failed to register user in Auth0", "auth/auth0-registration-failed"),
        AUTH0_TOKEN_ERROR("AUTH008", "Failed to get access token from Auth0", "auth/auth0-token-error"),
        INVALID_EMAIL_FORMAT("AUTH009", "Invalid email format", "auth/invalid-email-format"),
        WEAK_PASSWORD("AUTH010", "Password does not meet security requirements", "auth/weak-password"),
        USERNAME_TOO_SHORT("AUTH011", "Username must be at least 3 characters long", "auth/username-too-short"),
        USERNAME_INVALID_CHARACTERS("AUTH012", "Username contains invalid characters",
                        "auth/username-invalid-characters"),
        DATABASE_SAVE_ERROR("AUTH013", "Failed to save user to database", "auth/database-save-error"),
        EMAIL_VERIFICATION_TOKEN_ERROR("AUTH014", "Failed to create email verification token",
                        "auth/email-verification-token-error"),
        EMAIL_SEND_ERROR("AUTH015", "Failed to send verification email", "auth/email-send-error"),
        AUTH0_USER_DELETE_ERROR("AUTH016", "Failed to delete user from Auth0 during rollback",
                        "auth/auth0-user-delete-error"),
        EMAIL_ALREADY_EXISTS("AUTH017", "This email is already used by another user", "auth/email-already-exists"),

        // Notification errors (2000-2999)
        NOTIFICATION_SETTINGS_NOT_FOUND("NOT001", "Notification settings not found",
                        "notifications/settings-not-found"),

        // Authorization errors (3000-3999)
        INSUFFICIENT_PERMISSIONS("AUTH005", "You don't have sufficient permissions to perform this action",
                        "auth/insufficient-permissions"),

        // Resource errors (3000-3999)
        ITEM_NOT_FOUND("RES001", "Item not found", "resources/item-not-found"),
        ITEM_NOT_AVAILABLE("RES002", "Item is not available", "resources/item-not-available"),
        ITEM_ALREADY_RESERVED("RES003", "Item is already reserved", "resources/item-already-reserved"),
        LIBRARY_NOT_FOUND("RES004", "Library not found", "resources/library-not-found"),
        COMMUNITY_NOT_FOUND("RES005", "Community not found", "resources/community-not-found"),
        MEMBER_NOT_FOUND("RES006", "Member not found", "resources/member-not-found"),
        CUSTOM_PAGE_NOT_FOUND("RES007", "Custom page not found", "resources/custom-page-not-found"),
        BORROW_RECORD_NOT_FOUND("RES008", "Borrow record not found", "resources/borrow-record-not-found"),
        HELP_ARTICLE_NOT_FOUND("RES009", "Help article not found", "resources/help-article-not-found"),
        HELP_CATEGORY_NOT_FOUND("RES010", "Help category not found", "resources/help-category-not-found"),
        SETTINGS_NOT_FOUND("RES011", "Settings not found", "resources/settings-not-found"),
        CATEGORY_NOT_FOUND("RES012", "Category not found", "resources/category-not-found"),
        APPLICATION_NOT_FOUND("RES013", "Application not found", "resources/application-not-found"),
        INFOS_NOT_FOUND("RES014", "Community infos not found", "resources/infos-not-found"),
        ANNOUNCEMENT_NOT_FOUND("RES015", "Announcement not found", "resources/announcement-not-found"),
        NEWSLETTER_NOT_FOUND("RES016", "Newsletter not found", "resources/newsletter-not-found"),
        NEWSLETTER_OR_USER_NOT_FOUND("RES017", "Newsletter or user not found",
                        "resources/newsletter-or-user-not-found"),
        INVALID_NEWSLETTER_STATUS("VAL006", "Invalid newsletter status for this operation",
                        "validation/invalid-newsletter-status"),
        EMAIL_SEND_FAILED("SYS004", "Failed to send email", "system/email-send-failed"),

        // Validation errors (4000-4999)
        INVALID_INPUT("VAL001", "Invalid input provided", "validation/invalid-input"),
        MISSING_REQUIRED_FIELD("VAL002", "Required field is missing", "validation/missing-field"),
        INVALID_STATUS_TRANSITION("VAL003", "Invalid status transition", "validation/invalid-status-transition"),
        APPROVAL_NOT_REQUIRED("VAL004", "Approval is not required for this operation",
                        "validation/approval-not-required"),
        INVALID_BORROW_STATE("VAL005", "Invalid borrow state for this operation", "validation/invalid-borrow-state"),

        // System errors (5000-5999)
        INTERNAL_ERROR("SYS001", "An internal error occurred", "system/internal-error"),
        EXTERNAL_SERVICE_ERROR("SYS002", "External service error", "system/external-service-error"),
        EMAIL_SENDING_ERROR("SYS003", "Failed to send email", "system/email-sending-error");

        private final String code;
        private final String defaultMessage;
        private final String documentationPath;

        public String getDocumentationUrl() {
                return "https://docs.portal.neohoods.com/errors/" + documentationPath;
        }
}