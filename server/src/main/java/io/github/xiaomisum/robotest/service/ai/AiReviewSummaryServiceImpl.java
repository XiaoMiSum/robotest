package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiReviewSummaryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewSummaryRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewModuleSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewRecord;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewNodeSnapshotMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewRecordMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.provider.OpenAiCompatProvider;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiReviewSummaryServiceImpl implements AiReviewSummaryService {

    /** Markdown 总结长度上限（超出截断落库，提示词已约束篇幅，属防御性处理，详细设计 4.2） */
    static final int SUMMARY_MAX_LENGTH = 8000;
    /** 不通过用例采样上限（按评论长度降序，详细设计 4.2） */
    static final int FAIL_SAMPLE_LIMIT = 60;

    private static final String TASK_INSTRUCTION = """
            请基于业务数据中的评审统计与不通过用例采样，输出一份面向测试负责人的评审总结。""";

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private AiAnalysisTaskMapper aiTaskMapper;
    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    @Resource
    private TestReviewModuleSnapshotMapper reviewModuleSnapshotMapper;
    @Resource
    private TestReviewRecordMapper reviewRecordMapper;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public SseEmitter generateSummary(UUID userId, UUID workspaceId, UUID projectId, UUID reviewId,
            AiReviewSummaryReqDTO reqDTO) {
        TestReview review = requireInitiator(reviewId, userId);
        // 仅「已完成」评审可生成摘要（3.2.1）
        if (!Constants.Status.COMPLETED.equals(review.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TARGET_STATE_INVALID);
        }
        // 同评审同时只允许一个进行中的摘要生成（6005）
        if (!aiTaskMapper.lockInProgress(Constants.AiTaskType.REVIEW_SUMMARY, reviewId, null).isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_DUPLICATE);
        }

        List<TestReviewNodeSnapshot> caseNodes = reviewNodeSnapshotMapper.listAssociatedByReviewId(reviewId,
                Constants.NodeType.CASE);
        AiReviewSummaryRespDTO.Statistics statistics = computeStatistics(reviewId, caseNodes);
        String businessData = buildBusinessData(review, statistics, caseNodes);

        // 落库 running 任务（同步 SSE，不进 executor 队列）
        AiAnalysisTask task = new AiAnalysisTask();
        task.setWorkspaceId(workspaceId);
        task.setProjectId(projectId);
        task.setType(Constants.AiTaskType.REVIEW_SUMMARY);
        task.setTargetId(reviewId);
        task.setStatus(Constants.AiTaskStatus.RUNNING);
        task.setProgress(0);
        task.setCreatedBy(userId);
        aiTaskMapper.insert(task);
        UUID taskId = task.getId();

        AiCallContext context = new AiCallContext(userId, workspaceId, projectId, reqDTO.getModelId());
        // 摘要为自由 Markdown，不请求 json_object 格式（生成类中唯一非 JSON 输出场景，4.2）
        SseEmitter emitter = aiGatewayService.stream(context, AiFunctionType.REVIEW_SUMMARY,
                TASK_INSTRUCTION, businessData, ChatCallOptions.defaults(),
                statisticsPrelude(statistics), doneAssembler(reviewId, taskId, statistics));

        // 失败/断开时清理进行中记录（成功路径已在 doneAssembler 置 success，此处对 running 才生效）；
        // onCompletion 支持多回调（DefaultCallback.addDelegate），不覆盖网关的取消回调
        emitter.onCompletion(() -> aiTaskMapper.markFailedIfRunning(taskId, "生成未完成"));
        emitter.onError(e -> aiTaskMapper.markFailedIfRunning(taskId, "生成异常中断"));
        return emitter;
    }

    @Override
    public AiReviewSummaryRespDTO getSummary(UUID reviewId, UUID userId) {
        requireInitiator(reviewId, userId);
        AiAnalysisTask task = aiTaskMapper.findLatestSuccessByTypeAndTarget(
                Constants.AiTaskType.REVIEW_SUMMARY, reviewId);
        if (task == null || task.getResult() == null) {
            return null;
        }
        AiReviewSummaryRespDTO dto = objectMapper.convertValue(task.getResult(), AiReviewSummaryRespDTO.class);
        dto.setGeneratedAt(task.getUpdatedAt());
        return dto;
    }

    /** 连接建立后即发 statistics 帧（SQL 精确计算，不依赖 LLM，3.2.1） */
    private Consumer<SseEmitter> statisticsPrelude(AiReviewSummaryRespDTO.Statistics statistics) {
        return emitter -> {
            try {
                emitter.send(SseEmitter.event().name("statistics").data(statistics, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                // 客户端已断开：交由网关后续边界感知取消，无需在此处理
                throw new OpenAiCompatProvider.StreamCancelledException();
            }
        };
    }

    /**
     * done 帧组装：Markdown 非 JSON 输出，剥离噪声后按 8000 字符截断；
     * 覆盖式落库（markSuccess + 逻辑删除旧 success）。
     */
    private Function<String, Object> doneAssembler(UUID reviewId, UUID taskId,
            AiReviewSummaryRespDTO.Statistics statistics) {
        return fullContent -> {
            String cleaned = AiOutputValidator.stripNoise(fullContent);
            String markdown = cleaned == null ? "" : cleaned;
            if (markdown.length() > SUMMARY_MAX_LENGTH) {
                markdown = markdown.substring(0, SUMMARY_MAX_LENGTH) + "\n\n（内容超长已截断）";
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("statistics", statistics);
            result.put("summaryMarkdown", markdown);
            aiTaskMapper.markSuccessIfRunning(taskId, JsonUtils.toJsonString(result));
            aiTaskMapper.deleteSuccessExcept(Constants.AiTaskType.REVIEW_SUMMARY, reviewId, taskId);
            return result;
        };
    }

    private TestReview requireInitiator(UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        if (!review.getInitiatorId().equals(userId.toString())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        return review;
    }

    /** 统计：按 lastMark 分桶 + 按文档聚合不通过数（SQL 精确计算，4.2） */
    AiReviewSummaryRespDTO.Statistics computeStatistics(UUID reviewId, List<TestReviewNodeSnapshot> caseNodes) {
        long pass = 0;
        long fail = 0;
        long pending = 0;
        for (TestReviewNodeSnapshot node : caseNodes) {
            String mark = node.getLastMark();
            if (mark == null || mark.isBlank()) {
                pending++;
            } else if (Constants.ReviewMark.PASS.equals(mark)) {
                pass++;
            } else {
                fail++;
            }
        }
        long total = caseNodes.size();

        Map<UUID, String> docNameById = reviewModuleSnapshotMapper.listByReviewId(reviewId).stream()
                .filter(m -> Constants.ModuleType.DOCUMENT.equals(m.getType()))
                .collect(Collectors.toMap(TestReviewModuleSnapshot::getId, TestReviewModuleSnapshot::getName,
                        (a, b) -> a));
        Map<String, Long> failCountByDoc = caseNodes.stream()
                .filter(n -> Constants.ReviewMark.FAIL.equals(n.getLastMark()))
                .collect(Collectors.groupingBy(
                        n -> docNameById.getOrDefault(n.getDocumentSnapshotId(), "未命名文档"),
                        LinkedHashMap::new, Collectors.counting()));
        List<AiReviewSummaryRespDTO.FailByDocument> failByDocument = failCountByDoc.entrySet().stream()
                .map(e -> {
                    AiReviewSummaryRespDTO.FailByDocument item = new AiReviewSummaryRespDTO.FailByDocument();
                    item.setDocumentName(e.getKey());
                    item.setFailCount(e.getValue());
                    return item;
                })
                .sorted(Comparator.comparingLong(AiReviewSummaryRespDTO.FailByDocument::getFailCount).reversed())
                .toList();

        AiReviewSummaryRespDTO.Statistics statistics = new AiReviewSummaryRespDTO.Statistics();
        statistics.setTotalCases(total);
        statistics.setPassCount(pass);
        statistics.setFailCount(fail);
        statistics.setPendingCount(pending);
        statistics.setPassRate(total > 0 ? Math.round(pass * 10000.0 / total) / 100.0 : 0.0);
        statistics.setFailByDocument(failByDocument);
        return statistics;
    }

    /**
     * LLM 输入：评审基本信息 + 统计 + 不通过用例采样（标题 + 最近一条评论，≤60，按评论长度降序）。
     */
    private String buildBusinessData(TestReview review, AiReviewSummaryRespDTO.Statistics stats,
            List<TestReviewNodeSnapshot> caseNodes) {
        // 一次性加载评审全部记录，按节点取最近一条非空评论
        Map<UUID, String> latestCommentByNode = reviewRecordMapper.listByReviewId(review.getId()).stream()
                .filter(r -> r.getComment() != null && !r.getComment().isBlank())
                .sorted(Comparator.comparing(TestReviewRecord::getCreatedAt))
                .collect(Collectors.toMap(TestReviewRecord::getSnapshotNodeId, TestReviewRecord::getComment,
                        (older, newer) -> newer));

        List<String> failSamples = caseNodes.stream()
                .filter(n -> Constants.ReviewMark.FAIL.equals(n.getLastMark()))
                .map(n -> {
                    String comment = latestCommentByNode.getOrDefault(n.getId(), "");
                    return "- 用例：" + n.getTitle() + (comment.isEmpty() ? "" : "；评论：" + comment);
                })
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(FAIL_SAMPLE_LIMIT)
                .toList();

        StringBuilder data = new StringBuilder();
        data.append("【评审信息】\n标题：").append(review.getTitle());
        if (review.getDescription() != null && !review.getDescription().isBlank()) {
            data.append("；描述：").append(review.getDescription());
        }
        data.append('\n');
        data.append("【评审统计】\n")
                .append("关联用例总数：").append(stats.getTotalCases())
                .append("，通过：").append(stats.getPassCount())
                .append("，不通过：").append(stats.getFailCount())
                .append("，待评审：").append(stats.getPendingCount())
                .append("，通过率：").append(stats.getPassRate()).append("%\n");
        if (!failByDocumentEmpty(stats)) {
            data.append("按文档不通过分布：")
                    .append(stats.getFailByDocument().stream()
                            .map(d -> d.getDocumentName() + " " + d.getFailCount() + " 条")
                            .collect(Collectors.joining("、")))
                    .append('\n');
        }
        if (!failSamples.isEmpty()) {
            data.append("【不通过用例采样】\n").append(String.join("\n", failSamples)).append('\n');
        }
        return data.toString();
    }

    private boolean failByDocumentEmpty(AiReviewSummaryRespDTO.Statistics stats) {
        return stats.getFailByDocument() == null || stats.getFailByDocument().isEmpty();
    }
}
