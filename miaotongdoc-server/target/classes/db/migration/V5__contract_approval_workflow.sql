-- V5: Contract approval workflow redesign
-- Add sequential approval nodes table and contract integrity fields

CREATE TABLE mt_contract_approval_node (
    id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL REFERENCES mt_contract(id),
    step_order INT NOT NULL,
    approver_id BIGINT NOT NULL,
    approver_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'waiting',
    remark TEXT,
    acted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_node_contract ON mt_contract_approval_node(contract_id);
CREATE INDEX idx_approval_node_approver ON mt_contract_approval_node(approver_id);

ALTER TABLE mt_contract ADD COLUMN current_step INT DEFAULT 0;
ALTER TABLE mt_contract ADD COLUMN reminder_sent BOOLEAN DEFAULT FALSE;
ALTER TABLE mt_contract ADD COLUMN approved_hash VARCHAR(64);
ALTER TABLE mt_contract ADD COLUMN approved_version INT;

-- Fix stale signing records: mark records as cancelled/expired for non-active tasks
UPDATE mt_signing_record SET status = 'cancelled'
WHERE status = 'pending' AND task_id IN (
    SELECT id FROM mt_signing_task WHERE status = 'cancelled'
);
UPDATE mt_signing_record SET status = 'expired'
WHERE status = 'pending' AND task_id IN (
    SELECT id FROM mt_signing_task WHERE status = 'expired'
);
