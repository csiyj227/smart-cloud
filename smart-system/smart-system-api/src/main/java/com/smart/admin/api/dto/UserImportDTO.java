package com.smart.admin.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;


/**
 * User import/export Excel DTO.
 *
 * 用户导入导出 Excel DTO。
 */
@Data
@HeadRowHeight(25)
@ContentRowHeight(20)
public class UserImportDTO {

    @ExcelProperty(value = "用户名", index = 0)
    @ColumnWidth(20)
    private String username;

    @ExcelProperty(value = "真实姓名", index = 1)
    @ColumnWidth(15)
    private String realName;

    @ExcelProperty(value = "手机号", index = 2)
    @ColumnWidth(15)
    private String phone;

    @ExcelProperty(value = "邮箱", index = 3)
    @ColumnWidth(25)
    private String email;

    @ExcelProperty(value = "部门ID", index = 4)
    @ColumnWidth(10)
    private Long deptId;

    @ExcelProperty(value = "岗位ID", index = 5)
    @ColumnWidth(10)
    private Long postId;

    @ExcelProperty(value = "用户类型", index = 6)
    @ColumnWidth(10)
    private String userType;

    @ExcelProperty(value = "状态(0正常 1停用)", index = 7)
    @ColumnWidth(15)
    private String status;

    @ExcelProperty(value = "密码", index = 8)
    @ColumnWidth(20)
    private String password;
}