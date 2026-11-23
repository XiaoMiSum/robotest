package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDuplicateCheckReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDuplicateCheckRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugAnalysisServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private BugMapper bugMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private VectorSearchService vectorSearchService;

    @InjectMocks
    private BugAnalysisServiceImpl service;

    private static Bug bug(String status, LocalDateTime createdAt) {
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(PROJECT_ID);
        bug.setStatus(status);
        bug.setCreatedAt(createdAt);
        return bug;
    }

    // ---------- 3.6 录入重复检测 ----------

    private static BugDuplicateCheckReqDTO checkReq(String title, String steps, Integer limit) {
        BugDuplicateCheckReqDTO reqDTO = new BugDuplicateCheckReqDTO();
        reqDTO.setTitle(title);
        reqDTO.setSteps(steps);
        reqDTO.setLimit(limit);
        return reqDTO;
    }

    private static AiVectorSearchHitRespDTO hit(UUID bugId, double distance) {
        AiVectorSearchHitRespDTO hit = new AiVectorSearchHitRespDTO();
        hit.setEntityId(bugId);
        hit.setEntityType("bug");
        hit.setDistance(distance);
        hit.setContent("登录页点击登录按钮后无任何响应");
        return hit;
    }

    @Test
    void checkDuplicates_blankTitle_rejected() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.checkDuplicates(checkReq("  ", null, null), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_CHECK_INPUT_INVALID.code(), error.getCode());
    }

    @Test
    void checkDuplicates_limitOutOfRange_rejected() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.checkDuplicates(checkReq("登录无响应", null, 99), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_CHECK_INPUT_INVALID.code(), error.getCode());
    }

    @Test
    void checkDuplicates_vectorNotReady_mapsToUnavailable() {
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any()))
                .thenThrow(ServiceExceptionUtilSupplier.unavailable());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.checkDuplicates(checkReq("登录无响应", null, null), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_RAG_UNAVAILABLE.code(), error.getCode());
    }

    @Test
    void checkDuplicates_mapsHitsAndSkipsDeleted() {
        Bug alive = bug("active", LocalDateTime.parse("2026-10-01T10:00:00"));
        alive.setTitle("登录按钮无反应");
        UUID deletedId = UUID.randomUUID();
        when(vectorSearchService.search(any(), eq(List.of(PROJECT_ID)), eq("bug"), eq(5), eq(USER_ID)))
                .thenReturn(List.of(hit(alive.getId(), 0.13), hit(alive.getId(), 0.2),
                        hit(deletedId, 0.3)));
        when(bugMapper.listByIds(List.of(alive.getId(), deletedId))).thenReturn(List.of(alive));

        BugDuplicateCheckRespDTO resp = service.checkDuplicates(
                checkReq("登录无响应", "1. 打开登录页", null), PROJECT_ID, USER_ID);

        assertEquals(1, resp.getList().size());
        BugDuplicateCheckRespDTO.Item item = resp.getList().get(0);
        assertEquals(alive.getId(), item.getBugId());
        assertEquals("登录按钮无反应", item.getTitle());
        assertEquals(0.87, item.getSimilarity());
        assertEquals("登录页点击登录按钮后无任何响应", item.getBasis());
    }

    /** 向量门禁未就绪的两个错误码（1000018119 / 1000018122） */
    private static final class ServiceExceptionUtilSupplier {
        private static ServiceException unavailable() {
            return xyz.migoo.framework.common.exception.ServiceExceptionUtil
                    .get(ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE);
        }
    }
}
