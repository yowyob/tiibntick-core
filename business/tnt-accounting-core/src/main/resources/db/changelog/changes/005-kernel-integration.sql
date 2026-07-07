-- liquibase formatted sql
-- Author: MANFOUO Braun
-- changeset manfouo-braun:005-kernel-integration
-- comment: Add optional logical references to the Yowyob Kernel (RT-comops-accounting-core)
ALTER TABLE accounting.journal_entries
    ADD COLUMN IF NOT EXISTS kernel_invoice_id UUID;

ALTER TABLE accounting.journal_entries
    ADD COLUMN IF NOT EXISTS kernel_journal_entry_id UUID;

-- Partial index: only index rows that actually have Kernel links
CREATE INDEX IF NOT EXISTS idx_je_kernel_invoice_id
    ON accounting.journal_entries (kernel_invoice_id)
    WHERE kernel_invoice_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_je_kernel_journal_entry_id
    ON accounting.journal_entries (kernel_journal_entry_id)
    WHERE kernel_journal_entry_id IS NOT NULL;

COMMENT ON COLUMN accounting.journal_entries.kernel_invoice_id
    IS 'Optional logical reference to yow_kernel_db RT-comops-accounting-core invoice UUID. '
       'Set when the journal entry is auto-generated from a billing invoice event. '
       'No physical foreign key — cross-database logical reference only.';

COMMENT ON COLUMN accounting.journal_entries.kernel_journal_entry_id
    IS 'Optional logical reference to the counterpart RT-comops-accounting-core journal entry UUID. '
       'Set after successful sync to the Kernel ERP. '
       'No physical foreign key — cross-database logical reference only.';

-- rollback ALTER TABLE accounting.journal_entries DROP COLUMN IF EXISTS kernel_invoice_id;
-- rollback ALTER TABLE accounting.journal_entries DROP COLUMN IF EXISTS kernel_journal_entry_id;
