package com.smart.flow.application.form;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.flow.api.form.BindFormCmd;
import com.smart.flow.api.form.BoundFormView;
import com.smart.flow.api.form.FieldRuleSpec;
import com.smart.flow.domain.form.FormBindingRepository;
import com.smart.flow.domain.form.FormDescriptor;
import com.smart.flow.domain.form.FormQueryPort;
import com.smart.flow.infrastructure.config.FlowJacksonConfig;
import com.smart.flow.infrastructure.persistence.entity.FlowFormBindingEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service orchestrating form bindings.
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li><strong>Designer side</strong> - {@link #bind} / {@link #unbind} let the chart
 *       author attach forms to the chart (default) or to individual nodes (override).</li>
 *   <li><strong>Runtime side</strong> - {@link #loadEffectiveBinding} computes the
 *       merged (chart-default + node-override) view that the front-end shows when an
 *       approver opens a task.</li>
 * </ol>
 *
 * <p>JSON encoding of {@code field_rules} is centralised here so the persistence layer
 * stays as a thin row-mapper. We use the dedicated {@code flowObjectMapper} bean to
 * keep the workflow module's serialisation rules independent of any global Jackson
 * tweaks (e.g. snake-case on the public API).
 */
@Slf4j
@Service
public class FormBindingAppService {

    private static final TypeReference<List<FieldRuleSpec>> RULE_LIST_TYPE = new TypeReference<>() {
    };

    private final FormBindingRepository bindingRepository;
    private final FormQueryPort formQueryPort;
    private final ObjectMapper objectMapper;

    public FormBindingAppService(FormBindingRepository bindingRepository,
                                 FormQueryPort formQueryPort,
                                 @Qualifier(FlowJacksonConfig.BEAN_NAME) ObjectMapper objectMapper) {
        this.bindingRepository = bindingRepository;
        this.formQueryPort = formQueryPort;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists the binding. The form must exist in {@code sys_form}; otherwise we fail
     * fast so a typo in the designer cannot leave a dangling binding row that would
     * later blow up at runtime.
     */
    @Transactional(rollbackFor = Exception.class)
    public Long bind(BindFormCmd cmd) {
        formQueryPort.findById(cmd.getFormId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Form not found in sys_form, formId=" + cmd.getFormId()));

        FlowFormBindingEntity entity = new FlowFormBindingEntity();
        entity.setChartId(cmd.getChartId());
        entity.setNodeKey(cmd.getNodeKey());
        entity.setFormId(cmd.getFormId());
        entity.setFieldRules(serialiseRules(cmd.getFieldRules()));

        bindingRepository.upsert(entity);
        return entity.getBindingId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long bindingId) {
        bindingRepository.deleteById(bindingId);
    }

    /**
     * Builds the view shown when a user opens a task. Resolution order:
     * <ol>
     *   <li>If the node has its own override, use its form + rules.</li>
     *   <li>Otherwise fall back to the chart-level default form + rules.</li>
     *   <li>If neither exists, return {@link Optional#empty()}; the task has no form.</li>
     * </ol>
     */
    public Optional<BoundFormView> loadEffectiveBinding(Long chartId, String nodeKey) {
        Optional<FlowFormBindingEntity> override = nodeKey == null
                ? Optional.empty()
                : bindingRepository.findNodeLevel(chartId, nodeKey);
        FlowFormBindingEntity active = override.orElseGet(
                () -> bindingRepository.findChartLevel(chartId).orElse(null));
        if (active == null) {
            return Optional.empty();
        }
        Optional<FormDescriptor> descriptor = formQueryPort.findById(active.getFormId());
        if (descriptor.isEmpty()) {
            // The form was deleted after the binding was created. Surface this as a
            // domain-level inconsistency rather than a confusing NPE downstream.
            log.warn("Binding {} points to formId {} which no longer exists in sys_form",
                    active.getBindingId(), active.getFormId());
            return Optional.empty();
        }
        FormDescriptor form = descriptor.get();
        return Optional.of(BoundFormView.builder()
                .formId(form.getFormId())
                .formKey(form.getFormKey())
                .formName(form.getFormName())
                .schemaJson(form.getSchemaJson())
                .layoutJson(form.getLayoutJson())
                .effectiveRules(deserialiseRules(active.getFieldRules()))
                .build());
    }

    /**
     * Pure helper exposed to other application services that need to load the rules
     * (without the form schema) for runtime checks.
     */
    public List<FieldRuleSpec> resolveRulesForNode(Long chartId, String nodeKey) {
        FlowFormBindingEntity active = (nodeKey == null
                ? Optional.<FlowFormBindingEntity>empty()
                : bindingRepository.findNodeLevel(chartId, nodeKey))
                .or(() -> bindingRepository.findChartLevel(chartId))
                .orElse(null);
        if (active == null) {
            return Collections.emptyList();
        }
        return deserialiseRules(active.getFieldRules());
    }

    /* ============================================================ helpers ===== */

    private String serialiseRules(List<FieldRuleSpec> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JsonProcessingException e) {
            // The DTO is constructed in our own codebase; an encoding failure means a
            // real bug in the rule model itself.
            throw new IllegalStateException("Failed to encode field rules", e);
        }
    }

    private List<FieldRuleSpec> deserialiseRules(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, RULE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            // Stored data we no longer understand - rather than blow up the entire
            // task page, log loudly and degrade to "no rules" so the form is still
            // viewable. The fix is then a designer re-save.
            log.error("Corrupt field_rules payload, treating as empty: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * Convenience for callers that have payloads as raw JSON string and need a typed map.
     * Lives here so the JSON codec is in one place.
     */
    public Map<String, Object> decodeFormPayload(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Form payload is not valid JSON", e);
        }
    }

    public String encodeFormPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode form payload", e);
        }
    }
}
