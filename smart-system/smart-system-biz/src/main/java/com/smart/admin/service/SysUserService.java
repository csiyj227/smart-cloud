package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.api.dto.ProfileForm;
import com.smart.admin.entity.SysUser;

import java.util.List;
import java.util.Set;

/**
 * System user service.
 */
public interface SysUserService extends IService<SysUser> {

    SysUser findByUsernameAndTenant(String username, Long tenantId);

    SysUser findByPhoneAndTenant(String phone, Long tenantId);

    Set<String> getRoleCodesByUserId(Long userId);

    Set<String> getPermissionsByUserId(Long userId);

    void saveUserWithRoles(SysUser user, List<Long> roleIds);

    void updateUserWithRoles(SysUser user, List<Long> roleIds);

    List<Long> getRoleIdsByUserId(Long userId);

    void resetPassword(Long userId, String rawPassword);

    void saveUserRoles(Long userId, List<Long> roleIds);

    void deleteUserWithRoles(Long userId);

    /**
     * 当前用户自助更新基本资料（realName/phone/email/avatar）。
     * 不允许修改 username/status/deptId/roles 等敏感字段，避免越权。
     */
    void updateProfile(Long userId, ProfileForm form);

    /**
     * 当前用户自助修改密码：先校验旧密码，再写入新密码（BCrypt 加密）。
     *
     * @return true 表示修改成功，false 表示旧密码不匹配
     */
    boolean changePassword(Long userId, String oldRawPassword, String newRawPassword);

    /**
     * 仅更新头像 URL。
     */
    void updateAvatar(Long userId, String avatarUrl);
}