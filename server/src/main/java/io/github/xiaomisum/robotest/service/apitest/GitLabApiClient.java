package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * GitLab REST API 最小客户端（GitLab 集成详细设计 3.1 / 4.4）。
 * <p>
 * 不引入 SDK，复用 JDK HttpClient；探测顺序实例版本 → 令牌 scope → 项目路径 → 目标分支，
 * 任一不满足抛 1000017702 且失败原因可区分实例不可达 / 令牌无效 / 分支不存在（需求 BR-1.5）。
 * 真实 I/O 经 {@link HttpOpener} 函数式接缝注入，单测以假实现替代网络调用。
 */
@Component
public class GitLabApiClient {

    /** 目录树翻页上限（per_page=100 时即 10 万条），防御异常实例无限返回满页导致死循环 */
    private static final int MAX_TREE_PAGES = 1000;

    /** HTTP GET 接缝：真实 HttpClient 逻辑在客户端内提供，测试注入假实现 */
    @FunctionalInterface
    public interface HttpOpener {
        HttpResponse<String> get(String url, String token) throws Exception;
    }

    /** 探测结果：实例版本 + 目标分支实际分支名 */
    public record Probe(String version, String resolvedBranch) {
    }

    /** 目录树条目（GitLab repository/tree 接口，仅取路径与类型） */
    public record TreeEntry(String path, String type) {
    }

    /** 文件内容（GitLab repository/files 接口，base64 编码） */
    public record FileContent(String content, String encoding) {
    }

    private final HttpOpener httpOpener;

    public GitLabApiClient() {
        this(GitLabApiClient::httpGet);
    }

    GitLabApiClient(HttpOpener httpOpener) {
        this.httpOpener = httpOpener;
    }

    /**
     * 连通性探测：依次校验实例可达、令牌有效且具 api scope、项目路径可访问、目标分支存在。
     *
     * @throws xyz.migoo.framework.common.exception.ServiceException 1000017702，消息区分失败原因
     */
    public Probe probe(String baseUrl, String token, String repoPath, String branch) {
        String base = trimTrailingSlash(baseUrl);
        String version = probeVersion(base, token);
        verifyTokenScope(base, token);
        String projectId = verifyProject(base, token, repoPath);
        String resolvedBranch = verifyBranch(base, token, projectId, branch);
        return new Probe(version, resolvedBranch);
    }

    /** 实例可达性 + 版本：GET /api/v4/version（无需认证，401 亦视为可达） */
    private String probeVersion(String base, String token) {
        HttpResponse<String> response = send(base + "/api/v4/version", token);
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw connectFailed("实例不可达：HTTP " + response.statusCode());
        }
        VersionInfo info = JsonUtils.parseObject(response.body(), VersionInfo.class);
        return info == null ? null : info.version();
    }

    /** 令牌有效性与 scope：GET /api/v4/personal_access_tokens/self */
    private void verifyTokenScope(String base, String token) {
        HttpResponse<String> response = send(base + "/api/v4/personal_access_tokens/self", token);
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw connectFailed("令牌无效：HTTP " + status);
        }
        if (status != 200) {
            throw connectFailed("实例不可达：HTTP " + status);
        }
        TokenInfo info = JsonUtils.parseObject(response.body(), TokenInfo.class);
        if (info == null || info.scopes() == null || !info.scopes().contains("api")) {
            throw connectFailed("令牌缺少 api scope，无法访问 GitLab API");
        }
    }

    /** 项目路径可访问性：GET /api/v4/projects/:id，返回数字项目 ID 供分支探测复用 */
    private String verifyProject(String base, String token, String repoPath) {
        HttpResponse<String> response = send(base + "/api/v4/projects/" + encodePath(repoPath), token);
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw connectFailed("令牌无效或无项目访问权限：HTTP " + status);
        }
        if (status == 404) {
            throw connectFailed("项目路径不存在或令牌无访问权限：" + repoPath);
        }
        if (status != 200) {
            throw connectFailed("实例不可达：HTTP " + status);
        }
        ProjectInfo info = JsonUtils.parseObject(response.body(), ProjectInfo.class);
        if (info == null || info.id() == null) {
            throw connectFailed("实例响应异常：项目信息解析失败");
        }
        return String.valueOf(info.id());
    }

    /** 目标分支存在性：GET /api/v4/projects/:id/repository/branches/:branch */
    private String verifyBranch(String base, String token, String projectId, String branch) {
        BranchInfo info = fetchBranch(base, token, projectId, branch);
        return info != null && info.name() != null ? info.name() : branch;
    }

    /**
     * 取目标分支当前 HEAD 提交号（幂等闸门与源探查的比对基准，GitLab 集成详细设计 3.3 / 3.5）。
     *
     * @throws xyz.migoo.framework.common.exception.ServiceException 1000017702，分支不存在、令牌无权限或实例不可达
     */
    public String branchHead(String baseUrl, String token, String repoPath, String branch) {
        String base = trimTrailingSlash(baseUrl);
        String projectId = verifyProject(base, token, repoPath);
        BranchInfo info = fetchBranch(base, token, projectId, branch);
        if (info == null || info.commit() == null
                || info.commit().id() == null || info.commit().id().isBlank()) {
            throw connectFailed("分支 HEAD 提交号缺失：" + branch);
        }
        return info.commit().id();
    }

    /** 分支信息：GET .../repository/branches/:branch，名称与 HEAD 提交共用一次拉取 */
    private BranchInfo fetchBranch(String base, String token, String projectId, String branch) {
        HttpResponse<String> response = send(base + "/api/v4/projects/" + projectId
                + "/repository/branches/" + encodePath(branch), token);
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw connectFailed("令牌无效或无仓库访问权限：HTTP " + status);
        }
        if (status == 404) {
            throw connectFailed("分支不存在：" + branch);
        }
        if (status != 200) {
            throw connectFailed("实例不可达：HTTP " + status);
        }
        return JsonUtils.parseObject(response.body(), BranchInfo.class);
    }

    /**
     * 递归列出目录下的文件路径（GitLab repository/tree 接口，分页取全）。
     *
     * @param dir 目录路径（如 src/test/java），空串表示仓库根
     * @return 仓库相对文件路径清单（blob 类型，含分页合并）
     * @throws xyz.migoo.framework.common.exception.ServiceException 1000017702，查询失败
     */
    public List<String> listFiles(String baseUrl, String token, String repoPath, String branch, String dir) {
        String base = trimTrailingSlash(baseUrl);
        String projectId = verifyProject(base, token, repoPath);
        List<String> paths = new java.util.ArrayList<>();
        int page = 1;
        while (true) {
            String url = base + "/api/v4/projects/" + projectId + "/repository/tree"
                    + "?ref=" + URLEncoder.encode(branch, StandardCharsets.UTF_8)
                    + "&path=" + URLEncoder.encode(dir == null ? "" : dir, StandardCharsets.UTF_8)
                    + "&recursive=true&per_page=100&page=" + page;
            HttpResponse<String> response = send(url, token);
            int status = response.statusCode();
            if (status != 200) {
                throw connectFailed("仓库目录不可达：HTTP " + status);
            }
            List<TreeEntry> entries = JsonUtils.parseObject(response.body(),
                    new tools.jackson.core.type.TypeReference<List<TreeEntry>>() {
                    });
            if (entries == null || entries.isEmpty()) {
                break;
            }
            entries.stream()
                    .filter(e -> "blob".equals(e.type()))
                    .map(TreeEntry::path)
                    .forEach(paths::add);
            if (entries.size() < 100 || page >= MAX_TREE_PAGES) {
                break;
            }
            page++;
        }
        return paths;
    }

    /**
     * 读取单个文件内容（GitLab repository/files 接口，base64 解码为 UTF-8 文本）。
     *
     * @throws xyz.migoo.framework.common.exception.ServiceException 1000017702，文件不存在或读取失败
     */
    public String getFileContent(String baseUrl, String token, String repoPath, String branch, String filePath) {
        String content = findFileContent(baseUrl, token, repoPath, branch, filePath);
        if (content == null) {
            throw connectFailed("文件不存在：" + filePath);
        }
        return content;
    }

    /**
     * 读取单个文件内容；文件不存在返回 null（区分「缺失」与「不可达」，
     * 供 YAML 引用解析将缺失计入解析失败而非中止导入，GitLab 集成详细设计 3.5）。
     */
    public String findFileContent(String baseUrl, String token, String repoPath, String branch, String filePath) {
        String base = trimTrailingSlash(baseUrl);
        String projectId = verifyProject(base, token, repoPath);
        String url = base + "/api/v4/projects/" + projectId + "/repository/files/"
                + encodePath(filePath) + "?ref=" + URLEncoder.encode(branch, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(url, token);
        int status = response.statusCode();
        if (status == 404) {
            return null;
        }
        if (status != 200) {
            throw connectFailed("文件读取失败：HTTP " + status);
        }
        FileContent info = JsonUtils.parseObject(response.body(), FileContent.class);
        if (info == null || info.content() == null) {
            throw connectFailed("文件读取失败：响应解析异常");
        }
        byte[] bytes = java.util.Base64.getMimeDecoder().decode(info.content());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> send(String url, String token) {
        try {
            return httpOpener.get(url, token);
        } catch (Exception e) {
            throw connectFailed("实例不可达：" + (e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private static RuntimeException connectFailed(String reason) {
        return ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code(), reason);
    }

    /** 真实 HTTP GET：连接超时 10s、响应超时 30s，跟随重定向，携带 PRIVATE-TOKEN 头 */
    static HttpResponse<String> httpGet(String url, String token) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(10000))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(30000))
                .header("Accept", "application/json")
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("PRIVATE-TOKEN", token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String trimTrailingSlash(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** 项目路径与分支名按 URL 路径段编码（/ → %2F），GitLab v4 API 要求编码后的项目路径 */
    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record VersionInfo(String version) {
    }

    private record TokenInfo(List<String> scopes) {
    }

    private record ProjectInfo(Long id) {
    }

    private record BranchInfo(String name, CommitInfo commit) {

        private record CommitInfo(String id) {
        }
    }

}
