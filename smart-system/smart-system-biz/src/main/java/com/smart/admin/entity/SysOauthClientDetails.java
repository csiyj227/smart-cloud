package com.smart.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smart.common.core.annotation.TenantEntity;
import com.smart.common.data.domain.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OAuth2 client details entity for authorization server.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TenantEntity
@TableName("sys_oauth_client_details")
public class SysOauthClientDetails extends AuditableEntity {

    @TableId(type = IdType.INPUT)
    private String clientId;

    private String resourceIds;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String clientSecret;
    private String scope;
    private String authorizedGrantTypes;
    private String webServerRedirectUri;
    private String authorities;
    private Integer accessTokenValidity;
    private Integer refreshTokenValidity;
    private String additionalInformation;
    private String autoApprove;
    private Long tenantId;
}