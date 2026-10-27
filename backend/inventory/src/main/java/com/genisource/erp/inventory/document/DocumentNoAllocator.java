package com.genisource.erp.inventory.document;

import com.genisource.erp.platform.common.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Hands out document numbers.
 *
 * <p>A number looks like {@code RK-20261027-0001}: the type prefix, the business date
 * and a counter that restarts every day. Factories talk about "the third receipt of
 * the day", so a running global counter would not answer the question.
 *
 * <p>The counter is allocated with a single atomic statement. Reading the current
 * value and writing it back would hand out the same number twice when two clerks save
 * at the same moment; the upsert locks the counter row until the statement finishes,
 * so the numbers come out gapless and unique.
 */
@Component
public class DocumentNoAllocator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * @param tenantId the tenant the document belongs to
     * @param type     the document type, whose prefix opens the number
     * @param bizDate  the business date the counter restarts on
     */
    public String next(Long tenantId, DocumentType type, LocalDate bizDate) {
        Integer number;
        try {
            number = (Integer) entityManager.createNativeQuery("""
                            insert into inv_doc_counter (tenant_id, prefix, biz_date, next_no)
                            values (?1, ?2, ?3, 1)
                            on conflict (tenant_id, prefix, biz_date)
                            do update set next_no = inv_doc_counter.next_no + 1
                            returning next_no
                            """)
                    .setParameter(1, tenantId)
                    .setParameter(2, type.prefix())
                    .setParameter(3, bizDate)
                    .getSingleResult();
        } catch (RuntimeException ex) {
            throw ApiException.internal("Could not allocate a document number", ex);
        }
        if (number == null) {
            throw ApiException.internal("Could not allocate a document number", null);
        }
        return type.prefix() + "-" + DATE.format(bizDate) + "-" + String.format("%04d", number);
    }
}
