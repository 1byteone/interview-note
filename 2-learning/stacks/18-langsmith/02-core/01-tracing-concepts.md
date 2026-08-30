# LangSmith Tracing 核心概念（核心概念）

> 目标：真正理解 LangSmith 的"追踪模型"——Trace / Run / Thread 是什么、怎么看、怎么自定义。
> 这是理解"可观测性"的地基，也是面试高频考点。

---

## 一、三个核心概念（必背）

### Trace（追踪）— 单次执行的完整记录

一次用户请求 = 一条 Trace。它是**一棵执行树**：

- 根节点：请求入口（链 / Agent 的一次 invoke）
- 子节点：这次执行中发生的每一步

```
Trace: "推荐 3 款性价比高的手机"
├── Retriever (检索)        ← 树的一个分支
│   └── Embedding 调用
├── ChatModel (生成)
│   ├── 第一次工具调用
│   └── 工具返回后再生成
└── OutputParser (解析)
```

### Run（运行）— 树上的一个节点

Trace 中的**一次原子执行**：一次模型调用、一次工具执行、一个链/节点。每条 Run 记录：

- 输入 / 输出（完整数据）
- 消耗：token 数、延迟（ms）
- 元数据：模型名、温度、错误信息
- 父子关系：嵌套的 Run 形成树

### Thread（线程 / 会话）— 跨请求的关联维度

同一用户的**多轮对话**，多次 Trace 聚合到同一个 Thread：

- Thread 是按 `thread_id` 关联的会话档案
- 查看 Thread 可以还原"这个用户聊了什么 → 模型怎么一步步回答的"

**层级关系（面试一句话）**：`Thread（会话）→ Trace（单次执行）→ Run（执行步骤）`。

---

## 二、怎么"读"一条 Trace（瀑布视图排查法）

以 **RAG 问答回答错误**为例，看瀑布视图定位问题：

| 观察点 | 怎么判断 | 结论 |
|--------|---------|------|
| Retriever Run 的 hits | 召回片段是否相关？是否为空？ | 空召回 → 模型只能编造（幻觉根源） |
| Retriever 的耗时 | p95 是否异常 | 向量库慢 / chunk 过多 |
| ChatModel 的输入 | 系统提示词是否被覆盖？上下文是否完整 | prompt 注入 / 上下文截断 |
| ChatModel 的输出 | 是否合法 JSON / 格式 | 解析器失败点 |
| Tool Run | 工具返回了错误? | 外部依赖问题 |

> **黄金法则**：Locate first, fix second —— 先定位再动手，别猜。

---

## 三、自定义 Trace（两个常用武器）

### 1. `traceable` — 把任意函数变成 Run

```python
from langsmith import traceable

@traceable(
    name="rag_pipeline",           # Run 名称
    run_type="chain",              # chain / llm / tool / retriever
    project_name="mall-ai-rag",    # 覆盖默认项目
    metadata={"env": "prod"},      # 自定义元数据（可筛选）
)
def rag_pipeline(question: str) -> str:
    ...
```

### 2. 手动创建完整 Trace

```python
from langsmith import Client

client = Client()

with client.trace(
    name="manual_flow",
    inputs={"question": "..."},
) as rt:
    rt.runs.append(...)   # 手动挂子 run
    rt.end(outputs={"answer": "..."})
```

---

## 四、跨服务追踪（多进程 / 微服务）

LangSmith 支持把**分布式调用链**聚合到同一条 Trace：

- 上游服务在请求头传递 `langsmith-trace-id` / `parent-run-id`
- 下游服务的 Run 以子 Run 形式挂到同一 Trace
- Java / Go / Node 服务可用 langsmith SDK 或自建转发实现

> 适合"Python AI 服务 + Java 业务服务"混合架构——AI 链路的 trace 与业务上下文对应起来。

---

## 五、Tracing 与 Callbacks / 监控的关系（进阶认知）

| 层 | 机制 | 说明 |
|----|------|------|
| LangChain Callbacks | 事件机制 | `on_llm_start` 等，LangSmith 自动上报的底层通道 |
| LangSmith Tracing | 产品能力 | 把回调数据组织为 Trace / Run / Thread 的可视化记录 |
| Monitoring | 生产视图 | 在 trace 数据上做指标聚合、告警、在线评估 |

一句话：**回调是"机制"，LangSmith 是"产品"，监控是"基于产品的生产视图"**——三者层层向上。

---

## 六、小结

- **Trace / Run / Thread**：会话 / 执行 / 步骤 三级结构
- **瀑布视图**：RAG 排障第一现场（空召回、prompt 注入、解析失败一眼定位）
- **traceable**：非 LangChain 代码 1 分钟接入
- **分布式**：跨服务 trace_id 传递可聚合多进程调用

**下一步**：[02-core/02-evaluation.md](02-evaluation.md) —— Dataset 与 Experiment，理解"考卷 / 考生 / 判卷"的评测体系。