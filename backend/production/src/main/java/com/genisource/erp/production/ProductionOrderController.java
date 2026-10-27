package com.genisource.erp.production;

import com.genisource.erp.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

/**
 * Works orders: what the shop floor is going to make, and the material it will consume.
 *
 * <p>Releasing, issuing and completing have their own permission codes because they are
 * three different people's jobs — a planner raises and releases, a storeman issues, a
 * supervisor reports completions — and a factory that wanted one person to do all three
 * grants all three to that person.
 */
@RestController
@RequestMapping("/api/production/orders")
@Tag(name = "Production :: orders", description = "Works orders, material issues and completions")
public class ProductionOrderController {

    private final ProductionService production;

    public ProductionOrderController(ProductionService production) {
        this.production = production;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('production:order:read')")
    public PageResponse<ProductionDtos.OrderView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) ProductionOrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return production.search(keyword, itemId, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('production:order:read')")
    public ProductionDtos.OrderView get(@PathVariable Long id) {
        return production.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('production:order:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise a works order, freezing the released BOM and routing onto it")
    public ProductionDtos.OrderView create(@Valid @RequestBody ProductionDtos.OrderRequest request) {
        return production.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('production:order:write')")
    public ProductionDtos.OrderView update(@PathVariable Long id,
                                           @Valid @RequestBody ProductionDtos.OrderRequest request) {
        return production.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('production:order:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        production.delete(id);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('production:order:write')")
    @Operation(summary = "Release to the floor, setting aside what material is available")
    public ProductionDtos.OrderView release(@PathVariable Long id) {
        return production.release(id);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('production:issue:write')")
    @Operation(summary = "Issue material to the order, moving it out of stock")
    public ProductionDtos.OrderView issue(@PathVariable Long id,
                                          @Valid @RequestBody ProductionDtos.IssueRequest request) {
        return production.issue(id, request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('production:complete:write')")
    @Operation(summary = "Book finished goods in against the order")
    public ProductionDtos.OrderView complete(@PathVariable Long id,
                                             @Valid @RequestBody
                                             ProductionDtos.CompleteRequest request) {
        return production.complete(id, request);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('production:order:write')")
    @Operation(summary = "Wrap the order up short; material it still holds goes back")
    public ProductionDtos.OrderView close(@PathVariable Long id) {
        return production.close(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('production:order:write')")
    public ProductionDtos.OrderView cancel(@PathVariable Long id) {
        return production.cancel(id);
    }
}
