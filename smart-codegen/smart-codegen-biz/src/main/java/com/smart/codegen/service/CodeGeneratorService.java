package com.smart.codegen.service;

import com.smart.codegen.entity.GenTable;

import java.util.List;
import java.util.Map;

/**
 * 代码生成核心引擎。
 *
 * <p>支持两种数据源：
 * <ul>
 *   <li>已有 {@link GenTable} 元数据：用于按预设配置生成</li>
 *   <li>从数据库 information_schema 即时导入：自动建立 {@link GenTable}/GenTableColumn 后再生成</li>
 * </ul>
 */
public interface CodeGeneratorService {

    /**
     * 根据 tableId 生成全部模板文件，返回 {fileRelativePath -> fileContent}。
     */
    Map<String, String> generateCode(Long tableId);

    /**
     * 根据 tableId 把生成结果打成 zip，返回 zip 字节数组。
     * 适合直接作为下载响应体。
     */
    byte[] generateZip(Long tableId);

    /**
     * 从数据库 information_schema 列出所有"未导入"的物理表。
     * 用于 UI 上"从数据库导入"功能的下拉选择。
     *
     * @param keyword 模糊匹配表名 / 表注释，可为 null
     */
    List<Map<String, Object>> listDatabaseTables(String keyword);

    /**
     * 从 information_schema 导入指定的物理表，自动写入 gen_table + gen_table_column。
     * 已存在的表会被跳过（按 table_name 唯一）。
     *
     * @return 成功导入的 GenTable id 列表
     */
    List<Long> importFromDatabase(List<String> tableNames);
}