ALTER TABLE teams
    ADD CONSTRAINT uq_teams_name UNIQUE (name);

ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('EMPLOYEE', 'MANAGER', 'HR', 'ADMIN'));

ALTER TABLE leave_requests
    ADD CONSTRAINT chk_leave_requests_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'ESCALATED', 'REROUTED_TO_HR')
    );

CREATE INDEX idx_audit_logs_leave_request_id ON audit_logs (leave_request_id);

CREATE INDEX idx_leave_requests_user_id ON leave_requests (user_id);
CREATE INDEX idx_leave_requests_status ON leave_requests (status);
CREATE INDEX idx_leave_requests_start_date ON leave_requests (start_date);