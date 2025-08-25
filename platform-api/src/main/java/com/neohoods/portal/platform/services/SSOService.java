package com.neohoods.portal.platform.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.neohoods.portal.platform.entities.SettingsEntity;
import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.SettingsRepository;
import com.neohoods.portal.platform.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
public class SSOService {

    private final SettingsRepository settingsRepository;
    @Value("${neohoods.portal.frontend-url}")
    private String frontendUrl;
    private final ServerSecurityContextRepository serverSecurityContextRepository;
    private final UsersRepository usersRepository;

    public URI generateSSOLoginUrl() {
        State state = new State();
        Nonce nonce = new Nonce();
        SettingsEntity setting = settingsRepository.findTopByOrderByIdAsc().get();
        AuthenticationRequest request = null;
        try {
            request = new AuthenticationRequest.Builder(
                    new ResponseType("code"),
                    new Scope(setting.getSsoScope()),
                    new ClientID(setting.getSsoClientId()),
                    new URI(frontendUrl + "/sso/callback"))
                    .endpointURI(new URI(setting.getSsoAuthorizationEndpoint()))
                    .state(state)
                    .nonce(nonce)
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return request.toURI();
    }

    public boolean tokenExchange(ServerWebExchange exchange, String state, String authorizationCode) throws URISyntaxException, IOException {
        //TODO state and PKCE
        SettingsEntity setting = settingsRepository.findTopByOrderByIdAsc().get();
        AuthorizationCode code = new AuthorizationCode(authorizationCode);
        URI callback = new URI(frontendUrl + "/sso/callback");
        AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, callback);

        ClientID clientID = new ClientID(setting.getSsoClientId());
        Secret clientSecret = new Secret(setting.getSsoClientSecret());
        ClientAuthentication clientAuth = new ClientSecretBasic(clientID, clientSecret);

        URI tokenEndpoint = new URI(setting.getSsoTokenEndpoint());

        TokenRequest request = new TokenRequest(tokenEndpoint, clientAuth, codeGrant);

        TokenResponse tokenResponse = null;
        try {
            tokenResponse = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        if (! tokenResponse.indicatesSuccess()) {
            TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
            return false;
        }

        OIDCTokenResponse successResponse = (OIDCTokenResponse)tokenResponse.toSuccessResponse();
        JWT idToken = successResponse.getOIDCTokens().getIDToken();

        try {
            UserEntity user = usersRepository.findByUsername(idToken.getJWTClaimsSet().getSubject());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getId().toString(), null));

            serverSecurityContextRepository.save(exchange, context);

        } catch (java.text.ParseException e) {
            throw new RuntimeException(e);
        }

        return true;
    }
}
