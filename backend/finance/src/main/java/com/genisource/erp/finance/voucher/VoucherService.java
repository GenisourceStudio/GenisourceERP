package com.genisource.erp.finance.voucher;

import com.genisource.erp.finance.account.AccountService;
import com.genisource.erp.finance.ledger.LedgerRow;
import com.genisource.erp.masterdata.partner.Partner;
import com.genisource.erp.masterdata.partner.PartnerRepository;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.PageResponse;
import com.genisource.erp.platform.numbering.DocumentNoAllocator;
import com.genisource.erp.platform.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vouchers: writing, posting, reversing, and the sentences the business writes for
 * itself.
 *
 * <p>Two doors lead in. The manual door — a person types a voucher, it sits as DRAFT,
 * someone reviews and posts it — is how adjustments and openings enter. The automatic
 * door — a receipt posts, a shipment posts, money moves — writes its voucher in the
 * same transaction as the event, born POSTED, because an inventory ledger that moved
 * while the general ledger stood still is two books claiming to be one.
 *
 * <p>Both doors obey the same rules: lines balance before anything posts, accounts are
 * leaves and alive, and one source document produces at most one voucher. The rules are
 * enforced here and nowhere else, which is the only way they hold everywhere.
 */
@Service
public class VoucherService {

    private static final int SCALE = 4;
    /** Line numbers step by ten, so an inserted line does not renumber the world. */
    private static final int LINE_STEP = 10;

    private final FinVoucherRepository vouchers;
    private final FinVoucherLineRepository lines;
    private final FinSettlementRepository settlements;
    private final AccountService accounts;
    private final PartnerRepository partners;
    private final DocumentNoAllocator numbers;
    private final AuditService audit;

    public VoucherService(FinVoucherRepository vouchers,
                          FinVoucherLineRepository lines,
                          FinSettlementRepository settlements,
                          AccountService accounts,
                          PartnerRepository partners,
                          DocumentNoAllocator numbers,
                          AuditService audit) {
        this.vouchers = vouchers;
        this.lines = lines;
        this.settlements = settlements;
        this.accounts = accounts;
        this.partners = partners;
        this.numbers = numbers;
        this.audit = audit;
    }

    // ------------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public PageResponse<VoucherDtos.VoucherView> search(String keyword,
                                                        VoucherType type,
                                                        VoucherStatus status,
                                                        LocalDate from,
                                                        LocalDate to,
                                                        int page,
                                                        int size) {
        Specification<FinVoucher> spec = specification(keyword, type, status, from, to);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("bizDate").descending().and(Sort.by("id").descending()));
        Page<FinVoucher> result = vouchers.findAll(spec, pageable);
        List<VoucherDtos.VoucherView> content = new ArrayList<>();
        for (FinVoucher voucher : result.getContent()) {
            content.add(view(voucher));
        }
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public VoucherDtos.VoucherView get(Long id) {
        return view(findWithLines(id));
    }

    // ------------------------------------------------------------------ write

    /** Creates a hand-written voucher. It stays DRAFT until someone posts it. */
    @Transactional
    public VoucherDtos.VoucherView create(VoucherDtos.VoucherRequest request) {
        FinVoucher voucher = new FinVoucher(numbers.next("JZ", request.bizDate()),
                request.voucherType(), request.bizDate(), null, null, blankToNull(request.remark()));
        voucher.replaceLines(buildLines(request.lines()));
        vouchers.save(voucher);
        audit.record("finance.voucher.create", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "fin_voucher", String.valueOf(voucher.getId()),
                Map.of("voucherNo", voucher.getVoucherNo(),
                        "total", voucher.getTotalDebit().toPlainString()));
        return view(voucher);
    }

    /** Edits a draft. Posted vouchers are history; they are corrected by reversal. */
    @Transactional
    public VoucherDtos.VoucherView update(Long id, VoucherDtos.VoucherRequest request) {
        FinVoucher voucher = findWithLines(id);
        if (!voucher.getStatus().isEditable()) {
            throw ApiException.invalidState("Only a draft voucher can be edited; "
                    + voucher.getVoucherNo() + " is " + voucher.getStatus().name());
        }
        voucher.replaceLines(buildLines(request.lines()));
        if (request.remark() != null) {
            voucher.setRemark(blankToNull(request.remark()));
        }
        audit.record("finance.voucher.update", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "fin_voucher", String.valueOf(id),
                Map.of("voucherNo", voucher.getVoucherNo()));
        return view(voucher);
    }

    /**
     * Posts a draft: balances must already balance, accounts must be postable, and from
     * this moment the voucher counts in every report.
     */
    @Transactional
    public VoucherDtos.VoucherView post(Long id) {
        FinVoucher voucher = findWithLines(id);
        if (voucher.getStatus() != VoucherStatus.DRAFT) {
            throw ApiException.invalidState("Only a draft can be posted; "
                    + voucher.getVoucherNo() + " is " + voucher.getStatus().name());
        }
        if (voucher.getLines().size() < 2) {
            throw ApiException.badRequest("A voucher needs at least two lines to balance");
        }
        assertAccountsPostable(voucher);
        if (!voucher.balanced()) {
            throw ApiException.badRequest("Debit " + voucher.getTotalDebit().toPlainString()
                    + " and credit " + voucher.getTotalCredit().toPlainString()
                    + " do not balance; a voucher that is off by "
                    + voucher.imbalance().stripTrailingZeros().toPlainString() + " cannot post");
        }
        voucher.post(CurrentUser.username().orElse("system"), Instant.now());
        audit.record("finance.voucher.post", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "fin_voucher", String.valueOf(id),
                Map.of("voucherNo", voucher.getVoucherNo(),
                        "total", voucher.getTotalDebit().toPlainString()));
        return view(voucher);
    }

    /**
     * Reverses a posted voucher with a red-letter one. The original keeps its number
     * and its place in history — it counts no more, but it is not erased, because an
     * erased mistake teaches nothing and audits nothing.
     */
    @Transactional
    public VoucherDtos.VoucherView reverse(Long id, VoucherDtos.ReverseRequest request) {
        FinVoucher original = findWithLines(id);
        if (original.getStatus() != VoucherStatus.POSTED) {
            throw ApiException.invalidState("Only a posted voucher can be reversed; "
                    + original.getVoucherNo() + " is " + original.getStatus().name());
        }
        if (vouchers.findByReversalOf(id).isPresent()) {
            throw ApiException.invalidState(original.getVoucherNo() + " has already been reversed");
        }
        return view(doReverse(original, request.bizDate()));
    }

    // --------------------------------------------- automatic posting (the seam)

    /**
     * Writes the voucher a business event implies, in the event's own transaction,
     * already posted. Idempotent: the same source document asks twice — a retry, a
     * redelivered message — and is answered with the voucher it already has.
     */
    @Transactional
    public Long postFromDocument(VoucherDtos.DocumentPosting posting) {
        FinVoucher existing = vouchers.findBySourceTypeAndSourceId(
                posting.sourceType(), posting.sourceId()).orElse(null);
        if (existing != null) {
            return existing.getId();
        }
        List<FinVoucherLine> all = roleLines(posting, true);
        all.addAll(roleLines(posting, false));

        FinVoucher voucher = new FinVoucher(numbers.next("JZ", posting.bizDate()),
                posting.voucherType(), posting.bizDate(), posting.sourceType(), posting.sourceId(),
                blankToNull(posting.summary()));
        voucher.replaceLines(all);
        assertAccountsPostable(voucher);
        if (!voucher.balanced()) {
            // A rule that produces unbalanced lines is a programming error, not a user
            // input problem: the event rolls back rather than the books absorbing it.
            throw ApiException.internal("Automatic posting for " + posting.sourceType()
                    + " produced debit " + voucher.getTotalDebit().toPlainString()
                    + " against credit " + voucher.getTotalCredit().toPlainString(), null);
        }
        voucher.post("system", Instant.now());
        vouchers.save(voucher);
        return voucher.getId();
    }

    /**
     * Records one movement of money and its voucher. The settlement row is the anchor:
     * a debt settled three times is three events, three vouchers, one balance.
     */
    @Transactional
    public void recordSettlement(boolean payable, LedgerRow row, BigDecimal amount) {
        FinSettlement settlement = new FinSettlement(
                payable ? FinSettlement.Side.PAYABLE : FinSettlement.Side.RECEIVABLE,
                row.getId(), amount.setScale(SCALE, RoundingMode.HALF_UP), LocalDate.now(),
                "结算 " + row.getSourceNo());
        settlements.save(settlement);

        List<VoucherDtos.RoleAmount> debits = List.of(new VoucherDtos.RoleAmount(
                payable ? com.genisource.erp.finance.account.AccountRole.AP
                        : com.genisource.erp.finance.account.AccountRole.BANK,
                amount));
        List<VoucherDtos.RoleAmount> credits = List.of(new VoucherDtos.RoleAmount(
                payable ? com.genisource.erp.finance.account.AccountRole.BANK
                        : com.genisource.erp.finance.account.AccountRole.AR,
                amount));
        postFromDocument(new VoucherDtos.DocumentPosting(
                payable ? VoucherType.PAYMENT : VoucherType.RECEIPT,
                "FIN_SETTLE", settlement.getId(), row.getSourceNo(), settlement.getBizDate(),
                row.getPartnerId(),
                (payable ? "付款 " : "收款 ") + row.getSourceNo(), debits, credits));
    }

    /** The source document was reversed; its voucher must stop counting, the same way. */
    @Transactional
    public void reverseForSource(String sourceType, Long sourceId, LocalDate bizDate) {
        FinVoucher original = vouchers.findBySourceTypeAndSourceId(sourceType, sourceId).orElse(null);
        if (original == null || original.getStatus() != VoucherStatus.POSTED) {
            return;
        }
        if (vouchers.findByReversalOf(original.getId()).isPresent()) {
            return;
        }
        doReverse(original, bizDate);
    }

    private FinVoucher doReverse(FinVoucher original, LocalDate bizDate) {
        FinVoucher reversal = FinVoucher.asReversalOf(original,
                numbers.next("JZ", bizDate), bizDate);
        reversal.post(CurrentUser.username().orElse("system"), Instant.now());
        vouchers.save(reversal);
        original.markReversed();
        vouchers.save(original);
        audit.record("finance.voucher.reverse", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "fin_voucher", String.valueOf(reversal.getId()),
                Map.of("reversalNo", reversal.getVoucherNo(),
                        "originalNo", original.getVoucherNo()));
        return reversal;
    }

    // --------------------------------------------------------------- internals

    /**
     * Turns role-and-amount pairs into lines, resolving each role through the chart.
     *
     * <p>Line numbers run across both sides of the voucher, not one series per side.
     * {@link FinVoucher#replaceLines} merges by line number, so a debit line 10 and a
     * credit line 10 would be the same row: the credit would overwrite the debit and
     * the voucher would silently lose a leg. One series for the whole voucher is the
     * only numbering that survives merging.
     */
    private List<FinVoucherLine> roleLines(VoucherDtos.DocumentPosting posting, boolean debit) {
        List<VoucherDtos.RoleAmount> amounts = debit ? posting.debits() : posting.credits();
        List<FinVoucherLine> result = new ArrayList<>();
        int lineNo = LINE_STEP * (debit ? 1 : posting.debits().size() + 1);
        for (VoucherDtos.RoleAmount amount : amounts) {
            if (amount.amount() == null || amount.amount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Long accountId = accounts.requireRole(amount.role()).getId();
            result.add(new FinVoucherLine(lineNo, accountId,
                    debit ? amount.amount() : BigDecimal.ZERO,
                    debit ? BigDecimal.ZERO : amount.amount(),
                    posting.partnerId(), posting.summary()));
            lineNo += LINE_STEP;
        }
        return result;
    }

    private List<FinVoucherLine> buildLines(List<VoucherDtos.LineRequest> requested) {
        if (requested == null || requested.size() < 2) {
            throw ApiException.badRequest("A voucher needs at least two lines to balance");
        }
        List<FinVoucherLine> built = new ArrayList<>();
        int lineNo = LINE_STEP;
        for (VoucherDtos.LineRequest request : requested) {
            BigDecimal debit = scale(request.debit());
            BigDecimal credit = scale(request.credit());
            boolean debited = debit.signum() > 0;
            boolean credited = credit.signum() > 0;
            if (debited == credited) {
                throw ApiException.badRequest("Line " + (lineNo / LINE_STEP)
                        + " must carry exactly one of debit or credit");
            }
            accounts.require(request.accountId());
            built.add(new FinVoucherLine(lineNo, request.accountId(), debit, credit,
                    request.partnerId(), blankToNull(request.summary())));
            lineNo += LINE_STEP;
        }
        return built;
    }

    private void assertAccountsPostable(FinVoucher voucher) {
        for (FinVoucherLine line : voucher.getLines()) {
            var account = accounts.require(line.getAccountId());
            if (!account.isLeaf()) {
                throw ApiException.badRequest("科目 " + account.getCode()
                        + " 是汇总科目，不能记账");
            }
            if (!account.isActive()) {
                throw ApiException.badRequest("科目 " + account.getCode() + " 已停用，不能记账");
            }
        }
    }

    private VoucherDtos.VoucherView view(FinVoucher voucher) {
        Map<Long, Partner> partnerById = voucher.getLines().stream()
                .map(FinVoucherLine::getPartnerId).filter(java.util.Objects::nonNull).distinct()
                .collect(Collectors.toMap(Function.identity(),
                        id -> partners.findById(id).orElse(null)));
        // One lookup per account, not per line: a twenty-line voucher would otherwise
        // ask for the same four accounts eighty times on a list page.
        Map<Long, com.genisource.erp.finance.account.FinAccount> accountById =
                voucher.getLines().stream().map(FinVoucherLine::getAccountId).distinct()
                        .collect(Collectors.toMap(Function.identity(), accounts::require));
        List<VoucherDtos.LineView> lineViews = voucher.getLines().stream()
                .map(line -> {
                    var account = accountById.get(line.getAccountId());
                    return new VoucherDtos.LineView(
                            line.getId(), line.getLineNo(), line.getAccountId(),
                            account.getCode(), account.getName(),
                            line.getDebit(), line.getCredit(), line.getPartnerId(),
                            line.getPartnerId() == null ? null
                                    : java.util.Optional.ofNullable(partnerById.get(line.getPartnerId()))
                                    .map(Partner::getName).orElse(null),
                            line.getSummary());
                })
                .toList();
        Long reversedBy = voucher.getStatus() == VoucherStatus.REVERSED
                ? vouchers.findByReversalOf(voucher.getId()).map(FinVoucher::getId).orElse(null)
                : null;
        return new VoucherDtos.VoucherView(
                voucher.getId(), voucher.getVoucherNo(), voucher.getVoucherType(),
                voucher.getBizDate(), voucher.getStatus(), voucher.getSourceType(),
                voucher.getSourceId(), voucher.getReversalOf(), reversedBy,
                voucher.getTotalDebit(), voucher.getTotalCredit(), voucher.getPostedBy(),
                voucher.getPostedAt(), voucher.getRemark(), lineViews);
    }

    private FinVoucher findWithLines(Long id) {
        return vouchers.findWithLinesById(id)
                .orElseThrow(() -> ApiException.notFound("Voucher " + id + " does not exist"));
    }

    private Specification<FinVoucher> specification(String keyword, VoucherType type,
                                                    VoucherStatus status, LocalDate from,
                                                    LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("voucherType"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bizDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("bizDate"), to));
            }
            if (keyword != null && !keyword.isBlank()) {
                String term = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("voucherNo")), term));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, 200));
    }
}
