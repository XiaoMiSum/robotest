package io.github.xiaomisum.robotest.service.apitest.scan;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JavaParser 实现的测试工程源码扫描（详细设计 6.3：结构化提取注解与属性，替代正则匹配）。
 * <p>
 * 有效用例判定（SRS BR-1.6）：方法同时标注 {@code @Test} 与 {@code @RyzeTest}；
 * 块注释剥离为强制前置步骤。两类失败分列：约定违规计入 parseFailed，
 * 语法解析失败计入 errorFailed（详细设计 2.4）。
 */
@Component
public class JavaParserTestSourceScanner implements TestSourceScanner {

    private static final String TEST_SIMPLE_NAME = "Test";
    private static final String RYZE_TEST_SIMPLE_NAME = "RyzeTest";
    private static final String DESCRIPTION_MEMBER = "description";

    @Override
    public ScanOutcome scan(List<SourceFile> files) {
        List<ScannedClass> classes = new ArrayList<>();
        List<ScanIssue> parseFailed = new ArrayList<>();
        List<ScanIssue> errorFailed = new ArrayList<>();
        for (SourceFile file : files) {
            scanFile(file, classes, parseFailed, errorFailed);
        }
        return new ScanOutcome(List.copyOf(classes), List.copyOf(parseFailed), List.copyOf(errorFailed));
    }

    private void scanFile(SourceFile file, List<ScannedClass> classes,
                          List<ScanIssue> parseFailed, List<ScanIssue> errorFailed) {
        if (file.content() == null || file.content().isBlank()) {
            errorFailed.add(new ScanIssue(file.path(), null, "文件内容为空或不可读"));
            return;
        }
        // 强制前置：先剥离块注释再解析，位于块注释内的方法一律不计（BR-1.6）
        String source = BlockCommentStripper.strip(file.content());
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        CompilationUnit unit;
        try {
            var result = new JavaParser(config).parse(source);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                errorFailed.add(new ScanIssue(file.path(), null,
                        "源码语法解析失败：" + summarizeProblems(result.getProblems())));
                return;
            }
            unit = result.getResult().get();
        } catch (Exception e) {
            errorFailed.add(new ScanIssue(file.path(), null,
                    "源码解析异常：" + e.getClass().getSimpleName()));
            return;
        }

        String packageName = unit.getPackageDeclaration()
                .map(pd -> pd.getNameAsString()).orElse("");
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            // 只处理普通类（含嵌套类），接口 / 枚举 / 注解不产用例
            if (type.isInterface() || type.isEnumDeclaration() || type.isAnnotationDeclaration()) {
                continue;
            }
            List<ScannedMethod> methods = scanMethods(file, type, packageName, parseFailed);
            if (!methods.isEmpty()) {
                classes.add(new ScannedClass(file.path(), packageName, type.getNameAsString(), List.copyOf(methods)));
            }
        }
    }

    private List<ScannedMethod> scanMethods(SourceFile file, ClassOrInterfaceDeclaration type,
                                            String packageName, List<ScanIssue> parseFailed) {
        List<ScannedMethod> methods = new ArrayList<>();
        String className = type.getNameAsString();
        String fqn = packageName.isBlank() ? className : packageName + "." + className;
        for (MethodDeclaration method : type.getMethods()) {
            Optional<AnnotationExpr> testAnno = findAnnotation(method, TEST_SIMPLE_NAME);
            Optional<AnnotationExpr> ryzeAnno = findAnnotation(method, RYZE_TEST_SIMPLE_NAME);
            if (testAnno.isEmpty() || ryzeAnno.isEmpty()) {
                continue;
            }
            String methodKey = fqn + "#" + method.getNameAsString();
            String yamlPath = extractStringValue(ryzeAnno.get()).orElse("").trim();
            if (yamlPath.isEmpty()) {
                parseFailed.add(new ScanIssue(file.path(), methodKey, "@RyzeTest 未指定步骤模板路径"));
                continue;
            }
            String description = extractMemberValue(testAnno.get(), DESCRIPTION_MEMBER).orElse("").trim();
            if (description.isEmpty()) {
                parseFailed.add(new ScanIssue(file.path(), methodKey, "@Test 缺少 description 属性"));
                continue;
            }
            methods.add(new ScannedMethod(method.getNameAsString(), description, yamlPath));
        }
        return methods;
    }

    /** 按简单名或全限定名匹配注解，兼容未导入注解的全限定写法 */
    private Optional<AnnotationExpr> findAnnotation(MethodDeclaration method, String simpleName) {
        return method.getAnnotations().stream()
                .filter(a -> {
                    String name = a.getNameAsString();
                    return name.equals(simpleName) || name.endsWith("." + simpleName);
                })
                .findFirst();
    }

    /** 提取注解的单成员值或 value 成员值（@RyzeTest("x") 与 @RyzeTest(value = "x") 两种写法） */
    private Optional<String> extractStringValue(AnnotationExpr anno) {
        if (anno instanceof SingleMemberAnnotationExpr single) {
            return literalOf(single.getMemberValue());
        }
        return extractMemberValue(anno, "value");
    }

    /** 提取具名成员的字符串字面量值；成员缺失或非字面量时返回空 */
    private Optional<String> extractMemberValue(AnnotationExpr anno, String memberName) {
        if (!(anno instanceof NormalAnnotationExpr normal)) {
            return Optional.empty();
        }
        return normal.getPairs().stream()
                .filter(p -> p.getNameAsString().equals(memberName))
                .findFirst()
                .map(MemberValuePair::getValue)
                .flatMap(this::literalOf);
    }

    private Optional<String> literalOf(Expression expr) {
        return expr instanceof StringLiteralExpr literal ? Optional.of(literal.getValue()) : Optional.empty();
    }

    private String summarizeProblems(List<Problem> problems) {
        if (problems.isEmpty()) {
            return "未知原因";
        }
        String first = problems.get(0).getMessage();
        return problems.size() > 1 ? first + "（共 " + problems.size() + " 处）" : first;
    }
}
