package com.ppdv.api.security;

import com.ppdv.api.common.constant.Constants;
import com.ppdv.api.common.core.redis.RedisCache;
import com.ppdv.api.common.exception.ServiceException;
import com.ppdv.api.common.utils.ServletUtils;
import com.ppdv.api.system.domain.SysLogininfor;
import com.ppdv.api.system.repository.SysLogininforRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 登录服务：验证码校验 → 账号密码认证 → 生成令牌。
 */
@Service
public class SysLoginService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RedisCache redisCache;
    private final CaptchaProperties captchaProperties;
    private final SysLogininforRepository logininforRepository;

    public SysLoginService(AuthenticationManager authenticationManager,
                           TokenService tokenService,
                           RedisCache redisCache,
                           CaptchaProperties captchaProperties,
                           SysLogininforRepository logininforRepository) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.redisCache = redisCache;
        this.captchaProperties = captchaProperties;
        this.logininforRepository = logininforRepository;
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
            recordLogininfor(username, null, "1", e.getMessage());
            throw new ServiceException("用户不存在/密码错误");
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        HttpServletRequest request = ServletUtils.getRequest();
        if (request != null) {
            loginUser.setIpaddr(ServletUtils.getClientIp(request));
        }
        String token = tokenService.createToken(loginUser);
        recordLogininfor(loginUser.getUserName(), loginUser.getIpaddr(), "0", "登录成功");
        return token;
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

    /** 记录登录日志：整体 try/catch 吞掉异常，日志失败绝不能影响登录。 */
    private void recordLogininfor(String username, String ipaddr, String status, String msg) {
        try {
            HttpServletRequest request = ServletUtils.getRequest();
            SysLogininfor logininfor = new SysLogininfor();
            logininfor.setUserName(truncate(username, 50));
            logininfor.setIpaddr(truncate(StringUtils.hasText(ipaddr) ? ipaddr : ServletUtils.getClientIp(request), 128));
            logininfor.setLoginLocation("");
            logininfor.setBrowser(resolveBrowser(request));
            logininfor.setOs(resolveOs(request));
            logininfor.setStatus(status);
            logininfor.setMsg(truncate(msg, 255));
            logininfor.setLoginTime(LocalDateTime.now());
            logininforRepository.save(logininfor);
        } catch (Exception e) {
            // 日志写入失败不影响登录主流程
        }
    }

    /** 按 User-Agent 简单包含判断浏览器类型，解析不出返回空串。 */
    private String resolveBrowser(HttpServletRequest request) {
        try {
            String ua = request == null ? null : request.getHeader("User-Agent");
            if (!StringUtils.hasText(ua)) {
                return "";
            }
            if (ua.contains("Edg/")) {
                return "Edge";
            }
            if (ua.contains("MSIE") || ua.contains("Trident")) {
                return "IE";
            }
            if (ua.contains("Firefox")) {
                return "Firefox";
            }
            if (ua.contains("Chrome") || ua.contains("Chromium")) {
                return "Chrome";
            }
            if (ua.contains("Safari")) {
                return "Safari";
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 按 User-Agent 简单包含判断操作系统，解析不出返回空串。 */
    private String resolveOs(HttpServletRequest request) {
        try {
            String ua = request == null ? null : request.getHeader("User-Agent");
            if (!StringUtils.hasText(ua)) {
                return "";
            }
            if (ua.contains("Windows")) {
                return "Windows";
            }
            if (ua.contains("Android")) {
                return "Android";
            }
            if (ua.contains("iPhone") || ua.contains("iPad")) {
                return "iOS";
            }
            if (ua.contains("Mac OS")) {
                return "Mac OS";
            }
            if (ua.contains("Linux")) {
                return "Linux";
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 按数据库列长截断，避免超长文本导致日志写入失败。 */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
