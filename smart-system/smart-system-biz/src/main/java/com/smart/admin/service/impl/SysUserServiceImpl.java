package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.api.dto.ProfileForm;
import com.smart.admin.entity.SysUser;
import com.smart.admin.entity.SysUserRole;
import com.smart.admin.mapper.SysMenuMapper;
import com.smart.admin.mapper.SysUserMapper;
import com.smart.admin.mapper.SysUserRoleMapper;
import com.smart.admin.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public SysUser findByUsernameAndTenant(String username, Long tenantId) {
        return getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getTenantId, tenantId));
    }

    @Override
    public SysUser findByPhoneAndTenant(String phone, Long tenantId) {
        return getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .eq(SysUser::getTenantId, tenantId));
    }

    @Override
    public Set<String> getRoleCodesByUserId(Long userId) {
        List<String> roleCodes = baseMapper.selectRoleCodesByUserId(userId);
        return new HashSet<>(roleCodes != null ? roleCodes : Collections.emptyList());
    }

    @Override
    public Set<String> getPermissionsByUserId(Long userId) {
        List<Long> roleIds = baseMapper.selectRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<String> permissions = menuMapper.selectPermissionsByRoleIds(roleIds);
        return new HashSet<>(permissions != null ? permissions : Collections.emptyList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserWithRoles(SysUser user, List<Long> roleIds) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        save(user);
        saveUserRoles(user.getUserId(), roleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserWithRoles(SysUser user, List<Long> roleIds) {
        // P1-04: Don't overwrite password with null when updating
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            SysUser existing = getById(user.getUserId());
            user.setPassword(existing.getPassword());
        } else {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        updateById(user);
        // P1-01: Save user-role associations
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, user.getUserId()));
        saveUserRoles(user.getUserId(), roleIds);
    }

    @Override
    public List<Long> getRoleIdsByUserId(Long userId) {
        return userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
        ).stream().map(SysUserRole::getRoleId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String rawPassword) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserWithRoles(Long userId) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        removeById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, ProfileForm form) {
        SysUser existing = getById(userId);
        if (existing == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        // 只覆盖白名单字段；username / password / status / 角色等敏感字段一律不动
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setRealName(form.getRealName());
        update.setPhone(form.getPhone());
        update.setEmail(form.getEmail());
        update.setAvatar(form.getAvatar());
        updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changePassword(Long userId, String oldRawPassword, String newRawPassword) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        // 1. 校验旧密码：不匹配直接返回 false，让 controller 给出业务错误，避免抛异常污染日志
        if (!passwordEncoder.matches(oldRawPassword, user.getPassword())) {
            return false;
        }
        // 2. 防呆：新旧密码相同就没必要更新（也避免无意义的 passwordUpdateTime 漂移）
        if (passwordEncoder.matches(newRawPassword, user.getPassword())) {
            return true;
        }
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setPassword(passwordEncoder.encode(newRawPassword));
        update.setPasswordUpdateTime(LocalDateTime.now());
        updateById(update);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAvatar(Long userId, String avatarUrl) {
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setAvatar(avatarUrl);
        updateById(update);
    }
}