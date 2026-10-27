package com.genisource.erp.platform.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the row-level tenant discriminator into Hibernate.
 *
 * <p>Hibernate 7 enables the discriminator strategy automatically as soon as an entity
 * carries {@code @TenantId}; the only plumbing required is the resolver. There is
 * therefore no {@code hibernate.multiTenancy} property to set — registering the
 * resolver below is the whole configuration.
 */
@Configuration
public class TenantHibernateConfig {

    @Bean
    HibernatePropertiesCustomizer tenantDiscriminatorCustomizer(TenantIdentifierResolver resolver) {
        return properties -> properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
