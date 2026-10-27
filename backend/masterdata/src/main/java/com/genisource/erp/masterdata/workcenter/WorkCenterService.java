package com.genisource.erp.masterdata.workcenter;

import com.genisource.erp.masterdata.shared.Codes;
import com.genisource.erp.masterdata.shared.RecordStatus;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class WorkCenterService {

    private final WorkCenterRepository workCenters;
    private final AuditService audit;

    public WorkCenterService(WorkCenterRepository workCenters, AuditService audit) {
        this.workCenters = workCenters;
        this.audit = audit;
    }

    /** Work centres are few (tens), so the console loads them all and filters locally. */
    public List<WorkCenterDtos.WorkCenterView> list(RecordStatus status) {
        return workCenters.findAllByOrderByCodeAsc().stream()
                .filter(center -> status == null || center.getStatus() == status)
                .map(this::toView)
                .toList();
    }

    @Transactional
    public WorkCenterDtos.WorkCenterView create(WorkCenterDtos.WorkCenterRequest request) {
        String code = requireCode(request.code());
        if (workCenters.existsByCode(code)) {
            throw ApiException.duplicateCode("Work centre code " + code + " already exists");
        }
        WorkCenter center = new WorkCenter(code, request.name().trim());
        apply(center, request);
        workCenters.save(center);
        audit.record("work-center.create", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_work_center", String.valueOf(center.getId()), Map.of("code", center.getCode()));
        return toView(center);
    }

    @Transactional
    public WorkCenterDtos.WorkCenterView update(Long id, WorkCenterDtos.WorkCenterRequest request) {
        WorkCenter center = require(id);
        String code = requireCode(request.code());
        if (!code.equals(center.getCode()) && workCenters.existsByCode(code)) {
            throw ApiException.duplicateCode("Work centre code " + code + " already exists");
        }
        center.setCode(code);
        center.setName(request.name().trim());
        apply(center, request);
        workCenters.save(center);
        audit.record("work-center.update", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_work_center", String.valueOf(center.getId()), Map.of("code", center.getCode()));
        return toView(center);
    }

    @Transactional
    public WorkCenterDtos.WorkCenterView changeStatus(Long id, RecordStatus target) {
        WorkCenter center = require(id);
        center.setStatus(target);
        workCenters.save(center);
        audit.record("work-center.status", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_work_center", String.valueOf(center.getId()), Map.of("status", target.name()));
        return toView(center);
    }

    // ------------------------------------------------------------- internals

    WorkCenter require(Long id) {
        return workCenters.findById(id)
                .orElseThrow(() -> ApiException.notFound("Work centre " + id + " does not exist"));
    }

    private String requireCode(String raw) {
        String code = Codes.normalise(raw);
        if (code == null) {
            throw ApiException.badRequest("Work centre code is required");
        }
        return code;
    }

    private void apply(WorkCenter center, WorkCenterDtos.WorkCenterRequest request) {
        center.setDepartment(request.department());
        center.setCapacityMinutesPerDay(request.capacityMinutesPerDay());
        center.setEfficiencyPercent(request.efficiencyPercent());
        center.setCostRatePerHour(request.costRatePerHour());
        if (request.status() != null) {
            center.setStatus(request.status());
        }
        center.tidy();
        if (center.getEfficiencyPercent().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Efficiency must be greater than zero");
        }
    }

    private WorkCenterDtos.WorkCenterView toView(WorkCenter center) {
        return new WorkCenterDtos.WorkCenterView(
                center.getId(),
                center.getCode(),
                center.getName(),
                center.getDepartment(),
                center.getCapacityMinutesPerDay(),
                center.getEfficiencyPercent(),
                center.availableMinutesPerDay(),
                center.getCostRatePerHour(),
                center.getStatus(),
                center.getRowVersion(),
                center.getUpdatedAt());
    }
}
