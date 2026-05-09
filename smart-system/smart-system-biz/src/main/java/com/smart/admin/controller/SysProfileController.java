package com.smart.admin.controller;

import com.smart.admin.api.dto.AvatarForm;
import com.smart.admin.api.dto.ChangePasswordForm;
import com.smart.admin.api.dto.ProfileForm;
import com.smart.admin.entity.SysDept;
import com.smart.admin.entity.SysUser;
import com.smart.admin.service.SysDeptService;
import com.smart.admin.service.SysUserService;
import com.smart.admin.support.JwtClaimUtils;
import com.smart.common.core.web.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户「个人中心」相关接口。
 *
 * <p>独立成 controller 是为了：
 * 1. 与 /user/** 的「管理员管理用户」接口物理隔离 —— 后者依赖 sys_user_view/edit 等权限注解，
 *    而个人中心是「自己改自己」，仅需登录态，不需要任何业务权限；
 * 2. 路径语义清晰：所有个人维度操作都在 /profile 前缀下，便于权限网关单独配置（如限频、审计）。
 *
 * <p>所有方法的目标用户固定从 JWT 的 {@code user_id} claim 解析，禁止接受 path/body 中的 userId
 * 入参，从根本上杜绝越权改他人资料的可能。
 */
@RestController
@RequestMapping("/system/profile")
@RequiredArgsConstructor
public class SysProfileController {

    private final SysUserService sysUserService;
    private final SysDeptService sysDeptService;

    /**
     * 查询当前登录用户的完整档案（用于个人中心首页展示）。
     * 与 /user/info 不同的是这里直接返回数据库实体，包含 createTime、deptId 等额外信息。
     */
    @GetMapping
    public ApiResult<SysUser> getProfile(Authentication authentication) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        if (userId == null) {
            // 提示文案目前先走中文硬编码，等接入 i18n（MessageSource + Accept-Language）后
            // 改成 ApiResult.failure(messageSource.getMessage("profile.current.not.found", ...)) 即可。
            return ApiResult.failure("当前用户登录态异常");
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return ApiResult.failure("用户不存在");
        }
        // 关联部门名称：前端展示需要 deptName 而非裸 deptId。
        // 这里直接 getById 一次查表，QPS 低且 dept 数据量小，无需缓存。
        if (user.getDeptId() != null) {
            SysDept dept = sysDeptService.getById(user.getDeptId());
            if (dept != null) {
                user.setDeptName(dept.getDeptName());
            }
        }
        // 出于安全考虑，password 已通过 @JsonProperty(WRITE_ONLY) 在序列化时被剔除
        return ApiResult.success(user);
    }

    /**
     * 修改基本资料：仅 realName / phone / email / avatar，其余字段一律忽略。
     */
    @PutMapping
    public ApiResult<Void> updateProfile(Authentication authentication,
                                 @Valid @RequestBody ProfileForm form) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        if (userId == null) {
            return ApiResult.failure("当前用户登录态异常");
        }
        sysUserService.updateProfile(userId, form);
        return ApiResult.success();
    }

    /**
     * 修改密码：旧密码校验失败时返回业务错误码，不抛异常（避免被 GlobalExceptionHandler 兜成 500）。
     */
    @PutMapping("/password")
    public ApiResult<Void> changePassword(Authentication authentication,
                                  @Valid @RequestBody ChangePasswordForm form) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        if (userId == null) {
            return ApiResult.failure("当前用户登录态异常");
        }
        if (form.getOldPassword().equals(form.getNewPassword())) {
            return ApiResult.failure("新密码不能与旧密码相同");
        }
        boolean ok = sysUserService.changePassword(userId, form.getOldPassword(), form.getNewPassword());
        if (!ok) {
            return ApiResult.failure("旧密码不正确");
        }
        return ApiResult.success();
    }

    /**
     * 单独的头像更新接口。前端上传图片到 /file/upload 拿到 URL 后，调本接口写回 user.avatar。
     * 之所以独立出来，是为了让头像保存与基本资料保存解耦——典型场景下用户只换头像不改其他字段。
     */
    @PostMapping("/avatar")
    public ApiResult<Void> updateAvatar(Authentication authentication,
                                @Valid @RequestBody AvatarForm form) {
        Long userId = JwtClaimUtils.getLong(authentication, "user_id");
        if (userId == null) {
            return ApiResult.failure("当前用户登录态异常");
        }
        sysUserService.updateAvatar(userId, form.getAvatar());
        return ApiResult.success();
    }
}
