# 直接运行镜像，maven编译阶段需要在本地生成jar包
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安装ffmpeg + 时区
ENV TZ=Asia/Shanghai
RUN apk update && apk add --no-cache ffmpeg tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && rm -rf /var/cache/apk/*

# 创建普通用户
RUN addgroup -S vidego && adduser -S vidego -G vidego

# 复制本地已经打好的jar
COPY app.jar /app/app.jar

EXPOSE 8080
USER vidego
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]