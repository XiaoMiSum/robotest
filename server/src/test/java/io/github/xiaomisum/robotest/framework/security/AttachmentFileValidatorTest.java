package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 附件类型与内容双重校验（安全规范 6.3）：扩展名白名单 + 文件头嗅探 */
class AttachmentFileValidatorTest {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Test
    void acceptsWhitelistedExtensionWithMatchingMagic() {
        assertThatCode(() -> AttachmentFileValidator.validate(".png", PNG_MAGIC)).doesNotThrowAnyException();
        assertThatCode(() -> AttachmentFileValidator.validate("PNG", PNG_MAGIC)).doesNotThrowAnyException();
        assertThatCode(() -> AttachmentFileValidator.validate("txt", "hello".getBytes(StandardCharsets.UTF_8)))
                .doesNotThrowAnyException();
        assertThatCode(() -> AttachmentFileValidator.validate(".pdf", "%PDF-1.7 content".getBytes(StandardCharsets.UTF_8)))
                .doesNotThrowAnyException();
        assertThatCode(() -> AttachmentFileValidator.validate("zip", new byte[]{'P', 'K', 3, 4}))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDisallowedExtensionIncludingScriptAndMarkup() {
        for (String ext : new String[]{"html", "htm", "svg", "js", "exe", "sh", "jsp", "", null}) {
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> AttachmentFileValidator.validate(ext, PNG_MAGIC));
            assertTrue(exception.getMessage().contains("不支持的附件类型"),
                    "应拒绝扩展名: " + ext);
        }
    }

    @Test
    void rejectsContentMismatchingExtension() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> AttachmentFileValidator.validate("png", "pretend text".getBytes(StandardCharsets.UTF_8)));
        assertTrue(exception.getMessage().contains("附件内容与文件类型不符"));

        assertThrows(ServiceException.class,
                () -> AttachmentFileValidator.validate("txt", new byte[]{0x7F, 'E', 'L', 'F', 0, 0, 0}));
    }

    @Test
    void rejectsEmptyHead() {
        assertThrows(ServiceException.class, () -> AttachmentFileValidator.validate("png", new byte[0]));
        assertThrows(ServiceException.class, () -> AttachmentFileValidator.validate("png", null));
    }

    @Test
    void isAllowedExtensionReflectsWhitelist() {
        assertTrue(AttachmentFileValidator.isAllowedExtension(".JPG"));
        assertFalse(AttachmentFileValidator.isAllowedExtension("svg"));
        assertFalse(AttachmentFileValidator.isAllowedExtension(null));
    }

    @Test
    void validateWithCallerErrorCodes_throwsProvidedCodes() {
        // 调用域自报错误码（文件管理详设 4.3 校验分工）：白名单与内容不符均抛调用方给定码
        ServiceException typeEx = assertThrows(ServiceException.class,
                () -> AttachmentFileValidator.validate("html", PNG_MAGIC,
                        ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED, ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED));
        assertEquals(ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED.code(), typeEx.getCode());

        ServiceException contentEx = assertThrows(ServiceException.class,
                () -> AttachmentFileValidator.validate("png", "pretend text".getBytes(StandardCharsets.UTF_8),
                        ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED, ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED));
        assertEquals(ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED.code(), contentEx.getCode());

        // 缺陷附件默认口径保持不变（BUG_ATTACHMENT_* 契约）
        ServiceException bugEx = assertThrows(ServiceException.class,
                () -> AttachmentFileValidator.validate("html", PNG_MAGIC));
        assertEquals(ErrorCodeConstants.BUG_ATTACHMENT_TYPE_NOT_ALLOWED.code(), bugEx.getCode());
    }
}
