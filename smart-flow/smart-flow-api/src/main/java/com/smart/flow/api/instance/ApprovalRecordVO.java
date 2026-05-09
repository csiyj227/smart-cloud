package com.smart.flow.api.instance;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApprovalRecordVO {
    private Long recordId;
    private String taskId;
    private String nodeKey;
    private String nodeName;
    private String actionType;
    private Long actorId;
    private String actorName;
    private Long targetUserId;
    private String targetUserName;
    private String comment;
    private LocalDateTime occurredAt;
}
