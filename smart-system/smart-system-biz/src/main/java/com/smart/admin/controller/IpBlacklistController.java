package com.smart.admin.controller;

import com.smart.common.core.web.ApiResult;
import com.smart.common.security.component.IpBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * IP blacklist controller.
 *
 * IP 黑名单控制器。
 */
@Slf4j
@RestController
@RequestMapping("/system/ip-blacklist")
@RequiredArgsConstructor
public class IpBlacklistController {

    private final IpBlacklistService ipBlacklistService;

    /**
     * Get all blacklisted IPs.
     */
    @PreAuthorize("@authz.hasPermission('sys_ip_blacklist_view')")
    @GetMapping("/list")
    public ApiResult<List<String>> list() {
        return ApiResult.success(ipBlacklistService.getBlacklistedIps());
    }

    /**
     * Get all locked IPs.
     */
    @PreAuthorize("@authz.hasPermission('sys_ip_blacklist_view')")
    @GetMapping("/locked")
    public ApiResult<List<String>> getLockedIps() {
        return ApiResult.success(ipBlacklistService.getLockedIps());
    }

    /**
     * Add IP to blacklist.
     */
    @PreAuthorize("@authz.hasPermission('sys_ip_blacklist_add')")
    @PostMapping("/{ip}")
    public ApiResult<Void> addToBlacklist(
            @PathVariable String ip,
            @RequestParam(defaultValue = "1440") long durationMinutes) {
        ipBlacklistService.addToBlacklist(ip, durationMinutes);
        return ApiResult.success();
    }

    /**
     * Remove IP from blacklist.
     */
    @PreAuthorize("@authz.hasPermission('sys_ip_blacklist_del')")
    @DeleteMapping("/{ip}")
    public ApiResult<Void> removeFromBlacklist(@PathVariable String ip) {
        ipBlacklistService.removeFromBlacklist(ip);
        return ApiResult.success();
    }

    /**
     * Unlock IP.
     */
    @PreAuthorize("@authz.hasPermission('sys_ip_blacklist_unlock')")
    @PostMapping("/unlock/{ip}")
    public ApiResult<Void> unlock(@PathVariable String ip) {
        ipBlacklistService.unlockIp(ip);
        return ApiResult.success();
    }

    /**
     * Check if IP is blocked.
     */
    @GetMapping("/check/{ip}")
    public ApiResult<Map<String, Object>> check(@PathVariable String ip) {
        boolean blocked = ipBlacklistService.isBlocked(ip);
        long remainingLock = 0;
        if (blocked) {
            remainingLock = ipBlacklistService.getRemainingLockSeconds(ip);
        }
        return ApiResult.success(Map.of("blocked", blocked, "remainingLockSeconds", remainingLock));
    }
}