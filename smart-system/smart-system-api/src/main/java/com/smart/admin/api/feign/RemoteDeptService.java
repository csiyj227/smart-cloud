package com.smart.admin.api.feign;

import com.smart.common.core.web.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import static com.smart.common.core.auth.AuthHeaders.SERVICE_CALL;

/**
 * Remote dept service for inter-service dept lookups.
 *
 * 远程部门服务，用于服务间部门查询。
 */
@FeignClient(contextId = "remoteDeptService", value = "smart-system")
public interface RemoteDeptService {

    @GetMapping("/system/dept/ancestor/{deptId}")
    ApiResult<Long> getAncestorDeptId(@PathVariable("deptId") Long deptId,
                              @RequestHeader(SERVICE_CALL) String from);
}