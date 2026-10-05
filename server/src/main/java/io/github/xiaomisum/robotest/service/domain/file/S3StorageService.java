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
 * S3 对象存储封装（文件管理详设 3）：上传 / 下载 / 删除 / presigned 签名统一经 MinIO Java SDK 的标准 S3 操作，
 * 不自实现对象存储 HTTP 调用（引擎为 SeaweedFS，可切换任意 S3 兼容服务）；bucket 懒确保（首次操作时），应用不因对象存储未就绪而启动失败。
 */
@Component
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    @Value("${robotest.s3.endpoint:http://localhost:9000}")
    private String endpoint;

    /** presigned 签名地址（浏览器可达）；留空同 endpoint——compose 中内部网地址浏览器无法直连 */
    @Value("${robotest.s3.public-endpoint:}")
    private String publicEndpoint;

    @Value("${robotest.s3.access-key:robotest}")
    private String accessKey;

    @Value("${robotest.s3.secret-key:robotest-dev-secret}")
    private String secretKey;

    @Value("${robotest.s3.bucket:robotest}")
    private String bucket;

    @Value("${robotest.s3.presign-ttl-seconds:900}")
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
            log.warn("对象存储上传失败 key={}", objectKey, e);
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
            log.warn("对象存储读取失败 key={}", objectKey, e);
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
            log.warn("对象存储删除失败 key={}", objectKey, e);
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
            log.warn("对象存储签发访问地址失败 key={}", objectKey, e);
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
                    log.info("对象存储 bucket 不存在，已创建 bucket={}", bucket);
                }
                bucketEnsured = true;
            } catch (Exception e) {
                throw new IllegalStateException("对象存储 bucket 初始化失败 bucket=" + bucket, e);
            }
        }
    }
}
