package com.genisource.erp.integration.mes;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * Wires the MES adapter.
 *
 * <p>Everything here is conditional on MES being configured. A build without a MES
 * beside it must not start a scheduler that has nothing to do, and must not create an
 * HTTP client that nobody will use.
 */
@Configuration
@EnableConfigurationProperties(MesProperties.class)
@EnableScheduling
public class MesIntegrationConfig {

    /**
     * The HTTP client always exists, even with MES switched off: the adapter sleeps
     * rather than disappearing, so the beans that depend on it stay simple. A client
     * that only existed when MES was on would mean every caller had to ask whether
     * MES was configured before it could do anything.
     */
    @Bean
    public RestClient mesRestClient() {
        // Built directly rather than from the auto-configured builder: the adapter
        // needs nothing from the application's HTTP setup, and a build without the
        // restclient module should still start.
        return RestClient.create();
    }
}
