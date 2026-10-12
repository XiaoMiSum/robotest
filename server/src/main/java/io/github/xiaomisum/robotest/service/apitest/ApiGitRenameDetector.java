package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.service.apitest.scan.ScanOutcome;
import io.github.xiaomisum.robotest.service.apitest.scan.ScannedClass;
import io.github.xiaomisum.robotest.service.apitest.scan.ScannedMethod;
import io.github.xiaomisum.robotest.service.apitest.scan.TestSourceScanner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件级改名探查与内容校验（GitLab 集成详细设计 3.5 类改名与包改名迁移，BR-1.38 / BR-1.39）。
 * <p>
 * 两阶段纯探查组件：{@link #comparePairs} 在拉取源码树前取候选改名对，
 * {@link #validatePairs} 在扫描完成后回读旧版源码做重合度校验。
 * {@code renamed_file} 只是相似度提示，校验不达标、类名与文件名不一致、新侧非测试类
 * 一律静默退回删旧建新（BR-1.42）——误迁移把 A 类数据挂到 B 类比留陈旧条目严重；
 * 旧版文件读取 / 扫描失败属真实错误，经 {@link RenameResult#errors()} 上抛给导入侧计 errorFailed。
 * 本组件不落库、不改写任何状态，迁移应用由导入服务在单事务内完成。
 */
@Component
public class ApiGitRenameDetector {

    /** 内容校验门槛：重合度 = |交集| / |并集| ≥ 0.5（详设 3.5 规则 2） */
    private static final double OVERLAP_THRESHOLD = 0.5d;

    private final GitLabApiClient gitLabApiClient;
    private final TestSourceScanner scanner;

    public ApiGitRenameDetector(GitLabApiClient gitLabApiClient, TestSourceScanner scanner) {
        this.gitLabApiClient = gitLabApiClient;
        this.scanner = scanner;
    }

    /** compare 过滤后的候选改名对：两侧均为 testSourceDir 下的 .java 文件且路径不同 */
    public record RenamePair(String oldPath, String newPath) {
    }

    /**
     * 校验结论。
     *
     * @param classRenames 类改名映射（旧全限定名 → 新全限定名），已通过内容校验
     * @param errors       真实错误明细（旧版文件读取 / 扫描失败），供导入侧计 errorFailed
     */
    public record RenameResult(Map<String, String> classRenames, List<String> errors) {

        public static RenameResult empty() {
            return new RenameResult(Map.of(), List.of());
        }
    }

    /**
     * 取两次导入提交之间的候选改名对（compare 在此调用，失败原样上抛由导入侧降级，详设 3.5 规则 6）。
     *
     * @param testSourceDir 测试源码目录（空表示仓库根）
     */
    public List<RenamePair> comparePairs(String baseUrl, String token, String repoPath,
            String fromCommit, String toCommit, String testSourceDir) {
        String dir = trimSlashes(testSourceDir);
        List<RenamePair> pairs = new ArrayList<>();
        for (GitLabApiClient.DiffEntry diff : gitLabApiClient
                .compare(baseUrl, token, repoPath, fromCommit, toCommit)) {
            if (!diff.renamedFile() || diff.oldPath() == null || diff.newPath() == null
                    || diff.oldPath().equals(diff.newPath())) {
                continue;
            }
            if (isTestJava(diff.oldPath(), dir) && isTestJava(diff.newPath(), dir)) {
                pairs.add(new RenamePair(diff.oldPath(), diff.newPath()));
            }
        }
        return pairs;
    }

    /**
     * 对候选对逐个回读旧版源码并做内容校验（详设 3.5 规则 2）：方法集合或步骤模板引用集合
     * 重合度达标即采信，产出类改名映射；不可探查情形静默跳过，真实错误进 errors。
     *
     * @param fromCommit   旧版提交号（compare 的 from）
     * @param scannedClasses 本次扫描识别出的测试类（新侧，按仓库相对路径匹配候选对）
     */
    public RenameResult validatePairs(String baseUrl, String token, String repoPath, String fromCommit,
            List<RenamePair> pairs, List<ScannedClass> scannedClasses) {
        if (pairs == null || pairs.isEmpty()) {
            return RenameResult.empty();
        }
        Map<String, ScannedClass> byPath = new HashMap<>();
        for (ScannedClass cls : scannedClasses) {
            byPath.putIfAbsent(cls.filePath(), cls);
        }
        Map<String, String> classRenames = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (RenamePair pair : pairs) {
            ScannedClass newClass = byPath.get(pair.newPath());
            // 新侧不是可探查的测试类（含类名与文件名不一致）→ 退回删旧建新
            if (newClass == null || !newClass.className().equals(baseName(pair.newPath()))) {
                continue;
            }
            ScannedClass oldClass = scanOldClass(baseUrl, token, repoPath, fromCommit, pair, errors);
            // 旧侧不可解析、非测试类或类名与文件名不一致（非公开类等，BR-1.42）→ 退回删旧建新
            if (oldClass == null || !oldClass.className().equals(baseName(pair.oldPath()))) {
                continue;
            }
            String oldFqn = oldClass.fullyQualifiedName();
            String newFqn = newClass.fullyQualifiedName();
            if (oldFqn.equals(newFqn) || classRenames.containsKey(oldFqn)) {
                continue;
            }
            if (!accepted(oldClass, newClass)) {
                continue;
            }
            classRenames.put(oldFqn, newFqn);
        }
        return new RenameResult(Map.copyOf(classRenames), List.copyOf(errors));
    }

    /** 回读旧版文件并取与文件名同名的测试类；读取 / 扫描失败记入 errors，非测试类返回 null */
    private ScannedClass scanOldClass(String baseUrl, String token, String repoPath, String fromCommit,
            RenamePair pair, List<String> errors) {
        String content;
        try {
            content = gitLabApiClient.findFileContent(baseUrl, token, repoPath, fromCommit, pair.oldPath());
        } catch (RuntimeException e) {
            errors.add("旧版本文件读取失败：" + pair.oldPath() + "（" + messageOf(e) + "）");
            return null;
        }
        if (content == null) {
            errors.add("旧版本文件不存在：" + pair.oldPath());
            return null;
        }
        ScanOutcome outcome;
        try {
            outcome = scanner.scan(List.of(new TestSourceScanner.SourceFile(pair.oldPath(), content)));
        } catch (RuntimeException e) {
            errors.add("旧版本文件扫描失败：" + pair.oldPath() + "（" + messageOf(e) + "）");
            return null;
        }
        String baseName = baseName(pair.oldPath());
        return outcome.classes().stream()
                .filter(cls -> baseName.equals(cls.className()))
                .findFirst()
                .orElse(null);
    }

    /** 方法集合或步骤模板引用集合任一达标即采信；空集合不参与判定（详设 3.5 规则 2） */
    private static boolean accepted(ScannedClass oldClass, ScannedClass newClass) {
        return overlaps(methodNames(oldClass), methodNames(newClass))
                || overlaps(templateRefs(oldClass), templateRefs(newClass));
    }

    private static boolean overlaps(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        long common = left.stream().filter(right::contains).count();
        return (double) common / union.size() >= OVERLAP_THRESHOLD;
    }

    private static Set<String> methodNames(ScannedClass cls) {
        Set<String> names = new HashSet<>();
        for (ScannedMethod method : cls.methods()) {
            if (method.methodName() != null && !method.methodName().isBlank()) {
                names.add(method.methodName());
            }
        }
        return names;
    }

    private static Set<String> templateRefs(ScannedClass cls) {
        Set<String> refs = new HashSet<>();
        for (ScannedMethod method : cls.methods()) {
            if (method.yamlPath() != null && !method.yamlPath().isBlank()) {
                refs.add(method.yamlPath());
            }
        }
        return refs;
    }

    private static boolean isTestJava(String path, String dir) {
        if (!path.toLowerCase().endsWith(".java")) {
            return false;
        }
        return dir.isEmpty() || path.equals(dir) || path.startsWith(dir + "/");
    }

    /** 取路径最后一段并去扩展名（Java 规范要求 public 类名与文件名一致） */
    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String trimSlashes(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
