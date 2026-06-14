-- V6: Fix stale signing records and add rejection cleanup

-- Clean up existing stale records: pending records for cancelled/expired tasks
UPDATE mt_signing_record SET status = 'cancelled'
WHERE status = 'pending' AND task_id IN (
    SELECT id FROM mt_signing_task WHERE status = 'cancelled'
);
UPDATE mt_signing_record SET status = 'expired'
WHERE status = 'pending' AND task_id IN (
    SELECT id FROM mt_signing_task WHERE status = 'expired'
);
