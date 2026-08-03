package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 平台使用指引知识库（详细设计 4.5）。
 *
 * <p>静态知识片段位于 {@code ai/guide/*.md}，每片段头部 YAML 元数据：
 * {@code topic}（主题词数组）、{@code route}（平台路由模板）、{@code roles}（适用角色，可空）；
 * 启动时加载内存。检索先按 roles 过滤（非空时仅保留包含当前用户在本空间角色的片段，
 * 空表示全员适用），再按 topic 关键词匹配取 Top 3。</p>
 */
@Slf4j
@Component
public class GuideKnowledgeBase {

    private static final Pattern TOPIC_PATTERN = Pattern.compile("topic\\s*:\\s*\\[(.*)]");
    private static final Pattern ROUTE_PATTERN = Pattern.compile("route\\s*:\\s*(\\S+)");
    private static final Pattern ROLES_PATTERN = Pattern.compile("roles\\s*:\\s*\\[(.*)]");
    private static final Pattern YAML_BLOCK = Pattern.compile("^---\\s*$(.*?)^---\\s*$", Pattern.MULTILINE | Pattern.DOTALL);
    private static final int MAX_RESULTS = 3;

    private final List<GuideFragment> fragments = new ArrayList<>();

    @Resource
    private WorkspaceUserMapper workspaceUserMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @PostConstruct
    public void load() {
        try {
            org.springframework.core.io.Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:ai/guide/*.md");
            for (org.springframework.core.io.Resource resource : resources) {
                GuideFragment fragment = parse(resource);
                if (fragment != null) {
                    fragments.add(fragment);
                }
            }
        } catch (Exception e) {
            log.warn("[AI] 使用指引知识库加载失败: {}", e.getMessage());
        }
        log.info("[AI] 使用指引知识库加载完成，共 {} 片段", fragments.size());
    }

    /**
     * 按用户空间角色过滤 + topic 关键词匹配检索，取 Top 3
     *
     * @return 匹配片段（含 topic 元数据与正文）
     */
    public List<GuideFragment> search(UUID userId, UUID workspaceId, String topic) {
        if (topic == null || topic.isBlank()) {
            return List.of();
        }
        List<String> userRoles = resolveRoles(userId, workspaceId);
        String query = topic.trim().toLowerCase(Locale.ROOT);
        return fragments.stream()
                .filter(fragment -> rolesMatch(fragment, userRoles))
                .filter(fragment -> fragment.topics().stream().anyMatch(t -> query.contains(t.toLowerCase(Locale.ROOT))))
                .limit(MAX_RESULTS)
                .toList();
    }

    private List<String> resolveRoles(UUID userId, UUID workspaceId) {
        if (userId == null || workspaceId == null) {
            return List.of();
        }
        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null || workspaceUser.getWorkspaceRole() == null) {
            return List.of();
        }
        SysRole role = sysRoleMapper.selectById(workspaceUser.getWorkspaceRole());
        return role == null ? List.of() : List.of(role.getName());
    }

    private boolean rolesMatch(GuideFragment fragment, List<String> userRoles) {
        if (fragment.roles().isEmpty()) {
            return true;
        }
        return userRoles.stream().anyMatch(fragment.roles()::contains);
    }

    private GuideFragment parse(org.springframework.core.io.Resource resource) {
        String text = read(resource);
        if (text == null) {
            return null;
        }
        Matcher block = YAML_BLOCK.matcher(text);
        if (!block.find()) {
            return null;
        }
        String header = block.group(1);
        List<String> topics = matchList(header, TOPIC_PATTERN);
        String route = matchString(header, ROUTE_PATTERN);
        List<String> roles = matchList(header, ROLES_PATTERN);
        String body = text.substring(block.end()).trim();
        if (topics.isEmpty() || body.isEmpty()) {
            return null;
        }
        return new GuideFragment(topics, route, roles, body);
    }

    private String read(org.springframework.core.io.Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[AI] 使用指引片段读取失败: {}", resource.getFilename());
            return null;
        }
    }

    private List<String> matchList(String header, Pattern pattern) {
        Matcher matcher = pattern.matcher(header);
        if (!matcher.find()) {
            return List.of();
        }
        String content = matcher.group(1);
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        return Arrays.stream(content.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(t -> t.replaceAll("^['\"]|['\"]$", ""))
                .toList();
    }

    private String matchString(String header, Pattern pattern) {
        Matcher matcher = pattern.matcher(header);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        return StringUtils.hasText(value) ? value.replaceAll("^['\"]|['\"]$", "") : null;
    }

    /**
     * 供工具结果序列化使用的检索结果（只含元数据与正文，不含内部角色过滤逻辑）
     */
    public record GuideFragment(List<String> topics, String route, List<String> roles, String content) {
    }
}
