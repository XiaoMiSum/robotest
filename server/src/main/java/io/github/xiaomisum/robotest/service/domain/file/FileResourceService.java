package io.github.xiaomisum.robotest.service.domain.file;

import io.github.xiaomisum.robotest.model.dto.request.file.FilePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.file.FileAccessUrlRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.file.FileResourceRespDTO;
import io.github.xiaomisum.robotest.model.entity.file.FileResource;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.pojo.PageResult;

import java.io.InputStream;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 文件资源服务（文件管理详设 4）：模块级校验、对象存储落盘与访问三通道（下载 / presigned / 管理删除）。
 */
public interface FileResourceService {

    /**
     * 上传入口（Web，multipart）：空文件与 20MB 上限校验后转 store，返回资源信息（含稳定 downloadUrl）。
     */
    FileResourceRespDTO upload(MultipartFile file, UUID uploaderId);

    /**
     * 通用入库（Web 上传与存量回填共用）：文件头单遍先嗅探、后随对象写入，避免流二次读取。
     *
     * @param fileName      原始文件名（仅存库）
     * @param contentType   内容类型（可空）
     * @param size          声明的字节数（用于上限校验与对象长度）
     * @param streamSupplier 内容流供应（只消费一次）
     * @param uploaderId    上传者
     */
    FileResource store(String fileName, String contentType, long size, Supplier<InputStream> streamSupplier, UUID uploaderId);

    /**
     * 分页列表（file:view 由 Controller 把关，C2）。
     */
    PageResult<FileResourceRespDTO> page(FilePageReqDTO query);

    /**
     * 读取文件字节（平台下载通道）；未存在抛 1000018021，存储失败抛 1000018026。
     */
    FileContent readBytes(UUID id);

    /**
     * 换取 presigned 临时地址（详设 3.3）；未存在抛 1000018021，签发失败抛 1000018027。
     */
    FileAccessUrlRespDTO accessUrl(UUID id);

    /**
     * 删除（管理页，file:delete 由 Controller 把关）：先删对象后逻辑删行，失败可重试。
     */
    void delete(UUID id);
}
