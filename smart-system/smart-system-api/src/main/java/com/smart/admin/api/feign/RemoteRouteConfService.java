package com.smart.admin.api.feign;

import com.smart.admin.api.dto.RouteConfDTO;
import com.smart.common.core.web.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

import static com.smart.common.core.auth.AuthHeaders.SERVICE_CALL;

/**
 * Remote route configuration service for gateway route lookups.
 *
 * 远程路由配置服务，用于网关路由查询。
 */
@FeignClient(contextId = "remoteRouteConfService", value = "smart-system")
public interface RemoteRouteConfService {

    @GetMapping("/system/route/list")
    ApiResult<List<RouteConfDTO>> listRoutes(@RequestHeader(SERVICE_CALL) String from);
}