package com.ppdv.api.telemetry;

import com.ppdv.api.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 遥测实时推送：前端经 /ws/telemetry?token=JWT 握手，服务端广播上行遥测/告警。
 */
@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryWebSocketHandler.class);

    private final TokenService tokenService;
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public TelemetryWebSocketHandler(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = tokenOf(session);
        if (tokenService.getLoginUserByToken(token) == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未认证"));
            return;
        }
        sessions.add(session);
        log.debug("WS 连接建立，当前在线 {}", sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /** 向所有在线大屏广播一条 JSON 文本。 */
    public void broadcast(String json) {
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    synchronized (s) {
                        s.sendMessage(msg);
                    }
                } catch (Exception e) {
                    log.debug("WS 推送失败: {}", e.getMessage());
                }
            }
        }
    }

    private String tokenOf(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String kv : uri.getQuery().split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && "token".equals(kv.substring(0, i))) {
                return kv.substring(i + 1);
            }
        }
        return null;
    }
}
