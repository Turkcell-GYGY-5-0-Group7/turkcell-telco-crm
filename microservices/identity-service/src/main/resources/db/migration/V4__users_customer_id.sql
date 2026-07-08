-- Adds the identity-to-customer linkage projection field (Section 14.1.1 ruling: Identity-to-Customer
-- Linkage Gap). identity-service owns this projection; population happens later via the
-- customer.registered.v1 inbox consumer, not by this migration.
--
-- Nullable: agent/dealer-assisted registrations never resolve a customer_id and stay unlinked
-- (out of scope per the ruling until a future "claim my account" flow). Unique: a customer must
-- never resolve to more than one user, but multiple NULLs are allowed under PostgreSQL's unique
-- constraint semantics (NULL is never considered equal to another NULL), so any number of
-- not-yet-linked users can coexist.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS customer_id UUID UNIQUE;
