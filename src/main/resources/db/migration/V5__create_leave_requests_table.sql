CREATE TABLE leave_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    leave_type_id UUID NOT NULL REFERENCES leave_types(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_half_day BOOLEAN NOT NULL DEFAULT false,
    reason VARCHAR(1000),
    total_working_days DECIMAL(4,1) NOT NULL,
    status VARCHAR(20) NOT NULL,
    actioned_by UUID REFERENCES users(id),
    notice_period_warning BOOLEAN NOT NULL DEFAULT false,
    overlap_warning BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);