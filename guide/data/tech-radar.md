# 📡 技术雷达 — 生态视角

> **追踪 AI Agent 与 Coding Agent 领域的技术趋势**  
> 版本: v1.0 | 2026 Q3 | 日期: 2026-08-22

---

## 1. 雷达总览

```
                    评估
                    ↑
         采纳 ────────── 暂缓
                    │
                    │
                    ├───→ 时间
                    │
                    │
         试验 ────────── 监视
                    ↓
                    成熟度
```

| 象限 | 说明 | 行动 |
|------|------|------|
| 🟢 **采纳** | 成熟可靠，推荐使用 | 立即学习并应用于生产 |
| 🔵 **试验** | 前景看好，值得尝试 | 在非关键项目上试水 |
| 🟡 **暂缓** | 尚不成熟或正在被替代 | 保持关注，暂不投入 |
| 🟠 **监视** | 值得关注的新兴技术 | 了解但不深入学习 |

---

## 2. 技术雷达条目（按生态标注）

### 🟢 采纳（Adopt）

| 技术 | 所属生态 | 推荐理由 | 代表仓库 |
|------|----------|----------|----------|
| **Prompt Engineering** | E06 通识 | 已验证的经典技术，所有 Agent 的基石 | Prompt-Engineering-Guide |
| **MCP 协议** | E05 | Agent 工具调用的标准协议，生态成熟 | MCP-Chinese-Getting-Started-Guide |
| **Claude Code** | E01 | 当前最强的 Coding Agent 之一 | claude-code-ultimate-guide |
| **LangGraph** | E06 通识 | Agent 编排的标准方案 | AgentGuide (提及) |
| **RAG** | E06 通识 | 企业级知识问答的标准方案 | ai-system-design-guide (提及) |
| **ReAct 模式** | E06 通识 | Agent 交互的事实标准 | Prompt-Engineering-Guide |

### 🔵 试验（Trial）

| 技术 | 所属生态 | 评估意见 | 代表仓库 |
|------|----------|----------|----------|
| **Harness 工程** | E03 | 理论体系完整，但生产案例少 | harness_engineering_guide |
| **DeepSeek Harness (DSH)** | E03 | 插件生态快速发展，但文档分散 | deepeseek-harness-guide |
| **Hermes Agent** | E04 | 记忆系统设计优秀，社区活跃 | hermes-agent-guide |
| **Codex CLI** | E02 | OpenAI 新秀，生态待成熟 | CodexGuide |
| **Agent Skills 生态** | E06 | 爆发式增长，质量参差不齐 | awesome-agent-skills |
| **Agentic RAG** | E06 | GraphRAG + Agent 结合，前沿方向 | ai-system-design-guide |
| **多 Agent 编排** | E01 | 潜力巨大，但复杂度高 | claude-code-ultimate-guide (Agent Teams) |

### 🟡 暂缓（Hold）

| 技术 | 所属生态 | 暂缓原因 | 替代方案 |
|------|----------|----------|----------|
| **纯 Prompt 工程** | E06 | 已被 Agent 框架封装，不必深入 | 了解基础即可 |
| **自建 Agent 框架** | E03 | 生态已丰富，不值得自建 | 用 LangGraph/DSPy |
| **单 Agent 范式** | E04 | 正在被多 Agent 取代 | 多 Agent 编排 |

### 🟠 监视（Monitor）

| 技术 | 所属生态 | 关注点 | 期待内容 |
|------|----------|--------|----------|
| **A2A 协议** | E05 | Agent-to-Agent 通信标准 | ai-system-design-guide 提及 |
| **MCP 2.0** | E05 | 下一代 MCP 协议 | ai-system-design-guide 提及 |
| **Agentic 安全标准** | E04 | 零信任安全在 Agent 端的落地 | openclaw-security-practice-guide |
| **GRPO / RLVR** | E06 | 强化学习在 Agent 中的应用 | AgentGuide 提及 |
| **Agent 评估框架** | E06 | Agent 质量评估标准化 | 暂无成熟方案 |

---

## 3. 生态热度对比

```
生态热度（基于仓库数 + Stars + 活跃度）

E01 Claude Code  ██████████████████  🔥🔥🔥🔥🔥 (主流，文档最全)
E02 Codex        ████████████        🔥🔥🔥🔥   (成长中)
E03 DSH/Harness  ████████████        🔥🔥🔥🔥   (理论强，实践少)
E04 Hermes/OpenClaw ██████████████   🔥🔥🔥🔥   (社区活跃)
E05 MCP          ████████████        🔥🔥🔥🔥   (基础设施)
E06 通识         ████████████████████🔥🔥🔥🔥🔥 (入门必读)
```

---

## 4. 时间线趋势

```
2024                       2025                        2026
│                          │                          │
Prompt Engineering ────────┼──────────────────────── RAG
│                          │                          │
                          Claude Code ───────────── Coding Agent 爆发 (E01)
│                          │                          │
                          MCP 协议 ───────────────── MCP 2.0 (E05)
│                          │                          │
                          LangGraph ──────────────── Agent 框架成熟 (E06)
│                          │                          │
                          Harness 工程 ───────────── DSH 生态 (E03)
│                          │                          │
                          Hermes Agent ───────────── 多 Agent 编排 (E04)
│                          │                          │
                          Skills 生态 ────────────── Skills 爆发 (E06)
│                          │                          │
                          Agent 安全 ─────────────── 零信任架构 (E04)
```

---

## 5. 个人建议

### 立即投入
1. **深入学习 Claude Code / Codex（E01/E02）** — 当前最直接的效率提升工具
2. **掌握 MCP 协议（E05）** — Agent 工具调用的基础
3. **学习 LangGraph（E06）** — Agent 编排的标准方案

### 短期规划（3-6 个月）
1. **深入研究 Harness 工程（E03）** — 理解 Agent 底层原理
2. **探索 DSH 生态（E03）** — 插件开发
3. **关注 Agent 安全（E04）** — 随着 Agent 权限提升，安全将成刚需

### 长期跟踪（6-12 个月）
1. **A2A 协议（E05）** — 多 Agent 协作的标准
2. **Agent 评估框架（E06）** — 质量保证
3. **Agentic 安全标准（E04）** — 行业规范