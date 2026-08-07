package io.github.xiaomisum.robotest.service.ai.casegen;


import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCaseGenerateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiStepCompleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTextImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiNodeTreeDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.support.AiRequirementContextAssembler;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCaseGenerationServiceImplTest {

        private static final UUID USER_ID = UUID.randomUUID();
        private static final UUID WORKSPACE_ID = UUID.randomUUID();
        private static final UUID PROJECT_ID = UUID.randomUUID();
        private static final UUID DOC_ID = UUID.randomUUID();
        private static final UUID ROOT_ID = UUID.randomUUID();
        private static final UUID TARGET_ID = UUID.randomUUID();

        @Mock
        private AiGatewayService aiGatewayService;
        @Mock
        private AiOutputValidator outputValidator;
        @Mock
        private AiConfigService aiConfigService;
        @Mock
        private TestCaseModuleMapper testCaseModuleMapper;
        @Mock
        private TestCaseNodeMapper testCaseNodeMapper;
        @Mock
        private AiRequirementContextAssembler requirementContextAssembler;

        @Captor
        private ArgumentCaptor<String> businessDataCaptor;
        @Captor
        private ArgumentCaptor<Function<String, Object>> doneAssemblerCaptor;

        @InjectMocks
        private AiCaseGenerationServiceImpl service;

        @BeforeEach
        void stubDefaultRequirementContext() {
                // 大部分场景不关心需求上下文内容，空上下文即可；需要特定内容的测试单独覆盖
                lenient().when(requirementContextAssembler.assemble(any(), any(), any(), any()))
                                .thenReturn(new AiRequirementContextAssembler.RequirementContext("", List.of()));
        }

        private AiCaseGenerateReqDTO req() {
                AiCaseGenerateReqDTO dto = new AiCaseGenerateReqDTO();
                dto.setDocumentId(DOC_ID);
                dto.setTargetNodeId(TARGET_ID);
                dto.setRequirementText("用户可以通过邮箱登录系统");
                return dto;
        }

        private TestCaseModule document(UUID projectId, String type) {
                TestCaseModule module = new TestCaseModule();
                module.setId(DOC_ID);
                module.setProjectId(projectId);
                module.setType(type);
                module.setName("登录用例文档");
                return module;
        }

        private TestCaseNode node(UUID id, UUID parentId, String title, int sortOrder) {
                TestCaseNode node = new TestCaseNode();
                node.setId(id);
                node.setDocumentId(DOC_ID);
                node.setParentId(parentId);
                node.setType(Constants.NodeType.NORMAL);
                node.setTitle(title);
                node.setSortOrder(sortOrder);
                return node;
        }

        @Test
        void documentNotFound_throws() {
                when(testCaseModuleMapper.selectById(DOC_ID)).thenReturn(null);
                assertThrows(ServiceException.class,
                                () -> service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req()));
        }

        @Test
        void documentInOtherProject_throws() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(UUID.randomUUID(), Constants.ModuleType.DOCUMENT));
                assertThrows(ServiceException.class,
                                () -> service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req()));
        }

        @Test
        void moduleIsDirectory_throws() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DIRECTORY));
                assertThrows(ServiceException.class,
                                () -> service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req()));
        }

        @Test
        void targetNodeNotInDocument_throws() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                when(testCaseNodeMapper.listByDocumentId(DOC_ID))
                                .thenReturn(List.of(node(ROOT_ID, null, "根", 0)));
                assertThrows(ServiceException.class,
                                () -> service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req()));
        }

        @Test
        void happyPath_buildsContextAndTrimsChildrenTo50() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                when(requirementContextAssembler.assemble(eq(PROJECT_ID), any(), any(), any()))
                                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                                                "【需求文本】\n用户可以通过邮箱登录系统\n", List.of()));
                List<TestCaseNode> nodes = new ArrayList<>();
                nodes.add(node(ROOT_ID, null, "登录模块", 0));
                nodes.add(node(TARGET_ID, ROOT_ID, "密码登录", 0));
                for (int i = 1; i <= 60; i++) {
                        nodes.add(node(UUID.randomUUID(), TARGET_ID, "既有子节点" + i, i));
                }
                when(testCaseNodeMapper.listByDocumentId(DOC_ID)).thenReturn(nodes);
                when(aiGatewayService.stream(any(), eq(AiFunctionType.CASE_GENERATION), any(),
                                businessDataCaptor.capture(), any(), any(), any())).thenReturn(new SseEmitter());

                service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req());

                String businessData = businessDataCaptor.getValue();
                assertTrue(businessData.contains("用户可以通过邮箱登录系统"));
                assertTrue(businessData.contains("登录模块 > 密码登录"));
                // 同级参照裁剪至前 50 条（4.7）
                assertTrue(businessData.contains("既有子节点50"));
                assertFalse(businessData.contains("既有子节点51"));
        }

        @Test
        void doneAssembler_wrapsNodesAndWarnings() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                when(testCaseNodeMapper.listByDocumentId(DOC_ID))
                                .thenReturn(List.of(node(TARGET_ID, null, "根", 0)));
                when(aiGatewayService.stream(any(), any(), any(), any(), any(), any(),
                                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());

                AiNodeTreeDTO caseNode = new AiNodeTreeDTO();
                caseNode.setType(Constants.NodeType.CASE);
                caseNode.setTitle("超".repeat(250));
                caseNode.setPriority("P1");
                AiNodeTreeDTO.Payload payload = new AiNodeTreeDTO.Payload();
                payload.setNodes(List.of(caseNode));
                when(outputValidator.parseAndValidate(eq("raw"), eq(AiNodeTreeDTO.Payload.class), any()))
                                .thenReturn(payload);

                service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, req());
                Object doneData = doneAssemblerCaptor.getValue().apply("raw");

                assertTrue(doneData instanceof Map<?, ?>);
                Map<?, ?> map = (Map<?, ?>) doneData;
                assertEquals(payload.getNodes(), map.get("nodes"));
                // 截断规整计入 warnings，不触发校验失败
                assertEquals(1, ((List<?>) map.get("warnings")).size());
                verify(outputValidator).parseAndValidate(eq("raw"), eq(AiNodeTreeDTO.Payload.class), any());
        }

        // ==================== US-AI-002 补全步骤 ====================

        private AiStepCompleteReqDTO completeReq() {
                AiStepCompleteReqDTO dto = new AiStepCompleteReqDTO();
                dto.setDocumentId(DOC_ID);
                dto.setNodeId(TARGET_ID);
                dto.setExtraText("补充：验证码为 6 位数字");
                return dto;
        }

        @Test
        void completeSteps_nonCaseNode_throwsTargetStateInvalid() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                // node() 构造的是 normal 类型节点
                when(testCaseNodeMapper.listByDocumentId(DOC_ID))
                                .thenReturn(List.of(node(TARGET_ID, null, "普通节点", 0)));
                assertThrows(ServiceException.class,
                                () -> service.completeSteps(USER_ID, WORKSPACE_ID, PROJECT_ID, completeReq()));
        }

        @Test
        void completeSteps_buildsDedupContextAndExcludesSelfFromSiblings() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                when(requirementContextAssembler.assemble(eq(PROJECT_ID), any(), any(), any()))
                                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                                                "【需求文本】\n补充：验证码为 6 位数字\n", List.of()));
                TestCaseNode root = node(ROOT_ID, null, "登录模块", 0);
                TestCaseNode target = node(TARGET_ID, ROOT_ID, "短信登录成功", 0);
                target.setType(Constants.NodeType.CASE);
                TestCaseNode sibling = node(UUID.randomUUID(), ROOT_ID, "密码登录成功", 1);
                TestCaseNode existingStep = node(UUID.randomUUID(), TARGET_ID, "输入手机号", 0);
                existingStep.setType(Constants.NodeType.STEP);
                when(testCaseNodeMapper.listByDocumentId(DOC_ID))
                                .thenReturn(List.of(root, target, sibling, existingStep));
                when(aiGatewayService.stream(any(), eq(AiFunctionType.STEP_COMPLETION), any(),
                                businessDataCaptor.capture(), any(), any(), any())).thenReturn(new SseEmitter());

                service.completeSteps(USER_ID, WORKSPACE_ID, PROJECT_ID, completeReq());

                String businessData = businessDataCaptor.getValue();
                assertTrue(businessData.contains("【用例标题】短信登录成功"));
                assertTrue(businessData.contains("登录模块 > 短信登录成功"));
                assertTrue(businessData.contains("密码登录成功"));
                assertTrue(businessData.contains("step：输入手机号"));
                assertTrue(businessData.contains("补充：验证码为 6 位数字"));
                // 同级参照不含目标节点自身（否则模型会把自身当作"避免重复"的对象）
                assertFalse(businessData.contains("【同级节点】短信登录成功"));
        }

        @Test
        void completeSteps_doneAssembler_allowsEmptyFlatResult() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                TestCaseNode target = node(TARGET_ID, null, "用例", 0);
                target.setType(Constants.NodeType.CASE);
                when(testCaseNodeMapper.listByDocumentId(DOC_ID)).thenReturn(List.of(target));
                when(aiGatewayService.stream(any(), any(), any(), any(), any(), any(),
                                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());
                AiNodeTreeDTO.Payload payload = new AiNodeTreeDTO.Payload();
                payload.setNodes(List.of());
                when(outputValidator.parseAndValidate(eq("raw"), eq(AiNodeTreeDTO.Payload.class), any()))
                                .thenReturn(payload);

                service.completeSteps(USER_ID, WORKSPACE_ID, PROJECT_ID, completeReq());
                Map<?, ?> map = (Map<?, ?>) doneAssemblerCaptor.getValue().apply("raw");

                // 空数组表示无需补全，属正常结果不报 6003
                assertEquals(List.of(), map.get("nodes"));
        }

        // ==================== US-AI-016 外部文本导入 ====================

        private AiTextImportReqDTO importReq(String text) {
                AiTextImportReqDTO dto = new AiTextImportReqDTO();
                dto.setDocumentId(DOC_ID);
                dto.setTargetNodeId(TARGET_ID);
                dto.setText(text);
                return dto;
        }

        @Test
        void importText_overLimit_throwsValidationFailed() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                when(testCaseNodeMapper.listByDocumentId(DOC_ID))
                                .thenReturn(List.of(node(TARGET_ID, null, "根", 0)));
                when(aiConfigService.getIntSetting("importTextMaxLength")).thenReturn(100);
                assertThrows(ServiceException.class,
                                () -> service.importText(USER_ID, WORKSPACE_ID, PROJECT_ID,
                                                importReq("字".repeat(101))));
        }

        @Test
        void importText_emptyParsedTree_returnsWarningNotFailure() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                when(testCaseNodeMapper.listByDocumentId(DOC_ID))
                                .thenReturn(List.of(node(TARGET_ID, null, "根", 0)));
                when(aiConfigService.getIntSetting("importTextMaxLength")).thenReturn(20000);
                when(aiGatewayService.stream(any(), eq(AiFunctionType.TEXT_IMPORT), any(), any(), any(), any(),
                                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());
                AiNodeTreeDTO.Payload payload = new AiNodeTreeDTO.Payload();
                payload.setNodes(List.of());
                when(outputValidator.parseAndValidate(eq("raw"), eq(AiNodeTreeDTO.Payload.class), any()))
                                .thenReturn(payload);

                service.importText(USER_ID, WORKSPACE_ID, PROJECT_ID, importReq("无法解析的闲聊文本"));
                Map<?, ?> map = (Map<?, ?>) doneAssemblerCaptor.getValue().apply("raw");

                assertEquals(List.of(), map.get("nodes"));
                List<?> warnings = (List<?>) map.get("warnings");
                assertTrue(warnings.stream().anyMatch(w -> String.valueOf(w).contains("未能解析出用例结构")));
        }

        @Test
        void importText_targetNotInDocument_throws() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                when(testCaseNodeMapper.listByDocumentId(DOC_ID))
                                .thenReturn(List.of(node(ROOT_ID, null, "根", 0)));
                assertThrows(ServiceException.class,
                                () -> service.importText(USER_ID, WORKSPACE_ID, PROJECT_ID, importReq("需求文本")));
        }

        // ==================== US-AI-004 需求池消费接线 ====================

        private void stubDocumentAndTarget() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                when(testCaseNodeMapper.listByDocumentId(DOC_ID))
                                .thenReturn(List.of(node(TARGET_ID, null, "根", 0)));
        }

        private AiNodeTreeDTO.Payload validPayload() {
                AiNodeTreeDTO caseNode = new AiNodeTreeDTO();
                caseNode.setType(Constants.NodeType.CASE);
                caseNode.setTitle("校验用例");
                caseNode.setPriority("P1");
                AiNodeTreeDTO.Payload payload = new AiNodeTreeDTO.Payload();
                payload.setNodes(List.of(caseNode));
                return payload;
        }

        @Test
        void generate_textAndRequirementIdsBothEmpty_throws() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                AiCaseGenerateReqDTO dto = req();
                dto.setRequirementText(null);
                assertThrows(ServiceException.class,
                                () -> service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, dto));
        }

        @Test
        void generate_saveAsRequirementWithoutText_throws() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                AiCaseGenerateReqDTO dto = req();
                dto.setRequirementText(null);
                AiCaseGenerateReqDTO.SaveAsRequirement saveAs = new AiCaseGenerateReqDTO.SaveAsRequirement();
                saveAs.setTitle("登录需求");
                dto.setSaveAsRequirement(saveAs);
                assertThrows(ServiceException.class,
                                () -> service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, dto));
        }

        @Test
        void generate_saveAsRequirement_savesItemBeforeStreaming() {
                stubDocumentAndTarget();
                AiCaseGenerateReqDTO dto = req();
                AiCaseGenerateReqDTO.SaveAsRequirement saveAs = new AiCaseGenerateReqDTO.SaveAsRequirement();
                saveAs.setTitle("登录需求");
                dto.setSaveAsRequirement(saveAs);
                when(requirementContextAssembler.trySaveRequirement(eq(PROJECT_ID), eq(USER_ID), any(), any()))
                                .thenReturn(true);
                when(aiGatewayService.stream(any(), eq(AiFunctionType.CASE_GENERATION), any(), any(), any(),
                                any(), any())).thenReturn(new SseEmitter());

                service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

                verify(requirementContextAssembler).trySaveRequirement(eq(PROJECT_ID), eq(USER_ID),
                                eq("登录需求"), eq("用户可以通过邮箱登录系统"));
        }

        @Test
        void generate_saveAsRequirementSaveFails_continuesAndWarnsInDone() {
                stubDocumentAndTarget();
                AiCaseGenerateReqDTO dto = req();
                AiCaseGenerateReqDTO.SaveAsRequirement saveAs = new AiCaseGenerateReqDTO.SaveAsRequirement();
                saveAs.setTitle("登录需求");
                dto.setSaveAsRequirement(saveAs);
                when(requirementContextAssembler.trySaveRequirement(any(), any(), any(), any()))
                                .thenReturn(false);
                when(aiGatewayService.stream(any(), any(), any(), any(), any(), any(),
                                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());
                when(outputValidator.parseAndValidate(eq("raw"), eq(AiNodeTreeDTO.Payload.class), any()))
                                .thenReturn(validPayload());

                service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);
                Map<?, ?> map = (Map<?, ?>) doneAssemblerCaptor.getValue().apply("raw");

                List<?> warnings = (List<?>) map.get("warnings");
                assertTrue(warnings.stream()
                                .anyMatch(w -> String.valueOf(w).contains("临时需求保存为需求池条目失败")));
        }

        @Test
        void generate_withRequirementIds_appendsTitledBlocks() {
                stubDocumentAndTarget();
                UUID reqId = UUID.randomUUID();
                when(requirementContextAssembler.assemble(eq(PROJECT_ID), eq(List.of(reqId)), any(), any()))
                                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                                                "【需求条目】登录需求\n用户可通过邮箱与密码登录\n", List.of()));
                AiCaseGenerateReqDTO dto = req();
                dto.setRequirementIds(List.of(reqId));
                when(aiGatewayService.stream(any(), any(), any(), businessDataCaptor.capture(), any(), any(),
                                any())).thenReturn(new SseEmitter());

                service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

                String businessData = businessDataCaptor.getValue();
                assertTrue(businessData.contains("【需求条目】登录需求"));
                assertTrue(businessData.contains("用户可通过邮箱与密码登录"));
        }

        @Test
        void generate_singleItemOverBudget_truncatesContent() {
                stubDocumentAndTarget();
                UUID reqId = UUID.randomUUID();
                // 截断逻辑已下沉至 AiRequirementContextAssembler（AiTextUtilsTest 覆盖），此处验证组装结果接入
                when(requirementContextAssembler.assemble(eq(PROJECT_ID), eq(List.of(reqId)), any(), any()))
                                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                                                "长需求：用户可通过邮箱登录…", List.of()));
                AiCaseGenerateReqDTO dto = req();
                dto.setRequirementIds(List.of(reqId));
                when(aiGatewayService.stream(any(), any(), any(), businessDataCaptor.capture(), any(), any(),
                                any())).thenReturn(new SseEmitter());

                service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

                String businessData = businessDataCaptor.getValue();
                // 单条目内容截断至 token 预算，尾部加省略号
                assertTrue(businessData.contains("…"));
                assertTrue(businessData.length() < 9000);
        }

        @Test
        void generate_contextBudgetExceeded_dropsLaterItemsWithWarning() {
                stubDocumentAndTarget();
                UUID reqA = UUID.randomUUID();
                UUID reqB = UUID.randomUUID();
                // 预算裁剪与丢弃提示已下沉至 AiRequirementContextAssembler，此处验证 warning 透传
                when(requirementContextAssembler.assemble(eq(PROJECT_ID), eq(List.of(reqA, reqB)), any(), any()))
                                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                                                "条目A 需求内容\n",
                                                List.of("需求上下文超出预算，已按选取顺序丢弃后续需求条目")));
                AiCaseGenerateReqDTO dto = req();
                dto.setRequirementIds(List.of(reqA, reqB));
                when(aiGatewayService.stream(any(), any(), any(), businessDataCaptor.capture(), any(), any(),
                                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());
                when(outputValidator.parseAndValidate(eq("raw"), eq(AiNodeTreeDTO.Payload.class), any()))
                                .thenReturn(validPayload());

                service.generateCaseTree(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

                String businessData = businessDataCaptor.getValue();
                assertTrue(businessData.contains("条目A"));
                assertFalse(businessData.contains("条目B"));
                Map<?, ?> map = (Map<?, ?>) doneAssemblerCaptor.getValue().apply("raw");
                assertTrue(((List<?>) map.get("warnings")).stream()
                                .anyMatch(w -> String.valueOf(w).contains("超出预算")));
        }

        @Test
        void completeSteps_withRequirementIds_appendsBlocks() {
                when(testCaseModuleMapper.selectById(DOC_ID))
                                .thenReturn(document(PROJECT_ID, Constants.ModuleType.DOCUMENT));
                TestCaseNode target = node(TARGET_ID, null, "用例", 0);
                target.setType(Constants.NodeType.CASE);
                when(testCaseNodeMapper.listByDocumentId(DOC_ID)).thenReturn(List.of(target));
                UUID reqId = UUID.randomUUID();
                when(requirementContextAssembler.assemble(eq(PROJECT_ID), eq(List.of(reqId)), any(), any()))
                                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                                                "【需求条目】需求X\n覆盖支付流程\n", List.of()));
                AiStepCompleteReqDTO dto = completeReq();
                dto.setRequirementIds(List.of(reqId));
                when(aiGatewayService.stream(any(), eq(AiFunctionType.STEP_COMPLETION), any(),
                                businessDataCaptor.capture(), any(), any(), any())).thenReturn(new SseEmitter());

                service.completeSteps(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

                String businessData = businessDataCaptor.getValue();
                assertTrue(businessData.contains("【需求条目】需求X"));
                assertTrue(businessData.contains("覆盖支付流程"));
        }
}
