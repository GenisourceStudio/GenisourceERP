package com.genisource.erp.masterdata.category;

import com.genisource.erp.masterdata.item.ItemDtos;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.platform.common.Codes;
import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Item category use cases.
 *
 * <p>Categories form a tree with no depth limit, so the two things that must hold are
 * that a category never becomes its own ancestor, and that a category still used by items
 * or by child categories is not deactivated.
 */
@Service
@Transactional(readOnly = true)
public class ItemCategoryService {

    private final ItemCategoryRepository categories;
    private final ItemRepository items;
    private final AuditService audit;

    public ItemCategoryService(ItemCategoryRepository categories,
                               ItemRepository items,
                               AuditService audit) {
        this.categories = categories;
        this.items = items;
        this.audit = audit;
    }

    public List<ItemDtos.CategoryView> list() {
        List<ItemCategory> all = categories.findAllByOrderBySortOrderAscCodeAsc();
        Map<Long, String> names = all.stream()
                .collect(Collectors.toMap(ItemCategory::getId, ItemCategory::getName, (a, b) -> a));
        Map<Long, Long> itemCounts = items.countGroupedByCategory().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return all.stream()
                .map(category -> toView(category, names.get(category.getParentId()),
                        itemCounts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Transactional
    public ItemDtos.CategoryView create(ItemDtos.CategoryRequest request) {
        String code = requireCode(request.code());
        if (categories.existsByCode(code)) {
            throw ApiException.duplicateCode("Category code " + code + " already exists");
        }
        ItemCategory category = new ItemCategory(code, request.name().trim());
        apply(category, request);
        categories.save(category);
        audit.record("item-category.create", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_item_category", String.valueOf(category.getId()), Map.of("code", category.getCode()));
        return toView(category, parentName(category.getParentId()), 0L);
    }

    @Transactional
    public ItemDtos.CategoryView update(Long id, ItemDtos.CategoryRequest request) {
        ItemCategory category = require(id);
        String code = requireCode(request.code());
        if (!code.equals(category.getCode()) && categories.existsByCode(code)) {
            throw ApiException.duplicateCode("Category code " + code + " already exists");
        }
        category.setCode(code);
        category.setName(request.name().trim());
        apply(category, request);
        categories.save(category);
        audit.record("item-category.update", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_item_category", String.valueOf(category.getId()), Map.of("code", category.getCode()));
        long itemCount = items.countByCategoryId(id);
        return toView(category, parentName(category.getParentId()), itemCount);
    }

    @Transactional
    public void deactivate(Long id) {
        ItemCategory category = require(id);
        if (items.existsByCategoryId(id)) {
            throw ApiException.inUse(
                    "Category " + category.getCode() + " still groups items and cannot be deactivated");
        }
        boolean hasChildren = categories.findAll().stream().anyMatch(candidate -> id.equals(candidate.getParentId()));
        if (hasChildren) {
            throw ApiException.inUse("Category " + category.getCode() + " still has child categories");
        }
        category.setStatus(RecordStatus.INACTIVE);
        categories.save(category);
        audit.record("item-category.deactivate", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_item_category", String.valueOf(category.getId()), Map.of("code", category.getCode()));
    }

    // ------------------------------------------------------------- internals

    private ItemCategory require(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("Item category " + id + " does not exist"));
    }

    private String requireCode(String raw) {
        String code = Codes.normalise(raw);
        if (code == null) {
            throw ApiException.badRequest("Category code is required");
        }
        return code;
    }

    private void apply(ItemCategory category, ItemDtos.CategoryRequest request) {
        Long parentId = request.parentId();
        if (parentId != null) {
            if (parentId.equals(category.getId())) {
                throw ApiException.badRequest("A category cannot be its own parent");
            }
            require(parentId);
            ensureNoCycle(category.getId(), parentId);
        }
        category.setParentId(parentId);
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        if (request.status() != null) {
            category.setStatus(request.status());
        }
    }

    /** Walks up from the requested parent; meeting {@code categoryId} means a cycle. */
    private void ensureNoCycle(Long categoryId, Long parentId) {
        if (categoryId == null) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && seen.add(cursor)) {
            if (cursor.equals(categoryId)) {
                throw ApiException.invalidState("Category hierarchy would contain a cycle");
            }
            cursor = categories.findById(cursor).map(ItemCategory::getParentId).orElse(null);
        }
    }

    private String parentName(Long parentId) {
        return parentId == null ? null
                : categories.findById(parentId).map(ItemCategory::getName).orElse(null);
    }

    private ItemDtos.CategoryView toView(ItemCategory category, String parentName, long itemCount) {
        return new ItemDtos.CategoryView(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getParentId(),
                parentName,
                category.getSortOrder(),
                category.getStatus(),
                itemCount);
    }
}
