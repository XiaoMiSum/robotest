package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.audit.AuditOperation;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.project.ProjectSettingUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.project.ProjectSettingListRespDTO;
import io.github.xiaomisum.robotest.model.entity.project.ProjectSetting;
import io.github.xiaomisum.robotest.repository.project.ProjectSettingMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectSettingServiceImpl implements ProjectSettingService {

    @Resource
    private ProjectSettingMapper projectSettingMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Override
    public ProjectSettingListRespDTO getSettings(UUID projectId, UUID workspaceId, UUID userId, String domain) {
        if (!ProjectSettingRegistry.isSupportedDomain(domain)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);

        Map<String, ProjectSetting> rowByKey = projectSettingMapper.listByProjectIdAndDomain(projectId, domain).stream()
                .collect(Collectors.toMap(ProjectSetting::getSettingKey, Function.identity(), (a, b) -> a));

        List<ProjectSettingListRespDTO.Item> items = ProjectSettingRegistry.listByDomain(domain).stream()
                .map(definition -> toItem(definition, rowByKey.get(definition.key())))
                .toList();

        ProjectSettingListRespDTO respDTO = new ProjectSettingListRespDTO();
        respDTO.setItems(items);
        return respDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "ProjectSetting")
    public int updateSettings(UUID projectId, UUID workspaceId, UUID userId, ProjectSettingUpdateReqDTO reqDTO) {
        // 维护者校验已覆盖成员身份（非成员无成员记录同样被拒），不再重复调用成员校验
        projectAccessGuard.requireProjectMaintainer(projectId, workspaceId, userId);
        validateAll(reqDTO);

        reqDTO.getItems().forEach(item -> upsert(projectId, userId, item));
        return reqDTO.getItems().size();
    }

    private void validateAll(ProjectSettingUpdateReqDTO reqDTO) {
        for (ProjectSettingUpdateReqDTO.Item item : reqDTO.getItems()) {
            ProjectSettingRegistry.SettingDefinition definition =
                    ProjectSettingRegistry.find(item.getDomain(), item.getSettingKey());
            if (definition == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SETTING_KEY_INVALID);
            }
            if (!definition.validator().test(item.getSettingValue())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SETTING_VALUE_INVALID, item.getSettingKey());
            }
        }
    }

    private void upsert(UUID projectId, UUID userId, ProjectSettingUpdateReqDTO.Item item) {
        ProjectSetting existing = projectSettingMapper.findByProjectIdAndDomainAndKey(
                projectId, item.getDomain(), item.getSettingKey());
        if (existing != null) {
            // 查询仅做存在性判定，更新载体只携带变更字段（部分更新原则）
            ProjectSetting update = new ProjectSetting();
            update.setId(existing.getId());
            update.setSettingValue(item.getSettingValue());
            update.setUpdatedBy(userId);
            projectSettingMapper.updateById(update);
            return;
        }
        ProjectSetting insert = new ProjectSetting();
        insert.setProjectId(projectId);
        insert.setDomain(item.getDomain());
        insert.setSettingKey(item.getSettingKey());
        insert.setSettingValue(item.getSettingValue());
        insert.setUpdatedBy(userId);
        projectSettingMapper.insert(insert);
    }

    private ProjectSettingListRespDTO.Item toItem(ProjectSettingRegistry.SettingDefinition definition,
            ProjectSetting row) {
        ProjectSettingListRespDTO.Item item = new ProjectSettingListRespDTO.Item();
        item.setDomain(definition.domain());
        item.setSettingKey(definition.key());
        item.setSettingValue(row != null ? row.getSettingValue() : definition.defaultValue());
        item.setDefaultValue(definition.defaultValue());
        item.setExplicit(row != null);
        return item;
    }
}
