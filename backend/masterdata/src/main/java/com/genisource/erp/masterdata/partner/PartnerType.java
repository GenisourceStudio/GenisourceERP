package com.genisource.erp.masterdata.partner;

/**
 * A company we trade with.
 *
 * <p>One table, one type flag, because in practice the same factory is often both a
 * customer and a supplier (they buy our resin, we buy their moulds). Two tables would
 * duplicate the legal entity and split its credit history.
 */
public enum PartnerType {
    CUSTOMER,
    SUPPLIER,
    BOTH;

    public boolean tradesAsCustomer() {
        return this == CUSTOMER || this == BOTH;
    }

    public boolean tradesAsSupplier() {
        return this == SUPPLIER || this == BOTH;
    }
}
