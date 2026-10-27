package com.genisource.erp.platform.security;

import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.ErrorCode;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token handling is security-critical: a bug here is an authentication bypass, so the
 * contract (claims round-trip, type confusion, tampering, expiry) is pinned by tests.
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-at-least-32-bytes-long!!";

    private JwtService service(Duration accessTtl) {
        return new JwtService(new SecurityProperties(
                "admin", "admin123", true,
                new SecurityProperties.Jwt(SECRET, accessTtl, Duration.ofDays(7))));
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                42L, 1L, "planner", "计划员",
                List.of("ADMIN"),
                List.of("masterdata:item:read", "mrp:run:write"),
                false);
    }

    @Test
    void accessTokenRoundTripsAllClaims() {
        JwtService jwt = service(Duration.ofMinutes(15));

        JwtService.IssuedToken issued = jwt.issueAccessToken(principal());
        JWTClaimsSet claims = jwt.parse(issued.value(), JwtService.TYPE_ACCESS);
        AuthenticatedUser parsed = jwt.toPrincipal(claims);

        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.tenantId()).isEqualTo(1L);
        assertThat(parsed.username()).isEqualTo("planner");
        assertThat(parsed.displayName()).isEqualTo("计划员");
        assertThat(parsed.roles()).containsExactly("ADMIN");
        assertThat(parsed.permissions())
                .containsExactly("masterdata:item:read", "mrp:run:write");
        assertThat(parsed.forcePasswordChange()).isFalse();
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        JwtService jwt = service(Duration.ofMinutes(15));

        JwtService.IssuedToken refresh = jwt.issueRefreshToken(principal());

        assertThatThrownBy(() -> jwt.parse(refresh.value(), JwtService.TYPE_ACCESS))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = service(Duration.ofMinutes(15));
        String token = jwt.issueAccessToken(principal()).value();

        // Flip one character in the signature segment.
        int lastDot = token.lastIndexOf('.');
        char replacement = token.charAt(lastDot + 1) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, lastDot + 1) + replacement + token.substring(lastDot + 2);

        assertThatThrownBy(() -> jwt.parse(tampered, JwtService.TYPE_ACCESS))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtService jwt = service(Duration.ofMillis(1));
        String token = jwt.issueAccessToken(principal()).value();

        Thread.sleep(50);

        assertThatThrownBy(() -> jwt.parse(token, JwtService.TYPE_ACCESS))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService issuer = service(Duration.ofMinutes(15));
        String token = issuer.issueAccessToken(principal()).value();

        JwtService other = new JwtService(new SecurityProperties(
                "admin", "admin123", true,
                new SecurityProperties.Jwt("a-completely-different-secret-32-bytes-ok", 
                        Duration.ofMinutes(15), Duration.ofDays(7))));

        assertThatThrownBy(() -> other.parse(token, JwtService.TYPE_ACCESS))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> new SecurityProperties.Jwt(
                "too-short", Duration.ofMinutes(15), Duration.ofDays(7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
