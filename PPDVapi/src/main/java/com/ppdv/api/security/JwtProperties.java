package com.ppdv.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置：ppdv.jwt.*
 */
@ConfigurationProperties(prefix = "ppdv.jwt")
public class JwtProperties {

    /** 令牌请求头名 */
    private String header = "Authorization";
    /** 签名密钥（HS256，需 >= 32 字节） */
    private String secret = "ppdv-super-secret-key-please-change-in-prod-0123456789abcdef";
    /** 令牌有效期（分钟） */
    private long expireMinutes = 720;

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireMinutes() {
        return expireMinutes;
    }

    public void setExpireMinutes(long expireMinutes) {
        this.expireMinutes = expireMinutes;
    }
}
