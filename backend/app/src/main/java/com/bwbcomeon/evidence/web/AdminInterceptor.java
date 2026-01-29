package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.exception.ForbiddenException;
import com.bwbcomeon.evidence.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器：对 /api/admin/** 只允许 role_code=SYSTEM_ADMIN，否则 403 并写 audit AUTH_FORBIDDEN
 * 需在 AuthInterceptor 之后执行（保证已有 CURRENT_USER）
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private static final String ADMIN_ROLE = "SYSTEM_ADMIN";

    @Autowired
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/admin/")) {
            return true;
        }

        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return true; // 由 Auth 已处理 401
        }

        if (!ADMIN_ROLE.equals(user.getRoleCode())) {
            authService.recordAudit(request, "AUTH_FORBIDDEN", false, user.getId(),
                    null, null, "require SYSTEM_ADMIN");
            throw new ForbiddenException("需要管理员权限");
        }

        return true;
    }
}
