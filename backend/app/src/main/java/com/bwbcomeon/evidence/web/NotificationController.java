package com.bwbcomeon.evidence.web;

import com.bwbcomeon.evidence.dto.AuthUserVO;
import com.bwbcomeon.evidence.dto.Result;
import com.bwbcomeon.evidence.dto.TodoItemVO;
import com.bwbcomeon.evidence.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 待办/消息接口
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /**
     * 当前用户待办列表
     * GET /api/notifications/todos?unreadOnly=true&type=ARCHIVE_PENDING&limit=50
     */
    @GetMapping("/todos")
    public Result<List<TodoItemVO>> listTodos(HttpServletRequest request,
                                              @RequestParam(required = false) Boolean unreadOnly,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) Integer limit) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        List<TodoItemVO> list = notificationService.listTodos(user.getId(), unreadOnly, type, limit);
        return Result.success(list);
    }

    /**
     * 单条标记已读 PATCH /api/notifications/{id}/read
     */
    @PatchMapping("/{id}/read")
    public Result<Void> markRead(HttpServletRequest request, @PathVariable Long id) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        Long userId = user.getId() != null ? user.getId().longValue() : null;
        notificationService.markRead(userId, id != null ? id.longValue() : null);
        return Result.success();
    }

    /**
     * 批量标记已读 POST /api/notifications/read body: { "ids": [1, 2, 3] }
     */
    @PostMapping("/read")
    @SuppressWarnings("unchecked")
    public Result<Void> markReadByIds(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> body) {
        AuthUserVO user = (AuthUserVO) request.getAttribute(AuthInterceptor.REQUEST_CURRENT_USER);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        List<Long> ids = null;
        if (body != null && body.get("ids") instanceof List) {
            List<?> raw = (List<?>) body.get("ids");
            ids = new ArrayList<>();
            for (Object o : raw) {
                if (o instanceof Number) {
                    ids.add(((Number) o).longValue());
                }
            }
        }
        if (ids == null || ids.isEmpty()) {
            return Result.error(400, "ids 不能为空");
        }
        notificationService.markReadByIds(user.getId(), ids);
        return Result.success();
    }
}
