package com.bwbcomeon.evidence.config;

import com.bwbcomeon.evidence.security.ConcurrentSessionRegistry;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 HttpSessionListener，确保会话销毁后清理单登录映射。
 */
@Configuration
public class SessionListenerConfig {

    @Bean
    public ServletListenerRegistrationBean<HttpSessionListener> singleLoginSessionListener(
            ConcurrentSessionRegistry sessionRegistry) {
        HttpSessionListener listener = new HttpSessionListener() {
            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                sessionRegistry.unregister(se.getSession().getId());
            }
        };
        return new ServletListenerRegistrationBean<>(listener);
    }
}
