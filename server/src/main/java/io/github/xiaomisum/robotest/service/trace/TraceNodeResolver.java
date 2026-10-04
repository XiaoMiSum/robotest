package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceNodeRefRespDTO;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 追溯节点解析器（详设 3.4）：按节点类型分发到对应服务批量解析逻辑外键的标题与版本，
 * 并在建边 / 改挂 / 链路起点处校验节点存在性与项目归属（2.2 设计说明 1：引用完整性由 Service 层保证）。
 */
@Component
public class TraceNodeResolver {

    /** 解析结果：title 为 null 表示节点已不存在（展示占位）；version 仅测试用例有值 */
    public record NodeInfo(String title, String version) {
    }

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private TestPlanMapper testPlanMapper;

    /** 批量解析（type + id）→ 节点信息；未命中或已删除的节点不出现在结果中 */
    public Map<String, NodeInfo> resolve(Collection<TraceNodeRefRespDTO> refs) {
        Map<String, NodeInfo> result = new HashMap<>();
        if (refs == null || refs.isEmpty()) {
            return result;
        }
        Map<String, List<UUID>> idsByType = refs.stream()
                .filter(ref -> ref.getType() != null && ref.getId() != null)
                .collect(Collectors.groupingBy(TraceNodeRefRespDTO::getType,
                        LinkedHashMap::new,
                        Collectors.mapping(TraceNodeRefRespDTO::getId, Collectors.toList())));

        for (Map.Entry<String, List<UUID>> entry : idsByType.entrySet()) {
            String type = entry.getKey();
            List<UUID> ids = entry.getValue().stream().distinct().toList();
            switch (type) {
                case Constants.TraceNodeType.REQUIREMENT -> requirementMapper.listByIds(ids)
                        .forEach(item -> result.put(key(type, item.getId()),
                                new NodeInfo(requirementTitle(item), null)));
                case Constants.TraceNodeType.MODULE -> projectModuleMapper.listByIds(ids)
                        .forEach(item -> result.put(key(type, item.getId()),
                                new NodeInfo(item.getName(), null)));
                case Constants.TraceNodeType.MINDMAP_DOCUMENT -> testCaseDocumentMapper.listByIds(ids)
                        .forEach(item -> result.put(key(type, item.getId()),
                                new NodeInfo(item.getName(), null)));
                case Constants.TraceNodeType.TEST_CASE -> testCaseNodeMapper.listByIds(ids)
                        .forEach(item -> result.put(key(type, item.getId()),
                                new NodeInfo(item.getTitle(), caseVersion(item))));
                case Constants.TraceNodeType.TEST_REVIEW -> testReviewMapper.listByIds(ids)
                        .forEach(item -> result.put(key(type, item.getId()),
                                new NodeInfo(item.getTitle(), null)));
                case Constants.TraceNodeType.TEST_PLAN -> testPlanMapper.listByIds(ids)
                        .forEach(item -> result.put(key(type, item.getId()),
                                new NodeInfo(item.getName(), null)));
                default -> {
                    // 未知类型不解析，调用方按占位展示（详设 3.4）
                }
            }
        }
        return result;
    }

    /**
     * 校验单个节点存在且属于当前项目（不存在与越权一律 1000018152，不泄露存在性），
     * 返回可展示标题；不支持的节点类型返回 1000018160。
     */
    public String requireTitle(String type, UUID id, UUID projectId) {
        return requireNode(type, id, projectId).title();
    }

    /** 同 {@link #requireTitle}，额外返回版本标识（建边记录 target_version 用） */
    public NodeInfo requireNode(String type, UUID id, UUID projectId) {
        boolean owned = switch (type) {
            case Constants.TraceNodeType.REQUIREMENT -> {
                Requirement item = requirementMapper.selectById(id);
                yield item != null && Objects.equals(item.getProjectId(), projectId);
            }
            case Constants.TraceNodeType.MODULE -> {
                ProjectModule item = projectModuleMapper.selectById(id);
                yield item != null && Objects.equals(item.getProjectId(), projectId);
            }
            case Constants.TraceNodeType.MINDMAP_DOCUMENT -> {
                TestCaseDocument item = testCaseDocumentMapper.selectById(id);
                yield item != null && Objects.equals(item.getProjectId(), projectId);
            }
            case Constants.TraceNodeType.TEST_CASE -> ownedByProject(id, projectId);
            case Constants.TraceNodeType.TEST_REVIEW -> {
                TestReview item = testReviewMapper.selectById(id);
                yield item != null && Objects.equals(item.getProjectId(), projectId);
            }
            case Constants.TraceNodeType.TEST_PLAN -> {
                TestPlan item = testPlanMapper.selectById(id);
                yield item != null && Objects.equals(item.getProjectId(), projectId);
            }
            default -> throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_NODE_TYPE_UNSUPPORTED);
        };
        if (!owned) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_NODE_NOT_FOUND);
        }
        return resolve(List.of(TraceNodeRefRespDTO.of(type, id, null)))
                .getOrDefault(key(type, id), new NodeInfo(null, null));
    }

    /** 用例节点不带项目字段，经所属文档的项目归属判定 */
    private boolean ownedByProject(UUID caseNodeId, UUID projectId) {
        TestCaseNode node = testCaseNodeMapper.selectById(caseNodeId);
        if (node == null) {
            return false;
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(node.getDocumentId());
        return document != null && Objects.equals(document.getProjectId(), projectId);
    }

    /** 用例当前版本标识（详设 2.2：无版本概念的类型为 NULL，用例以 v{n} 表达内容版本） */
    public static String caseVersion(TestCaseNode node) {
        return node == null || node.getVersion() == null ? null : "v" + node.getVersion();
    }

    /** 需求展示标题：编号 + 空格 + 标题（详设 3.3 root 示例 "REQ-001 登录验证码"） */
    public static String requirementTitle(Requirement item) {
        if (item == null) {
            return null;
        }
        return item.getCode() == null ? item.getTitle() : item.getCode() + " " + item.getTitle();
    }

    /** 从边集合抽出全部端点引用，供批量解析 */
    public static List<TraceNodeRefRespDTO> refsOf(Collection<TraceEdge> edges) {
        Map<String, TraceNodeRefRespDTO> unique = new LinkedHashMap<>();
        for (TraceEdge edge : edges) {
            unique.putIfAbsent(key(edge.getSourceType(), edge.getSourceId()),
                    TraceNodeRefRespDTO.of(edge.getSourceType(), edge.getSourceId(), null));
            unique.putIfAbsent(key(edge.getTargetType(), edge.getTargetId()),
                    TraceNodeRefRespDTO.of(edge.getTargetType(), edge.getTargetId(), null));
        }
        return List.copyOf(unique.values());
    }

    public static String key(String type, UUID id) {
        return type + ":" + id;
    }
}
