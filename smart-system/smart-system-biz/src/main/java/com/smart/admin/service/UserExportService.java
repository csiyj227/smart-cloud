package com.smart.admin.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.admin.api.dto.UserImportDTO;
import com.smart.admin.entity.SysUser;
import com.smart.common.core.enums.StatusFlag;
import com.smart.common.core.tenant.TenantContext;
import com.smart.common.security.service.SmartUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User Excel import/export service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserExportService {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Export users to Excel.
     */
    public void export(HttpServletResponse response, SysUser query) {
        try {
            Long tenantId = TenantContext.get().orElse(null);
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
            if (query.getUsername() != null) {
                wrapper.like(SysUser::getUsername, query.getUsername());
            }
            if (query.getStatus() != null) {
                wrapper.eq(SysUser::getStatus, query.getStatus());
            }
            if (query.getDeptId() != null) {
                wrapper.eq(SysUser::getDeptId, query.getDeptId());
            }
            List<SysUser> users = sysUserService.list(wrapper);

            List<UserImportDTO> exportData = users.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            // Set response headers
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = "用户列表_" + System.currentTimeMillis();
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream(), UserImportDTO.class).build();
            WriteSheet writeSheet = EasyExcel.writerSheet("用户列表").build();
            excelWriter.write(exportData, writeSheet);
            excelWriter.finish();

            log.info("Exported {} users for tenant {}", users.size(), tenantId);
        } catch (IOException e) {
            log.error("Failed to export users", e);
            throw new RuntimeException("Failed to export users", e);
        }
    }

    /**
     * Import users from Excel.
     *
     * @param file    Excel file
     * @param update  whether to update existing users
     * @return import result with success/failure counts
     */
    public ImportResult importUser(MultipartFile file, boolean update) {
        try {
            List<UserImportDTO> dataList = EasyExcel.read(file.getInputStream())
                    .head(UserImportDTO.class)
                    .sheet()
                    .doReadSync();

            if (dataList.isEmpty()) {
                return new ImportResult(0, 0, List.of("Excel file is empty"));
            }

            Long tenantId = TenantContext.get().orElse(null);
            List<String> errors = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;

            for (int i = 0; i < dataList.size(); i++) {
                UserImportDTO dto = dataList.get(i);
                try {
                    SysUser user = toEntity(dto, tenantId);

                    // Check if user exists
                    SysUser existing = sysUserService.findByUsernameAndTenant(dto.getUsername(), tenantId);
                    if (existing != null) {
                        if (update) {
                            user.setUserId(existing.getUserId());
                            user.setPassword(existing.getPassword()); // Keep original password
                            sysUserService.updateById(user);
                            successCount++;
                        } else {
                            failureCount++;
                            errors.add("Row " + (i + 2) + ": User already exists - " + dto.getUsername());
                        }
                    } else {
                        // Encode password
                        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
                            user.setPassword(passwordEncoder.encode(dto.getPassword()));
                        } else {
                            user.setPassword(passwordEncoder.encode("123456")); // Default password
                        }
                        sysUserService.save(user);
                        successCount++;
                    }
                } catch (Exception e) {
                    failureCount++;
                    errors.add("Row " + (i + 2) + ": " + e.getMessage());
                    log.error("Failed to import user at row {}: {}", i + 2, dto.getUsername(), e);
                }
            }

            log.info("Import completed for tenant {}: success={}, failure={}", tenantId, successCount, failureCount);
            return new ImportResult(successCount, failureCount, errors);
        } catch (IOException e) {
            log.error("Failed to read Excel file", e);
            throw new RuntimeException("Failed to read Excel file", e);
        }
    }

    /**
     * Download template Excel file.
     */
    public void downloadTemplate(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=用户导入模板.xlsx");

            List<UserImportDTO> template = List.of(new UserImportDTO());

            ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream(), UserImportDTO.class).build();
            WriteSheet writeSheet = EasyExcel.writerSheet("用户导入模板").build();
            excelWriter.write(template, writeSheet);
            excelWriter.finish();
        } catch (IOException e) {
            log.error("Failed to download template", e);
            throw new RuntimeException("Failed to download template", e);
        }
    }

    private UserImportDTO toDTO(SysUser user) {
        UserImportDTO dto = new UserImportDTO();
        dto.setUsername(user.getUsername());
        dto.setRealName(user.getRealName());
        dto.setPhone(user.getPhone());
        dto.setEmail(user.getEmail());
        dto.setDeptId(user.getDeptId());
        dto.setPostId(user.getPostId());
        dto.setUserType(user.getUserType());
        dto.setStatus(user.getStatus());
        // Don't export password
        return dto;
    }

    private SysUser toEntity(UserImportDTO dto, Long tenantId) {
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setDeptId(dto.getDeptId());
        user.setPostId(dto.getPostId());
        user.setUserType(dto.getUserType());
        user.setStatus(dto.getStatus() != null ? dto.getStatus() : StatusFlag.ENABLED.getValue());
        user.setTenantId(tenantId);
        return user;
    }

    public record ImportResult(int successCount, int failureCount, List<String> errors) {}
}