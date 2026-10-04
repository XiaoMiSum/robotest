package io.github.xiaomisum.robotest.service.domain.file;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.file.FilePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.file.FileAccessUrlRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.file.FileResourceRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.file.FileResource;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.file.FileResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 文件资源服务单测（文件管理详设 4）：模块级校验、对象键形态、三通道与错误码映射。
 */
@ExtendWith(MockitoExtension.class)
class FileResourceServiceImplTest {

    @Mock
    private FileResourceMapper fileResourceMapper;
    @Mock
    private MinioStorageService storageService;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private FileResourceServiceImpl service;

    private UUID uploaderId;

    /** 真实 PNG 文件头 + 数据（安全规范 6.3：内容嗅探只认真实魔数） */
    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @BeforeEach
    void setUp() {
        uploaderId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    }

    private FileResource row(UUID id, String name) {
        FileResource row = new FileResource();
        row.setId(id);
        row.setObjectKey("objects/" + id + ".txt");
        row.setFileName(name);
        row.setContentType("text/plain");
        row.setFileSize(10L);
        row.setUploaderId(uploaderId);
        return row;
    }

    // ========== upload / store ==========

    @Test
    void upload_success() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        doAnswer(inv -> {
            try (InputStream in = inv.getArgument(1)) {
                uploaded.set(in.readAllBytes());
            }
            return null;
        }).when(storageService).put(anyString(), any(), anyLong(), anyString());
        doAnswer(inv -> {
            ((FileResource) inv.getArgument(0)).setId(rowId);
            return 1;
        }).when(fileResourceMapper).insert(any(FileResource.class));

        MockMultipartFile file = new MockMultipartFile("file", "截图.PNG", "image/png", PNG_BYTES);
        FileResourceRespDTO dto = service.upload(file, uploaderId);

        assertEquals(rowId, dto.getId());
        assertEquals("截图.PNG", dto.getFileName());
        assertEquals((long) PNG_BYTES.length, dto.getFileSize());
        assertEquals("/api/files/" + rowId + "/download", dto.getDownloadUrl());
        assertArrayEquals(PNG_BYTES, uploaded.get());

        ArgumentCaptor<FileResource> captor = ArgumentCaptor.forClass(FileResource.class);
        verify(fileResourceMapper).insert(captor.capture());
        FileResource saved = captor.getValue();
        assertTrue(saved.getObjectKey().startsWith("objects/"));
        assertTrue(saved.getObjectKey().endsWith(".PNG"));
        // 对象键由服务端 UUID 生成，原始文件名不进键（防路径注入，详设 3.2）
        assertFalse(saved.getObjectKey().contains("截图"));
        assertEquals(uploaderId, saved.getUploaderId());
    }

    @Test
    void upload_emptyFile_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.upload(file, uploaderId));
        assertEquals(ErrorCodeConstants.FILE_EMPTY.code(), ex.getCode());
        verify(fileResourceMapper, never()).insert(any(FileResource.class));
    }

    @Test
    void store_sizeExceeded_rejected() {
        long oversize = 20L * 1024 * 1024 + 1;

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.store("big.txt", "text/plain", oversize, () -> null, uploaderId));
        assertEquals(ErrorCodeConstants.FILE_SIZE_EXCEEDED.code(), ex.getCode());
        verify(storageService, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void store_disallowedExtension_rejected() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.store("evil.html", "text/html", html.length,
                        () -> new ByteArrayInputStream(html), uploaderId));
        assertEquals(ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED.code(), ex.getCode());
        verify(storageService, never()).put(anyString(), any(), anyLong(), anyString());
        verify(fileResourceMapper, never()).insert(any(FileResource.class));
    }

    @Test
    void store_contentMismatch_rejected() {
        byte[] fake = "not-a-real-png".getBytes(StandardCharsets.UTF_8);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.store("fake.png", "image/png", fake.length,
                        () -> new ByteArrayInputStream(fake), uploaderId));
        assertEquals(ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED.code(), ex.getCode());
        verify(storageService, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void store_storageFailure_mapsUploadCode() {
        byte[] txt = "hello".getBytes(StandardCharsets.UTF_8);
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.FILE_UPLOAD_FAILED))
                .when(storageService).put(anyString(), any(), anyLong(), anyString());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.store("a.txt", "text/plain", txt.length,
                        () -> new ByteArrayInputStream(txt), uploaderId));
        assertEquals(ErrorCodeConstants.FILE_UPLOAD_FAILED.code(), ex.getCode());
        verify(fileResourceMapper, never()).insert(any(FileResource.class));
    }

    // ========== page ==========

    @Test
    void page_success_withUploaderName() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        FileResource first = row(rowId, "SRS-登录模块.md");
        FileResource second = row(UUID.fromString("00000000-0000-0000-0000-000000000042"), "接口说明.txt");
        when(fileResourceMapper.selectPage(any(PageParam.class), any(LambdaQueryWrapperX.class)))
                .thenReturn(new PageResult<>(List.of(first, second), 2L));
        SysUser user = new SysUser();
        user.setId(uploaderId);
        user.setName("张三");
        when(userMapper.selectBatchIds(any(Set.class))).thenReturn(List.of(user));

        FilePageReqDTO query = new FilePageReqDTO();
        query.setFileName("SRS");
        PageResult<FileResourceRespDTO> page = service.page(query);

        assertEquals(2L, page.getTotal());
        assertEquals("张三", page.getList().get(0).getUploaderName());
        assertEquals("/api/files/" + rowId + "/download", page.getList().get(0).getDownloadUrl());
        assertEquals("接口说明.txt", page.getList().get(1).getFileName());
    }

    // ========== readBytes（平台下载） ==========

    @Test
    void readBytes_success() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        when(fileResourceMapper.selectById(rowId)).thenReturn(row(rowId, "a.txt"));
        when(storageService.get("objects/" + rowId + ".txt"))
                .thenReturn(new ByteArrayInputStream("minio".getBytes(StandardCharsets.UTF_8)));

        FileContent content = service.readBytes(rowId);

        assertEquals("minio", new String(content.content(), StandardCharsets.UTF_8));
        assertEquals("text/plain", content.contentType());
        assertEquals("a.txt", content.fileName());
    }

    @Test
    void readBytes_invalidContentType_fallbackOctetStream() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        FileResource row = row(rowId, "a.txt");
        row.setContentType("bad content/type");
        when(fileResourceMapper.selectById(rowId)).thenReturn(row);
        when(storageService.get(anyString()))
                .thenReturn(new ByteArrayInputStream(new byte[]{1}));

        assertEquals("application/octet-stream", service.readBytes(rowId).contentType());
    }

    @Test
    void readBytes_notFound() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        when(fileResourceMapper.selectById(rowId)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.readBytes(rowId));
        assertEquals(ErrorCodeConstants.FILE_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void readBytes_storageFailure_mapsDownloadCode() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        when(fileResourceMapper.selectById(rowId)).thenReturn(row(rowId, "a.txt"));
        when(storageService.get(anyString()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.FILE_DOWNLOAD_FAILED));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.readBytes(rowId));
        assertEquals(ErrorCodeConstants.FILE_DOWNLOAD_FAILED.code(), ex.getCode());
    }

    // ========== accessUrl（presigned 换签） ==========

    @Test
    void accessUrl_success() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        when(fileResourceMapper.selectById(rowId)).thenReturn(row(rowId, "a.txt"));
        when(storageService.presign("objects/" + rowId + ".txt")).thenReturn("http://minio/signed");
        when(storageService.getPresignTtlSeconds()).thenReturn(900);

        FileAccessUrlRespDTO dto = service.accessUrl(rowId);

        assertEquals("http://minio/signed", dto.getUrl());
        assertEquals(900, dto.getExpiresIn());
    }

    @Test
    void accessUrl_notFound() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        when(fileResourceMapper.selectById(rowId)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.accessUrl(rowId));
        assertEquals(ErrorCodeConstants.FILE_NOT_FOUND.code(), ex.getCode());
        verify(storageService, never()).presign(anyString());
    }

    // ========== delete ==========

    @Test
    void delete_objectFirstThenRow() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        when(fileResourceMapper.selectById(rowId)).thenReturn(row(rowId, "a.txt"));

        service.delete(rowId);

        // 对象先删、行后删：对象删除失败时行保留（管理页可见、可重试，详设 4.1）
        InOrder inOrder = inOrder(storageService, fileResourceMapper);
        inOrder.verify(storageService).remove("objects/" + rowId + ".txt");
        inOrder.verify(fileResourceMapper).deleteById(rowId);
    }

    @Test
    void delete_storageFailure_keepsRow() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        when(fileResourceMapper.selectById(rowId)).thenReturn(row(rowId, "a.txt"));
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.FILE_DELETE_FAILED))
                .when(storageService).remove(anyString());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.delete(rowId));
        assertEquals(ErrorCodeConstants.FILE_DELETE_FAILED.code(), ex.getCode());
        verify(fileResourceMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void delete_notFound() {
        UUID rowId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        when(fileResourceMapper.selectById(rowId)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.delete(rowId));
        assertEquals(ErrorCodeConstants.FILE_NOT_FOUND.code(), ex.getCode());
        verify(storageService, never()).remove(anyString());
    }
}
