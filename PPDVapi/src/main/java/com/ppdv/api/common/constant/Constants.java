package com.ppdv.api.common.constant;

/**
 * 通用常量。
 */
public final class Constants {

    private Constants() {
    }

    /** 令牌前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 令牌自定义标识（HTTP 头名，默认可由配置覆盖） */
    public static final String TOKEN = "token";

    /** JWT 中存放登录标识（uuid）的键 */
    public static final String LOGIN_USER_KEY = "login_user_key";

    /** 登录用户 Redis key 前缀 */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /** 验证码 Redis key 前缀 */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /** 字典缓存 key 前缀 */
    public static final String SYS_DICT_KEY = "sys_dict:";

    /** 参数缓存 key 前缀 */
    public static final String SYS_CONFIG_KEY = "sys_config:";

    /** 超级管理员用户 ID */
    public static final Long SUPER_ADMIN_ID = 1L;

    /** 全部权限标识 */
    public static final String ALL_PERMISSION = "*:*:*";

    /** 管理员角色权限标识 */
    public static final String SUPER_ADMIN_ROLE = "admin";
}
