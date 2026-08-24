> **[← 目录](README.md)** | 章节 05/12

# 第五部分 Hermes Agent：长期自主 Agent

Hermes Agent 是 Nous Research 开发的**自进化 AI Agent**。它的独特之处：内置学习循环，能从经验中创建 Skills，在使用中改进 Skills，并跨会话保持记忆。([Hermes Docs][6])

## 5.1 核心架构：CLI + TUI + Desktop + Messaging

```
Hermes Agent
├── CLI（命令行界面）
├── TUI（终端用户界面）
├── Desktop（Electron 桌面应用）
├── Messaging（多平台消息）
├── Memory（持久记忆）
├── Skills（可复用技能）
├── Subagents（子 Agent）
├── Terminal（终端执行）
├── Browser（浏览器操作）
└── Scheduler（任务调度）
```

## 5.2 持久记忆系统：MEMORY.md + USER.md

Hermes 的记忆系统由两个文件组成：

| 文件 | 用途 | 字符限制 |
|------|------|---------|
| **MEMORY.md** | Agent 的个人笔记：环境事实、约定、学到的东西 | 2,200 字符（~800 tokens） |
| **USER.md** | 用户画像：偏好、沟通风格、期望 | 1,375 字符（~500 tokens） |

存储在 `~/.hermes/memories/`，在每次会话开始时注入系统提示。

### 记忆条目示例

```
# 好的条目：信息密度高
用户运行 macOS 14，使用 Homebrew，有 Docker Desktop 和 Podman。
Shell: zsh with oh-my-zsh。编辑器: VS Code with Vim keybindings。

# 好的条目：具体可执行
项目 ~/code/api 使用 Go 1.22，sqlc 做 DB 查询，chi 路由。
测试命令: 'make test'。CI: GitHub Actions。

# 好的条目：带上下文的经验
staging 服务器 (10.0.1.50) 需要 SSH 端口 2222，不是 22。
密钥在 ~/.ssh/staging_ed25519。

# 差的条目：太模糊
用户有一个项目。

# 差的条目：太冗长
2026年1月5日，用户让我查看位于 ~/code/api 的项目，
我发现了它使用 Go 1.22...
```

### 记忆管理

```bash
# 查看当前记忆
/memory list

# 添加记忆
/memory add "项目使用 Spring Boot 3 + MyBatis-Plus"

# 替换记忆
/memory replace "dark mode" "用户在 VS Code 中偏好浅色模式"

# 删除记忆
/memory remove "过时的约定"

# 审批待定的记忆写入
/memory pending
/memory approve <id>
```

## 5.3 自进化 Skills：从经验中学习

这是 Hermes 最独特的能力。当 Agent 完成一个涉及 5+ 工具调用的复杂任务后，它会**自动创建可复用的 Skill**：

```
Agent 完成复杂任务
     ↓
分析任务模式
     ↓
提取可复用步骤
     ↓
生成 SKILL.md
     ↓
下次遇到类似任务时自动加载
```

### 8 种自进化 Skills 类型

1. **Error Recovery** - 从错误中学习恢复策略
2. **Workflow Optimization** - 优化重复工作流
3. **Tool Usage** - 改进工具使用方式
4. **Code Patterns** - 提取代码模式
5. **Debug Strategies** - 调试策略
6. **Research Methods** - 研究方法
7. **Communication** - 沟通风格
8. **Domain Knowledge** - 领域知识

### Skill 写入控制

```yaml
# ~/.hermes/config.yaml
skills:
  write_approval: false  # false = 自由写入 | true = 需要审批
```

当 `write_approval: true` 时，所有 Skill 写入都会被暂存等待审批：

```bash
/skills pending    # 列出暂存的 Skill 写入
/skills diff <id>  # 查看完整差异
/skills approve <id>  # 批准
/skills reject <id>   # 拒绝
```

## 5.4 Session Search：跨会话搜索

除了 MEMORY.md 和 USER.md，Agent 可以搜索过去的对话：

```bash
# 浏览过去的会话
hermes sessions list

# 搜索特定内容
hermes sessions search "订单超时取消的实现"
```

所有 CLI 和消息平台的会话都存储在 SQLite（`~/.hermes/state.db`）中，支持 FTS5 全文搜索。

| 特性 | 持久记忆 | Session Search |
|------|---------|---------------|
| 容量 | ~1,300 tokens | 无限 |
| 速度 | 即时（在系统提示中） | ~20ms FTS5 查询 |
| 成本 | 每次提示都有 token 成本 | 免费 |
| 用途 | 关键事实始终可用 | "上周我们讨论了 X？" |

## 5.5 Learning Journey：学习时间线

`/journey` 命令展示 Hermes 学习的一切——保存的 Skills 和记忆条目按时间排列：

```bash
# 查看学习时间线
hermes journey

# 动画回放
hermes journey --play

# 列出所有节点
hermes journey list

# 删除节点
hermes journey delete <node-id>

# 编辑节点
hermes journey edit <node-id>
```

## 5.6 外部记忆提供商集成

Hermes 内置 8 个外部记忆提供商插件：Honcho、OpenViking、Mem0、Hindsight、Holographic、RetainDB、ByteRover、Supermemory。

外部提供商**与内置记忆并行运行**，添加知识图谱、语义搜索、自动事实提取等能力：

```bash
hermes memory setup   # 选择提供商并配置
hermes memory status  # 检查活跃状态
```

---

---

[← 上一章: 04-DeepSeek-Harness]( prev_name ) | [目录](README.md) | [下一章: 06-Cursor( next_name )]( next_name )
