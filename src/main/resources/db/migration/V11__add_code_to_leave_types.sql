ALTER TABLE leave_types ADD COLUMN code VARCHAR(50);

UPDATE leave_types SET code = 'ANNUAL' WHERE name = 'ANNUAL';
UPDATE leave_types SET code = 'SICK' WHERE name = 'SICK';
UPDATE leave_types SET code = 'UNPAID' WHERE name = 'UNPAID';
UPDATE leave_types SET code = 'MATERNITY' WHERE name = 'MATERNITY';
UPDATE leave_types SET code = 'PATERNITY' WHERE name = 'PATERNITY';

ALTER TABLE leave_types ALTER COLUMN code SET NOT NULL;
ALTER TABLE leave_types ADD CONSTRAINT uq_leave_types_code UNIQUE (code);