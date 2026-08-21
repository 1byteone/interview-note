# AI Agent 面试题大全

## 📚 知识体系

```
AI Agent 核心概念
├── Agent 定义
├── Agent 与 LLM 的区别
├── Agent 核心组件（感知/决策/执行/记忆/工具）
├── Agent 循环 (Agent Loop)
├── ReAct 模式
├── Plan-and-Execute
├── Reflection 反思
├── Tool Calling 工具调用
├── Multi-Agent 多智能体
├── Agent Memory 记忆
└── Agent 评估与安全

Agent 技术栈
├── LangChain
├── LangGraph
├── AutoGen
├── CrewAI
├── OpenAI Function Calling
├── MCP (Model Context Protocol)
└── A2A (Agent to Agent)
```

---

## 🎯 Level 1：基础题

### 1. 什么是 AI Agent？和 LLM 有什么区别？
**答案**：
AI Agent 是一个能够感知环境、做出决策、采取行动以实现目标的智能体，它**不只是"回答问题"**，而是**"完成任务"**。

**LLM vs Agent**：

| 特性 | LLM | AI Agent |
|------|-----|----------|
| 基本能力 | 生成文本 | 完成目标任务 |
| 是否调用工具 | 否（仅输出） | 是（可执行工具） |
| 是否有循环 | 单次生成 | 循环推理-行动 |
| 是否多步骤 | 否 | 是（可规划多步） |
| 是否有记忆 | 有限上下文 | 短期+长期记忆 |
| 是否有自主性 | 被动响应 | 主动执行 |

**一句话**：LLM 是"会说话的大脑"，Agent 是"会干活的人"（LLM 是它的核心处理器）。

### 2. Agent 的核心组件有哪些？
**答案**：

```text
┌─────────────────────────────┐
│         Agent               │
│  ┌──────┐  ┌──────────┐    │
│  │ 感知 │  │  规划决策  │    │
│  │(感知)│  │ (Planning) │    │
│  └──────┘  └──────────┘    │
│  ┌──────┐  ┌──────────┐    │
│  │ 记忆 │  │  执行行动  │    │
│  │(Memory)│  │ (Action)  │    │
│  └──────┘  └──────────┘    │
│  ┌──────────────────┐      │
│  │  工具集 (Tools)    │      │
│  └──────────────────┘      │
└─────────────────────────────┘
```

1. **感知（Perception）**：接收环境信息（用户输入、状态）
2. **规划（Planning）**：用 LLM 制定完成目标的步骤
3. **执行（Action）**：调用工具执行具体操作
4. **记忆（Memory）**：短期（会话）+ 长期（向量库/数据库）
5. **工具（Tools）**：搜索、数据库、API、代码执行等

---

## 🎯 Level 2：进阶题

### 3. 什么是 ReAct 模式？
**答案**：
ReAct（Reasoning + Acting）是 Agent 的核心决策模式：**推理 → 行动 → 观察 → 再推理**的循环。

**ReAct 循环**：
```text
Thought（思考）：我需要查一下数据库
    ↓
Action（行动）：search_db("用户ID 1001")
    ↓
Observation（观察）：返回用户信息
    ↓
Thought（思考）：用户存在，下一步生成订单
    ↓
Action：create_order(...)
    ↓
Observation：订单创建成功
    ↓
Final Answer（最终回答）：订单已创建，编号为...
```

**ReAct 的优势**：
1. 解决复杂多步任务
2. 可观察行动过程（可调试）
3. 自我纠错（观察后调整）

### 4. 什么是 Tool Calling？如何实现？
**答案**：
Tool Calling（工具调用）是让 LLM 根据用户请求决定调用哪个外部函数并生成参数的机制。

**工作流程**：
```text
用户：帮我查一下北京的天气并订票
    ↓
LLM 分析 → 决定需要两个工具
    ↓
生成结构化调用：
  tool: get_weather(city="北京")
  tool: book_train(city="北京", date="2026-08-21")
    ↓
Agent 执行工具
    ↓
结果返回 LLM
    ↓
LLM 整合输出最终回答
```

**OpenAI Function Calling 示例**：
```json
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "获取城市天气",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {"type": "string", "description": "城市名称"}
          },
          "required": ["city"]
        }
      }
    }
  ]
}
```

**LangChain 实现**：
```python
from langchain_core.tools import tool

@tool
def get_weather(city: str) -> str:
    """获取城市天气"""
    return f"{city}：晴天，25°C"

# 绑定工具到模型
llm_with_tools = llm.bind_tools([get_weather])
```

---

## 🎯 Level 3：高级题

### 5. 什么是 Plan-and-Execute 模式？
**答案**：
Plan-and-Execute 是将"规划"和"执行"分离的 Agent 模式。

**与 ReAct 的区别**：
| 模式 | 特点 | 适用场景 |
|------|------|----------|
| ReAct | 边想边做（交替） | 简单的多步任务 |
| Plan-and-Execute | 先规划后执行 | 复杂的长任务 |

**流程**：
```text
用户任务
    ↓
Planner（规划器）
    ↓
计划列表：
  1. 分析用户数据
  2. 查询订单记录
  3. 生成推荐方案
  4. 汇总输出
    ↓
Executor（执行器）→ 逐步执行
    ↓
Re-plan（重新规划）→ 计划变更时
    ↓
完成
```

**LangGraph 实现**：
```python
# 规划节点
def plan_node(state):
    plan = llm.invoke(f"为以下任务制定执行计划: {state.task}")
    return {"plan": plan}

# 执行节点
def execute_node(state):
    task = state.plan.pop_reasoning_steps()[0]
    result = agent.invoke({"task": task})
    return {"results": state.results + [result]}

# 循环判定
def should_continue(state):
    if state.plan.done:
        return "final"
    return "execute"
```

### 6. Multi-Agent 有哪些协作模式？
**答案**：

**模式一：合作模式（Collaboration）**
```
Agent A → 消息共享 → Agent B
所有 Agent 共享消息列表，互相协作
```

**模式二：监督者模式（Supervisor）**
```
        Supervisor（监督者）
        ↓      ↓      ↓
    Agent A  Agent B  Agent C
监督者决定每个任务交给哪个 Agent
```

**模式三：层级团队（Hierarchical）**
```
       Manager
    ↓      ↓      ↓
  Team1   Team2   Team3
    ↓       ↓       ↓
 AgentA  AgentB   AgentC
```

**模式四：流水线（Pipe）**
```
Agent A → Agent B → Agent C → Agent D
每个 Agent 处理上一级的输出
```

---

## 🎯 Level 4：专家题

### 7. 如何设计和评估生产级 Agent 系统？
**答案**：

**设计要点**：
1. **任务边界**：明确 Agent 的能力边界
2. **工具安全**：权限控制、危险操作拦截
3. **超时控制**：防止 Agent 无限循环
4. **成本控制**：限制 LLM 调用次数
5. **错误处理**：失败降级、重试机制
6. **可观察性**：记录 Thought/Action 日志

**评估框架**：

| 维度 | 指标 | 说明 |
|------|------|------|
| 任务完成率 | 完成度 | 成功完成的比例 |
| 正确性 | 结果准确率 | 输出质量 |
| 效率 | 步数/成本 | 完成任务的资源消耗 |
| 稳定性 | 失败率 | 长任务可靠性 |
| 安全性 | 违规率 | 是否有危险操作 |

**生产架构**：
```
用户 → API Gateway
        ↓
Agent Orchestrator（任务管理）
        ↓
Agent Pool（隔离执行）
        ↓
Tools（受控调用）
        ↓
Monitoring + Logging + Evaluation
```

### 8. MCP 和 A2A 是什么？
**答案**：

**MCP（Model Context Protocol）**：模型上下文协议
- 标准化 Agent 与外部工具的连接
- 类似 USB-C：统一接口连接不同工具
- 解决工具定义、调用、认证的标准化

**A2A（Agent to Agent）**：智能体间通信协议
- 标准化 Agent 之间的通信
- 支持跨 Agent 的任务委托
- 类似 HTTP：Agent 间的"互联网协议"

---

## 📖 学习资源

### 推荐项目
- [LangChain](https://github.com/langchain-ai/langchain) - Agent 开发框架
- [LangGraph](https://github.com/langchain-ai/langgraph) - Agent 编排
- [AutoGen](https://github.com/microsoft/autogen) - 微软多 Agent 框架
- [CrewAI](https://github.com/crewAIInc/crewAI) - 角色化多 Agent
- [OpenAI Agents SDK](https://github.com/openai/openai-agents-python) - OpenAI 官方

### 最佳实践
1. 先定义清楚任务边界和完成标准
2. 工具调用必须有权限控制和日志
3. 设置最大迭代次数防死循环
4. 从 ReAct 起步，复杂化再上 Multi-Agent
5. 上线前建立评估集（golden set）