CREATE TABLE leave_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    requires_balance_tracking BOOLEAN NOT NULL DEFAULT false,
    requires_hr_approval BOOLEAN NOT NULL DEFAULT false,
    requires_reason BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO leave_types (name, requires_balance_tracking, requires_hr_approval, requires_reason) VALUES
    ('ANNUAL', true, false, false),
    ('SICK', false, false, true),
    ('UNPAID', false, true, true),
    ('MATERNITY', false, true, false),
    ('PATERNITY', false, true, false)
;