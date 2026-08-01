package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSummaryRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.requirement.DocumentRequirementRel;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementPoolItem;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.requirement.DocumentRequirementRelMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementPoolItemMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RequirementServiceImpl implements RequirementService {

    @Resource
    private RequirementPoolItemMapper requirementMapper;
    @Resource
    private DocumentRequirementRelMapper documentRequirementRelMapper;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
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

    @Override
    public List<RequirementSummaryRespDTO> getDocumentRequirements(UUID documentId, UUID projectId) {
        requireDocument(documentId, projectId);
        List<UUID> requirementIds = documentRequirementRelMapper.listByDocumentId(documentId).stream()
                .map(DocumentRequirementRel::getRequirementId).distinct().toList();
        if (requirementIds.isEmpty()) {
            return List.of();
        }
        // 跳过已被逻辑删除的条目（selectBatchIds 自动过滤 is_deleted）
        return requirementMapper.selectBatchIds(requirementIds).stream().map(item -> {
            RequirementSummaryRespDTO dto = new RequirementSummaryRespDTO();
            dto.setId(item.getId());
            dto.setTitle(item.getTitle());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDocumentRequirements(UUID documentId, UUID projectId, List<UUID> requirementIds) {
        requireDocument(documentId, projectId);
        // 去重并保序；每个条目须属于当前项目
        Set<UUID> target = new LinkedHashSet<>(requirementIds != null ? requirementIds : List.of());
        for (UUID reqId : target) {
            RequirementPoolItem item = requirementMapper.selectById(reqId);
            if (item == null || !Objects.equals(item.getProjectId(), projectId)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NOT_FOUND);
            }
        }

        Set<UUID> existing = documentRequirementRelMapper.listByDocumentId(documentId).stream()
                .map(DocumentRequirementRel::getRequirementId).collect(Collectors.toCollection(LinkedHashSet::new));

        // 差量删除：现存但不在目标中的关联逻辑删除
        List<UUID> toRemove = existing.stream().filter(id -> !target.contains(id)).toList();
        documentRequirementRelMapper.deleteByDocumentIdAndRequirementIds(documentId, toRemove);

        // 差量新增：目标中但尚未关联的条目插入（已存在的保留不动）
        for (UUID reqId : target) {
            if (!existing.contains(reqId)) {
                DocumentRequirementRel rel = new DocumentRequirementRel();
                rel.setDocumentId(documentId);
                rel.setRequirementId(reqId);
                documentRequirementRelMapper.insert(rel);
            }
        }
    }

    @Override
    public List<RequirementPoolItem> requireByIds(UUID projectId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // 去重保序；selectBatchIds 自动过滤已逻辑删除条目
        List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(ids));
        List<RequirementPoolItem> items = requirementMapper.selectBatchIds(distinct);
        // 缺失（含已删除）或跨项目的条目一律按不存在处理，防止越项目取内容
        if (items.size() != distinct.size()
                || items.stream().anyMatch(item -> !Objects.equals(item.getProjectId(), projectId))) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NOT_FOUND);
        }
        Map<UUID, RequirementPoolItem> byId = items.stream()
                .collect(Collectors.toMap(RequirementPoolItem::getId, item -> item));
        return distinct.stream().map(byId::get).toList();
    }

    /** 文档必须存在、为 document 类型且属于当前项目（与 AiCaseGenerationServiceImpl 同款判定） */
    private void requireDocument(UUID documentId, UUID projectId) {
        TestCaseModule document = testCaseModuleMapper.selectById(documentId);
        if (document == null || !Constants.ModuleType.DOCUMENT.equals(document.getType())
                || !Objects.equals(document.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }
    }

    /** content 长度上限复用 AI 配置项（缺省回退内置默认，需求池不受 AI 开关影响） */
    private void validateContentLength(String content) {
        int max = aiConfigService.getIntSetting("requirementContentMaxLength");
        if (content != null && content.length() > max) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
    }
}
