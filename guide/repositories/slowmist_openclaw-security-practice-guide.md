# slowmist/openclaw-security-practice-guide

> ⭐ 2,857 | 🗣 Shell | [GitHub](https://github.com/slowmist/openclaw-security-practice-guide) | 收录: 2026-08-22

---

## Metadata

| 字段 | 值 |
|------|-----|
| Stars | 2,857 |
| 语言 | Shell |
| Topics | 无 |
| 生态 | E04 · Hermes/OpenClaw 生态 |

## 内容分析

### 核心定位

Agent 端零信任安全实践，面向 Agent 而非人类。

### 核心架构

```
Pre-action（事前）
├── 行为黑名单
├── Skill 安装审计
└── 防供应链投毒

In-action（事中）
├── 权限收窄（最小权限原则）
├── 跨 Skill 预检
└── 业务风险控制

Post-action（事后）
├── 13 项自动审计指标
├── 夜间自动审计
└── Brain Git 灾难恢复
```

### 独特价值

- 首创 Agentic 零信任架构
- 从传统「主机静态防御」转向「Agent 端零信任」
- 13 项自动审计指标和 Git 灾难恢复

## 阅读建议

- **适合人群**: 高级 Agent 用户、安全工程师
- **前置知识**: 了解 OpenClaw 或类似 Agent 运行时
---

## 生态交叉引用

- **主生态**: E04 · Hermes/OpenClaw 生态
- **交叉引用**: 同上生态
  - **E01**: [FlorianBruniaux/claude-code-ultimate-guide](../repositories/FlorianBruniaux_claude-code-ultimate-guide.md), [zebbern/claude-code-guide](../repositories/zebbern_claude-code-guide.md)
  - **E03**: [yeasy/harness_engineering_guide](../repositories/yeasy_harness_engineering_guide.md), [flaqai/deepeseek-harness-guide](../repositories/flaqai_deepeseek-harness-guide.md)

> 📖 完整矩阵见 [data/cross-reference.md](../data/cross-reference.md)