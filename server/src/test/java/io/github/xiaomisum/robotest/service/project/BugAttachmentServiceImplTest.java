package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentDownloadDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.repository.bug.BugAttachmentMapper;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    @InjectMocks
    private BugAttachmentServiceImpl bugAttachmentService;

    @TempDir
    Path tempDir;

    private UUID bugId;
    private UUID userId;
    private UUID attachmentId;

    @BeforeEach
    void setUp() {
        bugId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        attachmentId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        ReflectionTestUtils.setField(bugAttachmentService, "uploadDir", tempDir.toString());
    }

    private Bug openBug() {
        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setStatus(Constants.BugStatus.ACTIVE);
        return bug;
    }

    // ========== uploadAttachment ==========

    @Test
    void uploadAttachment_success() throws Exception {
        when(bugMapper.selectById(bugId)).thenReturn(openBug());
        doAnswer(inv -> {
            ((BugAttachment) inv.getArgument(0)).setId(attachmentId);
            return 1;
        }).when(bugAttachmentMapper).insert(any(BugAttachment.class));
        doAnswer(inv -> {
            ((BugLog) inv.getArgument(0)).setId(UUID.randomUUID());
            return 1;
        }).when(bugLogMapper).insert(any(BugLog.class));

        MockMultipartFile file = new MockMultipartFile(
                "file", "截图.png", "image/png", "png-bytes".getBytes(StandardCharsets.UTF_8));

        BugAttachmentRespDTO dto = bugAttachmentService.uploadAttachment(bugId, userId, file);

        assertEquals(attachmentId, dto.getId());
        assertEquals("截图.png", dto.getFileName());
        assertEquals((long) "png-bytes".getBytes(StandardCharsets.UTF_8).length, dto.getFileSize());

        ArgumentCaptor<BugAttachment> captor = ArgumentCaptor.forClass(BugAttachment.class);
        verify(bugAttachmentMapper).insert(captor.capture());
        BugAttachment saved = captor.getValue();
        assertTrue(saved.getStoragePath().endsWith(".png"));
        assertTrue(Files.exists(tempDir.resolve(saved.getStoragePath())));

        ArgumentCaptor<BugLog> logCaptor = ArgumentCaptor.forClass(BugLog.class);
        verify(bugLogMapper).insert(logCaptor.capture());
        assertEquals(Constants.BugOperation.ATTACHMENT_UPLOAD, logCaptor.getValue().getOperationType());
    }

    @Test
    void uploadAttachment_bugNotFound() {
        when(bugMapper.selectById(bugId)).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.uploadAttachment(bugId, userId, file));
        verify(bugAttachmentMapper, never()).insert(any(BugAttachment.class));
    }

    @Test
    void uploadAttachment_bugClosed() {
        Bug bug = openBug();
        bug.setStatus(Constants.BugStatus.CLOSED);
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.uploadAttachment(bugId, userId, file));
        verify(bugAttachmentMapper, never()).insert(any(BugAttachment.class));
    }

    @Test
    void uploadAttachment_sizeExceeded() {
        when(bugMapper.selectById(bugId)).thenReturn(openBug());
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(11L * 1024 * 1024);

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.uploadAttachment(bugId, userId, file));
        verify(bugAttachmentMapper, never()).insert(any(BugAttachment.class));
    }

    @Test
    void uploadAttachment_emptyFile() {
        when(bugMapper.selectById(bugId)).thenReturn(openBug());
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.uploadAttachment(bugId, userId, file));
        verify(bugAttachmentMapper, never()).insert(any(BugAttachment.class));
    }

    // ========== getAttachments ==========

    @Test
    void getAttachments_success() {
        BugAttachment attachment = new BugAttachment();
        attachment.setId(attachmentId);
        attachment.setBugId(bugId);
        attachment.setFileName("log.txt");
        attachment.setFileSize(12L);
        attachment.setUploaderId(userId);
        when(bugAttachmentMapper.selectList(any())).thenReturn(List.of(attachment));

        SysUser uploader = new SysUser();
        uploader.setId(userId);
        uploader.setUsername("tester");
        when(userMapper.selectById(userId)).thenReturn(uploader);

        List<BugAttachmentRespDTO> result = bugAttachmentService.getAttachments(bugId);

        assertEquals(1, result.size());
        assertEquals("log.txt", result.get(0).getFileName());
        assertEquals("tester", result.get(0).getUploaderName());
    }

    // ========== downloadAttachment ==========

    @Test
    void downloadAttachment_success() throws Exception {
        Path stored = tempDir.resolve("stored.txt");
        Files.writeString(stored, "hello");

        BugAttachment attachment = new BugAttachment();
        attachment.setId(attachmentId);
        attachment.setFileName("原始名.txt");
        attachment.setStoragePath("stored.txt");
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(attachment);

        BugAttachmentDownloadDTO dto = bugAttachmentService.downloadAttachment(attachmentId);

        assertEquals("原始名.txt", dto.getFileName());
        // contentType 为空时回退为通用二进制类型
        assertEquals("application/octet-stream", dto.getContentType());
        assertEquals("hello", new String(dto.getContent(), StandardCharsets.UTF_8));
    }

    @Test
    void downloadAttachment_notFound() {
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.downloadAttachment(attachmentId));
    }

    @Test
    void downloadAttachment_fileMissing() {
        BugAttachment attachment = new BugAttachment();
        attachment.setId(attachmentId);
        attachment.setFileName("gone.txt");
        attachment.setStoragePath("not-exists/gone.txt");
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(attachment);

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.downloadAttachment(attachmentId));
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

        bugAttachmentService.deleteAttachment(attachmentId, userId);

        verify(bugAttachmentMapper).deleteById(attachmentId);
        ArgumentCaptor<BugLog> logCaptor = ArgumentCaptor.forClass(BugLog.class);
        verify(bugLogMapper).insert(logCaptor.capture());
        assertEquals(Constants.BugOperation.ATTACHMENT_DELETE, logCaptor.getValue().getOperationType());
    }

    @Test
    void deleteAttachment_notFound() {
        when(bugAttachmentMapper.selectById(attachmentId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> bugAttachmentService.deleteAttachment(attachmentId, userId));
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
                () -> bugAttachmentService.deleteAttachment(attachmentId, userId));
        verify(bugAttachmentMapper, never()).deleteById(any(UUID.class));
    }
}
