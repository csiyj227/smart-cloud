package com.smart.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.codegen.entity.GenTable;
import com.smart.codegen.entity.GenTableColumn;
import com.smart.codegen.service.CodeGeneratorService;
import com.smart.codegen.service.GenTableColumnService;
import com.smart.codegen.service.GenTableService;
import com.smart.codegen.util.CodeGenUtils;
import com.smart.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成主流程。
 *
 * <p>设计要点：
 * <ul>
 *   <li><strong>模板内置在 classpath</strong>：{@code resources/templates/*.vm}，免去手工维护 DB 模板的成本</li>
 *   <li>每个模板对应一个固定的产物路径（在 {@link #TEMPLATE_REGISTRY} 里集中声明），路径里支持
 *       {@code ${packageName}/${className}} 这种 Velocity 占位符</li>
 *   <li>生成结果既可以以 {@code Map<filePath, content>} 形式返回（用于前端预览），
 *       也可以打包成 zip（用于一键下载）</li>
 *   <li>额外提供 {@link #listDatabaseTables}/{@link #importFromDatabase} 用于从 information_schema
 *       自动建立元数据，避免手工逐字段录入</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGeneratorServiceImpl implements CodeGeneratorService {

    private final GenTableService genTableService;
    private final GenTableColumnService genTableColumnService;
    private final VelocityEngine velocityEngine;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 模板路径 -> 输出路径表达式。
     * 路径表达式里的 ${...} 会经过 Velocity 渲染。
     */
    private static final Map<String, String> TEMPLATE_REGISTRY = new LinkedHashMap<>();

    static {
        // 后端 Java 文件
        TEMPLATE_REGISTRY.put("templates/backend/Entity.java.vm",
                "main/java/${packagePath}/${moduleName}/entity/${className}.java");
        TEMPLATE_REGISTRY.put("templates/backend/DTO.java.vm",
                "main/java/${packagePath}/${moduleName}/dto/${className}DTO.java");
        TEMPLATE_REGISTRY.put("templates/backend/VO.java.vm",
                "main/java/${packagePath}/${moduleName}/vo/${className}VO.java");
        TEMPLATE_REGISTRY.put("templates/backend/Query.java.vm",
                "main/java/${packagePath}/${moduleName}/query/${className}Query.java");
        TEMPLATE_REGISTRY.put("templates/backend/Mapper.java.vm",
                "main/java/${packagePath}/${moduleName}/mapper/${className}Mapper.java");
        TEMPLATE_REGISTRY.put("templates/backend/Mapper.xml.vm",
                "main/resources/mapper/${className}Mapper.xml");
        TEMPLATE_REGISTRY.put("templates/backend/Service.java.vm",
                "main/java/${packagePath}/${moduleName}/service/${className}Service.java");
        TEMPLATE_REGISTRY.put("templates/backend/ServiceImpl.java.vm",
                "main/java/${packagePath}/${moduleName}/service/impl/${className}ServiceImpl.java");
        TEMPLATE_REGISTRY.put("templates/backend/Controller.java.vm",
                "main/java/${packagePath}/${moduleName}/controller/${className}Controller.java");
        // 测试文件
        TEMPLATE_REGISTRY.put("templates/backend/ControllerTest.java.vm",
                "test/java/${packagePath}/${moduleName}/controller/${className}ControllerTest.java");
        // 前端
        TEMPLATE_REGISTRY.put("templates/frontend/index.vue.vm",
                "frontend/views/${moduleName}/${lowerClassName}/index.vue");
        TEMPLATE_REGISTRY.put("templates/frontend/api.ts.vm",
                "frontend/api/${lowerClassName}.ts");
        TEMPLATE_REGISTRY.put("templates/frontend/i18n.json.vm",
                "frontend/i18n/${lowerClassName}.json");
        // SQL 菜单
        TEMPLATE_REGISTRY.put("templates/sql/menu.sql.vm",
                "sql/${lowerClassName}_menu.sql");
    }

    @Override
    public Map<String, String> generateCode(Long tableId) {
        GenTable table = loadTable(tableId);
        List<GenTableColumn> columns = loadColumns(tableId);
        VelocityContext ctx = buildContext(table, columns);

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : TEMPLATE_REGISTRY.entrySet()) {
            String templatePath = entry.getKey();
            String pathExpr = entry.getValue();
            try {
                String rendered = renderTemplate(templatePath, ctx);
                String filePath = renderInline(pathExpr, ctx);
                result.put(filePath, rendered);
            } catch (ResourceNotFoundException e) {
                log.warn("Template not found in classpath, skipping: {}", templatePath);
            }
        }
        return result;
    }

    @Override
    public byte[] generateZip(Long tableId) {
        Map<String, String> files = generateCode(tableId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new BusinessException("Failed to build zip: " + e.getMessage());
        }
        return out.toByteArray();
    }

    @Override
    public List<Map<String, Object>> listDatabaseTables(String keyword) {
        // PostgreSQL information_schema：忽略系统 schema 与 codegen 自身的元数据表
        StringBuilder sql = new StringBuilder()
                .append("SELECT table_name, ")
                .append("       COALESCE(obj_description(c.oid, 'pg_class'), '') AS table_comment ")
                .append("FROM information_schema.tables t ")
                .append("LEFT JOIN pg_class c ON c.relname = t.table_name ")
                .append("WHERE t.table_schema NOT IN ('pg_catalog', 'information_schema') ")
                .append("  AND t.table_type = 'BASE TABLE' ")
                .append("  AND t.table_name NOT LIKE 'gen\\_%' ESCAPE '\\' ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append("  AND (t.table_name LIKE ? OR COALESCE(obj_description(c.oid, 'pg_class'), '') LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append("ORDER BY t.table_name");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> importFromDatabase(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }

        // 已存在的 table_name 直接跳过，避免重复导入
        List<GenTable> existing = genTableService.list(
                new LambdaQueryWrapper<GenTable>().in(GenTable::getTableName, tableNames));
        Set<String> existingNames = new HashSet<>();
        for (GenTable t : existing) {
            existingNames.add(t.getTableName());
        }

        List<Long> result = new ArrayList<>();
        for (String tableName : tableNames) {
            if (existingNames.contains(tableName)) {
                log.info("Table already imported, skip: {}", tableName);
                continue;
            }

            // 1. 拉表注释
            String tableComment = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(obj_description(?::regclass, 'pg_class'), '') ",
                    String.class, tableName);

            // 2. 拉列元数据
            List<Map<String, Object>> rawColumns = jdbcTemplate.queryForList(
                    "SELECT c.column_name, c.data_type, c.is_nullable, " +
                            "       COALESCE(col_description(format('%s.%s', c.table_schema, c.table_name)::regclass::oid, c.ordinal_position), '') AS column_comment, " +
                            "       (SELECT TRUE FROM information_schema.table_constraints tc " +
                            "         JOIN information_schema.key_column_usage kcu " +
                            "           ON tc.constraint_name = kcu.constraint_name " +
                            "        WHERE tc.constraint_type = 'PRIMARY KEY' " +
                            "          AND tc.table_name = c.table_name " +
                            "          AND kcu.column_name = c.column_name " +
                            "        LIMIT 1) AS is_pk " +
                            "FROM information_schema.columns c " +
                            "WHERE c.table_name = ? " +
                            "ORDER BY c.ordinal_position", tableName);

            if (rawColumns.isEmpty()) {
                log.warn("No columns found for table {}, skip import", tableName);
                continue;
            }

            // 3. 落库 GenTable
            GenTable table = new GenTable();
            table.setTableName(tableName);
            table.setTableComment(tableComment);
            table.setClassName(CodeGenUtils.toUpperCamel(CodeGenUtils.stripTablePrefix(tableName, "sys_")));
            table.setPackageName("com.smart");
            table.setModuleName("admin");
            table.setBusinessName(CodeGenUtils.stripTablePrefix(tableName, "sys_"));
            table.setFunctionName(tableComment.isEmpty() ? tableName : tableComment);
            table.setFunctionAuthor("smart-codegen");
            table.setTplCategory("0");
            table.setGenType("0");
            table.setGenPath("/");
            table.setTenantId(1L);
            genTableService.save(table);

            // 4. 落库 columns
            List<GenTableColumn> cols = new ArrayList<>();
            int sort = 0;
            for (Map<String, Object> raw : rawColumns) {
                String columnName = (String) raw.get("column_name");
                String dataType = (String) raw.get("data_type");
                boolean isPk = Boolean.TRUE.equals(raw.get("is_pk"));
                boolean isCommon = CodeGenUtils.isCommonField(columnName);

                GenTableColumn col = new GenTableColumn();
                col.setTableId(table.getId());
                col.setColumnName(columnName);
                col.setColumnComment((String) raw.get("column_comment"));
                col.setColumnType(dataType);
                col.setJavaType(CodeGenUtils.sqlTypeToJavaType(dataType));
                col.setJavaField(CodeGenUtils.toLowerCamel(columnName));
                col.setIsPk(isPk);
                col.setIsIncrement(isPk);
                col.setIsRequired(!"YES".equalsIgnoreCase((String) raw.get("is_nullable")) && !isPk);
                col.setIsInsert(!isCommon && !isPk);
                col.setIsEdit(!isCommon && !isPk);
                col.setIsList(!isCommon || "id".equalsIgnoreCase(columnName));
                col.setIsQuery(!isCommon && col.getJavaType().equals("String"));
                col.setQueryType(col.getJavaType().equals("String") ? "LIKE" : "EQ");
                col.setHtmlType(CodeGenUtils.sqlTypeToHtmlType(dataType));
                col.setDictType("");
                col.setSortOrder(sort++);
                cols.add(col);
            }
            genTableColumnService.saveBatch(cols);

            result.add(table.getId());
            log.info("Imported table {} -> id={}, columns={}", tableName, table.getId(), cols.size());
        }
        return result;
    }

    // ────────────── 内部辅助 ──────────────

    private GenTable loadTable(Long tableId) {
        GenTable table = genTableService.getById(tableId);
        if (table == null) {
            throw new BusinessException("GenTable not found: " + tableId);
        }
        return table;
    }

    private List<GenTableColumn> loadColumns(Long tableId) {
        return genTableColumnService.list(
                new LambdaQueryWrapper<GenTableColumn>()
                        .eq(GenTableColumn::getTableId, tableId)
                        .orderByAsc(GenTableColumn::getSortOrder));
    }

    /**
     * 构建 Velocity 渲染上下文。
     * 这里把模板里需要的所有变量集中暴露，避免模板里出现复杂表达式。
     */
    private VelocityContext buildContext(GenTable table, List<GenTableColumn> columns) {
        VelocityContext ctx = new VelocityContext();

        // 基础元信息
        ctx.put("tableName", table.getTableName());
        ctx.put("tableComment", table.getTableComment() != null ? table.getTableComment() : "");
        ctx.put("className", table.getClassName());
        ctx.put("lowerClassName", CodeGenUtils.firstLower(table.getClassName()));
        ctx.put("packageName", table.getPackageName() != null ? table.getPackageName() : "com.smart");
        ctx.put("packagePath", CodeGenUtils.packageToPath(
                table.getPackageName() != null ? table.getPackageName() : "com.smart"));
        ctx.put("moduleName", table.getModuleName() != null ? table.getModuleName() : "");
        ctx.put("businessName", table.getBusinessName() != null ? table.getBusinessName() : "");
        ctx.put("functionName", table.getFunctionName() != null ? table.getFunctionName() : "");
        ctx.put("functionAuthor", table.getFunctionAuthor() != null ? table.getFunctionAuthor() : "smart");

        // 时间戳
        ctx.put("today", CodeGenUtils.today());

        // 字段信息
        ctx.put("columns", columns);
        ctx.put("pkColumn", columns.stream().filter(GenTableColumn::getIsPk).findFirst().orElse(null));
        ctx.put("listColumns", columns.stream().filter(GenTableColumn::getIsList).toList());
        ctx.put("insertColumns", columns.stream().filter(GenTableColumn::getIsInsert).toList());
        ctx.put("editColumns", columns.stream().filter(GenTableColumn::getIsEdit).toList());
        ctx.put("queryColumns", columns.stream().filter(GenTableColumn::getIsQuery).toList());

        // import 列表（去重）
        ctx.put("importList", buildImportList(columns));

        // 权限前缀（用于 PreAuthorize 表达式 / 菜单 SQL）
        String permPrefix = (table.getModuleName() != null ? table.getModuleName() : "")
                + "_"
                + (table.getBusinessName() != null ? table.getBusinessName() : "");
        ctx.put("permissionPrefix", permPrefix.toLowerCase(Locale.ROOT));

        return ctx;
    }

    /**
     * 渲染 classpath 中的模板文件。
     */
    private String renderTemplate(String templatePath, VelocityContext context) {
        Template template = velocityEngine.getTemplate(templatePath, "UTF-8");
        StringWriter writer = new StringWriter();
        template.merge(context, writer);
        return writer.toString();
    }

    /**
     * 渲染一段内联字符串（用于路径表达式 / DB 中存储的模板片段）。
     */
    private String renderInline(String inline, VelocityContext context) {
        StringWriter writer = new StringWriter();
        velocityEngine.evaluate(context, writer, "inline-template", inline);
        return writer.toString();
    }

    /**
     * 根据列的 javaType 推导需要 import 的全限定名集合，去重。
     */
    private Set<String> buildImportList(List<GenTableColumn> columns) {
        Set<String> imports = new LinkedHashSet<>();
        for (GenTableColumn col : columns) {
            String type = col.getJavaType();
            if (type == null) {
                continue;
            }
            switch (type) {
                case "LocalDateTime" -> imports.add("java.time.LocalDateTime");
                case "LocalDate" -> imports.add("java.time.LocalDate");
                case "LocalTime" -> imports.add("java.time.LocalTime");
                case "BigDecimal" -> imports.add("java.math.BigDecimal");
                default -> {
                    /* 基础类型无需导入 */
                }
            }
        }
        return imports;
    }
}