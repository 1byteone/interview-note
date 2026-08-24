# T08: DeepSeek Harness 自定义插件开发

> **[← 教程目录](README.md) | 工具: DeepSeek Harness Creator Mode | 时长: ~30min**

---

## Goal

用 DSH Creator Mode 开发一个**自定义 Java Code Review 插件**，理解 Cordis 插件架构。

## 前置条件

- 已安装 DSH（见 T07）
- 基础 TypeScript 知识

## Step 1: 在 Creator Mode 中探索插件结构

```bash
npx @deepseek-ai/dsh web --mode creator
```

```
我想了解 DSH 的插件架构。

请：
1. 列出当前已加载的插件
2. 展示一个简单插件的目录结构
3. 说明插件如何注册工具（Tool）
4. 说明插件如何挂载和卸载
```

## Step 2: 创建插件骨架

在 DSH 源码的 plugins 目录下：

```bash
mkdir -p plugins/java-review/src
cd plugins/java-review
```

```json
// plugins/java-review/package.json
{
  "name": "@dsh-plugin/java-review",
  "version": "0.1.0",
  "description": "Java Code Review plugin for DeepSeek Harness",
  "main": "src/index.ts",
  "keywords": ["dsh-plugin", "java", "code-review"],
  "dependencies": {
    "@cordisjs/core": "latest"
  }
}
```

## Step 3: 实现插件核心逻辑

```typescript
// plugins/java-review/src/index.ts
import { Context } from '@cordisjs/core'

export interface ReviewFinding {
  file: string
  line: number
  severity: 'error' | 'warning' | 'info'
  category: string
  message: string
  suggestion: string
}

export default function javaReviewPlugin(ctx: Context) {
  // 注册工具：Java 代码审查
  ctx.tool('java_review', {
    description: '审查 Java 代码，检查常见问题',
    parameters: {
      path: { type: 'string', description: '要审查的文件或目录路径' },
      rules: {
        type: 'array',
        description: '要应用的规则集',
        items: { type: 'string' }
      }
    },
    async execute(params) {
      const { path, rules } = params
      const findings: ReviewFinding[] = []

      // 规则: 检查 NPE 风险
      if (rules.includes('npe') || rules.includes('all')) {
        const npeIssues = await checkNpeRisk(path)
        findings.push(...npeIssues)
      }

      // 规则: 检查事务边界
      if (rules.includes('transaction') || rules.includes('all')) {
        const txIssues = await checkTransactionBoundary(path)
        findings.push(...txIssues)
      }

      // 规则: 检查并发安全
      if (rules.includes('concurrency') || rules.includes('all')) {
        const concurrencyIssues = await checkConcurrency(path)
        findings.push(...concurrencyIssues)
      }

      return {
        totalFindings: findings.length,
        bySeverity: {
          error: findings.filter(f => f.severity === 'error').length,
          warning: findings.filter(f => f.severity === 'warning').length,
          info: findings.filter(f => f.severity === 'info').length,
        },
        findings
      }
    }
  })

  // 注册技能：Spring Boot Review
  ctx.skill('springboot-review', {
    description: 'Spring Boot 项目完整代码审查',
    trigger: 'review, code review, PR review',
    prompt: `
      对当前 Spring Boot 项目进行全面代码审查。

      检查清单：
      1. NPE 风险（Optional 使用）
      2. 事务边界（@Transactional 注解）
      3. 并发安全（线程安全、分布式锁）
      4. SQL 注入（参数化查询）
      5. 缓存一致性（Redis 与 DB 同步）
      6. 异常处理（完整性、不泄露内部信息）
      7. 日志规范（级别、脱敏、traceId）
      8. API 设计（RESTful、版本控制）

      输出格式：按文件分组，每个发现包含位置、问题、影响、建议。
    `
  })
}

// 辅助函数（简化示例）
async function checkNpeRisk(path: string): Promise<ReviewFinding[]> {
  // 实际实现中用 AST 分析或正则匹配
  return []
}

async function checkTransactionBoundary(path: string): Promise<ReviewFinding[]> {
  return []
}

async function checkConcurrency(path: string): Promise<ReviewFinding[]> {
  return []
}
```

## Step 4: 在 Creator Mode 中测试插件

```
加载刚创建的 java-review 插件。

然后对当前项目的 UserController.java 执行审查。
使用 all 规则集。
```

## Step 5: 注册到 DSH 配置

```yaml
# ~/.dsh/config.yaml
plugins:
  - name: java-review
    path: ./plugins/java-review
    enabled: true
    options:
      rules:
        - npe
        - transaction
        - concurrency
        - security
        - logging
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| 插件加载失败 | 检查 package.json 的 main 字段路径 |
| 工具没有出现在列表中 | 确认 ctx.tool() 在插件入口函数中调用 |
| Creator Mode 不识别插件 | 重启 DSH 让配置生效 |
| 想调试插件 | 在 Creator Mode 中用 runtime inspection |

## 延伸

- → [T07: 四模式体验](T07-DeepSeek-Harness模式对比.md)
- → [04-DeepSeek Harness 详解](../04-DeepSeek-Harness.md)
