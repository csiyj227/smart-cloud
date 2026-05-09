package com.smart.nl2sql.infrastructure.nl2sql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight SQL analyzer that infers dimensions and measures from a SELECT clause.
 *
 * <p>This is a pragmatic regex-based implementation, not a full AST parser. It works
 * well for the typical NL2SQL outputs that we generate (single SELECT, few JOINs).
 *
 * <ul>
 *     <li><b>Measure</b>: an item in the SELECT list wrapped in an aggregate function
 *         (SUM/AVG/COUNT/MAX/MIN/STDDEV/VARIANCE).</li>
 *     <li><b>Dimension</b>: any other selected column.</li>
 * </ul>
 */
@Slf4j
@Component
public class SqlAnalyzer {

    private static final Pattern SELECT_BLOCK = Pattern.compile(
            "(?is)select\\s+(?:distinct\\s+)?(.*?)\\s+from\\s+");

    private static final Pattern AGG_FUNC = Pattern.compile(
            "(?i)^\\s*(sum|avg|count|max|min|stddev|variance)\\s*\\(");

    /**
     * Analyze the given SELECT SQL.
     */
    public AnalysisResult analyze(String sql) {
        AnalysisResult result = new AnalysisResult();
        result.setDimensions(new ArrayList<>());
        result.setMeasures(new ArrayList<>());

        if (sql == null || sql.isBlank()) {
            return result;
        }

        Matcher m = SELECT_BLOCK.matcher(sql);
        if (!m.find()) {
            log.debug("SQL does not match SELECT pattern: {}", sql);
            return result;
        }
        String selectList = m.group(1);

        for (String part : splitTopLevelCommas(selectList)) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            String alias = extractAlias(item);
            String expr = stripAlias(item);
            FieldInfo info = new FieldInfo(alias != null ? alias : expr, expr);

            if (AGG_FUNC.matcher(expr).find()) {
                result.getMeasures().add(info);
            } else {
                result.getDimensions().add(info);
            }
        }
        return result;
    }

    /**
     * Split SELECT items by top-level commas (ignoring commas inside parentheses).
     */
    private List<String> splitTopLevelCommas(String s) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                result.add(buf.toString());
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        if (buf.length() > 0) {
            result.add(buf.toString());
        }
        return result;
    }

    private String extractAlias(String item) {
        // Match: ... AS alias  OR ... " alias" at the end
        Matcher asMatcher = Pattern.compile("(?i)\\s+as\\s+([`\"]?[\\w$\\u4e00-\\u9fa5]+[`\"]?)\\s*$").matcher(item);
        if (asMatcher.find()) {
            return strip(asMatcher.group(1));
        }
        // Trailing space + identifier (e.g. "SUM(x) total")
        Matcher tail = Pattern.compile("\\s+([`\"]?[\\w$\\u4e00-\\u9fa5]+[`\"]?)\\s*$").matcher(item);
        if (tail.find() && !item.toLowerCase().endsWith(")")) {
            return strip(tail.group(1));
        }
        return null;
    }

    private String stripAlias(String item) {
        return item.replaceAll("(?i)\\s+as\\s+[`\"]?[\\w$\\u4e00-\\u9fa5]+[`\"]?\\s*$", "").trim();
    }

    private String strip(String s) {
        return s.replaceAll("^[`\"]|[`\"]$", "");
    }

    @Data
    @NoArgsConstructor
    public static class AnalysisResult {
        private List<FieldInfo> dimensions;
        private List<FieldInfo> measures;
    }

    @Data
    @AllArgsConstructor
    public static class FieldInfo {
        private String alias;
        private String expression;
    }
}
