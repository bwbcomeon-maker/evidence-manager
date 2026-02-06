package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserSimpleVO;
import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户列表（sys_user），供项目成员选择器等使用
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private SysUserMapper sysUserMapper;

    /**
     * 获取 sys_user 列表（id, username, realName），用于添加成员时选择用户
     * GET /api/users
     */
    @GetMapping
    public Result<List<AuthUserSimpleVO>> listUsers(HttpServletRequest request) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        List<SysUser> list = sysUserMapper.selectAll();
        List<AuthUserSimpleVO> result = list.stream()
                .map(u -> new AuthUserSimpleVO(u.getId(), u.getUsername(), u.getRealName()))
                .collect(Collectors.toList());
        return Result.success(result);
    }
}
