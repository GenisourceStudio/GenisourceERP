package com.genisource.erp.platform.admin;

import com.genisource.erp.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The audit trail.
 *
 * <p>Read-only: an entry is a fact about what happened, not a record the console is
 * allowed to tidy up. Filters exist because the question is always "who touched this
 * document", never "show me everything".
 */
@RestController
@RequestMapping("/api/system/audit")
@Tag(name = "System :: audit", description = "Append-only trail of state-changing actions")
public class AuditController {

    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sys:audit:read')")
    @Operation(summary = "Page through audit entries, newest first")
    public PageResponse<AdminDtos.AuditView> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return audit.list(action, username, resourceType, resourceId, from, to, page, size);
    }
}
