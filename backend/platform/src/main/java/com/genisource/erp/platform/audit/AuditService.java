package com.genisource.erp.platform.audit;

import com.genisource.erp.platform.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Writes audit entries. Uses a separate transaction so an audit row survives even when
 * the surrounding business transaction rolls back — a rejected document still needs to
 * explain who tried.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, Long userId, String username,
                       String resourceType, String resourceId, Map<String, Object> detail) {
        try {
            AuditLog entry = new AuditLog(userId, username, action);
            entry.describe(resourceType, resourceId);
            entry.setDetail(toJson(detail));
            currentRequest().ifPresent(request -> entry.setOrigin(request.getRemoteAddr(),
                    request.getHeader("User-Agent")));
            repository.save(entry);
        } catch (RuntimeException ex) {
            // Auditing must never break the business flow.
            log.warn("Failed to write audit entry action={} user={}", action, username, ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnderTenant(Long tenantId, String action, Long userId, String username,
                                  String resourceType, String resourceId, Map<String, Object> detail) {
        TenantContext.callWith(tenantId, () -> {
            record(action, userId, username, resourceType, resourceId, detail);
            return null;
        });
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JacksonException ex) {
            log.warn("Unable to serialise audit detail", ex);
            return null;
        }
    }

    private java.util.Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return java.util.Optional.of(attributes.getRequest());
        }
        return java.util.Optional.empty();
    }
}
