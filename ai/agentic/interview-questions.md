# Agentic AI 面试题大全

## 📚 知识体系

```
Agentic AI 核心概念
├── Agent 循环 (Agent Loop)
├── 自主规划 (Planning)
├── 工具调用 (Tool Calling)
├── 反思 (Reflection)
├── Self-Correction (自我纠错)
├── 多步骤推理
├── ReAct / ReWOO / Plan-and-Execute
└── Agent 评估 (Evaluation)

Agentic 架构
├── 单 Agent 架构
├── 多 Agent 架构
├── Supervisor 架构
├── 层级架构
├── 流水线架构
└── 网络架构 (任意连接)
```

---

## 🎯 Level 1：基础题

### 1. 什么是 Agentic AI？和传统 AI 的区别？
**答案**：
Agentic AI（智能体式 AI）指的是**能够自主规划、决策、执行多步骤任务**的 AI 系统，主动完成任务而不是被动响应。

**与传统 AI 的区别**：
| 维度 | 传统 AI（聊天/单轮） | Agentic AI |
|------|---------------------|-------------|
| 任务 | 单轮问答 | 多步骤任务 |
| 自主性 | 低（只响应） | 高（主动执行） |
| 工具调用 | 无 | 有 |
| 循环能力 | 无 | 有（推理-行动-观察） |
| 目标导向 | 无 | 有 |
| 记忆 | 无/有限 | 有 |

### 2. Agent Loop 是什么？
**答案**：
Agent Loop 是 Agent 的核心运行循环：

```text
┌─────────────────────────────┐
│   用户请求                    │
└──────────┬──────────────────┘
           ↓
┌─────────────────────────────┐
│   推理 (Reasoning)           │
│   LLM 分析当前状态和目标       │
└──────────┬──────────────────┘
           ↓
┌─────────────────────────────┐
│   决策 (Decision)            │
│   选择工具或生成回答          │
└──────────┬──────────────────┘
           ↓
┌─────────────────────────────┐
│   执行 (Action)              │
│   调用工具 / API / 代码       │
└──────────┬──────────────────┘
           ↓
┌─────────────────────────────┐
│   观察 (Observation)         │
│   获取工具结果               │
└──────────┬──────────────────┘
           ↓ （循环）直到任务完成
```

---

## 🎯 Level 2：进阶题

### 3. Agentic Patterns 有哪些？
**答案**：

**① Reflection（反思）**
```
生成 → 反思 → 改进 → 再反思 → 直到满意
```
- 自我批评 + 改进

**② Tool Use（工具使用）**
```
LLM → 工具调用 → 结果反馈 → LLM 生成
```
- 连接外部世界（搜索/API/代码）

**③ Planning（规划）**
```
任务 → 计划 → 执行 → 重新规划
```
- 复杂任务分解

**④ Multi-Agent（多智能体）**
```
多个 Agent 协作完成复杂任务
```
- Supervisor / Hierarchy / Collaboration

**⑤ Memory（记忆）**
```
短期记忆 + 长期记忆 + 向量检索
```
- 持久化上下文

**⑥ Evaluation（评估）**
```
N 次生成 → 评估器打分 → 选最优
```
- LLM-as-Judge

---

## 🎯 Level 3：高级题

### 4. 什么是 ReWOO？与 ReAct 的区别？
**答案**：

**ReAct**：推理和工具调用**交替**进行
```
Thought → Action → Observation → Thought → Action → Observation
```

**ReWOO（Reasoning WithOut Observation）**：
先把所有推理/规划做完，再一次性执行工具调用
```
Planner: 规划所有步骤（不执行）
    ↓
Worker: 根据规划执行工具调用
    ↓
Solver: 汇总结果生成最终回答
```

**区别**：
| 维度 | ReAct | ReWOO |
|------|-------|-------|
| 循环 | 边想边做 | 先想再做 |
| LLM 调用次数 | 多 | 少（成本低） |
| 决策质量 | 更灵活 | 较固定 |
| 并行性 | 低 | 高（可并行工具调用） |
| 适用 | 动态场景 | 可提前规划的场景 |

---

## 📖 学习资源

### 推荐项目
- [OpenAI Agents SDK](https://github.com/openai/openai-agents-python) - 官方 Agent SDK
- [Anthropic Agent Examples](https://github.com/anthropics/anthropic-cookbook)
- [AI Agents 学习](https://a16z.com/2024/08/30/agents/)

### 最佳实践
1. 先跑通最小 Agent Loop，再加记忆/多 Agent
2. 工具调用必须有错误处理和重试
3. 设置最大迭代次数防止死循环
4. LLM-as-Judge 评估 Agent 输出质量
5. 所有 Agent 行动都要有日志（可追踪）