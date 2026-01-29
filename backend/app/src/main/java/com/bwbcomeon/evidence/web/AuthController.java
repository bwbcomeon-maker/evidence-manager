package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.LoginRequest;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 登录鉴权接口（Session，无 Spring Security/JWT）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * POST /api/auth/login
     * body: { "username": "...", "password": "..." }
     * 成功：Session 写入 LOGIN_USER_ID，返回当前用户信息
     * 失败：401 + audit LOGIN_FAIL
     */
    @PostMapping("/login")
    public Result<AuthUserVO> login(HttpServletRequest request, @RequestBody LoginRequest body) {
        AuthUserVO user = authService.login(request, body);
        return Result.success(user);
    }

    /**
     * POST /api/auth/logout
     * 清除 Session，写 audit LOGOUT
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return Result.success();
    }

    /**
     * GET /api/auth/me
     * 从 Session 获取当前用户并返回；未登录由拦截器返回 401
     */
    @GetMapping("/me")
    public Result<AuthUserVO> me(HttpServletRequest request) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(user);
    }
}
