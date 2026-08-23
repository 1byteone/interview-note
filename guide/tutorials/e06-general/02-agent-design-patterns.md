# AI Agent 设计模式：7 种生产级架构

> **生态**: E06 · 通识与基础 | **等级**: 进阶 | **前置要求**: 了解 LLM 基本概念与 Prompt Engineering 基础

构建生产级 AI Agent 系统并非简单的"调用 LLM + 循环"。现实世界的 Agent 需要处理工具调用失败、上下文超限、长时任务、多 Agent 协作、安全合规等一系列挑战。业界在 2025-2026 年的实践中，沉淀出了一套可复用的设计模式体系。

本教程深入解析 7 种核心设计模式，包含多 Agent 架构对比、模式选择框架和真实案例，帮助你搭建可进化的 Agent 系统。

---

## 1. 模式总览：7 种生产级 Agent 设计模式

| # | 模式 | 一句话定位 | 复杂度 | 生产成熟度 |
|---|------|-----------|--------|-----------|
| 1 | **Reflection** | Agent 自我检查与修正输出 | 低 | 极高 |
| 2 | **Tool Use** | 通过函数调用与外部世界交互 | 低 | 极高 |
| 3 | **Planning** | 任务分解与逐步执行 | 中 | 高 |
| 4 | **Multi-agent** | 多 Agent 分工协作 | 高 | 中高 |
| 5 | **RAG** | 知识检索增强上下文 | 中 | 极高 |
| 6 | **Memory** | 短期/长期/情景记忆管理 | 中 | 高 |
| 7 | **Human-in-the-loop** | 人工审批与反馈注入 | 中 | 极高 |

这些模式不是互斥的，一个成熟的 Agent 系统通常组合使用 3-5 种模式。

---

## 2. Pattern 1：Reflection（反思模式）

**核心思想**：Agent 不直接将输出发给用户，而是先自我评估，检查质量、安全性和一致性，必要时自我修正。

### 工作原理

```
LLM 生成 → 自我评估 → 通过 → 输出
                    ↓ 不通过
                  重新生成 → 再次评估 → ...
```

### 代码示例

```python
class ReflectionAgent:
    def __init__(self, llm):
        self.llm = llm

    def generate_with_reflection(self, prompt, max_retries=3):
        for attempt in range(max_retries):
            output = self.llm.generate(prompt)

            # 自我评估
            eval_prompt = f"""
            评估以下 AI 回复的质量：

            原始问题：{prompt}
            AI 回复：{output}

            评估维度（1-5 分）：
            - 准确性：是否与事实一致
            - 完整性：是否覆盖了问题所有方面
            - 安全性：是否有有害内容

            如果所有维度 >= 4 分，回复 "PASS"；否则指出问题并回复 "FAIL: <原因>"
            """

            evaluation = self.llm.generate(eval_prompt)

            if evaluation.startswith("PASS"):
                return output

            # 根据评估结果修正
            correction_prompt = f"""
            你之前的回复存在以下问题：{evaluation}
            请重新生成回复，修正上述问题。

            原始问题：{prompt}
            """
            prompt = correction_prompt

        return output  # 超过最大重试次数，返回最后一次结果
```

### 适用场景

- 代码生成（自动检查语法和逻辑错误）
- 内容审核（检查敏感信息）
- 复杂推理（验证推理链的每一步）
- 翻译质量（检查术语一致性）

### 注意事项

- 增加延迟和 token 消耗（每次反思约 1.5-2x 成本）
- 需要设计合理的评估标准，防止过度修正
- 对确定性任务（如数学计算）效果显著，对创意性任务效果有限

---

## 3. Pattern 2：Tool Use（工具使用模式）

**核心思想**：Agent 通过 Function Calling 或 MCP 协议调用外部工具，突破 LLM 的知识边界和能力限制。

### 工具定义方式

```json
{
  "type": "function",
  "function": {
    "name": "get_weather",
    "description": "获取指定城市的实时天气信息",
    "parameters": {
      "type": "object",
      "properties": {
        "city": {
          "type": "string",
          "description": "城市名称，如"北京"、"上海""
        },
        "unit": {
          "type": "string",
          "enum": ["celsius", "fahrenheit"]
        }
      },
      "required": ["city"]
    }
  }
}
```

### Tool Use 生命周期

```
用户请求 → LLM 分析 → 决定调用工具 → 生成工具参数 → 执行工具
                                                      ↓
                  LLM 整合结果 → 生成最终回复 ← 返回工具结果
```

### 工具类型

| 工具类型 | 示例 | 用途 |
|---------|------|------|
| 信息检索 | 搜索、数据库查询、文档搜索 | 获取外部知识 |
| 计算引擎 | 数学计算、数据分析、代码执行沙箱 | 精确计算 |
| 写操作 | 发送邮件、创建工单、更新数据库 | 执行业务操作 |
| 感知工具 | 摄像头、传感器、日志采集 | 获取实时状态 |
| 通信工具 | 消息推送、Webhook 回调 | 通知与协调 |

### MCP 集成

MCP（Model Context Protocol）是 2025-2026 年标准化工具集成的重要协议。通过 MCP Server，Agent 可以动态发现和调用工具，无需预先定义：

```python
# MCP 客户端示例
class MCPToolClient:
    async def list_tools(self, server_url):
        """动态发现 MCP Server 提供的工具列表"""
        response = await fetch(f"{server_url}/tools/list")
        return response.tools

    async def call_tool(self, server_url, tool_name, args):
        """调用 MCP 工具"""
        response = await fetch(f"{server_url}/tools/call", {
            "name": tool_name,
            "arguments": args
        })
        return response
```

---

## 4. Pattern 3：Planning（规划模式）

**核心思想**：将复杂任务分解为多个子任务，按依赖关系逐步执行，并在执行过程中动态调整计划。

### 规划模式的工作流

```
输入任务 → 任务分解 → 创建执行图 → 逐步执行 → 任务完成
                            ↑            ↓
                         动态调整 ← 执行失败/新信息
```

### 规划实现示例

```python
class PlanningAgent:
    def plan(self, task):
        """将任务分解为子任务列表"""
        plan_prompt = f"""
        将以下任务分解为可执行的子任务列表：

        任务：{task}

        要求：
        1. 每个子任务必须是独立的、可执行的步骤
        2. 标注子任务之间的依赖关系
        3. 标注每个子任务需要的工具或资源

        输出格式（JSON）：
        {{
            "subtasks": [
                {{
                    "id": 1,
                    "name": "子任务名称",
                    "depends_on": [0],  // 前置依赖的任务 ID，0 表示无依赖
                    "tool": "所需工具",
                    "description": "子任务描述"
                }}
            ]
        }}
        """
        plan = self.llm.generate(plan_prompt)
        return json.loads(plan)

    def execute_plan(self, plan):
        """按依赖关系执行计划"""
        executed = set()
        results = {}

        while len(executed) < len(plan["subtasks"]):
            for task in plan["subtasks"]:
                if task["id"] in executed:
                    continue
                # 检查前置依赖是否完成
                deps = task["depends_on"]
                if all(d == 0 or d in executed for d in deps):
                    # 收集依赖结果作为上下文
                    context = {d: results[d] for d in deps if d != 0}
                    result = self.execute_task(task, context)
                    results[task["id"]] = result
                    executed.add(task["id"])

        return results
```

### 适用场景

- 多步骤数据分析（数据采集 → 清洗 → 分析 → 可视化）
- 软件架构设计（需求分析 → 模块划分 → 接口设计 → 文档生成）
- 报告生成（信息搜集 → 摘要 → 排版 → 审核）
- 自动化工作流（CRM 操作链、部署流水线）

---

## 5. Pattern 4：Multi-agent（多 Agent 模式）

**核心思想**：多个专用 Agent 分工协作，每个 Agent 负责特定领域，通过通信机制协同完成任务。

### 四种多 Agent 架构对比

| 架构 | 协作方式 | 优势 | 劣势 | 适用场景 |
|------|---------|------|------|---------|
| **Subagents** | 主 Agent 创建子 Agent 执行子任务 | 简单、隔离性好 | 通信开销大 | 并行子任务 |
| **Skills** | 预定义可复用技能模块 | 低延迟、可复用 | 灵活性差 | 标准化操作 |
| **Handoffs** | Agent 间转移对话控制权 | 自然、可追溯 | 复杂度高 | 客服分级 |
| **Routers** | 路由器根据意图分发到指定 Agent | 扩展性好、负载均衡 | 单点瓶颈 | 多服务整合 |

### Subagents 架构

```
主 Agent
├── 子 Agent A（数据采集）
├── 子 Agent B（数据分析）
└── 子 Agent C（报告生成）
```

主 Agent 负责任务分解、子 Agent 调度和结果整合。子 Agent 专注单一职责，可独立开发、测试和部署。

### Routers 架构

```
用户请求 → Router（意图识别）
            ├── 代码生成 Agent
            ├── 文档咨询 Agent
            ├── 故障诊断 Agent
            └── 通用对话 Agent
```

路由器根据用户意图将请求分发到对应 Agent。每个 Agent 可以独立升级，不影响其他服务。

### 实战：Google Cloud Agent 设计模式

Google Cloud 推荐的 Agent 设计模式包含以下组件：

- **Orchestrator**：负责任务分解、调度和监控
- **Specialist Agents**：领域专家，各司其职
- **Evaluator**：评估输出质量，触发重试或升级
- **Knowledge Base**：共享知识库，所有 Agent 可访问
- **Memory Store**：共享状态管理

### 实战：Azure 多 Agent 架构

Azure 的架构强调**企业级安全与治理**：

- **Enterprise Agents**：业务域 Agent（销售、客服、运维）
- **Guardrails**：安全护栏，防止 Agent 越权
- **Audit Logs**：所有 Agent 操作记录
- **Human Approval Gates**：关键操作需要人工审批

---

## 6. Pattern 5：RAG（检索增强生成）

**核心思想**：在 LLM 生成响应前，从外部知识库检索相关文档，作为上下文注入，解决 LLM 知识截止和幻觉问题。

### RAG 模式在 Agent 中的位置

```
用户请求 → 意图理解 → 检索策略 → 知识检索 → 上下文注入 → LLM 生成
                ↓
           是否需要检索
```

### 在 Agent 中集成 RAG

```python
class RAGAgent:
    def __init__(self, llm, vector_store, embedder):
        self.llm = llm
        self.vector_store = vector_store
        self.embedder = embedder

    def query(self, user_question, top_k=5):
        # 1. 判断是否需要检索
        retrieval_prompt = f"""
        判断以下问题是否需要查询外部知识库才能回答：
        问题：{user_question}
        如果问题是常识性知识（如"你好"、"今天天气怎么样"），回复 "NO_RETRIEVAL"
        如果问题涉及专业知识或最新信息，回复 "NEED_RETRIEVAL"
        """
        decision = self.llm.generate(retrieval_prompt)

        # 2. 检索
        if "NEED_RETRIEVAL" in decision:
            query_embedding = self.embedder.embed(user_question)
            docs = self.vector_store.search(query_embedding, top_k)

            # 3. 构建上下文
            context = "\n\n".join([f"[文档{i+1}] {doc.content}" for i, doc in enumerate(docs)])

            # 4. 增强生成
            final_prompt = f"""
            基于以下检索到的信息回答问题。

            检索到的上下文：
            {context}

            用户问题：{user_question}

            要求：
            - 优先使用检索到的信息回答
            - 如果信息不足以回答问题，明确说明
            - 引用信息来源 [文档编号]
            """
            return self.llm.generate(final_prompt)
        else:
            # 直接回答
            return self.llm.generate(user_question)
```

详细 RAG 设计见本系列第三篇教程 [生产级 AI Agent 系统设计](./03-production-system-design.md)。

---

## 7. Pattern 6：Memory（记忆模式）

**核心思想**：为 Agent 配备多层级记忆系统，使其能够在多轮对话和长期任务中保持上下文一致性。

### 三层记忆架构

| 记忆层级 | 存储介质 | 生命周期 | 容量 | 用途 |
|---------|---------|---------|------|------|
| **短期记忆** | LLM 上下文窗口 | 单次会话 | 有限（2K-200K tokens） | 当前对话上下文 |
| **长期记忆** | 向量数据库 | 跨会话持久化 | 大 | 用户偏好、历史知识 |
| **情景记忆** | 日志/数据库 | 永久 | 极大 | 操作记录、决策轨迹 |

### 记忆管理实现

```python
class MemoryManager:
    def __init__(self, vector_store, max_short_term=10000):
        self.short_term = []  # 短期记忆（对话窗口）
        self.long_term = vector_store  # 长期记忆（向量存储）
        self.episodic = []  # 情景记忆（操作日志）
        self.max_short_term = max_short_term

    def add_to_short_term(self, message):
        """添加短期记忆，超出时压缩"""
        self.short_term.append(message)
        if len(self.short_term) > self.max_short_term:
            self.summarize_and_archive()

    def summarize_and_archive(self):
        """压缩短期记忆并归档到长期记忆"""
        summary = self.llm.generate(f"总结以下对话：{self.short_term[:20]}")
        self.long_term.add(summary)
        self.short_term = self.short_term[-20:]  # 保留最近 20 条

    def retrieve_relevant_memory(self, query, top_k=5):
        """检索相关长期记忆"""
        memories = self.long_term.search(query, top_k)
        return memories
```

---

## 8. Pattern 7：Human-in-the-loop（人工介入模式）

**核心思想**：在 Agent 执行流程的关键节点设置审批门，确保高风险操作由人工确认。

### 审批门类型

| 类型 | 触发条件 | 超时处理 | 适用场景 |
|------|---------|---------|---------|
| 前置审批 | 执行高风险操作前 | 自动拒绝 | 删除数据、发送邮件、支付 |
| 后置审核 | 生成结果后，发送前 | 自动暂存 | 内容发布、报告生成 |
| 异常升级 | Agent 无法处理时 | 通知人工 | 客服投诉、复杂问题 |
| 阶段性确认 | 长任务每个阶段完成后 | 自动继续 | 多步骤工作流 |

### 实现示例

```python
class HumanInTheLoop:
    def __init__(self, notification_service):
        self.notifier = notification_service
        self.pending_approvals = {}

    async def request_approval(self, action, context, timeout=300):
        """请求人工审批"""
        approval_id = generate_id()
        self.pending_approvals[approval_id] = {
            "action": action,
            "context": context,
            "status": "pending",
            "timeout": timeout
        }

        # 发送审批通知
        await self.notifier.send(
            type="approval_request",
            title=f"审批请求：{action['description']}",
            content=f"""
            操作：{action['name']}
            参数：{action['parameters']}
            上下文：{context}
            审批链接：/approve/{approval_id}
            """
        )

        # 等待审批（带超时）
        approval = await self.wait_for_approval(approval_id, timeout)
        return approval

    async def wait_for_approval(self, approval_id, timeout):
        """等待审批结果，超时自动拒绝"""
        try:
            result = await asyncio.wait_for(
                self.approval_queue.get(approval_id),
                timeout=timeout
            )
            return result
        except asyncio.TimeoutError:
            return {"status": "rejected", "reason": "审批超时"}
```

---

## 9. 模式选择框架

如何为你的场景选择合适的设计模式？以下决策树可以帮助你快速判断：

```
你的 Agent 需要做什么？
│
├── 简单的单步任务 → Reflection + Tool Use
│   示例：翻译、格式化、简单查询
│
├── 多步骤复杂任务 → Planning + Tool Use + Reflection
│   示例：数据报告生成、代码审查流程
│
├── 需要专业知识 → RAG + Memory + Tool Use
│   示例：客服支持、法律咨询、医疗建议
│
├── 需要多人协作 → Multi-agent + Handoffs/Routers
│   示例：项目管理系统、客服中心
│
└── 涉及高风险操作 → 以上所有 + Human-in-the-loop
    示例：金融交易、医疗诊断、自动化运维
```

### 成熟度演进路线

```
Level 1: Tool Use + Reflection（基础能力）
    ↓
Level 2: + Planning + Memory（任务管理）
    ↓
Level 3: + RAG（知识增强）
    ↓
Level 4: + Multi-agent（协作扩展）
    ↓
Level 5: + Human-in-the-loop + 持续监控（生产级）
```

---

## 总结

7 种设计模式构成了生产级 Agent 系统的核心架构工具箱。关键洞察：

1. **没有银弹**：每个模式都有适用场景和成本，需要根据实际需求组合使用
2. **渐进式演进**：从 Level 1 开始，逐步增加模式，避免过度设计
3. **模式互不冲突**：Reflection 可用在所有模式中，Tool Use 是几乎所有 Agent 的标配
4. **架构决策影响深远**：选择 Subagents 还是 Routers 会直接影响系统的可扩展性和维护成本

下一教程：[生产级 AI Agent 系统设计](./03-production-system-design.md)，将以上述设计模式为基础，深入探讨端到端系统的架构设计、优化策略和生产运维。