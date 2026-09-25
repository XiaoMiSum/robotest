package io.github.xiaomisum.robotest.model.dto.request;

import io.github.xiaomisum.robotest.model.dto.request.admin.InitSetupReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.PasswordChangeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserPasswordResetReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.InvitationJoinReqDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SEC-009 守卫：创建、重置、邀请、改密、初始化入口使用统一的 8-64 密码规则，
 * 且 password_hash 只经框架注入的 PasswordEncoder 写入。
 *
 * <p>密码规则口径：仅 8-64 长度、不做字符类型强度校验（01-requirements/system-management/
 * 02-srs-system-management.md 业务规则、04-detailed-design/system-management/
 * 03-system-management-auth.md 1.5）。
 */
class PasswordRuleUniformityTest {

    private record PasswordEntry(String name, String field, Function<String, Object> build) {
    }

    private static final List<PasswordEntry> ENTRIES = List.of(
            new PasswordEntry("创建用户", "password", pw -> {
                UserCreateReqDTO dto = new UserCreateReqDTO();
                dto.setPassword(pw);
                return dto;
            }),
            new PasswordEntry("管理员重置", "newPassword", pw -> {
                UserPasswordResetReqDTO dto = new UserPasswordResetReqDTO();
                dto.setNewPassword(pw);
                return dto;
            }),
            new PasswordEntry("自助改密", "newPassword", pw -> {
                PasswordChangeReqDTO dto = new PasswordChangeReqDTO();
                dto.setOldPassword("old-pass");
                dto.setNewPassword(pw);
                return dto;
            }),
            new PasswordEntry("初始化设置", "password", pw -> {
                InitSetupReqDTO dto = new InitSetupReqDTO();
                dto.setPassword(pw);
                return dto;
            }),
            new PasswordEntry("邀请加入", "password", pw -> {
                InvitationJoinReqDTO dto = new InvitationJoinReqDTO();
                dto.setToken("token");
                dto.setEmail("user@example.com");
                dto.setPassword(pw);
                return dto;
            })
    );

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsEightAndSixtyFourCharsOnEveryEntry() {
        for (PasswordEntry entry : ENTRIES) {
            assertNoFieldViolation(entry, "pass1234");
            assertNoFieldViolation(entry, "a".repeat(64));
        }
    }

    @Test
    void rejectsShortEmptyAndOverlongPasswordsOnEveryEntry() {
        for (PasswordEntry entry : ENTRIES) {
            assertFieldViolation(entry, "ab12345");
            assertFieldViolation(entry, "");
            assertFieldViolation(entry, "a".repeat(65));
        }
    }

    @Test
    void passwordHashIsOnlyWrittenThroughFrameworkInjectedEncoder() throws IOException {
        Path root = Path.of("src/main/java");
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(root)) {
            javaFiles = walk.filter(path -> path.toString().endsWith(".java")).toList();
        }
        assertFalse(javaFiles.isEmpty(), "未扫描到后端源码：" + root);

        for (Path file : javaFiles) {
            String content = Files.readString(file);
            assertFalse(content.contains("new BCryptPasswordEncoder"),
                    "项目代码不得自行实例化 PasswordEncoder，必须使用框架统一配置的 Bean：" + file);
            if (content.contains("setPasswordHash(")) {
                assertTrue(content.contains("passwordEncoder.encode("),
                        "password_hash 写入必须经注入的 PasswordEncoder 加密：" + file);
            }
        }
    }

    private void assertNoFieldViolation(PasswordEntry entry, String password) {
        Set<String> messages = fieldViolationMessages(entry.build().apply(password), entry.field());
        assertTrue(messages.isEmpty(),
                "入口[" + entry.name() + "]应接受密码（长度 " + password.length() + "），实际违规：" + messages);
    }

    private void assertFieldViolation(PasswordEntry entry, String password) {
        Set<String> messages = fieldViolationMessages(entry.build().apply(password), entry.field());
        assertFalse(messages.isEmpty(),
                "入口[" + entry.name() + "]应拒绝密码（长度 " + password.length() + "）");
    }

    private Set<String> fieldViolationMessages(Object dto, String field) {
        return validator.validate(dto).stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(field))
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
    }
}
