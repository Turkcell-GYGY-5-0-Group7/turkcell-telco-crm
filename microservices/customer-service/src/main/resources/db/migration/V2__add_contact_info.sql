-- FR-03: contact information on the customer master record. Both optional; existing rows have none.
ALTER TABLE customers
    ADD COLUMN email VARCHAR(255),
    ADD COLUMN phone VARCHAR(32);
