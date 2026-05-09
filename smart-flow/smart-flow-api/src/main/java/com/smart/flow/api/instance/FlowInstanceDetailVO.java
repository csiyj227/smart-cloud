package com.smart.flow.api.instance;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class FlowInstanceDetailVO {
    // 基本信息（来自 flow_instance_biz + flow_definition）
    private String processInstanceId;
    private String chartKey;
    private String chartName;
    private String bizNo;
    private String title;
    private Long starterId;
    private String starterName;
    private String bizStatus;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    // 流程定义 DSL（JSON 字符串，前端解析后渲染流程图）
    private String chartDsl;
    // 当前活跃节点 key 列表（流程运行中时非空，用于流程图高亮当前节点）
    private List<String> activeNodeKeys;
    // 已完成节点 key 列表（用于流程图标记已走过的节点）
    private List<String> completedNodeKeys;
    // 绑定的表单类型：DYNAMIC / CUSTOM（从 chartDsl.forms 中解析）
    private String formType;
    // 自定义表单查看路径（仅 CUSTOM 类型有值）
    private String formViewUrl;
    // 自定义表单提交路径（仅 CUSTOM 类型有值）
    private String formSubmitUrl;
    // 绑定的表单名称
    private String formName;
    // 表单数据（最新快照的 JSON，存为 Map）
    private Map<String, Object> formData;
    // 字段标识 → 字段标签映射（用于前端展示表单数据时翻译 fieldKey）
    private Map<String, String> fieldLabelMap;
    // 审批记录列表
    private List<ApprovalRecordVO> records;
}
