# 电商平台 LangChain 集成实战

本章将 LangChain 的能力落地到电商平台（Mall）的真实业务场景中，涵盖搜索 Agent、智能客服、商品推荐 Chain 三个核心模块，并给出完整代码示例与综合架构图。

---

## 1. 搜索 Agent（多工具路由）

电商搜索需要同时处理商品查询、库存查询、订单状态查询等多种请求。通过 LangChain 的 Agent 机制可以动态路由到不同的工具。

**工具定义：**

```python
from langchain.tools import tool

@tool
def search_product(query: str) -> str:
    """根据关键词搜索商品"""
    # 调用商品搜索引擎
    return f"商品搜索结果：{query}"

@tool
def check_stock(product_id: str) -> str:
    """查询商品库存"""
    return f"商品 {product_id} 库存：充足"

@tool
def query_order(order_id: str) -> str:
    """查询订单状态"""
    return f"订单 {order_id} 状态：已发货"
```

**Agent 构建：**

```python
from langchain.agents import create_react_agent, AgentExecutor
from langchain_openai import ChatOpenAI
from langchain.prompts import PromptTemplate

llm = ChatOpenAI(model="gpt-4", temperature=0)
tools = [search_product, check_stock, query_order]

prompt = PromptTemplate.from_template(
    "你是一个电商助手。请根据用户问题选择合适的工具。\n"
    "可用工具：{tools}\n\n"
    "用户问题：{input}\n"
    "思考过程：{agent_scratchpad}"
)

agent = create_react_agent(llm, tools, prompt)
agent_executor = AgentExecutor(agent=agent, tools=tools, verbose=True)

# 使用示例
result = agent_executor.invoke({"input": "帮我查一下订单 20240801 的状态，顺便看看 iPhone 15 还有货吗"})
print(result["output"])
```

**多工具路由的关键要点：**
- 工具的描述（docstring）直接影响 Agent 的选路准确性
- 可通过 `verbose=True` 观察 Agent 的思考过程，便于调试
- 生产环境建议为工具添加超时和重试机制

---

## 2. 智能客服（记忆+工具+知识库）

智能客服需要：长对话记忆、知识库检索、工单系统对接。LangChain 提供了完整的组件链。

```python
from langchain.memory import ConversationBufferWindowMemory
from langchain.chains import ConversationChain
from langchain.agents import create_openai_functions_agent
from langchain_community.vectorstores import FAISS
from langchain_openai import OpenAIEmbeddings

# 1. 对话记忆（保留最近 5 轮）
memory = ConversationBufferWindowMemory(
    k=5,
    memory_key="chat_history",
    return_messages=True
)

# 2. 知识库（FAISS 向量检索）
vectorstore = FAISS.load_local(
    "faiss_index",
    OpenAIEmbeddings(),
    allow_dangerous_deserialization=True
)
retriever = vectorstore.as_retriever(search_kwargs={"k": 3})

# 3. 工单工具
@tool
def create_ticket(issue: str, user_id: str) -> str:
    """创建售后工单"""
    return f"工单已创建，编号 TK-{user_id[-4:]}，请等待客服处理"

# 4. 组装客服 Agent
tools = [
    Tool(name="知识库检索", func=retriever.get_relevant_documents, description="查询商城退换货政策"),
    create_ticket
]

agent = create_openai_functions_agent(llm, tools, prompt)
executor = AgentExecutor(agent=agent, tools=tools, memory=memory, verbose=True)

# 对话示例
executor.invoke({"input": "我买的衣服尺码不对，想换货"})
executor.invoke({"input": "换货运费谁出？"})  # 第二次对话使用记忆
```

**架构要点：**
- 使用 `ConversationBufferWindowMemory` 避免上下文过长
- 知识库检索作为 Tool 接入，而非简单拼接上下文
- 工单创建等操作型工具独立为 Function Calling

---

## 3. 商品推荐 Chain

基于用户行为和商品属性，构建多阶段推荐链路。

```python
from langchain.chains import LLMChain, SequentialChain
from langchain.prompts import ChatPromptTemplate

# 阶段一：用户画像分析
profile_prompt = ChatPromptTemplate.from_template(
    "根据用户行为生成画像：\n行为：{behaviors}\n生成：用户偏好标签（3-5个）"
)
profile_chain = LLMChain(llm=llm, prompt=profile_prompt, output_key="profile")

# 阶段二：候选商品筛选
filter_prompt = ChatPromptTemplate.from_template(
    "根据画像筛选商品：\n画像：{profile}\n候选池：{candidates}\n筛选：最匹配的5个商品ID"
)
filter_chain = LLMChain(llm=llm, prompt=filter_prompt, output_key="filtered")

# 阶段三：生成推荐理由
reason_prompt = ChatPromptTemplate.from_template(
    "为每个商品生成推荐理由：\n商品：{filtered}\n用户画像：{profile}\n输出：JSON格式"
)
reason_chain = LLMChain(llm=llm, prompt=reason_prompt, output_key="recommendations")

# 串联成顺序链
recommend_chain = SequentialChain(
    chains=[profile_chain, filter_chain, reason_chain],
    input_variables=["behaviors", "candidates"],
    output_variables=["profile", "filtered", "recommendations"],
    verbose=True
)

result = recommend_chain.invoke({
    "behaviors": "浏览了3次运动鞋，收藏了2件冲锋衣，最近搜索'户外装备'",
    "candidates": "商品ID列表（1001-2000）"
})
```

**推荐链设计原则：**
- 分阶段处理：画像 -> 筛选 -> 解释，每步可独立优化
- 使用 `SequentialChain` 保持数据流清晰
- 最终输出 JSON 格式，方便前端直接渲染

---

## 4. 综合架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                     Mall AI 集成架构                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │   搜索 Agent      │  │   智能客服        │  │  推荐 Chain   │  │
│  │                   │  │                   │  │              │  │
│  │ ┌───┐ ┌───┐ ┌───┐│  │ ┌───┐ ┌───┐ ┌───┐│  │ ┌──┐ ┌──┐  │  │
│  │ │商品│ │库存│ │订单││  │ │记忆│ │知识│ │工单││  │ │画像│ │筛选│  │  │
│  │ │搜索│ │查询│ │查询││  │ │模块│ │库  │ │工具││  │ │分析│ │排序│  │  │
│  │ └───┘ └───┘ └───┘│  │ └───┘ └───┘ └───┘│  │ └──┘ └──┘  │  │
│  └──────────────────┘  └──────────────────┘  └──────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   共享基础设施层                          │   │
│  │  ┌──────────┐  ┌──────────┐  ┌────────┐  ┌──────────┐  │   │
│  │  │ OpenAI   │  │  FAISS   │  │ Redis  │  │ MySQL    │  │   │
│  │  │ LLM/GPT  │  │ 向量库   │  │ 缓存   │  │ 业务库    │  │   │
│  │  └──────────┘  └──────────┘  └────────┘  └──────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                     API 网关层                            │   │
│  │      /api/search     /api/chat      /api/recommend        │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. 完整代码示例

以下是整合三个模块的启动入口：

```python
# mall_ai_app.py — 完整可运行示例
import os
from langchain_openai import ChatOpenAI
from langchain.agents import create_react_agent, AgentExecutor
from langchain.memory import ConversationBufferWindowMemory
from langchain.chains import LLMChain, SequentialChain
from langchain.prompts import ChatPromptTemplate
from langchain.tools import tool

os.environ["OPENAI_API_KEY"] = "your-api-key"
llm = ChatOpenAI(model="gpt-4", temperature=0)

# ========== 工具层 ==========
@tool
def search_product(query: str) -> str:
    """根据关键词搜索商品"""
    return f"找到与「{query}」相关的商品 12 件"

@tool
def check_stock(product_id: str) -> str:
    """查询商品库存"""
    return f"商品 {product_id} 库存：充足，可下单"

# ========== 搜索 Agent ==========
search_agent = create_react_agent(
    llm, [search_product, check_stock],
    ChatPromptTemplate.from_template("{input}\n{agent_scratchpad}")
)
search_executor = AgentExecutor(
    agent=search_agent, tools=[search_product, check_stock], verbose=True
)

# ========== 智能客服 ==========
memory = ConversationBufferWindowMemory(k=3, memory_key="history")
chat_executor = AgentExecutor(
    agent=create_react_agent(llm, [search_product], ChatPromptTemplate.from_template("...")),
    tools=[search_product],
    memory=memory,
    verbose=True
)

# ========== 推荐 Chain ==========
profile_chain = LLMChain(
    llm=llm,
    prompt=ChatPromptTemplate.from_template("分析用户行为：{behaviors}"),
    output_key="profile"
)
recommend_chain = SequentialChain(
    chains=[profile_chain],
    input_variables=["behaviors"],
    output_variables=["profile"]
)

# ========== 统一入口 ==========
def handle_request(scene: str, params: dict) -> str:
    if scene == "search":
        return search_executor.invoke({"input": params["query"]})["output"]
    elif scene == "chat":
        return chat_executor.invoke({"input": params["message"]})["output"]
    elif scene == "recommend":
        return recommend_chain.invoke({"behaviors": params["behaviors"]})["profile"]
    else:
        return "未知场景"

if __name__ == "__main__":
    print(handle_request("search", {"query": "无线耳机"}))
    print(handle_request("chat", {"message": "我想退货"}))
    print(handle_request("recommend", {"behaviors": "浏览了3次运动鞋"}))
```

---

## 小结

本章通过三个真实业务场景展示了 LangChain 在电商平台中的集成方式：

| 场景 | 核心组件 | 关键考量 |
|------|----------|----------|
| 搜索 Agent | ReAct Agent + 多工具 | 工具描述准确性、路由策略 |
| 智能客服 | 记忆 + 知识库 + 工具 | 窗口大小、检索质量 |
| 推荐 Chain | SequentialChain | 分阶段可观测性、输出格式 |

生产环境建议为每个模块独立部署，通过 API 网关统一暴露，并使用 Redis 缓存高频查询结果以降低 LLM 调用成本。