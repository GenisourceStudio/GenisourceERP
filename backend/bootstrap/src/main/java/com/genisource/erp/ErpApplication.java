package com.genisource.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the modular monolith.
 *
 * <p>Business modules are Maven modules under {@code com.genisource.erp.*}; they are
 * discovered by component scan from this package. Keeping one deployable keeps
 * transactions and referential integrity simple, which matters far more for a factory
 * of 50–500 people than independent scaling of microservices.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.genisource.erp")
public class ErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpApplication.class, args);
    }
}
