package com.genisource.erp.platform.common;

/**
 * A module that keeps an item in use can implement this to veto deactivation.
 *
 * <p>Master data must not depend on inventory, and inventory must not depend on master
 * data's internals, yet "a material with stock on hand cannot be disabled" is a rule
 * both sides care about. The interface lives in the platform so the dependency points
 * one way: the module that owns the constraint implements it, master data calls it.
 *
 * <p>Master data injects every implementation it finds. When no module is deployed
 * there is nothing to check, so the item is simply deactivated — which is the correct
 * behaviour for a build without stock at all.
 */
public interface ItemUsageGuard {

    /**
     * Throws when the item still carries usage that must survive deactivation.
     *
     * @param itemId the item about to be deactivated
     */
    void assertCanDeactivate(Long itemId);
}
