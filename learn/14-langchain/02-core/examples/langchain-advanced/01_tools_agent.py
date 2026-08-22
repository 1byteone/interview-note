# -*- coding: utf-8 -*-
"""
01_tools_agent.py
=================
LangChain Agent（智能体）与工具（Tools）演示

演示内容：
1. @tool 装饰器自定义工具（加法器 / 取反）
2. TavilySearchResults 联网搜索工具（可选），并提供假搜索工具作为离线备选
3. create_openai_tools_agent 创建 Agent（OpenAI 函数调用协议）
4. AgentExecutor 执行 Agent（支持多步推理 + 工具调用循环）

【运行前提】（需要 API Key）
- .env 中配置 OPENAI_API_KEY；如使用联网搜索还需 TAVILY_API_KEY
   （在 https://tavily.com 免费注册获取）
- 若没有 Tavily Key，脚本会自动退化为使用内置的假搜索工具（FakeSearchTool），
   便于离线演示 Agent 的工具调用机制

运行方式：
    python 01_tools_agent.py
"""
import os

from dotenv import load_dotenv

from langchain.agents import (
    AgentExecutor,
    create_openai_tools_agent,
)
from langchain.pydantic_v1 import BaseModel, Field
from langchain.tools import tool
from langchain.tools.tavily_search import TavilySearchResults
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_openai import ChatOpenAI

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
TAVILY_API_KEY = os.getenv("TAVILY_API_KEY")

llm = ChatOpenAI(
    model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
    temperature=0,
    api_key=OPENAI_API_KEY,
)


# =====================================================================
# 1. @tool 装饰器：把普通 Python 函数变成 Agent 可调用的工具
#    函数名 -> 工具名；docstring -> 工具描述（LLM 靠它决定何时调用）
# =====================================================================
@tool
def add_numbers(a: int, b: int) -> int:
    """计算两个整数的和。LLM 需要做加法时请调用此工具。"""
    return a + b


# 也可以给工具提供更精确的参数 schema（供 LLM 生成参数断言）
class NegateInput(BaseModel):
    number: int = Field(description="需要取反的整数")


@tool(args_schema=NegateInput)
def negate(number: int) -> int:
    """对一个整数取相反数（如 5 -> -5）。"""
    return -number


# =====================================================================
# 2. 联网搜索工具（可选） + 离线假搜索工具（备选）
# =====================================================================
tools = [add_numbers, negate]  # 自定义工具始终可用


def build_search_tool():
    """根据是否有 TAVILY_API_KEY 选择真实搜索工具或假搜索工具。"""
    if TAVILY_API_KEY:
        print("[Info] 检测到 TAVILY_API_KEY，使用 Tavily 联网搜索工具。")
        return TavilySearchResults(max_results=3)
    print("[Info] 未检测到 TAVILY_API_KEY，使用内置假搜索工具（离线演示）。")
    return _FakeSearchTool()


class _FakeSearchTool:
    """离线假搜索工具：伪造搜索接口，仅用于演示 Agent 调用工具的完整流程。

    真正的项目中应替换为 Tavily / SerpAPI / 自建搜索引擎。
    """

    # Agent 通过 name + description 识别工具能力
    name = "fake_search"
    description = "搜索互联网信息。当需要查询实时、最新或外部信息时使用此工具。"

    def run(self, query: str) -> str:
        # 模拟搜索结果
        return (
            f"[FakeSearch] 关于「{query}」的模拟搜索结果："
            "根据 2026 年的公开资料，该话题的要点包括……（离线演示数据）"
        )


search_tool = build_search_tool()
# Tavily 与假工具接口不同，统一包一层 run，方便 Agent 调用
if isinstance(search_tool, TavilySearchResults):
    tools.append(search_tool)
else:
    from langchain.tools import tool as lc_tool

    @lc_tool
    def search_web(query: str) -> str:
        """搜索最新互联网信息，例如新闻、股票、天气等实时数据。"""
        return search_tool.run(query)

    tools.append(search_web)


# =====================================================================
# 3. create_openai_tools_agent：构建 Agent
#    把 llm + tools + prompt 绑定，特点：
#    - 模型自主决定：是否需要调用工具、调用哪个、参数是什么
#    - 工具返回后模型继续推理，直到给出最终答案
# =====================================================================
system_prompt = (
    "你是一个乐于助人的 AI 助手。你可以使用工具解决数学计算和搜索任务；"
    "当工具结果不足以回答时，如实告诉用户。"
)

prompt = ChatPromptTemplate.from_messages(
    [
        ("system", system_prompt),
        # 历史对话占位符（多轮记忆可用 Memory 实现，此处固定为空）
        MessagesPlaceholder(variable_name="chat_history"),
        ("human", "{input}"),
        # Agent 的思考过程 / 工具调用记录占位符
        MessagesPlaceholder(variable_name="agent_scratchpad"),
    ]
)

agent = create_openai_tools_agent(llm=llm, tools=tools, prompt=prompt)

# =====================================================================
# 4. AgentExecutor：负责驱动 Agent 运转（推理 -> 调工具 -> 再推理）
#    verbose=True 会打印中间思考与工具调用日志，方便学习
# =====================================================================
agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    verbose=True,
    handle_parsing_errors=True,  # 模型输出解析失败时给出友好提示
    max_iterations=5,            # 防止无限循环
)

# ---- 场景一：纯工具调用（加法）----
print("=" * 60)
print("【场景一】数学计算：987 + 654 等于多少？")
print("=" * 60)
resp1 = agent_executor.invoke({"input": "请计算 987 + 654 等于多少？"})
print(f"最终答案：{resp1['output']}")
print()

# ---- 场景二：搜索（若使用假工具则为模拟结果）----
print("=" * 60)
print("【场景二】搜索：LangChain 发布的最新版本是多少？")
print("=" * 60)
resp2 = agent_executor.invoke({"input": "请搜索一下 LangChain 最新版本号。"})
print(f"最终答案：{resp2['output']}")
print()

# ---- 场景三：组合调用（多个工具协作）----
print("=" * 60)
print("【场景三】组合：先查 20 与 15 的和，再取相反数")
print("=" * 60)
resp3 = agent_executor.invoke(
    {"input": "先计算 20 加 15 的结果，再对这个结果取相反数。"}
)
print(f"最终答案：{resp3['output']}")