package com.genisource.erp.finance.voucher;

import com.genisource.erp.finance.account.AccountRole;
import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.masterdata.item.ItemType;
import com.genisource.erp.platform.production.ProductionCostListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the two movements of a works order into their accounting sentences.
 *
 * <p>Issuing material moves value out of the store and into work in process. Completing
 * moves it out of work in process and into finished goods.
 *
 * <p>Both anchor to the stock document the step posted, not to the works order — an
 * order that completes in three batches is three movements of value, and one voucher
 * per movement is what keeps the ledger reconcilable to the stock ledger line by line.
 */
@Component
public class ProductionCostVoucherListener implements ProductionCostListener {

    private final VoucherService vouchers;
    private final ItemRepository items;

    public ProductionCostVoucherListener(VoucherService vouchers, ItemRepository items) {
        this.vouchers = vouchers;
        this.items = items;
    }

    @Override
    public void onMaterialIssued(Long orderId, String orderNo, LocalDate bizDate,
                                 Map<Long, BigDecimal> amountByItem, Long stockDocument) {
        if (amountByItem.isEmpty() || stockDocument == null) {
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        Map<AccountRole, BigDecimal> credits = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : amountByItem.entrySet()) {
            BigDecimal amount = entry.getValue();
            if (amount == null || amount.signum() == 0) {
                continue;
            }
            total = total.add(amount);
            AccountRole role = inventoryRoleOf(entry.getKey());
            credits.merge(role, amount, BigDecimal::add);
        }
        if (total.signum() == 0) {
            return;
        }
        List<VoucherDtos.RoleAmount> creditLines = new ArrayList<>();
        for (Map.Entry<AccountRole, BigDecimal> entry : credits.entrySet()) {
            creditLines.add(new VoucherDtos.RoleAmount(entry.getKey(), entry.getValue()));
        }
        vouchers.postFromDocument(new VoucherDtos.DocumentPosting(
                VoucherType.PRODUCTION, "PRD_ISSUE", stockDocument, orderNo, bizDate,
                null, "生产领料 " + orderNo,
                List.of(new VoucherDtos.RoleAmount(AccountRole.WIP, total)),
                creditLines));
    }

    @Override
    public void onCompletion(Long orderId, String orderNo, LocalDate bizDate, BigDecimal amount,
                              Long stockDocument) {
        if (amount.signum() == 0 || stockDocument == null) {
            return;
        }
        vouchers.postFromDocument(new VoucherDtos.DocumentPosting(
                VoucherType.PRODUCTION, "PRD_COMPLETION", stockDocument, orderNo, bizDate,
                null, "完工入库 " + orderNo,
                List.of(new VoucherDtos.RoleAmount(AccountRole.INVENTORY_FINISHED, amount)),
                List.of(new VoucherDtos.RoleAmount(AccountRole.WIP, amount))));
    }

    /**
     * Where the issued material sat before it left: raw material and consumables come
     * out of the raw-material account, sub-assemblies and finished goods out of the
     * finished-goods account. Crediting everything to raw material would quietly make
     * the stock of sub-assemblies impossible to reconcile.
     */
    private AccountRole inventoryRoleOf(Long itemId) {
        Item item = items.findById(itemId).orElse(null);
        if (item != null && (item.getItemType() == ItemType.SEMI
                || item.getItemType() == ItemType.FINISHED)) {
            return AccountRole.INVENTORY_FINISHED;
        }
        return AccountRole.INVENTORY_RAW;
    }
}
