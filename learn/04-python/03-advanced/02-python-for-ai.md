# Python for AI

> 🎯 进阶 | 预计阅读：40 分钟

Python 之所以成为 AI 生态的通用语言，不是因为它性能最好，而是因为：**深度学习框架、数据处理库、AI 编排框架（LangChain）、云服务 SDK 全部原生支持 Python**。你只需要写胶水，把现成的零件拼接起来。

---

## 1. NumPy 基础

NumPy 是 Python 数值计算的基石，几乎所有 AI 库（Pandas、TensorFlow、PyTorch、Scikit-learn）都建立在它之上。

### 1.1 ndarray 核心概念

```python
import numpy as np

# 创建数组
a = np.array([1, 2, 3, 4])          # 一维
b = np.zeros((3, 3))                 # 3x3 全零矩阵
c = np.ones((2, 4))                  # 2x4 全一矩阵
d = np.arange(10)                    # [0..9]
e = np.linspace(0, 1, 5)             # [0, 0.25, 0.5, 0.75, 1] 均匀分布
f = np.random.rand(3, 3)             # 均匀分布随机数

# 数组属性
print(a.shape)   # (4,)
print(b.shape)   # (3, 3)
print(b.dtype)   # float64
print(b.ndim)    # 2（维度数）
```

### 1.2 向量化运算（关键优化）

**永远不要用 Python 循环处理数组**——NumPy 的向量化运算快 100 倍以上：

```python
# 慢：Python 循环
result = [x * 2 + 1 for x in data]

# 快：NumPy 向量化
result = data * 2 + 1

# 例：计算商品折扣价
prices = np.array([99.9, 199.0, 399.0, 999.0])
discounted = prices * 0.8
print(discounted)  # [ 79.92 159.2  319.2  799.2 ]
```

### 1.3 常用操作

```python
# 矩阵乘法（AI 生态的核心操作）
a = np.random.rand(3, 4)
b = np.random.rand(4, 5)
c = a @ b                    # 或者 np.dot(a, b)，形状 (3, 5)

# 聚合
data = np.array([[1, 2], [3, 4]])
print(data.sum())       # 10  全部求和
print(data.sum(axis=0)) # [4 6]  按列求和
print(data.mean(axis=1))# [1.5 3.5]  按行求均值

# 索引与切片
arr = np.arange(12).reshape(3, 4)
print(arr[1, 2])     # 第 1 行第 2 列 = 6
print(arr[:, 1])     # 第 1 列全部

# 广播（Broadcasting）
a = np.array([[1, 2, 3], [4, 5, 6]])
b = np.array([10, 20, 30])
print(a + b)  # 每一行都加 [10, 20, 30]
```

---

## 2. Pandas 数据处理

Pandas 是数据清洗与分析的主力，在 AI 商城中负责处理商品原始数据 → 高质量训练/向量化语料。

### 2.1 Series 与 DataFrame

```python
import pandas as pd

# Series：一维带标签数组
s = pd.Series([100, 200, 300], index=["iphone", "ipad", "mac"])

# DataFrame：二维表格（就像内存里的 Excel）
df = pd.DataFrame({
    "商品名": ["iPhone 17", "AirPods Pro 3", "MacBook Air"],
    "价格": [5999, 1899, 9499],
    "库存": [120, 350, 45],
    "是否上架": [True, True, False],
})
print(df)
```

### 2.2 数据清洗（AI 商城核心场景）

```python
df = pd.read_csv("products_raw.csv")
print(df.info())        # 查看类型与缺失值
print(df.isnull().sum())# 每列缺失值数量

# 1. 处理缺失值
df = df.dropna(subset=["商品名"])          # 删除关键字段缺失的行
df["价格"] = df["价格"].fillna(df["价格"].median())  # 用中位数填充

# 2. 类型转换
df["价格"] = df["价格"].astype(float)

# 3. 文本清洗（为 embedding 做准备）
df["清洗后标题"] = df["商品名"].str.strip().str.lower()

# 4. 去重
df = df.drop_duplicates(subset=["商品名"], keep="first")

# 5. 过滤异常值
df = df[df["价格"] > 0]
df = df[df["库存"] <= 10000]

# 6. 存储结果
df.to_csv("products_clean.csv", index=False, encoding="utf-8")
```

### 2.3 聚合与分析

```python
# groupby 分组聚合
stats = df.groupby("类目")["价格"].agg(["mean", "min", "max", "count"])

# 连接多个表（类似 SQL JOIN）
merged = df.merge(category_df, on="类目ID", how="left")

# 筛选（类似 SQL WHERE）
high_value = df[(df["价格"] > 3000) & (df["库存"] > 0)]

# 排序
df.sort_values("价格", ascending=False).head(10)  # TOP 10 商品
```

---

## 3. Python 作为 AI 生态的胶水语言

```
┌──────────────────────────────────────────────────────────────┐
│                    Python 胶水层                              │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌─────────────┐ │
│  │ NumPy    │  │ Pandas   │  │ 深度学习  │  │ 模型服务    │ │
│  │ 数值计算 │  │ 数据处理 │  │ PyTorch/  │  │ FastAPI/    │ │
│  │          │  │          │  │ TF        │  │ vLLM        │ │
│  └──────────┘  └──────────┘  └───────────┘  └─────────────┘ │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌─────────────┐ │
│  │ 向量库   │  │ 大模型   │  │ LangChain │  │ 消息队列    │ │
│  │ Qdrant/  │  │ OpenAI/  │  │ 编排框架  │  │ RabbitMQ/   │ │
│  │ Milvus   │  │ 本地模型 │  │           │  │ Kafka       │ │
│  └──────────┘  └──────────┘  └───────────┘  └─────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

**胶水语言的三大优势**：

1. **生态先发**：PyTorch/LangChain/HuggingFace 全部以 Python 为母语，新能力先在 Python 落地
2. **开发效率**：从"读数据 → 实验 → 上线服务"全链路只需一个语言
3. **人才库**：算法工程师与后端工程师能有效协作（同一种语言沟通）

**你的角色**：作为后端开发者，你主要工作在"胶水层"——处理数据（Pandas/NumPy）→ 调模型（LangChain/OpenAI SDK）→ 暴露服务（FastAPI）→ 接入商城（REST/MQ）。

---

## 4. LangChain 中 Python 的异步实践

[14-langchain](../../14-langchain/README.md) 会系统讲解，这里给出异步的关键模式——**调大模型 API 是最典型的 I/O 密集型场景**：

```python
import asyncio
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage

# 异步初始化模型（所有 LangChain 模型都有 async 版本）
llm = ChatOpenAI(
    model="gpt-4o-mini",
    temperature=0.7,
    max_retries=2,
    timeout=30,
)

async def generate_review(product_name: str) -> str:
    """异步生成商品推荐评语"""
    messages = [
        HumanMessage(content=f"为商品「{product_name}」写一段 50 字的种草文案")
    ]
    response = await llm.ainvoke(messages)  # await + ainvoke
    return response.content

async def main():
    products = ["iPhone 17", "AirPods Pro 3", "MacBook Air", "Apple Watch S11"]

    # 并发调用 4 个大模型请求
    tasks = [generate_review(p) for p in products]
    reviews = await asyncio.gather(*tasks)

    for product, review in zip(products, reviews):
        print(f"【{product}】{review}")

if __name__ == "__main__":
    asyncio.run(main())
```

### LangChain 异步 API 对照

| 同步方法 | 异步方法 | 说明 |
|---|---|---|
| `invoke()` | `ainvoke()` | 单次调用 |
| `stream()` | `astream()` | 流式输出 |
| `batch()` | `abatch()` | 批量调用 |

### 异步编排：Chain + LLM 并发

```python
from langchain_core.prompts import ChatPromptTemplate

prompt = ChatPromptTemplate.from_template(
    "你是资深导购。请为商品「{product}」推荐 3 个同类替代品，理由是：{reason}"
)

async def recommend(product: str, reason: str) -> str:
    chain = prompt | llm  # LangChain 链式调用
    response = await chain.ainvoke(
        {"product": product, "reason": reason}
    )
    return response.content

# 在 AI 购物助手中：
# 用户说 "我想买个降噪耳机，预算 2000"
# → 解析意图 → 查询商品库 → 异步并行生成 N 个商品推荐文案
```

---

## 总结

| 工具 | 角色 |
|---|---|
| NumPy | 数值计算与矩阵运算（底层性能） |
| Pandas | 数据清洗、聚合、分析（业务数据处理） |
| Python 胶水层 | 拼接 AI 生态各组件，开发效率优先 |
| asyncio | 并发调用大模型 API，显著降低用户等待时间 |

下一个技术栈：**05-fastapi**（用 Python 的异步能力构建 Web 服务）。

下一步项目实战：进入 [04-projects/mall-integration.md](../04-projects/mall-integration.md) 看这些能力如何落地到 AI 商城。