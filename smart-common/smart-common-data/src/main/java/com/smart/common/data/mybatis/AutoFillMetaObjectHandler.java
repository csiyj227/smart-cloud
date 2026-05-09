package com.smart.common.data.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.smart.common.core.web.HttpRequestHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus auto-fill handler for audit fields.
 * Automatically populates createBy, createTime, updateBy, updateTime.
 *
 * MyBatis-Plus 审计字段自动填充处理器。
 * 自动填充 createBy、createTime、updateBy、updateTime。
 */
@Slf4j
@Component
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "createBy", String.class, getCurrentUsername());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateBy", String.class, getCurrentUsername());
        // 逻辑删除标记默认值（0=正常，1=已删除）
        // 用 fillStrategy 而非 strictInsertFill，避免覆盖业务方手动设置的值
        this.fillStrategy(metaObject, "delFlag", "0");
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateBy", String.class, getCurrentUsername());
    }

    private String getCurrentUsername() {
        try {
            return HttpRequestHelper.header("X-Username").orElse("system");
        } catch (Exception e) {
            return "system";
        }
    }
}