package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugAnalysisQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugMetricsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugTrendsRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.domain.requirement.RequirementSuggestionParser.parseJsonObject;

/**
 * 缺陷趋势摘要处理器（缺陷分析详设 3.4）：基于同一次统计快照生成只读摘要，
 * 必附数据来源引用（缺引用为不合格产出，走 1000018117 任务失败重试），不写入任何数据。
 */
@Component
public class BugTrendSummaryHandler implements TaskHandler {

    public static final String TYPE = "bug_trend_summary";

    private static final String SYSTEM_PROMPT = "你是资深质量分析师，只输出 JSON，不输出解释或代码块标记以外的任何文字。";
    private static final String PERMISSION = "bug:view";
    private static final Set<String> GROUP_BY_VALUES = Set.of("none", "module", "severity", "type");

    @Resource
    private BugAnalysisService bugAnalysisService;
    @Resource
    private BugMapper bugMapper;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深软件质量分析师，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                根据统计快照生成本期缺陷趋势摘要：
                1. text 用 3~6 句中文概括新增 / 关闭 / 存量走势、环比变化、集中模块与质量风险；
                2. 结论只能基于快照中的数字，不得臆造快照外的数据；
                3. citations 必须引用快照关联的缺陷（type 固定为 bug，id 取缺陷 id，title 取缺陷标题），至少 1 条。
                输出结构（JSON 对象）：
                {"text":"…","citations":[{"type":"bug","id":"…","title":"…"}]}

                统计快照：
                {{statsContext}}
                """;
    }

    @Override
    public void checkPermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains(PERMISSION)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    /** 任务入口的范围校验与查询接口同口径（详设 3.2/3.3）：非法范围 1000018281，groupBy 越界 1000018285 */
    @Override
    public void validateInput(Map<String, Object> input) {
        String groupBy = asString(input == null ? null : input.get("groupBy"));
        if (groupBy != null && !GROUP_BY_VALUES.contains(groupBy.trim())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_GROUP_BY_INVALID);
        }
        parseDate(input, "from");
        parseDate(input, "to");
        LocalDate from = dateOf(input, "from");
        LocalDate to = dateOf(input, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_RANGE_INVALID);
        }
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        Map<String, Object> input = context.getInput();

        context.report(10, "统计趋势与度量");
        BugAnalysisQueryReqDTO query = new BugAnalysisQueryReqDTO();
        query.setFrom(asString(input.get("from")));
        query.setTo(asString(input.get("to")));
        query.setGroupBy(asString(input.get("groupBy")));
        BugTrendsRespDTO trends = bugAnalysisService.trends(query, context.getProjectId(), context.getUserId());
        BugMetricsRespDTO metrics = bugAnalysisService.metrics(query, context.getProjectId(), context.getUserId());
        List<Bug> citedPool = citedBugs(query, context.getProjectId());

        context.report(40, "构建统计快照");
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("statsContext", statsContext(trends, metrics, citedPool));

        context.report(50, "模型生成趋势摘要");
        AiChatReply reply = context.chat(SYSTEM_PROMPT, context.prompt(defaultPrompt(), variables));

        context.report(85, "解析趋势摘要");
        Map<String, Object> artifact = sanitize(reply, citedPool);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", List.of(artifact));
        return new TaskResult(result, reply.tokensIn(), reply.tokensOut());
    }

    /** 引用候选池：区间内新增缺陷（引用只允许指向本次统计涉及的缺陷，标题也以此为准） */
    private List<Bug> citedBugs(BugAnalysisQueryReqDTO query, UUID projectId) {
        BugAnalysisQueryReqDTO range = new BugAnalysisQueryReqDTO();
        range.setFrom(query.getFrom());
        range.setTo(query.getTo());
        List<Bug> analysis = bugMapper.findForAnalysis(projectId,
                startOfDay(range.getFrom()), endExclusiveOfDay(range.getTo()));
        return analysis.stream()
                .filter(bug -> bug.getCreatedAt() != null
                        && !bug.getCreatedAt().isBefore(startOfDay(range.getFrom()))
                        && bug.getCreatedAt().isBefore(endExclusiveOfDay(range.getTo())))
                .toList();
    }

    private static String statsContext(BugTrendsRespDTO trends, BugMetricsRespDTO metrics, List<Bug> citedPool) {
        StringBuilder builder = new StringBuilder();
        builder.append("时间范围：").append(String.join(" ~ ", trends.getAxis().isEmpty()
                ? List.of("无") : List.of(trends.getAxis().get(0), trends.getAxis().get(trends.getAxis().size() - 1))));
        builder.append("（分组：").append(trends.getGroupBy()).append("）\n");
        for (BugTrendsRespDTO.Series series : trends.getSeries()) {
            builder.append("系列[").append(series.getLabel()).append("] 新增=")
                    .append(sum(series.getCreated()))
                    .append(" 关闭=").append(sum(series.getClosed()))
                    .append(" 期末存量=").append(lastActive(series)).append('\n');
        }
        BugMetricsRespDTO.FixDuration fix = metrics.getFixDuration();
        builder.append("修复时长：样本=").append(fix.getSample())
                .append(" 均值(h)=").append(fix.getAvgHours())
                .append(" p50(h)=").append(fix.getP50Hours())
                .append(" p90(h)=").append(fix.getP90Hours()).append('\n');
        builder.append("重开率=").append(metrics.getReopenRate())
                .append(" 重复缺陷占比=").append(metrics.getDuplicateRate()).append('\n');
        appendDist(builder, "严重等级分布", metrics.getSeverityDist());
        appendDist(builder, "类型分布", metrics.getTypeDist());
        appendDist(builder, "模块分布", metrics.getModuleDist());
        builder.append("可引用缺陷（id|标题）：\n");
        citedPool.forEach(bug -> builder.append(bug.getId()).append('|').append(nvl(bug.getTitle())).append('\n'));
        return builder.toString();
    }

    private static void appendDist(StringBuilder builder, String name, List<BugMetricsRespDTO.DistItem> dist) {
        builder.append(name).append("：");
        builder.append(dist.stream()
                .map(item -> item.getKey() + "=" + item.getCount())
                .collect(Collectors.joining("、")));
        builder.append('\n');
    }

    private static int sum(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).sum();
    }

    private static int lastActive(BugTrendsRespDTO.Series series) {
        List<Integer> active = series.getActive();
        return active.isEmpty() ? 0 : active.get(active.size() - 1);
    }

    /** 清洗：引用必须指向引用池内缺陷；缺引用为不合格产出（详设 3.4），按模型失败落任务重试 */
    private Map<String, Object> sanitize(AiChatReply reply, List<Bug> citedPool) {
        Map<String, Object> parsed = asMap(parseJsonObject(reply.content()));
        Map<UUID, Bug> pool = citedPool.stream()
                .collect(Collectors.toMap(Bug::getId, bug -> bug, (left, right) -> left));

        List<Map<String, Object>> citations = new ArrayList<>();
        if (parsed.get("citations") instanceof List<?> raw) {
            for (Object element : raw) {
                if (!(element instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> citation = asMap(element);
                UUID bugId = null;
                try {
                    bugId = UUID.fromString(String.valueOf(citation.get("id")).trim());
                } catch (RuntimeException e) {
                    continue;
                }
                Bug bug = pool.get(bugId);
                if (bug == null) {
                    continue;
                }
                Map<String, Object> normalized = new LinkedHashMap<>();
                normalized.put("type", "bug");
                normalized.put("id", bugId.toString());
                normalized.put("title", nvl(bug.getTitle()));
                citations.add(normalized);
            }
        }
        if (citations.isEmpty()) {
            // 必附数据来源引用，缺引用的产物为不合格产出，不进入结果（详设 3.4）
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    "模型未产出有效的数据来源引用");
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("text", nvl(asString(parsed.get("text"))));
        content.put("citations", citations);

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("key", "summary-1");
        artifact.put("kind", Constants.AiArtifactKind.BUG_SUMMARY);
        artifact.put("title", "缺陷趋势摘要");
        artifact.put("content", content);
        // 只读摘要，无确认动作（详设 3.4）
        artifact.put("confirmStatus", "not_applicable");
        return artifact;
    }

    // ---------- 范围解析（与 BugAnalysisServiceImpl 同口径） ----------

    private static void parseDate(Map<String, Object> input, String field) {
        dateOf(input, field);
    }

    private static LocalDate dateOf(Map<String, Object> input, String field) {
        String raw = asString(input == null ? null : input.get(field));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (RuntimeException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_RANGE_INVALID);
        }
    }

    private static java.time.LocalDateTime startOfDay(String date) {
        return date == null || date.isBlank()
                ? LocalDate.now(java.time.ZoneOffset.UTC).minusDays(29).atStartOfDay()
                : LocalDate.parse(date).atStartOfDay();
    }

    private static java.time.LocalDateTime endExclusiveOfDay(String date) {
        return date == null || date.isBlank()
                ? LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1).atStartOfDay()
                : LocalDate.parse(date).plusDays(1).atStartOfDay();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
