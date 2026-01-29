package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.*;
import com.bwbcomeon.evidence.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理接口（仅 SYSTEM_ADMIN 可访问，由 AdminInterceptor 保证）
 * 所有写操作均记录 audit_log
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    /**
     * POST /api/admin/users 新增用户
     * 入参：username(必填), password(可选默认Init@12345), realName, phone, email, roleCode(必填), enabled
     */
    @PostMapping
    public Result<AdminUserListItemVO> create(HttpServletRequest request, @RequestBody AdminUserCreateRequest body) {
        AdminUserListItemVO vo = adminUserService.create(request, body);
        return Result.success(vo);
    }

    /**
     * GET /api/admin/users?page=&pageSize=&keyword=&roleCode=&enabled=
     * 分页+搜索（username/real_name/phone/email 模糊），返回 { total, records, page, pageSize }
     */
    @GetMapping
    public Result<PageResult<AdminUserListItemVO>> page(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Boolean enabled) {
        PageResult<AdminUserListItemVO> data = adminUserService.page(request, page, pageSize, keyword, roleCode, enabled);
        return Result.success(data);
    }

    /**
     * PUT /api/admin/users/{id} 修改用户（realName/phone/email/roleCode/enabled，不允许改 password_hash）
     */
    @PutMapping("/{id}")
    public Result<AdminUserListItemVO> update(HttpServletRequest request,
                                              @PathVariable Long id,
                                              @RequestBody AdminUserUpdateRequest body) {
        AdminUserListItemVO vo = adminUserService.update(request, id, body);
        return Result.success(vo);
    }

    /**
     * PATCH /api/admin/users/{id}/enable  body: { enabled: true/false }
     */
    @PatchMapping("/{id}/enable")
    public Result<Void> setEnabled(HttpServletRequest request,
                                  @PathVariable Long id,
                                  @RequestBody AdminUserEnableRequest body) {
        if (body == null || body.getEnabled() == null) {
            return Result.error(400, "enabled 不能为空");
        }
        adminUserService.setEnabled(request, id, body.getEnabled());
        return Result.success();
    }

    /**
     * POST /api/admin/users/{id}/reset-password
     * 重置为随机 8-12 位（数字+字母），返回一次性明文给前端展示，写 audit USER_RESET_PWD
     */
    @PostMapping("/{id}/reset-password")
    public Result<ResetPasswordVO> resetPassword(HttpServletRequest request, @PathVariable Long id) {
        ResetPasswordVO vo = adminUserService.resetPassword(request, id);
        return Result.success(vo);
    }

    /**
     * DELETE /api/admin/users/{id} 逻辑删除
     * 不能删除自己，不能删除系统内唯一管理员
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        adminUserService.logicalDelete(request, id);
        return Result.success();
    }
}
