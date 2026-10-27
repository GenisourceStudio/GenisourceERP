/**
 * Number formatting for quantities and money.
 *
 * <p>Both use the browser locale rather than a hard-coded pattern: a plant in Germany
 * and one in China read the same 1,234.5 differently, and the ERP has no business
 * deciding which is right. Fractions are capped at the precision the database stores,
 * so a quantity never renders as 0.30000000000000004.
 */

export function formatQty(value: number): string {
  return Number(value ?? 0).toLocaleString(undefined, { maximumFractionDigits: 6 })
}

export function formatMoney(value: number): string {
  return Number(value ?? 0).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  })
}
