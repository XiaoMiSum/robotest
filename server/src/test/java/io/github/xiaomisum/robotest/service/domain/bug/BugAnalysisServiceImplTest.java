package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDraftSuggestRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugAnalysisServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private BugMapper bugMapper;
    @Mock
    private VectorSearchService vectorSearchService;

    @InjectMocks
    private BugAnalysisServiceImpl service;

    private static Bug bug(UUID projectId, String status) {
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(projectId);
        bug.setStatus(status);
        return bug;
    }

    private static AiVectorSearchHitRespDTO hit(UUID bugId, double distance) {
        AiVectorSearchHitRespDTO hit = new AiVectorSearchHitRespDTO();
        hit.setEntityId(bugId);
        hit.setEntityType(VectorIndexService.TYPE_BUG);
        hit.setDistance(distance);
        hit.setContent("登录页点击登录按钮后无任何响应");
        return hit;
    }

    @Test
    void findDuplicates_vectorUnavailable_returnsEmpty() {
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE));

        assertTrue(service.findDuplicates("登录无响应", null, PROJECT_ID, USER_ID).isEmpty());
    }

    @Test
    void findDuplicates_mapsHitsSkipsDeletedAndDedupes() {
        Bug alive = bug(PROJECT_ID, "active");
        alive.setTitle("登录按钮无反应");
        UUID deletedId = UUID.randomUUID();
        when(vectorSearchService.search(eq("登录无响应\n1. 打开登录页"), eq(List.of(PROJECT_ID)),
                eq(VectorIndexService.TYPE_BUG), eq(5), eq(USER_ID)))
                .thenReturn(List.of(hit(alive.getId(), 0.13), hit(alive.getId(), 0.2), hit(deletedId, 0.3)));
        when(bugMapper.listByIds(List.of(alive.getId(), deletedId))).thenReturn(List.of(alive));

        List<BugDraftSuggestRespDTO.DuplicateItem> items =
                service.findDuplicates("登录无响应", "1. 打开登录页", PROJECT_ID, USER_ID);

        assertEquals(1, items.size());
        BugDraftSuggestRespDTO.DuplicateItem item = items.get(0);
        assertEquals(alive.getId(), item.getBugId());
        assertEquals("登录按钮无反应", item.getTitle());
        assertEquals("active", item.getStatus());
        assertEquals(0.87, item.getSimilarity());
        assertEquals("登录页点击登录按钮后无任何响应", item.getBasis());
        verify(vectorSearchService).search(eq("登录无响应\n1. 打开登录页"), eq(List.of(PROJECT_ID)),
                eq(VectorIndexService.TYPE_BUG), eq(5), eq(USER_ID));
    }

    @Test
    void findDuplicates_outOfProjectBugFiltered() {
        Bug otherProject = bug(UUID.randomUUID(), "active");
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any()))
                .thenReturn(List.of(hit(otherProject.getId(), 0.1)));
        when(bugMapper.listByIds(List.of(otherProject.getId()))).thenReturn(List.of(otherProject));

        assertTrue(service.findDuplicates("登录无响应", null, PROJECT_ID, USER_ID).isEmpty());
    }
}
