# Docker QuickStart 使用指南

## 项目说明

一个简单的 Python Flask 应用，演示 Docker 多阶段构建和 Compose 编排。

## 构建与运行

```bash
# 1. 构建镜像
docker build -t flask-quickstart .

# 2. 运行容器
docker run -d -p 5000:5000 --name flask-app flask-quickstart

# 3. 访问测试
curl http://localhost:5000
# 输出: Hello from Docker!

# 4. 使用 Docker Compose（含 Redis）
docker-compose up -d

# 5. 查看日志
docker-compose logs -f web

# 6. 停止
docker-compose down
```

## 关键概念

| 概念 | 说明 |
|------|------|
| **多阶段构建** | Stage 1 安装依赖，Stage 2 仅复制产物，减小镜像体积 |
| **Volume** | redis_data 持久化 Redis 数据 |
| **depends_on** | 控制服务启动顺序，web 在 redis 之后启动 |
| **EXPOSE 5000** | 声明容器监听端口，实际映射由 -p 或 ports 完成 |

## 镜像体积对比

```bash
# 单阶段构建约 900MB，多阶段仅约 180MB
docker images flask-quickstart
```