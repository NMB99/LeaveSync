ALTER TABLE leave_requests
    ADD COLUMN assigned_to VARCHAR(20) NOT NULL DEFAULT 'HR';

ALTER TABLE audit_logs
    ADD COLUMN assigned_to VARCHAR(20) NULL;