package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.exception.UnauthorizedException;
import com.bwbcomeon.evidence.mapper.SysUserMapper;
import com.bwbcomeon.evidence.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 登录鉴权拦截器：拦截 /api/**，放行 /api/auth/login、/api/auth/logout
 * 从 Session 取 LOGIN_USER_ID，校验用户存在且 enabled=true、未逻辑删除，并设置 CURRENT_USER
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String REQUEST_CURRENT_USER = "CURRENT_USER";

    private static final List<String> EXCLUDE_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/logout"
    );

    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (EXCLUDE_PATHS.stream().anyMatch(path::equals)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        Long userId = session == null ? null : (Long) session.getAttribute(AuthService.SESSION_LOGIN_USER_ID);

        if (userId == null) {
            authService.recordAudit(request, "AUTH_UNAUTHORIZED", false, null,
                    null, null, "no session");
            throw new UnauthorizedException("未登录");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || Boolean.FALSE.equals(user.getEnabled()) || Boolean.TRUE.equals(user.getIsDeleted())) {
            if (session != null) {
                session.invalidate();
            }
            authService.recordAudit(request, "AUTH_UNAUTHORIZED", false, userId,
                    null, null, "user disabled or deleted");
            throw new UnauthorizedException("账号已禁用或已删除，请重新登录");
        }

        AuthUserVO vo = new AuthUserVO(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRoleCode(),
                user.getEnabled()
        );
        request.setAttribute(REQUEST_CURRENT_USER, vo);
        return true;
    }
}
