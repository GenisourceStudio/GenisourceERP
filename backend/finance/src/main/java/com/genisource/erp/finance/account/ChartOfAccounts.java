package com.genisource.erp.finance.account;

import java.util.List;

/**
 * The chart a factory starts with.
 *
 * <p>Small-business codes from the Chinese standard chart, because that is what the
 * accountant a 200-person factory actually employs will recognise. Nothing here is
 * mandatory: every account can be renamed, re-pointed or deactivated, and new ones can
 * be added. The seed exists so that the first goods receipt can post without anyone
 * having to design a chart of accounts first.
 *
 * <p>Only the roles an automatic posting uses carry a {@link AccountRole}. A role is a
 * promise that some code path will look it up, and promising something nobody uses
 * leaves an administrator with a switch that does nothing.
 */
public final class ChartOfAccounts {

    /** One seeded account: its code, label, type, optional parent, and the role it plays. */
    public record Seed(String code, String name, AccountType type, String parentCode, AccountRole role) {
    }

    public static final List<Seed> SEEDS = List.of(
            new Seed("1001", "库存现金", AccountType.ASSET, null, AccountRole.CASH),
            new Seed("1002", "银行存款", AccountType.ASSET, null, AccountRole.BANK),
            new Seed("1122", "应收账款", AccountType.ASSET, null, AccountRole.AR),
            new Seed("1403", "原材料", AccountType.ASSET, null, AccountRole.INVENTORY_RAW),
            new Seed("1405", "库存商品", AccountType.ASSET, null, AccountRole.INVENTORY_FINISHED),
            new Seed("2202", "应付账款", AccountType.LIABILITY, null, AccountRole.AP),
            // A heading, not a bucket: nobody posts to 应交税费, they post to one of its two
            // sub-accounts. Its children differ in direction, which is why direction is
            // stored per account instead of being inferred from the type.
            new Seed("2221", "应交税费", AccountType.LIABILITY, null, null),
            new Seed("222101", "应交增值税—进项税额", AccountType.ASSET, "2221", AccountRole.TAX_INPUT),
            new Seed("222102", "应交增值税—销项税额", AccountType.LIABILITY, "2221", AccountRole.TAX_OUTPUT),
            new Seed("4001", "实收资本", AccountType.EQUITY, null, null),
            new Seed("4103", "本年利润", AccountType.EQUITY, null, AccountRole.PROFIT_CURRENT_YEAR),
            // 生产成本 is work in process, and work in process is an asset. It is left
            // out of the income statement on purpose: its movements are value moving
            // between inventory accounts, not profit.
            new Seed("5001", "生产成本（在产品）", AccountType.ASSET, null, AccountRole.WIP),
            new Seed("6001", "主营业务收入", AccountType.REVENUE, null, AccountRole.REVENUE),
            // 主营业务成本 is a cost, not an expense: the difference is that a cost is
            // matched against the revenue it produced, which is what the income
            // statement's cost line is for.
            new Seed("6401", "主营业务成本", AccountType.COST, null, AccountRole.COST_OF_SALES),
            new Seed("6601", "销售费用", AccountType.EXPENSE, null, null),
            new Seed("6602", "管理费用", AccountType.EXPENSE, null, null),
            new Seed("6603", "财务费用", AccountType.EXPENSE, null, null));

    private ChartOfAccounts() {
    }
}
