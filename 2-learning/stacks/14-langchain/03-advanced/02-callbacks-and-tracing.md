# Callbacks 事件机制与 LangSmith 追踪调试

> 面向 Python 后端开发者的 LangChain 可观测性教程，覆盖 Callbacks 事件机制、LangSmith 追踪调试、日志与监控、自定义回调处理器。

---

## 1. Callbacks 事件机制

LangChain 的 Callbacks 机制允许你在链式调用、Agent 执行、工具调用等关键生命周期节点插入自定义逻辑，用于日志、监控、追踪、审计等场景。

### 1.1 事件生命周期

LangChain 的每个组件（LLM、Chain、Tool、Agent）都遵循统一的生命周期：

```
on_start → on_llm_start / on_chain_start / on_tool_start
    ↓
on_llm_end / on_chain_end / on_tool_end
    ↓
on_error（如果发生异常）
```

### 1.2 内置回调事件

```python
from langchain.callbacks.base import BaseCallbackHandler

class MyCallbackHandler(BaseCallbackHandler):
    """自定义回调处理器"""

    def on_llm_start(self, serialized, prompts, **kwargs):
        """LLM 开始生成时触发"""
        print(f"[LLM Start] 提示词数：{len(prompts)}")

    def on_llm_end(self, response, **kwargs):
        """LLM 生成完成时触发"""
        print(f"[LLM End] 生成 Token 数：{response.llm_output.get('token_usage', {})}")

    def on_chain_start(self, serialized, inputs, **kwargs):
        """Chain 开始执行时触发"""
        print(f"[Chain Start] 输入：{str(inputs)[:100]}...")

    def on_chain_end(self, outputs, **kwargs):
        """Chain 执行完成时触发"""
        print(f"[Chain End] 输出：{str(outputs)[:100]}...")

    def on_tool_start(self, serialized, input_str, **kwargs):
        """工具开始调用时触发"""
        print(f"[Tool Start] 工具：{serialized.get('name')}，输入：{input_str}")

    def on_tool_end(self, output, **kwargs):
        """工具调用完成时触发"""
        print(f"[Tool End] 输出：{str(output)[:100]}...")

    def on_llm_error(self, error, **kwargs):
        """LLM 发生错误时触发"""
        print(f"[LLM Error] {error}")

    def on_tool_error(self, error, **kwargs):
        """工具调用发生错误时触发"""
        print(f"[Tool Error] {error}")
```

### 1.3 使用方式

**方式一：全局传入**

```python
from langchain_openai import ChatOpenAI
from langchain.agents import create_react_agent, AgentExecutor
from langchain import hub

handler = MyCallbackHandler()
llm = ChatOpenAI(model="gpt-4", temperature=0, callbacks=[handler])

tools = [search_product, check_stock, query_order]
prompt = hub.pull("hwchase17/react")
agent = create_react_agent(llm, tools, prompt)
agent_executor = AgentExecutor(agent=agent, tools=tools, callbacks=[handler])

result = agent_executor.invoke(
    {"input": "查一下 iPhone 15 的价格"},
    callbacks=[handler]  # 也可以在调用时传入
)
```

**方式二：上下文管理器**

```python
from langchain.callbacks import CallbackManager

manager = CallbackManager([handler])

with manager:
    # 在此上下文中的所有 LangChain 调用都会被记录
    result = agent_executor.invoke({"input": "查一下 MacBook 的价格"})
```

**方式三：使用 `with` 标签分类**

```python
from langchain.callbacks.manager import collect_runs

# 使用 collect_runs 收集一次完整请求的追踪信息
with collect_runs() as cb:
    result = agent_executor.invoke({"input": "帮我查订单"})
    run_id = cb.traced_runs[0].id
    print(f"Run ID: {run_id}")
```

---

## 2. LangSmith 追踪调试

LangSmith 是 LangChain 官方提供的可观测性平台，用于追踪、调试、评估 LLM 应用。

### 2.1 配置 LangSmith

```python
import os

# 设置环境变量
os.environ["LANGCHAIN_TRACING_V2"] = "true"
os.environ["LANGCHAIN_API_KEY"] = "ls_你的_API_KEY"
os.environ["LANGCHAIN_PROJECT"] = "ai-mall-customer-service"

# 设置后，所有 LangChain 调用自动上报
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4", temperature=0)

# 无需额外配置，调用自动追踪
response = llm.invoke("你好，请介绍一下 iPhone 15")
```

### 2.2 手动添加元数据

```python
from langchain.callbacks.tracers import LangChainTracer
from langchain.callbacks.manager import CallbackManager

tracer = LangChainTracer(
    project_name="ai-mall-customer-service",
)
callback_manager = CallbackManager([tracer])

# 为每次调用添加元数据
result = agent_executor.invoke(
    {"input": "我的订单什么时候到"},
    config={
        "callbacks": [tracer],
        "metadata": {
            "user_id": "U12345",
            "session_id": "S20240801",
            "platform": "mobile",
            "version": "v2.1.0",
        },
        "tags": ["customer-service", "order-query"],
    }
)
```

### 2.3 LangSmith 核心功能

| 功能 | 说明 | 用途 |
|------|------|------|
| **Trace 视图** | 可视化展示每次调用的完整调用链 | 调试 Agent 行为 |
| **延迟分析** | 每个步骤的耗时分布 | 性能优化 |
| **Token 统计** | 输入/输出 Token 数、成本估算 | 成本控制 |
| **错误回溯** | 异常调用链的完整上下文 | 问题排查 |
| **数据集** | 将 Trace 保存为数据集 | 回归测试 |
| **在线评估** | 自动评估生成质量 | 质量监控 |

---

## 3. 日志与监控

在生产环境中，需要将 Callbacks 与标准日志系统集成。

### 3.1 日志记录器回调

```python
import logging
from langchain.callbacks.base import BaseCallbackHandler
from typing import Any, Dict, List

logger = logging.getLogger(__name__)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler("langchain.log"),
        logging.StreamHandler(),
    ],
)

class LoggingCallbackHandler(BaseCallbackHandler):
    """将 LangChain 事件输出到标准日志系统"""

    def on_llm_start(self, serialized, prompts, **kwargs):
        logger.info(f"LLM 调用开始 | 模型：{serialized.get('name')} | 提示词数：{len(prompts)}")

    def on_llm_end(self, response, **kwargs):
        usage = response.llm_output.get("token_usage", {}) if response.llm_output else {}
        logger.info(f"LLM 调用完成 | 输入 Token：{usage.get('prompt_tokens', 'N/A')} | "
                    f"输出 Token：{usage.get('completion_tokens', 'N/A')}")

    def on_tool_start(self, serialized, input_str, **kwargs):
        logger.info(f"工具调用 | 工具：{serialized.get('name')} | 输入：{input_str}")

    def on_tool_end(self, output, **kwargs):
        logger.info(f"工具完成 | 输出长度：{len(str(output))}")

    def on_tool_error(self, error, **kwargs):
        logger.error(f"工具错误 | {error}", exc_info=True)

    def on_chain_start(self, serialized, inputs, **kwargs):
        logger.debug(f"Chain 开始 | 类型：{serialized.get('name')}")

    def on_chain_end(self, outputs, **kwargs):
        logger.debug(f"Chain 完成 | 输出：{str(outputs)[:200]}")
```

### 3.2 性能监控回调

```python
import time
from collections import defaultdict

class PerformanceMonitorCallback(BaseCallbackHandler):
    """监控各组件耗时"""

    def __init__(self):
        self.timings = defaultdict(list)
        self._start_times = {}

    def _record_start(self, event_id: str):
        self._start_times[event_id] = time.perf_counter()

    def _record_end(self, event_id: str, component: str):
        if event_id in self._start_times:
            elapsed = time.perf_counter() - self._start_times[event_id]
            self.timings[component].append(elapsed)
            del self._start_times[event_id]

    def on_llm_start(self, serialized, prompts, **kwargs):
        self._record_start(f"llm_{id(prompts)}")

    def on_llm_end(self, response, **kwargs):
        self._record_end(f"llm_{id(response)}", "LLM")

    def on_tool_start(self, serialized, input_str, **kwargs):
        self._record_start(f"tool_{serialized.get('name')}_{id(input_str)}")

    def on_tool_end(self, output, **kwargs):
        self._record_end(f"tool_{id(output)}", "Tool")

    def get_report(self) -> Dict[str, Dict[str, float]]:
        """生成性能报告"""
        report = {}
        for component, durations in self.timings.items():
            report[component] = {
                "count": len(durations),
                "avg_ms": (sum(durations) / len(durations)) * 1000,
                "max_ms": max(durations) * 1000,
                "total_ms": sum(durations) * 1000,
            }
        return report

# 使用示例
monitor = PerformanceMonitorCallback()
agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    callbacks=[monitor],
)

for _ in range(5):
    agent_executor.invoke({"input": "查一下 iPhone 的价格"})

report = monitor.get_report()
for component, stats in report.items():
    print(f"{component}: 调用 {stats['count']} 次，"
          f"平均 {stats['avg_ms']:.1f}ms，最大 {stats['max_ms']:.1f}ms")
```

### 3.3 监控指标采集

```python
class MetricsCollectorCallback(BaseCallbackHandler):
    """采集 Prometheus 兼容的指标数据"""

    def __init__(self):
        self.metrics = {
            "llm_calls": 0,
            "tool_calls": 0,
            "chain_calls": 0,
            "errors": 0,
            "total_tokens": 0,
        }

    def on_llm_start(self, serialized, prompts, **kwargs):
        self.metrics["llm_calls"] += 1

    def on_llm_end(self, response, **kwargs):
        if response.llm_output and "token_usage" in response.llm_output:
            usage = response.llm_output["token_usage"]
            self.metrics["total_tokens"] += (
                usage.get("prompt_tokens", 0) + usage.get("completion_tokens", 0)
            )

    def on_tool_start(self, serialized, input_str, **kwargs):
        self.metrics["tool_calls"] += 1

    def on_chain_start(self, serialized, inputs, **kwargs):
        self.metrics["chain_calls"] += 1

    def on_llm_error(self, error, **kwargs):
        self.metrics["errors"] += 1

    def on_tool_error(self, error, **kwargs):
        self.metrics["errors"] += 1
```

---

## 4. 自定义回调处理器

### 4.1 审计日志回调

```python
import json
from datetime import datetime

class AuditLogCallback(BaseCallbackHandler):
    """记录完整的工具调用审计日志"""

    def __init__(self, log_file: str = "audit.log"):
        self.log_file = log_file
        self.current_run = {}

    def _write_log(self, event: dict):
        event["timestamp"] = datetime.now().isoformat()
        with open(self.log_file, "a", encoding="utf-8") as f:
            f.write(json.dumps(event, ensure_ascii=False) + "\n")

    def on_tool_start(self, serialized, input_str, **kwargs):
        run_id = kwargs.get("run_id")
        self.current_run = {
            "run_id": str(run_id),
            "tool": serialized.get("name"),
            "input": input_str,
            "status": "started",
        }

    def on_tool_end(self, output, **kwargs):
        self.current_run["status"] = "completed"
        self.current_run["output"] = str(output)[:500]
        self._write_log(self.current_run)

    def on_tool_error(self, error, **kwargs):
        self.current_run["status"] = "failed"
        self.current_run["error"] = str(error)
        self._write_log(self.current_run)
```

### 4.2 速率限制回调

```python
import time
from collections import deque

class RateLimitCallback(BaseCallbackHandler):
    """防止 LLM API 调用过频的限流回调"""

    def __init__(self, max_calls: int = 10, window_seconds: int = 60):
        self.max_calls = max_calls
        self.window_seconds = window_seconds
        self.call_timestamps = deque()

    def on_llm_start(self, serialized, prompts, **kwargs):
        now = time.time()
        # 清理窗口外的记录
        while self.call_timestamps and self.call_timestamps[0] < now - self.window_seconds:
            self.call_timestamps.popleft()

        # 检查是否超过限流阈值
        if len(self.call_timestamps) >= self.max_calls:
            wait_time = self.call_timestamps[0] + self.window_seconds - now
            if wait_time > 0:
                print(f"达到限流阈值，等待 {wait_time:.1f} 秒...")
                time.sleep(wait_time)

        self.call_timestamps.append(now)
```

---

## 5. 综合实战：生产级监控方案

```python
import logging
from langchain.callbacks import CallbackManager
from langchain.agents import create_openai_tools_agent, AgentExecutor

# 初始化所有回调
logging_cb = LoggingCallbackHandler()
monitor_cb = PerformanceMonitorCallback()
metrics_cb = MetricsCollectorCallback()
audit_cb = AuditLogCallback("mall_agent_audit.log")

# 组合使用
callback_manager = CallbackManager([
    logging_cb,
    monitor_cb,
    metrics_cb,
    audit_cb,
])

# 构建生产级 Agent
agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    callbacks=callback_manager,
    verbose=False,  # 生产环境关闭 verbose
    max_iterations=8,
)

# 处理请求
result = agent_executor.invoke({"input": "我的订单 O20240801001 什么时候到？"})

# 获取性能报告
print(monitor_cb.get_report())
print(f"指标汇总：{metrics_cb.metrics}")
```

---

## 总结

- **Callbacks** 提供统一的事件机制，覆盖 LLM、Chain、Tool、Agent 的完整生命周期
- **LangSmith** 是 LangChain 官方可观测性平台，支持 Trace 视图、延迟分析、在线评估
- **日志与监控** 需要将 Callbacks 与标准日志系统（logging）、指标系统（Prometheus）集成
- **自定义回调** 可根据业务需求实现审计日志、限流、成本追踪等自定义逻辑
- 生产环境建议组合多个回调处理器，实现可观测性全覆盖

---

> 下一篇：[03-langgraph-intro.md](./03-langgraph-intro.md) — LangGraph 核心概念与状态机实战