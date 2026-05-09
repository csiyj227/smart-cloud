package com.smart.flow.api.taskcenter;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Paginated wrapper. Mirrors the {@code records / total / pageNum / pageSize} shape used
 * elsewhere in the smart platform so existing front-end pagination components work unchanged.
 */
@Value
@Builder
public class TaskCenterPage {

    List<TaskCenterItem> records;
    Long total;
    Integer pageNum;
    Integer pageSize;
}
