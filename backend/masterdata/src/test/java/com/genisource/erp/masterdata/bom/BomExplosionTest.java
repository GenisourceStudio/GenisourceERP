package com.genisource.erp.masterdata.bom;

import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.masterdata.item.ItemType;
import com.genisource.erp.masterdata.shared.RevisionStatus;
import com.genisource.erp.platform.common.BaseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Quantity arithmetic of a multi-level explosion.
 *
 * <p>This is the calculation MRP depends on, so it is tested against hand-computed numbers
 * rather than against whatever the implementation happens to output: a silent factor error
 * here would surface months later as a warehouse full of the wrong component.
 */
class BomExplosionTest {

    private BomRepository boms;
    private ItemRepository items;
    private BomExplosion explosion;

    private Item finishedGood;
    private Item semiFinished;
    private Item rawMaterial;

    @BeforeEach
    void setUp() {
        boms = mock(BomRepository.class);
        items = mock(ItemRepository.class);
        explosion = new BomExplosion(boms, items);

        finishedGood = withId(new Item("FG-100", "电控柜", ItemType.FINISHED, "PCS"), 1L);
        semiFinished = withId(new Item("SEMI-200", "柜体总成", ItemType.SEMI, "PCS"), 2L);
        rawMaterial = withId(new Item("RAW-300", "冷轧钢板", ItemType.RAW, "KG"), 3L);

        when(items.findById(1L)).thenReturn(Optional.of(finishedGood));
        when(items.findById(2L)).thenReturn(Optional.of(semiFinished));
        when(items.findById(3L)).thenReturn(Optional.of(rawMaterial));
    }

    @Test
    @DisplayName("quantities multiply through levels and a parent loss rate inflates the child demand")
    void multipliesThroughLevels() {
        // 1 cabinet needs 2 body assemblies; 1 body assembly needs 3 kg of steel + 10 % scrap.
        Bom semiBom = releasedBom(2L, line(10, 3L, "3", "10"));
        Bom rootBom = releasedBom(1L, line(10, 2L, "2", "0"));
        when(boms.findWithLinesById(100L)).thenReturn(Optional.of(rootBom));
        when(boms.findFirstByItemIdAndStatusOrderByVersionDesc(eq(2L), any()))
                .thenReturn(Optional.of(semiBom));
        when(boms.findFirstByItemIdAndStatusOrderByVersionDesc(eq(3L), any()))
                .thenReturn(Optional.empty());

        BomDtos.BomTreeNode tree = explosion.explode(100L, new BigDecimal("10"), 10);

        assertThat(tree.qty()).isEqualByComparingTo("10");
        assertThat(tree.children()).hasSize(1);

        BomDtos.BomTreeNode semi = tree.children().get(0);
        assertThat(semi.itemCode()).isEqualTo("SEMI-200");
        assertThat(semi.perParentQty()).isEqualByComparingTo("2");
        assertThat(semi.qty()).isEqualByComparingTo("20");
        assertThat(semi.level()).isEqualTo(1);

        assertThat(semi.children()).hasSize(1);
        BomDtos.BomTreeNode raw = semi.children().get(0);
        assertThat(raw.itemCode()).isEqualTo("RAW-300");
        // 3 kg per assembly inflated by 10 % scrap = 3.3; times 20 assemblies = 66 kg.
        assertThat(raw.perParentQty()).isEqualByComparingTo("3.3");
        assertThat(raw.qty()).isEqualByComparingTo("66");
        assertThat(raw.level()).isEqualTo(2);
        assertThat(raw.children()).isEmpty();
    }

    @Test
    @DisplayName("a base quantity scales the component factor instead of the line numbers")
    void respectsBaseQuantity() {
        // The BOM describes 100 units and needs 250 kg; one unit therefore needs 2.5 kg.
        Bom rootBom = new Bom(1L, "V1", new BigDecimal("100"));
        rootBom.replaceLines(List.of(bomLine(10, 3L, "250", "0")));
        when(boms.findWithLinesById(200L)).thenReturn(Optional.of(rootBom));
        when(boms.findFirstByItemIdAndStatusOrderByVersionDesc(eq(3L), any()))
                .thenReturn(Optional.empty());

        BomDtos.BomTreeNode tree = explosion.explode(200L, BigDecimal.ONE, 10);

        assertThat(tree.children().get(0).perParentQty()).isEqualByComparingTo("2.5");
        assertThat(tree.children().get(0).qty()).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("a circular reference is reported instead of recursing forever")
    void stopsAtACycle() {
        Bom aBom = releasedBom(1L, line(10, 2L, "1", "0"));
        Bom bBom = releasedBom(2L, line(10, 1L, "1", "0"));
        when(boms.findWithLinesById(300L)).thenReturn(Optional.of(aBom));
        when(boms.findFirstByItemIdAndStatusOrderByVersionDesc(eq(2L), any()))
                .thenReturn(Optional.of(bBom));

        BomDtos.BomTreeNode tree = explosion.explode(300L, BigDecimal.ONE, 10);

        BomDtos.BomTreeNode backToRoot = tree.children().get(0).children().get(0);
        assertThat(backToRoot.source()).isEqualTo("CYCLE");
        assertThat(backToRoot.children()).isEmpty();
    }

    @Test
    @DisplayName("the level limit truncates the tree even when data is deeper")
    void stopsAtMaxLevels() {
        Bom semiBom = releasedBom(2L, line(10, 3L, "1", "0"));
        Bom rootBom = releasedBom(1L, line(10, 2L, "1", "0"));
        when(boms.findWithLinesById(400L)).thenReturn(Optional.of(rootBom));
        when(boms.findFirstByItemIdAndStatusOrderByVersionDesc(eq(2L), any()))
                .thenReturn(Optional.of(semiBom));

        BomDtos.BomTreeNode tree = explosion.explode(400L, BigDecimal.ONE, 1);

        assertThat(tree.children()).hasSize(1);
        assertThat(tree.children().get(0).children()).isEmpty();
    }

    // ------------------------------------------------------------- helpers

    private static Bom releasedBom(Long itemId, BomLine... lines) {
        Bom bom = new Bom(itemId, "V1", BigDecimal.ONE);
        bom.replaceLines(List.of(lines));
        bom.release();
        return bom;
    }

    private static BomLine line(int lineNo, Long componentId, String qty, String lossRate) {
        return bomLine(lineNo, componentId, qty, lossRate);
    }

    private static BomLine bomLine(int lineNo, Long componentId, String qty, String lossRate) {
        BomLine line = new BomLine(lineNo, componentId, new BigDecimal(qty));
        line.setLossRate(new BigDecimal(lossRate));
        line.tidy();
        return line;
    }

    /** Entities carry a generated id, so tests seed it directly instead of persisting. */
    private static <T extends BaseEntity> T withId(T entity, long id) {
        try {
            Field field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to seed id for the test", ex);
        }
        return entity;
    }
}
