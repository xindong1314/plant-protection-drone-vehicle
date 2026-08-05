package com.ppdv.api.security;

import com.ppdv.api.common.constant.Constants;
import com.ppdv.api.common.core.redis.RedisCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 令牌服务：JWT 仅携带登录标识 uuid，用户会话存 Redis，支持滑动续期与强制下线。
 */
@Component
public class TokenService {

    /** 距过期不足该毫秒数时自动续期（20 分钟） */
    private static final long REFRESH_THRESHOLD_MILLIS = 20 * 60 * 1000L;

    private final RedisCache redisCache;
    private final JwtProperties jwtProperties;
    private final SecretKey key;

    public TokenService(RedisCache redisCache, JwtProperties jwtProperties) {
        this.redisCache = redisCache;
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 创建令牌：生成 uuid、写入 Redis、返回 JWT。 */
    public String createToken(LoginUser loginUser) {
        String uuid = UUID.randomUUID().toString();
        loginUser.setToken(uuid);
        refreshToken(loginUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put(Constants.LOGIN_USER_KEY, uuid);
        return Jwts.builder()
                .claims(claims)
                .signWith(key)
                .compact();
    }

    /** 从请求解析并获取登录用户（不存在或已失效返回 null）。 */
    public LoginUser getLoginUser(HttpServletRequest request) {
        return getLoginUserByToken(getToken(request));
    }

    /** 直接用 JWT 字符串获取登录用户（WebSocket 握手用，令牌走 query 参数）。 */
    public LoginUser getLoginUserByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String jwt = token.startsWith(Constants.TOKEN_PREFIX) ? token.substring(Constants.TOKEN_PREFIX.length()) : token;
        try {
            Claims claims = parseToken(jwt);
            String uuid = (String) claims.get(Constants.LOGIN_USER_KEY);
            return redisCache.get(tokenKey(uuid));
        } catch (Exception e) {
            return null;
        }
    }

    /** 校验令牌，临近过期则续期。 */
    public void verifyToken(LoginUser loginUser) {
        long expireTime = loginUser.getExpireTime() == null ? 0 : loginUser.getExpireTime();
        if (expireTime - System.currentTimeMillis() <= REFRESH_THRESHOLD_MILLIS) {
            refreshToken(loginUser);
        }
    }

    /** 刷新会话有效期并写回 Redis。 */
    public void refreshToken(LoginUser loginUser) {
        long now = System.currentTimeMillis();
        loginUser.setLoginTime(now);
        loginUser.setExpireTime(now + jwtProperties.getExpireMinutes() * 60 * 1000L);
        redisCache.set(tokenKey(loginUser.getToken()), loginUser,
                jwtProperties.getExpireMinutes(), TimeUnit.MINUTES);
    }

    /** 保存会话（信息变更后调用）。 */
    public void setLoginUser(LoginUser loginUser) {
        if (loginUser != null && StringUtils.hasText(loginUser.getToken())) {
            refreshToken(loginUser);
        }
    }

    /** 删除会话（登出 / 强制下线）。 */
    public void delLoginUser(String uuid) {
        if (StringUtils.hasText(uuid)) {
            redisCache.delete(tokenKey(uuid));
        }
    }

    private String getToken(HttpServletRequest request) {
        String token = request.getHeader(jwtProperties.getHeader());
        if (StringUtils.hasText(token) && token.startsWith(Constants.TOKEN_PREFIX)) {
            return token.substring(Constants.TOKEN_PREFIX.length());
        }
        return token;
    }

    private Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private String tokenKey(String uuid) {
        return Constants.LOGIN_TOKEN_KEY + uuid;
    }
}
