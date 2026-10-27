-- ============================================================================
-- V5 — where a stock document came from
--
-- Owned by the inventory module. M2 could not know that a later milestone would
-- post stock documents on behalf of somebody else: a purchase receipt arriving
-- from a supplier, a sales shipment leaving for a customer. When that happens
-- the ledger needs to answer "which business document caused this movement?"
-- without the reader guessing from a remark.
--
-- Editing V4 to add these columns would break every deployment that has already
-- applied it, so the change is a migration of its own. That is the rule: a
-- committed migration is immutable, however early the project is.
-- ============================================================================

ALTER TABLE inv_document
    ADD COLUMN source_type VARCHAR(32);
ALTER TABLE inv_document
    ADD COLUMN source_id VARCHAR(64);

CREATE INDEX ix_inv_document_source ON inv_document (tenant_id, source_type, source_id);

COMMENT ON COLUMN inv_document.source_type IS
    'What kind of business document created this one: null for a document typed in by hand, PUR_RECEIPT for goods arriving against a purchase order, SAL_SHIPMENT for goods leaving against a sales order.';
COMMENT ON COLUMN inv_document.source_id IS
    'The document number of the source, for example SL-20261028-0001.';
