package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.constant.Constants;
import com.ppdv.api.common.core.redis.RedisCache;
import com.ppdv.api.security.CaptchaProperties;
import com.wf.captcha.ArithmeticCaptcha;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 图形验证码（若依前端 /captchaImage）。captchaEnabled=false 时前端隐藏验证码。
 */
@RestController
public class CaptchaController {

    private final CaptchaProperties captchaProperties;
    private final RedisCache redisCache;

    public CaptchaController(CaptchaProperties captchaProperties, RedisCache redisCache) {
        this.captchaProperties = captchaProperties;
        this.redisCache = redisCache;
    }

    @GetMapping("/captchaImage")
    public AjaxResult getCode() {
        AjaxResult ajax = AjaxResult.success();
        boolean enabled = captchaProperties.isEnabled();
        ajax.put("captchaEnabled", enabled);
        if (!enabled) {
            return ajax;
        }
        String uuid = UUID.randomUUID().toString();
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(111, 36);
        captcha.setLen(2);
        String code = captcha.text();
        redisCache.set(Constants.CAPTCHA_CODE_KEY + uuid, code, 2, TimeUnit.MINUTES);

        String base64 = captcha.toBase64();
        int comma = base64.indexOf(',');
        ajax.put("uuid", uuid);
        ajax.put("img", comma >= 0 ? base64.substring(comma + 1) : base64);
        return ajax;
    }
}
