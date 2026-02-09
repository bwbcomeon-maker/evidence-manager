package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.LoginRequest;
import com.bwbcomeon.evidence.entity.AuditLog;
import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.exception.BusinessException;
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
     * 验证当前用户密码是否正确（仅校验，不修改），用于修改密码前第一步校验
     * @param userId 当前登录用户 ID
     * @param password 待验证的密码
     * @throws UnauthorizedException 密码错误时抛出，message 为「原始密码不正确」
     */
    public void verifyPassword(Long userId, String password) {
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || Boolean.FALSE.equals(user.getEnabled()) || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new UnauthorizedException("用户不存在或已禁用");
        }
        String pwd = (password != null) ? password.trim() : null;
        if (pwd == null || pwd.isEmpty() || user.getPasswordHash() == null
                || !passwordEncoder.matches(pwd, user.getPasswordHash())) {
            throw new BusinessException(400, "原始密码不正确");
        }
    }

    /**
     * 自助修改密码：校验原密码后更新为新密码
     * @param userId 当前登录用户 ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new UnauthorizedException("未登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || Boolean.FALSE.equals(user.getEnabled()) || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new UnauthorizedException("用户不存在或已禁用");
        }
        String oldPwd = (oldPassword != null) ? oldPassword.trim() : null;
        if (oldPwd == null || oldPwd.isEmpty() || user.getPasswordHash() == null
                || !passwordEncoder.matches(oldPwd, user.getPasswordHash())) {
            throw new BusinessException(400, "原始密码不正确");
        }
        String newPwd = (newPassword != null) ? newPassword.trim() : null;
        if (newPwd == null || newPwd.isEmpty()) {
            throw new BusinessException(400, "新密码不能为空");
        }
        if (newPwd.length() > 72) {
            throw new BusinessException(400, "新密码过长");
        }
        String newHash;
        try {
            newHash = passwordEncoder.encode(newPwd);
        } catch (Exception e) {
            throw new BusinessException(400, "新密码无效");
        }
        sysUserMapper.resetPassword(userId, newHash);
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
        recordAudit(request, action, success, actorUserId, ip, userAgent, detail, null, null, null, null, null);
    }

    /**
     * 写入审计日志（含目标类型/ID、项目ID、变更前后快照，供证据作废等场景）
     */
    public void recordAudit(HttpServletRequest request, String action, boolean success,
                            Long actorUserId, String ip, String userAgent, String detail,
                            String targetType, Long targetId, Long projectId, String beforeData, String afterData) {
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
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setProjectId(projectId);
        log.setBeforeData(beforeData);
        log.setAfterData(afterData);
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
