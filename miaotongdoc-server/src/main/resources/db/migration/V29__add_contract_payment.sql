-- V29: 合同付款计划 + 通知扩展 contract_id
-- 2026-08-09 合同管理内容识别重塑 — Phase 3 付款计划功能

-- 1. 付款计划表(独立表,1对多关系)
CREATE TABLE IF NOT EXISTS mt_contract_payment (
  id BIGSERIAL PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  sequence INT NOT NULL DEFAULT 1,
  title VARCHAR(100),
  amount NUMERIC(18,2),
  currency VARCHAR(10) DEFAULT 'CNY',
  due_date DATE NOT NULL,
  paid_date DATE,
  status VARCHAR(20) DEFAULT 'pending',
  reminder_sent BOOLEAN DEFAULT FALSE,
  remarks TEXT,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  CONSTRAINT fk_payment_contract FOREIGN KEY (contract_id) REFERENCES mt_contract(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_payment_contract ON mt_contract_payment(contract_id);
CREATE INDEX IF NOT EXISTS idx_payment_due_status ON mt_contract_payment(due_date, status);

COMMENT ON TABLE mt_contract_payment IS '合同付款计划 — 支持 AI 自动抽取 + 到期提醒';
COMMENT ON COLUMN mt_contract_payment.contract_id IS '合同 ID(外键,引用 mt_contract.id)';
COMMENT ON COLUMN mt_contract_payment.sequence IS '期次(1,2,3...)';
COMMENT ON COLUMN mt_contract_payment.status IS 'pending / paid / overdue';
COMMENT ON COLUMN mt_contract_payment.reminder_sent IS '防重复提醒标志';

-- 2. mt_notification 加 contract_id 字段(nullable,通知可关联合同)
ALTER TABLE mt_notification ADD COLUMN IF NOT EXISTS contract_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_notification_contract ON mt_notification(contract_id);

COMMENT ON COLUMN mt_notification.contract_id IS '关联合同 ID(可选,用于点击跳转合同详情)';