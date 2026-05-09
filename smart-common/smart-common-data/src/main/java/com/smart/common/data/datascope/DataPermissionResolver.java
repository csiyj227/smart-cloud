package com.smart.common.data.datascope;

import java.util.List;

/**
 * Interface for resolving data permission scope for the current user.
 * Implementations determine which departments and users the current
 * authenticated user is allowed to access based on their role configuration.
 *
 * 数据权限范围解析接口。
 * 实现类根据当前认证用户的角色配置，确定其可访问的部门和用户。
 */
public interface DataPermissionResolver {

    /**
     * Resolve the data permission for the currently authenticated user.
     * This is called by the DataPermissionInterceptor on every query.
     *
     * @return DataPermission object, or null if no filtering needed
     */
    DataPermission resolveCurrentPermission();

    /**
     * Resolve the list of department IDs the current user can access.
     *
     * @param roleId the role ID to resolve permissions for
     * @return list of accessible department IDs, or null for full access
     */
    List<Long> resolveDeptIds(Long roleId);

    /**
     * Get the current user's username for self-only data scope.
     */
    String getCurrentUsername();
}