CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leave_request_id UUID NOT NULL REFERENCES leave_requests(id),
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    actioned_by UUID REFERENCES users(id),
    notes VARCHAR(1000),
    changed_at TIMESTAMP NOT NULL DEFAULT now()
);