# Chains（链）

> 👶→🎯 入门到进阶 | 预计阅读：30 分钟

Chain 是 LangChain 的核心抽象，它将多个处理步骤串联成一个可执行的管道。你可以把 Chain 想象成**工作流中的流水线**——每个环节处理特定的任务，然后将结果传递给下一个环节。

---

## 1. LLMChain —— 最基础的链

`LLMChain` 是 LangChain 中最简单的链类型，它将 Prompt Template 和 LLM 组合在一起，完成"提示词模板 + 模型调用"的闭环。

```python
from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate
from langchain.chains import LLMChain

# 初始化 LLM
llm = ChatOpenAI(model="gpt-4o-mini", temperature=0.7)

# 定义提示词模板
prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一个专业的{role}，请用简洁的语言回答。"),
    ("human", "{question}")
])

# 创建链
chain = LLMChain(llm=llm, prompt=prompt)

# 执行链
result = chain.invoke({
    "role": "AI 商城客服",
    "question": "如何查询订单状态？"
})
print(result["text"])
```

**关键参数**：
- `llm`：大语言模型实例
- `prompt`：提示词模板，支持变量插值
- `verbose=True`：开启调试日志，查看每一步的输入输出

---

## 2. SequentialChain —— 多步骤流水线

当业务逻辑包含多个先后步骤时，`SequentialChain` 可以将多个链串联起来，上一个链的输出自动成为下一个链的输入。

```python
from langchain.chains import SequentialChain, LLMChain

# 第一步：提取商品关键词
extract_prompt = ChatPromptTemplate.from_messages([
    ("human", "从用户的问题中提取商品关键词：{question}")
])
extract_chain = LLMChain(
    llm=llm, prompt=extract_prompt,
    output_key="keywords"  # 指定输出变量名
)

# 第二步：根据关键词生成推荐理由
recommend_prompt = ChatPromptTemplate.from_messages([
    ("human", "用户搜索了{keywords}，请生成3条推荐理由。")
])
recommend_chain = LLMChain(
    llm=llm, prompt=recommend_prompt,
    output_key="recommendation"
)

# 第三步：润色回复
polish_prompt = ChatPromptTemplate.from_messages([
    ("human", "将以下推荐理由润色成一段友好的客服回复：\n{recommendation}")
])
polish_chain = LLMChain(
    llm=llm, prompt=polish_prompt,
    output_key="final_reply"
)

# 组装顺序链
pipeline = SequentialChain(
    chains=[extract_chain, recommend_chain, polish_chain],
    input_variables=["question"],
    output_variables=["final_reply"],
    verbose=True
)

result = pipeline.invoke({"question": "想要一款适合跑步的无线耳机"})
print(result["final_reply"])
```

**注意**：每个 LLMChain 的 `output_key` 必须有唯一名称，后续链的提示词模板中引用该变量名即可接收上一步的输出。

---

## 3. RouterChain —— 路由分发

当需要根据输入内容分发到不同处理分支时，`RouterChain` 可以根据输入自动选择最合适的子链。这在 AI 商城的客服系统中非常实用——不同的问题类型走不同的处理逻辑。

```python
from langchain.chains.router import LLMRouterChain
from langchain.chains.router.llm_router import RouterOutputParser
from langchain.chains.router.multi_prompt_prompt import MULTI_PROMPT_ROUTER_TEMPLATE

# 定义多个子链
order_chain = LLMChain(
    llm=llm,
    prompt=ChatPromptTemplate.from_template(
        "你是一个订单客服。用户的问题是：{input}"
    )
)

product_chain = LLMChain(
    llm=llm,
    prompt=ChatPromptTemplate.from_template(
        "你是一个商品导购。用户的问题是：{input}"
    )
)

after_sale_chain = LLMChain(
    llm=llm,
    prompt=ChatPromptTemplate.from_template(
        "你是一个售后客服。用户的问题是：{input}"
    )
)

# 定义路由映射
destinations = [
    ("订单查询", "适合处理订单状态、物流信息等查询"),
    ("商品咨询", "适合处理商品详情、规格参数、推荐等"),
    ("售后服务", "适合处理退换货、退款、投诉等"),
]

# 构建路由链（简化写法，实际需使用 RouterChain）
from langchain.chains.router import MultiPromptChain

router_chain = MultiPromptChain.from_prompts(
    llm=llm,
    prompt_infos=[
        {"name": "订单", "description": "订单和物流相关的问题", "prompt_template": "你是一个订单客服。{input}"},
        {"name": "商品", "description": "商品详情和推荐相关的问题", "prompt_template": "你是一个商品导购。{input}"},
        {"name": "售后", "description": "退换货和退款相关的问题", "prompt_template": "你是一个售后客服。{input}"},
    ],
    default_chain=LLMChain(
        llm=llm,
        prompt=ChatPromptTemplate.from_template("你是一个通用客服。{input}")
    ),
    verbose=True
)

# 测试路由
for question in [
    "我的订单号 20240801 到哪了？",
    "这款手机支持快充吗？",
    "我要退货，怎么操作？",
    "今天天气怎么样？",
]:
    print(f"Q: {question}")
    print(f"A: {router_chain.run(question)}\n")
```

---

## 4. 自定义 Chain

当内置 Chain 无法满足需求时，可以继承 `Chain` 基类实现自定义逻辑。

```python
from langchain.chains.base import Chain
from typing import Dict, Any, Optional, List

class ProductPriceCompareChain(Chain):
    """自定义链：比较多个商品的价格并给出推荐"""
    
    llm: ChatOpenAI
    max_products: int = 5
    
    @property
    def input_keys(self) -> List[str]:
        return ["product_name", "price_list"]
    
    @property
    def output_keys(self) -> List[str]:
        return ["analysis", "recommendation"]
    
    def _call(self, inputs: Dict[str, Any]) -> Dict[str, Any]:
        product_name = inputs["product_name"]
        price_list = inputs["price_list"]  # 格式: [{"shop": "xx", "price": 99.9}]
        
        # 1. 价格排序
        sorted_prices = sorted(price_list, key=lambda x: x["price"])
        
        # 2. 调用 LLM 进行分析
        prompt = f"商品【{product_name}】的各店铺价格如下：\n"
        for item in sorted_prices[:self.max_products]:
            prompt += f"- {item['shop']}: ¥{item['price']}\n"
        prompt += "\n请给出购买建议。"
        
        result = self.llm.invoke(prompt)
        
        return {
            "analysis": f"最低价 ¥{sorted_prices[0]['price']}，最高价 ¥{sorted_prices[-1]['price']}，共 {len(price_list)} 家店铺在售",
            "recommendation": result.content
        }

# 使用自定义链
compare_chain = ProductPriceCompareChain(llm=llm)
result = compare_chain.invoke({
    "product_name": "iPhone 15 Pro",
    "price_list": [
        {"shop": "Apple 官方", "price": 8999},
        {"shop": "京东自营", "price": 8499},
        {"shop": "拼多多百亿补贴", "price": 7899},
        {"shop": "天猫旗舰店", "price": 8799},
    ]
})
print(result["analysis"])
print(result["recommendation"])
```

---

## 5. 实战：AI 商城商品问答 Chain

综合运用上面的知识，构建一个完整的 AI 商城问答系统。

```python
from langchain.chains import ConversationChain
from langchain.memory import ConversationBufferMemory

# 构建商品问答链
def build_mall_qa_chain():
    """构建 AI 商城商品问答链"""
    
    system_prompt = """你是 AI 商城的智能客服，具备以下能力：
1. 商品咨询：介绍商品规格、功能、适用场景
2. 订单查询：查询订单状态和物流信息
3. 推荐导购：根据用户需求推荐商品
4. 售后服务：处理退换货和退款问题

请根据用户问题提供准确、友好的回答。如果不确定，请如实告知用户。"""
    
    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        ("human", "{input}")
    ])
    
    chain = LLMChain(
        llm=ChatOpenAI(model="gpt-4o-mini", temperature=0.3),
        prompt=prompt,
        verbose=True
    )
    
    return chain

# 使用示例
mall_chain = build_mall_qa_chain()
questions = [
    "你们有 5000 元左右的轻薄本推荐吗？",
    "下单后多久发货？",
    "这款电脑支持 Type-C 充电吗？",
]

for q in questions:
    response = mall_chain.invoke({"input": q})
    print(f"用户: {q}")
    print(f"客服: {response['text']}\n")
```

---

## 总结

| Chain 类型 | 适用场景 | 核心特点 |
|-----------|---------|---------|
| LLMChain | 单步问答 | 提示词模板 + LLM 调用 |
| SequentialChain | 多步骤流水线 | 链式传递输出变量 |
| RouterChain | 按内容分发 | 自动路由到不同子链 |
| 自定义 Chain | 复杂业务逻辑 | 继承 Chain 基类，自由扩展 |

**下一步**：学习 [Memory（记忆）](./02-memory.md)，为 Chain 添加对话记忆能力，让 AI 客服记住上下文。