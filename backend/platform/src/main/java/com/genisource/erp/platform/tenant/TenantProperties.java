package com.genisource.erp.platform.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param mode        single factory or SaaS
 * @param defaultCode tenant code used by a private deployment and by background jobs
 */
@ConfigurationProperties(prefix = "erp.tenant")
public record TenantProperties(TenantMode mode, String defaultCode) {

    public TenantProperties {
        if (mode == null) {
            mode = TenantMode.SINGLE;
        }
        if (defaultCode == null || defaultCode.isBlank()) {
            defaultCode = "default";
        }
    }
}
