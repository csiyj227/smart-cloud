package com.smart.flow.infrastructure.org;

import com.smart.flow.domain.org.OrgQueryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link OrgQueryPort} implementation backed by Feign calls to {@code smart-system}.
 *
 * <p><strong>Status: scaffolding.</strong> The current {@code RemoteUserService} /
 * {@code RemoteDeptService} contracts in {@code smart-system-api} expose only a handful of
 * methods (single-user lookup by username and ancestor-dept lookup) - none of which match the
 * batch semantics we need here. Rather than blocking M2 on a cross-module API expansion, this
 * adapter currently:
 * <ol>
 *   <li>logs a warning whenever it is invoked, so deployments notice that org-driven
 *       resolvers are not yet wired;</li>
 *   <li>returns a safe empty result so the calling node falls through to its configured
 *       backup assignee (typically the process initiator);</li>
 *   <li>documents the exact Feign methods that need to be added to {@code smart-system-api}
 *       in the M2-final integration step (todo #11).</li>
 * </ol>
 *
 * <p>The methods needed on the upms side are:
 * <ul>
 *   <li>{@code GET /user/ids/by-role?codes=...} - batch user-ids by role codes;</li>
 *   <li>{@code GET /user/ids/by-dept?deptIds=...&includeChildren=...} - batch user-ids by
 *       department, with optional descendant inclusion;</li>
 *   <li>{@code GET /user/{userId}/leader?level=N} - walk the management chain.</li>
 * </ul>
 *
 * <p>Until those endpoints exist, customers can override this bean (it is not marked
 * {@code @Primary}; a user-supplied {@link OrgQueryPort} bean takes precedence) and inject
 * their own data source - for example, a direct read against {@code sys_user} via a shared
 * datasource - without touching the resolvers.
 */
@Slf4j
@Component
public class FeignOrgQueryAdapter implements OrgQueryPort {

    @Override
    public List<Long> findUserIdsByRoleCodes(List<String> roleCodes, Long tenantId) {
        warnNotImplemented("findUserIdsByRoleCodes", roleCodes);
        return List.of();
    }

    @Override
    public List<Long> findUserIdsByDeptIds(List<Long> deptIds, boolean includeChildren, Long tenantId) {
        warnNotImplemented("findUserIdsByDeptIds", deptIds);
        return List.of();
    }

    @Override
    public Long findLeaderUserId(Long userId, int level, Long tenantId) {
        warnNotImplemented("findLeaderUserId", userId);
        return null;
    }

    private void warnNotImplemented(String method, Object args) {
        log.warn("OrgQueryPort.{} called with {} but no upms Feign endpoint is wired yet; "
                + "returning empty result. See FeignOrgQueryAdapter javadoc for the contract "
                + "that smart-system-api must expose.", method, args);
    }
}
