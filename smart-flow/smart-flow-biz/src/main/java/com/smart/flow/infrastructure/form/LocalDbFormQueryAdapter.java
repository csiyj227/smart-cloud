package com.smart.flow.infrastructure.form;

import com.smart.flow.domain.form.FormDescriptor;
import com.smart.flow.domain.form.FormQueryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default {@link FormQueryPort} implementation that reads {@code sys_form} from the
 * shared database via {@link JdbcTemplate}.
 *
 * <p>Why JdbcTemplate rather than reusing {@code SysFormMapper}? The mapper lives in
 * {@code smart-system-biz}, and pulling it in would couple smart-flow's startup to the
 * UPMS module - the runtime classpath would need every UPMS bean to satisfy
 * MyBatis-Plus's mapper scan. A short, hand-rolled SELECT keeps the dependency graph
 * one-way (smart-flow knows about the table, not the module) and is trivial to
 * refactor into a Feign call later.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalDbFormQueryAdapter implements FormQueryPort {

    // "schema" is a non-reserved keyword in PostgreSQL but a reserved word in some other
    // engines (and various IDE SQL linters complain). Quoting it costs nothing and keeps
    // the statement portable.
    private static final String SQL_FIND_BY_ID =
            "SELECT form_id, form_key, form_name, \"schema\", layout " +
                    "FROM sys_form WHERE form_id = ? AND del_flag = '0'";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<FormDescriptor> findById(Long formId) {
        if (formId == null) {
            return Optional.empty();
        }
        // We intentionally do NOT swallow DataAccessException here: the workflow code reads
        // sys_form on every task open / start, so a silent fallback to "form not found"
        // would mask every transient DB issue as a confusing "form deleted" error to the
        // user. EmptyResultDataAccessException IS swallowed because it just means the row
        // is missing - exactly the case we want to model with Optional.empty().
        try {
            return jdbcTemplate.query(SQL_FIND_BY_ID, rs -> {
                if (!rs.next()) {
                    return Optional.<FormDescriptor>empty();
                }
                return Optional.of(FormDescriptor.builder()
                        .formId(rs.getLong("form_id"))
                        .formKey(rs.getString("form_key"))
                        .formName(rs.getString("form_name"))
                        .schemaJson(rs.getString("schema"))
                        .layoutJson(rs.getString("layout"))
                        .build());
            }, formId);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
