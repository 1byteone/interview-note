# DeepSeek Harness 插件开发入门

> **生态**: E03 · DSH/Harness | **等级**: 进阶 | **前置要求**: 熟悉 Node.js/npm 与 Harness 基本概念（见本系列第 1 篇）

DeepSeek Harness（DSH）是 DeepSeek 推出的开源 Agent 运行时，它把 Harness 工程范式落地为一个实际可用的产品。DSH 最鲜明的特色是 **"Everything is a Plugin"**（万物皆插件）——整个 Agent 运行时由可插拔的插件构成，甚至核心能力本身也是插件。

本教程从 DSH 架构总览开始，带你创建第一个 DSH 插件，理解 Cordis 运行时基础，掌握三种运行模式，并介绍 8 步 Agent 开发方法论。

---

## 1. DSH 架构总览

### 1.1 设计哲学

```
DSH = Cordis 插件内核 + Agent 运行时插件集
```

DSH 建立在 [Cordis](https://github.com/cordiverse/cordis) 元框架之上。Cordis 是一个插件内核框架，提供插件加载、生命周期管理、依赖注入等基础能力。DSH 在此基础上实现了 Agent 运行时的全部能力。

### 1.2 目录结构

```
dsh/
├── src/
│   ├── core/          # 核心入口，组装插件
│   ├── plugins/       # 官方插件集
│   │   ├── model/     # 模型适配器（DeepSeek、OpenAI 等）
│   │   ├── tools/     # 工具插件（Shell、Git、MCP 等）
│   │   ├── memory/    # 记忆插件
│   │   └── security/  # 安全策略插件
│   └── runtime/       # 运行时基础设施
├── packages/          # 可发布的插件包
└── tests/             # 测试套件
```

### 1.3 分层视图

```
┌─────────────────────────────────────┐
│       Agent 应用层（你编写的插件）      │
├─────────────────────────────────────┤
│       Agent 运行时插件集              │
│  （模型/工具/记忆/安全/搜索...）        │
├─────────────────────────────────────┤
│      Cordis 插件内核（DSH 基于此）     │
│  （生命周期/依赖/事件/上下文）          │
├─────────────────────────────────────┤
│          Node.js 运行时               │
└─────────────────────────────────────┘
```

---

## 2. "Everything is a Plugin" 哲学

### 2.1 核心思想

在 DSH 中，**一切能力都以插件形式存在**：

| 能力 | 插件化方式 |
|------|-----------|
| 模型接入 | `model-deepseek`、`model-openai` 插件 |
| 工具执行 | `tool-shell`、`tool-git`、`tool-mcp` 插件 |
| 记忆管理 | `memory-vector`、`memory-file` 插件 |
| 安全策略 | `security-policy` 插件 |
| 用户界面 | `ui-cli`、`ui-web` 插件 |
| 日志追踪 | `observability-log` 插件 |

### 2.2 好处

- **最小核心**：内核只做插件管理，Agent 能力随需组装
- **灵活组合**：按任务需求选择插件组合，避免功能冗余
- **独立演进**：每个插件独立版本化、独立测试
- **社区生态**：任何人都可以发布 DSH 插件，共享 Agent 能力

### 2.3 插件的本质

DSH 插件就是一个 **npm 包**，通过标准的 package.json 暴露插件入口。这意味着：

- 使用 npm 的版本管理、依赖解析、发布流程
- 插件间可以通过 npm 依赖相互复用
- 复用 Node.js 庞大生态的函数库

---

## 3. 插件生命周期

DSH 插件的生命周期由 Cordis 管理，完整流程为：

```
安装 → 加载 → 启动 → 停止 → 卸载
```

### 3.1 生命周期钩子

插件通过实现生命周期钩子接入运行流程：

```typescript
import { Context } from 'cordis';
import { Plugin } from 'dsh';

export const name = 'my-plugin';

export function apply(ctx: Context) {
  // 启动阶段：注册服务、订阅事件、初始化资源
  ctx.on('ready', () => {
    ctx.logger.info('MyPlugin 已就绪');
  });

  // 停止阶段：清理资源、解绑事件
  ctx.on('dispose', () => {
    ctx.logger.info('MyPlugin 已停止');
  });
}
```

### 3.2 生命周期语义

| 阶段 | 触发条件 | 插件职责 |
|------|---------|---------|
| 安装 (install) | npm 安装时 | 声明依赖、下载二进制 |
| 加载 (load) | 启动时扫描 plugins 目录 | 注册元数据、解析依赖 |
| 启动 (start) | 所有依赖就绪后 | 初始化资源、订阅事件 |
| 停止 (stop) | 插件被卸载或运行时关闭 | 释放资源、取消订阅 |
| 卸载 (uninstall) | 手动移除插件 | 清理持久化状态 |

---

## 4. 创建第一个 DSH 插件

下面我们创建一个 `hello-dsh` 插件：当用户说"你好"时回复问候语。

### 4.1 初始化项目

```bash
mkdir hello-dsh && cd hello-dsh
npm init -y
npm install cordis dsh
```

### 4.2 配置 package.json

```json
{
  "name": "hello-dsh",
  "version": "1.0.0",
  "description": "DSH 示例插件：问候语",
  "main": "dist/index.js",
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch"
  },
  "keywords": ["dsh", "plugin", "agent"],
  "license": "MIT"
}
```

### 4.3 编写插件主体

```typescript
// src/index.ts
import { Context } from 'cordis';
import type { AgentContext } from 'dsh';

const name = 'hello-dsh';

/**
 * 插件主体：通过 apply 函数注入到运行时
 */
function apply(ctx: Context) {
  // 通过 ctx 获取 Agent 上下文服务
  const agent = ctx.get('agent') as AgentContext;

  // 注册一个消息处理器：拦截特定指令
  agent.on('message', async (session) => {
    const text = (session.text || '').trim();

    if (text === '你好' || text === 'hello') {
      await session.reply(`你好！我是 DSH 插件 ${name}，很高兴认识你。`);
    }

    // 继续传递消息给其他插件
    return true;
  });

  // 注册生命周期钩子
  ctx.on('ready', () => ctx.logger.info(`${name}: 已启动`));
  ctx.on('dispose', () => ctx.logger.info(`${name}: 已停止`));
}

export { name, apply };
export default { name, apply };
```

### 4.4 注册到 DSH

在 DSH 的配置文件（如 `dsh.config.ts`）中注册插件：

```typescript
// dsh.config.ts
import { defineConfig } from 'dsh';

export default defineConfig({
  // 插件列表：npm 包名或本地路径
  plugins: [
    '@deepseek/model-deepseek',   // 模型适配器
    '@deepseek/tool-shell',       // Shell 工具
    '@deepseek/memory-file',      // 文件记忆
    './plugins/hello-dsh',        // 我们的插件（本地路径）
  ],

  // 插件配置
  configs: {
    'model-deepseek': {
      apiKey: process.env.DEEPSEEK_API_KEY,
      model: 'deepseek-chat',
    },
    'hello-dsh': {
      // 自定义配置项
    },
  },
});
```

### 4.5 运行验证

```bash
# 构建插件
npm run build

# 启动 DSH
npx dsh start

# 在 DSH 交互终端中测试
> 你好
你好！我是 DSH 插件 hello-dsh，很高兴认识你。
```

---

## 5. Cordis 运行时基础

DSH 的一切插件能力都建立在 Cordis 之上。理解以下概念是插件开发的前提。

### 5.1 Context（上下文）

`Context` 是插件与运行时交互的唯一入口，提供：

```typescript
import { Context } from 'cordis';

export function apply(ctx: Context) {
  // 1. 服务获取：获取已注册的服务
  const agent = ctx.get('agent');
  const logger = ctx.logger;

  // 2. 事件订阅：监听运行时事件
  ctx.on('message', handler);
  ctx.once('ready', readyHandler);   // 一次性监听

  // 3. 服务注册：向其他插件提供服务
  ctx.provide('my-service', serviceImpl);

  // 4. 配置读取：读取插件自己的配置
  const config = ctx.config;          // 来自 dsh.config.ts
}
```

### 5.2 依赖注入

插件可以声明依赖其他插件提供的服务：

```typescript
export function apply(ctx: Context) {
  // 声明依赖 service-a 与 service-b，就绪后会注入
  const [a, b] = ctx.using(['service-a', 'service-b'], (a, b) => {
    // 依赖就绪后的回调
    return { init: () => a.foo() + b.bar() };
  });
}
```

### 5.3 可逆效应机制

Cordis 的核心特性之一：**插件可以被动态加载和卸载**。卸载时，插件注册的效应（事件监听、服务、定时器）会被自动撤销：

```typescript
export function apply(ctx: Context) {
  const timer = setInterval(() => {
    ctx.logger.info('心跳');
  }, 1000);

  // 挂载到 ctx 上的效应，会在 dispose 时自动清理
  ctx.effect(() => {
    clearInterval(timer);  // 清理逻辑
  });
}
```

---

## 6. 三种运行模式

DSH 支持三种运行模式，按场景选择：

### 6.1 Standard（标准模式）

完整的 UI 界面，适合开发调试和功能演示：

```bash
npx dsh start                # 默认标准模式
npx dsh start --mode standard
```

- 提供 Web UI，可视化查看会话、模型调用、工具日志
- 实时展示 Agent 的推理-行动过程
- 内置配置编辑器和插件管理器

### 6.2 Code（代码模式）

最小化 CLI，适合脚本化和 CI/CD 集成：

```bash
npx dsh start --mode code
```

- 面向开发者的简洁终端界面
- 支持管道输入：`echo "你好" | dsh start --mode code`
- 可编程输出：`dsh run --mode code -p "任务描述" --json`

### 6.3 Minimal（无界面模式）

Headless 模式，适合服务端嵌入：

```typescript
// server.ts
import { DSH } from 'dsh';

const dsh = new DSH({
  mode: 'minimal',
  plugins: [
    '@deepseek/model-deepseek',
    './plugins/hello-dsh',
  ],
  configs: {
    'model-deepseek': { apiKey: process.env.DEEPSEEK_API_KEY },
  },
});

await dsh.ready();

// 以编程方式调用 Agent
const reply = await dsh.ask('你好');
console.log(reply);

// 优雅关闭
await dsh.stop();
```

### 6.4 模式对比

| 特性 | Standard | Code | Minimal |
|------|----------|------|---------|
| 界面 | Web UI | 终端 CLI | 无 |
| 适合场景 | 调试/演示 | 开发/CI | 服务集成 |
| 启动速度 | 慢 | 中 | 快 |
| 插件能力 | 完整 | 完整 | 完整 |

---

## 7. 8 步 Agent 开发方法论

DSH 团队总结了一套 Agent 开发方法论，共 8 步：

### 第 1 步：定义任务边界

明确 Agent 的目标、输入、输出、边界条件：

```markdown
目标：为电商运营生成周报
输入：销售数据 CSV + 活动记录
输出：Markdown 周报（含趋势分析与建议）
边界：只读数据，不执行写操作
```

### 第 2 步：选择模型

根据任务复杂度选择模型：

| 任务类型 | 推荐模型 |
|---------|---------|
| 简单问答 | `deepseek-chat`（轻量） |
| 代码生成 | `deepseek-coder` |
| 复杂推理 | `deepseek-reasoner` |

### 第 3 步：设计工具集

列出 Agent 完成任务所需的工具，选取或开发对应插件：

```
需求：读取 CSV → 分析趋势 → 生成 Markdown
工具：tool-read-file、tool-dataframe、tool-write-file
```

### 第 4 步：配置记忆策略

确定哪些信息需要在会话间保持：

```typescript
configs: {
  'memory-vector': {
    enabled: true,
    collection: 'weekly-report',
    topK: 5,
  },
}
```

### 第 5 步：编写 Prompt 模板

将任务描述、工具说明、输出格式组织为系统提示词模板。

### 第 6 步：实现插件

开发自定义插件（本教程第 4 节），或组合现有插件。

### 第 7 步：安全配置

配置权限策略、审计开关、敏感操作审批。

### 第 8 步：测试与迭代

用 DSH 的测试框架编写测试用例，按结果迭代：

```typescript
// tests/agent.test.ts
import { DSH } from 'dsh';
import { test } from 'node:test';

test('周报 Agent 应返回 Markdown', async () => {
  const dsh = new DSH({ mode: 'minimal', plugins: [...] });
  const reply = await dsh.ask('根据 sample.csv 生成周报');
  assert.match(reply, /^#.*周报/m);
  await dsh.stop();
});
```

---

## 8. 插件分发

### 8.1 发布到 npm

```bash
# 登录 npm
npm login

# 发布
npm publish

# 或发布到 GitHub Packages
npm publish --registry=https://npm.pkg.github.com
```

### 8.2 标记元数据

在 package.json 中添加元数据，便于社区发现：

```json
{
  "dsh": {
    "category": "tool",
    "tags": ["shell", "devops"],
    "compat": ">=1.0.0"
  }
}
```

### 8.3 插件命名规范

- 官方插件：`@deepseek/xxx`
- 社区插件：`dsh-plugin-xxx` 或任意 npm 命名
- 推荐在 description 中标注"DSH plugin"

---

## 9. 最佳实践小结

1. **插件最小化**：每个插件只做一件事，围绕服务边界划分
2. **正确使用生命周期**：资源在 `ready` 中初始化，在 `dispose` 中清理
3. **拥抱 Context API**：通过 `ctx` 访问服务、订阅事件，避免全局状态
4. **声明式依赖**：用 `ctx.using` 声明依赖，让 Cordis 编排启动顺序
5. **尽早安全配置**：插件开发时就设计权限与审计，而非事后补充
6. **从模板起步**：复制官方插件作为起点，比从零开始更快

---

## 进阶指引

- 上一篇：[Harness 工程原理](./01-harness-engineering-principles.md)
- 下一篇：[Cordis 运行时机制深度解析](./03-cordis-runtime-deep-dive.md) — 理解 DSH 底层的插件内核