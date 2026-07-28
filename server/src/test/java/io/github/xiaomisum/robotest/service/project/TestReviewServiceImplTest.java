package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.PlannedCasesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.SnapshotModuleTreeRespDTO;
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
import static org.mockito.ArgumentMatchers.isNull;
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

        private UUID projectId;
        private UUID userId;
        private UUID reviewId;
        private UUID otherUserId;

        @BeforeEach
        void setUp() {
                // LambdaUpdateWrapper 解析实体列名依赖 TableInfo 缓存，纯 Mockito 环境无 starter 初始化，需手动注册
                TableInfoHelper.initTableInfo(
                                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                                TestReviewNodeSnapshot.class);
                projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
                userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
                reviewId = UUID.fromString("00000000-0000-0000-0000-000000000003");
                otherUserId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        }

        @Test
        void getReviewPage_success() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setTitle("Review 1");
                review.setStatus("in_progress");
                review.setInitiatorId(userId.toString());
                review.setParticipantIds(List.of(UUID.fromString("00000000-0000-0000-0000-000000000010"),
                                UUID.fromString("00000000-0000-0000-0000-000000000011")));

                PageResult<TestReview> page = new PageResult<>(List.of(review), 1L);
                doReturn(page).when(testReviewMapper).selectPage(any(PageParam.class), any(LambdaQueryWrapper.class));

                SysUser initiator = new SysUser();
                initiator.setId(userId);
                initiator.setUsername("reviewer");
                when(userMapper.selectById(userId.toString())).thenReturn(initiator);

                PageResult<TestReviewListRespDTO> result = reviewService.getReviewPage(
                                projectId, null, null, 1, 10);

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
                                projectId, null, null, 1, 10);

                assertNotNull(result);
                assertTrue(result.getList().isEmpty());
        }

        @Test
        void createReview_success() {
                Project project = new Project();
                project.setId(projectId);
                project.setWorkspaceId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
                when(projectMapper.selectById(projectId)).thenReturn(project);

                WorkspaceUser wu = new WorkspaceUser();
                wu.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000098"));
                wu.setWorkspaceId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
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
                review.setId(reviewId);
                review.setTitle("Review");
                review.setInitiatorId(userId.toString());

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);
                when(userMapper.selectById(userId.toString())).thenReturn(null);

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
                review.setId(reviewId);

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setReviewId(reviewId);
                snapshot.setIsAssociated(true);
                snapshot.setParentId(null);

                when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(snapshot));

                List<TestReviewSnapshotNodeRespDTO> result = reviewService.getReviewSnapshotTree(reviewId, null);

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
                review.setId(reviewId);
                review.setStatus("in_progress");

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setReviewId(reviewId);
                snapshot.setType("case");

                when(reviewNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(snapshot);

                TestReviewRecordReqDTO reqDTO = new TestReviewRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setOperationType("mark");
                reqDTO.setMark("pass");

                reviewService.submitReviewRecord(reviewId, userId, reqDTO);

                verify(reviewNodeSnapshotMapper).updateById(any(TestReviewNodeSnapshot.class));
                verify(reviewRecordMapper).insert(any(TestReviewRecord.class));
        }

        @Test
        void getReviewModuleTree_buildsHierarchy() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                TestReviewModuleSnapshot dir = new TestReviewModuleSnapshot();
                dir.setId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
                dir.setParentId(null);
                dir.setName("目录");
                dir.setType("directory");
                dir.setSortOrder(0);

                TestReviewModuleSnapshot doc = new TestReviewModuleSnapshot();
                doc.setId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
                doc.setParentId(dir.getId());
                doc.setName("文档");
                doc.setType("document");
                doc.setSortOrder(0);

                when(reviewModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(dir, doc));

                List<SnapshotModuleTreeRespDTO> tree = reviewService.getReviewModuleTree(reviewId);

                assertEquals(1, tree.size());
                assertEquals("目录", tree.get(0).getName());
                assertEquals(1, tree.get(0).getChildren().size());
                assertEquals("文档", tree.get(0).getChildren().get(0).getName());
        }

        @Test
        void updateReviewCases_notInProgress_throws() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setStatus("completed");
                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                TestReviewCasesUpdateReqDTO reqDTO = new TestReviewCasesUpdateReqDTO();
                TestReviewCreateReqDTO.SelectedNode sn = new TestReviewCreateReqDTO.SelectedNode();
                sn.setDocumentId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"));
                sn.setCaseIds(List.of(UUID.fromString("00000000-0000-0000-0000-0000000000c1")));
                reqDTO.setSelectedNodes(List.of(sn));

                assertThrows(ServiceException.class,
                                () -> reviewService.updateReviewCases(reviewId, reqDTO));
        }

        @Test
        void updateReviewCases_removesDocAndRefreshesAssociation() {
                UUID docA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
                UUID docB = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
                UUID caseC1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
                UUID caseC2 = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setProjectId(projectId);
                review.setStatus("in_progress");
                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                TestCaseModule docModuleB = new TestCaseModule();
                docModuleB.setId(docB);
                docModuleB.setProjectId(projectId);
                docModuleB.setType("document");
                when(testCaseModuleMapper.selectById(docB)).thenReturn(docModuleB);

                TestReviewModuleSnapshot snapA = new TestReviewModuleSnapshot();
                snapA.setId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
                snapA.setOriginalModuleId(docA);
                snapA.setType("document");
                TestReviewModuleSnapshot snapB = new TestReviewModuleSnapshot();
                snapB.setId(UUID.fromString("00000000-0000-0000-0000-0000000000bb"));
                snapB.setOriginalModuleId(docB);
                snapB.setType("document");

                // 第一次：文档快照查询；第二次：空目录清理循环（无目录即退出）
                when(reviewModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(snapA, snapB), List.of(snapB));

                TestReviewNodeSnapshot rootSnap = new TestReviewNodeSnapshot();
                rootSnap.setId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
                rootSnap.setOriginalNodeId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
                rootSnap.setType("normal");
                rootSnap.setIsAssociated(false);
                TestReviewNodeSnapshot caseSnap1 = new TestReviewNodeSnapshot();
                caseSnap1.setId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"));
                caseSnap1.setOriginalNodeId(caseC1);
                caseSnap1.setType("case");
                caseSnap1.setIsAssociated(true);
                when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(rootSnap, caseSnap1));

                TestCaseNode rootNode = new TestCaseNode();
                rootNode.setId(rootSnap.getOriginalNodeId());
                rootNode.setParentId(null);
                rootNode.setType("normal");
                rootNode.setTitle("root");
                TestCaseNode caseNode1 = new TestCaseNode();
                caseNode1.setId(caseC1);
                caseNode1.setParentId(rootNode.getId());
                caseNode1.setType("case");
                caseNode1.setTitle("case1");
                TestCaseNode caseNode2 = new TestCaseNode();
                caseNode2.setId(caseC2);
                caseNode2.setParentId(rootNode.getId());
                caseNode2.setType("case");
                caseNode2.setTitle("case2");
                when(testCaseNodeMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(rootNode, caseNode1, caseNode2));

                TestReviewCasesUpdateReqDTO reqDTO = new TestReviewCasesUpdateReqDTO();
                TestReviewCreateReqDTO.SelectedNode sn = new TestReviewCreateReqDTO.SelectedNode();
                sn.setDocumentId(docB);
                sn.setCaseIds(List.of(caseC2));
                reqDTO.setSelectedNodes(List.of(sn));

                reviewService.updateReviewCases(reviewId, reqDTO);

                // docA 被移除：节点快照批删 + 文档快照删除
                verify(reviewNodeSnapshotMapper).delete(any(LambdaQueryWrapper.class));
                verify(reviewModuleSnapshotMapper).deleteById(snapA.getId());
                // 快照后新增的 case2 被补入快照（插入时已带关联标记）
                verify(reviewNodeSnapshotMapper).insert(any(TestReviewNodeSnapshot.class));
                // c1 取消关联落库一次
                verify(reviewNodeSnapshotMapper, times(1)).updateById(any(TestReviewNodeSnapshot.class));
                assertFalse(caseSnap1.getIsAssociated());
        }

        @Test
        void getReviewPlannedCases_returnsOriginalIds() {
                UUID docA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
                UUID caseC1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

                TestReview review = new TestReview();
                review.setId(reviewId);
                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                TestReviewModuleSnapshot snapA = new TestReviewModuleSnapshot();
                snapA.setId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
                snapA.setOriginalModuleId(docA);
                snapA.setType("document");
                when(reviewModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(snapA));

                TestReviewNodeSnapshot caseSnap = new TestReviewNodeSnapshot();
                caseSnap.setId(UUID.fromString("00000000-0000-0000-0000-0000000000e2"));
                caseSnap.setOriginalNodeId(caseC1);
                caseSnap.setIsAssociated(true);
                when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(caseSnap));

                List<PlannedCasesRespDTO> result = reviewService.getReviewPlannedCases(reviewId);

                assertEquals(1, result.size());
                assertEquals(docA, result.get(0).getDocumentId());
                assertEquals(List.of(caseC1), result.get(0).getCaseIds());
        }

        @Test
        void getReviewModuleTree_reviewNotFound_throws() {
                when(testReviewMapper.selectById(reviewId)).thenReturn(null);

                assertThrows(ServiceException.class,
                                () -> reviewService.getReviewModuleTree(reviewId));
        }

        @Test
        void submitReviewRecord_pendingMark_resetsViaUpdateWrapper() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setStatus("in_progress");

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setReviewId(reviewId);
                snapshot.setType("case");
                snapshot.setLastMark("pass");

                when(reviewNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(snapshot);

                TestReviewRecordReqDTO reqDTO = new TestReviewRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setOperationType("mark");
                reqDTO.setMark("pending");

                reviewService.submitReviewRecord(reviewId, userId, reqDTO);

                // 重置路径走显式 set null 的 UpdateWrapper，不走 updateById（其会忽略 null 字段）
                verify(reviewNodeSnapshotMapper, never()).updateById(any(TestReviewNodeSnapshot.class));
                verify(reviewNodeSnapshotMapper).update(isNull(), any());
                verify(reviewRecordMapper).insert(any(TestReviewRecord.class));
        }

        @Test
        void submitReviewRecord_invalidMark_throws() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setStatus("in_progress");

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setReviewId(reviewId);
                snapshot.setType("case");

                when(reviewNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(snapshot);

                TestReviewRecordReqDTO reqDTO = new TestReviewRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setOperationType("mark");
                reqDTO.setMark("invalid");

                assertThrows(ServiceException.class,
                                () -> reviewService.submitReviewRecord(reviewId, userId, reqDTO));
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
                review.setId(reviewId);
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
                review.setId(reviewId);
                review.setStatus("in_progress");

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);
                when(reviewNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(null);

                TestReviewRecordReqDTO reqDTO = new TestReviewRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setOperationType("comment");

                assertThrows(ServiceException.class,
                                () -> reviewService.submitReviewRecord(reviewId, userId, reqDTO));
        }

        @Test
        void submitReviewRecord_markNonCaseNode_throws() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setStatus("in_progress");

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
                snapshot.setId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                snapshot.setReviewId(reviewId);
                snapshot.setType("normal");

                when(reviewNodeSnapshotMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000004")))
                                .thenReturn(snapshot);

                TestReviewRecordReqDTO reqDTO = new TestReviewRecordReqDTO();
                reqDTO.setSnapshotNodeId(UUID.fromString("00000000-0000-0000-0000-000000000004"));
                reqDTO.setOperationType("mark");
                reqDTO.setMark("pass");

                assertThrows(ServiceException.class,
                                () -> reviewService.submitReviewRecord(reviewId, userId, reqDTO));
        }

        @Test
        void getNodeReviewRecords_success() {
                UUID snapNodeId = UUID.fromString("00000000-0000-0000-0000-000000000004");

                TestReviewRecord record = new TestReviewRecord();
                record.setId(UUID.fromString("00000000-0000-0000-0000-000000000005"));
                record.setReviewId(reviewId);
                record.setSnapshotNodeId(snapNodeId);
                record.setReviewerId(userId);
                record.setOperationType("mark");
                record.setMark("pass");

                when(reviewRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(record));
                when(userMapper.selectById(userId)).thenReturn(null);

                List<TestReviewRecordRespDTO> result = reviewService.getNodeReviewRecords(reviewId, snapNodeId);

                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals("pass", result.get(0).getMark());
        }

        @Test
        void completeReview_success() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setInitiatorId(userId.toString());
                review.setStatus("in_progress");

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                reviewService.completeReview(reviewId, userId);

                verify(testReviewMapper).updateById(any(TestReview.class));
                assertEquals("completed", review.getStatus());
        }

        @Test
        void completeReview_notInitiator_throws() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setInitiatorId(otherUserId.toString());

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
                review.setId(reviewId);
                review.setInitiatorId(userId.toString());
                review.setStatus("in_progress");

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                UUID snapNodeId = UUID.fromString("00000000-0000-0000-0000-000000000004");
                UUID originalNodeId = UUID.fromString("00000000-0000-0000-0000-000000000006");

                TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
                snapshot.setId(snapNodeId);
                snapshot.setReviewId(reviewId);
                snapshot.setOriginalNodeId(originalNodeId);
                snapshot.setTitle("Old Title");
                snapshot.setType("normal");

                when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(snapshot));

                TestCaseNode currentNode = new TestCaseNode();
                currentNode.setId(originalNodeId);
                currentNode.setTitle("Updated Title");
                currentNode.setType("case");
                currentNode.setPriority("high");
                currentNode.setSortOrder(0);
                currentNode.setIsDeleted(false);

                when(testCaseNodeMapper.selectById(originalNodeId)).thenReturn(currentNode);

                reviewService.syncReview(reviewId, userId);

                verify(reviewNodeSnapshotMapper).updateById(any(TestReviewNodeSnapshot.class));
                assertEquals("Updated Title", snapshot.getTitle());
                assertEquals("case", snapshot.getType());
        }

        @Test
        void syncReview_notInitiator_throws() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setInitiatorId(otherUserId.toString());
                review.setStatus("in_progress");

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                assertThrows(ServiceException.class,
                                () -> reviewService.syncReview(reviewId, userId));
        }

        @Test
        void syncReview_notInProgress_throws() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setInitiatorId(userId.toString());
                review.setStatus("completed");

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                assertThrows(ServiceException.class,
                                () -> reviewService.syncReview(reviewId, userId));
        }

        @Test
        void syncReview_deletedOriginal_marksDeleted() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setInitiatorId(userId.toString());
                review.setStatus("in_progress");

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                UUID snapNodeId = UUID.fromString("00000000-0000-0000-0000-000000000004");
                UUID originalNodeId = UUID.fromString("00000000-0000-0000-0000-000000000006");

                TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
                snapshot.setId(snapNodeId);
                snapshot.setReviewId(reviewId);
                snapshot.setOriginalNodeId(originalNodeId);

                when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(snapshot));
                when(testCaseNodeMapper.selectById(originalNodeId)).thenReturn(null);

                reviewService.syncReview(reviewId, userId);

                verify(reviewNodeSnapshotMapper).updateById(any(TestReviewNodeSnapshot.class));
                assertTrue(snapshot.getIsDeleted());
        }

        // ========== getReviewProgress ==========

        @Test
        void getReviewProgress_success() {
                TestReview review = new TestReview();
                review.setId(reviewId);
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
                review.setId(reviewId);
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
                review.setId(reviewId);
                review.setInitiatorId(userId.toString());
                review.setStatus(Constants.Status.IN_PROGRESS);

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                UUID moduleSnapId = UUID.randomUUID();
                TestReviewModuleSnapshot moduleSnap = new TestReviewModuleSnapshot();
                moduleSnap.setId(moduleSnapId);
                moduleSnap.setOriginalModuleId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
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
                when(testCaseModuleMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000010")))
                                .thenReturn(originalModule);

                reviewService.syncReview(reviewId, userId);

                assertEquals("new name", moduleSnap.getName());
                assertEquals(2, moduleSnap.getSortOrder());
                verify(reviewModuleSnapshotMapper).updateById(moduleSnap);
        }

        @Test
        void syncReview_deletesRemovedModule() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setInitiatorId(userId.toString());
                review.setStatus(Constants.Status.IN_PROGRESS);

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                UUID moduleSnapId = UUID.randomUUID();
                TestReviewModuleSnapshot moduleSnap = new TestReviewModuleSnapshot();
                moduleSnap.setId(moduleSnapId);
                moduleSnap.setOriginalModuleId(UUID.fromString("00000000-0000-0000-0000-000000000010"));

                when(reviewModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(moduleSnap));
                when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(new ArrayList<>());
                when(testCaseModuleMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000010")))
                                .thenReturn(null);

                reviewService.syncReview(reviewId, userId);

                verify(reviewModuleSnapshotMapper).deleteById(moduleSnapId);
        }

        @Test
        void syncReview_deletedModule_cascadesNodeDeletion() {
                TestReview review = new TestReview();
                review.setId(reviewId);
                review.setInitiatorId(userId.toString());
                review.setStatus(Constants.Status.IN_PROGRESS);

                when(testReviewMapper.selectById(reviewId)).thenReturn(review);

                UUID moduleSnapId = UUID.randomUUID();
                TestReviewModuleSnapshot moduleSnap = new TestReviewModuleSnapshot();
                moduleSnap.setId(moduleSnapId);
                moduleSnap.setOriginalModuleId(UUID.fromString("00000000-0000-0000-0000-000000000010"));

                UUID nodeSnapId = UUID.randomUUID();
                TestReviewNodeSnapshot nodeSnap = new TestReviewNodeSnapshot();
                nodeSnap.setId(nodeSnapId);
                nodeSnap.setDocumentSnapshotId(moduleSnapId);

                when(reviewModuleSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(moduleSnap));
                when(reviewNodeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                                .thenReturn(List.of(nodeSnap));
                when(testCaseModuleMapper.selectById(UUID.fromString("00000000-0000-0000-0000-000000000010")))
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
