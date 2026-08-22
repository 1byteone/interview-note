# 🌐 AI Agent 生态全景图（Mermaid）

> 六大技术栈生态 + 仓库归属 + 交叉引用关系  
> 生成日期: 2026-08-22  
> GitHub 原生渲染：在仓库中打开本文件或嵌入 `.md` 文件时自动渲染。

---

## 1. 生态总览

```mermaid
graph TB
    subgraph E01[E01 · Claude Code 生态]
        direction TB
        A1[claude-code-ultimate-guide<br/>⭐5.8k 深度教学]
        A2[claude-code-guide<br/>⭐4.6k 功能手册]
        A3[claudecode-guide<br/>⭐34 入门]
    end

    subgraph E02[E02 · Codex 生态]
        direction TB
        B1[CodexGuide<br/>⭐3.2k 实践指南]
    end

    subgraph E03[E03 · DSH/Harness 生态]
        direction TB
        C1[harness_engineering_guide<br/>⭐115 理论专著]
        C2[deepeseek-harness-guide<br/>⭐13 DSH框架]
        C3[awesome-harness-engineering<br/>⭐3.9k 资源索引]
    end

    subgraph E04[E04 · Hermes/OpenClaw 生态]
        direction TB
        D1[hermes-agent-guide<br/>⭐650 30万字指南]
        D2[openclaw-security-practice-guide<br/>⭐2.9k 零信任安全]
        D3[awesome-hermes-agent<br/>⭐5.4k 资源索引]
    end

    subgraph E05[E05 · MCP 协议生态]
        direction TB
        E[ MCP-Chinese-Getting-Started-Guide<br/>⭐3.6k 入门实战]
    end

    subgraph E06[E06 · 通识与基础]
        direction TB
        F1[Prompt-Engineering-Guide<br/>⭐77.7k 提示工程圣经]
        F2[AgentGuide<br/>⭐8.6k 求职体系]
        F3[ai-system-design-guide<br/>⭐2.7k 系统设计]
        F4[ai-agent-interview-guide<br/>⭐2.1k 面试题库]
        F5[ai-agents-from-zero<br/>⭐4.0k 速成指南]
        F6[python-guide<br/>⭐29.8k Python工程]
        F7[awesome-agent-skills x2<br/>⭐11k Skills合集]
        F8[awesome-skills-cn / AI-Shell / daily-mentor]
    end

    %% 生态间关系
    E06 --- E01
    E06 --- E02
    E06 --- E03
    E06 --- E04
    E05 --- E01
    E05 --- E02
    E05 --- E03
    E05 --- E04
    E03 --- E01
    E03 --- E04
    E01 -. 竞品 .- E02
    E03 -. 竞品 .- E04

    %% 样式
    classDef eco fill:#1e1e2e,stroke:#89b4fa,stroke-width:2px,color:#cdd6f4
    class E01,E02,E03,E04,E05,E06 eco
```

---

## 2. 学习路线图（场景驱动）

```mermaid
flowchart LR
    S1[AI Agent 初学者] --> P1[Prompt-Engineering-Guide]
    P1 --> P2[AgentGuide]
    P2 --> P3{选择生态}
    P3 -->|Claude Code| Q1[E01 三库阶梯]
    P3 -->|Codex| Q2[E02 CodexGuide]
    P3 -->|开源框架| Q3[E04 Hermes]
    Q1 --> P4[E05 MCP 协议]
    Q2 --> P4
    Q3 --> P4
    P4 --> P5[ai-system-design-guide]
    P5 --> P6[ai-agent-interview-guide]

    S2[后端工程师转型] --> T1[python-guide]
    T1 --> T2[E03 Harness 原理]
    T2 --> T3[E01 或 E02 工具链]
    T3 --> T4[E04 开源框架]
    T4 --> T5[面试准备]

    S3[资深架构师] --> U1[ai-system-design-guide]
    U1 --> U2[E03 14章专著]
    U2 --> U3[E04 安全体系]
    U3 --> U4[多生态框架对比]
```

---

## 3. 生态关联矩阵（简化版）

```mermaid
graph LR
    MCP[E05 MCP] -->|协议基础| CC[E01 Claude]
    MCP -->|协议基础| CX[E02 Codex]
    MCP -->|协议基础| DSH[E03 DSH]
    MCP -->|协议基础| HR[E04 Hermes]
    BASE[E06 通识] -->|Prompt/理论| CC
    BASE -->|Prompt/理论| CX
    BASE -->|系统设计| DSH
    BASE -->|框架对比| HR
    HR -->|参考实现| DSH
    CC -->|参考实现| DSH
```

---

> 本图与 [`guide/data/landscape.md`](../data/landscape.md) 和 [`guide/data/cross-reference.md`](../data/cross-reference.md) 内容一致，为可视化表达。