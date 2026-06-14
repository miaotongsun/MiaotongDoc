package com.miaotong.doc.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnlyOfficeCleanupScheduler {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 每分钟清理过期的 task_result 和 doc_changes 记录
     *
     * 清理策略：
     * 1. 空 callback/baseurl：立即清理（格式转换残留，会导致"备份副本"错误）
     * 2. status=8（强制保存完成）：5分钟后清理（给编辑器时间处理"版本已更改"并重新加载）
     * 3. 其他状态：24小时后清理
     */
    @Scheduled(cron = "0 * * * * ?")
    public void cleanupStaleRecords() {
        try {
            // 清理空 callback 的脏数据（格式转换残留）
            int staleEmpty = jdbcTemplate.update(
                    "DELETE FROM task_result WHERE callback IS NULL OR callback = ''");

            // 清理 status=8，5分钟后清理（给编辑器时间处理版本变更并重新加载）
            int staleStatus8 = jdbcTemplate.update(
                    "DELETE FROM task_result WHERE status = 8 AND last_open_date < now() - interval '5 minutes'");

            // 清理超过24小时未活动的记录
            int tasks = jdbcTemplate.update(
                    "DELETE FROM task_result WHERE status != 8 AND last_open_date < now() - interval '24 hours'");

            // 清理孤立的 doc_changes
            int changes = jdbcTemplate.update(
                    "DELETE FROM doc_changes WHERE NOT EXISTS (SELECT 1 FROM task_result t WHERE t.id = doc_changes.id)");

            if (staleEmpty > 0 || staleStatus8 > 0 || tasks > 0 || changes > 0) {
                log.info("OnlyOffice 过期记录清理完成: 空callback删除{}条, status8删除{}条, task_result删除{}条, doc_changes删除{}条",
                        staleEmpty, staleStatus8, tasks, changes);
            }
        } catch (Exception e) {
            log.warn("OnlyOffice 过期记录清理失败", e);
        }
    }
}
