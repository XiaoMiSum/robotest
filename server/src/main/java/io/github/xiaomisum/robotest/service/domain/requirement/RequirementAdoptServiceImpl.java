package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementChangeLog;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementSplitRecord;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.requirement.RequirementChangeLogMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementSplitRecordMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ArtifactAdopter;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 拆解采纳落库实现（详设 4.5）：承接 requirement_split 产物确认（3.6.1 承接服务），
 * 与确认记录同事务（3.6.5）——编号重试耗尽等失败整体回滚该项，不产生半截数据（4.5.4）。
 */
@Component
public class RequirementAdoptServiceImpl implements RequirementAdoptService, ArtifactAdopter {

    private static final String TYPE = "requirement_split";
    private static final String TASK_TYPE_IMPORT = "requirement_import";
    private static final String SOURCE_TYPE_REQUIREMENT = "requirement";
    private static final String SOURCE_TYPE_DOCUMENT = "document";
    /** 需求条目 source 取值（2.2：manual / import / requirement）：导入采纳写 import */
    private static final String SOURCE_IMPORT = "import";
    /** 拆解记录状态（详设 2.4）：pending / adopted / rejected */
    private static final String SPLIT_PENDING = "pending";
    private static final String SPLIT_ADOPTED = "adopted";
    private static final String SPLIT_REJECTED = "rejected";
    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");
    private static final int SYSTEM_VERSION_MAX_LENGTH = 50;

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private RequirementSplitRecordMapper splitRecordMapper;
    @Resource
    private RequirementChangeLogMapper changeLogMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private RequirementCodeAllocator codeAllocator;
    /** 向量索引（WP-4.5）：采纳新增条目入索引、来源归档移出索引（详设 4.4） */
    @Resource
    private VectorIndexService vectorIndexService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        AiTask task = context.task();
        boolean importTask = TASK_TYPE_IMPORT.equals(task.getType());
        UUID requirementId = importTask ? null : parseRequirementId(task);
        RequirementSplitRecord record = findOrCreateRecord(task, requirementId, context.projectId(), importTask);
        Requirement source = loadSource(record, context.projectId());

        UUID createdId = null;
        Map<String, Object> adoptedRef = null;
        if (!Constants.AiArtifactAction.REJECTED.equals(context.action())) {
            createdId = createRequirement(context, record, source).getId();
            adoptedRef = Map.of("requirementId", createdId.toString());
            vectorIndexService.upsertRequirement(createdId, context.operatorId());
        }

        Map<String, Object> merged = mergeAdoptResult(record, context, createdId);
        boolean allProcessed = isAllProcessed(task, merged);
        String status = !allProcessed ? SPLIT_PENDING
                : adoptedCount(merged) > 0 ? SPLIT_ADOPTED : SPLIT_REJECTED;

        persistRecord(record, task, merged, status);
        archiveSourceIfDone(record, source, merged, allProcessed, context);
        return new AdoptOutcome(createdId, adoptedRef);
    }

    // ---------- 记录与来源定位 ----------

    private static UUID parseRequirementId(AiTask task) {
        return parseTaskUuid(task, "requirementId");
    }

    private static UUID parseFileId(AiTask task) {
        return parseTaskUuid(task, "fileId");
    }

    private static UUID parseTaskUuid(AiTask task, String key) {
        Object raw = task.getInput() == null ? null : task.getInput().get(key);
        if (raw == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /**
     * 定位拆解记录：任务行直查优先；任务重试产生新任务行而记录仍指原任务时按来源兜底
     * （adopt 时回指当前任务）；直提任务资源（无记录）在采纳时补建档，保证 split-logs 可反查。
     */
    private RequirementSplitRecord findOrCreateRecord(AiTask task, UUID requirementId, UUID projectId,
            boolean importTask) {
        List<RequirementSplitRecord> byTask = splitRecordMapper.selectByAiTaskId(task.getId());
        if (!byTask.isEmpty()) {
            return byTask.get(0);
        }
        if (importTask) {
            // 导入记录提交路径必建；重试换行 / 直提时按项目内同源文件的待确认记录兜底（3.8）
            UUID fileId = parseFileId(task);
            for (RequirementSplitRecord candidate : splitRecordMapper.selectPendingImportByProject(projectId)) {
                if (fileId.equals(candidate.getSourceFileId())) {
                    return candidate;
                }
            }
            RequirementSplitRecord fresh = new RequirementSplitRecord();
            fresh.setProjectId(projectId);
            fresh.setSourceType(SOURCE_TYPE_DOCUMENT);
            fresh.setSourceFileId(fileId);
            fresh.setAiTaskId(task.getId());
            fresh.setStatus(SPLIT_PENDING);
            return fresh;
        }
        List<RequirementSplitRecord> bySource = splitRecordMapper.selectPendingSplitBySource(requirementId);
        if (!bySource.isEmpty()) {
            return bySource.get(0);
        }
        RequirementSplitRecord fresh = new RequirementSplitRecord();
        fresh.setProjectId(projectId);
        fresh.setSourceType(SOURCE_TYPE_REQUIREMENT);
        fresh.setSourceRequirementId(requirementId);
        fresh.setAiTaskId(task.getId());
        fresh.setStatus(SPLIT_PENDING);
        return fresh;
    }

    private Requirement loadSource(RequirementSplitRecord record, UUID projectId) {
        if (record.getSourceRequirementId() == null) {
            return null;
        }
        Requirement source = requirementMapper.selectById(record.getSourceRequirementId());
        if (source == null) {
            return null;
        }
        if (!projectId.equals(source.getProjectId())) {
            // 活动项目与任务归属不一致（4.6 越权口径）：不允许跨项目采纳落库
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NOT_FOUND);
        }
        return source;
    }

    // ---------- 需求落库（4.5） ----------

    private Requirement createRequirement(AdoptContext context, RequirementSplitRecord record, Requirement source) {
        Map<String, Object> content = effectiveContent(context);
        String title = asString(content.get("title"));
        if (title == null || title.isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID);
        }
        Requirement item = new Requirement();
        item.setProjectId(context.projectId());
        item.setTitle(title.trim());
        String description = asString(content.get("description"));
        item.setDescription(description == null || description.isBlank() ? null : description);
        item.setModuleId(resolveModuleId(content.get("moduleId"), context.projectId()));
        item.setPriority(resolvePriority(content.get("priority")));
        item.setSystemVersion(resolveSystemVersion(context, source));
        item.setStatus(Constants.RequirementStatus.CONFIRMED);
        item.setConfirmedAt(LocalDateTime.now());
        // 需求来源（2.2）：导入写 import，拆分沿用记录来源 requirement
        item.setSource(TASK_TYPE_IMPORT.equals(context.task().getType())
                ? SOURCE_IMPORT : record.getSourceType());
        item.setSourceFileId(record.getSourceFileId());
        codeAllocator.insertWithCodeAllocation(context.projectId(), item);
        return item;
    }

    /** 编辑后采纳取确认请求内容，普通采纳取产物原内容（3.6.5） */
    private static Map<String, Object> effectiveContent(AdoptContext context) {
        Map<String, Object> edited = context.content();
        if (edited != null && !edited.isEmpty()) {
            return edited;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        if (context.artifact().get("content") instanceof Map<?, ?> raw) {
            raw.forEach((k, v) -> content.put(String.valueOf(k), v));
        }
        return content;
    }

    private UUID resolveModuleId(Object raw, UUID projectId) {
        String value = asString(raw);
        if (value == null || value.isBlank()) {
            return null;
        }
        UUID moduleId;
        try {
            moduleId = UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_MODULE_NOT_FOUND);
        }
        ProjectModule module = projectModuleMapper.selectById(moduleId);
        if (module == null || !projectId.equals(module.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_MODULE_NOT_FOUND);
        }
        return moduleId;
    }

    private static String resolvePriority(Object raw) {
        String value = asString(raw);
        if (value == null || value.isBlank()) {
            return null;
        }
        String priority = value.trim();
        if (!PRIORITIES.contains(priority)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID);
        }
        return priority;
    }

    /**
     * 系统版本（4.5）：确认面板值优先，空白为显式清空；未设置时拆分继承原条目，
     * 导入回退任务 documentMeta.detectedVersion（缺失或超长留空待手工补录），两者皆无留空。
     */
    private static String resolveSystemVersion(AdoptContext context, Requirement source) {
        String incoming = context.targetSystemVersion();
        if (incoming == null) {
            return source != null ? source.getSystemVersion() : detectedVersion(context.task());
        }
        String version = incoming.trim();
        if (version.isEmpty()) {
            return null;
        }
        if (version.length() > SYSTEM_VERSION_MAX_LENGTH) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID);
        }
        return version;
    }

    /** 文档级识别结果（3.8 documentMeta）：与导入侧清洗同口径，不可信值一律 null */
    private static String detectedVersion(AiTask task) {
        if (task == null || task.getResult() == null
                || !(task.getResult().get("documentMeta") instanceof Map<?, ?> meta)) {
            return null;
        }
        String version = asString(meta.get("detectedVersion"));
        if (version == null) {
            return null;
        }
        version = version.trim();
        return version.isEmpty() || version.length() > SYSTEM_VERSION_MAX_LENGTH ? null : version;
    }

    // ---------- 拆解记录回写与归档 ----------

    /** 合并 adoptResult（详设 3.11 样例）：adopted 逐条带生成需求 ID，rejected 仅记 key，operatorId 取最新操作人 */
    private static Map<String, Object> mergeAdoptResult(RequirementSplitRecord record, AdoptContext context,
            UUID createdId) {
        List<Object> adopted = new ArrayList<>();
        List<Object> rejected = new ArrayList<>();
        Map<String, Object> existing = record.getAdoptResult();
        if (existing != null) {
            if (existing.get("adopted") instanceof List<?> list) {
                adopted.addAll(list);
            }
            if (existing.get("rejected") instanceof List<?> list) {
                rejected.addAll(list);
            }
        }
        String artifactKey = asString(context.artifact().get("key"));
        if (Constants.AiArtifactAction.REJECTED.equals(context.action())) {
            rejected.add(artifactKey);
        } else {
            adopted.add(Map.of("artifactKey", artifactKey, "requirementId", createdId.toString()));
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("adopted", adopted);
        merged.put("rejected", rejected);
        merged.put("operatorId", context.operatorId().toString());
        return merged;
    }

    /** 全部产物处理完（3.11 status 口径）：已处理键集覆盖任务 result.artifacts 全量键 */
    private static boolean isAllProcessed(AiTask task, Map<String, Object> merged) {
        Set<String> allKeys = new HashSet<>();
        if (task.getResult() != null && task.getResult().get("artifacts") instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> artifact && artifact.get("key") != null) {
                    allKeys.add(String.valueOf(artifact.get("key")));
                }
            }
        }
        if (allKeys.isEmpty()) {
            return false;
        }
        Set<String> processed = new HashSet<>();
        collectAdoptedKeys(merged.get("adopted"), processed);
        if (merged.get("rejected") instanceof List<?> rejected) {
            rejected.forEach(key -> {
                if (key != null) {
                    processed.add(String.valueOf(key));
                }
            });
        }
        return processed.containsAll(allKeys);
    }

    private static void collectAdoptedKeys(Object adopted, Set<String> out) {
        if (!(adopted instanceof List<?> list)) {
            return;
        }
        for (Object element : list) {
            if (element instanceof Map<?, ?> entry && entry.get("artifactKey") != null) {
                out.add(String.valueOf(entry.get("artifactKey")));
            }
        }
    }

    private static int adoptedCount(Map<String, Object> merged) {
        return merged.get("adopted") instanceof List<?> list ? list.size() : 0;
    }

    private void persistRecord(RequirementSplitRecord record, AiTask task, Map<String, Object> merged,
            String status) {
        if (record.getId() == null) {
            // 直提路径无既有记录：采纳时补建档（3.9 提交路径已建的记录带 id 走更新）
            record.setAdoptResult(merged);
            record.setStatus(status);
            splitRecordMapper.insert(record);
            return;
        }
        LambdaUpdateWrapperX<RequirementSplitRecord> wrapper = new LambdaUpdateWrapperX<RequirementSplitRecord>();
        wrapper.eq(RequirementSplitRecord::getId, record.getId())
                .set(RequirementSplitRecord::getAdoptResult, merged)
                .set(RequirementSplitRecord::getStatus, status)
                .set(RequirementSplitRecord::getUpdatedAt, LocalDateTime.now());
        if (!task.getId().equals(record.getAiTaskId())) {
            wrapper.set(RequirementSplitRecord::getAiTaskId, task.getId());
        }
        splitRecordMapper.update(null, wrapper);
    }

    /** 来源为 requirement 且全部处理完、至少采纳一条时归档原条目（4.5.2）；全部驳回不归档（4.5.3） */
    private void archiveSourceIfDone(RequirementSplitRecord record, Requirement source, Map<String, Object> merged,
            boolean allProcessed, AdoptContext context) {
        if (!allProcessed || adoptedCount(merged) == 0 || source == null
                || !SOURCE_TYPE_REQUIREMENT.equals(record.getSourceType())
                || Constants.RequirementStatus.ARCHIVED.equals(source.getStatus())) {
            return;
        }
        requirementMapper.update(null, new LambdaUpdateWrapperX<Requirement>()
                .eq(Requirement::getId, source.getId())
                .set(Requirement::getStatus, Constants.RequirementStatus.ARCHIVED)
                .set(Requirement::getUpdatedAt, LocalDateTime.now()));
        RequirementChangeLog entry = new RequirementChangeLog();
        entry.setRequirementId(source.getId());
        entry.setOperatorId(context.operatorId());
        entry.setChangeType("status");
        entry.setBeforeSummary(summary("status", source.getStatus()));
        entry.setAfterSummary(summary("status", Constants.RequirementStatus.ARCHIVED));
        changeLogMapper.insert(entry);
        // 详设 4.4：来源归档同步移出索引
        vectorIndexService.removeRequirement(source.getId());
    }

    private static Map<String, Object> summary(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
