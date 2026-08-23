# 多 Agent 并行工作树：Git Worktrees 实战

> **生态**: E02 · Codex 生态 | **等级**: 高级 | **前置要求**: 熟悉 Codex CLI 基础命令与 AGENTS.md 配置（建议先阅读 [01-quickstart-and-commands.md](./01-quickstart-and-commands.md)、[02-agents-md-and-sandbox.md](./02-agents-md-and-sandbox.md)、[03-advanced-workflows.md](./03-advanced-workflows.md)）

单个 Agent 在大型仓库中串行推进时，常常遇到瓶颈：一边改后端接口、一边写前端对接、一边调整数据库迁移，三件事彼此独立却只能排队执行。Codex CLI 借助 **Git Worktrees（Git 工作树）** 把这类可并行任务真正并发起来——多个 Agent 各自占据一个隔离的工作目录，共享同一个 `.git` 仓库，零拷贝、零文件冲突，合并时再回归主分支。

本教程系统讲解 Worktree 模式下的多 Agent 编排：从概念、搭建、角色配置，到 Coordinator/Worker 模式、迁移基准、冲突规避与 CI/CD 集成，并对比 Claude Code 的工作树实现。

---

## 1. 为什么需要多 Agent 并行

串行 Agent 在以下场景效率低下：

| 场景 | 串行痛点 | 并行收益 |
|------|----------|----------|
| **微服务拆分** | 逐个服务改写，等待链长 | N 个服务同时改造，时间压缩到 1/N |
| **框架迁移** | JS→TS、JUnit4→JUnit5 逐文件推进 | 按模块切分，多 Agent 同步迁移 |
| **前后端联调** | 后端先行、前端等待 | 接口契约固定后双向并行 |
| **大规模重构** | 单 Agent 上下文窗口易溢出 | 每个 Worktree 只关注子任务，上下文更聚焦 |

实测基准：**3 个并行 Agent 完成一次 Spring Boot 2 → 3 迁移任务，相比单 Agent 串行可获得约 2.5 倍的吞吐提升**。收益来自三方面——CPU 多核并行、上下文窗口独立、等待 I/O 时其他 Agent 继续推进。

---

## 2. Git Worktrees 核心概念

Git Worktree 允许同一仓库检出多个工作目录，每个目录对应独立分支：

```
repo/                       ← 主工作目录（master 分支）
├── .git/                   ← 唯一的 Git 仓库元数据
├── src/...
└── ../
    ├── .worktrees/
    │   ├── feature-a/      ← Worktree A（分支 feature-a）
    │   ├── feature-b/      ← Worktree B（分支 feature-b）
    │   └── feature-c/      ← Worktree C（分支 feature-c）
```

关键特性：

- **共享对象库**：所有 Worktree 共用 `.git/objects`，零冗余拷贝
- **分支独立**：每个 Worktree 各自检出不同分支，互不干扰
- **零文件冲突**：物理目录隔离，不可能在文件层面互相覆盖
- **轻量创建**：`git worktree add` 比完整 clone 快一个数量级

与"复制文件夹"相比，Worktree 节省磁盘空间、保持 Git 历史一致、分支管理统一可控。

---

## 3. 为 Codex 搭建 Worktree 环境

### 3.1 启用多 Agent

在 `~/.codex/config.toml` 中开启实验特性：

```toml
[experimental]
multi_agent = true
```

### 3.2 手动创建 Worktree（推荐做法）

虽然 Codex 提供 `--worktree` 选项，但建议用原生 Git 命令显式管理，便于审计：

```bash
# 进入主仓库
cd /workspace/my-service

# 为主分支创建基于当前 HEAD 的三个工作树
git worktree add ../.worktrees/backend-api    -b feat/backend-api
git worktree add ../.worktrees/frontend-pages -b feat/frontend-pages
git worktree add ../.worktrees/db-migration   -b feat/db-migration

# 查看所有工作树
git worktree list
```

输出示例：

```
/workspace/my-service                    3f2a1c2 [master]
/workspace/.worktrees/backend-api        3f2a1c2 [feat/backend-api]
/workspace/.worktrees/frontend-pages     3f2a1c2 [feat/frontend-pages]
/workspace/.worktrees/db-migration       3f2a1c2 [feat/db-migration]
```

### 3.3 每个 Worktree 的初始化脚本

各 Worktree 可能需要独立依赖安装，Codex CLI 的 `--worktree` 不会自动跑 `npm install`。建议在仓库根放一个 `scripts/worktree-setup.sh`：

```bash
#!/bin/bash
# scripts/worktree-setup.sh —— 在新 Worktree 中执行
set -e

echo "[1/3] 安装依赖..."
npm ci --no-audit --prefer-offline

echo "[2/3] 拷贝环境配置..."
cp ../../.env.example .env 2>/dev/null || true

echo "[3/3] 校验工具链..."
npm run typecheck
```

在每个 Worktree 中执行一次即可：

```bash
cd /workspace/.worktrees/backend-api && bash ../../my-service/scripts/worktree-setup.sh
```

### 3.4 启动各 Worktree 的 Agent

```bash
# 终端 1
cd /workspace/.worktrees/backend-api
codex -p "实现订单 CRUD 接口与状态流转，遵循 AGENTS.md 中的 Controller/Service 分层"

# 终端 2
cd /workspace/.worktrees/frontend-pages
codex -p "实现订单列表与详情页，对接 /api/orders，使用 React 18 + TypeScript"

# 终端 3
cd /workspace/.worktrees/db-migration
codex -p "创建 orders 表迁移脚本，包含索引与外键约束"
```

三个 Agent 物理隔离，互不感知，各自向自己的分支提交。

---

## 4. config.toml 角色分配

多 Agent 场景下，可以为每个 Worktree 配置独立的 `config.toml`，让 Agent 扮演不同角色。Codex CLI 加载顺序是 `~/.codex/config.toml` → `<worktree>/.codex/config.toml`，后者覆盖前者。

**Worktree A: 后端 API 角色**

```toml
# /workspace/.worktrees/backend-api/.codex/config.toml
[agent]
role = "backend-engineer"
focus = "REST API, 事务边界, 异常处理"

[approval]
mode = "auto"

[sandbox]
allowed_paths = ["/workspace/.worktrees/backend-api/src"]
blocked_paths = ["/workspace/.worktrees/frontend-pages"]
```

**Worktree B: 前端页面角色**

```toml
# /workspace/.worktrees/frontend-pages/.codex/config.toml
[agent]
role = "frontend-engineer"
focus = "React 组件, 类型安全, 视觉还原"

[approval]
mode = "auto"

[sandbox]
allowed_paths = ["/workspace/.worktrees/frontend-pages/src"]
blocked_paths = ["/workspace/.worktrees/backend-api"]
```

通过 `sandbox.blocked_paths` 互相封锁写入路径，从配置层面杜绝跨 Worktree 的越权修改。

---

## 5. Coordinator/Worker 编排模式

复杂任务常需要"一个总指挥 + 多个执行者"的结构。Codex 通过 **OpenAI Agents SDK** 实现编排，Coordinator 负责任务拆分与结果合并，Worker 在各 Worktree 内执行子任务。

### 5.1 模式结构

```
                ┌──────────────┐
                │ Coordinator  │  ← 主 Agent，读 spec、派任务、收结果
                └──────┬───────┘
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
   ┌─────────┐    ┌─────────┐    ┌─────────┐
   │ Worker A │    │ Worker B │    │ Worker C │
   │ backend  │    │ frontend │    │ db migr  │
   └─────────┘    └─────────┘    └─────────┘
        │              │              │
        ▼              ▼              ▼
   feat/backend    feat/frontend   feat/db-mig
```

### 5.2 用 Agents SDK 编排（Python 片段）

```python
from agents import Agent, Runner, WebSearchTool

coordinator = Agent(
    name="coordinator",
    instructions=(
        "你是一个迁移总指挥。阅读 docs/migration-spec.md，"
        "把任务拆成 backend / frontend / db-migration 三类子任务，"
        "分别派给对应 Worker。每个 Worker 完成后收集其 PR 链接与"
        "测试报告，汇总成最终验收文档。"
    ),
)

backend_worker = Agent(
    name="backend-worker",
    instructions="在 feat/backend-api 分支实现订单 CRUD，遵循 AGENTS.md。",
)

# Runner 并行调度各 Worker，等待全部完成后合并
result = Runner.run_sync(coordinator, input="启动订单模块迁移")
```

### 5.3 可审计的交接

每次 Worker 完成会输出 trace（包含工具调用、文件改动、提交 SHA），Coordinator 据此判断是否回滚或继续。这类 trace 默认存放在 `~/.codex/sessions/<worktree>/`，便于事后回放。

---

## 6. 三 Agent 并行迁移基准

以一次真实的 **Spring Boot 2.7 → 3.3 + Java 17 迁移** 为例：

| 阶段 | 串行单 Agent | 3 Agent 并行 |
|------|--------------|--------------|
| 依赖与配置升级 | 28 min | 12 min（Worker A） |
| javax→jakarta 替换 | 35 min | 14 min（Worker B） |
| 测试套件适配 | 22 min | 10 min（Worker C） |
| 合并与回归 | 18 min | 20 min（冲突略增） |
| **合计** | **103 min** | **~56 min** |

加速比约 **1.84×**（理论 2.5× 受合并阶段拖累）。注意：并行度越高，合并阶段的协调成本越大——这正是下一节要解决的问题。

---

## 7. 冲突规避策略

Worktree 本身不会产生"文件级"冲突，但合并到 `master` 时仍会撞车。规避要点：

1. **按目录切分**：约定每个 Worker 只动 `src/<module>/` 下的文件，spec 阶段就划清边界
2. **共享文件提前冻结**：`AGENTS.md`、`openapi.yaml`、`pnpm-lock.yaml` 等共享文件在并行期间冻结，任何变更必须走 Coordinator 统一处理
3. **生成物不入仓**：锁文件、生成代码由合并阶段统一重新生成，避免各 Worktree 各自提交
4. **小步合并**：Worker 完成后立即合并回 `master` 并推送，其他 Worktree 定期 `git rebase master` 同步
5. **冲突检测脚本**：

```bash
#!/bin/bash
# scripts/detect-conflicts.sh
set -e
for wt in .worktrees/*; do
  branch=$(git -C "$wt" symbolic-ref --short HEAD)
  if ! git merge-tree "$(git merge-base master "$branch")" master "$branch" >/dev/null 2>&1; then
    echo "⚠️  $branch 与 master 存在潜在冲突"
  fi
done
```

---

## 8. 审查与合并工作流

多 Agent 产出必须经过统一审查才能入主分支：

```bash
# 1. Coordinator 拉取所有 Worktree 的最新提交
git fetch ../.worktrees/backend-api    feat/backend-api
git fetch ../.worktrees/frontend-pages feat/frontend-pages
git fetch ../.worktrees/db-migration   feat/db-migration

# 2. 逐个用 Codex read-only 审查
for br in feat/backend-api feat/frontend-pages feat/db-migration; do
  echo "=== Reviewing $br ==="
  git diff master...$br | codex exec -p "审查变更，输出严重问题清单" --mode read-only
done

# 3. 人工或自动合并（按依赖顺序：db → backend → frontend）
git merge --no-ff feat/db-migration
git merge --no-ff feat/backend-api
git merge --no-ff feat/frontend-pages

# 4. 全量回归
codex exec -p "运行全部单元测试与集成测试，修复失败用例" --mode auto

# 5. 清理 Worktree
git worktree remove ../.worktrees/backend-api
git worktree remove ../.worktrees/frontend-pages
git worktree remove ../.worktrees/db-migration
```

---

## 9. 注意事项

| 陷阱 | 说明 | 对策 |
|------|------|------|
| **磁盘占用** | 每个 Worktree 是完整 checkout，大型仓库 N 份会占满盘 | 用 `git worktree prune` 清理失效 Worktree；CI 用临时目录 |
| **分支管理** | Worktree 删除后分支可能成为孤儿 | 删除 Worktree 时同步 `git branch -d <branch>` |
| **锁文件冲突** | `package-lock.json` / `pnpm-lock.yaml` 多 Worker 同时改 | 锁文件统一在合并阶段重生，Worker 内禁止提交 |
| **IDE 索引膨胀** | IDE 为每个 Worktree 建索引，内存翻倍 | 关闭非活跃 Worktree 的 IDE 窗口，或用 `.idea/ignore` |
| **凭证泄漏** | 各 Worktree 各自 `.env` 易遗漏 | 用 `.env.worktree.example` 模板，禁止真实凭证入仓 |

---

## 10. 与 CI/CD 集成

多 Agent Worktree 天然适配 CI 流水线。以下 GitHub Actions 片段在 PR 触发时拉起三个 Worker 并行处理：

```yaml
name: Codex Parallel Migration
on:
  pull_request:
    types: [opened, synchronize]

jobs:
  spawn-workers:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        worker: [backend, frontend, db-migration]
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: actions/setup-node@v4
        with: { node-version: '22' }
      - run: npm install -g @openai/codex
      - name: Create worktree
        run: |
          git worktree add ../wt-${{ matrix.worker }} -b ci/${{ matrix.worker }}-${{ github.sha }}
      - name: Run worker
        run: |
          cd ../wt-${{ matrix.worker }}
          codex exec -p "按 spec.md 完成 ${{ matrix.worker }} 模块迁移" \
            --mode auto
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: wt-${{ matrix.worker }}
          path: ../wt-${{ matrix.worker }}

  merge-review:
    needs: spawn-workers
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: npm install -g @openai/codex
      - name: Aggregate & review
        run: |
          # 下载三个 artifact 后统一审查
          codex exec -p "审查三路并行迁移结果，输出验收报告" \
            --mode read-only
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
```

---

## 11. 与 Claude Code 工作树方案对比

Codex 与 Claude Code 都支持 Worktree 模式，但实现哲学不同：

| 维度 | Codex CLI | Claude Code |
|------|-----------|-------------|
| **创建方式** | `git worktree add` + `codex --worktree` | 内置 `EnterWorktree` 工具，会话内一键创建 |
| **配置隔离** | 每 Worktree 独立 `.codex/config.toml` | 每 Worktree 独立 `.claude/settings.json` + 子 Agent 类型 |
| **编排框架** | OpenAI Agents SDK | Claude Agent SDK + Subagent |
| **审批粒度** | 三档模式（auto / read-only / full-access） | 基于权限白名单的工具级控制 |
| **交接可审计性** | Session trace 存于 `~/.codex/sessions/` | 子 Agent 最终消息回传主 Agent，可追溯 |
| **适用场景** | 偏"多终端手动编排"，工程师掌控节奏 | 偏"会话内自动派发"，主 Agent 调度子 Agent |

选型建议：

- **任务边界清晰、需要人工审查每步** → Codex + 手动 Worktree
- **任务可全自动拆分、Coordinator 主导** → Claude Code + Subagent
- **混合模式**：Codex 负责实现、Claude Code 负责审查与合并，两者各取所长

---

## 12. 最佳实践速查

1. **先 spec 后并行**：无规格说明就启动多 Agent 等于盲飞，先写 `spec.md` 划清模块边界
2. **小而专的 Worker**：单个 Worker 任务控制在 2 小时内、改动文件不超过 30 个
3. **Coordinator 不写代码**：Coordinator 只拆任务、收结果、做审查，避免越权改文件
4. **频繁 rebase**：每完成一个 Worker 立即合并，其余 Worktree `git rebase master` 同步
5. **生成物不入仓**：锁文件、构建产物统一在合并阶段生成
6. **定期清理**：`git worktree prune` + `git branch --merged | xargs git branch -d`
7. **CI 中复用**：把本教程的脚本沉淀为可复用 Action，新仓库一键接入

---

## 参考链接

- [Codex CLI 快速上手与命令指南](./01-quickstart-and-commands.md)
- [AGENTS.md 配置与沙盒安全模型](./02-agents-md-and-sandbox.md)
- [高级工作流：MCP 集成与多 Agent 编排](./03-advanced-workflows.md)
- [Codex CLI GitHub](https://github.com/openai/codex)
- [Git Worktree 官方文档](https://git-scm.com/docs/git-worktree)
- [OpenAI Agents SDK](https://github.com/openai/openai-agents-python)
- [freestylefly/CodexGuide](https://github.com/freestylefly/CodexGuide)
