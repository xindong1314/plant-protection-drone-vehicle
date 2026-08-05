package com.ppdv.api.emqx;

import com.ppdv.api.device.DeviceService;
import com.ppdv.api.protocol.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * EMQX HTTP 认证/授权回调（一机一密）。
 * - /auth：username=deviceId, password=secret 校验；后端服务账号标记为 superuser。
 * - /acl ：设备仅能收发自己 ppdv/v1/{deviceId}/# 主题（superuser 由 EMQX 直接放行，不会调用此接口）。
 * 说明：该接口在 SecurityConfig 中放行（无 JWT），生产可加共享密钥头校验。
 */
@RestController
@RequestMapping("/api/emqx")
public class EmqxController {

    private static final Logger log = LoggerFactory.getLogger(EmqxController.class);

    private static final Map<String, Object> ALLOW = Map.of("result", "allow");
    private static final Map<String, Object> ALLOW_SUPER = Map.of("result", "allow", "is_superuser", true);
    private static final Map<String, Object> DENY = Map.of("result", "deny");

    private final DeviceService deviceService;
    private final String backendUser;
    private final String backendPass;

    public EmqxController(DeviceService deviceService,
                          @Value("${ppdv.mqtt.username:ppdv-backend}") String backendUser,
                          @Value("${ppdv.mqtt.password:ppdv-backend-secret}") String backendPass) {
        this.deviceService = deviceService;
        this.backendUser = backendUser;
        this.backendPass = backendPass;
    }

    @PostMapping("/auth")
    public Map<String, Object> auth(@RequestBody Map<String, Object> body) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        if (backendUser.equals(username) && backendPass.equals(password)) {
            return ALLOW_SUPER;
        }
        if (deviceService.authenticate(username, password)) {
            return ALLOW;
        }
        log.warn("EMQX 认证拒绝: username={}", username);
        return DENY;
    }

    @PostMapping("/acl")
    public Map<String, Object> acl(@RequestBody Map<String, Object> body) {
        String username = str(body.get("username"));
        String topic = str(body.get("topic"));
        if (username == null || topic == null) {
            return DENY;
        }
        // 设备仅可访问自己的主题前缀
        if (topic.startsWith(Topics.PREFIX + username + "/")) {
            return ALLOW;
        }
        log.warn("EMQX 授权拒绝: username={}, topic={}", username, topic);
        return DENY;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
