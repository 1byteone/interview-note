# Cordis 运行时机制深度解析

> **生态**: E03 · DSH/Harness | **等级**: 高阶 | **前置要求**: 熟悉 DSH 插件基础（见本系列第 2 篇）

Cordis 是 DSH 的底座，也是整个 Harness 生态的"内核中的内核"。它源自 [cordiverse/cordis](https://github.com/cordiverse/cordis) 开源项目，是一个专为可扩展 Agent 系统设计的元框架（meta-framework）。Cordis 以"插件即效应（plugin as effect）"为核心理念，提供了时空可组合性、可逆效应、响应式依赖等独到机制。

本教程深入 Cordis 的内部机制，剖析其插件框架、时空可组合性、可逆效应、响应式依赖、上下文生命周期，以及 DSH 如何在其上构建 453K 行规模的 Agent 运行时。

---

## 1. Cordis 插件框架概览

### 1.1 设计目标

Cordis 解决了传统插件框架的几个痛点：

| 传统痛点 | Cordis 方案 |
|---------|------------|
| 插件卸载困难，常留下残留状态 | 可逆效应：所有副作用自动撤销 |
| 依赖关系手工管理，易出错 | 响应式依赖：自动解析与编排 |
| 插件组合时上下文冲突 | 时空可组合性：上下文隔离 |
| 测试困难，副作用难隔离 | 效应模式：可观察、可回滚 |

### 1.2 核心抽象

Cordis 的核心抽象只有三个：

1. **Context（上下文）**：插件与运行时的交互入口，承载效应、依赖、配置
2. **Plugin（插件）**：一个 `apply(ctx)` 函数，描述插件对上下文产生的效应
3. **Effect（效应）**：可逆的副作用单元，由插件注册、由运行时追踪

```typescript
// 插件的最简形态
export const name = 'my-plugin';
export function apply(ctx: Context) {
  // 在 ctx 上注册效应
}
```

### 1.3 极简示例

下面是一个完整的 Cordis 插件，它每秒打印一次心跳：

```typescript
import { Context } from 'cordis';

export const name = 'heartbeat';

export function apply(ctx: Context) {
  const timer = setInterval(() => {
    ctx.logger.info('heartbeat');
  }, 1000);

  // 注册效应：卸载时清理 timer
  ctx.effect(() => {
    clearInterval(timer);
    ctx.logger.info('heartbeat stopped');
  });
}
```

Cordis 会自动追踪 `setInterval` 的副作用，并在插件卸载时调用清理函数。这是 Cordis 与传统插件框架最显著的区别。

---

## 2. 时空可组合性（Spatiotemporal Composability）

### 2.1 概念

**时空可组合性**指插件可以在两个维度上自由组合：

- **空间维度**：多个插件实例可以并存，互不干扰
- **时间维度**：插件可以动态加载和卸载，运行时形态可演进

### 2.2 空间隔离

每个插件实例拥有独立的上下文作用域：

```typescript
// 同时为两个用户运行同一插件，状态互不干扰
const ctx1 = app.createContext({ user: 'alice' });
const ctx2 = app.createContext({ user: 'bob' });

ctx1.plugin(heartbeat);
ctx2.plugin(heartbeat);
```

两个 heartbeat 实例各自维护自己的 timer 与日志，不会相互污染。

### 2.3 时间演进

Cordis 支持运行时动态加载/卸载插件，这是传统插件框架难以做到的：

```typescript
// 运行时加载插件
const handle = app.plugin(dynamicPlugin);

// 一段时间后卸载
await handle.unload();
// 此时该插件注册的所有效应被自动撤销
```

### 2.4 应用场景

时空可组合性对 Agent 系统至关重要：

- **多会话隔离**：每个用户会话独立加载插件，状态不串扰
- **A/B 测试**：同时运行两个版本的插件，对比效果
- **热修复**：发现问题插件时，立即卸载而不重启整个 Agent
- **按需加载**：仅在用户访问特定功能时才加载对应插件

---

## 3. 可逆效应机制

### 3.1 什么是可逆效应

**可逆效应（Reversible Effect）** 是 Cordis 的灵魂。所有副作用——事件监听、定时器、文件句柄、网络连接——都被 Cordis 追踪，并在插件卸载时自动撤销。

### 3.2 效应注册

通过 `ctx.effect()` 注册清理逻辑：

```typescript
export function apply(ctx: Context) {
  // 1. 事件订阅
  ctx.on('message', handler);
  ctx.effect(() => ctx.off('message', handler));

  // 2. 定时器
  const timer = setInterval(tick, 1000);
  ctx.effect(() => clearInterval(timer));

  // 3. 文件监听
  const watcher = fs.watch('./data');
  ctx.effect(() => watcher.close());

  // 4. 数据库连接
  const conn = await db.connect();
  ctx.effect(async () => { await conn.close(); });
}
```

### 3.3 自动效应

Cordis 还会自动追踪一些常见副作用，无需手动注册：

```typescript
export function apply(ctx: Context) {
  // 这些都会被自动追踪
  ctx.on('ready', handler);        // 事件监听
  ctx.provide('svc', service);     // 服务注册
  ctx.command('foo', handler);     // 命令注册
}
```

### 3.4 实现原理

Cordis 在内部维护一个效应栈（Effect Stack）：

```
插件 A 启动
  ├─ 注册 effect-1（事件订阅）
  ├─ 注册 effect-2（定时器）
  └─ 注册 effect-3（文件监听）
插件 A 卸载时
  ├─ 执行 effect-3 的清理
  ├─ 执行 effect-2 的清理
  └─ 执行 effect-1 的清理
```

卸载时按 **LIFO**（后进先出）顺序执行清理，保证依赖正确性：晚注册的效应可能依赖早注册的资源，必须先释放。

---

## 4. 响应式依赖解析

### 4.1 静态依赖

插件可以通过 `ctx.using` 声明依赖的服务，Cordis 会自动解析并编排启动顺序：

```typescript
export function apply(ctx: Context) {
  const [model, memory] = ctx.using(['model', 'memory'], (model, memory) => {
    // 只有当 model 与 memory 都就绪后，此回调才执行
    ctx.logger.info('model & memory ready');
  });
}
```

### 4.2 循环依赖处理

Cordis 能检测循环依赖并报错，避免无限递归：

```
Error: Circular dependency detected
  plugin-a → plugin-b → plugin-a
```

### 4.3 动态依赖

依赖可以在运行时变化，Cordis 会自动重新编排：

```typescript
ctx.on('config-changed', () => {
  // 配置变化后，Cordis 会卸载依赖旧配置的插件
  // 加载依赖新配置的插件
});
```

### 4.4 依赖图优化

Cordis 维护一个依赖图（DAG），按拓扑序启动插件：

```
启动顺序（拓扑序）:
1. memory-vector（无依赖）
2. model-deepseek（无依赖）
3. agent-core（依赖 model + memory）
4. tool-shell（依赖 agent-core）
5. ui-cli（依赖 agent-core）
```

---

## 5. 上下文生命周期

### 5.1 完整生命周期

Cordis 上下文有四个阶段：

```
create → start → stop → dispose
```

| 阶段 | 触发 | 语义 |
|------|------|------|
| create | `new Context()` 或 `app.createContext()` | 创建作用域，注入配置 |
| start | 依赖就绪，第一次访问时 | 初始化资源，启动效应 |
| stop | 显式调用 `ctx.stop()` | 暂停效应，但保留状态 |
| dispose | `ctx.dispose()` 或父上下文销毁 | 执行所有清理效应，释放资源 |

### 5.2 阶段示例

```typescript
const app = new Context({ /* 配置 */ });

// 1. create：创建上下文
const ctx = app.createContext({ user: 'alice' });

// 2. start：通过 ctx.plugin 加载插件触发
ctx.plugin(myPlugin);
// 此时会触发 myPlugin 的 apply 函数，注册效应

// 3. stop：暂停效应但保留状态
await ctx.stop();
// 所有 timer 暂停，事件监听暂时失效，但配置与状态保留

// 4. 重新 start：恢复
await ctx.start();

// 5. dispose：彻底销毁
await ctx.dispose();
// 执行所有清理效应，释放资源，无法恢复
```

### 5.3 上下文嵌套

Cordis 支持上下文嵌套，子上下文销毁时父上下文不受影响：

```typescript
// 父上下文：整个 Agent 应用
const app = new Context();

// 子上下文：每个用户会话
const session1 = app.createContext({ session: 's1' });
const session2 = app.createContext({ session: 's2' });

// 销毁某个会话
await session1.dispose();
// session2 不受影响，app 也不受影响
```

### 5.4 生命周期事件

```typescript
export function apply(ctx: Context) {
  ctx.on('ready', () => console.log('插件已就绪'));
  ctx.on('dispose', () => console.log('插件已销毁'));
  ctx.on('error', (err) => console.error('错误:', err));
}
```

---

## 6. DSH 与 Cordis 集成

### 6.1 DSH 在 Cordis 之上构建的层

DSH 仓库代码量约 **453K 行**（含测试与文档），这些代码分布在 Cordis 之上的几个层次：

```
┌────────────────────────────────────┐
│  Agent 应用插件（用户编写）         │  ~10K
├────────────────────────────────────┤
│  Agent 运行时能力插件              │
│  - 模型适配（DeepSeek/OpenAI 等）   │  ~80K
│  - 工具集（Shell/Git/MCP/Web 等）   │  ~150K
│  - 记忆系统（向量/文件/外部）        │  ~60K
│  - 安全策略                         │  ~40K
│  - 可观测性                         │  ~30K
├────────────────────────────────────┤
│  DSH 核心：组装插件、提供 API       │  ~80K
├────────────────────────────────────┤
│  Cordis 插件内核                    │  ~3K
└────────────────────────────────────┘
```

### 6.2 DSH 对 Cordis 的扩展

DSH 在 Cordis 基础上添加了 Agent 领域特有的能力：

```typescript
// DSH 扩展 Context，提供 Agent 特有 API
import { Context } from 'cordis';

declare module 'cordis' {
  interface Context {
    // Agent 上下文
    agent: AgentContext;
    // 模型调用
    model: ModelService;
    // 工具调用
    tools: ToolRegistry;
    // 记忆系统
    memory: MemoryService;
    // 安全策略
    security: SecurityService;
  }
}
```

### 6.3 DSH 插件示例

```typescript
// 一个 DSH 插件，同时利用了 Cordis 与 DSH 的 API
import { Context } from 'cordis';

export const name = 'weekly-report';

export function apply(ctx: Context) {
  const { agent, model, memory } = ctx;

  // 注册 Agent 处理器
  agent.on('command:weekly-report', async (session) => {
    const history = await memory.recall({
      query: session.text,
      topK: 5,
    });

    const prompt = agent.buildPrompt({
      task: '生成周报',
      context: history,
      input: session.text,
    });

    const result = await model.generate(prompt);
    await session.reply(result);
  });

  // 声明依赖
  ctx.using(['model', 'memory'], () => {
    ctx.logger.info('weekly-report ready');
  });
}
```

---

## 7. 性能考虑

### 7.1 效应追踪开销

Cordis 的效应追踪是有成本的。每次 `ctx.effect` 调用都会：

- 在效应栈中创建一个条目（约 100 字节）
- 注册清理函数引用

对于高频效应（如每次请求都注册），应考虑使用持久效应或共享效应。

### 7.2 插件加载性能

DSH 的 453K 行代码包含约 50+ 个官方插件，启动时全部加载会较慢。优化策略：

```typescript
// 按需加载：只加载必要插件
const dsh = new DSH({
  mode: 'minimal',
  plugins: [
    '@deepseek/model-deepseek',
    '@deepseek/tool-shell',
    // 不要无脑加载全部
  ],
});
```

### 7.3 上下文创建开销

每个上下文创建都有一定开销，频繁创建/销毁上下文会影响性能。对于短会话场景，考虑使用上下文池：

```typescript
// 上下文池示例
const pool = new ContextPool({
  factory: () => app.createContext({ /* 默认配置 */ }),
  max: 100,
  idleTimeout: 30_000,
});

const ctx = await pool.acquire();
try {
  await ctx.plugin(tempPlugin);
  // ... 执行任务
} finally {
  await pool.release(ctx);  // 复用而非销毁
}
```

### 7.4 内存管理

Cordis 的效应栈会持续增长，长运行的 Agent 需要定期清理：

```typescript
// 定期清理已销毁上下文的残留
setInterval(() => {
  app.gc();  // 显式触发垃圾回收
}, 60_000);
```

---

## 8. 调试技巧

### 8.1 查看效应栈

```typescript
// 启用 debug 模式
DEBUG=cordis:* npx dsh start

// 输出示例
cordis:effect register effect-1 (event listener)
cordis:effect register effect-2 (timer)
cordis:effect dispose effect-2
cordis:effect dispose effect-1
```

### 8.2 依赖图可视化

```bash
npx dsh plugins --graph
```

输出依赖关系图（Mermaid 格式），便于排查依赖问题。

### 8.3 内存泄漏排查

如果发现内存持续增长，可用 Cordis 的诊断工具：

```typescript
const stats = app.diagnose();
console.log('Active contexts:', stats.contexts);
console.log('Active effects:', stats.effects);
console.log('Top effect sources:', stats.topSources);
```

---

## 9. 最佳实践小结

1. **拥抱可逆效应**：所有副作用都用 `ctx.effect` 注册清理，保证插件可卸载
2. **声明式依赖**：用 `ctx.using` 让 Cordis 编排启动顺序，避免手工等待
3. **作用域隔离**：为不同用户会话创建独立上下文，避免状态串扰
4. **按需加载插件**：只加载必要插件，减少启动时间与内存占用
5. **定期诊断**：长运行的 Agent 要定期检查效应栈与上下文数
6. **从 Cordis 源码学起**：只有约 3K 行，是理解 DSH 内核的最短路径

---

## 进阶指引

- 上一篇：[DeepSeek Harness 插件开发入门](./02-dsh-plugin-development.md)
- 下一篇：[Hermes Agent 安装部署与架构解析](../e04-hermes-openclaw/01-installation-and-architecture.md)
- 生态仓库：[Cordis GitHub](https://github.com/cordiverse/cordis) | [DSH 文档](https://github.com/deepseek-ai/DeepSeek-Harness)