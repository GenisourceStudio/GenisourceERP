package com.genisource.erp.masterdata.workcenter;

import com.genisource.erp.masterdata.shared.RecordStatus;
import com.genisource.erp.masterdata.shared.StatusRequest;
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
@RequestMapping("/api/masterdata/work-centers")
@Tag(name = "Master data :: work centres", description = "Production resources with capacity and cost rate")
public class WorkCenterController {

    private final WorkCenterService workCenters;

    public WorkCenterController(WorkCenterService workCenters) {
        this.workCenters = workCenters;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('masterdata:routing:read')")
    @Operation(summary = "All work centres, optionally filtered by status")
    public List<WorkCenterDtos.WorkCenterView> list(@RequestParam(required = false) RecordStatus status) {
        return workCenters.list(status);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('masterdata:routing:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkCenterDtos.WorkCenterView create(@Valid @RequestBody WorkCenterDtos.WorkCenterRequest request) {
        return workCenters.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('masterdata:routing:write')")
    public WorkCenterDtos.WorkCenterView update(@PathVariable Long id,
                                                @Valid @RequestBody WorkCenterDtos.WorkCenterRequest request) {
        return workCenters.update(id, request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('masterdata:routing:write')")
    public WorkCenterDtos.WorkCenterView changeStatus(@PathVariable Long id,
                                                      @Valid @RequestBody StatusRequest request) {
        return workCenters.changeStatus(id, request.status());
    }
}
