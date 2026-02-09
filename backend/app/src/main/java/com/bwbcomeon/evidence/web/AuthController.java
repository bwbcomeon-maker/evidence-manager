package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.ChangePasswordRequest;
import com.bwbcomeon.evidence.dto.LoginRequest;
import com.bwbcomeon.evidence.dto.VerifyPasswordRequest;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

    /**
     * POST /api/auth/verify-password
     * 验证当前用户原密码是否正确（仅校验不修改），body: { password }，需登录。用于修改密码前第一步校验。
     */
    @PostMapping("/verify-password")
    public Result<Void> verifyPassword(HttpServletRequest request, @RequestBody @Valid VerifyPasswordRequest body) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        if (body == null) {
            return Result.error(400, "请求体不能为空");
        }
        String password = body.getPassword() != null ? body.getPassword().trim() : null;
        authService.verifyPassword(user.getId(), password);
        return Result.success();
    }

    /**
     * POST /api/auth/change-password
     * 自助修改密码，body: { oldPassword, newPassword }，需登录
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(HttpServletRequest request, @RequestBody @Valid ChangePasswordRequest body) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        if (body == null) {
            return Result.error(400, "请求体不能为空");
        }
        String oldPwd = body.getOldPassword() != null ? body.getOldPassword().trim() : null;
        String newPwd = body.getNewPassword() != null ? body.getNewPassword().trim() : null;
        authService.changePassword(user.getId(), oldPwd, newPwd);
        return Result.success();
    }
}
