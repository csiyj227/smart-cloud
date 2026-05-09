package com.smart.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.admin.api.dto.ClientDetailsDTO;
import com.smart.admin.entity.SysOauthClientDetails;
import com.smart.admin.service.SysOauthClientDetailsService;
import com.smart.common.core.web.ApiResult;
import com.smart.common.security.annotation.ServiceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/client")
@RequiredArgsConstructor
public class SysOauthClientDetailsController {

    private final SysOauthClientDetailsService sysOauthClientDetailsService;

    @ServiceApi
    @GetMapping("/{clientId}")
    public ApiResult<ClientDetailsDTO> getByClientId(@PathVariable String clientId) {
        SysOauthClientDetails client = sysOauthClientDetailsService.findByClientId(clientId);
        if (client == null) {
            return ApiResult.failure("Client not found");
        }
        ClientDetailsDTO dto = new ClientDetailsDTO();
        dto.setClientId(client.getClientId());
        dto.setClientSecret(client.getClientSecret());
        dto.setScope(client.getScope());
        dto.setAuthorizedGrantTypes(client.getAuthorizedGrantTypes());
        dto.setWebServerRedirectUri(client.getWebServerRedirectUri());
        dto.setAuthorities(client.getAuthorities());
        dto.setAccessTokenValidity(client.getAccessTokenValidity());
        dto.setRefreshTokenValidity(client.getRefreshTokenValidity());
        dto.setAdditionalInformation(client.getAdditionalInformation());
        dto.setAutoApprove(client.getAutoApprove());
        dto.setTenantId(client.getTenantId());
        return ApiResult.success(dto);
    }

    @PreAuthorize("@authz.hasPermission('sys_client_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysOauthClientDetails>> page(Page<SysOauthClientDetails> page) {
        return ApiResult.success(sysOauthClientDetailsService.page(page));
    }

    @PreAuthorize("@authz.hasPermission('sys_client_add')")
    @PostMapping
    public ApiResult<Void> save(@RequestBody SysOauthClientDetails client) {
        return sysOauthClientDetailsService.saveClient(client) ? ApiResult.success() : ApiResult.failure("保存失败");
    }

    @PreAuthorize("@authz.hasPermission('sys_client_edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysOauthClientDetails client) {
        return sysOauthClientDetailsService.updateClient(client) ? ApiResult.success() : ApiResult.failure("更新失败：客户端不存在或越权");
    }

    @PreAuthorize("@authz.hasPermission('sys_client_del')")
    @DeleteMapping("/{clientId}")
    public ApiResult<Void> delete(@PathVariable String clientId) {
        return sysOauthClientDetailsService.removeClient(clientId) ? ApiResult.success() : ApiResult.failure("删除失败：客户端不存在或越权");
    }
}