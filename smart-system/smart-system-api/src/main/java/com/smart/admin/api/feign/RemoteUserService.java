package com.smart.admin.api.feign;

import com.smart.admin.api.dto.UserInfo;
import com.smart.common.core.web.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import static com.smart.common.core.auth.AuthHeaders.SERVICE_CALL;
import static com.smart.common.core.auth.AuthHeaders.TENANT_ID;

/**
 * Remote user service for inter-service user lookups.
 * Used by the auth server to load user details during authentication.
 *
 * 远程用户服务，用于服务间用户查询。
 * 认证服务器在认证过程中使用此服务加载用户详情。
 */
@FeignClient(contextId = "remoteUserService", value = "smart-system")
public interface RemoteUserService {

    @GetMapping("/system/user/info/{username}")
    ApiResult<UserInfo> info(@PathVariable("username") String username,
                     @RequestHeader(TENANT_ID) Long tenantId,
                     @RequestHeader(SERVICE_CALL) String from);

    @GetMapping("/system/user/info/phone/{phone}")
    ApiResult<UserInfo> infoByPhone(@PathVariable("phone") String phone,
                            @RequestHeader(TENANT_ID) Long tenantId,
                            @RequestHeader(SERVICE_CALL) String from);
}