package com.smart.common.data.datascope;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Data permission parameter object.
 * Passed into mapper methods to trigger the DataPermissionInterceptor.
 *
 * The interceptor reads this object from method parameters and appends
 * appropriate WHERE conditions based on the current user's data scope.
 *
 * 数据权限参数对象。
 * 传入 Mapper 方法以触发 DataPermissionInterceptor 数据权限拦截器。
 *
 * 拦截器从方法参数中读取此对象，并根据当前用户的数据范围追加相应的 WHERE 条件。
 */
@Data
@Accessors(chain = true)
public class DataPermission {

    /**
     * The column name for department-based filtering. Default: dept_id
     */
    private String deptColumnName = "dept_id";

    /**
     * The column name for user-based filtering. Default: create_by
     */
    private String userColumnName = "create_by";

    /**
     * List of department IDs the current user is allowed to see.
     * Populated by the DataPermissionInterceptor based on role configuration.
     */
    private List<Long> deptIds;

    /**
     * Username for self-only data scope.
     */
    private String username;

    /**
     * Whether to only show the user's own data (ignoring department scope).
     */
    private boolean selfOnly = false;

    /**
     * Data permission function type.
     */
    private PermissionFunc func = PermissionFunc.ALL;

    /**
     * Permission function types.
     */
    public enum PermissionFunc {
        ALL,
        DEPT,
        DEPT_AND_CHILD,
        SELF,
        CUSTOM
    }
}