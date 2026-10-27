package com.genisource.erp.platform.security;

import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and verifies HMAC-SHA256 tokens.
 *
 * <p>Claims are shaped so that a request can be authorized without touching the
 * database: tenant, roles and the flattened permission list travel in the token. This
 * keeps the token small (a factory rarely exceeds a few dozen permissions) and makes
 * horizontally scaled instances stateless. Revocation is therefore bounded by
 * {@code accessTtl} — rotating the secret logs everyone out.
 */
@Service
public class JwtService {

    public static final String CLAIM_TENANT = "tid";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_DISPLAY_NAME = "name";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISSIONS = "perms";
    public static final String CLAIM_FORCE_PASSWORD_CHANGE = "pwd";
    public static final String CLAIM_TYPE = "typ";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecurityProperties.Jwt properties;
    private final JWSSigner signer;
    private final JWSVerifier verifier;

    public JwtService(SecurityProperties securityProperties) {
        this.properties = securityProperties.jwt();
        SecretKeySpec key = new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        try {
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to initialise JWT signer", e);
        }
    }

    public IssuedToken issueAccessToken(AuthenticatedUser user) {
        return issue(user, TYPE_ACCESS, properties.accessTtl());
    }

    public IssuedToken issueRefreshToken(AuthenticatedUser user) {
        return issue(user, TYPE_REFRESH, properties.refreshTtl());
    }

    /**
     * Verifies signature, expiry and token type, then returns the claims.
     *
     * @throws ApiException when the token is malformed, forged or of the wrong type
     */
    public JWTClaimsSet parse(String token, String expectedType) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                throw ApiException.unauthorized(ErrorCode.TOKEN_INVALID, "Token signature is invalid");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!expectedType.equals(claims.getStringClaim(CLAIM_TYPE))) {
                throw ApiException.unauthorized(ErrorCode.TOKEN_INVALID, "Unexpected token type");
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
                throw ApiException.unauthorized(ErrorCode.TOKEN_EXPIRED, "Token has expired");
            }
            return claims;
        } catch (ParseException | JOSEException e) {
            throw ApiException.unauthorized(ErrorCode.TOKEN_INVALID, "Token cannot be parsed");
        }
    }

    public AuthenticatedUser toPrincipal(JWTClaimsSet claims) {
        try {
            List<String> roles = claims.getStringListClaim(CLAIM_ROLES);
            List<String> permissions = claims.getStringListClaim(CLAIM_PERMISSIONS);
            return new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.getLongClaim(CLAIM_TENANT),
                    claims.getStringClaim(CLAIM_USERNAME),
                    claims.getStringClaim(CLAIM_DISPLAY_NAME),
                    roles == null ? List.of() : List.copyOf(roles),
                    permissions == null ? List.of() : List.copyOf(permissions),
                    Boolean.TRUE.equals(claims.getBooleanClaim(CLAIM_FORCE_PASSWORD_CHANGE)));
        } catch (ParseException e) {
            throw ApiException.unauthorized(ErrorCode.TOKEN_INVALID, "Token claims are malformed");
        }
    }

    public Duration accessTtl() {
        return properties.accessTtl();
    }

    public Duration refreshTtl() {
        return properties.refreshTtl();
    }

    private IssuedToken issue(AuthenticatedUser user, String type, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(user.id()))
                .claim(CLAIM_TENANT, user.tenantId())
                .claim(CLAIM_USERNAME, user.username())
                .claim(CLAIM_DISPLAY_NAME, user.displayName())
                .claim(CLAIM_ROLES, user.roles())
                .claim(CLAIM_PERMISSIONS, user.permissions())
                .claim(CLAIM_FORCE_PASSWORD_CHANGE, user.forcePasswordChange())
                .claim(CLAIM_TYPE, type)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .build();
        try {
            SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signed.sign(signer);
            return new IssuedToken(signed.serialize(), expiresAt, ttl.toSeconds());
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to sign token", e);
        }
    }

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
