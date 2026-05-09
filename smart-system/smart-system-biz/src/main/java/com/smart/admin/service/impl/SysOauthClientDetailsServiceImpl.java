package com.smart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.admin.entity.SysOauthClientDetails;
import com.smart.admin.mapper.SysOauthClientDetailsMapper;
import com.smart.admin.service.SysOauthClientDetailsService;
import com.smart.common.core.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;

/**
 * OAuth2 客户端管理 Service 实现。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>多租户管理隔离</b>：所有写操作都强制以当前 {@code TenantContext} 中的 tenantId
 *       作为归属，跨租户更新 / 删除会被识别为越权并拒绝。
 *       注意：OAuth2 协议规定 client_id 在认证阶段无法携带租户信息，因此 client_id
 *       仍要求<b>全局唯一</b>，租户隔离仅作用于"管理面"。</li>
 *   <li><b>缓存联动</b>：通过 {@link RegisteredClientRepository}（当前为
 *       {@code SmartJdbcRegisteredClientRepository}）的 reflective evict 让 auth 端缓存
 *       立即失效。Bean 通过 {@link ObjectProvider} 注入，避免 upms 强依赖 auth 端实现类
 *       （单体部署时可获取到，微服务拆分时也不会编译失败）。</li>
 * </ul>
 */
@Slf4j
@Service
public class SysOauthClientDetailsServiceImpl extends ServiceImpl<SysOauthClientDetailsMapper, SysOauthClientDetails>
        implements SysOauthClientDetailsService {

    private final ObjectProvider<RegisteredClientRepository> registeredClientRepositoryProvider;

    public SysOauthClientDetailsServiceImpl(
            ObjectProvider<RegisteredClientRepository> registeredClientRepositoryProvider) {
        this.registeredClientRepositoryProvider = registeredClientRepositoryProvider;
    }

    @Override
    public SysOauthClientDetails findByClientId(String clientId) {
        return getOne(new LambdaQueryWrapper<SysOauthClientDetails>()
                .eq(SysOauthClientDetails::getClientId, clientId));
    }

    @Override
    public boolean saveClient(SysOauthClientDetails client) {
        // 强制绑定当前租户
        Long tenantId = TenantContext.get().orElse(null);
        if (tenantId != null) {
            client.setTenantId(tenantId);
        }
        boolean ok = save(client);
        if (ok) {
            evictRegisteredClientCache(client.getClientId());
        }
        return ok;
    }

    @Override
    public boolean updateClient(SysOauthClientDetails client) {
        SysOauthClientDetails existing = getById(client.getClientId());
        if (existing == null) {
            return false;
        }
        ensureTenantMatch(existing);
        // 不允许通过 update 改变租户归属
        client.setTenantId(existing.getTenantId());
        boolean ok = updateById(client);
        if (ok) {
            evictRegisteredClientCache(client.getClientId());
        }
        return ok;
    }

    @Override
    public boolean removeClient(String clientId) {
        SysOauthClientDetails existing = getById(clientId);
        if (existing == null) {
            return false;
        }
        ensureTenantMatch(existing);
        boolean ok = removeById(clientId);
        if (ok) {
            evictRegisteredClientCache(clientId);
        }
        return ok;
    }

    /**
     * 校验当前租户上下文与目标 client 的归属一致；超管（无租户上下文）放行。
     */
    private void ensureTenantMatch(SysOauthClientDetails client) {
        Long current = TenantContext.get().orElse(null);
        if (current == null) {
            return; // 内部调用 / 平台超管不限制
        }
        if (client.getTenantId() != null && !current.equals(client.getTenantId())) {
            throw new IllegalStateException("不允许跨租户操作 OAuth Client: " + client.getClientId());
        }
    }

    /**
     * 反射式失效 RegisteredClientRepository 的缓存。
     *
     * <p>使用反射调用 {@code evictCache(String)} 方法，避免 upms 模块强依赖
     * smart-auth 的 {@code SmartJdbcRegisteredClientRepository} 类。
     * 单体部署下能拿到 Bean，微服务拆分时此 Bean 不存在，会被静默跳过。
     */
    private void evictRegisteredClientCache(String clientId) {
        RegisteredClientRepository repo = registeredClientRepositoryProvider.getIfAvailable();
        if (repo == null || clientId == null) {
            return;
        }
        try {
            repo.getClass().getMethod("evictCache", String.class).invoke(repo, clientId);
            log.debug("Evicted RegisteredClient cache for clientId={}", clientId);
        } catch (NoSuchMethodException e) {
            // 不是 Smart 自定义实现，跳过
            log.debug("RegisteredClientRepository {} has no evictCache method, skip", repo.getClass().getName());
        } catch (ReflectiveOperationException e) {
            log.warn("Failed to evict RegisteredClient cache for clientId={}: {}", clientId, e.getMessage());
        }
    }
}