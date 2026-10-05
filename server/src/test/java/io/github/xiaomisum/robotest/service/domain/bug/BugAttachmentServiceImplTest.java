package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.convert.BugConvertMapper;
import io.github.xiaomisum.robotest.model.convert.BugConvertMapperImpl;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentDownloadRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.file.FileResourceRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.repository.bug.BugAttachmentMapper;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.service.domain.file.FileContent;
import io.github.xiaomisum.robotest.service.domain.file.FileResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.exception.ServiceException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BugAttachmentServiceImplTest {

    @Mock
    private BugAttachmentMapper bugAttachmentMapper;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private BugLogMapper bugLogMapper;
    @Mock
    private SysUserMapper userMapper;

    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private FileResourceService fileResourceService;
    @Spy
    private BugConvertMapper bugConvertMapper = new BugConvertMapperImpl();

    @InjectMocks
    private BugAttachmentServiceImpl bugAttachmentService;

    @TempDir
    Path tempDir;

    private UUID bugId;
    private UUID userId;
    private UUID attachmentId;
    private UUID projectId;

    /** 真实 PNG 文件头 + 数据（安全规范 6.3：内容嗅探只认真实魔数） */
    private static final byte[] PNG_MAGIC_AND_DATA = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @BeforeEach
    void setUp() {
        bugId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        attachmentId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        projectId = UUID.fromString("00000000-0000-0000-0000-000000000009");
        ReflectionTestUtils.setField(bugAttachmentService, "uploadDir", tempDir.toString());
    }

    private Bug openBug() {
        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setProjectId(projectId);
        bug.setStatus(Constants.BugStatus.ACTIVE);
        return bug;
    }

    // ========== uploadAttachment ==========

    @Test
    void uploadAttachment_success() throws Exception {
        when(bugMapper.selectById(bugId)).thenReturn(openBug());
        UUID fileResourceId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        FileResourceRespDTO stored = new FileResourceRespDTO();
        stored.setId(fileResourceId);
        when(fileResourceService.upload(any(MultipartFile.class), eq(userId))).thenReturn(stored);
        doAnswer(inv -> {
            ((BugAttachment) inv.getArgument(0)).setId(attachmentId);
            return 1;
        }).when(bugAttachmentMapper).insert(any(BugAttachment.class));
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        MockMultipartFile file = new MockMultipartFile(
                "file", "截图.png", "image/png", PNG_MAGIC_AND_DATA);

        BugAttachmentRespDTO dto = bugAttachmentService.uploadAttachment(projectId, bugId, userId, file);

        assertEquals(attachmentId, dto.getId());
        assertEquals("截图.png", dto.getFileName());
        assertEquals((long) PNG_MAGIC_AND_DATA.length, dto.getFileSize());

        ArgumentCaptor<BugAttachment> captor = ArgumentCaptor.forClass(BugAttachment.class);
        verify(bugAttachmentMapper).insert(captor.capture());
        BugAttachment saved = captor.getValue();
        // 存储改走文件管理模块：关联 file_resource，storage_path 历史列不再写入（文件管理详设 4.3）
        assertEquals(fileResourceId, saved.getFileResourceId());
        assertNull(saved.getStoragePath());

        ArgumentCaptor<BugLog> logCaptor = ArgumentCaptor.forClass(BugLog.class);
        verify(bugLogMapper).insert(logCaptor.capture());
        assertEquals(Constants.BugOperation.ATTACHMENT_UPLOAD, logCaptor.getValue().getOperationType());
    }

    @Test
    void uploadAttachment_bugNotFound() {
        when(bugMapper.selectById(bugId)).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.uploadAttachment(projectId, bugId, userId, file));
        verify(bugAttachmentMapper, never()).insert(any(BugAttachment.class));
    }

    @Test
    void uploadAttachment_bugClosed() {
        Bug bug = openBug();
        bug.setStatus(Constants.BugStatus.CLOSED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.uploadAttachment(projectId, bugId, userId, file));
        verify(bugAttachmentMapper, never()).insert(any(BugAttachment.class));
    }

    @Test
    void uploadAttachment_sizeExceeded() {
        when(bugMapper.selectById(bugId)).thenReturn(openBug());
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(11L * 1024 * 1024);

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.uploadAttachment(projectId, bugId, userId, file));
        verify(bugAttachmentMapper, never()).insert(any(BugAttachment.class));
    }

    @Test
    void uploadAttachment_emptyFile() {
        when(bugMapper.selectById(bugId)).thenReturn(openBug());
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.uploadAttachment(projectId, bugId, userId, file));
        verify(bugAttachmentMapper, never()).insert(any(BugAttachment.class));
    }

    @Test
    void uploadAttachment_rejectsDisallowedExtension() {
        when(bugMapper.selectById(bugId)).thenReturn(openBug());
        MockMultipartFile file = new MockMultipartFile("file", "evil.html", "text/html",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> bugAttachmentService.uploadAttachment(projectId, bugId, userId, file));
        assertTrue(exception.getMessage().contains("不支持的附件类型"));
        verify(bugAttachmentMapper, never()).insert(any(BugAttachment.class));
    }

    @Test
    void uploadAttachment_rejectsContentMismatch() {
        when(bugMapper.selectById(bugId)).thenReturn(openBug());
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                "not-a-real-png".getBytes(StandardCharsets.UTF_8));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> bugAttachmentService.uploadAttachment(projectId, bugId, userId, file));
        assertTrue(exception.getMessage().contains("附件内容与文件类型不符"));
        verify(bugAttachmentMapper, never()).insert(any(BugAttachment.class));
    }

    // ========== getAttachments ==========

    @Test
    void getAttachments_success() {
        when(bugMapper.selectById(bugId)).thenReturn(openBug());

        BugAttachment attachment = new BugAttachment();
        attachment.setId(attachmentId);
        attachment.setBugId(bugId);
        attachment.setFileName("log.txt");
        attachment.setFileSize(12L);
        attachment.setUploaderId(userId);
        when(bugAttachmentMapper.listByBugId(bugId)).thenReturn(List.of(attachment));

        SysUser uploader = new SysUser();
        uploader.setId(userId);
        uploader.setUsername("tester");
        uploader.setName("钱七");
        when(userMapper.selectById(userId)).thenReturn(uploader);

        List<BugAttachmentRespDTO> result = bugAttachmentService.getAttachments(projectId, bugId, userId);

        assertEquals(1, result.size());
        assertEquals("log.txt", result.get(0).getFileName());
        assertEquals("钱七", result.get(0).getUploaderName());
        verify(projectAccessGuard).requireProjectMember(
                UUID.fromString("00000000-0000-0000-0000-000000000009"), userId);
    }

    // ========== downloadAttachment ==========

    @Test
    void downloadAttachment_success() throws Exception {
        Path stored = tempDir.resolve("stored.txt");
        Files.writeString(stored, "hello");

        BugAttachment attachment = new BugAttachment();
        attachment.setId(attachmentId);
        attachment.setBugId(bugId);
        attachment.setFileName("原始名.txt");
        attachment.setStoragePath("stored.txt");
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(attachment);
        when(bugMapper.selectById(bugId)).thenReturn(openBug());

        BugAttachmentDownloadRespDTO dto = bugAttachmentService.downloadAttachment(projectId, attachmentId, userId);

        assertEquals("原始名.txt", dto.getFileName());
        // contentType 为空时回退为通用二进制类型
        assertEquals("application/octet-stream", dto.getContentType());
        assertEquals("hello", new String(dto.getContent(), StandardCharsets.UTF_8));
        verify(projectAccessGuard).requireProjectMember(
                UUID.fromString("00000000-0000-0000-0000-000000000009"), userId);
    }

    @Test
    void downloadAttachment_notFound() {
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.downloadAttachment(projectId, attachmentId, userId));
    }

    @Test
    void downloadAttachment_fromFileResource() {
        // 已回填行：字节改从文件管理模块（对象存储）读取（文件管理详设 4.3）
        UUID fileResourceId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        BugAttachment attachment = new BugAttachment();
        attachment.setId(attachmentId);
        attachment.setBugId(bugId);
        attachment.setFileName("原始名.txt");
        attachment.setContentType("text/plain");
        attachment.setFileResourceId(fileResourceId);
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(attachment);
        when(bugMapper.selectById(bugId)).thenReturn(openBug());
        when(fileResourceService.readBytes(fileResourceId))
                .thenReturn(new FileContent("s3-bytes".getBytes(StandardCharsets.UTF_8), "text/plain", "原始名.txt"));

        BugAttachmentDownloadRespDTO dto = bugAttachmentService.downloadAttachment(projectId, attachmentId, userId);

        assertEquals("原始名.txt", dto.getFileName());
        assertEquals("text/plain", dto.getContentType());
        assertEquals("s3-bytes", new String(dto.getContent(), StandardCharsets.UTF_8));
    }

    @Test
    void downloadAttachment_crossProject_throws() {
        BugAttachment attachment = new BugAttachment();
        attachment.setId(attachmentId);
        attachment.setBugId(bugId);
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(attachment);
        Bug bug = openBug();
        bug.setProjectId(UUID.randomUUID());
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        // 归属活动项目校验（SEC-014）：跨项目按不存在处理，不泄露缺陷存在性
        ServiceException exception = assertThrows(ServiceException.class,
                () -> bugAttachmentService.downloadAttachment(projectId, attachmentId, userId));
        assertEquals(ErrorCodeConstants.BUG_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void downloadAttachment_fileMissing() {
        BugAttachment attachment = new BugAttachment();
        attachment.setId(attachmentId);
        attachment.setBugId(bugId);
        attachment.setFileName("gone.txt");
        attachment.setStoragePath("not-exists/gone.txt");
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(attachment);
        when(bugMapper.selectById(bugId)).thenReturn(openBug());

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.downloadAttachment(projectId, attachmentId, userId));
    }

    // ========== deleteAttachment ==========

    @Test
    void deleteAttachment_success() {
        BugAttachment attachment = new BugAttachment();
        attachment.setId(attachmentId);
        attachment.setBugId(bugId);
        attachment.setFileName("log.txt");
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(attachment);
        when(bugMapper.selectById(bugId)).thenReturn(openBug());
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        bugAttachmentService.deleteAttachment(projectId, attachmentId, userId);

        verify(bugAttachmentMapper).deleteById(attachmentId);
        ArgumentCaptor<BugLog> logCaptor = ArgumentCaptor.forClass(BugLog.class);
        verify(bugLogMapper).insert(logCaptor.capture());
        assertEquals(Constants.BugOperation.ATTACHMENT_DELETE, logCaptor.getValue().getOperationType());
    }

    @Test
    void deleteAttachment_notFound() {
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.deleteAttachment(projectId, attachmentId, userId));
        verify(bugAttachmentMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteAttachment_bugClosed() {
        BugAttachment attachment = new BugAttachment();
        attachment.setId(attachmentId);
        attachment.setBugId(bugId);
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(attachment);
        Bug bug = openBug();
        bug.setStatus(Constants.BugStatus.CLOSED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.deleteAttachment(projectId, attachmentId, userId));
        verify(bugAttachmentMapper, never()).deleteById(any(UUID.class));
    }
}
