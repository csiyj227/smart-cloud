package com.smart.admin.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * User info DTO returned by RemoteUserService for auth server consumption.
 *
 * 用户信息 DTO，由 RemoteUserService 返回供认证服务器使用。
 */
@Data
public class UserInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String password;
    private String realName;
    private String phone;
    /** 邮箱：和 phone / avatar 一样属于「用户可自助修改」字段，必须从 DB 实时读避免 JWT 缓存。 */
    private String email;
    private String avatar;
    private Long deptId;
    private Long postId;
    private Long tenantId;
    private String userType;
    private String lockFlag;
    private Set<String> roles;
    private Set<String> permissions;
}