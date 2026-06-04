<div align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=flat&logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql&logoColor=white" alt="MySQL" />
  <img src="https://img.shields.io/badge/Redis-7-DC382D?style=flat&logo=redis&logoColor=white" alt="Redis" />
  <img src="https://img.shields.io/badge/MinIO-9A2A8D?style=flat&logo=minio&logoColor=white" alt="MinIO" />
  <img src="https://img.shields.io/badge/JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white" alt="JWT" />
  <img src="https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white" alt="Docker" />
</div>


<h1 align="center">🎬 Vidego Backend</h1>

<p align="center">
  <strong>视频分享平台后端服务</strong> —— 基于 Spring Boot 3 构建的高性能 RESTful API
</p>
<p align="center">
  	支持用户上传视频、视频在线播放、点赞收藏、评论互动、视频分类管理等功能。
</p>

<p align="center">    项目采用前后端分离架构，集成 Redis 缓存、JWT 鉴权、对象存储等技术。</p>

<p align="center">    该项目由 Claude Code 辅助开发完成，开发过程中重点实践了 AI 协同编程、系统设计与工程化开发能力。</p>

<p align="center">    [前端页面](https://github.com/XLUFFYzerotwo/vidego-web)</p>

---

## 📋 技术栈

| 层级 | 技术 | 用途 |
|------|------|------|
| 核心框架 | Spring Boot 3.2 | 应用容器、依赖注入 |
| ORM | MyBatis-Plus 3.5 | 数据库访问、自动分页 |
| 数据库 | MySQL 8.0 | 持久化存储 |
| 缓存 | Redis 7 | 热点缓存、去重、分布式锁 |
| 对象存储 | MinIO | 视频、封面、头像文件存储 |
| 认证 | JWT (JJWT 0.12) | 无状态认证、Token 黑名单 |
| API 文档 | SpringDoc OpenAPI 2.3 | Swagger UI 接口文档 |
| 部署 | Docker / Docker Compose | 容器化编排 |

---

## ✨ 功能特性

### 核心业务
- **用户系统** — 注册/登录、JWT 双 Token 认证、Redis 黑名单登出、修改密码
- **视频模块** — 预签名 URL 直传 MinIO、FFmpeg 管道流封面生成、播放量防刷
- **评论系统** — 楼中楼回复、分页查询、评论点赞/取消
- **社交互动** — 视频点赞/收藏、用户关注/取关
- **内容发现** — 全站搜索(FULLTEXT)、热门排行、分类推荐、最新视频
- **个人中心** — 投稿/点赞/收藏列表、关注/粉丝列表、头像上传

### 技术亮点
- **Redis 多级缓存** — 视频详情缓存 + 分布式锁防击穿 + 空值缓存防穿透
- **MinIO 预签名 URL** — 前端直传文件不经过后端，分分钟享性能
- **FFmpeg 管道流** — 封面生成全程内存操作，零磁盘 IO
- **Hot Score 定时任务** — 3 小时刷新热门权重，排行榜性能从 filesort 降为索引扫描
- **数据库优化** — 复合索引覆盖核心查询，冗余计数字段避免 COUNT
- **全局异常处理** — 统一响应格式，业务异常 + 参数校验 + 兜底异常三层拦截

---

## 🏗 项目结构

```
vidego-backend/
├── docker/
│   └── docker-compose.yml          # 基础设施编排 (MySQL/Redis/MinIO)
├── sql/
│   └── init.sql                    # 8 张表建表脚本
├── src/main/java/com/vidego/
│   ├── VidegoApplication.java      # 启动入口
│   ├── auth/                       # JWT 认证层
│   │   ├── JwtUtil.java            # Token 生成/解析
│   │   ├── JwtAuthenticationFilter # 请求拦截过滤器
│   │   ├── TokenService.java       # Redis 黑名单管理
│   │   └── UserContext.java        # ThreadLocal 用户上下文
│   ├── common/                     # 公共基础设施
│   │   ├── config/                 # MinIO/Redis/CORS/MyBatis-Plus 配置
│   │   ├── exception/              # 全局异常处理器
│   │   ├── result/                 # 统一响应 Result + PageResult
│   │   ├── constant/               # 常量定义
│   │   └── task/                   # 定时任务 (HotVideoTask)
│   └── module/                     # 业务模块
│       ├── user/                   # 用户注册/登录/个人中心/关注
│       ├── video/                  # 视频上传/播放/点赞/收藏
│       ├── comment/                # 评论/回复/点赞
│       ├── feed/                   # 首页推荐/最新/分类
│       └── search/                 # 全文搜索
└── pom.xml
```

---

## 🚀 本地开发

### 前置条件

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose
- FFmpeg（系统 PATH 中可用）

### 启动步骤

```bash
# 1. 启动基础设施
cd docker
docker-compose up -d

# 2. 配置应用
cp src/main/resources/application-dev-example.yml src/main/resources/application-dev.yml
# 编辑 application-dev.yml 中的数据库/Redis/MinIO 连接信息

# 3. 启动应用
cd ..
mvn spring-boot:run -Dspring.profiles.active=dev

# 4. 访问 Swagger 文档
open http://localhost:8080/swagger-ui/index.html
```

---

## 🐳 Docker 部署

### 后端 Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

WORKDIR /app
COPY vidego-backend-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
```

### Docker Compose 编排

```yaml
# 后端 4 个服务共用 vidego-net 网桥
services:
  mysql:    # 8.0, port 3307
  redis:    # 7-alpine, port 6379
  minio:    # latest (不暴露 9000 公网)
  backend:  # JDK 17 + FFmpeg
```

### 部署步骤

```bash
# 1. 构建
mvn clean package -DskipTests
cp target/vidego-backend-1.0.0.jar deploy/backend/

# 2. 上传服务器
scp -r deploy/ root@<IP>:/opt/vidego/

# 3. 启动
ssh root@<IP>
cd /opt/vidego/deploy
docker-compose up -d
```

---

## 📖 API 文档

启动后访问 Swagger UI：`http://localhost:8080/swagger-ui/index.html`

| 模块 | 端点 | 说明 |
|------|------|------|
| Auth | `POST /api/auth/register` | 注册 |
| Auth | `POST /api/auth/login` | 登录 |
| Auth | `POST /api/auth/logout` | 登出 |
| Auth | `PUT /api/auth/password` | 修改密码 |
| User | `GET/PUT /api/users/{id}` | 用户信息 |
| User | `GET /api/users/{id}/videos` | 投稿列表 |
| User | `GET /api/users/{id}/likes` | 点赞列表 |
| User | `GET /api/users/{id}/favorites` | 收藏列表 |
| User | `POST/DELETE /api/users/{id}/follow` | 关注/取关 |
| User | `POST /api/users/{id}/avatar` | 上传头像 |
| Video | `GET /api/videos/upload-token` | 预签名上传 URL |
| Video | `POST /api/videos` | 创建视频 |
| Video | `GET/DELETE /api/videos/{id}` | 视频详情/删除 |
| Video | `POST /api/videos/{id}/view` | 记录播放量 |
| Video | `POST/DELETE /api/videos/{id}/like` | 点赞/取消 |
| Video | `POST/DELETE /api/videos/{id}/favorite` | 收藏/取消 |
| Comment | `GET /api/videos/{id}/comments` | 评论列表 |
| Comment | `POST /api/videos/{id}/comments` | 发表评论 |
| Comment | `POST/DELETE /api/comments/{id}/like` | 评论点赞 |
| Feed | `GET /api/feed/recommended` | 首页推荐 |
| Feed | `GET /api/feed/latest` | 最新视频 |
| Feed | `GET /api/feed/by-tag` | 按标签分类 |
| Search | `GET /api/search` | 全文搜索 |

---

## 🏆 项目亮点

### 1. 安全上传架构
采用 MinIO 预签名 URL + Nginx 反向代理，视频文件前端直传对象存储，后端零文件 IO。MinIO 不暴露公网端口，杜绝对象存储越权风险。

### 2. 三级缓存防御
针对热点视频详情，构建"Redis 缓存 → 分布式锁 → 空值缓存"三级防御，同时解决缓存穿透、击穿、雪崩三大问题。

### 3. 数据库索引优化
通过 `hot_score` 冗余字段将热门排序从 `filesort` 降为索引扫描；复合索引 `idx_status_created` 覆盖首页全部列表查询。

### 4. 管道流封面生成
FFmpeg 通过 pipe:0/stdin 直读 MinIO 视频流，输出 JPEG 到 stdout 后直接 PutObject，全程内存操作、零磁盘读写。
