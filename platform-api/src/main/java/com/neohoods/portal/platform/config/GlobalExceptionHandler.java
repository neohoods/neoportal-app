package com.neohoods.portal.platform.config;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.exceptions.CodedException;
import com.neohoods.portal.platform.exceptions.ResourceNotFoundException;
import com.neohoods.portal.platform.model.CodedError;

import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Autowired(required = false)
    private MatrixExceptionNotificationService exceptionNotificationService;

    @ExceptionHandler(CodedException.class)
    public Mono<ResponseEntity<CodedError>> handleCodedException(CodedException ex) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault("traceId", generateTraceId());
            logError(traceId, ex);
            notifyException(ex, traceId);

            CodedError error = new CodedError()
                    .code(ex.getCode())
                    .message(ex.getMessage())
                    .traceId(traceId)
                    .documentationUrl(ex.getDocumentationUrl())
                    .variables(ex.getVariables());

            return Mono.just(new ResponseEntity<>(error, HttpStatus.BAD_REQUEST));
        });
    }

    @ExceptionHandler(CodedErrorException.class)
    public Mono<ResponseEntity<CodedError>> handleCodedErrorException(CodedErrorException ex) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault("traceId", generateTraceId());
            logError(traceId, ex);
            notifyException(ex, traceId);

            // Build a nice error message based on the error type and variables
            String message = buildUserFriendlyMessage(ex);

            CodedError error = new CodedError()
                    .code(ex.getError().getCode())
                    .message(message)
                    .traceId(traceId)
                    .documentationUrl(ex.getError().getDocumentationUrl())
                    .variables(ex.getVariables());

            // Return 400 Bad Request for validation errors, keep other status codes as
            // appropriate
            HttpStatus status = isValidationError(ex.getError()) ? HttpStatus.BAD_REQUEST
                    : HttpStatus.INTERNAL_SERVER_ERROR;

            return Mono.just(new ResponseEntity<>(error, status));
        });
    }

    @ExceptionHandler(BadCredentialsException.class)
    public Mono<ResponseEntity<CodedError>> handleBadCredentials(BadCredentialsException ex) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault("traceId", generateTraceId());
            logError(traceId, ex);

            CodedError error = new CodedError()
                    .code("AUTH002")
                    .message("Invalid credentials provided")
                    .traceId(traceId);

            return Mono.just(new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED));
        });
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<CodedError>> handleAccessDeniedException(AccessDeniedException ex) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault("traceId", generateTraceId());
            logError(traceId, ex);

            CodedError error = new CodedError()
                    .code("AUTH005")
                    .message("You don't have sufficient permissions to perform this action")
                    .traceId(traceId);

            return Mono.just(new ResponseEntity<>(error, HttpStatus.FORBIDDEN));
        });
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<CodedError>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault("traceId", generateTraceId());
            logError(traceId, ex);

            CodedError error = new CodedError()
                    .code("RES001")
                    .message("The requested resource was not found")
                    .traceId(traceId);

            return Mono.just(new ResponseEntity<>(error, HttpStatus.NOT_FOUND));
        });
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<CodedError>> handleValidationException(WebExchangeBindException ex) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault("traceId", generateTraceId());
            logError(traceId, ex);

            Map<String, Object> variables = new HashMap<>();
            variables.put("fields", ex.getBindingResult().getFieldErrors().stream()
                    .map(error -> Map.of(
                            "field", error.getField(),
                            "message", error.getDefaultMessage()))
                    .toList());

            CodedError error = new CodedError()
                    .code("VAL001")
                    .message("Validation failed")
                    .traceId(traceId)
                    .variables(variables);

            return Mono.just(new ResponseEntity<>(error, HttpStatus.UNPROCESSABLE_ENTITY));
        });
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<CodedError>> handleException(Exception ex) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault("traceId", generateTraceId());
            logError(traceId, ex);
            notifyException(ex, traceId);

            CodedError error = new CodedError()
                    .code("SYS001")
                    .message("An unexpected error occurred")
                    .traceId(traceId);

            return Mono.just(new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR));
        });
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    private void logError(String traceId, Exception ex) {
        log.error("Error occurred - TraceId: {} - Message: {}", traceId, ex.getMessage(), ex);
    }

    /**
     * Notifies the IT room about the exception (if notification service is
     * available)
     */
    private void notifyException(Exception ex, String traceId) {
        try {
            if (exceptionNotificationService != null) {
                exceptionNotificationService.notifyException(ex, traceId);
            }
        } catch (Exception e) {
            // Don't let notification errors break the exception handling
            log.warn("Failed to send exception notification", e);
        }
    }

    private String buildUserFriendlyMessage(CodedErrorException ex) {
        com.neohoods.portal.platform.exceptions.CodedError error = ex.getError();
        Map<String, Object> variables = ex.getVariables();

        // Build user-friendly messages based on the error type
        switch (error) {
            case WEAK_PASSWORD:
                // Return just the reason without any additional text,
                // so the frontend can use its own translation template
                String reason = (String) variables.get("reason");
                return reason != null ? reason : error.getDefaultMessage();

            case INVALID_EMAIL_FORMAT:
                String email = (String) variables.get("email");
                return email != null ? String.format("The email address '%s' is not valid", email)
                        : error.getDefaultMessage();

            case USERNAME_TOO_SHORT:
                String username = (String) variables.get("username");
                return username != null
                        ? String.format("Username '%s' is too short. It must be at least 3 characters long", username)
                        : error.getDefaultMessage();

            case USERNAME_INVALID_CHARACTERS:
                String invalidUsername = (String) variables.get("username");
                return invalidUsername != null ? String.format(
                        "Username '%s' contains invalid characters. Only letters, numbers, underscores and hyphens are allowed",
                        invalidUsername) : error.getDefaultMessage();

            case MISSING_REQUIRED_FIELD:
                String field = (String) variables.get("field");
                return field != null ? String.format("The field '%s' is required", field) : error.getDefaultMessage();

            case USER_ALREADY_EXISTS:
                String existingUsername = (String) variables.get("username");
                String existingEmail = (String) variables.get("email");

                if (existingUsername != null) {
                    return String.format("Un utilisateur avec le nom d'utilisateur '%s' existe déjà", existingUsername);
                } else if (existingEmail != null) {
                    return String.format("Un utilisateur avec l'adresse email '%s' existe déjà", existingEmail);
                } else {
                    return "Un utilisateur avec ces informations existe déjà";
                }

            case EMAIL_ALREADY_EXISTS:
                String emailAlreadyExists = (String) variables.get("email");
                return emailAlreadyExists != null
                        ? String.format("L'adresse email '%s' est déjà utilisée par un autre utilisateur",
                                emailAlreadyExists)
                        : error.getDefaultMessage();

            case INVALID_CREDENTIALS:
                return "The username or password you entered is incorrect";

            case EMAIL_ALREADY_VERIFIED:
                return "This email address has already been verified";

            case INVALID_VERIFICATION_TOKEN:
                return "The verification link is invalid or has expired";

            default:
                return error.getDefaultMessage();
        }
    }

    private boolean isValidationError(com.neohoods.portal.platform.exceptions.CodedError error) {
        // All user input validation errors and client errors should return 400
        return error == com.neohoods.portal.platform.exceptions.CodedError.WEAK_PASSWORD ||
                error == com.neohoods.portal.platform.exceptions.CodedError.INVALID_EMAIL_FORMAT ||
                error == com.neohoods.portal.platform.exceptions.CodedError.USERNAME_TOO_SHORT ||
                error == com.neohoods.portal.platform.exceptions.CodedError.USERNAME_INVALID_CHARACTERS ||
                error == com.neohoods.portal.platform.exceptions.CodedError.MISSING_REQUIRED_FIELD ||
                error == com.neohoods.portal.platform.exceptions.CodedError.INVALID_INPUT ||
                error == com.neohoods.portal.platform.exceptions.CodedError.INVALID_STATUS_TRANSITION ||
                error == com.neohoods.portal.platform.exceptions.CodedError.APPROVAL_NOT_REQUIRED ||
                error == com.neohoods.portal.platform.exceptions.CodedError.INVALID_BORROW_STATE ||
                error == com.neohoods.portal.platform.exceptions.CodedError.USER_ALREADY_EXISTS ||
                error == com.neohoods.portal.platform.exceptions.CodedError.EMAIL_ALREADY_EXISTS ||
                error == com.neohoods.portal.platform.exceptions.CodedError.INVALID_CREDENTIALS ||
                error == com.neohoods.portal.platform.exceptions.CodedError.EMAIL_ALREADY_VERIFIED ||
                error == com.neohoods.portal.platform.exceptions.CodedError.INVALID_VERIFICATION_TOKEN;
    }
}