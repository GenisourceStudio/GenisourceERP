package com.genisource.erp.masterdata.partner;

import com.genisource.erp.platform.common.Codes;
import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.PageResponse;
import com.genisource.erp.platform.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Customer and supplier use cases.
 *
 * <p>The list is filtered by <em>role</em>, not by stored type: asking for suppliers must
 * return partners stored as {@code BOTH} as well, otherwise a company that buys and sells
 * silently disappears from half the screens.
 */
@Service
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerRepository partners;
    private final AuditService audit;

    public PartnerService(PartnerRepository partners, AuditService audit) {
        this.partners = partners;
        this.audit = audit;
    }

    public PageResponse<PartnerDtos.PartnerView> search(String keyword,
                                                        PartnerType role,
                                                        RecordStatus status,
                                                        int page,
                                                        int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                Sort.by("code").ascending());
        Page<Partner> result = partners.findAll(specification(keyword, role, status), pageable);
        return PageResponse.of(result, result.getContent().stream().map(this::toView).toList());
    }

    public PartnerDtos.PartnerView get(Long id) {
        return toView(require(id));
    }

    public List<PartnerDtos.PartnerOption> options(String keyword, PartnerType role, int limit) {
        String term = "%" + (keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return partners.searchTop(term, role, RecordStatus.ACTIVE,
                        PageRequest.of(0, Math.min(Math.max(limit, 1), 50))).stream()
                .map(partner -> new PartnerDtos.PartnerOption(
                        partner.getId(), partner.getCode(), partner.getName(), partner.getPartnerType()))
                .toList();
    }

    @Transactional
    public PartnerDtos.PartnerView create(PartnerDtos.PartnerRequest request) {
        String code = requireCode(request.code());
        if (partners.existsByCode(code)) {
            throw ApiException.duplicateCode("Partner code " + code + " already exists");
        }
        Partner partner = new Partner(code, request.name().trim(), request.partnerType());
        apply(partner, request);
        partners.save(partner);
        audit.record("partner.create", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_partner", String.valueOf(partner.getId()),
                Map.of("code", partner.getCode(), "name", partner.getName()));
        return toView(partner);
    }

    @Transactional
    public PartnerDtos.PartnerView update(Long id, PartnerDtos.PartnerRequest request) {
        Partner partner = require(id);
        String code = requireCode(request.code());
        if (!code.equals(partner.getCode()) && partners.existsByCode(code)) {
            throw ApiException.duplicateCode("Partner code " + code + " already exists");
        }
        partner.setCode(code);
        partner.setName(request.name().trim());
        partner.setPartnerType(request.partnerType());
        apply(partner, request);
        partners.save(partner);
        audit.record("partner.update", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_partner", String.valueOf(partner.getId()),
                Map.of("code", partner.getCode(), "name", partner.getName()));
        return toView(partner);
    }

    @Transactional
    public PartnerDtos.PartnerView changeStatus(Long id, RecordStatus target) {
        Partner partner = require(id);
        partner.setStatus(target);
        partners.save(partner);
        audit.record("partner.status", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_partner", String.valueOf(partner.getId()), Map.of("status", target.name()));
        return toView(partner);
    }

    // ------------------------------------------------------------- internals

    private Partner require(Long id) {
        return partners.findById(id)
                .orElseThrow(() -> ApiException.notFound("Partner " + id + " does not exist"));
    }

    private String requireCode(String raw) {
        String code = Codes.normalise(raw);
        if (code == null) {
            throw ApiException.badRequest("Partner code is required");
        }
        if (code.length() > 32) {
            throw ApiException.badRequest("Partner code must be at most 32 characters");
        }
        return code;
    }

    private Specification<Partner> specification(String keyword, PartnerType role, RecordStatus status) {
        Specification<Partner> spec = (root, query, cb) -> cb.conjunction();
        if (keyword != null && !keyword.isBlank()) {
            String term = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), term),
                    cb.like(cb.lower(root.get("name")), term),
                    cb.like(cb.lower(cb.coalesce(root.get("shortName"), "")), term),
                    cb.like(cb.lower(cb.coalesce(root.get("contactName"), "")), term)));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> role == PartnerType.BOTH
                    ? cb.equal(root.get("partnerType"), PartnerType.BOTH)
                    : cb.or(cb.equal(root.get("partnerType"), role),
                            cb.equal(root.get("partnerType"), PartnerType.BOTH)));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return spec;
    }

    private void apply(Partner partner, PartnerDtos.PartnerRequest request) {
        partner.setShortName(request.shortName());
        partner.setTaxNo(request.taxNo());
        partner.setContactName(request.contactName());
        partner.setPhone(request.phone());
        partner.setEmail(request.email());
        partner.setAddress(request.address());
        partner.setCurrency(request.currency());
        partner.setPaymentTermsDays(request.paymentTermsDays());
        partner.setCreditLimit(request.creditLimit());
        partner.setRemark(request.remark());
        if (request.status() != null) {
            partner.setStatus(request.status());
        }
        partner.tidy();
    }

    private PartnerDtos.PartnerView toView(Partner partner) {
        return new PartnerDtos.PartnerView(
                partner.getId(),
                partner.getCode(),
                partner.getName(),
                partner.getShortName(),
                partner.getPartnerType(),
                partner.getTaxNo(),
                partner.getContactName(),
                partner.getPhone(),
                partner.getEmail(),
                partner.getAddress(),
                partner.getCurrency(),
                partner.getPaymentTermsDays(),
                partner.getCreditLimit(),
                partner.getRemark(),
                partner.getStatus(),
                partner.getRowVersion(),
                partner.getUpdatedAt());
    }
}
