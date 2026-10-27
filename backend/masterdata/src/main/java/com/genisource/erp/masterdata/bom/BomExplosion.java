package com.genisource.erp.masterdata.bom;

import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.masterdata.shared.RevisionStatus;
import com.genisource.erp.platform.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Multi-level BOM explosion.
 *
 * <p>Kept out of {@link BomService} on purpose: MRP (M4) needs exactly this calculation —
 * "produce N of X, what does the whole tree require" — and duplicating it there is how
 * planning and the console end up disagreeing.
 *
 * <p>Only {@code RELEASED} revisions are followed, because a draft is not a commitment.
 * Quantity arithmetic uses 6-decimal {@link BigDecimal} with half-up rounding, matching
 * the column scale in the schema.
 */
@Service
@Transactional(readOnly = true)
public class BomExplosion {

    private static final int SCALE = 6;

    private final BomRepository boms;
    private final ItemRepository items;

    public BomExplosion(BomRepository boms, ItemRepository items) {
        this.boms = boms;
        this.items = items;
    }

    /**
     * @param requiredQty how many units of the BOM's parent item are to be produced
     * @param maxLevels   hard stop; protects against pathological data and runaway payloads
     */
    public BomDtos.BomTreeNode explode(Long bomId, BigDecimal requiredQty, int maxLevels) {
        Bom bom = boms.findWithLinesById(bomId)
                .orElseThrow(() -> ApiException.notFound("BOM " + bomId + " does not exist"));
        Item parent = items.findById(bom.getItemId())
                .orElseThrow(() -> ApiException.notFound("Item " + bom.getItemId() + " does not exist"));

        BigDecimal qty = requiredQty == null ? BigDecimal.ONE : requiredQty;
        int depth = Math.min(Math.max(maxLevels, 1), 20);

        Map<Long, Item> cache = new HashMap<>();
        cache.put(parent.getId(), parent);
        Set<Long> path = new HashSet<>();
        path.add(parent.getId());

        List<BomDtos.BomTreeNode> children =
                expandLines(bom, qty, 1, depth, path, cache);

        return new BomDtos.BomTreeNode(
                parent.getId(),
                parent.getCode(),
                parent.getName(),
                parent.getSpec(),
                parent.getUomCode(),
                qty,
                qty,
                BigDecimal.ZERO,
                false,
                0,
                bom.getVersion(),
                "BOM",
                children);
    }

    private List<BomDtos.BomTreeNode> expandLines(Bom bom,
                                                  BigDecimal parentQty,
                                                  int level,
                                                  int maxLevels,
                                                  Set<Long> path,
                                                  Map<Long, Item> cache) {
        if (level > maxLevels || bom.getLines().isEmpty()) {
            return List.of();
        }
        BigDecimal base = bom.getBaseQty() == null || bom.getBaseQty().signum() == 0
                ? BigDecimal.ONE
                : bom.getBaseQty();

        List<BomDtos.BomTreeNode> nodes = new ArrayList<>();
        for (BomLine line : bom.getLines()) {
            Item component = component(line.getComponentItemId(), cache);
            BigDecimal perParent = line.getQty()
                    .multiply(BigDecimal.ONE.add(orZero(line.getLossRate()).movePointLeft(2)))
                    .divide(base, SCALE, RoundingMode.HALF_UP);
            BigDecimal total = parentQty.multiply(perParent).setScale(SCALE, RoundingMode.HALF_UP);

            boolean cycle = path.contains(component.getId());
            List<BomDtos.BomTreeNode> grandchildren = List.of();
            String source = "ITEM";
            if (!cycle) {
                Optional<Bom> childBom = releasedBomOf(component.getId());
                if (childBom.isPresent()) {
                    source = "BOM";
                    path.add(component.getId());
                    grandchildren = expandLines(childBom.get(), total, level + 1, maxLevels, path, cache);
                    path.remove(component.getId());
                }
            }

            nodes.add(new BomDtos.BomTreeNode(
                    component.getId(),
                    component.getCode(),
                    component.getName(),
                    component.getSpec(),
                    component.getUomCode(),
                    total,
                    perParent,
                    orZero(line.getLossRate()),
                    line.isPhantom(),
                    level,
                    null,
                    cycle ? "CYCLE" : source,
                    grandchildren));
        }
        return nodes;
    }

    private Optional<Bom> releasedBomOf(Long itemId) {
        return boms.findFirstByItemIdAndStatusOrderByVersionDesc(itemId, RevisionStatus.RELEASED);
    }

    private Item component(Long itemId, Map<Long, Item> cache) {
        Item cached = cache.get(itemId);
        if (cached != null) {
            return cached;
        }
        Item loaded = items.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("Component item " + itemId + " does not exist"));
        cache.put(itemId, loaded);
        return loaded;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Item ids mentioned by a BOM, used by callers that want to prefetch names. */
    public Set<Long> componentIds(Bom bom) {
        Set<Long> ids = new LinkedHashSet<>();
        bom.getLines().forEach(line -> ids.add(line.getComponentItemId()));
        return ids;
    }
}
