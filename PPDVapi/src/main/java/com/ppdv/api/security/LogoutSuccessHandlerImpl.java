package com.ppdv.api.security;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 登出：删除 Redis 会话并返回成功。
 */
@Component
public class LogoutSuccessHandlerImpl implements LogoutSuccessHandler {

    private final TokenService tokenService;

    public LogoutSuccessHandlerImpl(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (loginUser != null) {
            tokenService.delLoginUser(loginUser.getToken());
        }
        ServletUtils.renderJson(response, AjaxResult.success("退出成功"));
    }
}
