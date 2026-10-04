package io.github.xiaomisum.robotest.service.domain.file;

/**
 * 文件内容载体（下载链路内部传递）：字节、内容类型与原始文件名。
 *
 * @param content     文件字节（模块级 ≤20MB，读入内存后一次性返回，避免流泄漏）
 * @param contentType 归一化后的内容类型（非法或空值回退 application/octet-stream）
 * @param fileName    原始文件名（用于 Content-Disposition）
 */
public record FileContent(byte[] content, String contentType, String fileName) {
}
