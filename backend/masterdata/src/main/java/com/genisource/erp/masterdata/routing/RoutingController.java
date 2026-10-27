package com.genisource.erp.masterdata.routing;

import com.genisource.erp.platform.common.RevisionStatus;
import com.genisource.erp.masterdata.shared.VersionRequest;
import com.genisource.erp.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/masterdata/routings")
@Tag(name = "Master data :: routings", description = "Process routes with operations bound to work centres")
public class RoutingController {

    private final RoutingService routings;

    public RoutingController(RoutingService routings) {
        this.routings = routings;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('masterdata:routing:read')")
    public PageResponse<RoutingDtos.RoutingSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RevisionStatus status,
            @RequestParam(required = false) Long itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return routings.search(q, status, itemId, page, size);
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('masterdata:routing:read')")
    public List<RoutingDtos.RoutingSummary> versions(@RequestParam Long itemId) {
        return routings.versionsOf(itemId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:routing:read')")
    public RoutingDtos.RoutingDetail get(@PathVariable Long id) {
        return routings.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('masterdata:routing:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public RoutingDtos.RoutingDetail create(@Valid @RequestBody RoutingDtos.RoutingRequest request) {
        return routings.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:routing:write')")
    @Operation(summary = "Replace a draft routing; released revisions are immutable")
    public RoutingDtos.RoutingDetail update(@PathVariable Long id,
                                            @Valid @RequestBody RoutingDtos.RoutingRequest request) {
        return routings.update(id, request);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('masterdata:routing:write')")
    public RoutingDtos.RoutingDetail release(@PathVariable Long id) {
        return routings.release(id);
    }

    @PostMapping("/{id}/obsolete")
    @PreAuthorize("hasAuthority('masterdata:routing:write')")
    public RoutingDtos.RoutingDetail obsolete(@PathVariable Long id) {
        return routings.obsolete(id);
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAuthority('masterdata:routing:write')")
    public RoutingDtos.RoutingDetail copy(@PathVariable Long id,
                                          @Valid @RequestBody VersionRequest request) {
        return routings.copy(id, request.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:routing:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        routings.delete(id);
    }
}
