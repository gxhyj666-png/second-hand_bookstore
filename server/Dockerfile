# 多阶段构建：Maven 打包 → 精简 JRE 运行
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 国内构建把 Maven 中央仓库换成阿里云镜像：直连 Central 约 0.5 MB/s，镜像约 20 MB/s
COPY docker/settings.xml /build/settings.xml
COPY pom.xml .
RUN mvn -B -q -s /build/settings.xml dependency:go-offline
COPY src ./src
RUN mvn -B -q -s /build/settings.xml -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=${SPRING_PROFILES_ACTIVE:mysql}"]
