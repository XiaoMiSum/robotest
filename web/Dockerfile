# web/Dockerfile — 多阶段构建：pnpm 构建 dist → nginx 托管（文件管理详设 8.2）
# server 运行镜像的静态产物目录与 nginx conf 的 root 保持一致（docker/web/nginx.conf 对齐基线 /var/www/html）
FROM node:22-alpine AS build
WORKDIR /build
# pnpm 主版本对齐本地开发环境：pnpm-workspace.yaml 仅含 allowBuilds（pnpm 10+ 语法，
# 审批 esbuild 等原生依赖构建脚本），pnpm 9 会视其为 workspace 并因缺 packages 字段失败
RUN npm install -g pnpm@12
# 审批文件 pnpm-workspace.yaml（allowBuilds）必须与依赖描述同批进入，否则 install 阶段无审批而拒绝执行 esbuild 等构建脚本
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# 构建产物使用 .env.production 的同源相对路径（/api、/ws），nginx 反代即为后端
RUN pnpm run build

FROM nginx:1.27-alpine
# conf 源在仓库 docker/web/，经 compose additional_contexts 引入（构建上下文仅 ./web）
COPY --from=conf nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /var/www/html
EXPOSE 80
