package io.github.xiaomisum.robotest.framework.mock;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Mock 访问服务配置（Mock服务详细设计 6.1；API测试基础设施详细设计 6.4）
 */
@ConfigurationProperties(prefix = "robotest.api-test.mock")
public class MockAccessProperties {

    /** 免登录 Mock 访问总开关 */
    private boolean accessEnabled = true;
    /** 独立 Mock 端口；为空时复用主端口（复用模式下排除平台业务与 SPA 前缀） */
    private Integer port;
    /** 复制地址展示用基础 URL；为空时按 port 推导 http://localhost:{port} */
    private String baseUrl;
    /** 单路径 QPS 上限，<=0 关闭限流 */
    private int pathQps = 50;
    /** 主端口复用模式下不参与 Mock 匹配的路径前缀（业务路由 + SPA 静态资源） */
    private List<String> excludedPrefixes = List.of(
            "/api", "/ws", "/assets", "/index.html", "/favicon.ico",
            "/login", "/init", "/join", "/admin", "/workspaces", "/workspace");

    public boolean isAccessEnabled() {
        return accessEnabled;
    }

    public void setAccessEnabled(boolean accessEnabled) {
        this.accessEnabled = accessEnabled;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getPathQps() {
        return pathQps;
    }

    public void setPathQps(int pathQps) {
        this.pathQps = pathQps;
    }

    public List<String> getExcludedPrefixes() {
        return excludedPrefixes;
    }

    public void setExcludedPrefixes(List<String> excludedPrefixes) {
        this.excludedPrefixes = excludedPrefixes;
    }

}
