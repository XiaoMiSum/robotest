package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiBugDedupReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugDedupRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.gateway.AiRateLimiter;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService.BugDedupHit;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缺陷语义查重单测（详细设计 3.2/4.2）：语义可用走向量检索并映射处理人名；
 * 语义降级/调用异常切关键词模式（semanticDegraded=true、similarity=null），不抛错打断录入。
 */
@ExtendWith(MockitoExtension.class)
class AiBugDedupServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ASSIGNEE_ID = UUID.randomUUID();

    @Mock
    private AiVectorSearchService vectorSearchService;
    @Mock
    private AiConfigService aiConfigService;
    @Mock
    private AiRateLimiter aiRateLimiter;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private AiBugDedupServiceImpl service;

    private void semantic(String value) {
        AiStatusRespDTO status = new AiStatusRespDTO();
        status.setSemanticSearch(value);
        when(aiConfigService.getStatus()).thenReturn(status);
    }

    private void semanticSettings(int topK, double threshold) {
        when(aiConfigService.getIntSetting("dedup.topK")).thenReturn(topK);
        when(aiConfigService.getNumberSetting("dedup.similarityThreshold")).thenReturn(threshold);
    }

    private void keywordSettings(int topK) {
        when(aiConfigService.getIntSetting("dedup.topK")).thenReturn(topK);
    }

    private AiBugDedupReqDTO req(String title) {
        AiBugDedupReqDTO dto = new AiBugDedupReqDTO();
        dto.setTitle(title);
        return dto;
    }

    private Bug openBug(UUID id, String title, UUID assigneeId) {
        Bug bug = new Bug();
        bug.setId(id);
        bug.setProjectId(PROJECT_ID);
        bug.setTitle(title);
        bug.setStatus(Constants.BugStatus.ACTIVE);
        bug.setAssigneeId(assigneeId);
        return bug;
    }

    @Test
    void semanticAvailable_returnsHitsWithAssigneeName() {
        semantic(Constants.AiSemanticSearch.AVAILABLE);
        semanticSettings(5, 0.75);
        UUID bugId = UUID.randomUUID();
        when(vectorSearchService.searchSimilarBugs(PROJECT_ID, "登录超时", null, null, 5, 0.75))
                .thenReturn(List.of(new BugDedupHit(bugId, "登录接口超时", "active", ASSIGNEE_ID, 0.87)));
        SysUser user = new SysUser();
        user.setId(ASSIGNEE_ID);
        user.setName("李四");
        when(userMapper.selectBatchIds(List.of(ASSIGNEE_ID))).thenReturn(List.of(user));

        AiBugDedupRespDTO resp = service.dedup(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录超时"));

        assertFalse(resp.isSemanticDegraded());
        assertEquals(1, resp.getItems().size());
        AiBugDedupRespDTO.Item item = resp.getItems().get(0);
        assertEquals(bugId, item.getBugId());
        assertEquals("active", item.getStatus());
        assertEquals("李四", item.getAssigneeName());
        assertEquals(0.87, item.getSimilarity());
        // 语义检索入口限流
        verify(aiRateLimiter).checkAndRecord(USER_ID, AiFunctionType.BUG_DEDUP);
    }

    @Test
    void semanticCallFails_fallsBackToKeywordDegraded() {
        semantic(Constants.AiSemanticSearch.AVAILABLE);
        semanticSettings(5, 0.75);
        when(vectorSearchService.searchSimilarBugs(PROJECT_ID, "登录 超时", null, null, 5, 0.75))
                .thenThrow(new RuntimeException("embedding 调用失败"));
        UUID bugId = UUID.randomUUID();
        when(bugMapper.findOpenBugsForDedup(PROJECT_ID))
                .thenReturn(List.of(openBug(bugId, "登录接口响应超过30秒", null)));

        AiBugDedupRespDTO resp = service.dedup(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录 超时"));

        assertTrue(resp.isSemanticDegraded());
        assertEquals(bugId, resp.getItems().get(0).getBugId());
        assertNull(resp.getItems().get(0).getSimilarity());
    }

    @Test
    void semanticDegraded_status_usesKeywordMode() {
        semantic(Constants.AiSemanticSearch.DEGRADED);
        keywordSettings(5);
        UUID bugId = UUID.randomUUID();
        when(bugMapper.findOpenBugsForDedup(PROJECT_ID))
                .thenReturn(List.of(openBug(bugId, "登录按钮无响应", ASSIGNEE_ID)));

        AiBugDedupRespDTO resp = service.dedup(USER_ID, WORKSPACE_ID, PROJECT_ID, req("!!!"));

        assertTrue(resp.isSemanticDegraded());
        // 标题无可分词（无 ≥2 字符片段）→ 关键词为空 → 无命中（降级不抛错）
        assertTrue(resp.getItems().isEmpty());
        verify(vectorSearchService, never()).searchSimilarBugs(any(), any(), any(), any(), anyInt(), anyDouble());
    }

    @Test
    void keywordMode_ranksByMatchedTokenCountAndLimitsTopK() {
        semantic(Constants.AiSemanticSearch.UNAVAILABLE);
        keywordSettings(2);
        UUID high1 = UUID.randomUUID();
        UUID high2 = UUID.randomUUID();
        UUID low = UUID.randomUUID();
        when(bugMapper.findOpenBugsForDedup(PROJECT_ID)).thenReturn(List.of(
                openBug(high1, "登录失败且页面无任何响应", null),
                openBug(high2, "登录超时后报网关错误", null),
                openBug(low, "注册接口返回 500", null)));

        AiBugDedupRespDTO resp = service.dedup(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录 失败"));

        // 仅取 topK=2，且命中词数更多的排前（登录+失败 双命中在前）
        assertEquals(2, resp.getItems().size());
        assertEquals(high1, resp.getItems().get(0).getBugId());
        assertNull(resp.getItems().get(0).getSimilarity());
    }

    @Test
    void keywordMode_excludesExcludeBugId() {
        semantic(Constants.AiSemanticSearch.UNAVAILABLE);
        keywordSettings(5);
        UUID excluded = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(bugMapper.findOpenBugsForDedup(PROJECT_ID)).thenReturn(List.of(
                openBug(excluded, "登录失败", null),
                openBug(other, "登录失败重试", null)));

        AiBugDedupReqDTO dto = req("登录 失败");
        dto.setExcludeBugId(excluded);
        AiBugDedupRespDTO resp = service.dedup(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

        assertEquals(1, resp.getItems().size());
        assertEquals(other, resp.getItems().get(0).getBugId());
    }

    @Test
    void keywordMode_tieBrokenByInputOrder() {
        semantic(Constants.AiSemanticSearch.UNAVAILABLE);
        keywordSettings(5);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(bugMapper.findOpenBugsForDedup(PROJECT_ID)).thenReturn(List.of(
                openBug(first, "登录失败重试", null),
                openBug(second, "注册登录联调", null)));

        AiBugDedupRespDTO resp = service.dedup(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录 失败"));

        assertEquals(first, resp.getItems().get(0).getBugId());
    }
}
