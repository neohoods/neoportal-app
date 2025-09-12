package com.neohoods.portal.platform.services;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.model.SignUpRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    private static final String USERNAME_REGEX = "^[a-zA-Z0-9_-]{3,30}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
    private static final Pattern USERNAME_PATTERN = Pattern.compile(USERNAME_REGEX);

    /**
     * Validates a signup request comprehensively
     */
    public void validateSignUpRequest(SignUpRequest request) {
        log.debug("Validating signup request for username: {}", request.getUsername());

        // Validate email format
        validateEmail(request.getEmail());

        // Validate username
        validateUsername(request.getUsername());

        // Validate password strength
        validatePassword(request.getPassword());

        // Validate required fields
        validateRequiredFields(request);

        log.debug("Signup request validation passed for username: {}", request.getUsername());
    }

    /**
     * Validates email format
     */
    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new CodedErrorException(CodedError.MISSING_REQUIRED_FIELD, "field", "email");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CodedErrorException(CodedError.INVALID_EMAIL_FORMAT, "email", email);
        }
    }

    /**
     * Validates username format and length
     */
    private void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new CodedErrorException(CodedError.MISSING_REQUIRED_FIELD, "field", "username");
        }

        if (username.length() < 3) {
            throw new CodedErrorException(CodedError.USERNAME_TOO_SHORT, "username", username);
        }

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new CodedErrorException(CodedError.USERNAME_INVALID_CHARACTERS, "username", username);
        }
    }

    /**
     * Validates password strength
     */
    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new CodedErrorException(CodedError.MISSING_REQUIRED_FIELD, "field", "password");
        }

        if (password.length() < 8) {
            throw new CodedErrorException(CodedError.WEAK_PASSWORD, "reason",
                    "Password must be at least 8 characters long");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new CodedErrorException(CodedError.WEAK_PASSWORD, "reason",
                    "Password must contain at least one uppercase letter");
        }

        if (!password.matches(".*[a-z].*")) {
            throw new CodedErrorException(CodedError.WEAK_PASSWORD, "reason",
                    "Password must contain at least one lowercase letter");
        }

        if (!password.matches(".*\\d.*")) {
            throw new CodedErrorException(CodedError.WEAK_PASSWORD, "reason",
                    "Password must contain at least one number");
        }
    }

    /**
     * Validates required fields are present
     */
    private void validateRequiredFields(SignUpRequest request) {
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            throw new CodedErrorException(CodedError.MISSING_REQUIRED_FIELD, "field", "firstName");
        }

        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            throw new CodedErrorException(CodedError.MISSING_REQUIRED_FIELD, "field", "lastName");
        }

        if (request.getType() == null) {
            throw new CodedErrorException(CodedError.MISSING_REQUIRED_FIELD, "field", "type");
        }
    }
}
