package com.neohoods.portal.platform.spaces.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CalendarTokenService {

    private final String jwtSecret;
    private final int jwtExpirationHours;
    private final JWSSigner signer;
    private final JWSVerifier verifier;

    public CalendarTokenService(
            @Value("${neohoods.portal.cleaning.calendar.jwt-secret}") String jwtSecret,
            @Value("${neohoods.portal.cleaning.calendar.jwt-expiration-hours:8760}") int jwtExpirationHours) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationHours = jwtExpirationHours;

        try {
            // Derive a 256-bit (32-byte) key from the secret using SHA-256
            // This ensures we always have a key of the correct length, regardless of input secret length
            byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secretBytes);
            
            this.signer = new MACSigner(keyBytes);
            this.verifier = new MACVerifier(keyBytes);
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to initialize JWT signer/verifier", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generate a JWT token for calendar access (backward compatibility)
     * 
     * @param spaceId The space ID
     * @return JWT token string
     */
    public String generateToken(UUID spaceId) {
        return generateToken(spaceId, "cleaning", null);
    }

    /**
     * Generate a JWT token for space calendar access
     * 
     * @param spaceId The space ID
     * @param type The calendar type ("cleaning" or "reservation")
     * @return JWT token string
     */
    public String generateToken(UUID spaceId, String type) {
        return generateToken(spaceId, type, null);
    }

    /**
     * Generate a JWT token for space calendar access with user filter
     * 
     * @param spaceId The space ID
     * @param type The calendar type ("cleaning" or "reservation")
     * @param userId Optional user ID for filtering reservations
     * @return JWT token string
     */
    public String generateToken(UUID spaceId, String type, UUID userId) {
        try {
            Date now = new Date();
            Date expiration = new Date(now.getTime() + (jwtExpirationHours * 3600L * 1000L));

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(spaceId.toString())
                    .claim("spaceId", spaceId.toString())
                    .claim("type", type)
                    .issuer("neohoods-portal")
                    .audience("calendar")
                    .issueTime(now)
                    .expirationTime(expiration);

            if (userId != null) {
                claimsBuilder.claim("userId", userId.toString());
            }

            JWTClaimsSet claimsSet = claimsBuilder.build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsSet);

            signedJWT.sign(signer);
            log.debug("Generated calendar token for space {} with type {} and userId {}", spaceId, type, userId);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("Failed to generate calendar token for space {}", spaceId, e);
            throw new RuntimeException("Failed to generate calendar token", e);
        }
    }

    /**
     * Generate a JWT token for user calendar access
     * 
     * @param userId The user ID
     * @return JWT token string
     */
    public String generateTokenForUser(UUID userId) {
        try {
            Date now = new Date();
            Date expiration = new Date(now.getTime() + (jwtExpirationHours * 3600L * 1000L));

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("userId", userId.toString())
                    .claim("type", "user")
                    .issuer("neohoods-portal")
                    .audience("calendar")
                    .issueTime(now)
                    .expirationTime(expiration)
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsSet);

            signedJWT.sign(signer);
            log.debug("Generated calendar token for user {}", userId);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("Failed to generate calendar token for user {}", userId, e);
            throw new RuntimeException("Failed to generate calendar token", e);
        }
    }

    /**
     * Token verification result containing extracted claims
     */
    public static class TokenVerificationResult {
        private final UUID spaceId;
        private final UUID userId;
        private final String type;

        public TokenVerificationResult(UUID spaceId, UUID userId, String type) {
            this.spaceId = spaceId;
            this.userId = userId;
            this.type = type;
        }

        public UUID getSpaceId() {
            return spaceId;
        }

        public UUID getUserId() {
            return userId;
        }

        public String getType() {
            return type;
        }
    }

    /**
     * Verify token and extract claims (backward compatibility)
     * 
     * @param token JWT token string
     * @return Space ID
     * @throws JOSEException If token is invalid or expired
     */
    public UUID verifyToken(String token) throws JOSEException {
        TokenVerificationResult result = verifyTokenWithClaims(token);
        if (result.getSpaceId() == null) {
            throw new JOSEException("Token does not contain spaceId");
        }
        return result.getSpaceId();
    }

    /**
     * Verify token and extract all claims
     * 
     * @param token JWT token string
     * @return TokenVerificationResult with spaceId, userId, and type
     * @throws JOSEException If token is invalid or expired
     */
    public TokenVerificationResult verifyTokenWithClaims(String token) throws JOSEException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                log.warn("Calendar token signature verification failed");
                throw new JOSEException("Signature verification failed");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            if (claims.getExpirationTime().before(new Date())) {
                log.warn("Calendar token expired. Expiration: {}", claims.getExpirationTime());
                throw new JOSEException("Token expired");
            }

            // Extract spaceId
            String spaceIdStr = claims.getSubject();
            if (spaceIdStr == null) {
                spaceIdStr = (String) claims.getClaim("spaceId");
            }
            UUID spaceId = spaceIdStr != null ? UUID.fromString(spaceIdStr) : null;

            // Extract userId
            String userIdStr = (String) claims.getClaim("userId");
            UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : null;

            // Extract type (default to "cleaning" for backward compatibility)
            String type = (String) claims.getClaim("type");
            if (type == null) {
                type = "cleaning";
            }

            log.debug("Verified calendar token - spaceId: {}, userId: {}, type: {}", spaceId, userId, type);
            return new TokenVerificationResult(spaceId, userId, type);
        } catch (ParseException e) {
            log.error("Failed to parse calendar token", e);
            throw new JOSEException("Failed to parse token", e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID in token claims", e);
            throw new JOSEException("Invalid token claims", e);
        }
    }

    /**
     * Verify token for user calendar and check userId matches
     * 
     * @param token JWT token string
     * @param expectedUserId Expected user ID from path parameter
     * @return User ID if valid
     * @throws JOSEException If token is invalid, expired, or userId mismatch
     */
    public UUID verifyTokenForUser(String token, UUID expectedUserId) throws JOSEException {
        TokenVerificationResult result = verifyTokenWithClaims(token);
        
        if (!"user".equals(result.getType())) {
            throw new JOSEException("Token type is not 'user'");
        }

        UUID tokenUserId = result.getUserId();
        if (tokenUserId == null) {
            throw new JOSEException("Token does not contain userId");
        }

        if (!tokenUserId.equals(expectedUserId)) {
            log.warn("Token userId {} does not match expected userId {}", tokenUserId, expectedUserId);
            throw new JOSEException("UserId mismatch");
        }

        log.debug("Verified user calendar token for user {}", expectedUserId);
        return tokenUserId;
    }
}



