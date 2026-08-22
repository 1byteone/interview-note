# Assistants API

## 架构概述

Assistants API 是 OpenAI 提供的托管式 AI 助手框架，支持持久化对话、内置工具（Code Interpreter、File Search）和知识管理。

### 核心组件

```
       Assistant（助手定义）
            │
            ▼
    ┌───────────────┐
    │   Thread (线程) │  ← 对话上下文容器
    │   ┌─────────┐  │
    │   │ Message │  │  ← 消息（用户/助手）
    │   │ Message │  │
    │   │ Message │  │
    │   └─────────┘  │
    └───────┬───────┘
            │
            ▼
       Run（运行实例）
            │
            ├── 调用模型
            ├── 执行工具（Code Interpreter / File Search）
            └── 生成回复
```

### 数据模型

| 对象 | 说明 | 生命周期 |
|------|------|----------|
| Assistant | 助手配置（模型、指令、工具） | 长期存在 |
| Thread | 对话线程 | 每次对话创建 |
| Message | 消息（用户/助手） | 属于 Thread |
| Run | 一次模型调用 | 每次交互创建 |
| Run Step | Run 的步骤详情 | 属于 Run |

## 创建 Assistant

```python
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")

# 创建助手
assistant = client.beta.assistants.create(
    name="AI 商城数据分析师",
    instructions=(
        "你是一个 AI 商城的数据分析助手。你可以：\n"
        "1. 分析销售数据并生成报告\n"
        "2. 使用 Code Interpreter 处理数据文件\n"
        "3. 使用 File Search 查找知识库文档\n"
        "4. 用中文回答，提供数据驱动的洞察"
    ),
    model="gpt-4o",
    tools=[
        {"type": "code_interpreter"},
        {"type": "file_search"},
    ],
    # 可选：关联知识库文件
    # tool_resources={
    #     "file_search": {
    #         "vector_store_ids": ["vs_xxx"]
    #     }
    # },
)

print(f"Assistant ID: {assistant.id}")
```

## Thread / Run / Message 模型

### 创建对话线程

```python
# 创建新线程
thread = client.beta.threads.create()

# 或创建带初始消息的线程
thread = client.beta.threads.create(
    messages=[
        {
            "role": "user",
            "content": "请分析上个月的销售数据",
        }
    ]
)
```

### 发送消息并执行

```python
# 添加用户消息
message = client.beta.threads.messages.create(
    thread_id=thread.id,
    role="user",
    content="上个月销量最高的 5 个品类是什么？",
)

# 执行 Run
run = client.beta.threads.runs.create(
    thread_id=thread.id,
    assistant_id=assistant.id,
)
```

### 等待 Run 完成

```python
import time


def wait_for_run(thread_id: str, run_id: str, poll_interval: int = 1):
    """等待 Run 完成并返回结果"""
    while True:
        run = client.beta.threads.runs.retrieve(
            thread_id=thread_id,
            run_id=run_id,
        )

        if run.status == "completed":
            # 获取回复消息
            messages = client.beta.threads.messages.list(
                thread_id=thread_id,
            )
            return messages.data[0].content[0].text.value

        elif run.status == "failed":
            raise Exception(f"Run failed: {run.last_error}")

        elif run.status == "requires_action":
            # 处理 Function Calling 工具调用
            handle_tool_calls(thread_id, run)

        time.sleep(poll_interval)


# 使用
result = wait_for_run(thread.id, run.id)
print(result)
```

### 处理工具调用

```python
def handle_tool_calls(thread_id: str, run):
    """处理 Function Calling 工具调用"""
    tool_calls = run.required_action.submit_tool_outputs.tool_calls
    tool_outputs = []

    for tool_call in tool_calls:
        function_name = tool_call.function.name
        arguments = json.loads(tool_call.function.arguments)

        # 执行函数
        if function_name == "get_sales_data":
            result = get_sales_data(**arguments)
        elif function_name == "get_product_stats":
            result = get_product_stats(**arguments)
        else:
            result = {"error": "未知函数"}

        tool_outputs.append({
            "tool_call_id": tool_call.id,
            "output": json.dumps(result, ensure_ascii=False),
        })

    # 提交工具结果
    client.beta.threads.runs.submit_tool_outputs(
        thread_id=thread_id,
        run_id=run.id,
        tool_outputs=tool_outputs,
    )
```

## Code Interpreter

Code Interpreter 让模型能够编写和执行 Python 代码，适合数据分析、可视化等任务。

```python
# 创建带 Code Interpreter 的助手
assistant = client.beta.assistants.create(
    name="数据分析助手",
    instructions="使用 Python 分析数据并生成报告。",
    model="gpt-4o",
    tools=[{"type": "code_interpreter"}],
)

# 上传数据文件
file = client.files.create(
    file=open("sales_data.csv", "rb"),
    purpose="assistants",
)

# 创建线程并引用文件
thread = client.beta.threads.create(
    messages=[
        {
            "role": "user",
            "content": "分析附件中的销售数据，生成月度趋势图",
            "attachments": [
                {
                    "file_id": file.id,
                    "tools": [{"type": "code_interpreter"}],
                }
            ],
        }
    ],
)
```

## File Search

File Search 让助手能够搜索知识库文档，适合 FAQ、产品文档等场景。

```python
# 创建向量存储
vector_store = client.beta.vector_stores.create(
    name="AI 商城产品知识库",
)

# 上传文件到向量存储
file_streams = [
    open("product_catalog.pdf", "rb"),
    open("faq_common.pdf", "rb"),
    open("return_policy.pdf", "rb"),
]

file_batch = client.beta.vector_stores.file_batches.upload_and_poll(
    vector_store_id=vector_store.id,
    files=file_streams,
)

# 创建带 File Search 的助手
assistant = client.beta.assistants.create(
    name="AI 商城客服助手",
    instructions="回答用户问题时，优先从知识库中查找准确信息。",
    model="gpt-4o-mini",
    tools=[{"type": "file_search"}],
    tool_resources={
        "file_search": {
            "vector_store_ids": [vector_store.id],
        }
    },
)
```

## 实战：AI 商城数据分析助手

### 完整实现

```python
class MallDataAssistant:
    """AI 商城数据分析助手"""

    def __init__(self, api_key: str):
        self.client = OpenAI(api_key=api_key)
        self.assistant = None
        self.thread = None

    def create(self):
        """创建助手"""
        self.assistant = self.client.beta.assistants.create(
            name="AI 商城数据分析助手",
            instructions=(
                "你是 AI 商城的数据分析助手。\n\n"
                "能力：\n"
                "1. 使用 Code Interpreter 分析销售数据\n"
                "2. 生成数据可视化图表\n"
                "3. 提供数据驱动的业务洞察\n"
                "4. 用中文回答，输出结构化报告\n\n"
                "分析维度：\n"
                "- 销售额趋势（日/周/月）\n"
                "- 品类销售排行\n"
                "- 用户购买行为\n"
                "- 库存周转分析\n"
                "- 促销效果评估"
            ),
            model="gpt-4o",
            tools=[
                {"type": "code_interpreter"},
                {"type": "file_search"},
            ],
        )
        return self

    def start_conversation(self):
        """开始新对话"""
        self.thread = self.client.beta.threads.create()
        return self

    def ask(self, question: str, files: list = None) -> str:
        """提问并获取回答"""
        # 添加消息
        message_data = {
            "role": "user",
            "content": question,
        }
        if files:
            message_data["attachments"] = [
                {"file_id": f, "tools": [{"type": "code_interpreter"}]}
                for f in files
            ]

        self.client.beta.threads.messages.create(
            thread_id=self.thread.id,
            **message_data,
        )

        # 执行 Run
        run = self.client.beta.threads.runs.create(
            thread_id=self.thread.id,
            assistant_id=self.assistant.id,
        )

        # 等待结果
        return self._wait_for_completion(run.id)

    def _wait_for_completion(self, run_id: str) -> str:
        """等待 Run 完成"""
        while True:
            run = self.client.beta.threads.runs.retrieve(
                thread_id=self.thread.id,
                run_id=run_id,
            )

            if run.status == "completed":
                messages = self.client.beta.threads.messages.list(
                    thread_id=self.thread.id,
                )
                return messages.data[0].content[0].text.value

            elif run.status in ("failed", "cancelled", "expired"):
                return f"请求失败: {run.status}"

            time.sleep(1)


# 使用
assistant = MallDataAssistant("sk-xxx")
assistant.create().start_conversation()

result = assistant.ask(
    "分析上个月的销售数据，找出增长最快的品类",
    files=["file_sales_data_xxx"],
)
print(result)
```

## 注意事项

### 1. 成本控制
- Assistants API 按 token 计费，Code Interpreter 额外计费
- 每个 Run 会消耗 token，建议设置合理的指令长度
- File Search 按向量存储大小计费

### 2. 性能优化
- 指令（instructions）不宜过长，控制在 2000 token 以内
- 及时清理不再使用的 Thread
- 使用 polling 方式获取结果，避免阻塞

### 3. 限制
- 每个 Assistant 最多关联 20 个工具
- 单次上传文件最大 512MB
- Thread 消息历史有长度限制，需要定期清理

### 4. 适用场景
- 需要持久化对话上下文的场景
- 需要代码执行能力的分析任务
- 需要知识库检索的客服系统
- 多步骤复杂工作流