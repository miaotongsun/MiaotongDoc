-- V4: Contract management tables
CREATE TABLE mt_contract (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES mt_document(id),
    contract_no VARCHAR(100),
    contract_type VARCHAR(50),
    party_a VARCHAR(200),
    party_b VARCHAR(200),
    amount DECIMAL(15,2),
    currency VARCHAR(10) DEFAULT 'CNY',
    signing_date DATE,
    effective_date DATE,
    expiry_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    owner_user_id BIGINT NOT NULL,
    department_id BIGINT,
    remarks TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_contract_status ON mt_contract(status);
CREATE INDEX idx_contract_owner ON mt_contract(owner_user_id);
CREATE INDEX idx_contract_dept ON mt_contract(department_id);
CREATE INDEX idx_contract_doc ON mt_contract(document_id);

CREATE TABLE mt_contract_approval (
    id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL REFERENCES mt_contract(id),
    signing_task_id BIGINT REFERENCES mt_signing_task(id),
    action VARCHAR(20) NOT NULL,
    operator_id BIGINT,
    operator_name VARCHAR(100),
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_contract_approval_cid ON mt_contract_approval(contract_id);
