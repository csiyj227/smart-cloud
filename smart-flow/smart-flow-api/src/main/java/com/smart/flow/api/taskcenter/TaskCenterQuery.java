package com.smart.flow.api.taskcenter;

import lombok.Data;

/**
 * Generic query DTO accepted by every task-center listing endpoint.
 *
 * <p>Page numbers follow the smart platform's convention of being 1-indexed - 0-indexed
 * pagination has tripped up enough integrators that we adopted the same defaults across the
 * whole codebase.
 *
 * <p>Optional filters ({@code chartKey} / {@code keyword}) are applied with AND semantics; an
 * unset filter means "do not constrain". The {@code keyword} matches against {@code title}
 * and {@code bizNo} via {@code LIKE}, mirroring what the front-end search box advertises.
 */
@Data
public class TaskCenterQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private String chartKey;
    private String keyword;
}
