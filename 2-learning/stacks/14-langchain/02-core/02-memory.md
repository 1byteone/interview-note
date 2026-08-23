# Memory（记忆）

> 👶→🎯 入门到进阶 | 预计阅读：30 分钟

对话记忆是构建智能客服系统的关键能力。没有记忆的 AI 像"失忆症患者"——每次对话都从零开始。LangChain 的 Memory 模块为 Chain 提供了多种记忆管理方案。

---

## 1. ConversationBufferMemory —— 缓冲区记忆

最简单的记忆方式：将对话历史全部保存在缓冲区中，每次请求时拼接完整的上下文。

```python
from langchain.memory import ConversationBufferMemory
from langchain.chains import ConversationChain
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4o-mini", temperature=0.7)

# 创建带记忆的对话链
memory = ConversationBufferMemory()
conversation = ConversationChain(
    llm=llm,
    memory=memory,
    verbose=True
)

# 多轮对话
print(conversation.predict(input="你好，我想买一台笔记本电脑"))
print(conversation.predict(input="预算在 6000 元左右"))
print(conversation.predict(input="主要用来写代码，偶尔玩游戏"))
```

**内存结构**：ConversationBufferMemory 内部维护一个消息列表，格式如下：

```python
# 查看记忆内容
print(memory.buffer)  # 所有历史消息的文本
print(memory.load_memory_variables({}))
# 输出: {'history': 'Human: 你好，我想买一台笔记本电脑\nAI: ...'}
```

**限制**：随着对话进行，Token 消耗会线性增长，对于长对话会导致超出上下文窗口。

---

## 2. ConversationBufferWindowMemory —— 窗口记忆

只保留最近 k 轮对话，超出窗口的旧消息自动丢弃，有效控制 Token 消耗。

```python
from langchain.memory import ConversationBufferWindowMemory

# 只保留最近 3 轮对话
window_memory = ConversationBufferWindowMemory(k=3, return_messages=True)

conversation = ConversationChain(
    llm=llm,
    memory=window_memory,
    verbose=True
)

conversation.predict(input="第一轮：你好")
conversation.predict(input="第二轮：推荐手机")
conversation.predict(input="第三轮：预算 3000")
conversation.predict(input="第四轮：要拍照好的")
# 此时第一轮对话已被丢弃
```

---

## 3. ConversationSummaryMemory —— 摘要记忆

窗口记忆的缺点是"粗暴丢弃"——可能丢失重要信息。摘要记忆的解决思路是：**用 LLM 定期总结历史对话，用摘要替代完整历史**。

```python
from langchain.memory import ConversationSummaryMemory

summary_memory = ConversationSummaryMemory(
    llm=llm,
    max_token_limit=200  # 摘要不超过 200 token
)

conversation = ConversationChain(
    llm=llm,
    memory=summary_memory,
    verbose=True
)

# 多轮对话后，memory 会自动生成摘要
conversation.predict(input="你好，我想给女朋友买个生日礼物")
conversation.predict(input="她喜欢运动，预算 1500 左右")
conversation.predict(input="有什么智能手表推荐吗？")

# 查看记忆内容（此时是 LLM 生成的摘要，而非原始对话）
print(summary_memory.load_memory_variables({}))
```

**摘要记忆的优缺点**：
- 优点：Token 消耗可控，不会丢失关键信息
- 缺点：需要额外调用 LLM 生成摘要，有延迟和成本

---

## 4. VectorStoreMemory —— 向量存储记忆

对于超长对话或需要检索特定记忆的场景，可以将历史消息向量化后存入向量数据库，按语义相似度检索相关记忆。

```python
from langchain.memory import VectorStoreRetrieverMemory
from langchain_openai import OpenAIEmbeddings
from langchain_community.vectorstores import FAISS
import numpy as np

# 准备示例对话
dialogues = [
    "用户说想买一台轻薄本，预算 5000-6000",
    "用户提到平时用 VS Code 写 Python",
    "用户说喜欢银色外观",
    "用户询问了保修政策",
    "用户说之前用过联想笔记本",
]

# 创建向量存储
embeddings = OpenAIEmbeddings()
vectorstore = FAISS.from_texts(dialogues, embeddings)
retriever = vectorstore.as_retriever(search_kwargs={"k": 2})

# 创建向量记忆
vector_memory = VectorStoreRetrieverMemory(
    retriever=retriever,
    memory_key="relevant_history",
    input_key="input"
)

# 查询相关记忆
query = "推荐什么笔记本？"
relevant = vector_memory.load_memory_variables({"input": query})
print("相关记忆:", relevant)
# 输出与"轻薄本""5000-6000"相关的历史记录
```

**适用场景**：
- 客服系统需要从海量历史对话中快速定位关键信息
- 需要跨会话记忆（用户昨天说过什么，今天还记得）
- 记忆库超过数万条，无法用简单缓冲区管理

---

## 5. 组合记忆：多种 Memory 一起用

实际项目中往往需要组合多种记忆策略。LangChain 提供了 `CombinedMemory` 来整合多个 Memory 实例。

```python
from langchain.memory import (
    CombinedMemory,
    ConversationBufferWindowMemory,
    ConversationSummaryMemory,
)

# 1. 窗口记忆：保留最近 3 轮完整对话
window_mem = ConversationBufferWindowMemory(
    k=3,
    memory_key="recent_history",
    return_messages=True
)

# 2. 摘要记忆：保存整个对话的摘要
summary_mem = ConversationSummaryMemory(
    llm=llm,
    memory_key="summary_history",
    return_messages=True
)

# 组合记忆
combined_memory = CombinedMemory(
    memories=[window_mem, summary_mem]
)

prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一个 AI 商城客服。\n\n近期对话：{recent_history}\n\n对话摘要：{summary_history}"),
    ("human", "{input}")
])

chain = LLMChain(llm=llm, prompt=prompt, memory=combined_memory)
```

---

## 6. 实战：AI 客服对话记忆

在 AI 商城的客服场景中，用户可能会：
- 先问商品，再问订单，再问售后——需要跨话题记忆
- 隔天回来继续之前的对话——需要持久化记忆
- 多次询问同一商品——需要避免重复推荐

```python
import json
from pathlib import Path

class MallCustomerMemory:
    """AI 商城客服记忆管理器"""
    
    def __init__(self, llm, user_id: str, memory_dir: str = "./memories"):
        self.user_id = user_id
        self.memory_dir = Path(memory_dir)
        self.memory_dir.mkdir(exist_ok=True)
        
        # 短期记忆：最近 5 轮完整对话
        self.short_term = ConversationBufferWindowMemory(
            k=5,
            memory_key="chat_history",
            return_messages=True
        )
        
        # 长期记忆：用户画像摘要
        self.long_term = ConversationSummaryMemory(
            llm=llm,
            memory_key="user_profile",
            max_token_limit=300
        )
        
        # 恢复持久化记忆
        self._load()
    
    def _load(self):
        """从磁盘加载持久化记忆"""
        path = self.memory_dir / f"{self.user_id}.json"
        if path.exists():
            data = json.loads(path.read_text())
            # 恢复用户画像
            if data.get("profile"):
                self.long_term.buffer = data["profile"]
    
    def save(self):
        """持久化保存记忆"""
        path = self.memory_dir / f"{self.user_id}.json"
        data = {
            "user_id": self.user_id,
            "profile": self.long_term.buffer,
            "updated_at": "2025-11-01T12:00:00Z"
        }
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2))
    
    def get_memory(self):
        """获取组合记忆变量"""
        short = self.short_term.load_memory_variables({})
        long = self.long_term.load_memory_variables({})
        return {**short, **long}

# 使用示例
memory_mgr = MallCustomerMemory(llm, user_id="user_10086")
chain = LLMChain(
    llm=llm,
    prompt=ChatPromptTemplate.from_messages([
        ("system", "你是 AI 商城客服。\n用户画像：{user_profile}\n对话历史：{chat_history}"),
        ("human", "{input}")
    ]),
    memory=memory_mgr.get_memory()  # 简写，实际需传入 CombinedMemory
)

# 多轮对话
chain.run("你好，我想买一台轻薄本")
chain.run("预算 5000-6000")
chain.run("主要用来写代码")

# 保存记忆，下次对话时恢复
memory_mgr.save()
```

---

## 总结

| Memory 类型 | 保留策略 | 适用场景 | 注意点 |
|-----------|---------|---------|-------|
| BufferMemory | 全部保留 | 短对话 | Token 线性增长 |
| WindowMemory | 最近 k 轮 | 通用场景 | 旧消息丢失 |
| SummaryMemory | LLM 摘要 | 长对话 | 额外 LLM 开销 |
| VectorStoreMemory | 语义检索 | 超大对话 | 需要向量数据库 |
| CombinedMemory | 组合策略 | 生产环境 | 配置复杂度高 |

**下一步**：学习 [Tools（工具）](./03-tools.md)，为 AI 客服添加工具调用能力，让它能真正查询订单、搜索商品。