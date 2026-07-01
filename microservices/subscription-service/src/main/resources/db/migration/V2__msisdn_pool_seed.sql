-- Seed the MSISDN pool with a range of FREE numbers for onboarding allocation (FR-13, task 9.1.3).
-- A fresh database must have FREE MSISDNs available; allocation flips a row FREE -> RESERVED ->
-- ALLOCATED and reduces the FREE count. ON CONFLICT DO NOTHING keeps re-runs idempotent, mirroring the
-- identity-service seed convention (V3__identity_seed.sql).
--
-- Range: Turkish mobile numbers 905320000000..905320000999 (1000 numbers, 12 digits each). The 0532
-- prefix is a standard Turkcell GSM block; the trailing seven digits give a contiguous allocatable
-- block large enough for dev/test onboarding flows.

INSERT INTO msisdn_pool (msisdn, status)
SELECT '90532' || LPAD(seq::text, 7, '0'), 'FREE'
FROM generate_series(0, 999) AS seq
ON CONFLICT (msisdn) DO NOTHING;
