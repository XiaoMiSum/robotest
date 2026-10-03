package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementChangeLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSplitRecordRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementChangeLog;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementSplitRecord;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementChangeLogMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementSplitRecordMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 需求条目服务实现（详设 3.x / 4.x）。
 */
@Service
public class RequirementServiceImpl implements RequirementService {

    private static final Logger log = LoggerFactory.getLogger(RequirementServiceImpl.class);

    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");
    private static final int SYSTEM_VERSION_MAX_LENGTH = 50;
    /** 唯一冲突重试上限（详设 4.1：最多 3 次） */
    private static final int CODE_ALLOCATE_MAX_RETRIES = 3;

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private RequirementChangeLogMapper changeLogMapper;
    @Resource
    private RequirementSplitRecordMapper splitRecordMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private PlatformTransactionManager transactionManager;

    /** AI 任务框架（WP-4.2）接入前为 null，需求侧静默跳过影响分析触发（详设 4.4） */
    @Autowired(required = false)
    private ImpactAnalysisTaskPublisher impactAnalysisTaskPublisher;

    // ---------- 3.2 列表 ----------

    @Override
    public PageResult<RequirementListRespDTO> page(RequirementPageReqDTO pageReq, UUID projectId) {
        List<String> status = splitStrings(pageReq.getStatus());
        List<UUID> moduleIds = splitUuids(pageReq.getModuleIds());
        PageResult<Requirement> page = requirementMapper.findPage(pageReq, projectId, status, moduleIds,
                pageReq.getOwnerId(), pageReq.getSystemVersion(), pageReq.getKeyword());
        if (page.getList().isEmpty()) {
            return new PageResult<>(List.of(), page.getTotal());
        }

        Map<UUID, String> moduleNameById = resolveModuleNames(page.getList());
        Map<UUID, String> ownerNameById = resolveUserNames(page.getList().stream()
                .map(Requirement::getOwnerId).filter(Objects::nonNull).collect(Collectors.toList()));

        List<RequirementListRespDTO> list = page.getList().stream().map(item -> {
            RequirementListRespDTO dto = new RequirementListRespDTO();
            dto.setId(item.getId());
            dto.setCode(item.getCode());
            dto.setTitle(item.getTitle());
            dto.setModuleId(item.getModuleId());
            dto.setModuleName(item.getModuleId() == null ? null : moduleNameById.get(item.getModuleId()));
            dto.setSystemVersion(item.getSystemVersion());
            dto.setStatus(item.getStatus());
            // 覆盖状态联查追溯侧（P3），接入前恒为 null，前端展示「—」（详设 3.2）
            dto.setCoverageStatus(null);
            dto.setPriority(item.getPriority());
            dto.setOwnerId(item.getOwnerId());
            dto.setOwnerName(item.getOwnerId() == null ? null : ownerNameById.get(item.getOwnerId()));
            dto.setSource(item.getSource());
            dto.setUpdatedAt(item.getUpdatedAt());
            return dto;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal());
    }

    // ---------- 3.4 详情 ----------

    @Override
    public RequirementDetailRespDTO getDetail(UUID id, UUID projectId) {
        return buildDetail(requireItem(id, projectId));
    }

    // ---------- 3.3 创建 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequirementDetailRespDTO create(UUID projectId, UUID userId, RequirementCreateReqDTO reqDTO) {
        validateTitle(reqDTO.getTitle());
        validateModule(projectId, reqDTO.getModuleId());
        validateAttributes(reqDTO.getSystemVersion(), reqDTO.getPriority());
        requireWorkspaceMember(projectId, reqDTO.getOwnerId());

        Requirement item = new Requirement();
        item.setProjectId(projectId);
        item.setTitle(reqDTO.getTitle());
        item.setDescription(reqDTO.getDescription());
        item.setModuleId(reqDTO.getModuleId());
        item.setSystemVersion(normalize(reqDTO.getSystemVersion()));
        item.setPriority(normalize(reqDTO.getPriority()));
        item.setOwnerId(reqDTO.getOwnerId());
        item.setTags(reqDTO.getTags());
        item.setStatus(Constants.RequirementStatus.DRAFT);
        item.setSource("manual");
        insertWithCodeAllocation(projectId, item);
        return buildDetail(item);
    }

    // ---------- 3.5 部分更新 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequirementDetailRespDTO update(UUID id, UUID projectId, UUID userId, RequirementUpdateReqDTO reqDTO) {
        Requirement item = requireItem(id, projectId);
        if (Constants.RequirementStatus.ARCHIVED.equals(item.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_ARCHIVED_READONLY);
        }
        validateTitle(reqDTO.getTitle());
        validateModule(projectId, reqDTO.getModuleId());
        validateAttributes(reqDTO.getSystemVersion(), reqDTO.getPriority());
        requireWorkspaceMember(projectId, reqDTO.getOwnerId());

        boolean titleChanged = reqDTO.getTitle() != null && !reqDTO.getTitle().equals(item.getTitle());
        boolean descriptionChanged = reqDTO.getDescription() != null
                && !reqDTO.getDescription().equals(item.getDescription());
        boolean moduleChanged = reqDTO.getModuleId() != null && !reqDTO.getModuleId().equals(item.getModuleId());
        boolean versionChanged = changed(reqDTO.getSystemVersion(), item.getSystemVersion());
        boolean versionCleared = cleared(reqDTO.getSystemVersion(), item.getSystemVersion());
        boolean priorityChanged = changed(reqDTO.getPriority(), item.getPriority());
        boolean priorityCleared = cleared(reqDTO.getPriority(), item.getPriority());
        boolean ownerChanged = reqDTO.getOwnerId() != null && !reqDTO.getOwnerId().equals(item.getOwnerId());
        boolean tagsChanged = reqDTO.getTags() != null && !reqDTO.getTags().equals(item.getTags());

        // 详设 4.4：confirmed 态改题/描述/模块 → 自动转 changed 并触发影响分析；属性变更不动状态
        boolean flipToChanged = Constants.RequirementStatus.CONFIRMED.equals(item.getStatus())
                && (titleChanged || descriptionChanged || moduleChanged);

        Requirement update = new Requirement();
        update.setId(id);
        if (titleChanged) {
            update.setTitle(reqDTO.getTitle());
        }
        if (descriptionChanged) {
            update.setDescription(reqDTO.getDescription());
        }
        if (moduleChanged) {
            update.setModuleId(reqDTO.getModuleId());
        }
        if (versionChanged) {
            update.setSystemVersion(reqDTO.getSystemVersion());
        }
        if (priorityChanged) {
            update.setPriority(reqDTO.getPriority());
        }
        if (ownerChanged) {
            update.setOwnerId(reqDTO.getOwnerId());
        }
        if (tagsChanged) {
            update.setTags(reqDTO.getTags());
        }
        if (flipToChanged) {
            update.setStatus(Constants.RequirementStatus.CHANGED);
        }

        boolean anyChange = titleChanged || descriptionChanged || moduleChanged || versionChanged || versionCleared
                || priorityChanged || priorityCleared || ownerChanged || tagsChanged;
        if (anyChange) {
            // C11：查询结果不作更新载体，只带本次变更字段；显式清空走 wrapper 置 null
            requirementMapper.updateById(update);
            if (versionCleared || priorityCleared) {
                LambdaUpdateWrapperX<Requirement> clear = new LambdaUpdateWrapperX<Requirement>()
                        .eq(Requirement::getId, id);
                clear.set(versionCleared, Requirement::getSystemVersion, null);
                clear.set(priorityCleared, Requirement::getPriority, null);
                requirementMapper.update(null, clear);
            }
            writeChangeLogs(item, reqDTO, userId, titleChanged, descriptionChanged, moduleChanged,
                    versionChanged, versionCleared, priorityChanged, priorityCleared, ownerChanged, tagsChanged);
        }
        if (flipToChanged) {
            publishImpactAnalysis(id, projectId, userId);
        }
        return buildDetail(requirementMapper.selectById(id));
    }

    // ---------- 3.6 确认 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequirementDetailRespDTO confirm(UUID id, UUID projectId, UUID userId) {
        Requirement item = requireItem(id, projectId);
        String status = item.getStatus();
        if (!Constants.RequirementStatus.DRAFT.equals(status)
                && !Constants.RequirementStatus.CHANGED.equals(status)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_STATUS_NOT_ALLOWED);
        }

        Requirement update = new Requirement();
        update.setId(id);
        update.setStatus(Constants.RequirementStatus.CONFIRMED);
        update.setConfirmedAt(LocalDateTime.now());
        requirementMapper.updateById(update);
        writeStatusChangeLog(item, Constants.RequirementStatus.CONFIRMED, userId);

        if (Constants.RequirementStatus.CHANGED.equals(status)) {
            // 详设 3.6：确认时兜底刷新未刷新的影响标记
            publishImpactAnalysis(id, projectId, userId);
        }
        return buildDetail(requirementMapper.selectById(id));
    }

    // ---------- 3.7 归档 / 取消归档 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequirementDetailRespDTO archive(UUID id, UUID projectId, UUID userId) {
        Requirement item = requireItem(id, projectId);
        if (Constants.RequirementStatus.ARCHIVED.equals(item.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_STATUS_NOT_ALLOWED);
        }
        Requirement update = new Requirement();
        update.setId(id);
        update.setStatus(Constants.RequirementStatus.ARCHIVED);
        requirementMapper.updateById(update);
        writeStatusChangeLog(item, Constants.RequirementStatus.ARCHIVED, userId);
        return buildDetail(requirementMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequirementDetailRespDTO unarchive(UUID id, UUID projectId, UUID userId) {
        Requirement item = requireItem(id, projectId);
        if (!Constants.RequirementStatus.ARCHIVED.equals(item.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_STATUS_NOT_ALLOWED);
        }
        // 详设 3.7：取消归档一律回到 draft 由人工重新确认，避免依赖已过期的 changed 态
        Requirement update = new Requirement();
        update.setId(id);
        update.setStatus(Constants.RequirementStatus.DRAFT);
        requirementMapper.updateById(update);
        writeStatusChangeLog(item, Constants.RequirementStatus.DRAFT, userId);
        return buildDetail(requirementMapper.selectById(id));
    }

    // ---------- 3.10 变更记录 ----------

    @Override
    public PageResult<RequirementChangeLogRespDTO> getChangeLogs(UUID id, UUID projectId, PageParam pageParam) {
        requireItem(id, projectId);
        PageResult<RequirementChangeLog> page = changeLogMapper.findPage(pageParam, id);
        if (page.getList().isEmpty()) {
            return new PageResult<>(List.of(), page.getTotal());
        }
        List<UUID> operatorIds = page.getList().stream().map(RequirementChangeLog::getOperatorId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<UUID, String> nameById = resolveUserNames(operatorIds);

        List<RequirementChangeLogRespDTO> list = page.getList().stream().map(entry -> {
            RequirementChangeLogRespDTO dto = new RequirementChangeLogRespDTO();
            dto.setId(entry.getId());
            dto.setChangeType(entry.getChangeType());
            dto.setOperatorId(entry.getOperatorId());
            dto.setOperatorName(entry.getOperatorId() == null ? null : nameById.get(entry.getOperatorId()));
            dto.setBeforeSummary(entry.getBeforeSummary());
            dto.setAfterSummary(entry.getAfterSummary());
            dto.setCreatedAt(entry.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal());
    }

    // ---------- 3.11 拆解记录 ----------

    @Override
    public PageResult<RequirementSplitRecordRespDTO> getSplitLogs(UUID id, UUID projectId, PageParam pageParam) {
        requireItem(id, projectId);
        return buildSplitPage(splitRecordMapper.findPageBySourceRequirementId(pageParam, id));
    }

    @Override
    public PageResult<RequirementSplitRecordRespDTO> getSplitRecords(UUID projectId, String status, String sourceType,
            PageParam pageParam) {
        return buildSplitPage(splitRecordMapper.findPage(pageParam, projectId, status, sourceType));
    }

    // ---------- 内部：编号分配 ----------

    /**
     * 编号分配（详设 4.1）：项目内 max+1 起零填充，依赖 uk_requirement_project_code 防并发，
     * 唯一冲突时重取序号重试（最多 3 次）。每次尝试落在嵌套事务（SAVEPOINT）上，
     * 避免 PG 唯一冲突污染外层事务状态导致重试必然失败。
     */
    private void insertWithCodeAllocation(UUID projectId, Requirement item) {
        TransactionTemplate nested = new TransactionTemplate(transactionManager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        int retries = 0;
        while (true) {
            item.setCode(String.format("REQ-%03d", requirementMapper.selectMaxSeq(projectId) + 1));
            try {
                nested.executeWithoutResult(status -> requirementMapper.insert(item));
                return;
            } catch (DuplicateKeyException e) {
                if (++retries > CODE_ALLOCATE_MAX_RETRIES) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_CODE_CONFLICT);
                }
            }
        }
    }

    // ---------- 内部：变更记录 ----------

    private void writeChangeLogs(Requirement before, RequirementUpdateReqDTO reqDTO, UUID userId,
            boolean titleChanged, boolean descriptionChanged, boolean moduleChanged, boolean versionChanged,
            boolean versionCleared, boolean priorityChanged, boolean priorityCleared, boolean ownerChanged,
            boolean tagsChanged) {
        if (titleChanged) {
            writeChangeLog(before.getId(), userId, "title", summary("title", before.getTitle()),
                    summary("title", reqDTO.getTitle()));
        }
        if (descriptionChanged) {
            writeChangeLog(before.getId(), userId, "description", summary("description", before.getDescription()),
                    summary("description", reqDTO.getDescription()));
        }
        if (moduleChanged) {
            writeChangeLog(before.getId(), userId, "module", summary("moduleId", before.getModuleId()),
                    summary("moduleId", reqDTO.getModuleId()));
        }
        boolean attributeChanged = versionChanged || versionCleared || priorityChanged || priorityCleared
                || ownerChanged || tagsChanged;
        if (attributeChanged) {
            Map<String, Object> beforeAttrs = new LinkedHashMap<>();
            Map<String, Object> afterAttrs = new LinkedHashMap<>();
            if (versionChanged || versionCleared) {
                beforeAttrs.put("systemVersion", before.getSystemVersion());
                afterAttrs.put("systemVersion", versionCleared ? null : reqDTO.getSystemVersion());
            }
            if (priorityChanged || priorityCleared) {
                beforeAttrs.put("priority", before.getPriority());
                afterAttrs.put("priority", priorityCleared ? null : reqDTO.getPriority());
            }
            if (ownerChanged) {
                beforeAttrs.put("ownerId", before.getOwnerId());
                afterAttrs.put("ownerId", reqDTO.getOwnerId());
            }
            if (tagsChanged) {
                beforeAttrs.put("tags", before.getTags());
                afterAttrs.put("tags", reqDTO.getTags());
            }
            writeChangeLog(before.getId(), userId, "attribute", beforeAttrs, afterAttrs);
        }
    }

    private void writeStatusChangeLog(Requirement before, String afterStatus, UUID userId) {
        writeChangeLog(before.getId(), userId, "status", summary("status", before.getStatus()),
                summary("status", afterStatus));
    }

    private void writeChangeLog(UUID requirementId, UUID userId, String changeType, Map<String, Object> before,
            Map<String, Object> after) {
        RequirementChangeLog entry = new RequirementChangeLog();
        entry.setRequirementId(requirementId);
        entry.setOperatorId(userId);
        entry.setChangeType(changeType);
        entry.setBeforeSummary(before);
        entry.setAfterSummary(after);
        changeLogMapper.insert(entry);
    }

    // ---------- 内部：影响分析触发 ----------

    /**
     * 详设 4.4：事务提交后异步提交影响分析任务；提交失败只记日志不回滚需求更新。
     * AI 任务框架未接入（无实现 Bean）时不触发，由 3.6 确认时兜底补提。
     */
    private void publishImpactAnalysis(UUID requirementId, UUID projectId, UUID operatorId) {
        if (impactAnalysisTaskPublisher == null) {
            return;
        }
        Runnable task = () -> {
            try {
                impactAnalysisTaskPublisher.publish(requirementId, projectId, operatorId);
            } catch (Exception e) {
                log.warn("影响分析任务提交失败，需求变更已保存（详设 4.4）", e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    // ---------- 内部：校验 ----------

    private Requirement requireItem(UUID id, UUID projectId) {
        Requirement item = requirementMapper.selectById(id);
        if (item == null || !Objects.equals(item.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NOT_FOUND);
        }
        return item;
    }

    private void validateTitle(String title) {
        if (title != null && title.isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID);
        }
    }

    private void validateModule(UUID projectId, UUID moduleId) {
        if (moduleId == null) {
            return;
        }
        ProjectModule module = projectModuleMapper.selectById(moduleId);
        if (module == null || !Objects.equals(module.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_MODULE_NOT_FOUND);
        }
    }

    /** 字符串属性三态：null 不改、空白清空、有值须合法（详设 3.3 / 3.5 校验规则） */
    private void validateAttributes(String systemVersion, String priority) {
        if (systemVersion != null && !systemVersion.isBlank()
                && systemVersion.trim().length() > SYSTEM_VERSION_MAX_LENGTH) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID);
        }
        if (priority != null && !priority.isBlank() && !PRIORITIES.contains(priority)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID);
        }
    }

    private void requireWorkspaceMember(UUID projectId, UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        WorkspaceUser member = project == null ? null
                : workspaceUserMapper.findByWorkspaceIdAndUserId(project.getWorkspaceId(), ownerId);
        if (member == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID);
        }
    }

    // ---------- 内部：装配 ----------

    private RequirementDetailRespDTO buildDetail(Requirement item) {
        RequirementDetailRespDTO dto = new RequirementDetailRespDTO();
        dto.setId(item.getId());
        dto.setCode(item.getCode());
        dto.setTitle(item.getTitle());
        dto.setDescription(item.getDescription());
        dto.setModuleId(item.getModuleId());
        if (item.getModuleId() != null) {
            ProjectModule module = projectModuleMapper.selectById(item.getModuleId());
            dto.setModuleName(module == null ? null : module.getName());
        }
        dto.setSystemVersion(item.getSystemVersion());
        dto.setStatus(item.getStatus());
        dto.setPriority(item.getPriority());
        dto.setOwnerId(item.getOwnerId());
        if (item.getOwnerId() != null) {
            SysUser owner = userMapper.selectById(item.getOwnerId());
            dto.setOwnerName(owner == null ? null : owner.getName());
        }
        dto.setTags(item.getTags());
        dto.setSource(item.getSource());
        // 来源附件回看由文件管理模块承载（计划外任务），接入前为 null
        dto.setSourceFile(null);
        dto.setConfirmedAt(item.getConfirmedAt());
        dto.setCoverageStatus(null);
        dto.setCreatedAt(item.getCreatedAt());
        dto.setUpdatedAt(item.getUpdatedAt());
        return dto;
    }

    private PageResult<RequirementSplitRecordRespDTO> buildSplitPage(PageResult<RequirementSplitRecord> page) {
        if (page.getList().isEmpty()) {
            return new PageResult<>(List.of(), page.getTotal());
        }
        List<UUID> requirementIds = page.getList().stream().map(RequirementSplitRecord::getSourceRequirementId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<UUID, String> codeById = requirementIds.isEmpty() ? Map.of()
                : requirementMapper.selectBatchIds(requirementIds).stream()
                        .collect(Collectors.toMap(Requirement::getId, Requirement::getCode, (a, b) -> a));

        List<RequirementSplitRecordRespDTO> list = page.getList().stream().map(record -> {
            RequirementSplitRecordRespDTO dto = new RequirementSplitRecordRespDTO();
            dto.setId(record.getId());
            dto.setSourceType(record.getSourceType());
            dto.setSourceRequirementId(record.getSourceRequirementId());
            dto.setSourceRequirementCode(record.getSourceRequirementId() == null ? null
                    : codeById.get(record.getSourceRequirementId()));
            dto.setAiTaskId(record.getAiTaskId());
            dto.setStatus(record.getStatus());
            dto.setAdoptResult(record.getAdoptResult());
            dto.setCreatedAt(record.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal());
    }

    // ---------- 内部：工具 ----------

    private Map<UUID, String> resolveModuleNames(List<Requirement> items) {
        List<UUID> moduleIds = items.stream().map(Requirement::getModuleId).filter(Objects::nonNull)
                .distinct().collect(Collectors.toList());
        if (moduleIds.isEmpty()) {
            return Map.of();
        }
        return projectModuleMapper.selectBatchIds(moduleIds).stream()
                .collect(Collectors.toMap(ProjectModule::getId, ProjectModule::getName, (a, b) -> a));
    }

    private Map<UUID, String> resolveUserNames(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getName, (a, b) -> a));
    }

    private static List<String> splitStrings(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static List<UUID> splitUuids(String csv) {
        List<UUID> ids = new ArrayList<>();
        for (String value : splitStrings(csv)) {
            try {
                ids.add(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
        }
        return ids;
    }

    private static Map<String, Object> summary(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    /** 三态-更新判定：传入有值且与原值不同 */
    private static boolean changed(String incoming, String current) {
        return incoming != null && !incoming.isBlank() && !incoming.equals(current);
    }

    /** 三态-清空判定：传入空白且原值非空 */
    private static boolean cleared(String incoming, String current) {
        return incoming != null && incoming.isBlank() && current != null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
