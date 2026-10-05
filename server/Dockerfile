# server/Dockerfile — 多阶段构建：maven 打包 → JRE 运行（文件管理详设 8.2）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 依赖先行：源码变更时复用依赖层缓存（go-offline 偶发失败不阻断，联网阶段兜底）
COPY pom.xml .
RUN mvn -B -q -Pprod dependency:go-offline -DskipTests || true
COPY src ./src
RUN mvn -B -Pprod -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 58080
ENTRYPOINT ["java", "-jar", "app.jar"]
