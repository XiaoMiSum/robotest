package io.github.xiaomisum.robotest.framework.security;

import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
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
}
