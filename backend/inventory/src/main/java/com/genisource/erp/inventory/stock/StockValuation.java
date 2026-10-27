package com.genisource.erp.inventory.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Moving weighted average valuation.
 *
 * <p>Pure arithmetic, kept away from JPA on purpose: this is the rule most likely to be
 * argued about, and it deserves a unit test that does not need a database.
 *
 * <p>The rule, stated once:
 * <ul>
 *   <li>the <strong>total</strong> is the authoritative value; the unit average is derived
 *       from it;</li>
 *   <li>incoming stock adds {@code qty × unitCost} to the total, then the average is
 *       recomputed as {@code total ÷ qty};</li>
 *   <li>outgoing stock is valued at the current average and subtracts
 *       {@code qty × average} from the total;</li>
 *   <li>an empty balance has zero value and zero average — the next receipt starts a
 *       fresh average rather than inheriting a stale one.</li>
 * </ul>
 *
 * <p>Why the total is authoritative: it accumulates exactly. If the average were the
 * source of truth, every receipt would multiply a rounded average by a quantity and the
 * error would compound across thousands of movements. Here the total only ever changes
 * by an addition or a subtraction of an exact amount.
 */
public final class StockValuation {

    /** Money scale. Four decimals, because unit costs carry fractions of a cent. */
    public static final int SCALE = 4;

    private StockValuation() {
    }

    /**
     * The balance after a receipt.
     *
     * @param currentQty     quantity on hand before the receipt
     * @param currentTotal   value on hand before the receipt
     * @param qty            quantity received, always positive
     * @param unitCost       cost per unit of the received goods
     */
    public static Result receive(BigDecimal currentQty,
                                 BigDecimal currentTotal,
                                 BigDecimal qty,
                                 BigDecimal unitCost) {
        BigDecimal inQty = requirePositive(qty, "Received quantity");
        BigDecimal inValue = inQty.multiply(unitCost).setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal newQty = currentQty.add(inQty);
        BigDecimal newTotal = currentTotal.add(inValue).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal newAvg = average(newTotal, newQty);

        return new Result(newQty, newTotal, newAvg, inValue);
    }

    /**
     * The balance after an issue.
     *
     * @param currentQty   quantity on hand before the issue
     * @param currentTotal value on hand before the issue
     * @param currentAvg   current unit average, the valuation price of the outgoing goods
     * @param qty          quantity issued, always positive
     */
    public static Result issue(BigDecimal currentQty,
                               BigDecimal currentTotal,
                               BigDecimal currentAvg,
                               BigDecimal qty) {
        BigDecimal outQty = requirePositive(qty, "Issued quantity");
        BigDecimal outValue = outQty.multiply(currentAvg).setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal newQty = currentQty.subtract(outQty);
        BigDecimal newTotal = currentTotal.subtract(outValue).setScale(SCALE, RoundingMode.HALF_UP);
        if (newQty.signum() == 0) {
            // Nothing left: drop the residue so a later receipt starts from a clean zero.
            return new Result(BigDecimal.ZERO.setScale(6), BigDecimal.ZERO.setScale(SCALE),
                    BigDecimal.ZERO.setScale(SCALE), outValue);
        }
        BigDecimal newAvg = average(newTotal, newQty);
        return new Result(newQty, newTotal, newAvg, outValue);
    }

    private static BigDecimal average(BigDecimal total, BigDecimal qty) {
        if (qty.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return total.divide(qty, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
        return value;
    }

    /**
     * The balance after a change, together with the value that moved.
     *
     * @param qty            quantity on hand afterwards
     * @param totalAmount    value on hand afterwards
     * @param avgCost        unit average afterwards
     * @param movementAmount value that entered or left with this movement
     */
    public record Result(BigDecimal qty,
                         BigDecimal totalAmount,
                         BigDecimal avgCost,
                         BigDecimal movementAmount) {
    }
}
