package com.neohoods.portal.platform.api.publicapi.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.AuthApiApiDelegate;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.entities.UserType;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.model.ConfirmResetPasswordRequest;
import com.neohoods.portal.platform.model.LoginRequest;
import com.neohoods.portal.platform.model.ResetPasswordRequest;
import com.neohoods.portal.platform.model.SignUpRequest;
import com.neohoods.portal.platform.model.User;
import com.neohoods.portal.platform.model.VerifyEmail200Response;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.Auth0Service;
import com.neohoods.portal.platform.services.EmailTemplateService;
import com.neohoods.portal.platform.services.JwtService;
import com.neohoods.portal.platform.services.MailService;
import com.neohoods.portal.platform.services.NotificationsService;
import com.neohoods.portal.platform.services.SettingsService;
import com.neohoods.portal.platform.services.UsersService;
import com.neohoods.portal.platform.services.ValidationService;
import com.nimbusds.jwt.JWTClaimsSet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthApi implements AuthApiApiDelegate {
        private final ReactiveAuthenticationManager authenticationManager;
        private final UsersRepository usersRepository;
        private final ServerSecurityContextRepository serverSecurityContextRepository;
        private final PasswordEncoder passwordEncoder;
        private final MailService mailService;
        private final JwtService jwtService;
        private final UsersService usersService;
        private final Auth0Service auth0Service;
        private final ValidationService validationService;
        private final SettingsService settingsService;
        private final NotificationsService notificationsService;
        private final EmailTemplateService emailTemplateService;

        @Value("${neohoods.portal.frontend-url}")
        private String frontendUrl;

        @Override
        public Mono<ResponseEntity<User>> login(Mono<LoginRequest> loginRequest, ServerWebExchange exchange) {
                return loginRequest
                                .doOnNext(request -> log.info("Login attempt for user: {}", request.getUsername()))
                                .flatMap(request -> authenticationManager
                                                .authenticate(UsernamePasswordAuthenticationToken
                                                                .unauthenticated(request.getUsername(),
                                                                                request.getPassword())))
                                .flatMap(authentication -> {
                                        log.info("User authenticated successfully: {}", authentication.getName());
                                        return usersRepository
                                                        .findByIdWithProperties(
                                                                        UUID.fromString(authentication.getName()))
                                                        .map(userEntity -> {
                                                                SecurityContext context = SecurityContextHolder
                                                                                .createEmptyContext();
                                                                Authentication auth = new UsernamePasswordAuthenticationToken(
                                                                                userEntity.getId().toString(),
                                                                                null,
                                                                                authentication.getAuthorities());
                                                                context.setAuthentication(auth);
                                                                return Tuples.of(context, userEntity);
                                                        })
                                                        .map(Mono::just)
                                                        .orElse(Mono.empty());
                                })
                                .flatMap(tuple -> serverSecurityContextRepository
                                                .save(exchange, tuple.getT1())
                                                .thenReturn(tuple.getT2()))
                                .map(userEntity -> {
                                        log.info("Login successful for user: {}", userEntity.getUsername());
                                        return ResponseEntity.ok(userEntity.toUser()
                                                        // TODO get the user roles
                                                        .roles(List.of("hub", "admin"))
                                                        .build());
                                })
                                .onErrorResume(e -> {
                                        if (e instanceof BadCredentialsException) {
                                                log.warn("Login failed: Bad credentials");
                                                return Mono.just(
                                                                ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                                        } else {
                                                log.error("Login failed with unexpected error", e);
                                                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                                .build());
                                        }
                                });
        }

        @Override
        public Mono<ResponseEntity<Void>> signUp(Mono<SignUpRequest> signUpRequest, ServerWebExchange exchange) {
                return signUpRequest.flatMap(request -> {
                        log.info("Processing signup request for username: {}", request.getUsername());

                        try {
                                // Step 1: Validate input
                                validationService.validateSignUpRequest(request);
                                log.debug("Input validation passed for username: {}", request.getUsername());

                                // Step 1.5: Prevent self-assignment of ADMIN role
                                if (request.getType() == com.neohoods.portal.platform.model.UserType.ADMIN) {
                                        log.warn("Signup failed: Users cannot self-assign ADMIN role: {}",
                                                        request.getUsername());
                                        return Mono.error(new CodedErrorException(CodedError.INSUFFICIENT_PERMISSIONS,
                                                        Map.of("username", request.getUsername())));
                                }

                                // Step 2: Check if user already exists locally
                                UserEntity existingUser = usersRepository.findByUsername(request.getUsername());
                                if (existingUser != null) {
                                        log.warn("Signup failed: Username already exists: {}", request.getUsername());
                                        return Mono.error(new CodedErrorException(CodedError.USER_ALREADY_EXISTS,
                                                        "username", request.getUsername()));
                                }

                                // Step 3: Check settings to determine if Auth0 should be used
                                return settingsService.getPublicSettings()
                                                .flatMap(settings -> {
                                                        if (settings.getSsoEnabled()) {
                                                                // Use Auth0 for user management when SSO is enabled
                                                                return auth0Service.userExists(request.getEmail())
                                                                                .flatMap(userExists -> {
                                                                                        if (userExists) {
                                                                                                log.warn("Signup failed: User already exists in Auth0: {}",
                                                                                                                request.getEmail());
                                                                                                return Mono.error(
                                                                                                                new CodedErrorException(
                                                                                                                                CodedError.USER_ALREADY_EXISTS,
                                                                                                                                "email",
                                                                                                                                request.getEmail()));
                                                                                        }

                                                                                        // Step 4: Register user in
                                                                                        // Auth0
                                                                                        Map<String, Object> userMetadata = new HashMap<>();
                                                                                        userMetadata.put("username",
                                                                                                        request.getUsername());
                                                                                        userMetadata.put("type", request
                                                                                                        .getType()
                                                                                                        .toString());
                                                                                        userMetadata.put("firstName",
                                                                                                        request.getFirstName());
                                                                                        userMetadata.put("lastName",
                                                                                                        request.getLastName());

                                                                                        try {
                                                                                                // Register user in
                                                                                                // Auth0
                                                                                                // Email verification
                                                                                                // redirect URL should
                                                                                                // be configured in
                                                                                                // Auth0 Dashboard
                                                                                                auth0Service.registerUser(
                                                                                                                request.getEmail(),
                                                                                                                request.getPassword(),
                                                                                                                request.getUsername(),
                                                                                                                userMetadata);
                                                                                                log.info("Successfully registered user in Auth0: {}",
                                                                                                                request.getEmail());
                                                                                        } catch (CodedErrorException e) {
                                                                                                log.error("Failed to register user in Auth0: {}",
                                                                                                                request.getEmail(),
                                                                                                                e);
                                                                                                return Mono.error(e);
                                                                                        }

                                                                                        // Step 5: Save user to local
                                                                                        // database
                                                                                        UserEntity newUser = createUserEntity(
                                                                                                        request);

                                                                                        try {
                                                                                                usersRepository.save(
                                                                                                                newUser);
                                                                                                log.info("Successfully saved user to local database: {}",
                                                                                                                newUser.getUsername());
                                                                                        } catch (Exception e) {
                                                                                                log.error("Failed to save user to local database: {}",
                                                                                                                newUser.getUsername(),
                                                                                                                e);

                                                                                                // Rollback: Delete user
                                                                                                // from Auth0
                                                                                                try {
                                                                                                        auth0Service.deleteUser(
                                                                                                                        request.getEmail());
                                                                                                        log.info("Successfully rolled back Auth0 user: {}",
                                                                                                                        request.getEmail());
                                                                                                } catch (Exception rollbackException) {
                                                                                                        log.error("Failed to rollback Auth0 user: {}",
                                                                                                                        request.getEmail(),
                                                                                                                        rollbackException);
                                                                                                }

                                                                                                return Mono.error(
                                                                                                                new CodedErrorException(
                                                                                                                                CodedError.DATABASE_SAVE_ERROR,
                                                                                                                                Map.of("username",
                                                                                                                                                request.getUsername()),
                                                                                                                                e));
                                                                                        }

                                                                                        // Auth0 handles email
                                                                                        // verification when SSO is
                                                                                        // enabled
                                                                                        log.info("User registration completed. Auth0 will handle email verification for: {}",
                                                                                                        request.getEmail());

                                                                                        // Notify admins about new user
                                                                                        // registration
                                                                                        return notificationsService
                                                                                                        .notifyAdminsNewUser(
                                                                                                                        newUser)
                                                                                                        .then(Mono.just(ResponseEntity
                                                                                                                        .status(HttpStatus.CREATED)
                                                                                                                        .build()));
                                                                                });
                                                        } else {
                                                                // Skip Auth0 when SSO is disabled - just save to local
                                                                // database
                                                                UserEntity newUser = createUserEntity(request);

                                                                try {
                                                                        usersRepository.save(newUser);
                                                                        log.info("Successfully saved user to local database: {}",
                                                                                        newUser.getUsername());
                                                                } catch (Exception e) {
                                                                        log.error("Failed to save user to local database: {}",
                                                                                        newUser.getUsername(),
                                                                                        e);
                                                                        return Mono.error(new CodedErrorException(
                                                                                        CodedError.DATABASE_SAVE_ERROR,
                                                                                        Map.of("username", request
                                                                                                        .getUsername()),
                                                                                        e));
                                                                }

                                                                // Send custom verification email when SSO is disabled
                                                                try {
                                                                        sendVerificationEmail(newUser);
                                                                        log.info("Successfully sent verification email to: {}",
                                                                                        request.getEmail());

                                                                        // Notify admins about new user registration
                                                                        return notificationsService
                                                                                        .notifyAdminsNewUser(newUser)
                                                                                        .then(Mono.just(ResponseEntity
                                                                                                        .status(HttpStatus.CREATED)
                                                                                                        .build()));
                                                                } catch (Exception e) {
                                                                        log.error("Failed to send verification email to: {}",
                                                                                        request.getEmail(), e);
                                                                        // Don't rollback the user creation for email
                                                                        // failures
                                                                        // The user can request a new verification email
                                                                        // later
                                                                        return Mono.error(new CodedErrorException(
                                                                                        CodedError.EMAIL_SEND_ERROR,
                                                                                        Map.of("email", request
                                                                                                        .getEmail()),
                                                                                        e));
                                                                }
                                                        }
                                                });

                        } catch (CodedErrorException e) {
                                // Re-throw coded exceptions as-is
                                return Mono.error(e);
                        } catch (Exception e) {
                                log.error("Unexpected error during signup for username: {}", request.getUsername(), e);
                                return Mono.error(new CodedErrorException(CodedError.INTERNAL_ERROR,
                                                Map.of("username", request.getUsername()), e));
                        }
                });
        }

        /**
         * Creates a new UserEntity from the signup request
         */
        private UserEntity createUserEntity(SignUpRequest request) {
                UserEntity newUser = new UserEntity();
                newUser.setId(UUID.randomUUID());
                newUser.setUsername(request.getUsername());
                String encodedPassword = passwordEncoder.encode(request.getPassword());
                newUser.setPassword(encodedPassword);
                newUser.setEmail(request.getEmail());
                newUser.setFirstName(request.getFirstName());
                newUser.setLastName(request.getLastName());
                newUser.setRoles(Set.of("hub-user"));
                newUser.setType(UserType.fromOpenApiUserType(request.getType()));
                return newUser;
        }

        /**
         * Sends verification email to the user using the active WELCOME template
         */
        private void sendVerificationEmail(UserEntity user) throws Exception {
                JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                                .subject(user.getUsername())
                                .expirationTime(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24))
                                .claim("type", "E_VERIFY")
                                .claim("email", user.getEmail());

                String token = jwtService.createToken(claimsBuilder);
                log.debug("Created verification token for user: {}", user.getUsername());

                // Try to get the active WELCOME template
                try {
                        var welcomeTemplate = emailTemplateService.getActiveTemplateByType("WELCOME").block();
                        if (welcomeTemplate != null) {
                                log.info("Using custom WELCOME template for user: {}", user.getUsername());

                                // Create template variables
                                var usernameVar = MailService.TemplateVariable.builder()
                                                .type(MailService.TemplateVariableType.RAW)
                                                .ref("username")
                                                .value(user.getUsername())
                                                .build();

                                var firstNameVar = MailService.TemplateVariable.builder()
                                                .type(MailService.TemplateVariableType.RAW)
                                                .ref("firstName")
                                                .value(user.getFirstName())
                                                .build();

                                var lastNameVar = MailService.TemplateVariable.builder()
                                                .type(MailService.TemplateVariableType.RAW)
                                                .ref("lastName")
                                                .value(user.getLastName())
                                                .build();

                                // Process template content with variables (no verif_url for welcome email)
                                List<MailService.TemplateVariable> processingVariables = Arrays.asList(
                                                usernameVar, firstNameVar, lastNameVar);

                                String processedSubject = processTemplateVariables(welcomeTemplate.getSubject(),
                                                processingVariables);
                                String processedContent = processTemplateVariables(welcomeTemplate.getContent(),
                                                processingVariables);

                                // Create final template variables for the email
                                List<MailService.TemplateVariable> templateVariables = new ArrayList<>(
                                                processingVariables);
                                templateVariables.add(MailService.TemplateVariable.builder()
                                                .type(MailService.TemplateVariableType.RAW)
                                                .ref("content")
                                                .value(processedContent)
                                                .build());

                                mailService.sendTemplatedEmail(
                                                user,
                                                processedSubject,
                                                "email/custom-template",
                                                templateVariables,
                                                user.getLocale());
                                return;
                        }
                } catch (Exception e) {
                        log.warn("Failed to get WELCOME template, falling back to default template: {}",
                                        e.getMessage());
                }

                // Fallback to default template if WELCOME template is not available
                log.info("Using default verification template for user: {}", user.getUsername());
                var usernameVar = MailService.TemplateVariable.builder()
                                .type(MailService.TemplateVariableType.RAW)
                                .ref("username")
                                .value(user.getUsername())
                                .build();

                var verifUrlVar = MailService.TemplateVariable.builder()
                                .type(MailService.TemplateVariableType.RAW)
                                .ref("verif_url")
                                .value(frontendUrl + "/email-confirmation?token=" + token)
                                .build();

                mailService.sendTemplatedEmail(
                                user,
                                "Welcome to portal NeoHoods",
                                "email/signup-email-verif",
                                new ArrayList<>(Arrays.asList(usernameVar, verifUrlVar)),
                                user.getLocale());
        }

        @Override
        public Mono<ResponseEntity<VerifyEmail200Response>> verifyEmail(String token, ServerWebExchange exchange) {
                log.info("Processing email verification request");
                try {
                        JWTClaimsSet claims = jwtService.verifyToken(token);
                        if (claims == null) {
                                log.warn("Email verification failed: Invalid token");
                                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                                .body(new VerifyEmail200Response()
                                                                .success(false)
                                                                .message("Invalid token")));
                        }

                        if (!claims.getClaim("type").equals("E_VERIFY")) {
                                log.warn("Email verification failed: Invalid token type");
                                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                                .body(new VerifyEmail200Response()
                                                                .success(false)
                                                                .message("Invalid token type")));
                        }

                        UserEntity user = usersRepository.findByUsername(claims.getSubject());
                        if (user == null) {
                                log.warn("Email verification failed: User not found for subject: {}",
                                                claims.getSubject());
                                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                                .body(new VerifyEmail200Response()
                                                                .success(false)
                                                                .message("User not found")));
                        }

                        if (user.isEmailVerified()) {
                                log.info("Email already verified for user: {}", user.getUsername());
                                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                                .body(new VerifyEmail200Response()
                                                                .success(false)
                                                                .message("Email already verified")));
                        }

                        user.setEmailVerified(true);
                        usersRepository.save(user);
                        log.info("Email verified successfully for user: {}", user.getUsername());

                        return Mono.just(ResponseEntity.status(HttpStatus.OK)
                                        .body(new VerifyEmail200Response().success(true)));
                } catch (Exception e) {
                        log.error("Email verification failed with exception", e);
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(new VerifyEmail200Response()
                                                        .success(false)
                                                        .message(e.getMessage())));
                }
        }

        @Override
        public Mono<ResponseEntity<Void>> resetPassword(Mono<ResetPasswordRequest> request,
                        ServerWebExchange exchange) {
                return request.flatMap(req -> {
                        UserEntity user = usersRepository.findByEmail(req.getEmail());
                        if (user != null) {
                                return settingsService.getPublicSettings()
                                                .map(settings -> {
                                                        if (settings.getSsoEnabled()) {
                                                                // Auth0 handles password reset when SSO is enabled
                                                                log.info("Password reset requested for user: {}. Auth0 will handle the reset process.",
                                                                                req.getEmail());
                                                                return ResponseEntity.ok().<Void>build();
                                                        } else {
                                                                // Send custom password reset email when SSO is disabled
                                                                try {
                                                                        String token = jwtService.createToken(
                                                                                        new JWTClaimsSet.Builder()
                                                                                                        .subject(user.getId()
                                                                                                                        .toString())
                                                                                                        .expirationTime(new Date(
                                                                                                                        System.currentTimeMillis()
                                                                                                                                        + 3600000))
                                                                                                        .claim("type", "PWD_RESET"));

                                                                        var usernameVar = MailService.TemplateVariable
                                                                                        .builder()
                                                                                        .type(MailService.TemplateVariableType.RAW)
                                                                                        .ref("username")
                                                                                        .value(user.getUsername())
                                                                                        .build();

                                                                        var resetUrlVar = MailService.TemplateVariable
                                                                                        .builder()
                                                                                        .type(MailService.TemplateVariableType.RAW)
                                                                                        .ref("reset_url")
                                                                                        .value(frontendUrl
                                                                                                        + "/reset-password?token="
                                                                                                        + token)
                                                                                        .build();

                                                                        mailService.sendTemplatedEmail(
                                                                                        user,
                                                                                        "Reset Your Password - portal NeoHoods",
                                                                                        "email/reset-password",
                                                                                        new ArrayList<>(Arrays.asList(
                                                                                                        usernameVar,
                                                                                                        resetUrlVar)),
                                                                                        user.getLocale());

                                                                        log.info("Password reset email sent to: {}",
                                                                                        req.getEmail());
                                                                } catch (Exception e) {
                                                                        log.error("Failed to process password reset",
                                                                                        e);
                                                                }
                                                                return ResponseEntity.ok().<Void>build();
                                                        }
                                                });
                        }
                        return Mono.just(ResponseEntity.ok().<Void>build()); // Always return OK to not reveal email
                                                                             // existence
                });
        }

        @Override
        public Mono<ResponseEntity<Void>> confirmResetPassword(Mono<ConfirmResetPasswordRequest> request,
                        ServerWebExchange exchange) {
                return request.map(req -> {
                        try {
                                JWTClaimsSet claims = jwtService.verifyToken(req.getToken());
                                if (!"PWD_RESET".equals(claims.getStringClaim("type"))) {
                                        throw new IllegalArgumentException("Invalid token type");
                                }

                                UUID userId = UUID.fromString(claims.getSubject());
                                usersService.setUserPassword(userId, req.getNewPassword());

                                return ResponseEntity.ok().<Void>build();
                        } catch (Exception e) {
                                throw new IllegalArgumentException("Invalid or expired token");
                        }
                });
        }

        /**
         * Process template variables in a string template
         */
        private String processTemplateVariables(String template, List<MailService.TemplateVariable> variables) {
                String result = template;

                // Create a map of variable references to values
                Map<String, String> variableMap = variables.stream()
                                .collect(Collectors.toMap(
                                                MailService.TemplateVariable::getRef,
                                                v -> v.getValue() != null ? v.getValue().toString() : ""));

                // Replace {{variableName}} patterns
                for (Map.Entry<String, String> entry : variableMap.entrySet()) {
                        String placeholder = "{{" + entry.getKey() + "}}";
                        result = result.replace(placeholder, entry.getValue());
                }

                return result;
        }

}