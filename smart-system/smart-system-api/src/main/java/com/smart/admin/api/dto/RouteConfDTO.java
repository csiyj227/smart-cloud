package com.smart.admin.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Route configuration DTO for gateway route lookups.
 *
 * 路由配置 DTO，用于网关路由查询。
 */
@Data
public class RouteConfDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String routeName;
    private String routeId;
    private String predicates;
    private String filters;
    private String uri;
    private Integer sortOrder;
    private String status;
}