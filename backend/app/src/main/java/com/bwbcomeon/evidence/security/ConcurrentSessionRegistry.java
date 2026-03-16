package com.bwbcomeon.evidence.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 单机版会话注册表：保证同一用户仅保留一个有效会话。
 */
@Component
public class ConcurrentSessionRegistry {

    private final ConcurrentMap<Long, String> userSessionIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> sessionUserIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, HttpSession> sessions = new ConcurrentHashMap<>();

    /**
     * 绑定用户与当前会话；若该用户已有其他会话，则返回旧会话供调用方失效处理。
     */
    public synchronized HttpSession register(Long userId, HttpSession session) {
        if (userId == null || session == null) {
            return null;
        }
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionUserIds.put(sessionId, userId);
        String previousSessionId = userSessionIds.put(userId, sessionId);
        if (previousSessionId == null || previousSessionId.equals(sessionId)) {
            return null;
        }
        return sessions.get(previousSessionId);
    }

    /**
     * 注销会话映射；会话销毁、主动登出时调用。
     */
    public synchronized void unregister(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessions.remove(sessionId);
        Long userId = sessionUserIds.remove(sessionId);
        if (userId != null) {
            userSessionIds.computeIfPresent(userId, (k, currentSessionId) ->
                    sessionId.equals(currentSessionId) ? null : currentSessionId);
        }
    }
}
