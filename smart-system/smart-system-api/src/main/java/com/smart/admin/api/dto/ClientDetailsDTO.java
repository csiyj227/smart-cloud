package com.smart.admin.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * OAuth2 client details DTO for inter-service lookups.
 *
 * OAuth2 客户端详情 DTO，用于服务间查询。
 */
@Data
public class ClientDetailsDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String clientId;
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