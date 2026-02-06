package com.bwbcomeon.evidence.service;

import com.bwbcomeon.evidence.dto.*;
import com.bwbcomeon.evidence.entity.AuthUser;
import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.exception.BusinessException;
import com.bwbcomeon.evidence.mapper.AuthUserMapper;
import com.bwbcomeon.evidence.mapper.SysUserMapper;
import com.bwbcomeon.evidence.web.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 管理员-用户管理服务（仅 SYSTEM_ADMIN 可访问，由 AdminInterceptor 保证）
 * 所有写操作均写 audit_log。
 * 安全规则：禁止对“自己”执行禁用/启用/删除/改角色/重置密码等；admin 禁止修改 admin 自己任何字段。
 */
@Service
public class AdminUserService {

    /** 禁止自我操作时返回的 HTTP 状态码（全系统统一） */
    public static final int SELF_OPERATION_FORBIDDEN_CODE = 403;
    /** 禁止自我操作时的提示文案 */
    public static final String SELF_OPERATION_FORBIDDEN_MESSAGE = "不允许对自己的账号执行该操作（请联系其他管理员）";
    /** 审计：拦截到自我管理被禁止时使用的 action */
    public static final String AUDIT_ACTION_SELF_OPERATION_FORBIDDEN = "SELF_OPERATION_FORBIDDEN";

    /** V1：系统级角色 SYSTEM_ADMIN / PMO / AUDITOR；PROJECT_* 保留仅作存量兼容，不再参与权限判断 */
    public static final Set<String> VALID_ROLE_CODES = Set.of(
            "SYSTEM_ADMIN", "PMO", "AUDITOR",
            "PROJECT_OWNER", "PROJECT_EDITOR", "PROJECT_VIEWER", "PROJECT_AUDITOR"
    );
    private static final String DEFAULT_PASSWORD = "Init@12345";
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String ADMIN_USERNAME = "admin";

    private final SysUserMapper sysUserMapper;
    private final AuthUserMapper authUserMapper;
    private final AuthService authService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminUserService(SysUserMapper sysUserMapper, AuthUserMapper authUserMapper, AuthService authService) {
        this.sysUserMapper = sysUserMapper;
        this.authUserMapper = authUserMapper;
        this.authService = authService;
    }

    private static AuthUserVO currentUser(HttpServletRequest request) {
        return (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
    }

    private static Long currentUserId(HttpServletRequest request) {
        AuthUserVO u = currentUser(request);
        return u != null ? u.getId() : null;
    }

    private static String currentUsername(HttpServletRequest request) {
        AuthUserVO u = currentUser(request);
        return u != null ? u.getUsername() : null;
    }

    /**
     * 禁止 admin 对 admin 自己执行任何管理操作（优先级最高）。
     * 违反时写审计并抛出 BusinessException(403)。
     */
    private void assertNotAdminSelfOperation(HttpServletRequest request, SysUser targetUser) {
        String current = currentUsername(request);
        if (current == null || targetUser == null) return;
        if (!ADMIN_USERNAME.equalsIgnoreCase(current.trim())) return;
        if (!ADMIN_USERNAME.equalsIgnoreCase(targetUser.getUsername() != null ? targetUser.getUsername().trim() : "")) return;
        authService.recordAudit(request, AUDIT_ACTION_SELF_OPERATION_FORBIDDEN, false, currentUserId(request),
                null, null, "actor=admin,target=admin,reason=admin cannot manage admin");
        throw new BusinessException(SELF_OPERATION_FORBIDDEN_CODE, SELF_OPERATION_FORBIDDEN_MESSAGE);
    }

    /**
     * 禁止任何登录用户对“自己”执行禁用/启用/删除/改角色/重置密码等操作。
     * 违反时写审计并抛出 BusinessException(403)。
     */
    private void assertNotSelfOperation(HttpServletRequest request, SysUser targetUser) {
        Long cid = currentUserId(request);
        String cname = currentUsername(request);
        if (targetUser == null) return;
        boolean byId = cid != null && targetUser.getId() != null && cid.equals(targetUser.getId());
        boolean byUsername = cname != null && targetUser.getUsername() != null
                && cname.trim().equalsIgnoreCase(targetUser.getUsername().trim());
        if (byId || byUsername) {
            authService.recordAudit(request, AUDIT_ACTION_SELF_OPERATION_FORBIDDEN, false, cid,
                    null, null, "actor=" + cname + ",targetUserId=" + targetUser.getId() + ",reason=self operation forbidden");
            throw new BusinessException(SELF_OPERATION_FORBIDDEN_CODE, SELF_OPERATION_FORBIDDEN_MESSAGE);
        }
    }

    /** 校验 roleCode 属于固定集合 */
    private static void validateRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BusinessException(400, "角色编码不能为空");
        }
        if (!VALID_ROLE_CODES.contains(roleCode.trim())) {
            throw new BusinessException(400, "角色编码不合法，可选：" + String.join(", ", VALID_ROLE_CODES));
        }
    }

    /**
     * 新增用户，写 audit USER_CREATE
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminUserListItemVO create(HttpServletRequest request, AdminUserCreateRequest req) {
        String username = req.getUsername() != null ? req.getUsername().trim() : "";
        if (username.isEmpty()) {
            throw new BusinessException(400, "登录账号不能为空");
        }
        validateRoleCode(req.getRoleCode());

        if (sysUserMapper.countByUsername(username) > 0) {
            throw new BusinessException(400, "登录账号已存在");
        }

        String plainPassword = (req.getPassword() != null && !req.getPassword().isBlank())
                ? req.getPassword() : DEFAULT_PASSWORD;
        String passwordHash = passwordEncoder.encode(plainPassword);

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setRealName(req.getRealName());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setRoleCode(req.getRoleCode().trim());
        user.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        user.setIsDeleted(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);

        // 同步写入 auth_user，便于创建项目/证据时 resolveCreatedByUuid 能解析到当前用户
        AuthUser authUser = new AuthUser();
        authUser.setUsername(username);
        authUser.setDisplayName(user.getRealName() != null && !user.getRealName().isBlank() ? user.getRealName() : username);
        authUser.setEmail(user.getEmail());
        authUser.setIsActive(Boolean.TRUE.equals(user.getEnabled()));
        authUser.setCreatedAt(OffsetDateTime.now());
        authUserMapper.insert(authUser);

        authService.recordAudit(request, "USER_CREATE", true, currentUserId(request),
                null, null, "userId=" + user.getId() + ",username=" + username);
        return toListItemVO(user);
    }

    /**
     * 分页查询，keyword 模糊 username/real_name/phone/email
     */
    public PageResult<AdminUserListItemVO> page(HttpServletRequest request,
                                                int page, int pageSize, String keyword, String roleCode, Boolean enabled) {
        int p = Math.max(1, page);
        int ps = Math.min(100, Math.max(1, pageSize));
        long offset = (long) (p - 1) * ps;

        List<SysUser> list = sysUserMapper.pageQuery(keyword, roleCode, enabled, offset, ps);
        long total = sysUserMapper.countPageQuery(keyword, roleCode, enabled);
        List<AdminUserListItemVO> records = list.stream().map(AdminUserService::toListItemVO).collect(Collectors.toList());
        return new PageResult<>(total, records, p, ps);
    }

    /**
     * 修改用户（不修改 password/username），写 audit USER_UPDATE
     * 禁止操作自己（含 admin 不能改 admin）
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminUserListItemVO update(HttpServletRequest request, Long id, AdminUserUpdateRequest req) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(404, "用户不存在");
        }
        assertNotAdminSelfOperation(request, user);
        assertNotSelfOperation(request, user);
        if (req.getRoleCode() != null && !req.getRoleCode().isBlank()) {
            validateRoleCode(req.getRoleCode());
        }

        if (req.getRealName() != null) user.setRealName(req.getRealName());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getRoleCode() != null) user.setRoleCode(req.getRoleCode().trim());
        if (req.getEnabled() != null) user.setEnabled(req.getEnabled());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.update(user);

        authService.recordAudit(request, "USER_UPDATE", true, currentUserId(request),
                null, null, "userId=" + id);
        return toListItemVO(user);
    }

    /**
     * 启用/禁用，写 audit USER_ENABLE
     * 禁止操作自己（含 admin 不能禁用/启用自己）
     */
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(HttpServletRequest request, Long id, boolean enabled) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(404, "用户不存在");
        }
        assertNotAdminSelfOperation(request, user);
        assertNotSelfOperation(request, user);
        sysUserMapper.setEnabled(id, enabled);
        authService.recordAudit(request, "USER_ENABLE", true, currentUserId(request),
                null, null, "userId=" + id + ",enabled=" + enabled);
    }

    /**
     * 重置密码为随机 8-12 位（数字+字母），返回明文一次，写 audit USER_RESET_PWD
     * 禁止操作自己（含 admin 不能重置自己密码，应走自助改密）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResetPasswordVO resetPassword(HttpServletRequest request, Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(404, "用户不存在");
        }
        assertNotAdminSelfOperation(request, user);
        assertNotSelfOperation(request, user);
        String newPassword = randomPassword(8, 12);
        String hash = passwordEncoder.encode(newPassword);
        sysUserMapper.resetPassword(id, hash);
        authService.recordAudit(request, "USER_RESET_PWD", true, currentUserId(request),
                null, null, "userId=" + id);
        return new ResetPasswordVO(newPassword);
    }

    /**
     * 逻辑删除，写 audit USER_DELETE；不能删除自己，不能删除系统内唯一管理员
     * 禁止操作自己（统一 403 + SELF_OPERATION_FORBIDDEN 审计）
     */
    @Transactional(rollbackFor = Exception.class)
    public void logicalDelete(HttpServletRequest request, Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(404, "用户不存在");
        }
        assertNotAdminSelfOperation(request, user);
        assertNotSelfOperation(request, user);
        if ("SYSTEM_ADMIN".equals(user.getRoleCode()) && sysUserMapper.countByRoleCodeAndNotDeleted("SYSTEM_ADMIN") <= 1) {
            throw new BusinessException(400, "不能删除系统内唯一管理员");
        }
        sysUserMapper.logicalDelete(id);
        authService.recordAudit(request, "USER_DELETE", true, currentUserId(request), null, null, "userId=" + id);
    }

    private static AdminUserListItemVO toListItemVO(SysUser u) {
        return new AdminUserListItemVO(
                u.getId(),
                u.getUsername(),
                u.getRealName(),
                u.getPhone(),
                u.getEmail(),
                u.getRoleCode(),
                u.getEnabled(),
                u.getCreatedAt()
        );
    }

    private static String randomPassword(int minLen, int maxLen) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int len = r.nextInt(minLen, maxLen + 1);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
