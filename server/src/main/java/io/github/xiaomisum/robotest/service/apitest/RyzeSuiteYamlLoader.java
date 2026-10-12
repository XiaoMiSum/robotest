package io.github.xiaomisum.robotest.service.apitest;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 远程 Ryze 套件 YAML 加载器（GitLab 集成详细设计 6.3，WP-3.2）。
 * <p>
 * 步骤模板经 GitLab API 拉取，{@code !include} 标签按资源根目录相对路径远程取回并内联
 * （样例工程实测引用均为资源根相对路径）；单次加载会话缓存文件文本避免重复拉取，
 * 祖先引用栈检测防环；引用缺失、YAML 非法、成环一律抛 {@link LoadException}，
 * 由调用方计入解析失败明细（BR-1.9），不中止整次导入。
 * <p>
 * 纯内存组件：仓库文件读取经 {@link FileFetcher} 接缝注入，单测以假实现替代网络；
 * 非线程安全，一次导入构造一个实例。
 */
public class RyzeSuiteYamlLoader {

    /** 仓库文件读取接缝 */
    @FunctionalInterface
    public interface FileFetcher {

        /** @param path 资源根相对路径（如 common/env/env.yaml）；文件不存在返回 null */
        String fetch(String path);
    }

    /** 套件加载失败（引用缺失 / YAML 非法 / 成环 / 根结构非法），计入解析失败明细而非中止导入 */
    public static class LoadException extends RuntimeException {

        public LoadException(String message) {
            super(message);
        }
    }

    private static final Tag INCLUDE_TAG = new Tag("!include");

    private final FileFetcher fetcher;
    /** 文件文本缓存：一次导入会话内复用，同一被引用文件不重复拉取 */
    private final Map<String, String> textCache = new HashMap<>();
    /** 祖先引用栈：链上重复出现即为环；兄弟位置的重复引用合法 */
    private final Deque<String> resolving = new ArrayDeque<>();

    public RyzeSuiteYamlLoader(FileFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * 加载套件 YAML 为 Map（title / variables / children 等键，交由 RyzeYamlToSceneConverter 转换）。
     *
     * @param path 资源根相对路径（与 @RyzeTest 声明一致，如 order/list/query_status.yaml）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadSuite(String path) {
        Object root = loadDocument(path);
        if (!(root instanceof Map)) {
            throw new LoadException("步骤模板 " + path + " 根结构不是映射");
        }
        return (Map<String, Object>) root;
    }

    private Object loadDocument(String path) {
        String normalized = normalize(path);
        if (normalized == null) {
            throw new LoadException("非法引用路径：" + path);
        }
        boolean root = resolving.isEmpty();
        if (resolving.contains(normalized)) {
            throw new LoadException("引用成环：" + normalized);
        }
        resolving.push(normalized);
        try {
            String text = textCache.computeIfAbsent(normalized, p -> {
                String fetched = fetcher.fetch(p);
                if (fetched == null) {
                    throw new LoadException((root ? "步骤模板 " : "引用文件 ") + p + " 未找到");
                }
                return fetched;
            });
            // include 构造中递归解析子文档：新实例隔离 composer 状态，缓存与引用栈由本会话共享
            return new Yaml(new IncludeConstructor()).load(text);
        } catch (LoadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LoadException("步骤模板 " + normalized + " 非法：" + e.getMessage());
        } finally {
            resolving.pop();
        }
    }

    private static String normalize(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.contains("..")) {
            return null;
        }
        return normalized;
    }

    /** {@code !include} 标签处理器：按资源根相对路径取回并内联为解析后的文档根 */
    private final class IncludeConstructor extends SafeConstructor {

        IncludeConstructor() {
            super(new org.yaml.snakeyaml.LoaderOptions());
            yamlConstructors.put(INCLUDE_TAG, new Construct() {

                @Override
                public Object construct(Node node) {
                    if (!(node instanceof ScalarNode scalar)) {
                        throw new LoadException("!include 引用必须是文件路径标量");
                    }
                    return loadDocument(scalar.getValue());
                }

                @Override
                public void construct2ndStep(Node node, Object object) {
                    // 标量节点无二段构造
                }
            });
        }
    }
}
