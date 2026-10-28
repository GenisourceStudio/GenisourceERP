package com.genisource.erp.platform.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Read side of the audit trail.
 *
 * <p>{@code JpaSpecificationExecutor} rather than a pile of finder methods: the console
 * filters on any combination of actor, action, resource and date range, which is six
 * methods if written out and one builder if not.
 */
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
}
