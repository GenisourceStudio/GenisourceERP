package com.genisource.erp.masterdata.item;

import com.genisource.erp.masterdata.category.ItemCategoryService;
import com.genisource.erp.masterdata.shared.RecordStatus;
import com.genisource.erp.masterdata.shared.StatusRequest;
import com.genisource.erp.masterdata.uom.Uom;
import com.genisource.erp.masterdata.uom.UomRepository;
import com.genisource.erp.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Item master, item categories and the unit dictionary.
 *
 * <p>Grouped in one controller because they are one screen in the console: the item form
 * cannot be filled without categories and units, and splitting them across three
 * controllers would only add three files that always change together.
 */
@RestController
@RequestMapping("/api/masterdata")
@Tag(name = "Master data :: items", description = "Items, item categories and units of measure")
public class ItemController {

    private final ItemService items;
    private final ItemCategoryService categories;
    private final UomRepository uoms;

    public ItemController(ItemService items, ItemCategoryService categories, UomRepository uoms) {
        this.items = items;
        this.categories = categories;
        this.uoms = uoms;
    }

    // ----------------------------------------------------------------- items

    @GetMapping("/items")
    @PreAuthorize("hasAuthority('masterdata:item:read')")
    @Operation(summary = "Search items with paging")
    public PageResponse<ItemDtos.ItemView> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ItemType itemType,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return items.search(q, itemType, status, categoryId, page, size);
    }

    @GetMapping("/items/options")
    @PreAuthorize("hasAuthority('masterdata:item:read')")
    @Operation(summary = "Picker source for comboboxes; requires a search term")
    public List<ItemDtos.ItemOption> options(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "20") int limit) {
        return items.options(q, limit);
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasAuthority('masterdata:item:read')")
    public ItemDtos.ItemView get(@PathVariable Long id) {
        return items.get(id);
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('masterdata:item:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an item; the code is upper-cased and must be unique")
    public ItemDtos.ItemView create(@Valid @RequestBody ItemDtos.ItemRequest request) {
        return items.create(request);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAuthority('masterdata:item:write')")
    public ItemDtos.ItemView update(@PathVariable Long id,
                                    @Valid @RequestBody ItemDtos.ItemRequest request) {
        return items.update(id, request);
    }

    @PostMapping("/items/{id}/status")
    @PreAuthorize("hasAuthority('masterdata:item:write')")
    @Operation(summary = "Activate or deactivate; deactivation is refused while the item is a component of a released BOM")
    public ItemDtos.ItemView changeStatus(@PathVariable Long id,
                                          @Valid @RequestBody StatusRequest request) {
        return items.changeStatus(id, request.status());
    }

    // ------------------------------------------------------------ categories

    @GetMapping("/item-categories")
    @PreAuthorize("hasAuthority('masterdata:item:read')")
    public List<ItemDtos.CategoryView> listCategories() {
        return categories.list();
    }

    @PostMapping("/item-categories")
    @PreAuthorize("hasAuthority('masterdata:item:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDtos.CategoryView createCategory(@Valid @RequestBody ItemDtos.CategoryRequest request) {
        return categories.create(request);
    }

    @PutMapping("/item-categories/{id}")
    @PreAuthorize("hasAuthority('masterdata:item:write')")
    public ItemDtos.CategoryView updateCategory(@PathVariable Long id,
                                                @Valid @RequestBody ItemDtos.CategoryRequest request) {
        return categories.update(id, request);
    }

    @DeleteMapping("/item-categories/{id}")
    @PreAuthorize("hasAuthority('masterdata:item:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a category; refused while it still groups items or children")
    public void deactivateCategory(@PathVariable Long id) {
        categories.deactivate(id);
    }

    // ----------------------------------------------------------------- units

    @GetMapping("/uoms")
    @PreAuthorize("hasAuthority('masterdata:item:read')")
    @Operation(summary = "Unit dictionary; read-only in M1")
    public List<ItemDtos.UomView> listUoms() {
        return uoms.findAllByOrderByCodeAsc().stream()
                .map(this::toView)
                .toList();
    }

    private ItemDtos.UomView toView(Uom uom) {
        String label = uom.getCode() + " · " + uom.getName();
        return new ItemDtos.UomView(uom.getCode(), uom.getName(), uom.getNameEn(), uom.getDecimals(), label);
    }
}
