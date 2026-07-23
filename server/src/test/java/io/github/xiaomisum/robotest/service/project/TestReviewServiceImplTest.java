package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestReviewDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestReviewListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestReviewRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestReviewSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.entity.*;
import io.github.xiaomisum.robotest.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.TestReviewProgressRespDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestReviewServiceImplTest {

    @Mock
    private TestReviewMapper testReviewMapper;
    @Mock
    private TestReviewModuleSnapshotMapper reviewModuleSnapshotMapper;
    @Mock
    private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    @Mock
    private TestReviewRecordMapper reviewRecordMapper;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;

    @InjectMocks
    private TestReviewServiceImpl reviewService;

    private String projectId;
    private String userId;
    private String reviewId;

    @BeforeEach
    void setUp() {
        projectId = "00000000-0000-0000-0000-000000000001";
        userId = "00000000-0000-0000-0000-000000000002";
        reviewId = "00000000-0000-0000-0000-000000000003";
    }

    @Test
    void getReviewPage_success() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setTitle("Review 1");
        review.setStatus("in_progress");
        review.setInitiatorId(userId);
        review.setParticipantIds(List.of(UUID.fromString("00000000-0000-0000-0000-000000000010"), UUID.fromString("00000000-0000-0000-0000-000000000011")));

        PageResult<TestReview> page = new PageResult<>(List.of(review), 1L);
        doReturn(page).when(testReviewMapper).selectPage(any(PageParam.class), any(LambdaQueryWrapper.class));

        SysUser initiator = new SysUser();
        initiator.setId(UUID.fromString(userId));
        initiator.setUsername("reviewer");
        when(userMapper.selectById(userId)).thenReturn(initiator);

        PageResult<TestReviewListRespDTO> result = reviewService.getReviewPage(
                projectId, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals("Review 1", result.getList().get(0).getTitle());
        assertEquals(2, result.getList().get(0).getParticipantCount());
    }

    @Test
    void getReviewPage_empty() {
        PageResult<TestReview> page = new PageResult<>(Collections.emptyList(), 0L);
        doReturn(page).when(testReviewMapper).selectPage(any(PageParam.class), any(LambdaQueryWrapper.class));

        PageResult<TestReviewListRespDTO> result = reviewService.getReviewPage(
                projectId, null, 1, 10);

        assertNotNull(result);
        assertTrue(result.getList().isEmpty());
    }

    @Test
    void createReview_success() {
        // Mock project lookup for workspace membership validation
        Project project = new Project();
        project.setId(UUID.fromString(projectId));
        project.setWorkspaceId("00000000-0000-0000-0000-000000000010");
        when(projectMapper.selectById(projectId)).thenReturn(project);

        // Mock workspace membership validation for participant
        WorkspaceUser wu = new WorkspaceUser();
        wu.setUserId("00000000-0000-0000-0000-000000000098");
        wu.setWorkspaceId("00000000-0000-0000-0000-000000000010");
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(wu);

        doAnswer(inv -> {
            ((TestReview) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(testReviewMapper).insert(any(TestReview.class));

        TestReviewCreateReqDTO reqDTO = new TestReviewCreateReqDTO();
        reqDTO.setTitle("New Review");
        reqDTO.setParticipantIds(List.of(UUID.fromString("00000000-0000-0000-0000-000000000098")));
        reqDTO.setSelectedNodes(Collections.emptyList());

        TestReviewDetailRespDTO result = reviewService.createReview(projectId, userId, reqDTO);

        assertNotNull(result);
        assertEquals("New Review", result.getTitle());
        verify(testReviewMapper).insert(any(TestReview.class));
    }

    @Test
    void getReviewDetail_success() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setTitle("Review");
        review.setInitiatorId(userId);

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);
        when(userMapper.selectById(userId)).thenReturn(null);

        TestReviewDetailRespDTO result = reviewService.getReviewDetail(reviewId);

        assertNotNull(result);
        assertEquals("Review", result.getTitle());
    }

    @Test
    void getReviewDetail_notFound_throws() {
        when(testReviewMapper.selectById(reviewId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> reviewService.getReviewDetail(reviewId));
    }

    @Test
    void getReviewSnapshotTree_success() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setReviewId(reviewId);
        snapshot.setIsAssociated(true);
        snapshot.setParentId(null);

        when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(snapshot));

        List<TestReviewSnapshotNodeRespDTO> result =
                reviewService.getReviewSnapshotTree(reviewId, null);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void getReviewSnapshotTree_reviewNotFound_throws() {
        when(testReviewMapper.selectById(reviewId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> reviewService.getReviewSnapshotTree(reviewId, null));
    }

    @Test
    void submitReviewRecord_success() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setStatus("in_progress");

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setReviewId(reviewId);
        snapshot.setType("case");

        when(reviewNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(snapshot);

        TestReviewRecordReqDTO reqDTO = new TestReviewRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reqDTO.setOperationType("mark");
        reqDTO.setMark("pass");

        reviewService.submitReviewRecord(reviewId, userId, reqDTO);

        verify(reviewNodeSnapshotMapper).updateById(any(TestReviewNodeSnapshot.class));
        verify(reviewRecordMapper).insert(any(TestReviewRecord.class));
    }

    @Test
    void submitReviewRecord_reviewNotFound_throws() {
        when(testReviewMapper.selectById(reviewId)).thenReturn(null);

        TestReviewRecordReqDTO reqDTO = new TestReviewRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        reqDTO.setOperationType("comment");

        assertThrows(ServiceException.class,
                () -> reviewService.submitReviewRecord(reviewId, userId, reqDTO));
    }

    @Test
    void submitReviewRecord_notInProgress_throws() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setStatus("completed");

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        TestReviewRecordReqDTO reqDTO = new TestReviewRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        reqDTO.setOperationType("comment");

        assertThrows(ServiceException.class,
                () -> reviewService.submitReviewRecord(reviewId, userId, reqDTO));
    }

    @Test
    void submitReviewRecord_snapshotNotFound_throws() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setStatus("in_progress");

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);
        when(reviewNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(null);

        TestReviewRecordReqDTO reqDTO = new TestReviewRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reqDTO.setOperationType("comment");

        assertThrows(ServiceException.class,
                () -> reviewService.submitReviewRecord(reviewId, userId, reqDTO));
    }

    @Test
    void submitReviewRecord_markNonCaseNode_throws() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setStatus("in_progress");

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setReviewId(reviewId);
        snapshot.setType("normal");

        when(reviewNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004"))).thenReturn(snapshot);

        TestReviewRecordReqDTO reqDTO = new TestReviewRecordReqDTO();
        reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        reqDTO.setOperationType("mark");
        reqDTO.setMark("pass");

        assertThrows(ServiceException.class,
                () -> reviewService.submitReviewRecord(reviewId, userId, reqDTO));
    }

    @Test
    void getNodeReviewRecords_success() {
        TestReviewRecord record = new TestReviewRecord();
        record.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        record.setReviewId(reviewId);
        record.setSnapshotNodeId("00000000-0000-0000-0000-000000000004");
        record.setReviewerId(userId);
        record.setOperationType("mark");
        record.setMark("pass");

        when(reviewRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(record));
        when(userMapper.selectById(userId)).thenReturn(null);

        List<TestReviewRecordRespDTO> result =
                reviewService.getNodeReviewRecords(reviewId, "00000000-0000-0000-0000-000000000004");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("pass", result.get(0).getMark());
    }

    @Test
    void completeReview_success() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setInitiatorId(userId);
        review.setStatus("in_progress");

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        reviewService.completeReview(reviewId, userId);

        verify(testReviewMapper).updateById(any(TestReview.class));
        assertEquals("completed", review.getStatus());
    }

    @Test
    void completeReview_notInitiator_throws() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setInitiatorId("other-user");

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        assertThrows(ServiceException.class,
                () -> reviewService.completeReview(reviewId, userId));
    }

    @Test
    void completeReview_notFound_throws() {
        when(testReviewMapper.selectById(reviewId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> reviewService.completeReview(reviewId, userId));
    }

    @Test
    void syncReview_success() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setInitiatorId(userId);
        review.setStatus("in_progress");

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setReviewId(reviewId);
        snapshot.setOriginalNodeId("00000000-0000-0000-0000-000000000006");
        snapshot.setTitle("Old Title");
        snapshot.setType("normal");

        when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(snapshot));

        TestCaseNode currentNode = new TestCaseNode();
        currentNode.setId(UUID.fromString("00000000-0000-0000-0000-000000000006"));
        currentNode.setTitle("Updated Title");
        currentNode.setType("case");
        currentNode.setPriority("high");
        currentNode.setSortOrder(0);
        currentNode.setIsDeleted(false);

        when(testCaseNodeMapper.selectById("00000000-0000-0000-0000-000000000006")).thenReturn(currentNode);

        reviewService.syncReview(reviewId, userId);

        verify(reviewNodeSnapshotMapper).updateById(any(TestReviewNodeSnapshot.class));
        assertEquals("Updated Title", snapshot.getTitle());
        assertEquals("case", snapshot.getType());
    }

    @Test
    void syncReview_notInitiator_throws() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setInitiatorId("other-user");
        review.setStatus("in_progress");

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        assertThrows(ServiceException.class,
                () -> reviewService.syncReview(reviewId, userId));
    }

    @Test
    void syncReview_notInProgress_throws() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setInitiatorId(userId);
        review.setStatus("completed");

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        assertThrows(ServiceException.class,
                () -> reviewService.syncReview(reviewId, userId));
    }

    @Test
    void syncReview_deletedOriginal_marksDeleted() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setInitiatorId(userId);
        review.setStatus("in_progress");

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
        snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        snapshot.setReviewId(reviewId);
        snapshot.setOriginalNodeId("00000000-0000-0000-0000-000000000006");

        when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(snapshot));
        when(testCaseNodeMapper.selectById("00000000-0000-0000-0000-000000000006")).thenReturn(null);

        reviewService.syncReview(reviewId, userId);

        verify(reviewNodeSnapshotMapper).updateById(any(TestReviewNodeSnapshot.class));
        assertTrue(snapshot.getIsDeleted());
    }

    // ========== getReviewProgress ==========

    @Test
    void getReviewProgress_success() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        TestReviewNodeSnapshot snap1 = new TestReviewNodeSnapshot();
        snap1.setLastMark("pass");
        TestReviewNodeSnapshot snap2 = new TestReviewNodeSnapshot();
        snap2.setLastMark("fail");
        TestReviewNodeSnapshot snap3 = new TestReviewNodeSnapshot();
        snap3.setLastMark(null);

        when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(snap1, snap2, snap3));

        TestReviewProgressRespDTO result = reviewService.getReviewProgress(reviewId);

        assertEquals(3, result.getTotalAssociated());
        assertEquals(1, result.getPassed());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getPending());
    }

    @Test
    void getReviewProgress_notFound_throws() {
        when(testReviewMapper.selectById(reviewId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> reviewService.getReviewProgress(reviewId));
    }

    @Test
    void getReviewProgress_emptySnapshots() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        when(testReviewMapper.selectById(reviewId)).thenReturn(review);
        when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>());

        TestReviewProgressRespDTO result = reviewService.getReviewProgress(reviewId);

        assertEquals(0, result.getTotalAssociated());
        assertEquals(0.0, result.getProgressPercent());
    }

    // ========== syncReview module snapshot ==========

    @Test
    void syncReview_syncsModuleName() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setInitiatorId(userId);
        review.setStatus(Constants.Status.IN_PROGRESS);

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        UUID moduleSnapId = UUID.randomUUID();
        TestReviewModuleSnapshot moduleSnap = new TestReviewModuleSnapshot();
        moduleSnap.setId(moduleSnapId);
        moduleSnap.setOriginalModuleId("00000000-0000-0000-0000-000000000010");
        moduleSnap.setName("old name");
        moduleSnap.setSortOrder(1);

        when(reviewModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(moduleSnap));
        when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>());

        TestCaseModule originalModule = new TestCaseModule();
        originalModule.setName("new name");
        originalModule.setSortOrder(2);
        originalModule.setIsDeleted(false);
        when(testCaseModuleMapper.selectById("00000000-0000-0000-0000-000000000010"))
                .thenReturn(originalModule);

        reviewService.syncReview(reviewId, userId);

        assertEquals("new name", moduleSnap.getName());
        assertEquals(2, moduleSnap.getSortOrder());
        verify(reviewModuleSnapshotMapper).updateById(moduleSnap);
    }

    @Test
    void syncReview_deletesRemovedModule() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setInitiatorId(userId);
        review.setStatus(Constants.Status.IN_PROGRESS);

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        UUID moduleSnapId = UUID.randomUUID();
        TestReviewModuleSnapshot moduleSnap = new TestReviewModuleSnapshot();
        moduleSnap.setId(moduleSnapId);
        moduleSnap.setOriginalModuleId("00000000-0000-0000-0000-000000000010");

        when(reviewModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(moduleSnap));
        when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(new ArrayList<>());
        when(testCaseModuleMapper.selectById("00000000-0000-0000-0000-000000000010"))
                .thenReturn(null);

        reviewService.syncReview(reviewId, userId);

        verify(reviewModuleSnapshotMapper).deleteById(moduleSnapId);
    }

    @Test
    void syncReview_deletedModule_cascadesNodeDeletion() {
        TestReview review = new TestReview();
        review.setId(UUID.fromString(reviewId));
        review.setInitiatorId(userId);
        review.setStatus(Constants.Status.IN_PROGRESS);

        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        UUID moduleSnapId = UUID.randomUUID();
        TestReviewModuleSnapshot moduleSnap = new TestReviewModuleSnapshot();
        moduleSnap.setId(moduleSnapId);
        moduleSnap.setOriginalModuleId("00000000-0000-0000-0000-000000000010");

        UUID nodeSnapId = UUID.randomUUID();
        TestReviewNodeSnapshot nodeSnap = new TestReviewNodeSnapshot();
        nodeSnap.setId(nodeSnapId);
        nodeSnap.setDocumentSnapshotId(moduleSnapId.toString());

        when(reviewModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(moduleSnap));
        when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(nodeSnap));
        when(testCaseModuleMapper.selectById("00000000-0000-0000-0000-000000000010"))
                .thenReturn(null);

        reviewService.syncReview(reviewId, userId);

        verify(reviewModuleSnapshotMapper).deleteById(moduleSnapId);
        verify(reviewNodeSnapshotMapper).deleteById(nodeSnapId);
    }

    @Test
    void syncReview_notFound_throws() {
        when(testReviewMapper.selectById(reviewId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> reviewService.syncReview(reviewId, userId));
    }
}
