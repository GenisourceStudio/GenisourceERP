package com.genisource.erp.masterdata.bom;

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

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/masterdata/boms")
@Tag(name = "Master data :: bills of materials", description = "Multi-level BOMs with revisions and substitutes")
public class BomController {

    private final BomService boms;

    public BomController(BomService boms) {
        this.boms = boms;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('masterdata:bom:read')")
    @Operation(summary = "Search BOM revisions by item, status or keyword")
    public PageResponse<BomDtos.BomSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RevisionStatus status,
            @RequestParam(required = false) Long itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return boms.search(q, status, itemId, page, size);
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('masterdata:bom:read')")
    @Operation(summary = "Every revision of one item, newest first")
    public List<BomDtos.BomSummary> versions(@RequestParam Long itemId) {
        return boms.versionsOf(itemId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:bom:read')")
    public BomDtos.BomDetail get(@PathVariable Long id) {
        return boms.get(id);
    }

    @GetMapping("/{id}/tree")
    @PreAuthorize("hasAuthority('masterdata:bom:read')")
    @Operation(summary = "Expand the BOM through released revisions, multiplying quantities and loss rates")
    public BomDtos.BomTreeNode tree(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal qty,
            @RequestParam(defaultValue = "10") int maxLevels) {
        return boms.tree(id, qty, maxLevels);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('masterdata:bom:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public BomDtos.BomDetail create(@Valid @RequestBody BomDtos.BomRequest request) {
        return boms.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:bom:write')")
    @Operation(summary = "Replace a draft BOM; released revisions are immutable")
    public BomDtos.BomDetail update(@PathVariable Long id,
                                    @Valid @RequestBody BomDtos.BomRequest request) {
        return boms.update(id, request);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('masterdata:bom:write')")
    @Operation(summary = "Release a draft; retires the previously released revision of the same item")
    public BomDtos.BomDetail release(@PathVariable Long id) {
        return boms.release(id);
    }

    @PostMapping("/{id}/obsolete")
    @PreAuthorize("hasAuthority('masterdata:bom:write')")
    public BomDtos.BomDetail obsolete(@PathVariable Long id) {
        return boms.obsolete(id);
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAuthority('masterdata:bom:write')")
    @Operation(summary = "Clone any revision into a new draft under a new version label")
    public BomDtos.BomDetail copy(@PathVariable Long id,
                                  @Valid @RequestBody VersionRequest request) {
        return boms.copy(id, request.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:bom:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a draft; released revisions must be obsoleted instead")
    public void delete(@PathVariable Long id) {
        boms.delete(id);
    }
}
