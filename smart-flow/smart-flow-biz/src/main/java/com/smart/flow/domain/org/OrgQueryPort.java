package com.smart.flow.domain.org;

import java.util.List;

/**
 * Outbound port for organisational data queries that the assignee-resolution domain needs.
 *
 * <p>This interface lives in the {@code domain} layer on purpose: it is a pure contract owned
 * by the workflow domain and describes <em>what</em> data the resolvers require, completely
 * independently of <em>where</em> that data physically lives. Concrete adapters in the
 * {@code infrastructure} layer wire it to the real source of truth - currently a Feign client
 * to {@code smart-system}, but this could just as well be a local cache, a GraphQL gateway, or
 * an in-memory fake for tests.
 *
 * <p>Adopting a Port-Adapter split here is the main place where smart-flow consciously
 * diverges from the reference implementations:
 * <ul>
 *   <li>calls {@code sysUserService} directly inside resolution helpers, which makes
 *       the resolution logic untestable without a full Spring context;</li>
 *   <li>uses {@code SpringUtil.getBean(RemoteUserService.class)} from static helpers,
 *       which couples the domain to the Feign client even in unit tests.</li>
 * </ul>
 * In smart-flow, every resolver depends on this port and nothing else from the user/org
 * world.
 */
public interface OrgQueryPort {

    /**
     * Returns the user ids that hold any of the given role codes within the active tenant.
     * The order of the returned list is not guaranteed; callers that need stable ordering
     * must sort explicitly.
     */
    List<Long> findUserIdsByRoleCodes(List<String> roleCodes, Long tenantId);

    /**
     * Returns the user ids belonging to any of the given departments. When
     * {@code includeChildren} is {@code true}, members of descendant departments are also
     * included (the adapter is responsible for descending the dept tree).
     */
    List<Long> findUserIdsByDeptIds(List<Long> deptIds, boolean includeChildren, Long tenantId);

    /**
     * Returns the leader user id for {@code userId}, walking up the management chain
     * {@code level} times (1 = direct manager, 2 = manager's manager, ...). Returns
     * {@code null} when the chain is shorter than requested.
     */
    Long findLeaderUserId(Long userId, int level, Long tenantId);
}
