package com.smart.admin.controller;

import com.smart.admin.service.OnlineUserService;
import com.smart.common.core.web.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Online user controller.
 *
 * 在线用户控制器。
 */
@Slf4j
@RestController
@RequestMapping("/system/online-user")
@RequiredArgsConstructor
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    /**
     * Get all online users for current tenant.
     */
    @PreAuthorize("@authz.hasPermission('sys_online_user_view')")
    @GetMapping("/list")
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.success(onlineUserService.getOnlineUsers());
    }

    /**
     * Get online user count.
     */
    @GetMapping("/count")
    public ApiResult<Long> count() {
        return ApiResult.success(onlineUserService.getOnlineUserCount());
    }

    /**
     * Force logout a user.
     */
    @PreAuthorize("@authz.hasPermission('sys_online_user_force_logout')")
    @DeleteMapping("/{userId}")
    public ApiResult<Void> forceLogout(@PathVariable Long userId) {
        onlineUserService.forceLogout(userId);
        return ApiResult.success();
    }

    /**
     * Force logout by token (current user kick self).
     */
    @DeleteMapping("/token/{token}")
    public ApiResult<Void> forceLogoutByToken(@PathVariable String token) {
        onlineUserService.forceLogoutByToken(token);
        return ApiResult.success();
    }

    /**
     * Clean up expired online user entries.
     */
    @PreAuthorize("@authz.hasPermission('sys_online_user_view')")
    @DeleteMapping("/cleanup")
    public ApiResult<Void> cleanup() {
        onlineUserService.cleanupExpiredUsers();
        return ApiResult.success();
    }
}