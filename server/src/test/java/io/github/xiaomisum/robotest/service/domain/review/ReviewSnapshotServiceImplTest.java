package io.github.xiaomisum.robotest.service.domain.review;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.PlannedCasesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.SnapshotModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewModuleSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.review.TestReviewModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewNodeSnapshotMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewSnapshotServiceImplTest {

        @Mock
        private TestReviewModuleSnapshotMapper reviewModuleSnapshotMapper;
        @Mock
        private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
        @Mock
        private TestCaseDocumentMapper testCaseDocumentMapper;
        @Mock
        private ProjectModuleMapper projectModuleMapper;
        @Mock
        private TestCaseNodeMapper testCaseNodeMapper;

        private ReviewSnapshotServiceImpl snapshotService;

        private UUID reviewId;
        private UUID docId;
        private UUID directoryId;

        @BeforeEach
        void setUp() {
                reviewId = UUID.fromString("00000000-0000-0000-0000-000000000001");
                docId = UUID.fromString("00000000-0000-0000-0000-00000000000a");
                directoryId = UUID.fromString("00000000-0000-0000-0000-00000000000b");

                snapshotService = new ReviewSnapshotServiceImpl(
                        reviewModuleSnapshotMapper, reviewNodeSnapshotMapper,
                        testCaseDocumentMapper, projectModuleMapper, testCaseNodeMapper);
        }

        @Test
        void getSnapshotTree_prunesToAssociatedAndKeepsAncestors() {
                UUID rootId = UUID.fromString("00000000-0000-0000-0000-000000000100");
                UUID parentId = UUID.fromString("00000000-0000-0000-0000-000000000101");
                UUID assocId = UUID.fromString("00000000-0000-0000-0000-000000000102");
                UUID orphanId = UUID.fromString("00000000-0000-0000-0000-000000000103");
                UUID descendantId = UUID.fromString("00000000-0000-0000-0000-000000000104");

                TestReviewNodeSnapshot n = snapshotNode(rootId, null, false);
                TestReviewNodeSnapshot p = snapshotNode(parentId, rootId, false);
                TestReviewNodeSnapshot a = snapshotNode(assocId, parentId, true);
                TestReviewNodeSnapshot d = snapshotNode(descendantId, assocId, false);
                TestReviewNodeSnapshot orphan = snapshotNode(orphanId, null, false);

                when(reviewNodeSnapshotMapper.listByReviewIdAndDocumentId(reviewId, docId))
                        .thenReturn(List.of(n, p, a, d, orphan));

                List<TestReviewSnapshotNodeRespDTO> tree = snapshotService.getSnapshotTree(reviewId, docId);

                assertEquals(1, tree.size());
                assertEquals(rootId, tree.get(0).getId());
                assertEquals(1, tree.get(0).getChildren().size());
                assertEquals(parentId, tree.get(0).getChildren().get(0).getId());
                assertEquals(1, tree.get(0).getChildren().get(0).getChildren().size());
                TestReviewSnapshotNodeRespDTO assoc = tree.get(0).getChildren().get(0).getChildren().get(0);
                assertEquals(assocId, assoc.getId());
                assertEquals(1, assoc.getChildren().size());
                assertEquals(descendantId, assoc.getChildren().get(0).getId());
        }

        @Test
        void getModuleTree_buildsHierarchy() {
                TestReviewModuleSnapshot dir = moduleSnapshot(directoryId, null, "目录", "directory");
                TestReviewModuleSnapshot doc = moduleSnapshot(docId, directoryId, "文档", "document");

                when(reviewModuleSnapshotMapper.listByReviewId(reviewId))
                        .thenReturn(List.of(dir, doc));

                List<SnapshotModuleTreeRespDTO> tree = snapshotService.getModuleTree(reviewId);

                assertEquals(1, tree.size());
                assertEquals("目录", tree.get(0).getName());
                assertEquals(1, tree.get(0).getChildren().size());
                assertEquals("文档", tree.get(0).getChildren().get(0).getName());
        }

        @Test
        void generateSnapshots_copiesModulePathAndCreatesNodes() {
                UUID caseId = UUID.fromString("00000000-0000-0000-0000-000000000105");
                UUID snapshotDirId = UUID.fromString("00000000-0000-0000-0000-000000000200");
                UUID snapshotDocId = UUID.fromString("00000000-0000-0000-0000-000000000201");

                TestCaseDocument doc = new TestCaseDocument();
                doc.setId(docId);
                doc.setModuleId(directoryId);
                doc.setName("文档");
                doc.setSortOrder(0);

                ProjectModule dir = new ProjectModule();
                dir.setId(directoryId);
                dir.setParentId(null);
                dir.setName("目录");
                dir.setSortOrder(0);

                TestCaseNode node = new TestCaseNode();
                node.setId(caseId);
                node.setParentId(null);
                node.setTitle("用例");
                node.setType(Constants.NodeType.CASE);
                node.setPriority("M");
                node.setSortOrder(0);
                node.setAiGenerated(false);

                when(reviewModuleSnapshotMapper.listByReviewId(reviewId)).thenReturn(List.of());
                when(reviewModuleSnapshotMapper.findByReviewIdAndOriginalModuleId(reviewId, directoryId))
                        .thenReturn(moduleSnapshot(snapshotDirId, null, "目录", "directory"));
                when(reviewModuleSnapshotMapper.findByReviewIdAndOriginalModuleId(reviewId, docId))
                        .thenReturn(moduleSnapshot(snapshotDocId, snapshotDirId, "文档", "document"));
                when(projectModuleMapper.selectById(docId)).thenReturn(null);
                when(testCaseDocumentMapper.selectById(docId)).thenReturn(doc);
                when(testCaseDocumentMapper.selectById(directoryId)).thenReturn(null);
                when(projectModuleMapper.selectById(directoryId)).thenReturn(dir);
                when(testCaseNodeMapper.listByDocumentId(docId)).thenReturn(List.of(node));

                TestReviewCreateReqDTO.SelectedNode selected = new TestReviewCreateReqDTO.SelectedNode();
                selected.setDocumentId(docId);
                selected.setCaseIds(List.of(caseId));
                snapshotService.generateSnapshots(reviewId, List.of(selected));

                verify(reviewModuleSnapshotMapper, times(2)).insert(any(TestReviewModuleSnapshot.class));
                verify(reviewNodeSnapshotMapper).insert(any(TestReviewNodeSnapshot.class));
        }

        @Test
        void updateCases_removesRemovedDocumentsAndPrunesEmptyDirectories() {
                UUID removedDocId = UUID.fromString("00000000-0000-0000-0000-000000000108");
                UUID keptDocId = UUID.fromString("00000000-0000-0000-0000-000000000106");
                UUID keptSnapDocId = UUID.fromString("00000000-0000-0000-0000-000000000210");
                UUID removedSnapDocId = UUID.fromString("00000000-0000-0000-0000-000000000211");
                UUID caseId = UUID.fromString("00000000-0000-0000-0000-000000000107");

                TestCaseDocument keptDoc = new TestCaseDocument();
                keptDoc.setId(keptDocId);
                keptDoc.setProjectId(UUID.fromString("00000000-0000-0000-0000-00000000000c"));

                TestReviewModuleSnapshot keptSnap = moduleSnapshot(keptSnapDocId, null, "保留", "document");
                keptSnap.setOriginalModuleId(keptDocId);
                TestReviewModuleSnapshot removedSnap = moduleSnapshot(removedSnapDocId, null, "移除", "document");
                removedSnap.setOriginalModuleId(removedDocId);

                when(testCaseDocumentMapper.listByIds(Set.of(keptDocId))).thenReturn(List.of(keptDoc));
                when(reviewModuleSnapshotMapper.listByReviewIdAndType(reviewId, Constants.ModuleType.DOCUMENT))
                        .thenReturn(List.of(keptSnap, removedSnap));
                when(reviewModuleSnapshotMapper.listByReviewId(reviewId))
                        .thenReturn(List.of(keptSnap, removedSnap));
                when(reviewNodeSnapshotMapper.listByReviewIdAndDocumentId(reviewId, keptSnapDocId))
                        .thenReturn(List.of());
                when(testCaseNodeMapper.listByDocumentId(keptDocId)).thenReturn(List.of());

                TestReviewCreateReqDTO.SelectedNode kept = new TestReviewCreateReqDTO.SelectedNode();
                kept.setDocumentId(keptDocId);
                kept.setCaseIds(List.of(caseId));
                snapshotService.updateCases(reviewId, keptDoc.getProjectId(), List.of(kept));

                verify(reviewNodeSnapshotMapper).deleteByReviewIdAndDocumentId(reviewId, removedSnapDocId);
                verify(reviewModuleSnapshotMapper).deleteById(removedSnapDocId);
                // 移除文档后无目录残留，无空目录可剪
                verify(reviewModuleSnapshotMapper, never()).deleteById(keptSnapDocId);
        }

        @Test
        void deleteByReviewId_cascadesWhenNoPhysicalForeignKey() {
                snapshotService.deleteByReviewId(reviewId);

                verify(reviewNodeSnapshotMapper).deleteByReviewId(reviewId);
                verify(reviewModuleSnapshotMapper).deleteByReviewId(reviewId);
        }

        @Test
        void applyMark_carrierOnlyContainsIdAndMark() {
                UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-000000000222");

                snapshotService.applyMark(snapshotId, UUID.fromString("00000000-0000-0000-0000-000000000002"), "fail",
                        LocalDateTime.of(2026, 9, 13, 12, 0));

                ArgumentCaptor<TestReviewNodeSnapshot> captor = ArgumentCaptor.forClass(TestReviewNodeSnapshot.class);
                verify(reviewNodeSnapshotMapper).updateById(captor.capture());
                assertEquals(snapshotId, captor.getValue().getId());
                assertEquals("fail", captor.getValue().getLastMark());
                assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        captor.getValue().getLastReviewerId());
        }

        @Test
        void resetMarkAsPending_delegatesToMapper() {
                UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-000000000221");
                UUID reviewerId = UUID.fromString("00000000-0000-0000-0000-000000000002");

                snapshotService.resetMarkAsPending(snapshotId, reviewerId, LocalDateTime.now());

                verify(reviewNodeSnapshotMapper).resetLastMarkAsPending(eq(snapshotId), eq(reviewerId),
                        any(LocalDateTime.class));
        }

        @Test
        void getPlannedCases_groupsByDocument() {
                UUID snapshotDocId = UUID.fromString("00000000-0000-0000-0000-000000000230");
                UUID caseId = UUID.fromString("00000000-0000-0000-0000-000000000231");

                TestReviewModuleSnapshot docSnap = moduleSnapshot(snapshotDocId, null,
                        "文档", Constants.ModuleType.DOCUMENT);
                docSnap.setOriginalModuleId(docId);

                TestReviewNodeSnapshot assoc = new TestReviewNodeSnapshot();
                assoc.setOriginalNodeId(caseId);

                when(reviewModuleSnapshotMapper.listByReviewIdAndType(reviewId, Constants.ModuleType.DOCUMENT))
                        .thenReturn(List.of(docSnap));
                when(reviewNodeSnapshotMapper.listAssociatedByReviewIdAndDocumentId(reviewId, snapshotDocId))
                        .thenReturn(List.of(assoc));

                List<PlannedCasesRespDTO> planned = snapshotService.getPlannedCases(reviewId);

                assertEquals(1, planned.size());
                assertEquals(docId, planned.get(0).getDocumentId());
                assertEquals(List.of(caseId), planned.get(0).getCaseIds());
        }

        private TestReviewNodeSnapshot snapshotNode(UUID id, UUID parentId, boolean associated) {
                TestReviewNodeSnapshot snap = new TestReviewNodeSnapshot();
                snap.setId(id);
                snap.setParentId(parentId);
                snap.setIsAssociated(associated);
                return snap;
        }

        private TestReviewModuleSnapshot moduleSnapshot(UUID id, UUID parentId, String name, String type) {
                TestReviewModuleSnapshot snap = new TestReviewModuleSnapshot();
                snap.setId(id);
                snap.setParentId(parentId);
                snap.setName(name);
                snap.setType(type);
                return snap;
        }
}