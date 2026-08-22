# 07 · LangChain Agent 机制：create_agent + Tool + 结构化输出

> Agent 是 AI 搜索的"决策中枢"。它自主决定"需不需要检索商品"→"检索什么"→"如何组织推荐"，最终输出结构化结果。本篇是整套技术栈中**最核心**的一篇。
>
> **对应项目：** `src/smart_search/core/search_service.py`

---

## 一、基础概念

### 1.1 什么是 Agent（智能体）

传统 LLM 调用模式是**一问一答**：

```
用户输入 → LLM → 文本输出
```

Agent 模式是**循环决策**的：

```
用户输入 → LLM → 需要更多信息？→ 调用工具 → 整合结果 → 输出
                          ↑_____________________________↓
```

**Agent = LLM（大脑）+ Tools（手脚）+ 循环决策（执行逻辑）**

### 1.2 为什么需要 Agent

场景 | 纯 LLM | Agent
-----|--------|------
"5000元以下华为手机" | 凭训练数据编造 | 调向量库检索真实商品，再回答 |
"推荐理由是什么" | 无法记住上下文 | 通过 thread_id 恢复会话 |
"输出 JSON 格式" | 可能输出非标 JSON | response_format 强制结构化 |

**Agent 的核心价值：** 让 LLM 不是"凭记忆回答"，而是"有能力获取真实信息后再回答"。

### 1.3 LangChain 中的 Agent 演变

```
V1 (Legacy):  AgentExecutor + ZeroShotAgent
  → 手动构建 prompt，复杂

V2 (Current): create_agent() → 本项目使用
  → 简化创建，内置 tool binding、response_format

V3 (Future):   LangGraph 原生 Agent
  → 更精细的状态控制
```

---

## 二、进阶机制

### 2.1 create_agent API 深度解析

项目中的核心调用：

```python
from langchain.agents import create_agent

agent = create_agent(
    model=self.llm,                    # ChatOpenAI 实例
    tools=[self.vector_search_tool],   # 可调用的工具列表
    system_prompt=self.search_prompt,  # 系统提示词
    checkpointer=self.checkpointer,    # LangGraph 记忆
    response_format=ProductRecommendResponse,  # 结构化输出
)
```

**源码底层逻辑（伪代码）：**

```python
def create_agent(model, tools, system_prompt, checkpointer, response_format):
    # 1. 将工具绑定到模型
    model_with_tools = model.bind_tools(tools)

    # 2. 构建 SystemPrompt
    prompt = SystemMessage(system_prompt)

    # 3. 构造执行图（实际是 LangGraph）
    graph = StateGraph(AgentState)
    graph.add_node("agent", call_model(model_with_tools, prompt))
    graph.add_node("tools", call_tools(tools))

    # 4. 条件边：如果模型调用了工具 → 进入工具节点
    #         如果模型未调用工具 → 生成最终输出
    graph.add_conditional_edges("agent", should_continue, {
        "continue": "tools",
        "end": "__end__"
    })
    graph.add_edge("tools", "agent")

    # 5. 绑定 checkpointer 实现记忆
    graph.compile(checkpointer=checkpointer)

    # 6. response_format 绑定 tool_strategy 强制结构化输出
    graph.with_structured_output(response_format)

    return graph
```

### 2.2 Agent 执行循环

```
用户输入: "5000元以下续航强的华为手机"
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  Agent 执行循环                                              │
│                                                             │
│  ┌──────────┐     ┌──────────┐     ┌──────────┐            │
│  │ Agent    │────→│ Tool     │────→│ Agent    │            │
│  │ 判断:    │     │ 向量检索  │     │ 生成:    │            │
│  │ 需要检索  │     │ TOP-10   │     │ 推荐+理由 │            │
│  │ 调用工具  │     │ + 记忆   │     │ 结构化   │            │
│  └──────────┘     └──────────┘     └──────────┘            │
│       │                                                    │
│       │ 不需要工具？                                        │
│       └──→ 直接输出（本场景不会发生）                         │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
ProductRecommendResponse
```

**关键特性：** Agent 会自主判断是否调用工具。如果用户问"今天天气怎么样"，Agent 不调用向量搜索工具，直接回复。但在本项目场景中，每次推荐都会调用工具。

### 2.3 Tool（工具）定义

```python
@tool
def vector_search_tool(query: str) -> str:
    """
    商品向量检索工具，获取相关商品资料。
    Args:
        query: 用户问题
    return: 商品信息列表
    """
    docs = self.vector_store.similarity_search(query, k=10)
    return "\n".join([f"{doc.page_content} | meta:{doc.metadata}" for doc in docs])
```

**`@tool` 装饰器做了什么：**

```python
# 装饰器将函数转为 Tool 对象，自动：
# 1. 提取函数名 → tool name
# 2. 提取函数文档字符串 → tool description
# 3. 提取参数类型注解 → tool schema
# 4. 绑定到 LLM 的 tool calling 能力

vector_search_tool = Tool(
    name="vector_search_tool",
    description="商品向量检索工具，获取相关商品资料。",
    args_schema={"query": {"type": "string", "description": "用户问题"}},
    func=__wrapped_function,
)
```

**Tool 的定义质量直接影响 Agent 执行效果：**
- **name：** 清晰的名字，LLM 能理解
- **description：** 描述什么时候调用这个工具（LLM 据此判断）
- **args_schema：** 参数限定，LLM 自动填充

### 2.4 response_format —— 结构化输出

```python
response_format=ProductRecommendResponse
```

这是 `langchain >= 1.2.10` 引入的新特性，原理是：

```
1. 将 Pydantic 模型转换为 JSON Schema
2. 通过 tool_strategy 注册为 LLM 的 tool calling
3. LLM 在生成最终输出时，以 tool call 形式返回结构化数据
4. LangChain 自动将 tool call 解析为 Pydantic 实例
```

**好处：**
- 零解析烦恼——不需要手写 `JsonOutputParser`
- 强类型约束——LLM 必须生成符合 Schema 的结构
- 天然支持嵌套——`ProductRecommendResponse` 包含 `List[GoodsInfo]`

---

## 三、项目现场

### 3.1 SearchService 完整源码

```python
class SearchService:
    def __init__(self):
        # 1. 大语言模型
        self.llm = tools.get_model()

        # 2. Prompt
        self.search_prompt = prompt.SEARCH_PROMPT

        # 3. 向量库
        self.vector_store = tools.get_vector_store()

        # 4. 记忆（LangGraph Checkpointer）
        self.checkpointer = InMemorySaver()

        # 5. 定义工具（@tool 装饰器内嵌）
        @tool
        def vector_search_tool(query: str) -> str:
            """商品向量检索工具，获取相关商品资料。"""
            docs = self.vector_store.similarity_search(query, k=10)
            return "\n".join([f"{doc.page_content} | meta:{doc.metadata}" for doc in docs])

        self.vector_search_tool = vector_search_tool

    async def recommend_product(self, query: str, thread_id=0):
        """商品推荐"""
        agent = create_agent(
            model=self.llm,
            tools=[self.vector_search_tool],
            system_prompt=self.search_prompt,
            checkpointer=self.checkpointer,
            response_format=ProductRecommendResponse
        )
        response = await agent.ainvoke(
            {"messages": query},
            {"configurable": {"thread_id": thread_id}}
        )
        return response["structured_response"]
```

### 3.2 关键设计细节

**1. 为什么 `@tool` 定义在 `__init__` 内部？**

因为 `vector_search_tool` 依赖 `self.vector_store`，而 `self.vector_store` 在构造函数中初始化。如果把 `@tool` 定义在类方法层面，就无法访问 `self.vector_store`。这是**闭包模式**——工具函数通过闭包捕获外部变量。

**2. `await agent.ainvoke(...)` 异步调用**

Agent 执行是异步的，因为：
- 内部调用 LLM API（网络 IO）
- 可能调用工具（向量检索，Redis 网络 IO）
- 异步等待各步骤完成

**3. `thread_id` 的作用**

LangGraph Checkpointer 以 `thread_id` 为粒度保存会话状态。同一个 `thread_id` 的多次调用共享记忆，不同 `thread_id` 隔离。

### 3.3 条件提取（/extract）—— 非 Agent 模式

```python
async def extract_search_condition(self, query: str) -> SearchCondition:
    """商品搜索条件结构化提取"""
    parser = PydanticOutputParser(pydantic_object=SearchCondition)

    prompt = ChatPromptTemplate.from_messages([
        ("system", self.search_extract_prompt),
        ("human", "用户查询：{query}")
    ]).partial(format_instructions=parser.get_format_instructions())

    # 简单链式调用，不需要 Agent
    extract_chain = prompt | self.llm | parser
    result = await extract_chain.ainvoke({"query": query})
    return result
```

**为什么这里不用 Agent 而用 Chain？**

| | 条件提取 (/extract) | 商品推荐 (/recommend) |
|--|-------------------|--------------------|
| 复杂度 | 简单：原文→结构化 | 复杂：检索→上下文→判断→输出 |
| 需要工具 | 否，纯文本理解 | 是，需向量检索 |
| 需要记忆 | 否，单次无状态 | 是，多轮对话 |
| 执行方式 | Chain（线性） | Agent（循环决策） |

**"好的架构是：只在需要复杂度的地方引入复杂度。"**

---

## 四、Java 对照

### 4.1 Spring AI 中的 Agent 概念

Java 生态中 Spring AI 还没有直接等价于 `create_agent` 的 API，但可以通过 `ToolCallback` 实现类似机制：

```java
@Service
public class RecommendAgentService {

    private final ChatClient chatClient;
    private final VectorSearchService vectorSearchService;

    public RecommendAgentService(ChatClient.Builder builder, VectorSearchService vs) {
        // 构造带工具调用的 ChatClient
        this.chatClient = builder
            .defaultSystem(SEARCH_SYSTEM_PROMPT)
            .defaultTools(new VectorSearchTool(vs))  // 注册工具
            .build();
        this.vectorSearchService = vs;
    }

    public ProductRecommendResponse recommend(String query, String threadId) {
        return chatClient.prompt()
            .user(query)
            .call()
            .entity(ProductRecommendResponse.class);  // 结构化输出
    }

    // 工具定义
    public static class VectorSearchTool implements ToolCallback {
        private final VectorSearchService vs;

        @Override
        public String getName() { return "vector_search_tool"; }

        @Override
        public String getDescription() {
            return "商品向量检索工具，获取相关商品资料。";
        }

        @Override
        public String call(String query) {
            return vs.similaritySearch(query, 10);
        }
    }
}
```

### 4.2 对照总结

| 维度 | Python (LangChain) | Java (Spring AI) |
|------|-------------------|-----------------|
| 创建 Agent | `create_agent(model, tools, ...)` | `ChatClient.builder().defaultTools(tool)` |
| 工具定义 | `@tool` 装饰器 | `ToolCallback` 接口实现 |
| 结构化输出 | `response_format=PydanticModel` | `.entity(PydanticModel.class)` |
| 记忆（Checkpointer） | `InMemorySaver` / `RedisSaver` | 需自行实现 `ConversationMemory` |
| 执行循环 | 自动（LangGraph 内置） | 需自行管理循环 |

---

## 五、最小可复现示例

### 5.1 完整 Agent 流程

```python
# agent_demo.py
# 需要: pip install langchain langchain-openai langchain-core
from langchain.agents import create_agent
from langchain.tools import tool
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.memory import InMemorySaver
from pydantic import BaseModel, Field
from typing import List

class RecommendResult(BaseModel):
    summary: str = Field(description="推荐导语")
    products: List[str] = Field(description="推荐商品列表")
    reasons: List[str] = Field(description="推荐理由")

def demo_agent():
    """演示 Agent 的完整流程"""

    # 1. 模拟工具
    @tool
    def search_products(query: str) -> str:
        """模拟商品检索"""
        # 真实场景这里调 Redis 向量库
        products = {
            "华为": "华为Pura 70 4999元, 华为Mate 60 5499元",
            "苹果": "iPhone 16 5999元",
            "手机": "华为Pura 70 4999元, iPhone 16 5999元, 小米14 3999元",
        }
        for key, val in products.items():
            if key in query:
                return val
        return "未找到相关商品"

    # 2. 创建 LLM
    llm = ChatOpenAI(
        base_url="https://api.siliconflow.cn/v1",
        api_key="your-api-key",
        model="Qwen/Qwen2.5-7B-Instruct",
        temperature=0.1,
    )

    # 3. 创建 Agent
    agent = create_agent(
        model=llm,
        tools=[search_products],
        system_prompt="请根据用户查询检索商品，基于检索结果给出推荐。",
        checkpointer=InMemorySaver(),
        response_format=RecommendResult,
    )

    # 4. 执行
    result = agent.invoke(
        {"messages": "5000元以下的华为手机"},
        {"configurable": {"thread_id": "test_001"}}
    )

    response = result["structured_response"]
    print(f"总结: {response.summary}")
    print(f"推荐商品: {response.products}")
    print(f"理由: {response.reasons}")

    return response
```

### 5.2 验证 Agent 的 Tool 调用逻辑

```python
def test_agent_tool_calling():
    """验证 Agent 是否真的调用了工具"""

    tool_called = False

    @tool
    def trackable_tool(query: str) -> str:
        nonlocal tool_called
        tool_called = True
        return f"模拟商品: {query}"

    llm = ChatOpenAI(temperature=0.1, ...)
    agent = create_agent(
        model=llm,
        tools=[trackable_tool],
        system_prompt="必须调用工具才能回答。",
        response_format=RecommendResult,
    )

    result = agent.invoke({"messages": "华为手机"})
    assert tool_called, "Agent 应该调用工具"
    assert result["structured_response"] is not None, "应有结构化输出"
```

---

## 六、面试要点

### Q1: Agent 和 Chain 的区别是什么？

**回答思路：** Chain 是线性的、确定的执行路径（A→B→C），Agent 是循环的、**自主决策**的执行路径（LLM 判断→调用工具→再判断→输出）。Agent 适合需要外部信息获取和动态决策的场景，Chain 适合确定性的处理流程。

### Q2: create_agent 的 response_format 是如何工作的？

**回答思路：** 底层原理是将 Pydantic 模型转为 JSON Schema，通过 tool_strategy 注册为 LLM 的 tool calling 能力。LLM 在生成最终输出时以 tool call 形式返回结构化数据，LangChain 自动解析为 Pydantic 实例。好处是零解析烦恼、强类型约束、天然嵌套支持。

### Q3: 项目中 /extract 用 Chain、/recommend 用 Agent，为什么这样设计？

**回答思路：** 条件提取是"一段文本→结构化"的简单映射，Chain 够了。推荐需要"检索→判断→生成"的循环，Agent 更合适。**只在需要复杂度的地方引入复杂度。**

### Q4: @tool 装饰器做了什么？工具定义质量如何影响 Agent？

**回答思路：** 自动提取函数名、文档字符串、参数类型为 Tool 对象。工具质量直接影响 Agent 执行：name 要清晰，description 要告诉 LLM 什么场景下调用此工具，args_schema 限制参数格式。**LLM 通过 description 判断"要不要调用这个工具"**。

### Q5: 在 Java 生态中如何实现类似 Agent 的能力？

**回答思路：** Spring AI 的 `ChatClient` + `ToolCallback` 可以实现类似机制。但 Java 生态的 Agent 能力不如 Python 成熟，记忆管理（Checkpointer）需要自行实现。大规模 Agent 场景建议用 Python 或使用 LangChain4j。

---

> **下一篇：** [08-LANGGRAPH-MEMORY.md —— LangGraph 记忆与状态管理：Checkpointer + InMemorySaver](./08-LANGGRAPH-MEMORY.md)
>
> Agent 的"记忆"从哪来？看 LangGraph 如何通过 Checkpointer 实现会话级的对话记忆。