package com.genisource.erp.platform.admin;

import com.genisource.erp.platform.audit.AuditLog;
import com.genisource.erp.platform.audit.AuditLogRepository;
import com.genisource.erp.platform.common.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Query side of the audit trail.
 *
 * <p>Read-only by design. An audit log that can be edited is not an audit log, so there
 * is no update and no delete here even though JPA would happily offer both.
 */
@Service
public class AuditQueryService {

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AuditView> list(String action, String username, String resourceType,
                                                  String resourceId, LocalDate from, LocalDate to,
                                                  int page, int size) {
        List<Specification<AuditLog>> parts = new ArrayList<>();
        if (StringUtils.hasText(action)) {
            parts.add(equal("action", action.trim()));
        }
        if (StringUtils.hasText(username)) {
            parts.add(equal("username", username.trim()));
        }
        if (StringUtils.hasText(resourceType)) {
            parts.add(equal("resourceType", resourceType.trim()));
        }
        if (StringUtils.hasText(resourceId)) {
            parts.add(equal("resourceId", resourceId.trim()));
        }
        if (from != null) {
            parts.add((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), startOfDay(from)));
        }
        if (to != null) {
            parts.add((root, query, builder) ->
                    builder.lessThan(root.get("createdAt"), startOfDay(to.plusDays(1))));
        }

        Specification<AuditLog> where = parts.stream().reduce(Specification::and).orElse(null);
        Page<AuditLog> result = repository.findAll(where,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result, this::view);
    }

    private static Specification<AuditLog> equal(String attribute, String value) {
        return (root, query, builder) -> builder.equal(root.get(attribute), value);
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private AdminDtos.AuditView view(AuditLog entry) {
        return new AdminDtos.AuditView(
                entry.getId(),
                entry.getUsername(),
                entry.getAction(),
                entry.getResourceType(),
                entry.getResourceId(),
                entry.getDetail(),
                entry.getIpAddress(),
                entry.getCreatedAt());
    }
}
