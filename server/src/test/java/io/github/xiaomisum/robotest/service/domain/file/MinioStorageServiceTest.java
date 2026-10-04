package io.github.xiaomisum.robotest.service.domain.file;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinIO 存储封装单测（文件管理详设 3）：bucket 懒确保、SDK 异常到 10 位错误码的映射。
 */
@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock
    private MinioClient client;

    private MinioStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new MinioStorageService();
        ReflectionTestUtils.setField(service, "endpoint", "http://localhost:9000");
        ReflectionTestUtils.setField(service, "publicEndpoint", "");
        ReflectionTestUtils.setField(service, "accessKey", "minioadmin");
        ReflectionTestUtils.setField(service, "secretKey", "minioadmin");
        ReflectionTestUtils.setField(service, "bucket", "robotest");
        ReflectionTestUtils.setField(service, "presignTtlSeconds", 900);
        // 注入测试缝：懒构建的两个客户端均替换为 mock，避免真实网络
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "publicClient", client);
        // lenient：bucketMissing 用例会覆盖该 stub
        lenient().when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
    }

    @Test
    void put_ensuresBucketThenUploads() throws Exception {
        service.put("objects/a.txt", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), 1, "text/plain");

        verify(client).putObject(any(PutObjectArgs.class));
    }

    @Test
    void put_bucketMissing_createsBucketFirst() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        service.put("objects/a.txt", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), 1, "text/plain");

        verify(client).makeBucket(any(MakeBucketArgs.class));
        verify(client).putObject(any(PutObjectArgs.class));
    }

    @Test
    void put_sdkFailure_mapsUploadCode() throws Exception {
        doThrow(new RuntimeException("connection refused")).when(client).putObject(any(PutObjectArgs.class));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.put("objects/a.txt", new ByteArrayInputStream(new byte[]{1}), 1, null));
        assertEquals(ErrorCodeConstants.FILE_UPLOAD_FAILED.code(), ex.getCode());
    }

    @Test
    void get_sdkFailure_mapsDownloadCode() throws Exception {
        doThrow(new RuntimeException("NoSuchKey")).when(client).getObject(any(GetObjectArgs.class));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.get("objects/a.txt"));
        assertEquals(ErrorCodeConstants.FILE_DOWNLOAD_FAILED.code(), ex.getCode());
    }

    @Test
    void remove_sdkFailure_mapsDeleteCode() throws Exception {
        doThrow(new RuntimeException("down")).when(client).removeObject(any(RemoveObjectArgs.class));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.remove("objects/a.txt"));
        assertEquals(ErrorCodeConstants.FILE_DELETE_FAILED.code(), ex.getCode());
    }

    @Test
    void presign_returnsSignedUrl() throws Exception {
        when(client.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/robotest/objects/a.txt?X-Amz-Signature=x");

        assertEquals("http://localhost:9000/robotest/objects/a.txt?X-Amz-Signature=x", service.presign("objects/a.txt"));
        assertEquals(900, service.getPresignTtlSeconds());
    }

    @Test
    void presign_sdkFailure_mapsAccessUrlCode() throws Exception {
        doThrow(new RuntimeException("clock skew")).when(client).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.presign("objects/a.txt"));
        assertEquals(ErrorCodeConstants.FILE_ACCESS_URL_FAILED.code(), ex.getCode());
    }
}
