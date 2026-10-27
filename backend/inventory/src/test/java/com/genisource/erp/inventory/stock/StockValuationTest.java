package com.genisource.erp.inventory.stock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The moving weighted average rules.
 *
 * <p>These are the numbers an accountant will check by hand, so the expected values
 * are written out as plain arithmetic rather than as expected constants: "10 at 5.00,
 * then 10 at 7.00, so the average is 6.00" reads like the manual it replaces.
 */
class StockValuationTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    @DisplayName("the first receipt sets the average")
    void firstReceiptSetsTheAverage() {
        StockValuation.Result result =
                StockValuation.receive(ZERO, ZERO, new BigDecimal("10"), new BigDecimal("5.00"));

        assertEquals(0, new BigDecimal("10").compareTo(result.qty()));
        assertEquals(0, new BigDecimal("50.00").compareTo(result.totalAmount()));
        assertEquals(0, new BigDecimal("5.0000").compareTo(result.avgCost()));
        assertEquals(0, new BigDecimal("50.00").compareTo(result.movementAmount()));
    }

    @Test
    @DisplayName("a second receipt re-averages the whole balance")
    void secondReceiptReAverages() {
        // 10 @ 5.00 then 10 @ 7.00 -> 20 units worth 120.00 -> average 6.00
        StockValuation.Result result = StockValuation.receive(
                new BigDecimal("10"), new BigDecimal("50.00"),
                new BigDecimal("10"), new BigDecimal("7.00"));

        assertEquals(0, new BigDecimal("20").compareTo(result.qty()));
        assertEquals(0, new BigDecimal("120.00").compareTo(result.totalAmount()));
        assertEquals(0, new BigDecimal("6.0000").compareTo(result.avgCost()));
    }

    @Test
    @DisplayName("an issue is valued at the current average and leaves the rest intact")
    void issueUsesTheAverage() {
        // 20 units worth 120.00 (average 6.00); issue 5 -> 15 units worth 90.00
        StockValuation.Result result = StockValuation.issue(
                new BigDecimal("20"), new BigDecimal("120.00"), new BigDecimal("6.0000"),
                new BigDecimal("5"));

        assertEquals(0, new BigDecimal("15").compareTo(result.qty()));
        assertEquals(0, new BigDecimal("90.00").compareTo(result.totalAmount()));
        assertEquals(0, new BigDecimal("6.0000").compareTo(result.avgCost()));
        assertEquals(0, new BigDecimal("30.00").compareTo(result.movementAmount()));
    }

    @Test
    @DisplayName("emptying the balance clears the average so the next receipt starts fresh")
    void emptyBalanceResetsTheAverage() {
        StockValuation.Result result = StockValuation.issue(
                new BigDecimal("10"), new BigDecimal("50.00"), new BigDecimal("5.0000"),
                new BigDecimal("10"));

        assertEquals(0, ZERO.compareTo(result.qty()));
        assertEquals(0, ZERO.compareTo(result.totalAmount()));
        assertEquals(0, ZERO.compareTo(result.avgCost()));
    }

    @Test
    @DisplayName("the total accumulates exactly instead of drifting with the average")
    void totalDoesNotDrift() {
        // Ten receipts of 3 units at 1.11 each. Multiplying a rounded average by a
        // quantity would drift; adding to the total cannot.
        BigDecimal qty = ZERO;
        BigDecimal total = ZERO;
        for (int i = 0; i < 10; i++) {
            StockValuation.Result result =
                    StockValuation.receive(qty, total, new BigDecimal("3"), new BigDecimal("1.11"));
            qty = result.qty();
            total = result.totalAmount();
        }

        assertEquals(0, new BigDecimal("30").compareTo(qty));
        assertEquals(0, new BigDecimal("33.30").compareTo(total));
    }

    @Test
    @DisplayName("a zero or negative quantity is refused")
    void quantityMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> StockValuation.receive(ZERO, ZERO, ZERO, new BigDecimal("1")));
        assertThrows(IllegalArgumentException.class,
                () -> StockValuation.issue(new BigDecimal("1"), new BigDecimal("1"),
                        new BigDecimal("1"), new BigDecimal("-1")));
    }
}
