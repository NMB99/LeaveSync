CREATE TABLE leave_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    year INT NOT NULL,
    total_entitlement DECIMAL(4,1) NOT NULL,
    carried_over DECIMAL(4,1) NOT NULL DEFAULT 0,
    leave_used DECIMAL(4,1) NOT NULL DEFAULT 0,
    pending_days DECIMAL(4,1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_leave_balance_user_year UNIQUE (user_id, year)
);