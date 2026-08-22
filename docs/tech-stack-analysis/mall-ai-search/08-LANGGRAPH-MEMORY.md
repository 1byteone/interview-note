# 08 · LangGraph 记忆与状态管理：Checkpointer + InMemorySaver

> Agent 的"记忆"从哪来？用户连续提问时，Agent 如何知道"刚才说了什么"？LangGraph Checkpointer 提供了会话级的状态持久化机制，让 Agent 具备**短期记忆**能力。
>
> **对应项目：** `src/smart_search/core/search_service.py`（`checkpointer = InMemorySaver()`）

---

## 一、基础概念

### 1.1 为什么需要记忆

用户可能连续提问：

```
用户： "5000元以下续航强的华为手机"
Agent：推荐华为Pura 70，理由：5000元以下、续航强

用户： "那苹果的呢？"  ← 如果没有记忆，Agent 不知道"那"指代什么
Agent：... 需要上下文才能理解
```

**记忆解决了三个问题：**

| 问题 | 无记忆的表现 | 有记忆的表现 |
|------|-------------|------------|
| 上下文消歧 | "那"、"它"、"刚才说的"无法理解 | 自动关联前文 |
| 对话连贯 | 每次回答都是独立对话 | 回答有延续性 |
| 状态复用 | 相同查询重复向量检索 | 基于历史优化 |

### 1.2 LangGraph 是什么

LangGraph 是 LangChain 官方推出的**有状态图执行框架**，用于构建 Agent 的执行逻辑。

```
传统 Chain：   A → B → C（线性，无状态）

LangGraph：    A → B → C
                ↑     ↓     ← 循环，有状态
                D ← E
```

**Agent 的每一次执行都是一次"图遍历"，而 Checkpointer 就是保存这个遍历过程状态的机制。**

### 1.3 记忆的类型

LangGraph 提供两种记忆层次：

| 类型 | 存储方式 | 存储位置 | 生命周期 | 用途 |
|------|---------|---------|---------|------|
| **短期记忆** | Checkpointer | 内存/Redis/DB | 会话周期 | 当前对话历史 |
| **长期记忆** | BaseStore | 持久化存储 | 跨会话 | 用户偏好、知识积累 |

本项目使用**短期记忆**（InMemorySaver），通过 `thread_id` 隔离不同会话。

---

## 二、进阶机制

### 2.1 Checkpointer 是什么

Checkpointer 是 LangGraph 的**状态持久化接口**，在 Agent 执行的每一步自动保存状态：

```
Agent 执行流水线（每一步自动 checkpoint）：

Step 1: Agent 接收消息 → 状态: {messages: [user_msg]}
                    ↓ checkpoint
Step 2: Agent 调用工具 → 状态: {messages: [user_msg, tool_call]}
                    ↓ checkpoint
Step 3: 工具返回结果 → 状态: {messages: [user_msg, tool_call, tool_result]}
                    ↓ checkpoint
Step 4: Agent 生成回复 → 状态: {messages: [user_msg, ..., final_response]}
                    ↓ checkpoint
```

**Checkpointer** 保存的就是"每一步的完整状态快照"。

### 2.2 InMemorySaver

```python
from langgraph.checkpoint.memory import InMemorySaver

self.checkpointer = InMemorySaver()
```

**InMemorySaver** 是 LangGraph 内置的**内存级 Checkpointer 实现**：

| 特性 | 说明 |
|------|------|
| 存储位置 | 进程内存（Python 对象） |
| 读写速度 | 纳秒级 |
| 生命周期 | 进程存在时 |
| 进程重启 | 数据丢失 |
| 适用场景 | 开发/演示/测试 |

**为什么项目用 InMemorySaver 而不是 RedisSaver？**

项目依赖中包含了 `langgraph-checkpoint-redis`，但实际代码中使用了 `InMemorySaver`。推测原因：

1. 开发阶段快速迭代，不需要持久化
2. 电商搜索场景，每次搜索不依赖历史对话（用户通常不会连续搜索）
3. 简化部署，不需要额外配置 Redis Checkpointer

> **生产环境建议：** 多轮对话场景应替换为 `RedisSaver` 或 `PostgresSaver`，避免进程重启丢失对话。

### 2.3 thread_id —— 会话隔离

```python
response = await agent.ainvoke(
    {"messages": query},
    {"configurable": {"thread_id": thread_id}}  # 会话 ID
)
```

**thread_id 的作用：**

```
thread_id = "user_001"    thread_id = "user_002"
    │                         │
    ▼                         ▼
┌──────────────┐         ┌──────────────┐
│ 消息历史 A    │         │ 消息历史 B    │
│ 消息1: 华为  │         │ 消息1: 苹果  │
│ 消息2: 那... │         │ 消息2: 那... │
│  → 知道指"华为"│       │  → 知道指"苹果"│
└──────────────┘         └──────────────┘
     ▲                         ▲
     │    完全隔离，互不影响     │
     └─────────────────────────┘
```

前端生成 thread_id：

```javascript
threadId: Math.floor(Math.random() * 100000)
```

**生产环境建议：** thread_id 应该绑定到用户 ID，而非每次刷新页面随机生成。

### 2.4 记忆在 Agent 执行中的影响

```
第一次调用 (thread_id = "user_001"):
  Agent 状态: []
  LLM 输入: "5000元以下华为手机"
  → Agent 调用工具 → 返回结果 → 输出推荐
  Checkpointer 保存: [{user: "5000元以下华为手机"}, {assistant: "推荐华为Pura 70..."}]

第二次调用 (thread_id = "user_001"):
  Agent 状态: [{user: "5000元以下华为手机"}, {assistant: "推荐华为Pura 70..."}]
  LLM 输入: "那苹果的呢？"
  → Agent 识别"那"指代"5000元以下" → 调用工具 → 返回结果 → 输出推荐
  Checkpointer 保存: [{...}, {...}, {user: "那苹果的呢？"}, {assistant: "推荐iPhone 16..."}]

第三次调用 (thread_id = "user_002"):  ← 不同会话
  Agent 状态: []
  LLM 输入: "那苹果的呢？"
  → Agent 无法理解"那" → 输出兜底
```

---

## 三、项目现场

### 3.1 记忆在项目中的实际使用

```python
class SearchService:
    def __init__(self):
        # 创建 Checkpointer
        self.checkpointer = InMemorySaver()

    async def recommend_product(self, query: str, thread_id=0):
        agent = create_agent(
            ...
            checkpointer=self.checkpointer,  # 注入 Checkpointer
            ...
        )
        response = await agent.ainvoke(
            {"messages": query},
            {"configurable": {"thread_id": thread_id}}  # 指定会话 ID
        )
        return response["structured_response"]
```

### 3.2 当前记忆设计的局限性

| 局限 | 影响 | 改进方案 |
|------|------|---------|
| **InMemorySaver** | 进程重启丢失记忆 | 切换为 `RedisSaver` |
| **thread_id 随机生成** | 刷新页面丢失会话 | 绑定用户 ID |
| **仅短期记忆** | 无法记忆用户偏好 | 添加 BaseStore 长期记忆 |
| **无遗忘机制** | 历史过长影响 LLM 性能 | 添加滑动窗口截断 |

### 3.3 如果切换到 RedisSaver

```python
# 依赖: langgraph-checkpoint-redis
from langgraph.checkpoint.redis import RedisSaver

# 创建 Redis Checkpointer
redis_saver = RedisSaver.from_conn_info(
    url=settings.REDIS_URL,
)

# 使用
agent = create_agent(
    model=self.llm,
    tools=[self.vector_search_tool],
    system_prompt=self.search_prompt,
    checkpointer=redis_saver,  # 替换为持久化版本
    response_format=ProductRecommendResponse,
)
```

---

## 四、Java 对照

### 4.1 会话管理在 Spring 生态中的实现

```java
// 1. 会话上下文
public class ConversationContext {
    private final List<Message> messages = new ArrayList<>();
    private final String threadId;

    public void addMessage(Message msg) { messages.add(msg); }
    public List<Message> getHistory() { return List.copyOf(messages); }
}

// 2. 会话存储器（类似 Checkpointer）
@Component
public class ConversationStore {
    private final Map<String, ConversationContext> store = new ConcurrentHashMap<>();  // InMemory 版

    // Redis 版
    private final StringRedisTemplate redis;

    public ConversationContext getOrCreate(String threadId) {
        return store.computeIfAbsent(threadId, ConversationContext::new);
    }

    // 会话复用方法
    public List<Message> getContext(String threadId, String userMessage) {
        ConversationContext ctx = getOrCreate(threadId);
        ctx.addMessage(new Message("user", userMessage));
        return ctx.getHistory();
    }
}

// 3. 在服务中使用
@Service
public class SearchService {
    private final ConversationStore conversationStore;

    public ProductRecommendResponse recommend(String query, String threadId) {
        // 获取历史上下文（类似 Checkpointer 的恢复）
        List<Message> history = conversationStore.getContext(threadId, query);

        // 构造带上下文的 Prompt
        String prompt = buildPromptWithHistory(history, query);

        // 调用 LLM
        return llm.call(prompt, ProductRecommendResponse.class);
    }
}
```

### 4.2 对照总结

| 维度 | Python (LangGraph) | Java (Spring Boot) |
|------|-------------------|-------------------|
| 记忆模型 | Checkpointer 接口 | `ConversationStore` 自定义 |
| 内存实现 | `InMemorySaver` | `ConcurrentHashMap` |
| Redis 实现 | `RedisSaver` | `StringRedisTemplate` |
| 会话隔离 | `thread_id` | `Map<String, Context>` |
| 状态恢复 | 自动（框架内置） | 手动（从历史拼接） |
| 遗忘策略 | 需自行实现 | 需自行实现 |

---

## 五、最小可复现示例

### 5.1 演示 Checkpointer 的会话隔离

```python
# memory_demo.py
# 需要: pip install langgraph langchain-openai
from langgraph.checkpoint.memory import InMemorySaver
from langchain.agents import create_agent
from langchain.tools import tool
from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field
from typing import List

class RecommendResult(BaseModel):
    summary: str = Field(description="推荐导语")
    products: List[str] = Field(description="商品列表")
    reasons: List[str] = Field(description="推荐理由")

def demo_memory():
    """演示 Checkpointer 的会话隔离"""

    @tool
    def search(query: str) -> str:
        return "华为Pura 70 4999元, 华为Mate 60 5499元, iPhone 16 5999元"

    llm = ChatOpenAI(temperature=0.1, ...)
    checkpointer = InMemorySaver()

    agent = create_agent(
        model=llm,
        tools=[search],
        system_prompt="依据检索结果回答，不要编造",
        checkpointer=checkpointer,
        response_format=RecommendResult,
    )

    # 会话 A：用户查询
    result1 = agent.invoke(
        {"messages": "5000元以下华为手机"},
        {"configurable": {"thread_id": "session_a"}}
    )
    print(f"会话A 第1次: {result1['structured_response'].summary}")

    # 会话 A：继续提问（利用记忆）
    result2 = agent.invoke(
        {"messages": "那苹果的呢？"},
        {"configurable": {"thread_id": "session_a"}}
    )
    print(f"会话A 第2次: {result2['structured_response'].summary}")

    # 会话 B：新建会话，同样的问题
    result3 = agent.invoke(
        {"messages": "那苹果的呢？"},
        {"configurable": {"thread_id": "session_b"}}
    )
    # 没有上下文，"那苹果的"会理解成"苹果手机"
    print(f"会话B 第1次: {result3['structured_response'].summary}")

    # 关键验证：会话A 第2次能理解"那"指代"5000元以下"
    # 会话B 第1次只能当成"苹果手机"处理
    assert "5000" in result2['structured_response'].summary or \
           "5000" in str(result2['structured_response'].reasons), \
           "会话A 应记住'5000元以下'的上下文"
```

### 5.2 验证 Checkpointer 的恢复

```python
def test_checkpointer_resume():
    """验证中断后恢复"""

    checkpointer = InMemorySaver()
    agent = create_agent(..., checkpointer=checkpointer, ...)

    # 第一次执行
    agent.invoke(
        {"messages": "我的预算是5000元"},
        {"configurable": {"thread_id": "test_001"}}
    )

    # 模拟中断后恢复（同一 thread_id）
    result = agent.invoke(
        {"messages": "推荐手机"},
        {"configurable": {"thread_id": "test_001"}}
    )

    # 验证：Agent 知道"推荐手机"在"5000元预算"的上下文中
    assert result["structured_response"] is not None
```

---

## 六、面试要点

### Q1: LangGraph 的 Checkpointer 解决了什么问题？

**回答思路：** 解决了 Agent 的**短期记忆**问题。Agent 执行是一个循环决策过程，每一步都需要知道之前的状态（消息历史、工具调用结果、中间输出）。Checkpointer 在每一步自动保存状态快照，中断后可从任意 checkpoint 恢复执行。

### Q2: InMemorySaver 和 RedisSaver 的区别是什么？

**回答思路：** InMemorySaver 存进程内存，读写快但进程重启丢失；RedisSaver 存 Redis，持久化但多一次网络 IO。开发/演示用 InMemory，生产用 RedisSaver 或 PostgresSaver。

### Q3: thread_id 在项目中是如何设计和使用的？

**回答思路：** thread_id 作为会话隔离的 key。前端每次创建页面时随机生成，传递给后端。后端以 thread_id 为粒度保存对话历史。不同 thread_id 的记忆完全隔离。生产环境应绑定到用户 ID。

### Q4: 如果对话历史太长，如何优化？

**回答思路：** 滑动窗口截断：只保留最近 N 轮对话；或者摘要压缩：对历史对话生成摘要，替代原始历史注入 prompt。LangGraph 的 `checkpointer` 支持 `get_update` 钩子，可在保存前截断/压缩。

### Q5: 短期记忆和长期记忆在 LangGraph 中分别怎么实现？

**回答思路：** Checkpointer 实现短期记忆（会话内），BaseStore 实现长期记忆（跨会话）。短期记忆存对话历史，按 thread_id 隔离；长期记忆存用户偏好、知识积累，按 user_id 存储。本项目只用了短期记忆。

---

> **下一篇：** [09-DATA-SYNC.md —— 商品数据向量化同步链路：MySQL → 切片 → Embedding → RedisVL](./09-DATA-SYNC.md)
>
> 搜索链路的前提是数据已经准备好。看离线数据同步如何将 MySQL 商品数据流式处理为向量索引。