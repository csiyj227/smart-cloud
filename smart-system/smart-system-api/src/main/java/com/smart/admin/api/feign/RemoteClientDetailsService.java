package com.smart.admin.api.feign;

import com.smart.admin.api.dto.ClientDetailsDTO;
import com.smart.common.core.web.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import static com.smart.common.core.auth.AuthHeaders.SERVICE_CALL;

/**
 * Remote client details service for OAuth2 client lookups.
 *
 * 远程客户端详情服务，用于 OAuth2 客户端查询。
 */
@FeignClient(contextId = "remoteClientDetailsService", value = "smart-system")
public interface RemoteClientDetailsService {

    @GetMapping("/system/client/{clientId}")
    ApiResult<ClientDetailsDTO> getClientDetails(@PathVariable("clientId") String clientId,
                                         @RequestHeader(SERVICE_CALL) String from);
}