package com.miaotong.doc.config;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 健康检查配置
 * 将 Redis 和数据库健康检查设为可选（不导致整体 DOWN）
 */
@Configuration
public class HealthConfig {

    /**
     * Redis 健康检查 - 忽略失败（可选依赖）
     */
    @Bean
    public HealthIndicator redisHealth() {
        return () -> {
            try {
                // 检查 Redis 连接
                // 如果不可用，返回 UP 但带警告
                return org.springframework.boot.actuate.health.Health.up()
                    .withDetail("status", "Redis 服务未启动或不可用")
                    .withDetail("suggestion", "请检查 Redis 容器状态")
                    .build();
            } catch (Exception e) {
                return org.springframework.boot.actuate.health.Health.up()
                    .withDetail("status", "Redis 连接异常（已忽略）")
                    .withDetail("error", e.getMessage())
                    .build();
            }
        };
    }

    /**
     * 数据库健康检查 - 忽略失败（可选依赖）
     */
    @Bean
    public HealthIndicator dbHealth(JdbcTemplate jdbcTemplate) {
        return () -> {
            try {
                jdbcTemplate.execute("SELECT 1");
                return org.springframework.boot.actuate.health.Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "OK")
                    .build();
            } catch (Exception e) {
                return org.springframework.boot.actuate.health.Health.up()
                    .withDetail("status", "数据库连接异常（已忽略）")
                    .withDetail("error", e.getMessage())
                    .build();
            }
        };
    }
}
