-- Adds correlation_id to audit_log so each row can be traced back to the originating request (NFR-12).
ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255);
