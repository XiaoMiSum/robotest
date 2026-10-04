package io.github.xiaomisum.robotest.service.domain.file;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.AttachmentFileValidator;
import io.github.xiaomisum.robotest.model.dto.request.file.FilePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.file.FileAccessUrlRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.file.FileResourceRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.file.FileResource;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.file.FileResourceMapper;
import jakarta.annotation.Resource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 文件资源服务实现（文件管理详设 4）：对象键服务端生成、先校验后落盘、对象先删后行删。
 */
@Service
public class FileResourceServiceImpl implements FileResourceService {

    /** 模块级上限 20MB（SRS 导入详设 3.8 / 文件管理详设 5.2）；缺陷附件 10MB 由缺陷侧业务层继续强制 */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /** 文件头嗅探读取长度，覆盖全部 magic 序列 */
    private static final int HEAD_BYTES = 512;

    @Resource
    private FileResourceMapper fileResourceMapper;
    @Resource
    private MinioStorageService storageService;
    @Resource
    private SysUserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileResourceRespDTO upload(MultipartFile file, UUID uploaderId) {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.FILE_EMPTY);
        }
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "unnamed";
        FileResource row = store(fileName, file.getContentType(), file.getSize(),
                () -> {
                    try {
                        return file.getInputStream();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                uploaderId);
        return toRespDTO(row, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileResource store(String fileName, String contentType, long size,
                              Supplier<InputStream> streamSupplier, UUID uploaderId) {
        if (size <= 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.FILE_EMPTY);
        }
        if (size > MAX_FILE_SIZE) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.FILE_SIZE_EXCEEDED);
        }
        String ext = extractExtension(fileName);
        // 对象键服务端生成，原始文件名永不进入键（防路径注入，详设 3.2）
        String objectKey = "objects/" + UUID.randomUUID() + ext;
        try (InputStream raw = streamSupplier.get()) {
            byte[] head = raw.readNBytes(HEAD_BYTES);
            // 安全规范 6.3：白名单 + 文件头嗅探；错误码取文件域（详设 6）
            AttachmentFileValidator.validate(ext, head,
                    ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED, ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED);
            storageService.put(objectKey,
                    new SequenceInputStream(new ByteArrayInputStream(head), raw), size, contentType);
        } catch (IOException | UncheckedIOException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.FILE_UPLOAD_FAILED);
        }

        FileResource row = new FileResource();
        row.setObjectKey(objectKey);
        row.setFileName(fileName);
        row.setContentType(contentType);
        row.setFileSize(size);
        row.setUploaderId(uploaderId);
        fileResourceMapper.insert(row);
        return row;
    }

    @Override
    public PageResult<FileResourceRespDTO> page(FilePageReqDTO query) {
        LambdaQueryWrapperX<FileResource> wrapper = new LambdaQueryWrapperX<FileResource>()
                .likeIfPresent(FileResource::getFileName, query.getFileName())
                .orderByDesc(FileResource::getCreatedAt);
        PageResult<FileResource> page = fileResourceMapper.selectPage(query, wrapper);

        List<FileResource> rows = page.getList();
        Set<UUID> uploaderIds = rows.stream()
                .map(FileResource::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = uploaderIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(uploaderIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getName, (a, b) -> a));
        List<FileResourceRespDTO> list = rows.stream()
                .map(row -> toRespDTO(row, names.get(row.getUploaderId())))
                .toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public FileContent readBytes(UUID id) {
        FileResource row = require(id);
        try (InputStream in = storageService.get(row.getObjectKey())) {
            return new FileContent(in.readAllBytes(),
                    normalizeContentType(row.getContentType()), row.getFileName());
        } catch (IOException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    public FileAccessUrlRespDTO accessUrl(UUID id) {
        FileResource row = require(id);
        FileAccessUrlRespDTO dto = new FileAccessUrlRespDTO();
        dto.setUrl(storageService.presign(row.getObjectKey()));
        dto.setExpiresIn(storageService.getPresignTtlSeconds());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        FileResource row = require(id);
        // 对象先删：对象删除失败则行保留（管理页可见、可重试）；行失败仅残留孤儿对象
        storageService.remove(row.getObjectKey());
        fileResourceMapper.deleteById(id);
    }

    private FileResource require(UUID id) {
        FileResource row = fileResourceMapper.selectById(id);
        if (row == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.FILE_NOT_FOUND);
        }
        return row;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        // 后缀限制长度，防止异常文件名生成超长对象键
        if (dotIndex < 0 || fileName.length() - dotIndex > 20) {
            return "";
        }
        return fileName.substring(dotIndex);
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        try {
            MediaType.parseMediaType(contentType);
            return contentType;
        } catch (InvalidMediaTypeException e) {
            return "application/octet-stream";
        }
    }

    private FileResourceRespDTO toRespDTO(FileResource row, String uploaderName) {
        FileResourceRespDTO dto = new FileResourceRespDTO();
        dto.setId(row.getId());
        dto.setFileName(row.getFileName());
        dto.setFileSize(row.getFileSize());
        dto.setContentType(row.getContentType());
        dto.setUploaderId(row.getUploaderId());
        dto.setUploaderName(uploaderName);
        dto.setDownloadUrl("/api/files/" + row.getId() + "/download");
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }
}
