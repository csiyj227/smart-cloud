package com.smart.admin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.api.dto.UserInfo;
import com.smart.admin.api.dto.UserForm;
import com.smart.admin.api.dto.VerifyPasswordForm;
import com.smart.admin.entity.SysUser;
import com.smart.admin.service.SysUserService;
import com.smart.admin.service.UserExportService;
import com.smart.admin.support.JwtClaimUtils;
import com.smart.common.core.auth.AuthHeaders;
import com.smart.common.core.web.ApiResult;
import com.smart.common.log.annotation.AuditTrace;
import com.smart.common.security.annotation.ServiceApi;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;
    private final UserExportService userExportService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/info")
    public ApiResult<UserInfo> getCurrentUserInfo(Authentication authentication) {
        UserInfo info = new UserInfo();
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        info.setUserId(userId);
        info.setUsername(JwtClaimUtils.getString(authentication, "username", authentication.getName()));
        info.setDeptId(JwtClaimUtils.getLong(authentication, "dept_id"));
        info.setTenantId(JwtClaimUtils.getLong(authentication, "tenant_id"));

        // 关键：avatar / realName / phone / email 这几个「用户可自助修改」的字段必须从 DB 实时读，
        // 不能直接用 JWT claim——JWT 是登录时的快照，用户在「个人中心」改完头像/姓名后，
        // 不重新登录的话，token 里的旧值会让前端永远刷不出新头像。
        // 其余字段（roles/permissions/tenantId）继续走 JWT，省一次权限聚合查询。
        if (userId != null) {
            SysUser user = sysUserService.getById(userId);
            if (user != null) {
                info.setAvatar(user.getAvatar());
                info.setRealName(user.getRealName());
                info.setPhone(user.getPhone());
                info.setEmail(user.getEmail());
            }
        }
        // 兜底：DB 查不到时回退到 JWT，避免前端拿到 null 时直接崩
        if (info.getAvatar() == null) {
            info.setAvatar(JwtClaimUtils.getString(authentication, "avatar"));
        }
        if (info.getRealName() == null) {
            info.setRealName(JwtClaimUtils.getString(authentication, "real_name"));
        }
        if (info.getPhone() == null) {
            info.setPhone(JwtClaimUtils.getString(authentication, "phone"));
        }

        info.setRoles(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet()));
        info.setPermissions(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toSet()));
        return ApiResult.success(info);
    }

    @ServiceApi
    @GetMapping("/info/{username}")
    public ApiResult<UserInfo> info(@PathVariable String username,
                            @RequestHeader(AuthHeaders.TENANT_ID) Long tenantId) {
        SysUser user = sysUserService.findByUsernameAndTenant(username, tenantId);
        if (user == null) {
            return ApiResult.failure("User not found");
        }

        UserInfo info = new UserInfo();
        info.setUserId(user.getUserId());
        info.setUsername(user.getUsername());
        info.setPassword(user.getPassword());
        info.setRealName(user.getRealName());
        info.setPhone(user.getPhone());
        info.setAvatar(user.getAvatar());
        info.setDeptId(user.getDeptId());
        info.setPostId(user.getPostId());
        info.setTenantId(user.getTenantId());
        info.setUserType(user.getUserType());
        info.setLockFlag(user.getLockFlag());
        info.setRoles(sysUserService.getRoleCodesByUserId(user.getUserId()));
        info.setPermissions(sysUserService.getPermissionsByUserId(user.getUserId()));
        return ApiResult.success(info);
    }

    @ServiceApi
    @GetMapping("/info/phone/{phone}")
    public ApiResult<UserInfo> infoByPhone(@PathVariable String phone,
                                   @RequestHeader(AuthHeaders.TENANT_ID) Long tenantId) {
        SysUser user = sysUserService.findByPhoneAndTenant(phone, tenantId);
        if (user == null) {
            return ApiResult.failure("User not found");
        }
        return info(user.getUsername(), tenantId);
    }

    @PreAuthorize("@authz.hasPermission('sys_user_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysUser>> page(Page<SysUser> page, SysUser query) {
        return ApiResult.success(sysUserService.page(page, Wrappers.<SysUser>lambdaQuery()
                .like(query.getUsername() != null && !query.getUsername().isEmpty(), SysUser::getUsername, query.getUsername())
                .like(query.getRealName() != null && !query.getRealName().isEmpty(), SysUser::getRealName, query.getRealName())
                .like(query.getPhone() != null && !query.getPhone().isEmpty(), SysUser::getPhone, query.getPhone())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(), SysUser::getStatus, query.getStatus())
                .eq(query.getDeptId() != null && query.getDeptId() > 0, SysUser::getDeptId, query.getDeptId())));
    }

    @PreAuthorize("@authz.hasPermission('sys_user_view')")
    @GetMapping("/{userId}")
    public ApiResult<SysUser> getById(@PathVariable Long userId) {
        return ApiResult.success(sysUserService.getById(userId));
    }

    @PreAuthorize("@authz.hasPermission('sys_user_add')")
    @AuditTrace("新增用户")
    @PostMapping
    public ApiResult<Void> save(@Valid @RequestBody UserForm form) {
        SysUser user = new SysUser();
        BeanUtils.copyProperties(form, user);
        sysUserService.saveUserWithRoles(user, form.getRoleIds());
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_user_edit')")
    @AuditTrace("修改用户")
    @PutMapping
    public ApiResult<Void> update(@Valid @RequestBody UserForm form) {
        SysUser user = new SysUser();
        BeanUtils.copyProperties(form, user);
        sysUserService.updateUserWithRoles(user, form.getRoleIds());
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_user_del')")
    @AuditTrace("删除用户")
    @DeleteMapping("/{userId}")
    public ApiResult<Void> delete(@PathVariable Long userId) {
        sysUserService.deleteUserWithRoles(userId);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_user_edit')")
    @AuditTrace("重置密码")
    @PutMapping("/{userId}/password")
    public ApiResult<Void> resetPassword(@PathVariable Long userId, @RequestBody String rawPassword) {
        sysUserService.resetPassword(userId, rawPassword);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_user_edit')")
    @AuditTrace("分配用户角色")
    @PutMapping("/{userId}/roles")
    public ApiResult<Void> saveUserRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        sysUserService.saveUserRoles(userId, roleIds);
        return ApiResult.success();
    }

    @PreAuthorize("@authz.hasPermission('sys_user_view')")
    @GetMapping("/{userId}/roles")
    public ApiResult<List<Long>> getUserRoleIds(@PathVariable Long userId) {
        return ApiResult.success(sysUserService.getRoleIdsByUserId(userId));
    }

    /**
     * 锁屏解锁/敏感操作的密码校验接口。
     *
     * <p>必须接收 {@link VerifyPasswordForm} 而非 {@code @RequestBody String}：
     * 前端发的是 JSON {"password":"xxx"}，用 String 会把整段 JSON 字符串当成密码原文
     * 比对 BCrypt 哈希，永远返回 false（之前一直是这种 bug 状态）。
     */
    @PostMapping("/verify-password")
    public ApiResult<Boolean> verifyPassword(Authentication authentication,
                                     @Valid @RequestBody VerifyPasswordForm form) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        if (userId == null) {
            return ApiResult.success(false);
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return ApiResult.success(false);
        }
        return ApiResult.success(passwordEncoder.matches(form.getPassword(), user.getPassword()));
    }

    @PreAuthorize("@authz.hasPermission('sys_user_export')")
    @GetMapping("/export")
    public void export(HttpServletResponse response, SysUser query) {
        userExportService.export(response, query);
    }

    @PreAuthorize("@authz.hasPermission('sys_user_import')")
    @AuditTrace("导入用户")
    @PostMapping("/import")
    public ApiResult<UserExportService.ImportResult> importUser(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "update", defaultValue = "false") boolean update) {
        if (file.isEmpty()) {
            return ApiResult.failure("File is empty");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xls") && !filename.endsWith(".xlsx"))) {
            return ApiResult.failure("Invalid file format, only .xls and .xlsx are supported");
        }
        UserExportService.ImportResult result = userExportService.importUser(file, update);
        return ApiResult.success(result);
    }

    @PreAuthorize("@authz.hasPermission('sys_user_import')")
    @GetMapping("/import/template")
    public void downloadTemplate(HttpServletResponse response) {
        userExportService.downloadTemplate(response);
    }
}
