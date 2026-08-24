# 第十七章：LLM API 集成（P1 进阶）

> 📖 **参考资料**：[OpenAI API 文档](https://platform.openai.com/docs/) | [Ollama](https://ollama.com/) | [Pydantic](https://docs.pydantic.dev/) | [httpx](https://www.python-httpx.org/)

---

## 17.1 OpenAI 兼容 API

大多数 LLM 提供商（OpenAI / DeepSeek / 智谱 / Ollama）都实现了 **OpenAI 兼容协议**：同样的请求体、同样的 `/v1/chat/completions` 端点。只需切换 `base_url` 即可接入不同模型。

```python
# app/llm/client.py
import httpx
from typing import Optional

class LLMClient:
    """OpenAI 兼容的 LLM 客户端（同步版）"""

    def __init__(
        self,
        base_url: str = "https://api.openai.com/v1",
        api_key: Optional[str] = None,
        model: str = "gpt-4o-mini",
        timeout: float = 60.0,
    ):
        self.base_url = base_url.rstrip("/")
        self.model = model
        headers = {"Content-Type": "application/json"}
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"
        self._client = httpx.Client(base_url=self.base_url, headers=headers, timeout=timeout)

    def chat(self, messages: list[dict], temperature: float = 0.7) -> str:
        """调用 chat/completions，返回回答文本"""
        resp = self._client.post(
            "/chat/completions",
            json={
                "model": self.model,
                "messages": messages,
                "temperature": temperature,
            },
        )
        resp.raise_for_status()  # 4xx/5xx 抛异常
        data = resp.json()
        return data["choices"][0]["message"]["content"]

    def close(self):
        self._client.close()
```

```python
# 使用示例
client = LLMClient(api_key=os.environ["OPENAI_API_KEY"])
answer = client.chat([
    {"role": "system", "content": "你是资深 Python 后端工程师"},
    {"role": "user", "content": "解释一下什么是协程"},
])
print(answer)
```

---

## 17.2 结构化输出（Pydantic）

LLM 返回文本解析容易出错。通过 **response_format + Pydantic** 获得类型安全的 JSON 输出：

```python
# app/llm/structured.py
import json
from typing import Literal
from pydantic import BaseModel, Field, ValidationError
from app.llm.client import LLMClient

class ClassifiedIssue(BaseModel):
    """LLM 返回的结构化分类结果"""
    category: Literal["bug", "feature", "question"] = Field(description="问题分类")
    severity: Literal["low", "medium", "high", "critical"]
    title: str = Field(min_length=1, max_length=100)
    suggested_fix: str | None = None


def classify_issue(client: LLMClient, text: str) -> ClassifiedIssue:
    system_prompt = (
        "请分析用户的工单描述，将结果输出为 JSON。"
        "JSON 必须符合："
        '{"category": "bug|feature|question", "severity": "low|medium|high|critical", '
        '"title": "一句话标题", "suggested_fix": "修复建议或null"}'
    )
    raw = client.chat([
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": text},
    ], temperature=0.0)

    try:
        # 方法 A：response_format=json_object 时直接解析
        return ClassifiedIssue.model_validate_json(raw)
    except ValidationError:
        # 方法 B：容错——提取代码块中的 JSON
        start, end = raw.find("{"), raw.rfind("}")
        if start != -1 and end != -1:
            return ClassifiedIssue.model_validate_json(raw[start:end + 1])
        raise
```

> 若 API 支持 `response_format={"type": "json_object"}`，把它加入请求体；否则配合正则提取 + `model_validate_json` 兜底。

---

## 17.3 流式 SSE

长回答逐字返回（SSE，Server-Sent Events），显著降低首字延迟：

```python
# app/llm/stream.py
import httpx

def stream_chat(client: LLMClient, messages: list[dict]):
    """流式请求：yield 增量文本"""
    with httpx.Client(base_url=client.base_url, headers=client._client.headers) as http:
        with http.stream(
            "POST",
            "/chat/completions",
            json={
                "model": client.model,
                "messages": messages,
                "stream": True,          # 关键：开启流式
                "stream_options": {"include_usage": True},
            },
        ) as resp:
            resp.raise_for_status()
            # SSE 协议：以 "data:" 开头，空行分隔
            for line in resp.iter_lines():
                if not line or not line.startswith("data:"):
                    continue
                payload = line[len("data:"):].strip()
                if payload == "[DONE]":   # 流结束标记
                    break
                chunk = json.loads(payload)
                delta = chunk["choices"][0].get("delta", {})
                if delta.get("content"):
                    yield delta["content"]
```

```python
# FastAPI 中通过 StreamingResponse 转发
from fastapi.responses import StreamingResponse

@app.post("/chat")
async def chat(request: ChatRequest):
    def gen():
        for token in stream_chat(client, request.messages):
            yield f"data: {token}\n\n"

    return StreamingResponse(gen(), media_type="text/event-stream")
```

---

## 17.4 Tool Calling

让 LLM 调用你的业务函数（查数据库、调用 API），实现 Agent 能力：

```python
# app/llm/tools.py
import json, httpx

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "获取指定城市的当前天气",
            "parameters": {
                "type": "object",
                "properties": {
                    "city": {"type": "string", "description": "城市名，如 北京"}
                },
                "required": ["city"],
            },
        },
    }
]

def get_weather(city: str) -> str:
    """实际业务函数（示例：调用天气 API）"""
    return f"{city}：25°C，晴"

def run_with_tools(client: LLMClient, user_input: str) -> str:
    messages = [{"role": "user", "content": user_input}]
    resp = client._client.post(
        "/chat/completions",
        json={"model": client.model, "messages": messages, "tools": TOOLS},
    )
    result = resp.json()
    msg = result["choices"][0]["message"]

    # 模型请求调用工具
    if msg.get("tool_calls"):
        messages.append(msg)
        for call in msg["tool_calls"]:
            tool_result = get_weather(**json.loads(call["function"]["arguments"]))
            messages.append({
                "role": "tool",
                "tool_call_id": call["id"],
                "content": tool_result,
            })
        # 把工具结果回传给模型生成最终回答
        final = client._client.post(
            "/chat/completions",
            json={"model": client.model, "messages": messages, "tools": TOOLS},
        )
        return final.json()["choices"][0]["message"]["content"]

    return msg.get("content", "")
```

流程：

```text
用户提问 ──▶ LLM(带tools) ──▶ 请求调用 get_weather(北京)
    ▲                              │
    └────── 工具结果回传 ──── 执行工具获取 25°C
```

---

## 17.5 本地 LLM：Ollama

企业内网 / 隐私场景可用 Ollama 跑本地模型，**API 完全 OpenAI 兼容**，只需换 base_url：

### 安装与启动

```bash
# 1. 安装 Ollama：https://ollama.com/download
# 2. 拉取并启动模型
ollama pull qwen2.5:7b
ollama serve            # 默认监听 http://localhost:11434
```

### 集成代码

```python
# app/llm/ollama_client.py
from app.llm.client import LLMClient

# Ollama 的 OpenAI 兼容端点：http://localhost:11434/v1
ollama = LLMClient(
    base_url="http://localhost:11434/v1",
    api_key="ollama",            # Ollama 不校验密钥，占位即可
    model="qwen2.5:7b",
    timeout=120.0,               # 本地 CPU 推理较慢
)

def ask_local(question: str) -> str:
    """使用本地模型回答问题（零成本、离线可用）"""
    return ollama.chat([
        {"role": "system", "content": "你是一个有用的中文助手"},
        {"role": "user", "content": question},
    ])
```

### 对比：云端 vs 本地

| 维度 | OpenAI / DeepSeek API | Ollama 本地 |
|------|----------------------|-------------|
| 延迟 | 网络往返 + 排队 | 本地推理，受硬件影响 |
| 成本 | 按 token 计费 | 仅电费 |
| 数据安全 | 数据出域 | 完全内网 |
| 模型规模 | 可达千亿参数 | 受显存限制（7B~70B） |
| 可用性 | 依赖上游服务 | 无外部依赖 |

---

## 17.6 错误处理

LLM 调用是典型的「外部依赖」，必须做**超时、重试、降级**三件套：

```python
# app/llm/errors.py
import time, random
import httpx

class LLMUnavailableError(Exception):
    """LLM 服务不可用（超时 / 5xx / 配额耗尽）"""


def call_with_retry(fn, retries: int = 3, base_delay: float = 1.0):
    """指数退避重试"""
    for attempt in range(retries):
        try:
            return fn()
        except (httpx.TimeoutException, httpx.HTTPStatusError) as e:
            # 4xx 客户端错误不重试（如 401/400），直接透传
            if isinstance(e, httpx.HTTPStatusError) and 400 <= e.response.status_code < 500:
                raise
            if attempt == retries - 1:
                raise LLMUnavailableError(f"LLM 调用失败: {e}") from e
            delay = base_delay * (2 ** attempt) + random.uniform(0, 0.5)  # 指数退避 + 抖动
            time.sleep(delay)
```

```python
# FastAPI 中的降级策略
@app.get("/summarize")
def summarize(text: str):
    try:
        return call_with_retry(lambda: ollama.chat([
            {"role": "user", "content": f"请总结：{text}"},
        ]))
    except LLMUnavailableError:
        # 降级：返回兜底文案，而不是 500
        return {
            "summary": "【摘要服务暂不可用，请稍后重试】",
            "degraded": True,
        }
```

| 异常 | 处理策略 |
|------|----------|
| `httpx.TimeoutException` | 指数退避重试（≤3 次） |
| HTTP 5xx / 429 | 重试 + 抖动 |
| HTTP 401 / 400 | 立即失败，检查密钥与参数 |
| `ValidationError`（Pydantic） | 重新解析或返回降级结果 |

---

## 必读资源

| 资源 | 链接 |
|------|------|
| OpenAI API 文档 | https://platform.openai.com/docs/ |
| Ollama 官方文档 | https://ollama.com/ |
| httpx 流式请求 | https://www.python-httpx.org/advanced/clients/ |
| Pydantic 文档 | https://docs.pydantic.dev/ |
| 建议阅读 | *"Building LLM Apps"* — FastAPI + LangChain 生态教程 |