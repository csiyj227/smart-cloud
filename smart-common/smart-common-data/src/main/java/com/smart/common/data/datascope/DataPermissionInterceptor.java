package com.smart.common.data.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

/**
 * MyBatis-Plus data permission handler.
 * Appends row-level WHERE conditions based on the current user's data scope.
 *
 * Data scope types (dsType in sys_role):
 * 0 = ALL:        no filtering
 * 1 = CUSTOM:     filter by custom dept IDs from sys_role_dept
 * 2 = DEPT:       filter by user's own dept_id only
 * 3 = DEPT_AND_CHILD: filter by user's dept and all child depts
 * 4 = SELF:       filter by create_by = current username
 *
 * MyBatis-Plus 数据权限处理器。
 * 根据当前用户的数据范围追加行级 WHERE 条件。
 *
 * 数据范围类型（sys_role 中的 dsType）：
 * 0 = ALL：        不过滤
 * 1 = CUSTOM：     按 sys_role_dept 中的自定义部门 ID 过滤
 * 2 = DEPT：       仅按用户所在部门 dept_id 过滤
 * 3 = DEPT_AND_CHILD：按用户部门及所有子部门过滤
 * 4 = SELF：       按 create_by = 当前用户名 过滤
 */
@Slf4j
@RequiredArgsConstructor
public class DataPermissionInterceptor implements DataPermissionHandler {

    private final ObjectProvider<DataPermissionResolver> resolverProvider;

    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> false);

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        if (RESOLVING.get()) {
            return where;
        }

        // Skip data permission for association / junction tables that lack audit columns
        if (shouldSkip(mappedStatementId)) {
            return where;
        }

        try {
            DataPermissionResolver permissionResolver = resolverProvider.getIfAvailable();
            if (permissionResolver == null) {
                return where;
            }
            RESOLVING.set(true);
            DataPermission permission = permissionResolver.resolveCurrentPermission();
            if (permission == null || permission.getFunc() == null || permission.getFunc() == DataPermission.PermissionFunc.ALL) {
                return where;
            }

            Expression scopeExpression = buildScopeExpression(permission, null);
            if (scopeExpression == null) {
                return where;
            }

            if (where == null) {
                return scopeExpression;
            }

            return new AndExpression(where, scopeExpression);
        } catch (Exception e) {
            // No security context available (startup, system queries) — skip filtering
            log.trace("Data permission skipped for {}: {}", mappedStatementId, e.getMessage());
            return where;
        } finally {
            RESOLVING.remove();
        }
    }

    /**
     * Build a data scope expression for the given permission configuration.
     */
    public Expression buildScopeExpression(DataPermission permission, String tableAlias) {
        if (permission == null || permission.getFunc() == null) {
            return null;
        }

        String alias = tableAlias != null ? tableAlias + "." : "";

        return switch (permission.getFunc()) {
            case ALL -> null;
            case DEPT, DEPT_AND_CHILD, CUSTOM -> buildDeptExpression(permission, alias);
            case SELF -> buildSelfExpression(permission, alias);
        };
    }

    private Expression buildDeptExpression(DataPermission permission, String alias) {
        List<Long> deptIds = permission.getDeptIds();
        if (deptIds == null || deptIds.isEmpty()) {
            EqualsTo impossible = new EqualsTo();
            impossible.setLeftExpression(new Column(alias + permission.getDeptColumnName()));
            impossible.setRightExpression(new LongValue(-1));
            return impossible;
        }

        Column deptColumn = new Column(alias + permission.getDeptColumnName());

        if (deptIds.size() == 1) {
            EqualsTo eq = new EqualsTo();
            eq.setLeftExpression(deptColumn);
            eq.setRightExpression(new LongValue(deptIds.get(0)));
            return eq;
        }

        InExpression in = new InExpression();
        in.setLeftExpression(deptColumn);
        ExpressionList<Expression> idList = new ParenthesedExpressionList<>();
        for (Long deptId : deptIds) {
            idList.add(new LongValue(deptId));
        }
        in.setRightExpression(idList);
        return in;
    }

    private Expression buildSelfExpression(DataPermission permission, String alias) {
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(alias + permission.getUserColumnName()));
        eq.setRightExpression(new StringValue(permission.getUsername()));
        return eq;
    }

    /**
     * Determine if the mapped statement should skip data permission filtering.
     * Association / junction tables (e.g. sys_user_role, sys_role_menu, sys_role_dept)
     * do not have create_by / dept_id columns, so appending data scope conditions
     * would cause SQL errors.
     */
    private boolean shouldSkip(String mappedStatementId) {
        if (mappedStatementId == null) {
            return false;
        }
        return mappedStatementId.contains("UserRoleMapper")
                || mappedStatementId.contains("RoleMenuMapper")
                || mappedStatementId.contains("RoleDeptMapper");
    }
}
