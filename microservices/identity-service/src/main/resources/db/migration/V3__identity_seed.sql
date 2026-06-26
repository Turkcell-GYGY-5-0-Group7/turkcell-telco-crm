-- Seed default roles and permissions (FR-IAM-04). Fixed UUIDs ensure idempotent re-runs.
-- ON CONFLICT DO NOTHING makes this safe to execute against an already-seeded database.

-- Permissions

INSERT INTO permissions (id, code) VALUES
    ('d62a65ed-3439-4b53-b86e-c6de20cf946e', 'USER_MANAGE'),
    ('e792093c-ff2c-474d-975c-f1ca3ff495fa', 'ROLE_MANAGE'),
    ('b8ef7dbe-607e-4db3-a087-5f497ced8b18', 'TARIFF_MANAGE'),
    ('a9c0e2ed-4ca0-47f2-bd9e-3c1dfe3a7d8d', 'SUBSCRIPTION_VIEW_ALL'),
    ('e2f80755-7d68-4f56-8f87-83724ca667cf', 'TICKET_MANAGE_ALL'),
    ('945ca5df-edf0-40df-bdf3-67de00373bc5', 'CUSTOMER_VIEW'),
    ('d49c0b5c-d8fb-4cd2-b613-34388e065290', 'TICKET_MANAGE'),
    ('60e34447-4087-453c-9858-bc5a10e3d2e3', 'SUBSCRIPTION_VIEW'),
    ('2b000bf6-5a77-4582-a2d2-15a50db9ea7f', 'PLAN_CHANGE'),
    ('acb485da-e6c6-4292-a706-ff17224d8c23', 'CUSTOMER_CREATE'),
    ('43eca81a-bb9c-40a8-95be-b0e42ccb32e6', 'KYC_SUBMIT'),
    ('0e21dfde-56fc-4bce-a37e-fe911734449b', 'SUBSCRIPTION_ACTIVATE'),
    ('fea0b98f-59fa-4cf9-b44e-2aed70a372f5', 'INVOICE_VIEW_ALL'),
    ('47803d74-a89b-4381-9a55-90c9e20a636d', 'INVOICE_CANCEL'),
    ('ef678d2e-a634-4e47-9120-46bb9d31695a', 'BILL_RUN_MANAGE'),
    ('f4c56523-9f8f-45e0-8eb8-26dff3a5dbd5', 'PROFILE_VIEW'),
    ('67515112-c2a7-4c9d-a695-ca15f6310e3d', 'INVOICE_VIEW_OWN'),
    ('1ee096b9-c801-4069-86df-faf3da0d93d5', 'TICKET_CREATE'),
    ('86dd926a-063d-4d5e-8277-c7214e18e7e0', 'PLAN_CHANGE_OWN')
ON CONFLICT DO NOTHING;

-- Roles

INSERT INTO roles (id, name) VALUES
    ('c5cf43f1-3cbb-4da4-bd36-43b788bde6ea', 'ADMIN'),
    ('849f2936-140e-4d71-bb79-cde3fe9ec677', 'AGENT'),
    ('6afe0205-c857-4416-88bc-e78609d966d9', 'DEALER'),
    ('a64eeb31-7e02-45a3-bcc5-41d62ee83284', 'BILLING_OPERATOR'),
    ('db571967-4b22-4451-b9c2-e136b8050000', 'CUSTOMER')
ON CONFLICT DO NOTHING;

-- ADMIN permissions: full platform administration

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('c5cf43f1-3cbb-4da4-bd36-43b788bde6ea', 'd62a65ed-3439-4b53-b86e-c6de20cf946e'),
    ('c5cf43f1-3cbb-4da4-bd36-43b788bde6ea', 'e792093c-ff2c-474d-975c-f1ca3ff495fa'),
    ('c5cf43f1-3cbb-4da4-bd36-43b788bde6ea', 'b8ef7dbe-607e-4db3-a087-5f497ced8b18'),
    ('c5cf43f1-3cbb-4da4-bd36-43b788bde6ea', 'a9c0e2ed-4ca0-47f2-bd9e-3c1dfe3a7d8d'),
    ('c5cf43f1-3cbb-4da4-bd36-43b788bde6ea', 'e2f80755-7d68-4f56-8f87-83724ca667cf')
ON CONFLICT DO NOTHING;

-- AGENT permissions: customer support and subscription visibility

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('849f2936-140e-4d71-bb79-cde3fe9ec677', '945ca5df-edf0-40df-bdf3-67de00373bc5'),
    ('849f2936-140e-4d71-bb79-cde3fe9ec677', 'd49c0b5c-d8fb-4cd2-b613-34388e065290'),
    ('849f2936-140e-4d71-bb79-cde3fe9ec677', '60e34447-4087-453c-9858-bc5a10e3d2e3'),
    ('849f2936-140e-4d71-bb79-cde3fe9ec677', '2b000bf6-5a77-4582-a2d2-15a50db9ea7f')
ON CONFLICT DO NOTHING;

-- DEALER permissions: customer onboarding and KYC

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('6afe0205-c857-4416-88bc-e78609d966d9', 'acb485da-e6c6-4292-a706-ff17224d8c23'),
    ('6afe0205-c857-4416-88bc-e78609d966d9', '43eca81a-bb9c-40a8-95be-b0e42ccb32e6'),
    ('6afe0205-c857-4416-88bc-e78609d966d9', '0e21dfde-56fc-4bce-a37e-fe911734449b')
ON CONFLICT DO NOTHING;

-- BILLING_OPERATOR permissions: invoice and billing management

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('a64eeb31-7e02-45a3-bcc5-41d62ee83284', 'fea0b98f-59fa-4cf9-b44e-2aed70a372f5'),
    ('a64eeb31-7e02-45a3-bcc5-41d62ee83284', '47803d74-a89b-4381-9a55-90c9e20a636d'),
    ('a64eeb31-7e02-45a3-bcc5-41d62ee83284', 'ef678d2e-a634-4e47-9120-46bb9d31695a')
ON CONFLICT DO NOTHING;

-- CUSTOMER permissions: self-service access to own data

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('db571967-4b22-4451-b9c2-e136b8050000', 'f4c56523-9f8f-45e0-8eb8-26dff3a5dbd5'),
    ('db571967-4b22-4451-b9c2-e136b8050000', '67515112-c2a7-4c9d-a695-ca15f6310e3d'),
    ('db571967-4b22-4451-b9c2-e136b8050000', '1ee096b9-c801-4069-86df-faf3da0d93d5'),
    ('db571967-4b22-4451-b9c2-e136b8050000', '86dd926a-063d-4d5e-8277-c7214e18e7e0')
ON CONFLICT DO NOTHING;
