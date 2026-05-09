package com.smart.flow.application.taskcenter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.common.security.component.PermissionEvaluator;
import com.smart.flow.api.taskcenter.TaskCenterItem;
import com.smart.flow.api.taskcenter.TaskCenterPage;
import com.smart.flow.api.taskcenter.TaskCenterQuery;
import com.smart.flow.infrastructure.cache.PendingCountCache;
import com.smart.flow.infrastructure.persistence.entity.FlowCcRecordEntity;
import com.smart.flow.infrastructure.persistence.entity.FlowInstanceBizEntity;
import com.smart.flow.infrastructure.persistence.entity.FlowTaskViewEntity;
import com.smart.flow.infrastructure.persistence.mapper.FlowCcRecordMapper;
import com.smart.flow.infrastructure.persistence.mapper.FlowInstanceBizMapper;
import com.smart.flow.infrastructure.persistence.mapper.FlowTaskViewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only application service powering the task-center pages: <em>my todo</em>,
 * <em>my done</em>, <em>my started</em> and <em>my CC</em>.
 *
 * <p>Every query is a pure SELECT against either {@code flow_task_view} (the CQRS read
 * model) or {@code flow_instance_biz} / {@code flow_cc_record} - the projector keeps these
 * tables eventually consistent with the engine, so the queries themselves never join
 * {@code act_*}.
 *
 * <p>The {@code pendingCount} fast-path goes through {@link PendingCountCache} for the
 * common "every-few-seconds badge poll" use case; on a miss it backfills the cache from a
 * single {@code COUNT(*)} on {@code flow_task_view}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCenterQueryService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CLAIMED = "claimed";
    private static final String STATUS_COMPLETED = "completed";
    private static final String CC_READ_FLAG_UNREAD = "0";

    private final FlowTaskViewMapper viewMapper;
    private final FlowInstanceBizMapper instanceMapper;
    private final FlowCcRecordMapper ccMapper;
    private final PendingCountCache pendingCountCache;
    private final PermissionEvaluator permissionEvaluator;

    public TaskCenterPage myTodo(TaskCenterQuery query) {
        Long userId = requireCurrentUserId();
        LambdaQueryWrapper<FlowTaskViewEntity> wrapper = baseViewWrapper(query)
                .eq(FlowTaskViewEntity::getCandidateUserId, userId)
                .in(FlowTaskViewEntity::getViewStatus, STATUS_PENDING, STATUS_CLAIMED)
                .orderByDesc(FlowTaskViewEntity::getReceivedAt);
        return paginateView(wrapper, query);
    }

    public TaskCenterPage myDone(TaskCenterQuery query) {
        Long userId = requireCurrentUserId();
        LambdaQueryWrapper<FlowTaskViewEntity> wrapper = baseViewWrapper(query)
                .eq(FlowTaskViewEntity::getCandidateUserId, userId)
                .eq(FlowTaskViewEntity::getViewStatus, STATUS_COMPLETED)
                .orderByDesc(FlowTaskViewEntity::getFinishedAt);
        return paginateView(wrapper, query);
    }

    public TaskCenterPage myStarted(TaskCenterQuery query) {
        Long userId = requireCurrentUserId();
        Page<FlowInstanceBizEntity> mpPage = new Page<>(safePage(query.getPageNum()), safeSize(query.getPageSize()));
        LambdaQueryWrapper<FlowInstanceBizEntity> wrapper = new LambdaQueryWrapper<FlowInstanceBizEntity>()
                .eq(FlowInstanceBizEntity::getStarterId, userId)
                .orderByDesc(FlowInstanceBizEntity::getStartTime);
        if (query.getChartKey() != null && !query.getChartKey().isBlank()) {
            wrapper.eq(FlowInstanceBizEntity::getChartKey, query.getChartKey());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String like = "%" + query.getKeyword() + "%";
            wrapper.and(w -> w.like(FlowInstanceBizEntity::getTitle, like)
                    .or().like(FlowInstanceBizEntity::getBizNo, like));
        }
        Page<FlowInstanceBizEntity> result = instanceMapper.selectPage(mpPage, wrapper);
        List<TaskCenterItem> items = result.getRecords().stream()
                .map(this::toItemFromInstance)
                .toList();
        return TaskCenterPage.builder()
                .records(items)
                .total(result.getTotal())
                .pageNum((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .build();
    }

    public TaskCenterPage myCc(TaskCenterQuery query) {
        Long userId = requireCurrentUserId();
        Page<FlowCcRecordEntity> mpPage = new Page<>(safePage(query.getPageNum()), safeSize(query.getPageSize()));
        LambdaQueryWrapper<FlowCcRecordEntity> wrapper = new LambdaQueryWrapper<FlowCcRecordEntity>()
                .eq(FlowCcRecordEntity::getCcUserId, userId)
                .orderByDesc(FlowCcRecordEntity::getSentAt);
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(FlowCcRecordEntity::getNodeName, "%" + query.getKeyword() + "%");
        }
        Page<FlowCcRecordEntity> result = ccMapper.selectPage(mpPage, wrapper);
        List<TaskCenterItem> items = result.getRecords().stream()
                .map(this::toItemFromCc)
                .toList();
        return TaskCenterPage.builder()
                .records(items)
                .total(result.getTotal())
                .pageNum((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .build();
    }

    /**
     * Fast badge endpoint - reads from Redis, falls back to a single count query on a miss.
     */
    public long pendingCount() {
        Long userId = requireCurrentUserId();
        Long cached = pendingCountCache.get(userId);
        if (cached != null) {
            return cached;
        }
        long live = viewMapper.selectCount(new LambdaQueryWrapper<FlowTaskViewEntity>()
                .eq(FlowTaskViewEntity::getCandidateUserId, userId)
                .in(FlowTaskViewEntity::getViewStatus, STATUS_PENDING, STATUS_CLAIMED));
        pendingCountCache.put(userId, live);
        return live;
    }

    /**
     * Marks a CC entry as read. Idempotent: a second call is a no-op. The update is
     * conditional on {@code read_flag = '0'} so two concurrent callers cannot trample
     * each other's {@code read_at} timestamp.
     */
    public void markCcRead(Long ccId) {
        Long userId = requireCurrentUserId();
        // Ownership guard - we still need to read the row once to make sure the caller is
        // actually the addressee. The conditional UPDATE below handles the race window.
        FlowCcRecordEntity record = ccMapper.selectById(ccId);
        if (record == null) {
            throw new IllegalArgumentException("CC record not found: " + ccId);
        }
        if (!userId.equals(record.getCcUserId())) {
            throw new IllegalStateException(
                    "CC " + ccId + " is not addressed to caller " + userId);
        }
        ccMapper.update(null, new LambdaUpdateWrapper<FlowCcRecordEntity>()
                .eq(FlowCcRecordEntity::getCcId, ccId)
                .eq(FlowCcRecordEntity::getReadFlag, CC_READ_FLAG_UNREAD)
                .set(FlowCcRecordEntity::getReadFlag, "1")
                .set(FlowCcRecordEntity::getReadAt, java.time.LocalDateTime.now()));
    }

    /* ============================================================ private helpers ===== */

    private LambdaQueryWrapper<FlowTaskViewEntity> baseViewWrapper(TaskCenterQuery query) {
        LambdaQueryWrapper<FlowTaskViewEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getChartKey() != null && !query.getChartKey().isBlank()) {
            wrapper.eq(FlowTaskViewEntity::getChartKey, query.getChartKey());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String like = "%" + query.getKeyword() + "%";
            wrapper.and(w -> w.like(FlowTaskViewEntity::getTitle, like)
                    .or().like(FlowTaskViewEntity::getBizNo, like));
        }
        return wrapper;
    }

    private TaskCenterPage paginateView(LambdaQueryWrapper<FlowTaskViewEntity> wrapper, TaskCenterQuery query) {
        Page<FlowTaskViewEntity> mpPage = new Page<>(safePage(query.getPageNum()), safeSize(query.getPageSize()));
        Page<FlowTaskViewEntity> result = viewMapper.selectPage(mpPage, wrapper);
        List<TaskCenterItem> items = result.getRecords().stream()
                .map(this::toItemFromView)
                .toList();
        return TaskCenterPage.builder()
                .records(items)
                .total(result.getTotal())
                .pageNum((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .build();
    }

    private TaskCenterItem toItemFromView(FlowTaskViewEntity row) {
        return TaskCenterItem.builder()
                .taskId(row.getTaskId())
                .processInstanceId(row.getProcessInstanceId())
                .chartKey(row.getChartKey())
                .chartName(row.getChartName())
                .bizNo(row.getBizNo())
                .title(row.getTitle())
                .nodeKey(row.getNodeKey())
                .nodeName(row.getNodeName())
                .viewStatus(row.getViewStatus())
                .starterId(row.getStarterId())
                .starterName(row.getStarterName())
                .receivedAt(row.getReceivedAt())
                .finishedAt(row.getFinishedAt())
                .formId(row.getFormId())
                .build();
    }

    private TaskCenterItem toItemFromInstance(FlowInstanceBizEntity biz) {
        return TaskCenterItem.builder()
                .processInstanceId(biz.getProcessInstanceId())
                .chartKey(biz.getChartKey())
                .bizNo(biz.getBizNo())
                .title(biz.getTitle())
                .viewStatus(biz.getBizStatus())
                .starterId(biz.getStarterId())
                .starterName(biz.getStarterName())
                .receivedAt(biz.getStartTime())
                .finishedAt(biz.getEndTime())
                .build();
    }

    private TaskCenterItem toItemFromCc(FlowCcRecordEntity cc) {
        return TaskCenterItem.builder()
                .taskId(String.valueOf(cc.getCcId()))
                .processInstanceId(cc.getProcessInstanceId())
                .nodeKey(cc.getNodeKey())
                .nodeName(cc.getNodeName())
                .viewStatus(CC_READ_FLAG_UNREAD.equals(cc.getReadFlag()) ? "unread" : "read")
                .receivedAt(cc.getSentAt())
                .finishedAt(cc.getReadAt())
                .build();
    }

    private int safePage(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safeSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        // Guardrail to prevent abusive callers from exploding the result set.
        return Math.min(pageSize, 200);
    }

    private Long requireCurrentUserId() {
        Long userId = permissionEvaluator.getCurrentUserId();
        if (userId != null) {
            return userId;
        }
        // Fallback: extract user_id directly from SecurityContext for principal types
        // that PermissionEvaluator does not yet recognise.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            log.debug("PermissionEvaluator returned null userId; principal type = {}",
                    principal != null ? principal.getClass().getName() : "null");

            // JWT-based authentication: principal is a Jwt object with claims
            if (principal instanceof Jwt jwt) {
                log.debug("JWT claims: {}", jwt.getClaims());
                userId = asLong(jwt.getClaim("user_id"));
                if (userId == null) {
                    userId = asLong(jwt.getClaim("userId"));
                }
                if (userId == null) {
                    userId = asLong(jwt.getClaim("uid"));
                }
                if (userId == null) {
                    userId = asLong(jwt.getClaim("sub"));
                }
            }
            // Opaque token / introspection: principal is OAuth2AuthenticatedPrincipal
            if (userId == null && principal instanceof OAuth2AuthenticatedPrincipal oauth2) {
                userId = asLong(oauth2.getAttribute("user_id"));
            }
            // Last resort: reflection for SmartUserPrincipal.getUserId()
            if (userId == null && principal != null) {
                try {
                    java.lang.reflect.Method method = principal.getClass().getMethod("getUserId");
                    Object result = method.invoke(principal);
                    userId = asLong(result);
                } catch (Exception ignored) {
                    // not available
                }
            }
        }
        if (userId == null) {
            throw new IllegalStateException("No authenticated user in current context");
        }
        return userId;
    }

    private Long asLong(Object value) {
        if (value instanceof Long longVal) {
            return longVal;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isEmpty()) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return null;
    }
}
