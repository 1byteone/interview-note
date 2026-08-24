> **[← 目录](README.md)** | 章节 03/12

# 第三部分 Codex（CLI + ChatGPT App）：终端与桌面双形态 Agent

截至 2026 年 7 月，Codex 已从独立 CLI 工具演进为 **ChatGPT 桌面应用的核心编码引擎**，同时保留 CLI 形态。OpenAI 官方将 Codex 定位为"Software Engineering Agent"，强调自主读取、修改、执行和验证代码的能力。([OpenAI][2])

## 3.1 Codex CLI：终端编码 Agent

Codex CLI 是一个基于 Rust 构建的轻量级开源编码 Agent，运行在终端中。它读取、修改并直接在你机器上执行代码。

### 三种自主程度模式

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| **Suggest** | 只建议修改，不自动执行 | 保守场景、生产相关 |
| **Auto Edit** | 自动修改文件，执行前确认 | 日常开发 |
| **Full Auto** | 自主读取、修改、执行全部操作 | 长时间自动任务 |

### 安装与配置

```bash
# 安装
npm install -g @openai/codex

# 基础使用
codex "分析这个 Spring Boot 项目的启动失败原因并修复"

# Full Auto 模式（谨慎使用）
codex --full-auto "修复所有失败的测试"

# 指定模型
codex --model o3 "重构这个 Service 层"
```

### 权限控制（2026 最新）

Codex CLI 现在支持**逐终端权限配置**，可以自动批准安全操作，同时阻止 push、force-push、merge 和 branch delete：

```json
// codex.config.json
{
  "permissions": {
    "allow": ["bash(mvn *)", "bash(git diff*)", "bash(git status*)", "read", "edit"],
    "deny": ["bash(git push*)", "bash(rm -rf*)", "bash(git force-push*)"]
  }
}
```

## 3.2 ChatGPT App：Codex 合并后的桌面工作台

2026 年 7 月，Codex 应用正式合并到 ChatGPT 桌面应用。Codex 仍然是同一个强大的编码 Agent，但现在拥有更多能力：

- **Computer Use**：通过浏览器反馈
- **Memory**：跨会话记忆
- **Automations**：后台定时任务
- **Plugins**：角色专用插件（6 种新角色插件于 2026 年 6 月发布）
- **文件预览**：直接在界面中查看代码变更

### Preview 系统（2026 新特性）

Codex 现在可以生成 **2-4 种不同的实现方案**，让你在执行前选择最佳方案。这对架构决策非常有价值。

## 3.3 Workspace Agents：企业级自动化

2026 年 4 月，OpenAI 发布了 Workspace Agents —— Codex 驱动的共享 Agent，可以在团队间自动化复杂工作流：

- **创建**：定义 Agent 的目标、工具和权限
- **共享**：发布给团队成员使用
- **调度**：设置定时执行
- **监控**：查看执行历史和结果

```
Workspace Agent 用例：
├── Issue Triage Agent：自动分类 GitHub Issue
├── Code Review Agent：自动审查 PR
├── Test Generation Agent：为新代码生成测试
├── Documentation Agent：自动更新 API 文档
└── Deployment Agent：自动化部署流程
```

## 3.4 Automations：定时任务与后台 Agent

Codex 的 Automations 功能允许 Agent 在后台按计划运行：

```bash
# 通过 ChatGPT 创建定时任务
"每天早上 9 点检查所有服务的健康状态，
 如果有异常则创建 GitHub Issue 并通知 Slack"

# 通过 CLI 创建
codex automation create \
  --name "daily-health-check" \
  --schedule "0 9 * * *" \
  --prompt "检查所有微服务健康状态，异常则报警"
```

## 3.5 实战：Spring Boot 项目自动修复循环

```
# 场景：CI 测试失败，自动修复

codex --full-auto "
这个 Spring Boot 项目在 CI 中有 3 个测试失败。

要求：
1. 运行 mvn test 找到所有失败的测试
2. 分析每个失败的根因
3. 修复代码（不是修改测试）
4. 重新运行测试验证
5. 如果还有失败，继续修复循环
6. 最后创建一个 commit，包含所有修复

注意：不要修改测试的预期行为，只修复实现代码。
"
```

---

---

[← 上一章: 02-Claude-Code](02-Claude-Code.md) | [目录](README.md) | [下一章: 04-DeepSeek-Harness(04-DeepSeek-Harness.md)](04-DeepSeek-Harness.md)
