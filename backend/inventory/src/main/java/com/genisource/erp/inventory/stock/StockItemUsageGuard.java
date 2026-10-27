package com.genisource.erp.inventory.stock;

import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.ItemUsageGuard;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Keeps stocked materials from being deactivated.
 *
 * <p>Implements the platform's {@link ItemUsageGuard}, so master data learns about
 * this rule without ever depending on the inventory module. A material that still sits
 * on a shelf cannot be switched off: the balance would point at something the
 * catalogue no longer offers, and the next issue would have nothing to pick.
 */
@Component
public class StockItemUsageGuard implements ItemUsageGuard {

    private final StockRepository stocks;

    public StockItemUsageGuard(StockRepository stocks) {
        this.stocks = stocks;
    }

    @Override
    public void assertCanDeactivate(Long itemId) {
        long held = stocks.countByItemIdAndOnHandGreaterThan(itemId, BigDecimal.ZERO);
        if (held > 0) {
            throw ApiException.inUse("Item still holds stock in " + held
                    + " place(s) and cannot be deactivated; move or issue the stock first");
        }
    }
}
