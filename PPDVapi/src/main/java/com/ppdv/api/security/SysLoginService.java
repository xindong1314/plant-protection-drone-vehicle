package com.ppdv.api.security;

import com.ppdv.api.common.constant.Constants;
import com.ppdv.api.common.core.redis.RedisCache;
import com.ppdv.api.common.exception.ServiceException;
import com.ppdv.api.common.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录服务：验证码校验 → 账号密码认证 → 生成令牌。
 */
@Service
public class SysLoginService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RedisCache redisCache;
    private final CaptchaProperties captchaProperties;

    public SysLoginService(AuthenticationManager authenticationManager,
                           TokenService tokenService,
                           RedisCache redisCache,
                           CaptchaProperties captchaProperties) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.redisCache = redisCache;
        this.captchaProperties = captchaProperties;
    }

    public String login(String username, String password, String code, String uuid) {
        if (captchaProperties.isEnabled()) {
            validateCaptcha(code, uuid);
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new ServiceException("用户名/密码必须填写");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (ServiceException e) {
            throw e;
        } catch (AuthenticationException e) {
            throw new ServiceException("用户不存在/密码错误");
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        HttpServletRequest request = ServletUtils.getRequest();
        if (request != null) {
            loginUser.setIpaddr(ServletUtils.getClientIp(request));
        }
        return tokenService.createToken(loginUser);
    }

    private void validateCaptcha(String code, String uuid) {
        String key = Constants.CAPTCHA_CODE_KEY + (uuid == null ? "" : uuid);
        String captcha = redisCache.get(key);
        redisCache.delete(key);
        if (captcha == null) {
            throw new ServiceException("验证码已失效");
        }
        if (!captcha.equalsIgnoreCase(code)) {
            throw new ServiceException("验证码错误");
        }
    }
}
