package com.ppdv.api.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Servlet 工具：获取当前请求、渲染 JSON、取客户端 IP。
 */
public final class ServletUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ServletUtils() {
    }

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    /** 向响应体写入 JSON（HTTP 状态固定 200，业务码放在 body.code，对齐若依前端约定）。 */
    public static void renderJson(HttpServletResponse response, Object data) {
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print(MAPPER.writeValueAsString(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = header(request, "X-Forwarded-For");
        if (ip == null) {
            ip = header(request, "X-Real-IP");
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private static String header(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return (v == null || v.isBlank() || "unknown".equalsIgnoreCase(v)) ? null : v;
    }
}
