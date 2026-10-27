package com.genisource.erp.platform.numbering;

import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.security.CurrentUser;
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
 * so the numbers come out unique and without gaps.
 *
 * <p>Every module numbers its documents through this one allocator, against one
 * counter table. A counter per module would be the same eight lines repeated, and
 * the place where a subtly wrong copy quietly hands out duplicate numbers.
 */
@Component
public class DocumentNoAllocator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Allocates the next number for the current tenant.
     *
     * @param prefix  short type marker that opens the number, e.g. {@code RK}
     * @param bizDate the business date the counter restarts on
     */
    public String next(String prefix, LocalDate bizDate) {
        return next(CurrentUser.tenantIdOrContext(), prefix, bizDate);
    }

    /**
     * Allocates the next number for an explicit tenant.
     *
     * @param tenantId tenant the document belongs to
     * @param prefix   short type marker that opens the number
     * @param bizDate  the business date the counter restarts on
     */
    public String next(Long tenantId, String prefix, LocalDate bizDate) {
        Integer number;
        try {
            number = (Integer) entityManager.createNativeQuery("""
                            insert into sys_doc_counter (tenant_id, prefix, biz_date, next_no)
                            values (?1, ?2, ?3, 1)
                            on conflict (tenant_id, prefix, biz_date)
                            do update set next_no = sys_doc_counter.next_no + 1
                            returning next_no
                            """)
                    .setParameter(1, tenantId)
                    .setParameter(2, prefix)
                    .setParameter(3, bizDate)
                    .getSingleResult();
        } catch (RuntimeException ex) {
            throw ApiException.internal("Could not allocate a document number", ex);
        }
        if (number == null) {
            throw ApiException.internal("Could not allocate a document number", null);
        }
        return prefix + "-" + DATE.format(bizDate) + "-" + String.format("%04d", number);
    }
}
