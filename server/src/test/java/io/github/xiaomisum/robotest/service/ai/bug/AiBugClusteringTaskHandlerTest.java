package io.github.xiaomisum.robotest.service.ai.bug;


import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.ai.BugEmbedding;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.BugEmbeddingMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缺陷聚类任务处理器单测（详细设计 4.3）：贪心聚类的确定性、单缺陷簇归 unclustered、
 * 无向量现场补建、前 maxLabeledClusters 簇 LLM 归纳（超限/失败保留「未命名主题 N」）、
 * 2.3 快照结构（severityDist/moduleDist 聚合）与协作式取消。
 */
@ExtendWith(MockitoExtension.class)
class AiBugClusteringTaskHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final double THRESHOLD = 0.82;

    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private AiAnalysisTaskMapper aiTaskMapper;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private BugEmbeddingMapper bugEmbeddingMapper;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private AiVectorSearchService vectorSearchService;
    @Mock
    private AiConfigService aiConfigService;

    @InjectMocks
    private AiBugClusteringTaskHandler handler;

    private AiAnalysisTask task() {
        AiAnalysisTask task = new AiAnalysisTask();
        task.setId(UUID.randomUUID());
        task.setWorkspaceId(UUID.randomUUID());
        task.setProjectId(PROJECT_ID);
        task.setCreatedBy(UUID.randomUUID());
        return task;
    }

    private Bug bug(UUID id, String title, String severity, UUID moduleId) {
        Bug bug = new Bug();
        bug.setId(id);
        bug.setProjectId(PROJECT_ID);
        bug.setTitle(title);
        bug.setReproSteps("步骤" + title);
        bug.setSeverity(severity);
        bug.setModuleId(moduleId);
        return bug;
    }

    private BugEmbedding embedding(UUID bugId, String vectorText) {
        BugEmbedding embedding = new BugEmbedding();
        embedding.setBugId(bugId);
        embedding.setProjectId(PROJECT_ID);
        embedding.setEmbedding(vectorText);
        return embedding;
    }

    private void vectors(List<Bug> bugs, String... vectorTexts) {
        List<BugEmbedding> embeddings = new ArrayList<>();
        for (int i = 0; i < bugs.size(); i++) {
            embeddings.add(embedding(bugs.get(i).getId(), vectorTexts[i]));
        }
        when(bugEmbeddingMapper.findEmbeddingsByProjectId(PROJECT_ID)).thenReturn(embeddings);
    }

    private void settings(int maxLabeled) {
        when(aiConfigService.getNumberSetting("clustering.similarityThreshold")).thenReturn(THRESHOLD);
        when(aiConfigService.getIntSetting("clustering.maxLabeledClusters")).thenReturn(maxLabeled);
    }

    private void heartbeatOk() {
        when(aiTaskMapper.updateProgressIfRunning(any(), anyInt(), any())).thenReturn(1);
    }

    private AiBugClusteringTaskHandler.ClusterLabelOut label(String text) {
        AiBugClusteringTaskHandler.ClusterLabelOut out = new AiBugClusteringTaskHandler.ClusterLabelOut();
        out.setLabel(text);
        out.setRootCause("疑似" + text + "引发的根因");
        return out;
    }

    @Test
    void execute_greedyClustering_deterministicAndSnapshotAggregates() {
        AiAnalysisTask task = task();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        List<Bug> bugs = List.of(
                bug(a, "登录按钮无响应", "fatal", MODULE_ID),
                bug(b, "登录页面卡死", "serious", null),
                bug(c, "支付回调丢失", "general", MODULE_ID),
                bug(d, "支付金额对不上", "minor", null));
        // A/B 相似、C/D 相似、跨组正交（阈值 0.82）
        vectors(bugs, "[1.0,0.0]", "[0.9,0.0]", "[0.0,1.0]", "[0.141,0.99]");
        settings(10);
        heartbeatOk();
        TestCaseModule module = new TestCaseModule();
        module.setId(MODULE_ID);
        module.setName("登录模块");
        when(testCaseModuleMapper.selectBatchIds(List.of(MODULE_ID))).thenReturn(List.of(module));
        when(bugMapper.findOpenBugsForClustering(PROJECT_ID)).thenReturn(bugs);
        when(aiGatewayService.completeStructured(
                any(), eq(AiFunctionType.BUG_CLUSTERING), any(), any(), any(),
                eq(AiBugClusteringTaskHandler.ClusterLabelOut.class), any()))
                .thenReturn(label("登录态异常"), label("支付金额异常"));

        Map<String, Object> result = handler.execute(task);

        assertEquals(4, result.get("bugCount"));
        assertTrue(((List<?>) result.get("unclustered")).isEmpty());
        List<?> clusters = (List<?>) result.get("clusters");
        assertEquals(2, clusters.size());
        Map<?, ?> clusterA = (Map<?, ?>) clusters.get(0);
        assertEquals("登录态异常", clusterA.get("label"));
        assertEquals(List.of(a.toString(), b.toString()), clusterA.get("bugIds"));
        // severityDist 四键零初始化 + 聚合
        assertEquals(Map.of("fatal", 1, "serious", 1, "general", 0, "minor", 0), clusterA.get("severityDist"));
        // moduleDist：moduleId 为空聚合为「未指定模块」（按数量降序、再按模块名升序，未指定排前）
        List<?> moduleDist = (List<?>) clusterA.get("moduleDist");
        assertEquals(2, moduleDist.size());
        Map<?, ?> unspecified = (Map<?, ?>) moduleDist.get(0);
        assertEquals(null, unspecified.get("moduleId"));
        assertEquals("未指定模块", unspecified.get("moduleName"));
        assertEquals(1, unspecified.get("count"));
        Map<?, ?> named = (Map<?, ?>) moduleDist.get(1);
        assertEquals(MODULE_ID.toString(), named.get("moduleId"));
        assertEquals("登录模块", named.get("moduleName"));
        // 心跳序列：10 → 40 → 每簇归纳进度（40+50*k/N）
        verify(aiTaskMapper).updateProgressIfRunning(eq(task.getId()), eq(10), any());
        verify(aiTaskMapper).updateProgressIfRunning(eq(task.getId()), eq(40), any());
        verify(aiTaskMapper).updateProgressIfRunning(eq(task.getId()), eq(65), any());
        verify(aiTaskMapper).updateProgressIfRunning(eq(task.getId()), eq(90), any());
    }

    @Test
    void execute_singleMemberClusters_movedToUnclustered_noLlmCall() {
        AiAnalysisTask task = task();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<Bug> bugs = List.of(
                bug(a, "登录按钮无响应", "fatal", null),
                bug(b, "支付回调丢失", "general", null));
        vectors(bugs, "[1.0,0.0]", "[0.0,1.0]");
        settings(10);
        heartbeatOk();
        when(bugMapper.findOpenBugsForClustering(PROJECT_ID)).thenReturn(bugs);

        Map<String, Object> result = handler.execute(task);

        assertTrue(((List<?>) result.get("clusters")).isEmpty());
        assertEquals(List.of(a.toString(), b.toString()).stream().sorted().toList(), result.get("unclustered"));
        verify(aiGatewayService, never()).completeStructured(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void execute_labelLimit_exceedsUnlabeledFallback() {
        AiAnalysisTask task = task();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        List<Bug> bugs = List.of(
                bug(a, "登录按钮无响应", "fatal", null),
                bug(b, "登录页面卡死", "serious", null),
                bug(c, "支付回调丢失", "general", null),
                bug(d, "支付金额对不上", "minor", null));
        vectors(bugs, "[1.0,0.0]", "[0.9,0.0]", "[0.0,1.0]", "[0.141,0.99]");
        settings(1);
        heartbeatOk();
        when(bugMapper.findOpenBugsForClustering(PROJECT_ID)).thenReturn(bugs);
        when(aiGatewayService.completeStructured(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(label("登录态异常"));

        Map<String, Object> result = handler.execute(task);

        List<?> clusters = (List<?>) result.get("clusters");
        assertEquals(2, clusters.size());
        assertEquals("登录态异常", ((Map<?, ?>) clusters.get(0)).get("label"));
        // 超出 maxLabeledClusters 的簇保留「未命名主题 N」
        assertEquals("未命名主题 2", ((Map<?, ?>) clusters.get(1)).get("label"));
        // 仅一次 LLM 归纳调用
        verify(aiGatewayService).completeStructured(
                any(), eq(AiFunctionType.BUG_CLUSTERING), any(), any(), any(),
                eq(AiBugClusteringTaskHandler.ClusterLabelOut.class), any());
    }

    @Test
    void execute_missingVectors_builtOnSiteFailedUnclustered() {
        AiAnalysisTask task = task();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<Bug> bugs = List.of(
                bug(a, "登录按钮无响应", "fatal", null),
                bug(b, "支付回调丢失", "general", null));
        // b 无向量且补建失败（findEmbeddingsByProjectId 两次均不返回 b）
        when(bugEmbeddingMapper.findEmbeddingsByProjectId(PROJECT_ID))
                .thenReturn(List.of(embedding(a, "[1.0,0.0]")));
        when(vectorSearchService.indexBug(bugs.get(1))).thenReturn(false);
        settings(10);
        heartbeatOk();
        when(bugMapper.findOpenBugsForClustering(PROJECT_ID)).thenReturn(bugs);

        Map<String, Object> result = handler.execute(task);

        // a 单缺陷簇 → unclustered，b 无向量 → unclustered
        assertTrue(((List<?>) result.get("clusters")).isEmpty());
        assertEquals(List.of(a.toString(), b.toString()).stream().sorted().toList(), result.get("unclustered"));
        verify(vectorSearchService).indexBug(bugs.get(1));
    }

    @Test
    void execute_cancelledAtHeartbeat_returnsPartialWithoutLlm() {
        AiAnalysisTask task = task();
        UUID a = UUID.randomUUID();
        List<Bug> bugs = List.of(bug(a, "登录按钮无响应", "fatal", null));
        vectors(bugs, "[1.0,0.0]");
        // 协作式取消：首个心跳影响行数为 0（此时尚未读取 settings）
        when(aiTaskMapper.updateProgressIfRunning(any(), anyInt(), any())).thenReturn(0);
        when(bugMapper.findOpenBugsForClustering(PROJECT_ID)).thenReturn(bugs);

        Map<String, Object> result = handler.execute(task);

        assertFalse(result.isEmpty());
        assertEquals(1, result.get("bugCount"));
        verify(aiGatewayService, never()).completeStructured(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void execute_llmLabelFail_keepsUnnamedThemeWithoutAborting() {
        AiAnalysisTask task = task();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        List<Bug> bugs = List.of(
                bug(a, "登录按钮无响应", "fatal", null),
                bug(b, "登录页面卡死", "serious", null),
                bug(c, "支付回调丢失", "general", null),
                bug(d, "支付金额对不上", "minor", null));
        vectors(bugs, "[1.0,0.0]", "[0.9,0.0]", "[0.0,1.0]", "[0.141,0.99]");
        settings(10);
        heartbeatOk();
        when(bugMapper.findOpenBugsForClustering(PROJECT_ID)).thenReturn(bugs);
        when(aiGatewayService.completeStructured(
                any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("mock llm fail"))
                .thenReturn(label("登录态异常"));

        Map<String, Object> result = handler.execute(task);

        List<?> clusters = (List<?>) result.get("clusters");
        assertEquals(2, clusters.size());
        assertEquals("未命名主题 1", ((Map<?, ?>) clusters.get(0)).get("label"));
        assertEquals("登录态异常", ((Map<?, ?>) clusters.get(1)).get("label"));
    }
}
