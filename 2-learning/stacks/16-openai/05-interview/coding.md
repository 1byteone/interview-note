# 面试代码题

## 1. 实现流式输出

### 题目

实现一个函数，使用 Chat Completions API 流式输出，支持在生成过程中逐字返回内容。

### 参考实现

```python
from openai import OpenAI
from typing import Generator


client = OpenAI(api_key="sk-xxx")


def stream_chat(
    messages: list,
    model: str = "gpt-4o-mini",
    temperature: float = 0.7,
) -> Generator[str, None, None]:
    """流式对话生成

    要求：
    1. 支持流式返回每个 token
    2. 能够处理中断（客户端断开）
    3. 记录完整的生成结果
    """
    full_response = []

    try:
        stream = client.chat.completions.create(
            model=model,
            messages=messages,
            temperature=temperature,
            stream=True,
        )

        for chunk in stream:
            delta = chunk.choices[0].delta
            if delta and delta.content:
                full_response.append(delta.content)
                yield delta.content

    except GeneratorExit:
        # 客户端中断，保存已生成的内容
        print(f"生成中断，已生成 {len(''.join(full_response))} 字")
        raise

    except Exception as e:
        print(f"流式生成出错: {e}")
        yield f"\n[生成失败: {e}]"


# 使用
for token in stream_chat([
    {"role": "user", "content": "帮我写一首关于春天的诗"},
]):
    print(token, end="", flush=True)
```

### 考察点

1. stream=True 的使用
2. delta.content 的解析
3. 异常处理与中断处理
4. 全量结果的累积

## 2. 实现 Function Calling

### 题目

实现一个"商品下单助手"：用户用自然语言下单，助手通过 Function Calling 调用下单函数。

### 参考实现

```python
import json
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")

# 工具定义
ORDER_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "create_order",
            "description": "创建商品订单",
            "parameters": {
                "type": "object",
                "properties": {
                    "product_id": {
                        "type": "string",
                        "description": "商品 ID",
                    },
                    "quantity": {
                        "type": "integer",
                        "description": "购买数量",
                        "minimum": 1,
                    },
                    "address": {
                        "type": "string",
                        "description": "收货地址",
                    },
                },
                "required": ["product_id", "quantity", "address"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "check_stock",
            "description": "检查商品库存",
            "parameters": {
                "type": "object",
                "properties": {
                    "product_id": {"type": "string"},
                },
                "required": ["product_id"],
                "additionalProperties": False,
            },
        },
    },
]


def create_order(product_id: str, quantity: int, address: str) -> dict:
    """模拟创建订单"""
    return {
        "order_id": f"ORD{product_id}{quantity}",
        "product_id": product_id,
        "quantity": quantity,
        "address": address,
        "status": "created",
        "total": 7999 * quantity,  # 模拟价格
    }


def check_stock(product_id: str) -> dict:
    """模拟检查库存"""
    stock = {"P001": 50, "P002": 200, "P003": 30}
    return {"product_id": product_id, "stock": stock.get(product_id, 0)}


def order_assistant(user_message: str) -> str:
    """下单助手主流程"""
    messages = [
        {
            "role": "system",
            "content": (
                "你是 AI 商城下单助手。下单流程：\n"
                "1. 先检查库存\n"
                "2. 确认商品、数量、地址\n"
                "3. 调用 create_order 创建订单\n"
                "4. 汇报订单结果"
            ),
        },
        {"role": "user", "content": user_message},
    ]

    for round_num in range(5):  # 最多 5 轮工具调用
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=messages,
            tools=ORDER_TOOLS,
            tool_choice="auto",
        )

        message = response.choices[0].message

        # 无工具调用，直接返回
        if not message.tool_calls:
            return message.content

        messages.append(message)

        # 处理工具调用
        for tool_call in message.tool_calls:
            name = tool_call.function.name
            args = json.loads(tool_call.function.arguments)

            if name == "check_stock":
                result = check_stock(args["product_id"])
            elif name == "create_order":
                result = create_order(**args)
            else:
                result = {"error": "未知工具"}

            messages.append({
                "role": "tool",
                "tool_call_id": tool_call.id,
                "content": json.dumps(result, ensure_ascii=False),
            })

    return "下单流程过于复杂，请简化操作"


# 使用
print(order_assistant("我要买 P001 商品 2 个，送到北京市海淀区中关村大街 1 号"))
```

### 考察点

1. tools 定义的 JSON Schema 结构
2. 多轮工具调用循环（check_stock → create_order）
3. tool_call_id 的正确传递
4. 循环上限保护

## 3. 微调数据准备

### 题目

将原始对话日志转换为 OpenAI 微调所需的 JSONL 格式，并处理数据清洗。

### 参考实现

```python
import json
import re
from typing import List, Dict


class FineTuningDataPreparer:
    """微调数据准备器"""

    def __init__(self, system_prompt: str):
        self.system_prompt = system_prompt

    def clean_text(self, text: str) -> str:
        """清洗文本"""
        # 1. 去除多余空白
        text = re.sub(r"\s+", " ", text).strip()

        # 2. 去除敏感信息（手机号、身份证）
        text = re.sub(r"1[3-9]\d{9}", "[手机号]", text)
        text = re.sub(r"\d{18}", "[身份证]", text)

        # 3. 去除 HTML 标签
        text = re.sub(r"<[^>]+>", "", text)

        return text

    def validate_sample(self, sample: Dict) -> bool:
        """校验单条样本"""
        messages = sample.get("messages", [])
        if len(messages) < 2:
            return False

        # 校验角色
        valid_roles = {"system", "user", "assistant"}
        for m in messages:
            if m.get("role") not in valid_roles:
                return False
            if not m.get("content", "").strip():
                return False

        # 校验长度（防止超长样本）
        total_len = sum(len(m["content"]) for m in messages)
        return 10 < total_len < 4000

    def convert_to_jsonl(
        self,
        conversations: List[List[Dict]],
        output_file: str = "fine_tune_data.jsonl",
    ) -> int:
        """转换对话为 JSONL

        输入格式:
            conversations = [
                [
                    {"role": "user", "content": "你好"},
                    {"role": "assistant", "content": "您好！"},
                ],
                ...
            ]
        """
        count = 0
        with open(output_file, "w", encoding="utf-8") as f:
            for conv in conversations:
                # 构建样本
                messages = [{"role": "system", "content": self.system_prompt}]
                for turn in conv:
                    messages.append({
                        "role": turn["role"],
                        "content": self.clean_text(turn["content"]),
                    })

                sample = {"messages": messages}

                # 校验通过才写入
                if self.validate_sample(sample):
                    f.write(
                        json.dumps(sample, ensure_ascii=False) + "\n"
                    )
                    count += 1

        print(f"写入 {count} 条有效样本")
        return count

    def split_dataset(
        self,
        jsonl_file: str,
        train_ratio: float = 0.9,
    ) -> tuple:
        """划分训练集和验证集"""
        with open(jsonl_file, "r", encoding="utf-8") as f:
            lines = f.readlines()

        split_idx = int(len(lines) * train_ratio)
        train_file, val_file = "train.jsonl", "val.jsonl"

        with open(train_file, "w", encoding="utf-8") as f:
            f.writelines(lines[:split_idx])
        with open(val_file, "w", encoding="utf-8") as f:
            f.writelines(lines[split_idx:])

        return train_file, val_file


# 使用
preparer = FineTuningDataPreparer(
    system_prompt="你是 AI 商城客服助手。"
)

conversations = [
    [
        {"role": "user", "content": "怎么退货？"},
        {"role": "assistant", "content": "您好，退货流程是..."},
    ],
    [
        {"role": "user", "content": "我的手机号是 13812345678，帮我查订单"},
        {"role": "assistant", "content": "好的，已为您脱敏处理..."},
    ],
]

n = preparer.convert_to_jsonl(conversations)
train_file, val_file = preparer.split_dataset("fine_tune_data.jsonl")
```

### 考察点

1. JSONL 格式与 messages 结构
2. 数据清洗（去敏感信息）
3. 数据质量校验
4. 训练/验证集划分

## 4. 使用 Assistants API

### 题目

使用 Assistants API 实现一个会议纪要助手：接收会议记录，生成结构化纪要。

### 参考实现

```python
import time
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")


class MeetingMinutesAssistant:
    """会议纪要助手"""

    INSTRUCTIONS = """
    你是会议纪要助手。根据会议记录生成结构化纪要：
    1. 会议主题和基本信息
    2. 参会人员
    3. 讨论要点（分类列出）
    4. 决议事项
    5. 待办事项（含负责人和截止时间）
    6. 风险与问题

    输出要求：
    - 使用 Markdown 格式
    - 逻辑清晰，要点分明
    - 不要添加会议记录中不存在的信息
    """

    def create_assistant(self) -> str:
        """创建助手"""
        assistant = client.beta.assistants.create(
            name="会议纪要助手",
            instructions=self.INSTRUCTIONS,
            model="gpt-4o-mini",
            tools=[{"type": "code_interpreter"}],
        )
        return assistant.id

    def generate_minutes(
        self,
        assistant_id: str,
        meeting_transcript: str,
    ) -> str:
        """生成会议纪要"""
        # 1. 创建线程
        thread = client.beta.threads.create(
            messages=[
                {
                    "role": "user",
                    "content": f"请根据以下会议记录生成会议纪要：\n\n{meeting_transcript}",
                }
            ]
        )

        # 2. 执行 Run
        run = client.beta.threads.runs.create(
            thread_id=thread.id,
            assistant_id=assistant_id,
        )

        # 3. 轮询等待完成
        while True:
            run_status = client.beta.threads.runs.retrieve(
                thread_id=thread.id,
                run_id=run.id,
            )
            if run_status.status in ("completed", "failed"):
                break
            time.sleep(1)

        if run_status.status == "failed":
            return f"生成失败: {run_status.last_error}"

        # 4. 获取结果
        messages = client.beta.threads.messages.list(
            thread_id=thread.id,
        )
        content = messages.data[0].content[0]
        if content.type == "text":
            return content.text.value

        # 可能包含文件输出（代码执行结果）
        return "已生成纪要并提供附件"


# 使用
assistant_id = MeetingMinutesAssistant().create_assistant()
minutes = MeetingMinutesAssistant().generate_minutes(
    assistant_id,
    "会议记录：讨论了 Q3 营销计划...（完整会议转录）",
)
print(minutes)
```

### 考察点

1. Assistant 的创建与配置
2. Thread / Run / Message 完整流程
3. 轮询等待 Run 完成
4. 结果提取与错误处理

## 5. 附加：Token 计算与成本估算

### 题目

编写一个函数，估算多轮对话的 token 消耗与成本。

### 参考实现

```python
import tiktoken


def estimate_conversation_cost(
    messages: list,
    model: str = "gpt-4o-mini",
    expected_output_tokens: int = 500,
) -> dict:
    """估算对话成本

    返回: 输入 token、预计输出 token、总成本
    """
    # 1. 获取编码器
    try:
        encoding = tiktoken.encoding_for_model(model)
    except KeyError:
        encoding = tiktoken.get_encoding("cl100k_base")

    # 2. 计算输入 token（含格式开销）
    input_tokens = 3  # 格式开销
    for m in messages:
        input_tokens += len(encoding.encode(m.get("content", ""))) + 3
        if m.get("name"):
            input_tokens += 1

    # 3. 定价查询
    pricing = {
        "gpt-4o":        (2.50, 10.00),
        "gpt-4o-mini":   (0.15, 0.60),
        "o1":           (15.00, 60.00),
    }
    in_price, out_price = pricing.get(model, (0.15, 0.60))

    # 4. 成本计算
    cost = (
        input_tokens * in_price
        + expected_output_tokens * out_price
    ) / 1_000_000

    return {
        "model": model,
        "input_tokens": input_tokens,
        "expected_output_tokens": expected_output_tokens,
        "estimated_cost": round(cost, 6),
        "per_1000_calls": round(cost * 1000, 2),
    }


# 使用
messages = [
    {"role": "system", "content": "你是 AI 商城客服，回答要简洁专业。"},
    {"role": "user", "content": "推荐一款 5000 元以内的轻薄本"},
]
result = estimate_conversation_cost(messages)
print(f"输入: {result['input_tokens']} tokens")
print(f"预计输出: {result['expected_output_tokens']} tokens")
print(f"单次成本: ${result['estimated_cost']}")
print(f"千次成本: ${result['per_1000_calls']}")
```

### 考察点

1. tiktoken 的正确使用
2. 对话格式开销的计算
3. 输入/输出分别计价的成本模型
4. 业务层面的成本估算思维