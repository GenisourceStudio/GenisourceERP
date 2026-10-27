package com.genisource.erp.mrp;

import com.genisource.erp.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Material requirements planning: run it, read it, act on it.
 *
 * <p>Turning a recommendation into an order asks for the target module's permission as
 * well as MRP's own. Generous as it looks, the alternative is worse: a planner who may run
 * a plan would otherwise be able to raise purchase orders without holding purchasing's
 * permission, and the audit trail would show a document nobody was entitled to create.
 */
@RestController
@RequestMapping("/api/mrp")
@Tag(name = "MRP :: planning", description = "Material requirements planning runs and plans")
public class MrpController {

    private final MrpService mrp;

    public MrpController(MrpService mrp) {
        this.mrp = mrp;
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('mrp:run:read')")
    public PageResponse<MrpDtos.RunView> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return mrp.search(page, size);
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAuthority('mrp:run:read')")
    public MrpDtos.RunDetail get(@PathVariable Long id) {
        return mrp.get(id);
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAuthority('mrp:run:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Run MRP: net demand against stock and open supply, then explode what is short")
    public MrpDtos.RunDetail run(@Valid @RequestBody(required = false) MrpDtos.RunRequest request) {
        return mrp.run(request == null ? new MrpDtos.RunRequest(null, null, null, null, null,
                null, null) : request);
    }

    @PostMapping("/plans/{planId}/to-purchase-order")
    @PreAuthorize("hasAuthority('mrp:run:write') and hasAuthority('purchase:order:write')")
    @Operation(summary = "Raise a purchase order from a plan row")
    public MrpDtos.PlanView toPurchaseOrder(@PathVariable Long planId,
                                            @Valid @RequestBody(required = false)
                                            MrpDtos.ConvertRequest request) {
        return mrp.convertToPurchaseOrder(planId, request);
    }

    @PostMapping("/plans/{planId}/to-production-order")
    @PreAuthorize("hasAuthority('mrp:run:write') and hasAuthority('production:order:write')")
    @Operation(summary = "Raise a works order from a plan row")
    public MrpDtos.PlanView toProductionOrder(@PathVariable Long planId,
                                              @Valid @RequestBody(required = false)
                                              MrpDtos.ConvertRequest request) {
        return mrp.convertToProductionOrder(planId, request);
    }

    @PostMapping("/plans/{planId}/ignore")
    @PreAuthorize("hasAuthority('mrp:run:write')")
    @Operation(summary = "Drop a recommendation, recording why")
    public MrpDtos.PlanView ignore(@PathVariable Long planId,
                                   @Valid @RequestBody(required = false)
                                   MrpDtos.IgnoreRequest request) {
        return mrp.ignore(planId, request == null ? null : request.remark());
    }
}
