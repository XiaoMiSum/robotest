package io.github.xiaomisum.robotest.service.domain.file;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.io.InputStream;

/**
 * MinIO 对象存储封装（文件管理详设 3）：上传 / 下载 / 删除 / presigned 签名统一经官方 SDK，
 * 不自实现对象存储 HTTP 调用；bucket 懒确保（首次操作时），应用不因 MinIO 未就绪而启动失败。
 */
@Component
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    @Value("${robotest.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    /** presigned 签名地址（浏览器可达）；留空同 endpoint——compose 中内部网地址浏览器无法直连 */
    @Value("${robotest.minio.public-endpoint:}")
    private String publicEndpoint;

    @Value("${robotest.minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${robotest.minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${robotest.minio.bucket:robotest}")
    private String bucket;

    @Value("${robotest.minio.presign-ttl-seconds:900}")
    private int presignTtlSeconds;

    private volatile MinioClient client;
    private volatile MinioClient publicClient;
    private volatile boolean bucketEnsured;

    public void put(String objectKey, InputStream in, long size, String contentType) {
        try {
            ensureBucket();
            client().putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, size, -1)
                    .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                    .build());
        } catch (Exception e) {
            log.warn("MinIO 上传对象失败 key={}", objectKey, e);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.FILE_UPLOAD_FAILED);
        }
    }

    public InputStream get(String objectKey) {
        try {
            ensureBucket();
            return client().getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO 读取对象失败 key={}", objectKey, e);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.FILE_DOWNLOAD_FAILED);
        }
    }

    public void remove(String objectKey) {
        try {
            ensureBucket();
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO 删除对象失败 key={}", objectKey, e);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.FILE_DELETE_FAILED);
        }
    }

    /** 以浏览器可达地址签发 presigned URL（详设 3.3），时效取 presign-ttl-seconds */
    public String presign(String objectKey) {
        try {
            ensureBucket();
            return publicClient().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(presignTtlSeconds)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO 签发访问地址失败 key={}", objectKey, e);
            throw ServiceExceptionUtil.get(ErrorCodeConstants.FILE_ACCESS_URL_FAILED);
        }
    }

    /** presigned URL 时效（秒），供换签响应回传 expiresIn */
    public int getPresignTtlSeconds() {
        return presignTtlSeconds;
    }

    private MinioClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = buildClient(endpoint);
                }
            }
        }
        return client;
    }

    private MinioClient publicClient() {
        if (publicClient == null) {
            synchronized (this) {
                if (publicClient == null) {
                    publicClient = buildClient(StringUtils.hasText(publicEndpoint) ? publicEndpoint : endpoint);
                }
            }
        }
        return publicClient;
    }

    private MinioClient buildClient(String url) {
        return MinioClient.builder().endpoint(url).credentials(accessKey, secretKey).build();
    }

    private void ensureBucket() {
        if (bucketEnsured) {
            return;
        }
        synchronized (this) {
            if (bucketEnsured) {
                return;
            }
            try {
                boolean exists = client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO bucket 不存在，已创建 bucket={}", bucket);
                }
                bucketEnsured = true;
            } catch (Exception e) {
                throw new IllegalStateException("MinIO bucket 初始化失败 bucket=" + bucket, e);
            }
        }
    }
}
