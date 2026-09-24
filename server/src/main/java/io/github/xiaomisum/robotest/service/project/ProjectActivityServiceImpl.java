package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectActivityRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.ProjectActivity;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectActivityMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ProjectActivityServiceImpl implements ProjectActivityService {

    @Resource
    private ProjectActivityMapper projectActivityMapper;
    @Resource
    private SysUserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(UUID projectId, UUID actorId, String resourceType, UUID resourceId,
                       String resourceName, String action, String summary) {
        if (projectId == null || resourceId == null || !StringUtils.hasText(summary)) {
            return;
        }
        try {
            ProjectActivity activity = new ProjectActivity();
            activity.setProjectId(projectId);
            activity.setActorId(actorId == null ? ProjectAccessGuard.SYSTEM_OPERATOR_ID : actorId);
            activity.setActorName(truncate(resolveActorName(actorId), 100));
            activity.setResourceType(resourceType);
            activity.setResourceId(resourceId);
            activity.setResourceName(truncate(resourceName, 200));
            activity.setAction(action);
            activity.setSummary(truncate(summary, 500));
            activity.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
            projectActivityMapper.insert(activity);
        } catch (Exception e) {
            log.warn("[ProjectActivity] Failed to record activity for project {}: {}", projectId, e.getMessage());
        }
    }

    @Override
    public List<ProjectActivityRespDTO> listRecent(UUID projectId, int limit) {
        return projectActivityMapper.findRecentByProjectId(projectId, limit).stream()
                .map(this::toRespDTO)
                .toList();
    }

    private ProjectActivityRespDTO toRespDTO(ProjectActivity activity) {
        ProjectActivityRespDTO dto = new ProjectActivityRespDTO();
        dto.setId(activity.getId());
        dto.setProjectId(activity.getProjectId());
        dto.setActorId(activity.getActorId());
        dto.setActorName(activity.getActorName());
        dto.setResourceType(activity.getResourceType());
        dto.setResourceId(activity.getResourceId());
        dto.setResourceName(activity.getResourceName());
        dto.setAction(activity.getAction());
        dto.setSummary(activity.getSummary());
        dto.setOccurredAt(activity.getOccurredAt() == null ? null
                : activity.getOccurredAt().atZone(ZoneOffset.UTC).toInstant().toString());
        return dto;
    }

    private String resolveActorName(UUID actorId) {
        if (actorId == null || ProjectAccessGuard.SYSTEM_OPERATOR_ID.equals(actorId)) {
            return "系统";
        }
        SysUser actor = userMapper.selectById(actorId);
        if (actor == null) {
            return "未知用户";
        }
        if (StringUtils.hasText(actor.getName())) {
            return actor.getName();
        }
        return StringUtils.hasText(actor.getUsername()) ? actor.getUsername() : "未知用户";
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "未命名";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
