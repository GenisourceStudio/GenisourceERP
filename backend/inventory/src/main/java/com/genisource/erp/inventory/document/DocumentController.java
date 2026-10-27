package com.genisource.erp.inventory.document;

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

/**
 * Stock documents: receipts, issues, transfers and counts.
 *
 * <p>One controller for four document types because they share one envelope, one
 * validation and one posting routine. The type in the payload decides which it is.
 */
@RestController
@RequestMapping("/api/inventory/documents")
@Tag(name = "Inventory :: documents", description = "Receipts, issues, transfers and counts")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    public PageResponse<DocumentDtos.DocumentView> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DocumentType docType,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documents.search(q, docType, status, warehouseId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    public DocumentDtos.DocumentView get(@PathVariable Long id) {
        return documents.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft document; the number is allocated on creation")
    public DocumentDtos.DocumentView create(@Valid @RequestBody DocumentDtos.DocumentRequest request) {
        return documents.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    @Operation(summary = "Edit a draft; a posted document is frozen")
    public DocumentDtos.DocumentView update(@PathVariable Long id,
                                            @Valid @RequestBody DocumentDtos.DocumentRequest request) {
        return documents.update(id, request);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    @Operation(summary = "Post the document: the ledger and the balances change together")
    public DocumentDtos.DocumentView post(@PathVariable Long id) {
        return documents.post(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    @Operation(summary = "Undo a posted document by writing its mirror image")
    public DocumentDtos.DocumentView reverse(@PathVariable Long id) {
        return documents.reverse(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a draft; a posted document can only be reversed")
    public void delete(@PathVariable Long id) {
        documents.delete(id);
    }
}
