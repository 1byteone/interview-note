# -*- coding: utf-8 -*-
"""
03_langgraph_basic.py
====================
LangGraph 基础入门：StateGraph 图执行引擎

演示内容：
1. StateGraph + State：定义图的状态结构
2. Node（节点）与 Edge（边）：编排执行流程
3. Conditional edges（条件边）：根据状态动态选择下一步
4. 一个简单的 ReAct 循环：Agent 推理 -> 是否调用工具 -> 收敛后结束

设计说明：
- 为了离线可运行，内置了一个"模拟 LLM"（FakeReasoner），
  它根据当前"函数调用需求"伪随机决定是否调用工具。
- 换成真实 LLM 时，只需把 fake_agent_node 里的逻辑替换为
  真实 ChatOpenAI + 工具绑定即可，图的骨架无需改动。

运行方式：
    python 03_langgraph_basic.py
"""

import random
from typing import Annotated, Literal, TypedDict
from typing import Sequence

from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages


# =====================================================================
# 1. 定义图的状态（State）：所有节点共享的"内存黑板"
# =====================================================================
class AgentState(TypedDict):
    # messages: 对话消息列表，使用 add_messages 减少器自动追加
    #           即每个节点返回的 messages 会自动合并到已有列表
    messages: Annotated[Sequence, add_messages]
    # steps: 记录推理步数，便于限制循环次数
    steps: int
    # needs_tool: 本轮是否还需要调用工具
    needs_tool: bool


# 简单消息结构（仅演示用，也可以用 langchain_core.messages 的类）
class Msg(dict):
    """极简消息类型：{"role": "user"|"assistant"|"tool", "content": str}。"""


# =====================================================================
# 2. 节点（Node）：图中的执行单元
# =====================================================================

def call_model(state: AgentState) -> AgentState:
    """【Agent 推理节点】模拟 LLM 决策：是否需要调用工具。

    真实项目中，这里应调用 ChatOpenAI，并把 model.bind_tools(...) 的
    输出追加到 state["messages"]，检测 tool_calls 字段即可判断。
    """
    messages = state["messages"]
    last_msg = messages[-1] if messages else Msg(role="user", content="")
    user_text = last_msg.get("content", "") if isinstance(last_msg, dict) else str(last_msg)

    steps = state.get("steps", 0) + 1  # 步数 +1

    # 模拟规则：文本包含"计算"或"搜索"时，模型认为自己需要调用工具
    needs_tool = ("计算" in user_text or "搜索" in user_text) and steps < 4

    if needs_tool:
        reply = Msg(
            role="assistant",
            content=f"[模拟LLM] 第{steps}步：我需要调用工具来完成任务。",
        )
    else:
        reply = Msg(
            role="assistant",
            content=f"[模拟LLM] 我已经掌握足够信息，最终答案是：已完成处理（第{steps}步）。",
        )

    return {
        "messages": [reply],
        "steps": steps,
        "needs_tool": needs_tool,
    }


def call_tool(state: AgentState) -> AgentState:
    """【工具执行节点】模拟工具被调用后的返回值。

    真实项目中，这里应根据 LLM 提出的 tool_calls 遍历执行对应工具函数。
    """
    tool_result = Msg(
        role="tool",
        content="[模拟工具] 计算结果/搜索结果：42（离线演示数据）",
    )
    return {"messages": [tool_result], "needs_tool": False}


def should_continue(state: AgentState) -> Literal["tools", END]:
    """【条件边】根据状态决定下一步走向。

    返回值为边的名称：
    - 若还需要工具 -> 跳到 "tools" 节点
    - 否则          -> 结束（END）
    """
    if state["needs_tool"]:
        return "tools"
    return END


# =====================================================================
# 3. 组装图：StateGraph(状态类型) -> 添加节点 -> 添加边 -> 编译
# =====================================================================
graph = StateGraph(AgentState)

# 注册节点
graph.add_node("agent", call_model)   # LLM 推理节点
graph.add_node("tools", call_tool)    # 工具执行节点

# START -> agent：图的入口
graph.add_edge(START, "agent")

# agent 之后走条件边：根据 needs_tool 动态决定去向
graph.add_conditional_edges(
    "agent",
    should_continue,      # 决策函数
    {"tools": "tools", END: END},  # 返回值 -> 目标边映射
)

# tools 执行完后回到 agent，形成 ReAct 循环
graph.add_edge("tools", "agent")

# 编译成可执行对象
app = graph.compile()


# =====================================================================
# 4. 驱动图执行
# =====================================================================
# 用 ASCII 打印图结构（确认循环拓扑）
print("=" * 60)
print("【图结构】")
print("=" * 60)
try:
    print(app.get_graph().draw_ascii())
except Exception as e:  # 兼容不同版本接口
    print("(无法打印图结构:", e, ")")
print()


def run_example(user_input: str):
    """封装一次完整的图执行，并逐节点打印过程。"""
    print("=" * 60)
    print(f"【执行用户输入】{user_input}")
    print("=" * 60)

    initial_state: dict = {
        "messages": [Msg(role="user", content=user_input)],
        "steps": 0,
        "needs_tool": False,
    }

    # stream 模式：按节点执行顺序逐个 yield
    for event in app.stream(initial_state, stream_mode="values"):
        latest = event["messages"][-1]
        # 打印最新一条消息，模拟"节点输出"过程
        print(f"  [{latest['role']:>9}] {latest['content']}")

    print("-" * 60)
    final_messages = app.invoke(initial_state)["messages"]
    print(f"最终对话轮数：{len(final_messages)}")
    print(f"最终答案：{final_messages[-1]['content']}")
    print()


# 场景一：触发工具循环（包含"计算"关键词）
run_example("请帮我计算 1+1，然后给出结果。")

# 场景二：无需工具，直接回答
run_example("你好，介绍一下你自己。")