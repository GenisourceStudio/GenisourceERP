package com.genisource.erp.masterdata.bom;

import java.math.BigDecimal;

/**
 * One direct component and how much of it a given quantity of its parent consumes.
 *
 * <p>The result of a single-level explosion. MRP wants the level on its own rather than
 * the finished tree, because it nets each level against stock before deciding whether
 * there is any point asking what that item is made of — exploding four levels deep and
 * then discarding the answers is how a planner ends up ordering components for a
 * sub-assembly nobody is going to build.
 *
 * @param itemId  the component
 * @param qty     quantity for the requested parent quantity, base quantity and loss applied
 * @param phantom whether the parent's line marks this component as a pass-through, in which
 *                case it is never stocked or demanded: its own components are demanded instead
 */
public record ComponentRequirement(Long itemId, BigDecimal qty, boolean phantom) {
}
