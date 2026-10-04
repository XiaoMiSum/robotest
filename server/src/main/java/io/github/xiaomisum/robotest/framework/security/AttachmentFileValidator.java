package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.exception.ErrorCode;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 附件类型与内容校验（安全规范 6.3）：扩展名白名单 + 文件头嗅探双重校验，
 * 不仅依赖扩展名，阻断 HTML/SVG/可执行体等伪造后缀上传。
 */
public final class AttachmentFileValidator {

    /** 白名单：截图、办公文档、文本与压缩包；显式排除 html/htm/svg/js 与可执行体后缀 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "md", "json", "log", "xml",
            "zip", "gz", "7z", "rar");

    private AttachmentFileValidator() {
    }

    /**
     * 校验扩展名白名单与文件头内容是否一致（缺陷附件口径，错误码 BUG_ATTACHMENT_*）
     *
     * @param extension 原始扩展名（可带前导点，大小写不敏感）
     * @param head      文件头字节（读取前若干字节即可）
     */
    public static void validate(String extension, byte[] head) {
        validate(extension, head,
                ErrorCodeConstants.BUG_ATTACHMENT_TYPE_NOT_ALLOWED,
                ErrorCodeConstants.BUG_ATTACHMENT_CONTENT_MISMATCH);
    }

    /**
     * 校验扩展名白名单与文件头内容是否一致（调用域自报错误码，文件管理详设 4.3 校验分工）
     *
     * @param extension   原始扩展名（可带前导点，大小写不敏感）
     * @param head        文件头字节
     * @param typeCode    不在白名单时抛出的错误码
     * @param contentCode 文件头与类型不一致时抛出的错误码
     */
    public static void validate(String extension, byte[] head, ErrorCode typeCode, ErrorCode contentCode) {
        String ext = normalize(extension);
        if (ext.isEmpty() || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw ServiceExceptionUtil.get(typeCode);
        }
        if (!matchesMagic(ext, head)) {
            throw ServiceExceptionUtil.get(contentCode);
        }
    }

    /**
     * 读取文件头字节（内容嗅探用，512 字节足够覆盖全部 magic 序列）
     *
     * @param file      待读取文件
     * @param errorCode 读取失败时抛出的错误码（由调用域决定语义）
     */
    public static byte[] readHead(MultipartFile file, ErrorCode errorCode) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(512);
        } catch (IOException e) {
            throw ServiceExceptionUtil.get(errorCode);
        }
    }

    /** 是否在白名单内（同包与测试可见的查询入口） */
    public static boolean isAllowedExtension(String extension) {
        String ext = normalize(extension);
        return !ext.isEmpty() && ALLOWED_EXTENSIONS.contains(ext);
    }

    private static String normalize(String extension) {
        if (extension == null) {
            return "";
        }
        String ext = extension.trim().toLowerCase(Locale.ROOT);
        return ext.startsWith(".") ? ext.substring(1) : ext;
    }

    private static boolean matchesMagic(String ext, byte[] head) {
        if (head == null || head.length == 0) {
            return false;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> startsWith(head, 'G', 'I', 'F', '8');
            case "webp" -> startsWith(head, 'R', 'I', 'F', 'F') && regionEquals(head, 8, 'W', 'E', 'B', 'P');
            case "bmp" -> startsWith(head, 'B', 'M');
            case "pdf" -> startsWith(head, '%', 'P', 'D', 'F');
            case "zip", "docx", "xlsx", "pptx" -> startsWith(head, 'P', 'K');
            case "gz" -> startsWith(head, 0x1F, 0x8B);
            case "7z" -> startsWith(head, '7', 'z', 0xBC, 0xAF, 0x27, 0x1C);
            case "rar" -> startsWith(head, 'R', 'a', 'r', '!');
            case "doc", "xls", "ppt" -> startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            // 文本类：含 NUL 字节即视为二进制伪装
            default -> isPlainText(head);
        };
    }

    private static boolean isPlainText(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean regionEquals(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
