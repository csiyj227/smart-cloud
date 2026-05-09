package com.smart.admin.api.feign;

import com.smart.admin.api.dto.SysLogDTO;
import com.smart.common.core.web.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static com.smart.common.core.auth.AuthHeaders.SERVICE_CALL;

/**
 * Remote log service for async log persistence.
 *
 * 远程日志服务，用于异步日志持久化。
 */
@FeignClient(contextId = "remoteLogService", value = "smart-system")
public interface RemoteLogService {

    @PostMapping("/system/log")
    ApiResult<Void> saveLog(@RequestBody SysLogDTO logDTO,
                    @RequestHeader(SERVICE_CALL) String from);
}