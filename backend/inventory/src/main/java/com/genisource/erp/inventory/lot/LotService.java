package com.genisource.erp.inventory.lot;

import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.inventory.stock.StockService;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.Codes;
import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Batches and serial numbers.
 *
 * <p>The lot number is the link between a physical object and the ledger. Whether a
 * material is tracked by batch or by serial is decided on the item, not here: the item
 * knows whether two units with the same number are the same goods.
 */
@Service
@Transactional(readOnly = true)
public class LotService {

    private final LotRepository lots;
    private final ItemRepository items;
    private final StockService stock;
    private final AuditService audit;

    public LotService(LotRepository lots,
                      ItemRepository items,
                      StockService stock,
                      AuditService audit) {
        this.lots = lots;
        this.items = items;
        this.stock = stock;
        this.audit = audit;
    }

    public List<LotDtos.LotView> list(Long itemId) {
        List<Lot> source = itemId == null
                ? lots.findAll()
                : lots.findByItemIdOrderByLotNoAsc(itemId);
        return source.stream().map(this::view).toList();
    }

    public LotDtos.LotView get(Long id) {
        return view(require(id));
    }

    @Transactional
    public LotDtos.LotView create(LotDtos.LotRequest request) {
        Item item = requireItem(request.itemId());
        LotType lotType = resolveType(item, request.lotType());
        String lotNo = requireLotNo(request.lotNo());

        if (lots.existsByItemIdAndLotNo(item.getId(), lotNo)) {
            throw ApiException.duplicateCode(
                    "Lot " + lotNo + " already exists for item " + item.getCode());
        }
        Lot lot = new Lot(item.getId(), lotNo, lotType);
        lot.setProductionDate(request.productionDate());
        lot.setExpiryDate(request.expiryDate());
        lot.setRemark(blankToNull(request.remark()));
        if (request.status() != null) {
            lot.setStatus(request.status());
        }
        lots.save(lot);
        audit.record("inventory.lot.create", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_lot", String.valueOf(lot.getId()),
                Map.of("lotNo", lot.getLotNo(), "item", item.getCode()));
        return view(lot);
    }

    @Transactional
    public LotDtos.LotView changeStatus(Long id, RecordStatus target) {
        Lot lot = require(id);
        if (target == RecordStatus.INACTIVE && lot.getStatus().isActive()) {
            BigDecimal held = stock.quantityOnHandOfLot(lot.getId());
            if (held.signum() > 0) {
                throw ApiException.inUse("Lot " + lot.getLotNo()
                        + " still holds stock and cannot be deactivated");
            }
        }
        lot.setStatus(target);
        lots.save(lot);
        audit.record("inventory.lot.status", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_lot", String.valueOf(lot.getId()),
                Map.of("status", target.name()));
        return view(lot);
    }

    /**
     * Finds a lot by number, or creates it when the item is tracked.
     *
     * <p>Posting a receipt should not require the clerk to pre-create the batch; the
     * batch number on the delivery note is enough. But an item that is not tracked must
     * not get a lot, otherwise the stock key would silently gain a dimension.
     */
    @Transactional
    public Lot requireOrCreate(Item item, String rawLotNo, LotType fallbackType) {
        String lotNo = Codes.normalise(rawLotNo);
        if (lotNo == null) {
            if (item.isLotControlled() || item.isSerialControlled()) {
                throw ApiException.badRequest("Item " + item.getCode()
                        + " is tracked by " + (item.isSerialControlled() ? "serial" : "batch")
                        + " and needs a number");
            }
            return null;
        }
        if (!item.isLotControlled() && !item.isSerialControlled()) {
            throw ApiException.badRequest("Item " + item.getCode() + " is not tracked by batch"
                    + " or serial and cannot carry lot " + lotNo);
        }
        LotType lotType = item.isSerialControlled() ? LotType.SERIAL : LotType.LOT;
        return lots.findByItemIdAndLotNo(item.getId(), lotNo)
                .orElseGet(() -> {
                    Lot created = new Lot(item.getId(), lotNo, lotType);
                    lots.save(created);
                    return created;
                });
    }

    public Lot require(Long id) {
        return lots.findById(id)
                .orElseThrow(() -> ApiException.notFound("Lot " + id + " does not exist"));
    }

    private Item requireItem(Long itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> ApiException.badRequest("Item " + itemId + " does not exist"));
    }

    /**
     * The lot type has to agree with how the item is tracked: a serial-tracked item
     * cannot have a batch, and an untracked item cannot have either.
     */
    private LotType resolveType(Item item, LotType requested) {
        if (!item.isLotControlled() && !item.isSerialControlled()) {
            throw ApiException.badRequest("Item " + item.getCode()
                    + " is not tracked by batch or serial");
        }
        LotType expected = item.isSerialControlled() ? LotType.SERIAL : LotType.LOT;
        if (requested != null && requested != expected) {
            throw ApiException.badRequest("Item " + item.getCode() + " is tracked by "
                    + (expected == LotType.SERIAL ? "serial" : "batch")
                    + ", not by " + (requested == LotType.SERIAL ? "serial" : "batch"));
        }
        return expected;
    }

    private LotDtos.LotView view(Lot lot) {
        Item item = items.findById(lot.getItemId()).orElse(null);
        BigDecimal[] onHand = stock.lotBalance(lot.getId());
        return new LotDtos.LotView(
                lot.getId(),
                lot.getItemId(),
                item == null ? null : item.getCode(),
                item == null ? null : item.getName(),
                lot.getLotNo(),
                lot.getLotType(),
                lot.getProductionDate(),
                lot.getExpiryDate(),
                lot.getRemark(),
                lot.getStatus(),
                onHand[0],
                onHand[1],
                lot.getRowVersion());
    }

    private String requireLotNo(String raw) {
        String lotNo = Codes.normalise(raw);
        if (lotNo == null) {
            throw ApiException.badRequest("Lot number is required");
        }
        if (lotNo.length() > 64) {
            throw ApiException.badRequest("Lot number must be at most 64 characters");
        }
        return lotNo;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
