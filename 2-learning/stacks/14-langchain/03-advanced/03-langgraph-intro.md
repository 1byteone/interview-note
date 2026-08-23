# LangGraph 核心概念与状态机实战

> 面向 Python 后端开发者的 LangGraph 入门教程，覆盖 StateGraph、Node、Edge、条件分支等核心概念，并与 LangChain 线性 DAG 对比，通过订单状态机实战掌握图编排能力。

---

## 1. LangGraph 概述

LangGraph 是 LangChain 生态中的**图编排框架**，用于构建复杂的 Agent 工作流。与 LangChain 的线性 Chain 不同，LangGraph 支持：

- **循环（Cycle）**：Agent 的 Thought→Action→Observation 循环
- **条件分支（Conditional Branching）**：根据状态决定下一步
- **状态管理（State Management）**：全局状态在节点间传递
- **并行执行（Parallel Execution）**：多节点并发运行

### 1.1 LangChain vs LangGraph

| 对比维度 | LangChain Chain | LangGraph |
|----------|----------------|-----------|
| 结构 | 线性 DAG（有向无环） | 图（有环、有分支） |
| 流程 | Chain1 → Chain2 → Chain3 | Node → Edge → Node（可循环） |
| 循环 | 不支持 | 原生支持 |
| 状态 | 手动传递 | StateGraph 自动管理 |
| 适用场景 | 固定流程（RAG、翻译） | Agent Loop、状态机、复杂工作流 |
| 调试难度 | 低 | 中（需理解图执行） |

### 1.2 安装

```bash
pip install langgraph
```

---

## 2. 核心概念

### 2.1 StateGraph

StateGraph 是 LangGraph 的核心类，定义了一个**带类型的全局状态**，节点读取和修改该状态，边决定执行流向。

```python
from typing import TypedDict, Literal
from langgraph.graph import StateGraph, END

# 定义状态类型
class OrderState(TypedDict):
    """订单状态类型"""
    order_id: str
    status: Literal["PENDING", "PAID", "SHIPPED", "DELIVERED", "CANCELLED"]
    user_id: str
    amount: float
    history: list[str]  # 状态变更历史
```

### 2.2 Node（节点）

Node 是图中的一个处理单元，接收当前状态，返回更新后的状态。

```python
def process_payment(state: OrderState) -> OrderState:
    """处理支付节点"""
    print(f"处理支付：订单 {state['order_id']}，金额 {state['amount']}")
    return {
        **state,
        "status": "PAID",
        "history": state["history"] + [f"{state['status']} → PAID (已支付)"],
    }
```

### 2.3 Edge（边）

Edge 连接两个节点，定义执行顺序。

```python
# 添加边
graph.add_edge("START", "validate_order")
graph.add_edge("validate_order", "process_payment")
```

### 2.4 Conditional Edge（条件边）

条件边根据当前状态动态决定下一个节点。

```python
def route_after_payment(state: OrderState) -> Literal["ship_order", "cancel_order"]:
    """根据支付结果决定下一步"""
    if state["status"] == "PAID":
        return "ship_order"  # 支付成功，发货
    return "cancel_order"    # 支付失败，取消
```

---

## 3. 实战：订单状态机

下面通过一个完整的电商订单状态机来掌握 LangGraph 的核心用法。

```python
from typing import TypedDict, Literal, Annotated, Sequence
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver
import operator

# ── 1. 定义状态 ──

class OrderState(TypedDict):
    order_id: str
    status: Literal["PENDING", "PAID", "SHIPPED", "DELIVERED", "CANCELLED"]
    user_id: str
    amount: float
    history: Annotated[Sequence[str], operator.add]  # 自动追加
    payment_method: str
    address: str

# ── 2. 定义节点函数 ──

def validate_order(state: OrderState) -> OrderState:
    """校验订单"""
    errors = []
    if state["amount"] <= 0:
        errors.append("金额无效")
    if not state["address"]:
        errors.append("地址为空")
    if not state["payment_method"]:
        errors.append("支付方式为空")

    if errors:
        return {
            **state,
            "status": "CANCELLED",
            "history": [f"校验失败：{'；'.join(errors)}"],
        }
    return {
        **state,
        "history": [f"PENDING → 校验通过，准备支付"],
    }

def process_payment(state: OrderState) -> OrderState:
    """处理支付"""
    # 模拟支付处理
    payment_success = True  # 实际场景调用支付网关
    if payment_success:
        return {
            **state,
            "status": "PAID",
            "history": [f"PENDING → PAID | 支付方式：{state['payment_method']} | 金额：￥{state['amount']}"],
        }
    return {
        **state,
        "status": "CANCELLED",
        "history": [f"PENDING → CANCELLED | 支付失败"],
    }

def ship_order(state: OrderState) -> OrderState:
    """发货"""
    return {
        **state,
        "status": "SHIPPED",
        "history": [f"PAID → SHIPPED | 已发货，配送地址：{state['address']}"],
    }

def deliver_order(state: OrderState) -> OrderState:
    """确认送达"""
    return {
        **state,
        "status": "DELIVERED",
        "history": [f"SHIPPED → DELIVERED | 订单已完成"],
    }

def cancel_order(state: OrderState) -> OrderState:
    """取消订单"""
    return {
        **state,
        "status": "CANCELLED",
        "history": [f"{state['status']} → CANCELLED | 订单已取消"],
    }

# ── 3. 定义路由函数 ──

def route_after_validation(state: OrderState) -> Literal["process_payment", "cancel_order"]:
    """校验后的路由"""
    if state["status"] == "CANCELLED":
        return "cancel_order"
    return "process_payment"

def route_after_payment(state: OrderState) -> Literal["ship_order", "cancel_order"]:
    """支付后的路由"""
    if state["status"] == "PAID":
        return "ship_order"
    return "cancel_order"

# ── 4. 构建状态机 ──

# 创建图
workflow = StateGraph(OrderState)

# 添加节点
workflow.add_node("validate_order", validate_order)
workflow.add_node("process_payment", process_payment)
workflow.add_node("ship_order", ship_order)
workflow.add_node("deliver_order", deliver_order)
workflow.add_node("cancel_order", cancel_order)

# 添加入口
workflow.set_entry_point("validate_order")

# 添加条件边
workflow.add_conditional_edges(
    "validate_order",
    route_after_validation,
    {
        "process_payment": "process_payment",
        "cancel_order": "cancel_order",
    },
)

workflow.add_conditional_edges(
    "process_payment",
    route_after_payment,
    {
        "ship_order": "ship_order",
        "cancel_order": "cancel_order",
    },
)

# 添加普通边
workflow.add_edge("ship_order", "deliver_order")
workflow.add_edge("deliver_order", END)
workflow.add_edge("cancel_order", END)

# 编译图（支持持久化检查点）
checkpointer = MemorySaver()
app = workflow.compile(checkpointer=checkpointer)

# ── 5. 执行状态机 ──

# 初始状态
initial_state: OrderState = {
    "order_id": "O20240801001",
    "status": "PENDING",
    "user_id": "U12345",
    "amount": 2999.00,
    "history": [],
    "payment_method": "微信支付",
    "address": "北京市朝阳区xxx路100号",
}

# 执行
config = {"configurable": {"thread_id": "order_O20240801001"}}
result = app.invoke(initial_state, config=config)

print("最终状态：", result["status"])
print("变更历史：")
for h in result["history"]:
    print(f"  - {h}")
```

**输出结果：**

```
最终状态： DELIVERED
变更历史：
  - PENDING → 校验通过，准备支付
  - PENDING → PAID | 支付方式：微信支付 | 金额：￥2999.0
  - PAID → SHIPPED | 已发货，配送地址：北京市朝阳区xxx路100号
  - SHIPPED → DELIVERED | 订单已完成
```

### 3.1 状态机流程图

```
┌────────────────┐
│  validate_order │ ◄──── 入口
└───────┬────────┘
        │
        ▼  (条件判断)
┌───────────────┐      ┌───────────────┐
│ process_payment│ ──►  │  cancel_order  │
└───────┬───────┘      └───────┬───────┘
        │                      │
        ▼  (条件判断)           │
┌───────────────┐              │
│  ship_order   │              │
└───────┬───────┘              │
        │                      │
        ▼                      ▼
┌───────────────┐      ┌───────────────┐
│ deliver_order │      │      END      │
└───────┬───────┘      └───────────────┘
        │
        ▼
       END
```

---

## 4. 高级特性

### 4.1 带记忆的对话 Agent

LangGraph 可以构建带有内部循环的 Agent，实现 ReAct 循环：

```python
from langgraph.graph import StateGraph, END
from langchain_openai import ChatOpenAI
from langchain.tools import tool
from typing import TypedDict, Literal

class AgentState(TypedDict):
    messages: list
    next_action: str

def call_model(state: AgentState) -> AgentState:
    """调用 LLM 决定下一步"""
    llm = ChatOpenAI(model="gpt-4", temperature=0)
    response = llm.invoke(state["messages"])
    return {**state, "messages": state["messages"] + [response]}

def should_continue(state: AgentState) -> Literal["tools", "end"]:
    """判断是继续调用工具还是结束"""
    last_message = state["messages"][-1]
    if hasattr(last_message, "tool_calls") and last_message.tool_calls:
        return "tools"
    return "end"

# 构建带循环的 Agent 图
agent_graph = StateGraph(AgentState)
agent_graph.add_node("agent", call_model)
agent_graph.add_node("tools", call_tools)
agent_graph.set_entry_point("agent")
agent_graph.add_conditional_edges("agent", should_continue, {
    "tools": "tools",
    "end": END,
})
agent_graph.add_edge("tools", "agent")  # 循环：tools → agent
```

### 4.2 检查点持久化

LangGraph 支持将中间状态持久化到存储后端，实现断点续跑和状态回溯。

```python
from langgraph.checkpoint.sqlite import SqliteSaver

# 使用 SQLite 持久化检查点
with SqliteSaver.from_conn_string("checkpoints.db") as saver:
    app = workflow.compile(checkpointer=saver)

    # 第一次执行
    result1 = app.invoke(initial_state, config=config)

    # 从检查点继续执行（模拟中断恢复）
    result2 = app.invoke(None, config=config)
```

---

## 5. LangGraph 在 LangChain 生态中的位置

```
LangChain 生态
├── LangChain Core       — 基础组件（LLM、Prompt、Chain）
├── LangChain Community  — 第三方集成（向量库、文档加载器）
├── LangChain CLI        — 项目脚手架
├── LangSmith            — 可观测性平台
└── LangGraph            — 图编排框架（本教程）
    ├── StateGraph       — 有状态图
    ├── Checkpoint       — 状态持久化
    ├── Managed Value    — 共享值管理
    └── Prebuilt         — 预置 Agent 组件
```

LangGraph 适合以下场景：

- **Agent Loop**：ReAct 循环的 Thought→Action→Observation
- **状态机**：订单流转、审批流程、工单系统
- **多步骤工作流**：数据清洗→特征工程→模型训练→评估
- **人机协作**：需要人工审核介入的流程
- **条件分支复杂的流程**：不同条件走不同路径

---

## 总结

- **StateGraph** 是 LangGraph 的核心，以图结构组织工作流，Node 处理逻辑、Edge 定义流向
- **Conditional Edge** 实现动态路由，根据状态值决定下一步
- 相比 LangChain 线性 Chain，LangGraph 支持**循环**和**多分支**，适合复杂 Agent 工作流
- **检查点持久化**提供状态回溯、断点续跑、故障恢复能力
- 订单状态机是 LangGraph 的经典入门案例，掌握后可迁移到审批流程、Agent 循环等场景

---

> 下一篇：[04-evaluation.md](./04-evaluation.md) — LangChain 评估与回归测试