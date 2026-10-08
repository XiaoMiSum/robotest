package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugAnalysisQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDuplicateCheckReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDuplicateCheckRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugMetricsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugTrendsRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 缺陷分析实现（详设 3.2 / 3.3 / 3.8、4.1 口径）：按当前项目实时聚合，
 * 时间字段取 UTC 日历日；比率与分布的分母统一为区间内新增缺陷（详设 3.3）。
 */
@Service
public class BugAnalysisServiceImpl implements BugAnalysisService {

    private static final int DEFAULT_RANGE_DAYS = 30;
    /** 逐日轴与全量装载的规模护栏：跨度超过一年按范围非法拒绝（详设未约定，防全表扫描） */
    private static final int MAX_RANGE_DAYS = 366;
    private static final String GROUP_BY_DEFAULT = "none";
    private static final String GROUP_NONE_KEY = "none";
    private static final String LABEL_ALL = "全部";
    private static final String LABEL_UNSET_MODULE = "未指定模块";
    private static final String LABEL_UNSET = "未指定";

    private static final int TITLE_MAX_LENGTH = 300;
    private static final int DEFAULT_DUPLICATE_LIMIT = 5;
    private static final int MAX_DUPLICATE_LIMIT = 20;
    private static final int BASIS_MAX_LENGTH = 200;
    /** 比率精度：四位小数，避免展示层出现无意义尾数 */
    private static final int RATE_SCALE = 10_000;

    private static final Set<String> GROUP_BY_VALUES = Set.of("none", "module", "severity", "type");

    @Resource
    private BugMapper bugMapper;
    @Resource
    private BugLogMapper bugLogMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private VectorSearchService vectorSearchService;

    // ==================== 3.2 趋势 ====================

    @Override
    public BugTrendsRespDTO trends(BugAnalysisQueryReqDTO query, UUID projectId, UUID userId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        Range range = resolveRange(query);
        String groupBy = resolveGroupBy(query);
        List<Bug> bugs = bugMapper.findForAnalysis(projectId, range.fromStart(), range.toEnd());

        List<LocalDate> days = range.days();
        BugTrendsRespDTO resp = new BugTrendsRespDTO();
        resp.setAxis(days.stream().map(LocalDate::toString).toList());
        resp.setGroupBy(groupBy);
        resp.setSeries(buildSeries(groupBy, bugs, days, projectId));
        return resp;
    }

    private List<BugTrendsRespDTO.Series> buildSeries(String groupBy, List<Bug> bugs, List<LocalDate> days,
            UUID projectId) {
        if ("none".equals(groupBy)) {
            return List.of(buildSeries("all", LABEL_ALL, bugs, days));
        }
        Map<String, String> labels = "module".equals(groupBy) ? moduleLabels(projectId) : Map.of();
        String unsetLabel = "module".equals(groupBy) ? LABEL_UNSET_MODULE : LABEL_UNSET;

        Map<String, List<Bug>> groups = new LinkedHashMap<>();
        for (Bug bug : bugs) {
            String key = groupKey(groupBy, bug);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(bug);
        }
        // 空组不产序列，前端按 axis 对齐补 0；排序按组内条数降序、键升序，保证同参结果稳定
        List<String> orderedKeys = groups.keySet().stream()
                .sorted(Comparator.comparingInt((String key) -> groups.get(key).size()).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        List<BugTrendsRespDTO.Series> series = new ArrayList<>();
        for (String key : orderedKeys) {
            String label = GROUP_NONE_KEY.equals(key) ? unsetLabel : labels.getOrDefault(key, key);
            series.add(buildSeries(key, label, groups.get(key), days));
        }
        return series;
    }

    private static String groupKey(String groupBy, Bug bug) {
        Object value = switch (groupBy) {
            case "module" -> bug.getModuleId();
            case "severity" -> bug.getSeverity();
            default -> bug.getBugType();
        };
        return value == null ? GROUP_NONE_KEY : String.valueOf(value);
    }

    private Map<String, String> moduleLabels(UUID projectId) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (ProjectModule module : projectModuleMapper.listByProjectId(projectId)) {
            if (module.getName() != null) {
                labels.put(String.valueOf(module.getId()), module.getName());
            }
        }
        return labels;
    }

    private static BugTrendsRespDTO.Series buildSeries(String key, String label, List<Bug> group,
            List<LocalDate> days) {
        int size = days.size();
        int[] created = new int[size];
        int[] closed = new int[size];
        int[] active = new int[size];
        for (int i = 0; i < size; i++) {
            LocalDateTime dayStart = days.get(i).atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            for (Bug bug : group) {
                if (!bug.getCreatedAt().isBefore(dayStart) && bug.getCreatedAt().isBefore(dayEnd)) {
                    created[i]++;
                }
                if (bug.getClosedAt() != null && !bug.getClosedAt().isBefore(dayStart)
                        && bug.getClosedAt().isBefore(dayEnd)) {
                    closed[i]++;
                }
                // 逐日存量 = 创建 ≤ 当日 24:00 且（未关闭或关闭晚于当日）（详设 3.3）
                if (bug.getCreatedAt().isBefore(dayEnd)
                        && (bug.getClosedAt() == null || bug.getClosedAt().isAfter(dayEnd))) {
                    active[i]++;
                }
            }
        }
        BugTrendsRespDTO.Series series = new BugTrendsRespDTO.Series();
        series.setKey(key);
        series.setLabel(label);
        series.setCreated(toList(created));
        series.setClosed(toList(closed));
        series.setActive(toList(active));
        return series;
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

    // ==================== 3.3 质量度量 ====================

    @Override
    public BugMetricsRespDTO metrics(BugAnalysisQueryReqDTO query, UUID projectId, UUID userId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        Range range = resolveRange(query);
        List<Bug> analysis = bugMapper.findForAnalysis(projectId, range.fromStart(), range.toEnd());
        List<Bug> createdInRange = analysis.stream()
                .filter(bug -> !bug.getCreatedAt().isBefore(range.fromStart())
                        && bug.getCreatedAt().isBefore(range.toEnd()))
                .toList();

        BugMetricsRespDTO resp = new BugMetricsRespDTO();
        long total = createdInRange.size();
        long reopened = createdInRange.stream()
                .filter(bug -> bug.getReopenCount() != null && bug.getReopenCount() >= 1)
                .count();
        long duplicated = createdInRange.stream()
                .filter(bug -> Constants.BugResolution.DUPLICATE.equals(bug.getResolution()))
                .count();
        resp.setReopenRate(rate(reopened, total));
        resp.setDuplicateRate(rate(duplicated, total));
        resp.setSeverityDist(distribution(createdInRange.stream()
                .map(bug -> nvl(bug.getSeverity(), GROUP_NONE_KEY))
                .collect(Collectors.toList())));
        resp.setTypeDist(distribution(createdInRange.stream()
                .map(bug -> nvl(bug.getBugType(), GROUP_NONE_KEY))
                .collect(Collectors.toList())));
        resp.setModuleDist(moduleDistribution(createdInRange, projectId));
        resp.setFixDuration(fixDuration(analysis, range));
        return resp;
    }

    private List<BugMetricsRespDTO.DistItem> moduleDistribution(List<Bug> createdInRange, UUID projectId) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (ProjectModule module : projectModuleMapper.listByProjectId(projectId)) {
            if (module.getName() != null) {
                names.put(module.getId(), module.getName());
            }
        }
        return distribution(createdInRange.stream()
                .map(bug -> bug.getModuleId() == null
                        ? LABEL_UNSET_MODULE
                        : names.getOrDefault(bug.getModuleId(), LABEL_UNSET_MODULE))
                .collect(Collectors.toList()));
    }

    private static List<BugMetricsRespDTO.DistItem> distribution(List<String> keys) {
        Map<String, Long> counts = keys.stream()
                .collect(Collectors.groupingBy(key -> key, LinkedHashMap::new, Collectors.counting()));
        List<BugMetricsRespDTO.DistItem> items = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> {
                    BugMetricsRespDTO.DistItem item = new BugMetricsRespDTO.DistItem();
                    item.setKey(entry.getKey());
                    item.setCount(entry.getValue());
                    items.add(item);
                });
        return items;
    }

    /**
     * 修复时长（详设 3.3）：样本取修复时间落区间的缺陷，激活时刻取该次修复前最近一次激活日志，
     * 无则取创建时间；同缺陷多次流转按首次修复计。
     */
    private BugMetricsRespDTO.FixDuration fixDuration(List<Bug> analysis, Range range) {
        List<Bug> resolvedInRange = analysis.stream()
                .filter(bug -> bug.getResolvedAt() != null
                        && !bug.getResolvedAt().isBefore(range.fromStart())
                        && bug.getResolvedAt().isBefore(range.toEnd()))
                .toList();

        List<Double> hours = new ArrayList<>();
        if (!resolvedInRange.isEmpty()) {
            Map<UUID, List<LocalDateTime>> reopenTimes = bugLogMapper
                    .findReopensByBugIds(resolvedInRange.stream().map(Bug::getId).toList()).stream()
                    .filter(log -> log.getCreatedAt() != null)
                    .collect(Collectors.groupingBy(BugLog::getBugId,
                            Collectors.mapping(BugLog::getCreatedAt, Collectors.toList())));
            for (Bug bug : resolvedInRange) {
                LocalDateTime activatedAt = activationOf(bug, reopenTimes.getOrDefault(bug.getId(), List.of()));
                Duration duration = Duration.between(activatedAt, bug.getResolvedAt());
                // 时钟回拨或脏日志产生的负值不计入样本，避免拉低均值
                if (!duration.isNegative()) {
                    hours.add(duration.toMinutes() / 60.0);
                }
            }
        }
        hours.sort(Double::compareTo);

        BugMetricsRespDTO.FixDuration fix = new BugMetricsRespDTO.FixDuration();
        fix.setSample(hours.size());
        fix.setAvgHours(hours.isEmpty() ? 0.0
                : round1(hours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)));
        fix.setP50Hours(round1(percentile(hours, 0.5)));
        fix.setP90Hours(round1(percentile(hours, 0.9)));
        return fix;
    }

    private static LocalDateTime activationOf(Bug bug, List<LocalDateTime> reopenTimes) {
        LocalDateTime resolvedAt = bug.getResolvedAt();
        return reopenTimes.stream()
                .filter(time -> !time.isAfter(resolvedAt))
                .max(Comparator.naturalOrder())
                .orElseGet(bug::getCreatedAt);
    }

    /** 最近秩百分位：样本为空由调用方补 0（详设 3.3 分母为 0 返回 0） */
    private static double percentile(List<Double> sorted, double ratio) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(ratio * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static double rate(long hit, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((double) hit / total * RATE_SCALE) / (double) RATE_SCALE;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // ==================== 3.8 录入重复检测 ====================

    @Override
    public BugDuplicateCheckRespDTO checkDuplicates(BugDuplicateCheckReqDTO reqDTO, UUID projectId, UUID userId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        String title = reqDTO.getTitle() == null ? "" : reqDTO.getTitle().trim();
        if (title.isEmpty() || title.length() > TITLE_MAX_LENGTH) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_CHECK_INPUT_INVALID);
        }
        int limit = reqDTO.getLimit() == null ? DEFAULT_DUPLICATE_LIMIT : reqDTO.getLimit();
        if (limit < 1 || limit > MAX_DUPLICATE_LIMIT) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_CHECK_INPUT_INVALID);
        }
        String queryText = StringUtils.hasText(reqDTO.getSteps()) ? title + "\n" + reqDTO.getSteps() : title;

        List<AiVectorSearchHitRespDTO> hits;
        try {
            hits = vectorSearchService.search(queryText, List.of(projectId), VectorIndexService.TYPE_BUG,
                    limit, userId);
        } catch (ServiceException e) {
            // 未配置 / 重建中按向量能力未就绪口径回执，入口据此置灰（详设 3.8）
            if (e.getCode() == ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED.code()
                    || e.getCode() == ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE.code()) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_RAG_UNAVAILABLE);
            }
            throw e;
        }

        List<UUID> hitIds = hits.stream()
                .map(AiVectorSearchHitRespDTO::getEntityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Bug> bugById = hitIds.isEmpty() ? Map.of()
                : bugMapper.listByIds(hitIds).stream()
                        .filter(bug -> projectId.equals(bug.getProjectId()))
                        .collect(Collectors.toMap(Bug::getId, bug -> bug, (left, right) -> left));

        // 同一缺陷多分块命中只回首条（检索已按相似度排序）；已删除缺陷跳过
        Set<UUID> seen = new LinkedHashSet<>();
        List<BugDuplicateCheckRespDTO.Item> items = new ArrayList<>();
        for (AiVectorSearchHitRespDTO hit : hits) {
            UUID bugId = hit.getEntityId();
            if (bugId == null || !seen.add(bugId)) {
                continue;
            }
            Bug bug = bugById.get(bugId);
            if (bug == null) {
                continue;
            }
            BugDuplicateCheckRespDTO.Item item = new BugDuplicateCheckRespDTO.Item();
            item.setBugId(bug.getId());
            item.setTitle(bug.getTitle());
            item.setStatus(bug.getStatus());
            // similarity 由距离单调换算，仅作排序展示，阈值不对外承诺（详设 3.8）
            item.setSimilarity(hit.getDistance() == null ? null
                    : Math.round((1 - hit.getDistance()) * RATE_SCALE) / (double) RATE_SCALE);
            item.setBasis(basisOf(hit.getContent()));
            items.add(item);
        }

        BugDuplicateCheckRespDTO resp = new BugDuplicateCheckRespDTO();
        resp.setList(items);
        return resp;
    }

    private static String basisOf(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String trimmed = content.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= BASIS_MAX_LENGTH ? trimmed : trimmed.substring(0, BASIS_MAX_LENGTH) + "…";
    }

    // ==================== 范围与分组解析 ====================

    private record Range(LocalDate from, LocalDate to, LocalDateTime fromStart, LocalDateTime toEnd) {

        List<LocalDate> days() {
            List<LocalDate> days = new ArrayList<>();
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                days.add(day);
            }
            return days;
        }
    }

    /** 缺省最近 30 天（含当日）；解析失败、起止倒置或跨度超护栏一律 1000018281（详设 3.2） */
    private static Range resolveRange(BugAnalysisQueryReqDTO query) {
        LocalDate to = parseDate(query == null ? null : query.getTo());
        LocalDate from = parseDate(query == null ? null : query.getFrom());
        if (to == null) {
            to = LocalDate.now(ZoneOffset.UTC);
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_RANGE_DAYS - 1L);
        }
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_RANGE_INVALID);
        }
        return new Range(from, to, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    private static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_RANGE_INVALID);
        }
    }

    private static String resolveGroupBy(BugAnalysisQueryReqDTO query) {
        String groupBy = query == null || !StringUtils.hasText(query.getGroupBy())
                ? GROUP_BY_DEFAULT
                : query.getGroupBy().trim();
        if (!GROUP_BY_VALUES.contains(groupBy)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_GROUP_BY_INVALID);
        }
        return groupBy;
    }

    private static String nvl(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
