> **[← 目录](README.md)** | 章节 04/12

# 第四部分 DeepSeek Harness：Agent 工程实验场

DeepSeek Harness（DSH）不是又一个"AI 编程工具"。它的官方定位是 **Agent Harness** —— 一个让你研究和构建 Agent 系统的开源框架。核心理念：**Everything is a plugin**。([DeepSeek][4])

## 4.1 核心理念：Everything is a Plugin

```
Agent = Model + Harness
```

每个 Agent 能力都是可替换的插件：

```
Agent
├── Model Plugin（模型适配器）
├── Tool Plugin（工具注册）
├── Skill Plugin（技能）
├── Session Plugin（会话管理）
├── Sandbox Plugin（沙箱执行）
├── Storage Plugin（存储）
├── Loop Plugin（Agent 循环）
├── Scheduler Plugin（调度）
└── UI Plugin（界面）
```

开发者可以在配置中选择、替换或扩展任何能力，**无需修改 DSH 源代码**。

## 4.2 四种运行模式详解

### Standard Mode — 完整 Coding Agent

```
文件编辑 + Shell + 文件搜索 + Web 搜索
+ Skills + 规划 + 目标
+ Subagents + Workflow
```

适合：正常的 AI 编程任务。

### Code Mode — 模型生成编排程序（最值得研究）

这是 DSH 最独特的能力。传统 Agent 模式：

```
LLM → Tool Call → LLM → Tool Call → LLM → Tool Call
```

Code Mode：

```
LLM → 生成 TypeScript 程序 → 程序执行：
  Search → Read → Analyze → Edit → Test
```

**价值**：一次模型调用生成完整的多步工具编排程序，减少大量中间往返。

```typescript
// DSH Code Mode 示例：模型生成的编排程序
import { search, read, edit, exec } from '@deepseek-ai/dsh-sdk';

async function refactorService() {
  // 1. 搜索所有引用
  const refs = await search('UserService', { type: 'references' });

  // 2. 读取相关文件
  const files = await Promise.all(refs.map(r => read(r.path)));

  // 3. 分析依赖关系
  const deps = analyzeDependencies(files);

  // 4. 批量编辑
  for (const change of deps.refactoringPlan) {
    await edit(change.path, change.newContent);
  }

  // 5. 运行测试验证
  const result = await exec('mvn test -pl user-service');
  return { success: result.exitCode === 0, changes: deps.refactoringPlan };
}
```

### Minimal Mode — 极简 Benchmark 环境

只提供 bash + str_replace_editor，非常适合：
- 模型能力 Benchmark
- Agent 能力研究
- 最小环境下的能力测试

### Creator Mode — 自定义 Agent Preset

可以检查当前运行时、在内存中测试 Cordis 插件、组合成新模式。这已经进入 **Agent Engineering** 领域。

## 4.3 Cordis 内核：插件化 Agent 架构

Cordis 是 DSH 的插件内核，负责管理插件的挂载、卸载和依赖。它基于服务和事件机制让插件协作：

```
Cordis Kernel
├── 服务注册/发现
├── 依赖注入
├── 事件总线
├── 生命周期管理
└── 配置热更新
```

## 4.4 Code Mode 深度解析

Code Mode 的核心是通过 **Code Mode SDK** 将所有工具暴露为可编程接口：

| SDK 方法 | 功能 |
|---------|------|
| `search(query, options)` | 搜索代码库 |
| `read(path)` | 读取文件 |
| `edit(path, content)` | 编辑文件 |
| `exec(command)` | 执行命令 |
| `write(path, content)` | 写入文件 |
| `webSearch(query)` | Web 搜索 |

**为什么 Code Mode 重要？**

对于需要多步工具调用的任务（如"找到所有使用旧 API 的文件并批量替换"），传统模式需要 10+ 次 LLM 往返，而 Code Mode 只需要 1 次模型调用生成程序。

## 4.5 Trajectory：可追溯的执行轨迹

所有模型看到的内容都记录在**只追加的会话日志**中：
- 系统提示
- 推理过程
- 工具调用和结果
- Subagent 调度
- 每次上下文注入

在 Trajectory 视图中，可以按来源检查这些记录。**Resume、Fork、Search、Replay** 都操作同一事件流。

---

---

[← 上一章: 03-Codex](03-Codex.md) | [目录](README.md) | [下一章: 05-Hermes(05-Hermes.md)](05-Hermes.md)
