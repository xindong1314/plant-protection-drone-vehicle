package com.ppdv.api.security;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 未认证访问受保护资源时返回 code:401（HTTP 200），若依前端据此跳转登录。
 */
@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint, Serializable {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) {
        ServletUtils.renderJson(response, AjaxResult.error(401, "认证失败，无法访问系统资源，请重新登录"));
    }
}
