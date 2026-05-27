package com.miaotong.doc.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConditionalOnProperty(prefix = "sso", name = "enabled", havingValue = "true")
@ConfigurationProperties(prefix = "sso")
public class SsoConfig {

    /** 是否启用 SSO */
    private boolean enabled;

    /** 提供商名称（显示用） */
    private String providerName;

    /** Claim 映射 */
    private ClaimMapping claimMapping = new ClaimMapping();

    /** 自动创建用户配置 */
    private AutoProvision autoProvision = new AutoProvision();

    /** 登录成功重定向 */
    private String successRedirect = "/home.html";

    /** 登录失败重定向 */
    private String failureRedirect = "/login.html?error=sso_failed";

    @Data
    public static class ClaimMapping {
        private String employeeId = "employee_id";
        private String departmentId = "department_id";
        private String realName = "name";
    }

    @Data
    public static class AutoProvision {
        /** 是否允许自动创建用户 */
        private boolean enabled = true;

        /** SSO 用户工号范围起始 */
        private int employeeIdRangeStart = 90000001;

        /** SSO 用户工号范围结束 */
        private int employeeIdRangeEnd = 99999999;

        /** 默认角色 */
        private String defaultRole = "user";

        /** 邮箱冲突策略：link（自动关联）/ reject（拒绝） */
        private String emailCollisionStrategy = "reject";
    }
}
