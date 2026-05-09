package com.smart.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.admin.entity.SysOauthClientDetails;

/**
 * OAuth2 客户端管理 Service。
 *
 * <p>对于增删改操作，实现类需要负责：
 * <ul>
 *   <li>多租户隔离：保存时强制写入当前租户的 tenant_id，更新/删除时校验 tenant_id 匹配</li>
 *   <li>缓存失效：调用 {@code SmartJdbcRegisteredClientRepository#evictCache(clientId)}
 *       让 auth 模块的 RegisteredClient 缓存立刻失效，新配置下次请求即生效</li>
 * </ul>
 */
public interface SysOauthClientDetailsService extends IService<SysOauthClientDetails> {

    /**
     * 根据 clientId 查询（不做租户过滤，供 auth 模块的 RegisteredClientRepository 使用）。
     */
    SysOauthClientDetails findByClientId(String clientId);

    /**
     * 后台保存（自动绑定当前租户 + 失效缓存）。
     */
    boolean saveClient(SysOauthClientDetails client);

    /**
     * 后台更新（校验租户归属 + 失效缓存）。
     */
    boolean updateClient(SysOauthClientDetails client);

    /**
     * 后台删除（校验租户归属 + 失效缓存）。
     */
    boolean removeClient(String clientId);
}