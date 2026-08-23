# 02 · RAG 智能客服系统

> RAG（Retrieval-Augmented Generation）客服系统是 AI 商城的第二大脑。它基于企业知识库，通过"检索 + 生成"的方式回答用户问题，并具备多轮对话记忆和证据门控防幻觉能力。

---

## 一、系统架构

```
┌──────────────────────────────────────────────────────────────────────┐
│                       ai-rag-service (FastAPI 9012)                  │
│  /api/rag/ask  ·  /api/rag/upload  ·  /api/rag/history              │
└──────────────────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────────────────────┐
│  RAG 核心流程                                                         │
│                                                                      │
│  用户问题                                                             │
│     │                                                                 │
│     ▼                                                                 │
│  ┌────────────────┐     ┌────────────────┐     ┌────────────────┐    │
│  │ 1. Embedding    │────→│ 2. 向量检索     │────→│ 3. 上下文构建   │    │
│  │ 用户问题→向量   │     │ 相似度 TOP-K    │     │ 检索结果+历史   │    │
│  └────────────────┘     └────────────────┘     └────────────────┘    │
│                                                         │            │
│                                                         ▼            │
│  ┌────────────────┐     ┌────────────────┐     ┌────────────────┐    │
│  │ 6. 证据门控     │←────│ 5. LLM 生成     │←────│ 4. Prompt 组装  │    │
│  │ 验证回答是否    │     │ 基于上下文回答   │     │ 系统+用户+证据  │    │
│  │ 基于证据       │     └────────────────┘     └────────────────┘    │
│  └────────────────┘                                                  │
│     │                                                                 │
│     ▼                                                                 │
│  最终回答（带证据引用）                                                │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 二、知识库构建

### 2.1 数据源

RAG 客服系统的知识库由以下数据源构成：

| 数据源 | 内容 | 更新频率 | 优先级 |
|--------|------|---------|--------|
| **商品 FAQ** | 常见商品问题（尺寸、材质、退换货） | 按需 | 高 |
| **售后政策** | 退换货规则、保修政策、运费说明 | 政策变更时 | 高 |
| **物流说明** | 配送范围、时效、费用 | 合作物流变更时 | 中 |
| **用户指南** | 账号注册、支付方式、优惠券使用 | 功能更新时 | 中 |

### 2.2 文档切片

```python
from langchain.text_splitter import RecursiveCharacterTextSplitter
import tiktoken

# token 计算函数
tokenizer = tiktoken.get_encoding("cl100k_base")

def tiktoken_len(text: str) -> int:
    return len(tokenizer.encode(text))

# 文档切片器
splitter = RecursiveCharacterTextSplitter(
    chunk_size=512,          # 每段 512 token（比搜索场景大，因为客服需要完整上下文）
    chunk_overlap=50,        # 50 token 重叠
    separators=["\n\n", "\n", "。", "！", "？", "，", " "],
    length_function=tiktoken_len,
    strip_whitespace=True,
)
```

**为什么客服场景 chunk_size 比搜索场景大？** 客服回答需要更完整的上下文，512 token 能容纳一篇完整的 FAQ 条目。搜索场景 256 token 足够匹配商品名和属性。

### 2.3 向量化存储

```python
from langchain_redis import RedisConfig, RedisVectorStore
from langchain_openai import OpenAIEmbeddings

# Embedding 实例（BGE-M3）
embeddings = OpenAIEmbeddings(
    base_url="https://api.siliconflow.cn/v1",
    model="BAAI/bge-m3",
)

# 向量存储（与 AI 搜索共用 Redis 实例，不同索引名）
config = RedisConfig(
    index_name="rag_knowledge_idx",  # 与搜索索引不同
    redis_url=os.getenv("REDIS_URL"),
)
vector_store = RedisVectorStore(embeddings=embeddings, config=config)
```

---

## 三、RAG 检索流程

### 3.1 检索策略

```python
class RAGRetriever:
    """RAG 检索器，支持多种检索策略"""

    def __init__(self, vector_store):
        self.vector_store = vector_store

    def retrieve(self, query: str, k: int = 5) -> List[Document]:
        """
        基础向量检索
        返回 TOP-K 相关文档作为 LLM 上下文
        """
        return self.vector_store.similarity_search(query, k=k)

    def retrieve_with_score(self, query: str, k: int = 5, threshold: float = 0.7):
        """
        带阈值的检索：低于相似度阈值的文档不返回
        用于证据门控：低相关度的知识不送给 LLM，减少幻觉
        """
        results = self.vector_store.similarity_search_with_relevance_scores(query, k=k)
        return [(doc, score) for doc, score in results if score >= threshold]
```

### 3.2 上下文构建

```python
def build_context(docs: List[Document], history: List[dict]) -> str:
    """
    构建 LLM 上下文
    格式：历史对话 + 检索到的知识片段
    """
    parts = []

    # 1. 历史对话
    if history:
        parts.append("【历史对话】")
        for msg in history[-3:]:  # 只取最近 3 轮
            role = "用户" if msg["role"] == "user" else "客服"
            parts.append(f"{role}: {msg['content']}")

    # 2. 知识片段
    if docs:
        parts.append("\n【相关知识】")
        for i, doc in enumerate(docs, 1):
            parts.append(f"[{i}] {doc.page_content}")

    return "\n".join(parts)
```

---

## 四、多轮对话记忆

### 4.1 LangGraph Checkpointer 实现

```python
from langgraph.checkpoint.memory import InMemorySaver
from langchain.agents import create_agent

class RAGService:
    def __init__(self):
        self.llm = tools.get_model()
        self.vector_store = tools.get_vector_store()
        self.checkpointer = InMemorySaver()

    async def ask(self, query: str, thread_id: str) -> RAGResponse:
        """
        RAG 客服问答
        thread_id 用于会话隔离，同一个用户的多轮问题共享上下文
        """
        # 1. 检索相关知识
        docs = self.vector_store.similarity_search(query, k=5)

        # 2. 构建对话上下文
        #    Checkpointer 自动恢复 thread_id 对应的历史消息
        #    Agent 在系统提示词中注入检索到的知识

        # 3. 创建 RAG Agent
        agent = create_agent(
            model=self.llm,
            tools=[],  # 纯 RAG 不需要额外工具
            system_prompt=self._build_rag_prompt(docs),
            checkpointer=self.checkpointer,
            response_format=RAGResponse,
        )

        # 4. 执行（自动附带历史消息）
        response = await agent.ainvoke(
            {"messages": query},
            {"configurable": {"thread_id": thread_id}}
        )
        return response["structured_response"]

    def _build_rag_prompt(self, docs: List[Document]) -> str:
        """构建 RAG 系统提示词"""
        context = "\n".join([doc.page_content for doc in docs])
        return f"""
你是智能商城的 AI 客服，请根据以下知识库内容回答用户问题。

【知识库内容】
{context}

【回答规则】
1. 严格基于知识库内容回答，不要编造信息
2. 如果知识库中没有相关信息，请说"抱歉，我暂时无法回答这个问题"
3. 引用知识来源时标注来源编号
4. 保持友好、专业的客服语气
5. 涉及售后、退款等操作时，引导用户联系人工客服
"""
```

### 4.2 会话隔离

```
用户 A (thread_id = "user_a_001")    用户 B (thread_id = "user_b_001")
    │                                        │
    ▼                                        ▼
┌──────────────────────┐         ┌──────────────────────┐
│  Checkpointer 状态    │         │  Checkpointer 状态    │
│  messages: [          │         │  messages: [          │
│    user: "怎么退款"    │         │    user: "发货时间"    │
│    assistant: "...",  │         │    assistant: "...",  │
│    user: "多久到账"   │         │    user: "快递公司"    │
│  ]                   │         │  ]                   │
│  → 知道"多久"指退款   │         │  → 知道"快递"指发货   │
└──────────────────────┘         └──────────────────────┘
     ▲                                   ▲
     └────────── 完全隔离，互不影响 ────────┘
```

---

## 五、证据门控防幻觉

### 5.1 三层防幻觉机制

```
Layer 1: 检索质量门控
  相似度阈值过滤（score < 0.7 的不送入上下文）
  确保 LLM 只看到"足够相关"的知识

Layer 2: Prompt 约束
  "严格基于知识库内容回答，不要编造信息"
  "如果知识库中无相关信息，请说无法回答"

Layer 3: 输出验证
  回答后验证：回答中的关键事实是否在检索结果中
  未通过验证的回答降级为"抱歉，我无法确认"
```

### 5.2 输出验证示例

```python
def verify_response(response: RAGResponse, source_docs: List[Document]) -> bool:
    """
    验证 LLM 回答是否基于提供的知识
    简单策略：检查回答中的关键实体是否出现在知识片段中
    """
    response_text = f"{response.answer} {response.reason}"
    source_text = " ".join([doc.page_content for doc in source_docs])

    # 提取关键实体（简化版，生产环境可用 NER）
    # 这里只做关键词交叉验证
    key_phrases = extract_key_phrases(response_text)
    for phrase in key_phrases:
        if phrase not in source_text:
            # 回答中的关键信息不在知识库中，可能编造
            return False
    return True
```

---

## 六、Spring Boot 对照

```java
// Spring AI 中的 RAG 实现
@Service
public class RAGService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RAGResponse ask(String query, String threadId) {
        // 1. 检索相关文档
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.query(query).withTopK(5)
        );

        // 2. 构建上下文
        String context = docs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n"));

        // 3. 调用 LLM
        return chatClient.prompt()
            .system(systemPrompt(context))
            .user(query)
            .call()
            .entity(RAGResponse.class);
    }
}
```

---

> **下一篇：** [03-llm-providers.md](03-llm-providers.md) — 多 LLM Provider 配置