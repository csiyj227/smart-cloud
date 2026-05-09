package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_route_conf")
public class SysRouteConf extends AuditableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String routeName;
    private String routeId;
    private String predicates;
    private String filters;
    private String uri;
    private Integer sortOrder;
    private String status;
}