package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.LoginRequest;
import com.bwbcomeon.evidence.entity.AuditLog;
import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.exception.UnauthorizedException;
import com.bwbcomeon.evidence.mapper.AuditLogMapper;
import com.bwbcomeon.evidence.mapper.SysUserMapper;
import com.bwbcomeon.evidence.util.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录鉴权服务（Session，无 Spring Security/JWT）
 */
@Service
public class AuthService {

    public static final String SESSION_LOGIN_USER_ID = "LOGIN_USER_ID";

    private final SysUserMapper sysUserMapper;
    private final AuditLogMapper auditLogMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(SysUserMapper sysUserMapper, AuditLogMapper auditLogMapper) {
        this.sysUserMapper = sysUserMapper;
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 登录：校验用户名密码，写 Session，更新最近登录，写审计
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthUserVO login(HttpServletRequest request, LoginRequest body) {
        String ip = WebUtils.getClientIp(request);
        String userAgent = WebUtils.getUserAgent(request);
        String username = body != null && body.getUsername() != null ? body.getUsername().trim() : "";
        String password = body != null ? body.getPassword() : "";

        SysUser user = sysUserMapper.selectByUsername(username);
        if (user == null) {
            recordAudit(request, "LOGIN_FAIL", false, null, ip, userAgent, "username not found");
            throw new UnauthorizedException("用户名或密码错误");
        }
        if (Boolean.FALSE.equals(user.getEnabled()) || Boolean.TRUE.equals(user.getIsDeleted())) {
            recordAudit(request, "LOGIN_FAIL", false, user.getId(), ip, userAgent, "user disabled or deleted");
            throw new UnauthorizedException("账号已禁用或已删除");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            recordAudit(request, "LOGIN_FAIL", false, user.getId(), ip, userAgent, "password mismatch");
            throw new UnauthorizedException("用户名或密码错误");
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_LOGIN_USER_ID, user.getId());

        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        sysUserMapper.update(user);

        recordAudit(request, "LOGIN_SUCCESS", true, user.getId(), ip, userAgent, null);

        return toAuthUserVO(user);
    }

    /**
     * 登出：清除 Session，写审计
     */
    @Transactional(rollbackFor = Exception.class)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long userId = session != null ? (Long) session.getAttribute(SESSION_LOGIN_USER_ID) : null;
        String ip = WebUtils.getClientIp(request);
        String userAgent = WebUtils.getUserAgent(request);

        if (session != null) {
            session.invalidate();
        }

        recordAudit(request, "LOGOUT", true, userId, ip, userAgent, null);
    }

    /**
     * 根据用户ID 获取当前用户 VO（供 /api/auth/me 或拦截器后使用）
     */
    public AuthUserVO getCurrentUser(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || Boolean.FALSE.equals(user.getEnabled()) || Boolean.TRUE.equals(user.getIsDeleted())) {
            return null;
        }
        return toAuthUserVO(user);
    }

    /**
     * 写入审计日志（供 Service 与拦截器调用）
     */
    public void recordAudit(HttpServletRequest request, String action, boolean success,
                            Long actorUserId, String ip, String userAgent, String detail) {
        if (ip == null && request != null) {
            ip = WebUtils.getClientIp(request);
        }
        if (userAgent == null && request != null) {
            userAgent = WebUtils.getUserAgent(request);
        }
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setSuccess(success);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    private static AuthUserVO toAuthUserVO(SysUser user) {
        return new AuthUserVO(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRoleCode(),
                user.getEnabled()
        );
    }
}
