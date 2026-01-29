package com.bwbcomeon.evidence.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求工具（IP、User-Agent 等）
 */
public final class WebUtils {

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_CLIENT_IP"
    };

    private WebUtils() {
    }

    /**
     * 获取客户端 IP（考虑代理）
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return comma > 0 ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取 User-Agent（截断过长）
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > 512) {
            return ua.substring(0, 512);
        }
        return ua;
    }
}
