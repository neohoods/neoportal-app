package com.neohoods.portal.platform.services;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.entities.NotificationSettingsEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.exceptions.CodedError;
import com.neohoods.portal.platform.exceptions.CodedErrorException;
import com.neohoods.portal.platform.repositories.NotificationSettingsRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class SSOService {

        @Value("${neohoods.portal.frontend-url}")
        private String frontendUrl;

        @Value("${neohoods.portal.sso.enabled:false}")
        private boolean ssoEnabled;

        @Value("${neohoods.portal.sso.client-id:}")
        private String ssoClientId;

        @Value("${neohoods.portal.sso.client-secret:}")
        private String ssoClientSecret;

        @Value("${neohoods.portal.sso.token-endpoint:}")
        private String ssoTokenEndpoint;

        @Value("${neohoods.portal.sso.authorization-endpoint:}")
        private String ssoAuthorizationEndpoint;

        @Value("${neohoods.portal.sso.scope:openid profile email}")
        private String ssoScope;

        @Value("${neohoods.portal.email.template.app-name}")
        private String appName;

        private final ServerSecurityContextRepository serverSecurityContextRepository;
        private final UsersRepository usersRepository;
        private final NotificationSettingsRepository notificationSettingsRepository;
        private final Auth0Service auth0Service;
        private final PasswordEncoder passwordEncoder;
        private final MailService mailService;
        private final NotificationsService notificationsService;
        private final EmailTemplateService emailTemplateService;

        public SSOService(ServerSecurityContextRepository serverSecurityContextRepository,
                        UsersRepository usersRepository,
                        NotificationSettingsRepository notificationSettingsRepository,
                        Auth0Service auth0Service,
                        PasswordEncoder passwordEncoder,
                        MailService mailService,
                        NotificationsService notificationsService,
                        EmailTemplateService emailTemplateService) {
                this.serverSecurityContextRepository = serverSecurityContextRepository;
                this.usersRepository = usersRepository;
                this.notificationSettingsRepository = notificationSettingsRepository;
                this.auth0Service = auth0Service;
                this.passwordEncoder = passwordEncoder;
                this.mailService = mailService;
                this.notificationsService = notificationsService;
                this.emailTemplateService = emailTemplateService;
        }

        public URI generateSSOLoginUrl() {
                State state = new State();
                Nonce nonce = new Nonce();
                AuthenticationRequest request = null;
                try {
                        // Split the SSO scope string by space and create Scope object
                        String[] scopeArray = ssoScope.split("\\s+");
                        Scope scope = new Scope(scopeArray);

                        request = new AuthenticationRequest.Builder(
                                        new ResponseType("code"),
                                        scope,
                                        new ClientID(ssoClientId),
                                        new URI(frontendUrl + "/token-exchange"))
                                        .endpointURI(new URI(ssoAuthorizationEndpoint))
                                        .state(state)
                                        .nonce(nonce)
                                        .build();

                        // Add ui_locales parameter to the URI
                        URI originalUri = request.toURI();
                        String uriString = originalUri.toString();
                        String separator = uriString.contains("?") ? "&" : "?";
                        URI uriWithLocale = new URI(uriString + separator + "ui_locales=fr");
                        return uriWithLocale;
                } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                }
        }

        public Mono<Boolean> tokenExchange(ServerWebExchange exchange, String state, String authorizationCode) {
                return Mono.fromCallable(() -> {
                        // TODO state and PKCE
                        AuthorizationCode code = new AuthorizationCode(authorizationCode);

                        URI callback;
                        URI tokenEndpoint;
                        try {
                                callback = new URI(frontendUrl + "/sso/callback");
                                tokenEndpoint = new URI(ssoTokenEndpoint);
                        } catch (URISyntaxException e) {
                                log.error("Invalid URI configuration", e);
                                throw new CodedErrorException(CodedError.INTERNAL_ERROR,
                                                Map.of("error", "Invalid URI configuration", "details",
                                                                e.getMessage()));
                        }

                        AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, callback);

                        ClientID clientID = new ClientID(ssoClientId);
                        Secret clientSecret = new Secret(ssoClientSecret);
                        ClientAuthentication clientAuth = new ClientSecretBasic(clientID, clientSecret);

                        TokenRequest request = new TokenRequest(tokenEndpoint, clientAuth, codeGrant, null);

                        TokenResponse tokenResponse = null;
                        try {
                                tokenResponse = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());
                        } catch (ParseException e) {
                                log.error("Failed to parse token response", e);
                                throw new CodedErrorException(CodedError.EXTERNAL_SERVICE_ERROR,
                                                Map.of("service", "SSO Token Exchange", "error", e.getMessage()));
                        }

                        if (!tokenResponse.indicatesSuccess()) {
                                return false;
                        }

                        OIDCTokenResponse successResponse = (OIDCTokenResponse) tokenResponse.toSuccessResponse();
                        JWT idToken = successResponse.getOIDCTokens().getIDToken();

                        try {
                                String email = idToken.getJWTClaimsSet().getStringClaim("email");
                                log.info("Looking up user by email: {}", email);

                                UserEntity user = usersRepository.findByEmail(email);

                                if (user == null) {
                                        // User not found, create a new user automatically
                                        log.info("User not found for email: {}, creating new user", email);

                                        // Extract user information from JWT token
                                        String firstName = idToken.getJWTClaimsSet().getStringClaim("given_name");
                                        String lastName = idToken.getJWTClaimsSet().getStringClaim("family_name");
                                        String avatarUrl = idToken.getJWTClaimsSet().getStringClaim("picture");
                                        Boolean emailVerified = (Boolean) idToken.getJWTClaimsSet()
                                                        .getClaim("email_verified");

                                        // Try to extract phone number from various possible claim names
                                        String phoneNumber = null;
                                        String[] phoneClaimNames = { "phone_number", "phone", "phoneNumber",
                                                        "phone_number_verified" };
                                        for (String claimName : phoneClaimNames) {
                                                String phone = idToken.getJWTClaimsSet().getStringClaim(claimName);
                                                if (phone != null && !phone.trim().isEmpty()) {
                                                        phoneNumber = phone.trim();
                                                        log.debug("Found phone number in claim '{}': {}", claimName,
                                                                        phoneNumber);
                                                        break;
                                                }
                                        }

                                        // Generate username from email (part before @)
                                        String username = email != null && email.contains("@")
                                                        ? email.substring(0, email.indexOf("@"))
                                                        : email != null ? email
                                                                        : "user_" + UUID.randomUUID().toString()
                                                                                        .substring(0, 8);

                                        // Generate a random password for SSO users (they won't use it, but DB requires
                                        // it)
                                        // Use a random UUID as the password source to ensure uniqueness
                                        String randomPassword = UUID.randomUUID().toString()
                                                        + UUID.randomUUID().toString();
                                        String encodedPassword = passwordEncoder.encode(randomPassword);

                                        // Create new user entity (without notificationSettings - will be created after
                                        // save)
                                        user = UserEntity.builder()
                                                        .id(UUID.randomUUID())
                                                        .email(email)
                                                        .username(username)
                                                        .password(encodedPassword) // Required field - SSO users won't
                                                                                   // use this
                                                        .firstName(firstName)
                                                        .lastName(lastName)
                                                        .avatarUrl(avatarUrl)
                                                        .phoneNumber(phoneNumber) // May be null if not in claims
                                                        .isEmailVerified(Boolean.TRUE.equals(emailVerified))
                                                        .type(null) // Do not set user.type - leave it null
                                                        .disabled(false)
                                                        .preferredLanguage("fr")
                                                        .profileSharingConsent(true)
                                                        .roles(Set.of("hub-user"))
                                                        .build();

                                        // Save the new user first
                                        user = usersRepository.save(user);
                                        log.info(
                                                        "Created new user with email: {}, username: {}, firstName: {}, lastName: {}, avatarUrl: {}, phoneNumber: {}",
                                                        email, username, firstName, lastName, avatarUrl,
                                                        phoneNumber != null ? phoneNumber : "not provided");

                                        // Create notification settings after user is persisted
                                        NotificationSettingsEntity notificationSettings = NotificationSettingsEntity
                                                        .builder()
                                                        .id(UUID.randomUUID())
                                                        .user(user)
                                                        .enableNotifications(true)
                                                        .newsletterEnabled(true)
                                                        .build();
                                        notificationSettingsRepository.save(notificationSettings);
                                        log.info("Created notification settings for user: {}", email);

                                        // Send welcome email and notify admins
                                        try {
                                                sendWelcomeEmail(user);
                                                log.info("Successfully sent welcome email to: {}", email);
                                        } catch (Exception e) {
                                                log.error("Failed to send welcome email to: {}", email, e);
                                                // Don't fail user creation for email failures
                                        }

                                        // Notify admins about new user registration
                                        try {
                                                notificationsService.notifyAdminsNewUser(user).subscribe(
                                                                null,
                                                                error -> log.error(
                                                                                "Failed to notify admins about new user: {}",
                                                                                email, error));
                                                log.info("Successfully notified admins about new user: {}", email);
                                        } catch (Exception e) {
                                                log.error("Failed to notify admins about new user: {}", email, e);
                                                // Don't fail user creation for notification failures
                                        }
                                }

                                log.info("Found user: {} with roles: {}", user.getUsername(), user.getRoles());

                                // Convert user roles to authorities
                                Collection<GrantedAuthority> authorities = user.getRoles() != null
                                                ? user.getRoles().stream()
                                                                .map(role -> new SimpleGrantedAuthority(
                                                                                "ROLE_" + role.toUpperCase()))
                                                                .collect(Collectors.toList())
                                                : java.util.Collections.emptyList();

                                log.info("Created authorities: {}", authorities);

                                SecurityContext context = SecurityContextHolder.createEmptyContext();
                                context.setAuthentication(new UsernamePasswordAuthenticationToken(
                                                user.getId().toString(),
                                                null,
                                                authorities));

                                log.info("Created security context for user: {} with authorities: {}", user.getId(),
                                                authorities);

                                // Return the context for saving
                                return new SecurityContext[] { context };

                        } catch (java.text.ParseException e) {
                                log.error("Failed to parse JWT claims", e);
                                throw new CodedErrorException(CodedError.EXTERNAL_SERVICE_ERROR,
                                                Map.of("service", "JWT Parsing", "error", e.getMessage()));
                        }
                }).flatMap(contextArray -> {
                        if (contextArray == null) {
                                log.warn("Context array is null, returning false");
                                return Mono.just(false);
                        }

                        SecurityContext[] contexts = (SecurityContext[]) contextArray;
                        SecurityContext context = contexts[0];

                        log.info("Saving security context to session");
                        return serverSecurityContextRepository.save(exchange, context)
                                        .doOnSuccess(v -> log.info("Security context saved successfully"))
                                        .doOnError(e -> log.error("Failed to save security context", e))
                                        .then(Mono.just(true));
                });
        }

        /**
         * Sends welcome email to the user using the active WELCOME template (without
         * verification token)
         */
        private void sendWelcomeEmail(UserEntity user) throws Exception {
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

                                // Add appName variable for template processing
                                var appNameVar = MailService.TemplateVariable.builder()
                                                .type(MailService.TemplateVariableType.RAW)
                                                .ref("appName")
                                                .value(appName)
                                                .build();

                                // Process template content with variables (no verif_url for welcome email)
                                List<MailService.TemplateVariable> processingVariables = Arrays.asList(
                                                usernameVar, firstNameVar, lastNameVar, appNameVar);

                                String processedSubject = processTemplateVariables(welcomeTemplate.getSubject(),
                                                processingVariables);
                                String processedContent = processTemplateVariables(welcomeTemplate.getContent(),
                                                processingVariables);

                                // Create final template variables for the email (exclude appName as it's added
                                // by MailService)
                                List<MailService.TemplateVariable> templateVariables = new ArrayList<>();
                                templateVariables.add(usernameVar);
                                templateVariables.add(firstNameVar);
                                templateVariables.add(lastNameVar);
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

                // Fallback to default welcome template if WELCOME template is not available
                log.info("Using default welcome template for user: {}", user.getUsername());
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

                mailService.sendTemplatedEmail(
                                user,
                                "Welcome to portal NeoHoods",
                                "email/welcome",
                                new ArrayList<>(Arrays.asList(usernameVar, firstNameVar, lastNameVar)),
                                user.getLocale());
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
