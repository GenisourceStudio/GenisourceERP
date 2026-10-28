package com.genisource.erp.integration.mes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches and caches the access token MES issues us.
 *
 * <p>The token is cached until a minute before it expires, because a token that
 * expires mid-flight is a failure nobody needs: MES would reject the call with a
 * 401 that looks like a contract problem, and the retry queue would fill with
 * messages that were never wrong.
 *
 * <p>The token is fetched once at a time. Two clerks releasing orders at the same
 * moment would otherwise both ask for a token, and MES would issue two — harmless,
 * but it doubles the load on a system that has no reason to be busy.
 */
@Component
public class MesTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(MesTokenProvider.class);
    /** Renew a minute early, so a token never dies between being fetched and used. */
    private static final long RENEW_MARGIN_SECONDS = 60;

    private final MesProperties properties;
    private final RestClient restClient;
    private final AtomicReference<CachedToken> cached = new AtomicReference<>();

    public MesTokenProvider(MesProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /** The bearer token to use, fetching a new one when the old one is nearly dead. */
    public String token() {
        CachedToken current = cached.get();
        if (current != null && current.validAt().isAfter(Instant.now())) {
            return current.value();
        }
        synchronized (this) {
            current = cached.get();
            if (current != null && current.validAt().isAfter(Instant.now())) {
                return current.value();
            }
            CachedToken fresh = fetch();
            cached.set(fresh);
            return fresh.value();
        }
    }

    /** Forgets the token, so the next call fetches a new one. Used after a 401. */
    public void invalidate() {
        cached.set(null);
    }

    private CachedToken fetch() {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(properties.getBaseUrl() + "/oauth2/token")
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials&client_id=" + properties.getClientId()
                            + "&client_secret=" + properties.getClientSecret())
                    .retrieve()
                    .body(Map.class);
            String value = response == null ? null : (String) response.get("access_token");
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("MES returned no access_token");
            }
            long expiresIn = response.get("expires_in") instanceof Number number
                    ? number.longValue() : 3600;
            Instant validAt = Instant.now().plusSeconds(Math.max(expiresIn - RENEW_MARGIN_SECONDS, 30));
            log.info("Fetched an MES access token, valid for {} seconds", expiresIn);
            return new CachedToken(value, validAt);
        } catch (RuntimeException ex) {
            throw new MesException("Could not get an access token from MES", ex);
        }
    }

    private record CachedToken(String value, Instant validAt) {
    }
}
