# Hermes Agent 安装部署与架构解析

> **生态**: E04 · Hermes/OpenClaw | **等级**: 入门 | **前置要求**: 终端基础操作与 Agent 基本概念

Hermes Agent 是 Nous Research 推出的开源智能体运行时，由 OpenClaw 演进而来并完成全面升级。它定位为一个"可演进的智能体操作系统"：通过插件化的五层架构、三层记忆系统、47 个内置工具和 15+ 平台集成，让 Agent 从一次性脚本走向可持续运行的生产系统。

本教程覆盖 Hermes 的系统要求、三种安装方式、初始化向导、从 OpenClaw 自动导入、五层架构、多平台支持、模型配置以及与 OpenClaw 的对比。

---

## 1. 系统要求

Hermes 是基于 Node.js 的应用，对系统要求较为宽松：

| 项目 | 最低要求 | 推荐 |
|------|---------|------|
| 操作系统 | macOS 12+ / Windows 10 1809+ / Ubuntu 20.04+ | 同左 |
| Node.js | 20.0+ | 22 LTS |
| 内存 | 4 GB | 8 GB（运行本地模型时 16 GB+） |
| 磁盘 | 500 MB（不含模型） | 5 GB+（含本地模型权重） |
| 网络 | 可访问模型 API 端点 | 同左 |

> **本地模型可选**：如果你希望使用本地模型（如 Ollama 集成的 Llama 系列），需要额外的 GPU 或较大内存。使用在线 API 时无此要求。

验证环境：

```bash
node --version   # 需要 >= 20.x
npm --version    # 需要 >= 10.x
```

---

## 2. 安装方式

Hermes 提供三种安装方式，按场景选择。

### 2.1 CLI 安装（推荐开发者使用）

最简洁的方式，适合终端用户与开发场景：

```bash
# npm 全局安装
npm install -g @nous/hermes

# 或使用 Homebrew（macOS）
brew install hermes

# 或使用 winget（Windows）
winget install NousResearch.Hermes
```

验证：

```bash
hermes --version
# Hermes Agent v1.x.x
```

### 2.2 Desktop 安装（推荐普通用户）

Hermes 提供原生桌面应用，支持 macOS / Windows / Linux：

1. 访问 [hermes.nousresearch.com/download](https://hermes.nousresearch.com/download)
2. 下载对应平台安装包（`.dmg` / `.exe` / `.AppImage`）
3. 安装后启动，进入图形化初始化向导

桌面版本优势：

- 图形化配置与技能管理
- 系统托盘常驻
- 文件拖放支持
- 与 IDE 集成

### 2.3 Docker 安装（推荐服务端部署）

适合服务器端无界面部署或隔离环境运行：

```bash
# 拉取官方镜像
docker pull nousresearch/hermes:latest

# 运行容器
docker run -d \
  --name hermes \
  -p 8080:8080 \
  -v ~/.hermes:/root/.hermes \
  -e HERMES_MODEL_API_KEY=$OPENAI_API_KEY \
  nousresearch/hermes:latest

# 使用 docker-compose
cat > docker-compose.yml <<'EOF'
version: '3.8'
services:
  hermes:
    image: nousresearch/hermes:latest
    ports:
      - "8080:8080"
    volumes:
      - ./hermes-data:/root/.hermes
    environment:
      - HERMES_MODEL_PROVIDER=openai
      - HERMES_MODEL_API_KEY=${OPENAI_API_KEY}
      - HERMES_LOG_LEVEL=info
    restart: unless-stopped
EOF

docker-compose up -d
```

---

## 3. 初始化向导：`hermes setup`

首次安装后，运行 `hermes setup` 进入交互式初始化向导：

```bash
hermes setup
```

向导会依次询问：

```
[1/6] 选择模型供应商
  > OpenAI (gpt-4o, gpt-4.1)
  > Anthropic (claude-3.7-sonnet, claude-3.5-haiku)
  > DeepSeek (deepseek-chat, deepseek-reasoner)
  > 本地 Ollama
  > 自定义 OpenAI 兼容端点

[2/6] 输入 API Key（或留空使用环境变量）
  API Key: ************

[3/6] 选择默认模型
  > gpt-4o (推荐通用场景)
  > gpt-4.1-mini (轻量任务)
  > ...

[4/6] 配置记忆存储后端
  > 本地文件（默认）
  > SQLite
  > 向量数据库（需配置连接）

[5/6] 启用哪些内置工具？
  [x] shell          [x] git
  [x] web-search     [x] file-io
  [x] mcp            [ ] browser

[6/6] 是否启用夜间审计与零信任安全？
  > 启用（推荐）
  > 暂不启用
```

完成向导后，Hermes 会生成配置文件 `~/.hermes/config.yaml`。

### 3.1 配置文件示例

```yaml
# ~/.hermes/config.yaml
model:
  provider: openai
  api_key: ${OPENAI_API_KEY}
  default: gpt-4o
  fallback: gpt-4o-mini

memory:
  backend: file
  path: ~/.hermes/memory
  vector:
    enabled: false

tools:
  enabled: [shell, git, web-search, file-io, mcp]
  shell:
    sandbox: true
    blacklist: [rm -rf, sudo, curl|wget]

security:
  zero_trust: true
  nightly_audit: true
  audit_indicators: all

logging:
  level: info
  file: ~/.hermes/logs/hermes.log
```

---

## 4. 从 OpenClaw 自动导入

Hermes 由 OpenClaw 升级而来，提供完善的迁移路径。运行 `hermes setup` 时，向导会自动检测本地 OpenClaw 安装并询问是否导入：

```
检测到 OpenClaw 安装（v0.x.x），是否自动导入？
  > 是，全部导入（推荐）
  > 选择性导入
  > 不导入，全新开始
```

### 4.1 自动导入的内容

| 类别 | 导入内容 | 迁移方式 |
|------|---------|---------|
| 设置 | config.yaml 中的配置 | 字段映射后写入 hermes/config.yaml |
| 记忆 | 短期/长期记忆条目 | 格式转换后写入新存储 |
| API Key | OpenAI / Anthropic 等 Key | 复制到 keychain 或环境变量 |
| 技能 | 已安装的技能 | 通过技能市场重新安装兼容版本 |
| 工具配置 | 自定义工具参数 | 直接复制 |

### 4.2 字段映射示例

OpenClaw 与 Hermes 的配置字段略有不同，Hermes 会自动转换：

```yaml
# OpenClaw 旧配置
model:
  name: gpt-4
  api_key: sk-xxx

# Hermes 新配置（自动转换）
model:
  provider: openai
  default: gpt-4
  api_key: ${OPENAI_API_KEY}  # 转为环境变量引用
```

### 4.3 迁移后验证

```bash
# 列出已导入的记忆
hermes memory list --limit 10

# 列出已安装技能
hermes skills list

# 测试 Agent
hermes ask "你好，介绍下你自己"
```

---

## 5. 五层架构解析

Hermes 采用五层架构，自顶向下为：

```
┌─────────────────────────────────┐
│  1. Interface Layer（界面层）   │  CLI / Desktop / API / SDK
├─────────────────────────────────┤
│  2. Core Layer（核心层）        │  Agent Runtime / 推理-行动循环
├─────────────────────────────────┤
│  3. Memory Layer（记忆层）     │  短期 / 长期 / 外部
├─────────────────────────────────┤
│  4. Skills Layer（技能层）     │  可安装的技能包
├─────────────────────────────────┤
│  5. Tools Layer（工具层）      │  47 个内置工具 + MCP
└─────────────────────────────────┘
```

### 5.1 Interface Layer（界面层）

提供与用户交互的入口，支持多种形态：

- **CLI**：`hermes` 命令，适合终端用户与脚本
- **Desktop**：图形桌面应用，适合普通用户
- **HTTP API**：RESTful 接口，适合服务端集成
- **SDK**：TypeScript / Python SDK，适合二次开发

### 5.2 Core Layer（核心层）

Agent 运行时核心，负责：

- 推理-行动循环（Reasoning-Action Loop）
- 上下文窗口管理
- 工具调用调度
- 安全策略执行
- 会话状态管理

### 5.3 Memory Layer（记忆层）

三层记忆系统，详见本系列第 2 篇：

- **Short-term（短期）**：当前会话上下文
- **Long-term（长期）**：跨会话持久化记忆
- **External（外部）**：接入向量数据库、知识库

### 5.4 Skills Layer（技能层）

可安装的技能包，封装领域知识与工作流。技能可通过技能市场安装，也可本地创建。

### 5.5 Tools Layer（工具层）

47 个内置工具 + MCP 协议支持，覆盖：

- 文件操作：read、write、glob、grep
- 系统操作：shell、process、env
- 网络：http、web-search、browser
- 开发：git、npm、docker
- 数据：sql、redis、vector-db
- MCP：所有 MCP 兼容工具

---

## 6. 多平台支持

Hermes 强调跨平台一致性，目前支持：

| 平台 | 支持程度 | 安装方式 |
|------|---------|---------|
| macOS（Intel/Apple Silicon） | 完整 | Homebrew / npm / Desktop |
| Windows 10+ | 完整 | winget / npm / Desktop |
| Ubuntu / Debian | 完整 | npm / Desktop / Docker |
| 其他 Linux（Fedora、Arch） | 社区支持 | npm / 源码 |
| Docker | 完整 | 镜像 |
| Kubernetes | 完整 | Helm Chart |

### 6.1 Kubernetes 部署示例

```yaml
# hermes-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hermes
spec:
  replicas: 1
  selector:
    matchLabels: { app: hermes }
  template:
    metadata:
      labels: { app: hermes }
    spec:
      containers:
      - name: hermes
        image: nousresearch/hermes:latest
        ports: [{ containerPort: 8080 }]
        env:
        - name: HERMES_MODEL_API_KEY
          valueFrom:
            secretKeyRef: { name: hermes-secrets, key: api-key }
        volumeMounts:
        - name: data
          mountPath: /root/.hermes
      volumes:
      - name: data
        persistentVolumeClaim: { claimName: hermes-pvc }
```

---

## 7. 模型配置

Hermes 支持多种模型接入方式。

### 7.1 在线模型

```yaml
model:
  provider: openai   # openai | anthropic | deepseek | custom
  api_key: ${OPENAI_API_KEY}
  default: gpt-4o
  fallback: gpt-4o-mini   # 主模型失败时回退
  temperature: 0.7
  max_tokens: 4096
```

### 7.2 本地模型（Ollama）

```yaml
model:
  provider: ollama
  base_url: http://localhost:11434
  default: llama3.1:8b
  fallback: qwen2.5:7b
```

启动 Ollama：

```bash
# 拉取模型
ollama pull llama3.1:8b

# 启动 Hermes
hermes start
```

### 7.3 自定义 OpenAI 兼容端点

适合接入自建网关或第三方代理：

```yaml
model:
  provider: custom
  base_url: https://your-gateway.com/v1
  api_key: ${CUSTOM_API_KEY}
  default: your-model-name
```

### 7.4 多模型路由

Hermes 支持根据任务类型自动路由到不同模型：

```yaml
model:
  routing:
    - task: code_generation
      model: deepseek-coder
    - task: reasoning
      model: deepseek-reasoner
    - task: general
      model: gpt-4o
    - task: simple
      model: gpt-4o-mini
```

---

## 8. OpenClaw 与 Hermes 对比

| 维度 | OpenClaw | Hermes |
|------|---------|--------|
| 定位 | 早期开源 Agent | 升级版智能体运行时 |
| 架构 | 三层 | 五层 + 三层记忆 |
| 工具数 | ~20 | 47 内置 + MCP |
| 技能系统 | 基础 | 完整市场 + 创建工具 |
| 安全模型 | 基础黑名单 | 零信任三层防御 |
| 多平台 | CLI 为主 | CLI + Desktop + Docker |
| 迁移路径 | - | 自动导入 OpenClaw |
| 文档 | 英文 | 30 万字中文指南（16 卷） |

### 8.1 何时选择 OpenClaw vs Hermes

- **OpenClaw**：维护中的旧项目，不愿迁移
- **Hermes**：新项目，或希望享受完整生态与持续更新

### 8.2 兼容性

Hermes 保留了对 OpenClaw 技能的兼容层：

```bash
# 启用 OpenClaw 兼容模式
hermes start --compat openclaw
```

---

## 9. 最佳实践小结

1. **优先 CLI 安装**：开发者首选，升级与脚本化最方便
2. **认真跑 setup 向导**：初始化决策影响后续所有使用
3. **从 OpenClaw 迁移要选全部导入**：Hermes 已做好字段映射
4. **生产环境用 Docker / K8s**：便于版本管理与横向扩展
5. **配置多模型路由**：复杂任务用强模型，简单任务用轻模型，优化成本
6. **启用零信任安全**：生产环境必备，详见本系列第 3 篇

---

## 进阶指引

- 下一篇：[技能系统与三层记忆系统详解](./02-skills-and-memory-system.md) — 深入 Skills 与 Memory
- 生态仓库：[Hermes 官方文档](https://hermes.nousresearch.com) | [OpenClaw GitHub](https://github.com/openclaw/openclaw)