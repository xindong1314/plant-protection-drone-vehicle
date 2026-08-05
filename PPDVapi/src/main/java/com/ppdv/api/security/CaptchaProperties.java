package com.ppdv.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 验证码开关：ppdv.captcha.enabled
 */
@Component
@ConfigurationProperties(prefix = "ppdv.captcha")
public class CaptchaProperties {

    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
