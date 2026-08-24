# T07: DeepSeek Harness 四模式体验

> **[← 教程目录](README.md) | 工具: DeepSeek Harness | 时长: ~25min**

---

## Goal

在本地安装 DeepSeek Harness，分别体验 Standard、Code、Minimal、Creator 四种运行模式，理解它们的差异。

## 前置条件

```bash
# 需要 Node.js 18+
node --version

# 安装 DSH
npx @deepseek-ai/dsh web
# 或从源码安装
git clone https://github.com/deepseek-ai/deepseek-harness
cd deepseek-harness && npm install && npm run build
```

## Step 1: Standard Mode——完整 Coding Agent

```bash
# 启动 Standard 模式（默认）
npx @deepseek-ai/dsh web

# 在浏览器中打开 http://localhost:3000
# 输入任务：
```

```
分析当前目录的 Spring Boot 项目。
输出：模块结构、核心依赖、分层架构、潜在问题。
只读分析，不修改代码。
```

**观察点**：Standard 模式会使用完整工具集——文件搜索、Shell、Web 搜索、规划等。

## Step 2: Code Mode——模型生成编排程序

```
# 在 Web UI 中切换到 Code Mode
# 或通过配置启动：
npx @deepseek-ai/dsh web --mode code
```

```
在当前项目中，找到所有使用了 @Deprecated 注解的方法，
分析每个方法的调用方，生成替换方案。

要求：用 Code Mode 一次性生成完整的分析程序。
```

**观察点**：Code Mode 会生成一个 TypeScript 程序，一次性调用搜索、读取、分析工具，而不是逐步 LLM 往返。

## Step 3: Minimal Mode——极简 Benchmark

```bash
npx @deepseek-ai/dsh web --mode minimal
```

```
在当前目录创建一个 Hello World Spring Boot 应用。
只使用 bash 和文件编辑器完成。
```

**观察点**：Minimal 模式只有 bash + str_replace_editor，非常适合测试模型的基础编码能力。

## Step 4: Creator Mode——自定义 Preset

```bash
npx @deepseek-ai/dsh web --mode creator
```

```
我想创建一个 Java Code Review Agent。

它应该：
1. 只读分析 Java 代码
2. 检查 NPE 风险、并发问题、事务边界
3. 输出结构化报告

请帮我构建这个 Agent 的 Preset 配置。
```

**观察点**：Creator Mode 可以检查当前运行时、测试插件、组合成新模式。

## Step 5: 对比四种模式

| 模式 | 工具数 | 适合场景 | Token 消耗 |
|------|-------|---------|-----------|
| Standard | 完整 | 日常编程 | 高 |
| Code | 完整（通过 SDK） | 多步批量操作 | 中 |
| Minimal | 2 个 | Benchmark | 低 |
| Creator | 完整 + 调试 | 构建新 Agent | 高 |

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| npx 安装失败 | 用源码安装: `git clone && npm install && npm run build` |
| Web UI 打不开 | 检查端口占用: `lsof -i :3000` |
| Code Mode 报错 | 确保模型支持 function calling |
| 想用其他模型 | 修改配置中的 model 字段: `deepseek-chat` 或 `gpt-4o` |

## 延伸

- → [T08: 自定义插件开发](T08-DeepSeek-Harness自定义插件.md)
- → [04-DeepSeek Harness 详解](../04-DeepSeek-Harness.md)
