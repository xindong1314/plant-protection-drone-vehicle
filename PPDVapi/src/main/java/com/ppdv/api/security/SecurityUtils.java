package com.ppdv.api.security;

import com.ppdv.api.common.constant.Constants;
import com.ppdv.api.common.exception.ServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 安全工具：获取当前登录用户、密码加解密、管理员判定。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getLoginUser() {
        try {
            return (LoginUser) getAuthentication().getPrincipal();
        } catch (Exception e) {
            throw new ServiceException("获取用户信息异常", 401);
        }
    }

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    public static Long getDeptId() {
        return getLoginUser().getDeptId();
    }

    public static String getUsername() {
        return getLoginUser().getUsername();
    }

    /** 是否超级管理员（用户 ID = 1）。 */
    public static boolean isAdmin(Long userId) {
        return userId != null && Constants.SUPER_ADMIN_ID.equals(userId);
    }

    public static String encryptPassword(String password) {
        return new BCryptPasswordEncoder().encode(password);
    }

    public static boolean matchesPassword(String rawPassword, String encodedPassword) {
        return new BCryptPasswordEncoder().matches(rawPassword, encodedPassword);
    }
}
