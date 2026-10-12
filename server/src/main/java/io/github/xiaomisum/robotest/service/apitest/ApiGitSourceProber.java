package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.repository.apitest.ApiGitRepoMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 源探查服务（GitLab 集成详细设计 3.7，SRS BR-1.35 / BR-1.45）：
 * 固定 30 分钟周期回写启用仓库的默认分支 HEAD，「源有更新」由
 * {@code head_commit != last_import_commit} 派生，不落独立状态位。
 *
 * <p>探查只读：不触发导入、不写场景、不落任务执行记录、失败不重试（下周期自然重试）、
 * 无用户入口（BR-1.35 规定周期固定不可配置，故取常量）。探查失败仅留痕
 * {@code head_probe_status=unreachable} 并保留上次 head_commit，不影响场景查看与执行。
 *
 * <p>令牌沿用仓库配置的 AES-256-GCM 密文，解密失败按不可达处理，禁止明文入日志。
 */
@Slf4j
@Component
public class ApiGitSourceProber {

    /** 探查周期毫秒（BR-1.35：固定周期、不可配置）；启动延迟同周期，避免实例重启即发起请求 */
    private static final long PERIOD_MS = 30 * 60 * 1000L;

    private static final String PROBE_OK = "ok";
    private static final String PROBE_UNREACHABLE = "unreachable";

    @Resource
    private ApiGitRepoMapper gitRepoMapper;
    @Resource
    private GitLabApiClient gitLabApiClient;

    private final byte[] secretKey;

    @Autowired
    public ApiGitSourceProber(@Value("${robotest.env.secret-key:}") String base64SecretKey) {
        this(SecretCryptoUtil.parseKey(base64SecretKey));
    }

    /** 测试构造：直接注入已解析密钥 */
    ApiGitSourceProber(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    @Scheduled(fixedDelay = PERIOD_MS, initialDelay = PERIOD_MS)
    public void probeAll() {
        List<ApiGitRepo> repos;
        try {
            repos = gitRepoMapper.selectEnabledRepos();
        } catch (Exception e) {
            log.error("源探查：读取启用仓库失败，本轮跳过", e);
            return;
        }
        for (ApiGitRepo repo : repos) {
            // 回写异常（如瞬时 DB 故障）同样不得中断整轮探查
            try {
                probeOne(repo);
            } catch (Exception e) {
                log.warn("源探查回写失败：repo={} 原因={}", repo.getRepoPath(), e.getMessage());
            }
        }
    }

    /** 单仓库探查：异常全捕获，失败只写 unreachable 留痕，不影响其余仓库与探查循环 */
    void probeOne(ApiGitRepo repo) {
        LocalDateTime checkedAt = LocalDateTime.now();
        try {
            String token = decryptToken(repo);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("访问令牌缺失或无法解密");
            }
            String head = gitLabApiClient.branchHead(repo.getGitlabBaseUrl(), token,
                    repo.getRepoPath(), repo.getDefaultBranch());
            gitRepoMapper.updateProbeResult(repo.getId(), head, checkedAt, PROBE_OK);
        } catch (Exception e) {
            // 令牌不入日志（脱敏）；head_commit 列不触碰，保留上次探查值
            gitRepoMapper.updateProbeResult(repo.getId(), null, checkedAt, PROBE_UNREACHABLE);
            log.warn("源探查失败：repo={} 分支={} 原因={}", repo.getRepoPath(),
                    repo.getDefaultBranch(), e.getMessage());
        }
    }

    /** 解密失败（密钥未配置 / 轮换 / 密文损坏）返回 null，由调用方按不可达降级 */
    private String decryptToken(ApiGitRepo repo) {
        if (secretKey == null) {
            return null;
        }
        return SecretCryptoUtil.decrypt(secretKey, repo.getTokenCipher());
    }
}
