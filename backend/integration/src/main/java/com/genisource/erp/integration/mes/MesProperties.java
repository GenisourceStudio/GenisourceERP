package com.genisource.erp.integration.mes;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How this ERP reaches MES.
 *
 * <p>Everything is off by default. A factory that runs only the ERP never sets these,
 * and the adapter must then be completely inert: no background jobs, no outbound
 * calls, no listener that writes anything. A feature that is merely "harmless" when
 * disabled is a feature that will eventually be half-enabled by accident.
 */
@ConfigurationProperties(prefix = "erp.mes")
public class MesProperties {

    /** Where MES lives. Blank means there is no MES. */
    private String baseUrl = "";

    private String clientId = "";

    private String clientSecret = "";

    /** The master switch. False means the adapter sleeps. */
    private boolean syncEnabled = false;

    /** How often the outbox worker looks for messages to deliver. */
    private int dispatchIntervalSeconds = 30;

    /** How many times a message is retried before it is marked dead. */
    private int maxAttempts = 4;

    public boolean isConfigured() {
        return syncEnabled
                && baseUrl != null && !baseUrl.isBlank()
                && clientId != null && !clientId.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public int getDispatchIntervalSeconds() {
        return dispatchIntervalSeconds;
    }

    public void setDispatchIntervalSeconds(int dispatchIntervalSeconds) {
        this.dispatchIntervalSeconds = dispatchIntervalSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
}
