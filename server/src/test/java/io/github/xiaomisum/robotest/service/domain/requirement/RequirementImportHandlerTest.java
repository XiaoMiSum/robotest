package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatMedia;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.domain.file.FileContent;
import io.github.xiaomisum.robotest.service.domain.file.FileResourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementImportHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID FILE_ID = UUID.randomUUID();

    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private FileResourceService fileResourceService;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private RequirementImportHandler handler;

    private static FileContent file(String fileName, byte[] content) {
        return new FileContent(content, "application/octet-stream", fileName);
    }

    private void stubContext() {
        when(context.getInput()).thenReturn(Map.of("fileId", FILE_ID.toString()));
    }

    private void stubContextWithProject() {
        stubContext();
        when(context.getProjectId()).thenReturn(PROJECT_ID);
    }

    /** 构造最小 docx（zip 容器 + word/document.xml） */
    private static byte[] docxWith(String documentXml) {
        return zipOf("word/document.xml", documentXml);
    }

    private static byte[] zipOf(String entryName, String content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    // ---------- SPI 契约 ----------

    @Test
    void type_isRequirementImport() {
        assertEquals("requirement_import", handler.type());
        assertTrue(handler.defaultPrompt().contains("{{documentText}}"));
        assertTrue(handler.defaultPrompt().contains("{{documentName}}"));
        assertTrue(handler.defaultPrompt().contains("documentMeta"));
    }

    @Test
    void validateInput_missingFileId_throwsInputInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of()));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_unparsableUuid_throwsInputInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("fileId", "not-a-uuid")));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void checkPermission_withoutCreateAuthority_throwsNoPermission() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("requirement:view"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.checkPermission(user));
        assertEquals(ErrorCodeConstants.REQUIREMENT_NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void checkPermission_withCreateAuthority_passes() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("requirement:create"));

        handler.checkPermission(user); // 不抛即通过
    }

    // ---------- 执行：三类文件 ----------

    @Test
    void execute_markdown_rendersTextAndReturnsMeta() {
        stubContextWithProject();
        when(fileResourceService.readBytes(FILE_ID)).thenReturn(
                file("PRD.md", "## 登录\n验证码必填".getBytes(StandardCharsets.UTF_8)));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(context.prompt(any(), any())).thenReturn("rendered prompt");
        String modelOutput = """
                {"documentMeta":{"detectedVersion":"V2.3","versionEvidence":"本文档对应 V2.3 版本"},
                 "artifacts":[{"content":{"title":"验证码必填","description":"d","moduleId":null,"priority":"high"}}]}
                """;
        when(context.chat(any(), eq("rendered prompt"), eq(null)))
                .thenReturn(new AiChatReply(modelOutput, 11, 22));

        TaskResult result = handler.execute(context);

        assertEquals(11, result.tokensIn());
        assertEquals(22, result.tokensOut());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) result.result().get("documentMeta");
        assertEquals("V2.3", meta.get("detectedVersion"));
        assertEquals("本文档对应 V2.3 版本", meta.get("versionEvidence"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(1, artifacts.size());

        ArgumentCaptor<Map<String, String>> varCaptor = ArgumentCaptor.forClass(Map.class);
        verify(context).prompt(any(), varCaptor.capture());
        assertTrue(varCaptor.getValue().get("documentText").contains("验证码必填"));
        assertEquals("PRD.md", varCaptor.getValue().get("documentName"));
        verify(context).report(50, "模型解析文档");
    }

    @Test
    void execute_docx_extractsParagraphText() {
        stubContextWithProject();
        byte[] docx = docxWith("<w:document xmlns:w=\"x\"><w:body>"
                + "<w:p><w:r><w:t>订单列表分页</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>导出需权限校验</w:t></w:r></w:p>"
                + "</w:body></w:document>");
        when(fileResourceService.readBytes(FILE_ID)).thenReturn(file("需求.docx", docx));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(context.prompt(any(), any())).thenReturn("p");
        when(context.chat(any(), any(), any())).thenReturn(
                new AiChatReply("{\"documentMeta\":null,\"artifacts\":[]}", 1, 1));

        TaskResult result = handler.execute(context);

        ArgumentCaptor<Map<String, String>> varCaptor = ArgumentCaptor.forClass(Map.class);
        verify(context).prompt(any(), varCaptor.capture());
        String text = varCaptor.getValue().get("documentText");
        assertTrue(text.contains("订单列表分页"));
        assertTrue(text.contains("导出需权限校验"));
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) result.result().get("documentMeta");
        assertNull(meta.get("detectedVersion"));
        assertNull(meta.get("versionEvidence"));
    }

    @Test
    void execute_docxWithoutDocumentXml_throwsUnparsable() {
        stubContext();
        when(fileResourceService.readBytes(FILE_ID))
                .thenReturn(file("a.docx", zipOf("other.txt", "x")));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.REQUIREMENT_IMPORT_EMPTY.code(), exception.getCode());
    }

    @Test
    void execute_docxNotZip_throwsUnparsable() {
        stubContext();
        when(fileResourceService.readBytes(FILE_ID))
                .thenReturn(file("a.docx", "PK\u0003\u0004broken".getBytes(StandardCharsets.UTF_8)));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.REQUIREMENT_IMPORT_EMPTY.code(), exception.getCode());
    }

    @Test
    void execute_image_passesMediaWithMime() {
        stubContextWithProject();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
        when(fileResourceService.readBytes(FILE_ID)).thenReturn(file("截图.png", png));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(context.prompt(any(), any())).thenReturn("p");
        when(context.chat(any(), any(), any())).thenReturn(
                new AiChatReply("{\"documentMeta\":{\"detectedVersion\":null},\"artifacts\":[]}", 5, 6));

        handler.execute(context);

        ArgumentCaptor<AiChatMedia> mediaCaptor = ArgumentCaptor.forClass(AiChatMedia.class);
        verify(context).chat(any(), eq("p"), mediaCaptor.capture());
        AiChatMedia media = mediaCaptor.getValue();
        assertEquals("image/png", media.mimeType());
        assertArrayEquals(png, media.data());

        ArgumentCaptor<Map<String, String>> varCaptor = ArgumentCaptor.forClass(Map.class);
        verify(context).prompt(any(), varCaptor.capture());
        assertEquals("", varCaptor.getValue().get("documentText"));
    }

    // ---------- 执行：失败分支 ----------

    @Test
    void execute_fileDeleted_throwsSourceFileNotFound() {
        stubContext();
        when(fileResourceService.readBytes(FILE_ID))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.FILE_NOT_FOUND));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.REQUIREMENT_SOURCE_FILE_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void execute_blankMarkdown_throwsUnparsable() {
        stubContext();
        when(fileResourceService.readBytes(FILE_ID)).thenReturn(file("a.md", new byte[0]));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.REQUIREMENT_IMPORT_EMPTY.code(), exception.getCode());
    }

    @Test
    void execute_unsupportedExtensionDirectTask_throwsTypeUnsupported() {
        stubContext();
        when(fileResourceService.readBytes(FILE_ID))
                .thenReturn(file("a.pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8)));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.REQUIREMENT_IMPORT_TYPE_UNSUPPORTED.code(), exception.getCode());
    }

    @Test
    void execute_unparsableOutput_throwsModelCallFailed() {
        stubContextWithProject();
        when(fileResourceService.readBytes(FILE_ID))
                .thenReturn(file("a.md", "正文".getBytes(StandardCharsets.UTF_8)));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(context.prompt(any(), any())).thenReturn("p");
        when(context.chat(any(), any(), any())).thenReturn(new AiChatReply("这不是 JSON", 1, 1));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), exception.getCode());
    }

    // ---------- documentMeta 清洗（3.8） ----------

    @Test
    void execute_documentMetaUntrustedValues_sanitizedToNull() {
        stubContextWithProject();
        when(fileResourceService.readBytes(FILE_ID))
                .thenReturn(file("a.md", "正文".getBytes(StandardCharsets.UTF_8)));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(context.prompt(any(), any())).thenReturn("p");
        String modelOutput = """
                {"documentMeta":{"detectedVersion":"%s","versionEvidence":"%s"},"artifacts":[]}
                """.formatted("V" + "9".repeat(60), "引语".repeat(400));
        when(context.chat(any(), any(), any())).thenReturn(new AiChatReply(modelOutput, 1, 1));

        TaskResult result = handler.execute(context);

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) result.result().get("documentMeta");
        assertNull(meta.get("detectedVersion")); // 超 50 字符视为不可信
        assertTrue(String.valueOf(meta.get("versionEvidence")).length() <= 500);
    }
}
