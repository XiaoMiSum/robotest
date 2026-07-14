# 工程规范 — 构建与部署

**文档版本**：V1.0
**日期**：2026-07-06
**状态**：已发布

---

## 1. 构建流程

### 前端构建

```bash
cd web
pnpm ci                 # 锁定版本安装pnpm run lint           # 代码检pnpm run typecheck      # 类型pnpm run test:unit      # 单pnpm
pnpm run build          # 构建产物 → web/dist/
```

### 后端构建

```bash
cd server
mvn clean verify -Pdev  # 包含 checkstyle + spotbugs + test
mvn package -Pprod      # 生产构建
```

### 合并部署构建

```bash
cd web && pnpm run build
cp -r web/dist/* server/src/main/resources/static/
cd server && mvn package -Pmerged
```

---

## 2. 环境配置管理

| 环境   | 配置源                                        | 数据库         | Redis       |
| ---- | ------------------------------------------ | ----------- | ----------- |
| dev  | `.env.development` + `application-dev.yml` | 本地 MySQL    | 本地 Redis    |
| test | `.env.test` + `application-test.yml`       | 测试服 MySQL   | 测试服 Redis   |
| prod | `.env.production` + `application-prod.yml` | 生产 MySQL 主从 | 生产 Redis 集群 |

**敏感信息管理**：

- 密码、密钥、Token 等敏感信息不提交到代码仓库。
- 开发环境使用 `.env.local`（已加入 `.gitignore`）。
- 生产环境通过环境变量或密钥管理服务注入。

**前端 `.env` 文件规范**：

```
# .env.development
VITE_API_BASE_URL=/api
VITE_WS_BASE_URL=ws://localhost:8080/ws
```

---

## 3. 部署方案

| 方案   | 适用场景              | 架构           |
| ---- | ----------------- | ------------ |
| 分离部署 | 大规模团队，前端需要 CDN 加速 | Nginx + 后端集群 |
| 合并部署 | 中小团队，快速交付         | 单 jar（含前端资源） |

分离部署 Nginx 配置示例：

```nginx
location /api/ {
    proxy_pass http://backend:8080;
}
location /ws/ {
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
location / {
    root /var/www/html;
    try_files $uri $uri/ /index.html;
}
```

合并部署 Spring Boot 静态资源配置：

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{path:^(?!api|ws).*}")
                .setViewName("forward:/index.html");
    }
}
```

---

## 4. CI 流水线

```
触发: Push / PR → develop / main

步骤:
  1. Checkout
  2. 前端: pnpm ci → lint → typecheck → test → build
  3. 后端: mvn verify → package
  4. 镜像构建 & 推送（可选）
  5. 部署至 test 环境（可选）
  6. 通知结果

门禁:
  - lint error → 失败
  - typecheck error → 失败
  - test 失败 → 失败
  - coverage < 70% → 不稳定
```

---

**文档结束**
