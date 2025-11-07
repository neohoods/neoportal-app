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
        USER_NOT_FOUND_SSO("AUTH018", "User not found for SSO login. Please sign up first", "auth/user-not-found-sso"),
        AUTH0_LINKING_FAILED("AUTH019", "Failed to link Auth0 accounts", "auth/auth0-linking-failed"),

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
        RESERVATION_NOT_FOUND("RES018", "Reservation not found", "resources/reservation-not-found"),
        SPACE_NOT_FOUND("RES019", "Space not found", "resources/space-not-found"),
        SPACE_NOT_AVAILABLE("RES020", "Space is not available for the selected dates", "resources/space-not-available"),
        RESERVATION_ALREADY_CANCELLED("RES021", "Reservation is already cancelled",
                        "resources/reservation-already-cancelled"),
        RESERVATION_ALREADY_COMPLETED("RES022", "Cannot modify a completed reservation",
                        "resources/reservation-already-completed"),
        RESERVATION_NOT_PENDING_PAYMENT("RES023", "Reservation is not in pending payment status",
                        "resources/reservation-not-pending-payment"),
        RESERVATION_NOT_CONFIRMED("RES024", "Reservation is not in confirmed status",
                        "resources/reservation-not-confirmed"),
        RESERVATION_NOT_ACTIVE("RES025", "Reservation is not in active status", "resources/reservation-not-active"),
        RESERVATION_CANNOT_RETRY_PAYMENT("RES026", "Reservation is not in a state that allows payment retry",
                        "resources/reservation-cannot-retry-payment"),
        RESERVATION_START_DATE_NOT_TODAY("RES027", "Reservation start date is not today",
                        "resources/reservation-start-date-not-today"),
        RESERVATION_END_DATE_NOT_REACHED("RES028", "Reservation end date has not been reached",
                        "resources/reservation-end-date-not-reached"),
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
        EMAIL_SENDING_ERROR("SYS003", "Failed to send email", "system/email-sending-error"),

        // Digital Lock errors (6000-6999)
        DIGITAL_LOCK_NOT_FOUND("DLK001", "Digital lock not found", "digital-locks/lock-not-found"),
        DIGITAL_LOCK_OFFLINE("DLK002", "Digital lock is offline", "digital-locks/lock-offline"),
        DIGITAL_LOCK_ERROR("DLK003", "Digital lock error", "digital-locks/lock-error"),
        ACCESS_CODE_CREATION_FAILED("DLK004", "Failed to create access code on digital lock",
                        "digital-locks/access-code-creation-failed"),
        ACCESS_CODE_UPDATE_FAILED("DLK005", "Failed to update access code on digital lock",
                        "digital-locks/access-code-update-failed"),
        ACCESS_CODE_DELETE_FAILED("DLK006", "Failed to delete access code from digital lock",
                        "digital-locks/access-code-delete-failed"),
        TTLOCK_API_ERROR("DLK007", "TTLock API error", "digital-locks/ttlock-api-error"),
        NUKI_API_ERROR("DLK008", "Nuki API error", "digital-locks/nuki-api-error"),
        RESERVATION_NOT_COMPLETED_OR_ACTIVE("RES026", "Cannot submit feedback for reservation in this status",
                        "resources/reservation-not-completed-or-active"),
        FEEDBACK_ALREADY_SUBMITTED("RES024", "Feedback already submitted for this reservation",
                        "resources/feedback-already-submitted"),
        FEEDBACK_SUBMISSION_FAILED("RES025", "Failed to submit feedback", "resources/feedback-submission-failed"),
        PAYMENT_INTENT_CREATION_FAILED("STR001", "Failed to create payment intent",
                        "resources/payment-intent-creation-failed"),
        CHECKOUT_SESSION_CREATION_FAILED("STR002", "Failed to create checkout session",
                        "resources/checkout-session-creation-failed"),
        WEBHOOK_PROCESSING_FAILED("STR003", "Webhook processing failed", "resources/webhook-processing-failed"),
        PAYMENT_INTENT_REQUIRED("STR004", "PaymentIntent must be created before CheckoutSession",
                        "resources/payment-intent-required"),
        IMAGE_NOT_BELONGS_TO_SPACE("SPA001", "Image does not belong to this space",
                        "resources/image-not-belongs-to-space"),
        ACCESS_CODE_NOT_VALID("ACC001", "Access code is not valid", "resources/access-code-not-valid"),

        // Space reservation validation errors (7000-7999)
        SPACE_INACTIVE("SPA002", "Space is not active and cannot be reserved", "spaces/space-inactive"),
        SPACE_DURATION_TOO_SHORT("SPA003", "Reservation duration is shorter than the minimum allowed",
                        "spaces/duration-too-short"),
        SPACE_DURATION_TOO_LONG("SPA004", "Reservation duration is longer than the maximum allowed",
                        "spaces/duration-too-long"),
        SPACE_ANNUAL_QUOTA_EXCEEDED("SPA005", "Space has reached its maximum annual reservation limit",
                        "spaces/annual-quota-exceeded"),
        UNIT_NOT_FOUND("UNIT001", "Unit not found", "units/unit-not-found"),
        UNIT_MEMBER_NOT_FOUND("UNIT002", "Unit member not found", "units/member-not-found"),
        UNIT_INVITATION_NOT_FOUND("UNIT003", "Unit invitation not found", "units/invitation-not-found"),
        USER_NOT_MEMBER_OF_UNIT("UNIT004", "User is not a member of this unit", "units/user-not-member"),
        USER_NOT_ADMIN_OF_UNIT("UNIT005", "User is not an admin of this unit", "units/user-not-admin"),
        CANNOT_DEMOTE_LAST_ADMIN("UNIT006", "Cannot demote the last admin of a unit", "units/cannot-demote-last-admin"),
        USER_ALREADY_MEMBER("UNIT007", "User is already a member of this unit", "units/user-already-member"),
        USER_HAS_NO_UNIT("UNIT008", "User does not belong to any unit", "units/user-has-no-unit"),
        USER_NOT_TENANT_OR_OWNER("UNIT009", "User is not a tenant or owner of any unit", "units/user-not-tenant-or-owner");

        private final String code;
        private final String defaultMessage;
        private final String documentationPath;

        public String getDocumentationUrl() {
                return "https://docs.portal.neohoods.com/errors/" + documentationPath;
        }
}