package com.smart.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.admin.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Audit log mapper.
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}