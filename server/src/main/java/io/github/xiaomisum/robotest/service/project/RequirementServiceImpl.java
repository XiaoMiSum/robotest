package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementListRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementPoolItem;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementPoolItemMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.ai.AiConfigService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RequirementServiceImpl implements RequirementService {

    @Resource
    private RequirementPoolItemMapper requirementMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private AiConfigService aiConfigService;

    @Override
    public PageResult<RequirementListRespDTO> getPage(UUID projectId, String keyword, Integer pageNo,
            Integer pageSize) {
        PageResult<RequirementPoolItem> page = requirementMapper.findPage(new PageParam() {
            {
                setPageNo(pageNo);
                setPageSize(pageSize);
            }
        }, projectId, keyword);

        // 批量取创建人名，避免逐行 N+1
        List<UUID> creatorIds = page.getList().stream()
                .map(RequirementPoolItem::getCreatedBy).distinct().toList();
        Map<UUID, String> nameById = creatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(creatorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (a, b) -> a));

        List<RequirementListRespDTO> dtos = page.getList().stream().map(item -> {
            RequirementListRespDTO dto = new RequirementListRespDTO();
            dto.setId(item.getId());
            dto.setTitle(item.getTitle());
            dto.setSourceUrl(item.getSourceUrl());
            dto.setCreatedBy(item.getCreatedBy());
            dto.setCreatorName(nameById.get(item.getCreatedBy()));
            dto.setUpdatedAt(item.getUpdatedAt());
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(dtos, page.getTotal());
    }

    @Override
    public RequirementDetailRespDTO getDetail(UUID id, UUID projectId) {
        RequirementPoolItem item = requireItem(id, projectId);
        RequirementDetailRespDTO dto = new RequirementDetailRespDTO();
        dto.setId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setContent(item.getContent());
        dto.setSourceUrl(item.getSourceUrl());
        dto.setCreatedBy(item.getCreatedBy());
        SysUser creator = userMapper.selectById(item.getCreatedBy());
        dto.setCreatorName(creator != null ? creator.getUsername() : null);
        dto.setUpdatedBy(item.getUpdatedBy());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setUpdatedAt(item.getUpdatedAt());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(UUID projectId, UUID userId, RequirementCreateReqDTO reqDTO) {
        validateContentLength(reqDTO.getContent());

        RequirementPoolItem item = new RequirementPoolItem();
        item.setProjectId(projectId);
        item.setTitle(reqDTO.getTitle());
        item.setContent(reqDTO.getContent());
        item.setSourceUrl(StringUtils.hasText(reqDTO.getSourceUrl()) ? reqDTO.getSourceUrl() : null);
        item.setCreatedBy(userId);
        item.setUpdatedBy(userId);
        requirementMapper.insert(item);
        return item.getId().toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID id, UUID projectId, UUID userId, RequirementUpdateReqDTO reqDTO) {
        RequirementPoolItem item = requireItem(id, projectId);
        requireEditable(item, projectId, userId);
        if (reqDTO.getContent() != null) {
            validateContentLength(reqDTO.getContent());
        }

        // 更新载体只携带前端传入字段，避免全列覆盖
        RequirementPoolItem update = new RequirementPoolItem();
        update.setId(id);
        if (StringUtils.hasText(reqDTO.getTitle())) {
            update.setTitle(reqDTO.getTitle());
        }
        if (reqDTO.getContent() != null) {
            update.setContent(reqDTO.getContent());
        }
        // sourceUrl 三态：null 不改、空串清空、非空更新
        if (reqDTO.getSourceUrl() != null) {
            update.setSourceUrl(reqDTO.getSourceUrl().isEmpty() ? null : reqDTO.getSourceUrl());
        }
        update.setUpdatedBy(userId);
        requirementMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID projectId, UUID userId) {
        RequirementPoolItem item = requireItem(id, projectId);
        requireEditable(item, projectId, userId);
        // 逻辑删除；不影响已生成的用例（详细设计 3.1.4）
        requirementMapper.deleteById(id);
    }

    /** 条目必须存在且属于当前项目 */
    private RequirementPoolItem requireItem(UUID id, UUID projectId) {
        RequirementPoolItem item = requirementMapper.selectById(id);
        if (item == null || !Objects.equals(item.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NOT_FOUND);
        }
        return item;
    }

    /** 编辑/删除权限：创建人或项目管理权限成员（仿 ProjectServiceImpl 的 ADMIN_ID 判定） */
    private void requireEditable(RequirementPoolItem item, UUID projectId, UUID userId) {
        if (Objects.equals(item.getCreatedBy(), userId)) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        if (project != null) {
            WorkspaceUser wu = workspaceUserMapper.findByWorkspaceIdAndUserId(project.getWorkspaceId(), userId);
            if (wu != null && Constants.WorkspaceRole.ADMIN_ID.equals(wu.getWorkspaceRole())) {
                return;
            }
        }
        throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
    }

    /** content 长度上限复用 AI 配置项（缺省回退内置默认，需求池不受 AI 开关影响） */
    private void validateContentLength(String content) {
        int max = aiConfigService.getIntSetting("requirementContentMaxLength");
        if (content != null && content.length() > max) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
    }
}
