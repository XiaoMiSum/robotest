package io.github.xiaomisum.robotest.service.domain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * domain 包边界断言（03 §3.1.1），纯源码结构检查、零依赖：
 * 直接读取 service/domain 的 .java 源码文本做规则断言，
 * 避免引入 ArchUnit 依赖（AGENTS 边界）。
 */
class DomainArchitectureTest {

    private static final Path DOMAIN_SOURCE =
            Path.of("src/main/java/io/github/xiaomisum/robotest/service/domain");

    @Test
    void reviewStatusValueOnlyFromWorkflowOrEnum() throws IOException {
        List<String> violations = readSourceFiles("review").stream()
                // DTO 字段读取不属于状态判定，跳过（如 dto.setStatus(review.getStatus()) 传输）
                .filter(line -> line.contains(".setStatus(") && !line.contains("dto.setStatus"))
                // 载体落库与实体跟踪 belong 到 ServiceImpl，但值只能来自 transition 返回值或枚举
                .filter(line -> !line.contains("ReviewStatus.") && !line.contains(".getCode()"))
                .toList();
        assertTrue(violations.isEmpty(),
                "review 域 setStatus 值只能来自 ReviewWorkflow.transition 返回值或 ReviewStatus 枚举:\n" + violations);
    }

    @Test
    void domainPackagesDoNotImportAiOrApitestImplementationClasses() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path p : listJavaFiles(DOMAIN_SOURCE)) {
            String rel = DOMAIN_SOURCE.relativize(p).toString();
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            for (String line : lines) {
                // 实现类（*Impl）禁止 import；服务窗口接口（AiEmbeddingWriteService/AiConfigService）与端口（ModuleReferencedGuard）允许
                boolean implImport = line.matches("import io\\.github\\.xiaomisum\\.robotest\\.service\\.(ai|apitest)\\.[^;]+Impl;");
                if (implImport) {
                    violations.add(rel + " → " + line.trim());
                }
            }
        }
        assertTrue(violations.isEmpty(), "domain/* 不得 import service.ai/service.apitest 实现类:\n" + violations);
    }

    @Test
    void reviewDomainUsesServiceExceptionUtil() throws IOException {
        List<String> violations = readSourceFiles("review").stream()
                // 业务异常统一经 ServiceExceptionUtil；禁止裸 RuntimeException 抛出
                .filter(line -> line.matches(".*throw new [A-Z][A-Za-z]*\\(.*"))
                .filter(line -> !line.contains("ServiceExceptionUtil"))
                .toList();
        assertTrue(violations.isEmpty(),
                "review 域非法跃迁等业务异常必须经 ServiceExceptionUtil（C3）:\n" + violations);
    }

    @Test
    void reviewDomainIsolated() throws IOException {
        List<String> imports = readSourceFiles("review").stream()
                .filter(line -> line.startsWith("import io.github.xiaomisum.robotest.service."))
                .map(line -> line.replace("import ", "").replace(";", "").trim())
                .toList();
        assertEquals(List.of(), imports.stream()
                        .filter(i -> i.startsWith("io.github.xiaomisum.robotest.service.ai.")
                                || i.startsWith("io.github.xiaomisum.robotest.service.apitest."))
                        .toList(),
                "review 域不得依赖 service.ai/service.apitest（事件/端口放共享层）");
    }

    private List<Path> listJavaFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private List<String> readSourceFiles(String subDir) throws IOException {
        List<String> merged = new ArrayList<>();
        for (Path p : listJavaFiles(DOMAIN_SOURCE.resolve(subDir))) {
            merged.addAll(Files.readAllLines(p, StandardCharsets.UTF_8));
        }
        return merged;
    }
}