# 第十六章：CI/CD 流水线（P1 进阶）

> 📖 **参考资料**：[GitHub Actions 文档](https://docs.github.com/en/actions) | [Docker Docs](https://docs.docker.com/) | [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)

---

## 16.1 CI/CD 流水线

CI/CD（持续集成 / 持续交付）将「写代码 → 上线」从手工流程变成自动化流水线：

```
┌────────┐   ┌────────┐   ┌────────┐   ┌────────┐   ┌────────┐
│  push  │──▶│  lint  │──▶│  test  │──▶│ build  │──▶│ deploy │
│  (提交) │   │  (静态) │   │ (测试)  │   │ (镜像)  │   │ (部署)  │
└────────┘   └────────┘   └────────┘   └────────┘   └────────┘
    │            │            │            │            │
  main 分支    ruff/black  pytest 100%   docker build  ssh 到服务器
  PR 触发      mypy 类型    + coverage    push GHCR     docker compose
```

| 阶段 | 工具 | 产物 / 目标 |
|------|------|-------------|
| Lint | ruff + black + mypy | 代码质量门禁 |
| Test | pytest + coverage | 单元/集成测试通过 |
| Build | docker buildx | 多架构镜像 |
| Push | GHCR | 镜像托管 |
| Deploy | ssh + docker compose | 生产服务器 |

---

## 16.2 GitHub Actions 完整工作流

### 目录结构约定

```text
.github/
└── workflows/
    └── ci.yml          # 主流水线
```

### ci.yml 主流程概览

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
          cache: pip
      - run: pip install ruff mypy
      - run: ruff check . && ruff format --check .
      - run: mypy app

  test:
    runs-on: ubuntu-latest
    needs: lint            # 依赖 lint 通过
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
          cache: pip
      - run: pip install -r requirements.txt -r requirements-dev.txt
      - run: pytest --cov=app --cov-report=xml
      - uses: codecov/codecov-action@v4  # 上传覆盖率
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
```

> `needs` 控制作业依赖，`concurrency` 防止同一分支并发运行浪费资源。

---

## 16.3 Docker Build + Push to GHCR

GHCR（GitHub Container Registry）地址格式为 `ghcr.io/<owner>/<image>`，需要令牌认证。

### 构建作业

```yaml
  build:
    runs-on: ubuntu-latest
    needs: test                      # 测试通过后才构建
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    permissions:
      contents: read
      packages: write                # 推送 GHCR 所需权限
    outputs:
      image_tag: ${{ steps.meta.outputs.tags }}
    steps:
      - uses: actions/checkout@v4

      - name: Set up QEMU
        uses: docker/setup-qemu-action@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Docker meta
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository }}
          tags: |
            type=ref,event=branch
            type=sha,prefix=git-
            type=raw,value=latest,enable={{is_default_branch}}
            type=semver,pattern={{version}}

      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          platforms: linux/amd64,linux/arm64   # 多架构
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

> `metadata-action` 自动生成语义化标签（`latest` / `git-<sha>` / 版本号），`gha` 缓存大幅加速重复构建。

### 配套 Dockerfile 要点

```dockerfile
FROM python:3.12-slim AS builder
COPY requirements.txt .
RUN pip wheel --no-cache-dir -w /wheels -r requirements.txt

FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1
WORKDIR /app
COPY --from=builder /wheels /wheels
RUN pip install --no-index --find-links=/wheels -r requirements.txt
COPY app ./app
EXPOSE 8000
USER nobody
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

---

## 16.4 SSH 部署

构建完成后通过 SSH 登录服务器，拉取新镜像并滚动重启。

### 部署作业

```yaml
  deploy:
    runs-on: ubuntu-latest
    needs: build                    # 等待镜像推送完成
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    environment: production         # 关联环境保护规则
    steps:
      - uses: actions/checkout@v4

      - name: Install SSH key
        run: |
          mkdir -p ~/.ssh
          printf '%s\n' "${{ secrets.DEPLOY_SSH_KEY }}" > ~/.ssh/id_ed25519
          chmod 600 ~/.ssh/id_ed25519
          ssh-keyscan -H ${{ secrets.DEPLOY_HOST }} >> ~/.ssh/known_hosts

      - name: Deploy via SSH
        run: |
          ssh -o StrictHostKeyChecking=no \
            ${{ secrets.DEPLOY_USER }}@${{ secrets.DEPLOY_HOST }} << 'EOF'
            set -e
            cd /opt/python-backend
            # 1. 拉取最新镜像
            docker pull ghcr.io/${{ github.repository }}:latest
            # 2. 优雅重启（先启动新容器，再移除旧容器）
            docker compose up -d --no-deps --build api
            # 3. 清理悬空镜像
            docker image prune -f
          EOF

      - name: Health check
        run: |
          sleep 10
          curl -sf http://${{ secrets.DEPLOY_HOST }}/api/v1/health || exit 1
```

### 所需 Secrets 清单

| Secret 名称 | 用途 |
|-------------|------|
| `DEPLOY_SSH_KEY` | 服务器 SSH 私钥（无密码） |
| `DEPLOY_HOST` | 服务器 IP / 域名 |
| `DEPLOY_USER` | SSH 用户名 |
| `GITHUB_TOKEN` | 自动生成，无需配置 |
| `CODECOV_TOKEN` | 覆盖率上传令牌（可选） |

> **安全提醒**：SSH 私钥永远只保存在 GitHub Secrets 中，切勿写入仓库代码。

---

## 16.5 分支策略

推荐 **GitHub Flow + 环境门禁** 组合：

```text
feature/xxx ──▶ PR ──▶ main ──▶ 构建+部署到 staging(自动)
     │            │          │
     │            │          └──▶ production（需人工审批）
     │            └──▶ lint+test 全部通过方可合并
     └── 短命分支，合并后即删
```

| 分支 | 用途 | 部署环境 | 触发时机 |
|------|------|----------|----------|
| `feature/*` | 功能开发 | 无 | PR 时跑 lint/test |
| `develop` | 集成联调 | staging | push 自动部署 |
| `main` | 稳定主干 | production | push 后需人工审批 |

在 GitHub 上通过 **Branch Protection Rules** 强制：

```text
- 要求 PR 通过 CI 检查
- 要求线性历史（rebase merge）
- main 分支禁止直接 push
- 生产环境部署需 1 人以上 approve
```

---

## 必读资源

| 资源 | 链接 |
|------|------|
| GitHub Actions 官方文档 | https://docs.github.com/en/actions |
| Docker Official Images | https://hub.docker.com/_/python |
| GHCR 使用指南 | https://docs.github.com/en/packages |
| 简易部署工具 | https://github.com/appleboy/ssh-action |
| 建议阅读 | *"Continuous Delivery"* — Jez Humble & Dave Farley |