# 01 · AI 智能搜索服务

> AI 搜索是智能商城的核心亮点，也是贯穿项目中 Python AI 技术栈的集中体现。本文基于 mall-ai-search 项目的源码分析，展示如何使用 FastAPI + LangChain Agent + Redis 向量库构建电商智能搜索系统。

---

## 一、服务架构

```
┌──────────────────────────────────────────────────────────────────────┐
│                       ai-search-gateway (FastAPI 9010)               │
│  Pydantic 统一响应  ·  全局异常处理  ·  APIRouter 模块化               │
│  /api/v1/recommend  ·  /api/v1/extract  ·  /api/v1/sync              │
└──────────────────────────────────────────────────────────────────────┘
           │                          │
           ▼                          ▼
┌──────────────────────┐   ┌──────────────────────────────────────────┐
│  条件提取 Chain       │   │      LangChain Agent (create_agent)      │
│  LLM + Pydantic      │   │  ┌────────────────────────────────────┐  │
│  OutputParser         │   │  │  Tool: vector_search_tool          │  │
│  → SearchCondition    │   │  │  → RedisVectorStore.similarity    │  │
│  {keyword, price}     │   │  │  → BGE-M3 Embedding               │  │
└──────────────────────┘   │  │  → HNSW 索引                       │  │
                           │  └────────────────────────────────────┘  │
                           │  ┌────────────────────────────────────┐  │
                           │  │  LangGraph InMemorySaver           │  │
                           │  │  (thread_id 会话记忆)              │  │
                           │  │  → ProductRecommendResponse        │  │
                           │  └────────────────────────────────────┘  │
                           └──────────────────────────────────────────┘
```

---

## 二、API 路由设计

### 2.1 路由总览

| 路由 | 方法 | 职责 | 同步/异步 |
|------|------|------|----------|
| `GET /api/v1/test` | 同步 | 健康检查 | 同步 |
| `GET /api/v1/sync` | 同步 | 触发数据同步（MySQL → Redis 向量库） | 同步 |
| `GET /api/v1/recommend` | 异步 | AI 商品推荐（Agent 核心链路） | 异步 |
| `GET /api/v1/extract` | 异步 | 查询条件提取（自然语言 → 结构化） | 异步 |

### 2.2 统一响应体

```python
from pydantic import BaseModel, Field
from typing import Generic, Optional, TypeVar

T = TypeVar("T")

class Result(BaseModel, Generic[T]):
    """接口通用返回体"""
    code: int = Field(default=200)
    msg: str = Field(default="操作成功")
    data: Optional[T] = None
```

**对比 Spring Boot：**

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code = 200;
    private String msg = "操作成功";
    private T data;
}
```

---

## 三、核心链路 1：条件提取（/extract）

### 3.1 流程

```
用户输入: "5000元以下续航强的华为手机"
    │
    ▼
1. PydanticOutputParser(SearchCondition) 生成格式说明
    │
    ▼
2. ChatPromptTemplate: system + human + format_instructions
    │
    ▼
3. LLM (通义千问, temperature=0.1) 执行推理
    │
    ▼
4. 输出: {"keyword": "华为手机", "min_price": 0, "max_price": 5000}
    │
    ▼
5. Pydantic 解析为 SearchCondition 对象
```

### 3.2 代码实现

```python
from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate

class SearchCondition(BaseModel):
    keyword: Optional[str] = Field(default=None, description="搜索关键词")
    min_price: float = Field(default=0, description="最低价格")
    max_price: float = Field(default=100000, description="最高价格")

async def extract_search_condition(self, query: str) -> SearchCondition:
    parser = PydanticOutputParser(pydantic_object=SearchCondition)

    prompt = ChatPromptTemplate.from_messages([
        ("system", SEARCH_EXTRACT_PROMPT),
        ("human", "用户查询：{query}")
    ]).partial(format_instructions=parser.get_format_instructions())

    extract_chain = prompt | self.llm | parser
    result = await extract_chain.ainvoke({"query": query})
    return result
```

### 3.3 提取提示词

```python
SEARCH_EXTRACT_PROMPT = """
你是商品搜索条件提取助手，根据用户查询提取商品筛选条件。
需要提取的参数：
1. keyword: 搜索关键词
2. min_price: 最低价格,若没有提取到值，min_price=0
3. max_price: 最高价格,若没有提取到值，max_price=100000
示例：
查询："我想买一个2000-4000元的小米手机"
返回：{"keyword": "小米手机", "min_price": 2000, "max_price": 4000}
只输出JSON，不要额外文字: {format_instructions}"""
```

---

## 四、核心链路 2：AI 推荐（/recommend）

### 4.1 Agent 执行流程

```
用户输入: "5000元以下续航强的华为手机"
    │
    ▼
┌──────────────────────────────────────────────────────────────────┐
│  Agent 执行循环 (LangGraph 驱动)                                  │
│                                                                  │
│  Step 1: Agent 接收消息 → 判断需要调用工具                          │
│      │                                                            │
│      ▼                                                            │
│  Step 2: 调用 vector_search_tool(query)                           │
│      │   → RedisVectorStore.similarity_search(query, k=10)        │
│      │   → 返回 TOP-10 商品文档 (page_content + metadata)         │
│      ▼                                                            │
│  Step 3: Agent 接收工具结果 → 判断是否需要更多工具                    │
│      │   → 不需要（本次搜索只有向量检索一个工具）                     │
│      ▼                                                            │
│  Step 4: Agent 生成最终输出                                        │
│      │   → LLM 基于召回结果生成推荐                                 │
│      │   → response_format 强制 ProductRecommendResponse 结构     │
│      ▼                                                            │
│  Step 5: 返回 structured_response                                  │
│      │   → {summary, product_list, reason}                        │
└──────────────────────────────────────────────────────────────────┘
```

### 4.2 代码实现

```python
from langchain.agents import create_agent
from langchain.tools import tool
from langgraph.checkpoint.memory import InMemorySaver

class SearchService:
    def __init__(self):
        # 1. LLM 实例
        self.llm = tools.get_model()

        # 2. 向量库
        self.vector_store = tools.get_vector_store()

        # 3. Checkpointer（会话记忆）
        self.checkpointer = InMemorySaver()

        # 4. 定义工具（闭包模式，捕获 self.vector_store）
        @tool
        def vector_search_tool(query: str) -> str:
            """商品向量检索工具，获取相关商品资料。"""
            docs = self.vector_store.similarity_search(query, k=10)
            return "\n".join(
                [f"{doc.page_content} | meta:{doc.metadata}" for doc in docs]
            )

        self.vector_search_tool = vector_search_tool

    async def recommend_product(self, query: str, thread_id=0):
        """商品推荐"""
        agent = create_agent(
            model=self.llm,
            tools=[self.vector_search_tool],
            system_prompt=SEARCH_PROMPT,
            checkpointer=self.checkpointer,
            response_format=ProductRecommendResponse
        )
        response = await agent.ainvoke(
            {"messages": query},
            {"configurable": {"thread_id": thread_id}}
        )
        return response["structured_response"]
```

### 4.3 推荐提示词（防幻觉设计）

```python
SEARCH_PROMPT = """
请严格依据上下文内真实信息回答用户问题，**严禁编造不存在的商品信息**。
规则要求：
1. 根据用户问题调用vector_search_tool工具检索商品知识库，返回的商品信息生成上下文；
2. 仅使用上下文存在的数据进行商品推荐，并给出推荐理由；
3. 若上下文没有匹配内容：summary="暂无相关信息"，product_list=[]，reason=[]；
4. 输出格式必须是纯粹标准JSON，禁止附带Markdown等任何额外文本；
5. 严格遵循输出字段结构，不随意增删字段。
"""
```

### 4.4 结构化输出模型

```python
class GoodsInfo(BaseModel):
    id: int = Field(description="商品ID")
    spuId: int = Field(description="SPU ID")
    skuName: str = Field(description="商品名称")
    price: float = Field(description="价格")
    image: str = Field(description="商品图片")

class ProductRecommendResponse(BaseModel):
    summary: str = Field(description="推荐导语")
    product_list: List[GoodsInfo] = Field(description="推荐商品列表")
    reason: List[str] = Field(description="推荐理由列表")
```

---

## 五、Agent vs Chain 的设计选择

| 对比项 | 条件提取 (/extract) | 商品推荐 (/recommend) |
|--------|--------------------|--------------------|
| 复杂度 | 简单：原文 → 结构化 | 复杂：检索 → 判断 → 生成 |
| 需要工具 | 否，纯文本理解 | 是，需向量检索 |
| 需要记忆 | 否，单次无状态 | 是，多轮对话 |
| 执行方式 | Chain（线性） | Agent（循环决策） |
| 框架 | `prompt \| llm \| parser` | `create_agent(model, tools, ...)` |

**设计原则：** 只在需要复杂度的地方引入复杂度。提取场景用 Chain 足够，推荐场景需要 Agent 的循环决策能力。

---

## 六、Java 对照（Spring AI 实现）

```java
@Configuration
public class AiSearchConfig {

    @Bean
    public ChatClient chatClient(AppSettings settings) {
        var openAiApi = new OpenAiApi(
            settings.getActiveLlmConfig().getBaseUrl(),
            settings.getActiveLlmConfig().getApiKey()
        );
        var chatModel = new OpenAiChatModel(openAiApi,
            OpenAiChatOptions.builder()
                .withTemperature(0.1d)
                .build());
        return ChatClient.builder(chatModel)
            .defaultSystem(SEARCH_SYSTEM_PROMPT)
            .defaultTools(new VectorSearchTool())
            .build();
    }
}

@Service
public class SearchService {

    private final ChatClient chatClient;

    public ProductRecommendResponse recommend(String query, String threadId) {
        return chatClient.prompt()
            .user(query)
            .call()
            .entity(ProductRecommendResponse.class);
    }

    public SearchCondition extract(String query) {
        return chatClient.prompt()
            .system(EXTRACT_SYSTEM_PROMPT)
            .user(query)
            .call()
            .entity(SearchCondition.class);
    }
}
```

---

> **下一篇：** [02-rag-customer-service.md](02-rag-customer-service.md) — RAG 智能客服系统