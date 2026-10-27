package com.genisource.erp.masterdata.partner;

import com.genisource.erp.masterdata.shared.RecordStatus;
import com.genisource.erp.masterdata.shared.StatusRequest;
import com.genisource.erp.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/masterdata/partners")
@Tag(name = "Master data :: partners", description = "Customers and suppliers")
public class PartnerController {

    private final PartnerService partners;

    public PartnerController(PartnerService partners) {
        this.partners = partners;
    }

    /**
     * @param role CUSTOMER or SUPPLIER; BOTH partners are returned for either value
     */
    @GetMapping
    @PreAuthorize("hasAuthority('masterdata:partner:read')")
    @Operation(summary = "Search partners by role and keyword")
    public PageResponse<PartnerDtos.PartnerView> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PartnerType role,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return partners.search(q, role, status, page, size);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('masterdata:partner:read')")
    public List<PartnerDtos.PartnerOption> options(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "SUPPLIER") PartnerType role,
            @RequestParam(defaultValue = "20") int limit) {
        return partners.options(q, role, limit);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:partner:read')")
    public PartnerDtos.PartnerView get(@PathVariable Long id) {
        return partners.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('masterdata:partner:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public PartnerDtos.PartnerView create(@Valid @RequestBody PartnerDtos.PartnerRequest request) {
        return partners.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:partner:write')")
    public PartnerDtos.PartnerView update(@PathVariable Long id,
                                          @Valid @RequestBody PartnerDtos.PartnerRequest request) {
        return partners.update(id, request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('masterdata:partner:write')")
    public PartnerDtos.PartnerView changeStatus(@PathVariable Long id,
                                                @Valid @RequestBody StatusRequest request) {
        return partners.changeStatus(id, request.status());
    }
}
