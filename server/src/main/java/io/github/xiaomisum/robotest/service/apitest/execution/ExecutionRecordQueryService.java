package io.github.xiaomisum.robotest.service.apitest.execution;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiChangeHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionHistoryItemRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 场景执行历史与变更历史查询（测试场景详细设计 3.11、基础设施详细设计 3.2）。
 * 自 04 §4.1 步骤 4 拆分：只读投影，无执行/落库职责。
 */
@Service
public class ExecutionRecordQueryService {

    private static final String TARGET_TYPE_SCENE = "scene";

    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private ApiExecutionRecordMapper executionRecordMapper;
    @Resource
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private SysUserMapper userMapper;

    public PageResult<ApiExecutionHistoryItemRespDTO> pageExecutions(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        SceneExecutionSupport.requireScene(sceneMapper, projectId, sceneId);
        PageResult<ApiExecutionRecord> page = executionRecordMapper.selectPageByScene(sceneId, pageParam);
        List<ApiExecutionHistoryItemRespDTO> items = page.getList().stream().map(this::toHistoryItem).toList();
        return new PageResult<>(items, page.getTotal());
    }

    private ApiExecutionHistoryItemRespDTO toHistoryItem(ApiExecutionRecord record) {
        return ApiExecutionHistoryItemRespDTO.builder()
                .id(record.getId().toString())
                .status(record.getStatus())
                .executionMode(record.getExecutionMode())
                .triggerType(record.getTriggerType())
                .executedAt(record.getExecutedAt())
                .durationMs(record.getDurationMs())
                .reportId(record.getReportId() == null ? null : record.getReportId().toString())
                .build();
    }

    public PageResult<ApiChangeHistoryItemRespDTO> pageChangeHistory(UUID workspaceId, UUID projectId, UUID userId,
            UUID sceneId, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        SceneExecutionSupport.requireScene(sceneMapper, projectId, sceneId);
        PageResult<ApiChangeHistory> page =
                changeHistoryMapper.selectPageByTarget(TARGET_TYPE_SCENE, sceneId, pageParam);
        Map<UUID, String> operatorNames = loadOperatorNames(page.getList());
        List<ApiChangeHistoryItemRespDTO> items = page.getList().stream()
                .map(history -> toItem(history, operatorNames)).toList();
        return new PageResult<>(items, page.getTotal());
    }

    private Map<UUID, String> loadOperatorNames(List<ApiChangeHistory> histories) {
        List<UUID> userIds = histories.stream().map(ApiChangeHistory::getCreatedBy)
                .filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new LinkedHashMap<>();
        for (SysUser user : userMapper.selectBatchIds(userIds)) {
            names.put(user.getId(), user.getUsername());
        }
        return names;
    }

    private ApiChangeHistoryItemRespDTO toItem(ApiChangeHistory history, Map<UUID, String> operatorNames) {
        return ApiChangeHistoryItemRespDTO.builder()
                .id(history.getId().toString())
                .version(history.getVersion())
                .operatorName(operatorNames.get(history.getCreatedBy()))
                .changeType(history.getChangeType())
                .changeSummary(history.getContentDiff() == null ? null
                        : Objects.toString(history.getContentDiff().get("summary"), null))
                .contentDiff(history.getContentDiff())
                .changedAt(history.getCreatedAt())
                .build();
    }
}