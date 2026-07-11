package com.miaotong.doc.constants;

public final class NotificationType {

    private NotificationType() {}

    /** 文档共享 */
    public static final String SHARE = "SHARE";

    /** 共享被撤回 */
    public static final String REVOKE = "REVOKE";

    /** 共享权限变更 */
    public static final String PERMISSION_CHANGE = "PERMISSION_CHANGE";

    /** 签署请求 */
    public static final String SIGN_REQUEST = "SIGN_REQUEST";

    /** 签署确认 */
    public static final String SIGN_CONFIRM = "SIGN_CONFIRM";

    /** 签署拒绝 */
    public static final String SIGN_REJECT = "SIGN_REJECT";

    /** 签署取消 */
    public static final String SIGN_CANCEL = "SIGN_CANCEL";

    /** 签署超期 */
    public static final String SIGN_EXPIRED = "SIGN_EXPIRED";

    /** 文档评论 */
    public static final String COMMENT = "COMMENT";

    /** @提及（评论中） */
    public static final String MENTION = "MENTION";

    /** @提及（文档正文中） */
    public static final String DOC_MENTION = "DOC_MENTION";

    /** 文档新版本 */
    public static final String VERSION = "VERSION";
}
