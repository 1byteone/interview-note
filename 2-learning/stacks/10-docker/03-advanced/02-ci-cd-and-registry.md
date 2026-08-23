# CI/CD 与镜像仓库 — Harbor · GitLab CI · GitHub Actions

> 等级：🎯 面试进阶
> 目标：搭建私有镜像仓库，构建 Git → 构建 → 推送 → 部署 的自动化交付流水线。

---

## 一、私有镜像仓库

### 1.1 为什么需要私有仓库

| 原因 | 说明 |
|------|------|
| 安全性 | 业务代码、内部镜像不能公开 |
| 合规性 | 镜像需审计、版本留痕 |
| 网络 | 内网环境无法访问 Docker Hub |
| 效率 | 内网拉取速度快数倍 |
| 治理 | 多环境共享同一镜像源 |

### 1.2 Docker Registry（轻量方案）

```bash
# 启动一个简单的私有 Registry
docker run -d \
  --name registry \
  -p 5000:5000 \
  -v registry_data:/var/lib/registry \
  registry:2

# 推送本地镜像
docker tag mall-order:1.0 localhost:5000/mall/order:1.0
docker push localhost:5000/mall/order:1.0

# 拉取
docker pull localhost:5000/mall/order:1.0
```

### 1.3 Harbor（企业级方案）

Harbor 提供：权限控制（RBAC）、镜像复制、漏洞扫描、Web 管理界面、审计日志。

```yaml
# docker-compose 部署 Harbor
version: "3.8"
services:
  harbor:
    image: goharbor/harbor-portal:v2.10.1
    ...
  # 完整部署参考官方 install.sh
```

```bash
# Harbor 常用流程
# 1. 登录
docker login harbor.example.com

# 2. 打标签（需包含仓库域名）
docker tag mall-order:1.0 harbor.example.com/mall/order:1.0

# 3. 推送
docker push harbor.example.com/mall/order:1.0

# 4. 生产节点拉取
docker pull harbor.example.com/mall/order:1.0
```

---

## 二、CI/CD 核心链路

```
Git 提交 → CI (构建/测试) → 打镜像 → 推送仓库 → CD (部署)
     │            │            │         │          │
  push/master   mvn test     build      push      deploy
               docker build            到 Harbor    compose 或 k8s
```

### 2.1 各阶段详解

| 阶段 | 工具 | 产出 |
|------|------|------|
| 代码托管 | GitLab / GitHub | 源码 + 版本 |
| CI 构建 | GitLab CI / GitHub Actions / Jenkins | 可部署产物 + 镜像 |
| 镜像仓库 | Harbor / Docker Hub | 版本化镜像 |
| CD 部署 | SSH + docker compose / kubectl | 运行中的服务 |
| 状态反馈 | 监控 + 通知 | 部署结果 |

### 2.2 镜像版本规范

```bash
# Tag 规范
mall-order:1.0.0           # 语义化版本（推荐生产）
mall-order:20260822-1430   # 时间戳版本
mall-order:git-abc1234     # Git Commit 版本（方便追溯）
mall-order:latest          # 易变，生产慎用
```

---

## 三、GitLab CI 配置

### 3.1 GitLab Runner 安装

```bash
# 安装 GitLab Runner
curl -L "https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh" | bash
apt-get install gitlab-runner

# 注册 Runner（使用 GitLab 项目 Settings → CI/CD → Runners）
gitlab-runner register \
  --url https://gitlab.example.com \
  --token <PROJECT_TOKEN> \
  --executor docker \
  --docker-image alpine:latest
```

### 3.2 .gitlab-ci.yml 完整示例

```yaml
# .gitlab-ci.yml
stages:
  - build
  - test
  - image
  - deploy

variables:
  IMAGE_TAG: $CI_COMMIT_SHORT_SHA          # Git 提交短 SHA

maven-build:
  stage: build
  image: maven:3.9-eclipse-temurin-17
  script:
    - mvn clean package -DskipTests
  artifacts:
    paths:
      - mall-order-service/target/*.jar
    expire_in: 1 hour

unit-test:
  stage: test
  image: maven:3.9-eclipse-temurin-17
  script:
    - mvn test
  only:
    - merge_requests
    - master

docker-build:
  stage: image
  image: docker:26
  services:
    - docker:26-dind                       # Docker in Docker
  before_script:
    - docker login -u $HARBOR_USER -p $HARBOR_PASSWORD harbor.example.com
  script:
    - docker build -t harbor.example.com/mall/order:${IMAGE_TAG} ./mall-order-service
    - docker tag harbor.example.com/mall/order:${IMAGE_TAG} harbor.example.com/mall/order:latest
    - docker push harbor.example.com/mall/order:${IMAGE_TAG}
    - docker push harbor.example.com/mall/order:latest
  only:
    - master

deploy-prod:
  stage: deploy
  image: alpine
  before_script:
    - apk add --no-cache openssh-client
  script:
    - ssh root@prod-server "docker compose -f /srv/mall/docker-compose.yml pull order-service &&
                           docker compose -f /srv/mall/docker-compose.yml up -d --no-deps order-service"
  environment:
    name: production
  only:
    - master
  when: manual                          # 生产部署手动触发
```

---

## 四、GitHub Actions 配置

```yaml
# .github/workflows/deploy.yml
name: Build and Deploy

on:
  push:
    branches: [main]
    paths:
      - 'mall-order-service/**'          # 只对订单服务触发

env:
  IMAGE_NAME: mall/order
  IMAGE_TAG: ${{ github.sha }}

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      # 1. 检出代码
      - name: Checkout
        uses: actions/checkout@v4

      # 2. 设置 JDK
      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      # 3. Maven 构建
      - name: Build with Maven
        run: mvn -B package -DskipTests --file mall-order-service/pom.xml

      # 4. 登录镜像仓库
      - name: Login to Harbor
        uses: docker/login-action@v3
        with:
          registry: harbor.example.com
          username: ${{ secrets.HARBOR_USER }}
          password: ${{ secrets.HARBOR_PASSWORD }}

      # 5. 构建并推送镜像
      - name: Build and Push Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./mall-order-service
          push: true
          tags: |
            harbor.example.com/${{ env.IMAGE_NAME }}:${{ env.IMAGE_TAG }}
            harbor.example.com/${{ env.IMAGE_NAME }}:latest

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to server
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.PROD_HOST }}
          username: ${{ secrets.PROD_USER }}
          key: ${{ secrets.PROD_SSH_KEY }}
          script: |
            cd /srv/mall
            docker compose pull order-service
            docker compose up -d --no-deps order-service
            docker image prune -f
```

### GitHub Actions 关键概念

| 概念 | 说明 |
|------|------|
| `on` | 触发条件（push / pull_request / schedule） |
| `jobs` | 任务（可并行、可依赖） |
| `steps` | 具体步骤 |
| `uses` | 复用社区 Action |
| `secrets` | 加密的敏感配置 |

---

## 五、实战：AI 商城自动部署流水线

### 5.1 目标

```
开发者 push 代码到 master
    → 自动构建 + 单测 + 镜像
    → 自动推送到 Harbor
    → SSH 登录生产服务器
    → docker compose 滚动更新订单服务
    → 健康检查通过则完成，失败自动回滚
```

### 5.2 生产服务器执行脚本

```bash
# deploy.sh（生产环境执行）
#!/bin/bash
set -e

IMAGE_TAG=$1
SERVICE=$2
COMPOSE_FILE=/srv/mall/docker-compose.yml

# 1. 拉取新镜像
docker compose -f $COMPOSE_FILE pull $SERVICE

# 2. 滚动更新（先启动新容器，健康后停旧容器）
docker compose -f $COMPOSE_FILE up -d --no-deps --scale $SERVICE=2 $SERVICE

# 3. 等待健康检查
sleep 30
HEALTH=$(docker inspect --format='{{.State.Health.Status}}' ${SERVICE}_1)
if [ "$HEALTH" != "healthy" ]; then
    echo "部署失败，回滚！"
    docker compose -f $COMPOSE_FILE up -d --no-deps --scale $SERVICE=2 $SERVICE:previous_tag
    exit 1
fi

# 4. 缩容回单副本
docker compose -f $COMPOSE_FILE up -d --no-deps --scale $SERVICE=1 $SERVICE

# 5. 清理旧镜像
docker image prune -f
```

### 5.3 与 docker-compose 版本管理配合

```yaml
services:
  order-service:
    image: harbor.example.com/mall/order:${ORDER_TAG}   # 用变量控制版本
    restart: unless-stopped
```

```bash
# 部署不同版本
ORDER_TAG=1.0.0 docker compose up -d --no-deps order-service
ORDER_TAG=1.0.1 docker compose up -d --no-deps order-service
```

---

## 六、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| 为什么用私有镜像仓库？ | 安全隔离、内网加速、权限控制和审计需求 |
| Harbor 和 Docker Registry 区别？ | Harbor 多了权限控制、漏洞扫描、复制、Web 界面 |
| 镜像 tag 怎么规范？ | 语义化版本或 Git SHA，方便回滚追溯，慎用 latest |
| CI 和 CD 的边界？ | CI 负责构建测试出镜像，CD 负责部署发布 |
| 容器如何滚动更新？ | compose scale 扩容 + up -d 替换 + 健康检查后缩容 |

> 掌握 CI/CD 后，下一节学习容器安全与可观测性。