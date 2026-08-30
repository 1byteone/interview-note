# LangSmith 面试题大全

> LangSmith 专题题库（1-knowledge 面试题库体系），按 Level 1-4 分级。
> 每题仅保留「问题 + 答案 + 解析 + 代码（可选）」；面试官意图 / 深挖方向 / 扣分项见根目录《[LangChain_LangGraph_LangSmith_面试QA_详解版](../../../LangChain_LangGraph_LangSmith_面试QA_详解版.md)》第三章（Q43-Q56）。
> 完整教程见 `2-learning/stacks/18-langsmith/`（可观测 · 评测 · Prompt 管理五层体系）。

---

## 📚 知识体系

```
LangSmith 核心概念
├── 平台定位（铁三角：LangChain 构建 / LangGraph 编排 / LangSmith 验证）
├── Tracing（Trace / Run / Thread 层级）
├── 接入（环境变量 / traceable / 采样）
├── 评测（Dataset / Experiment / Evaluator 三态）
├── LLM-as-Judge（裁判配置 / 校准四法）
├── Prompt Hub（版本化 / 热更新）
├── 反馈闭环（Feedback / Annotation Queue / Automation）
├── 生产监控（质量 / 健康 / 成本三层）
└── 选型（LangSmith vs Langfuse）
```

---

## 🎯 Level 1：基础题

### 1. LangSmith 是什么？与 LangChain / LangGraph 什么关系？
**答案**：LangSmith 是 LangChain 官方推出的 **AI 应用可观测性与评测平台**，覆盖 LLM/Agent 应用全生命周期：开发期调试（Tracing）→ 评测期量化（Evaluation）→ 生产期监控（Monitoring）。三者是"铁三角"：**LangChain 构建（造）、LangGraph 编排（跑）、LangSmith 验证（看 + 量）**。

**解析**：考生态全貌。要理解三件套分工而非当三个名词；能说出接入零侵入（两个环境变量，业务代码不改一行）。（深挖见详解版 Q43）

### 2. LangSmith 的 Trace / Run / Thread 分别是什么？
**答案**：**Thread（线程/会话）**：跨多次调用的关联维度（同一用户会话的多轮对话聚合）；**Trace（追踪）**：单次执行的完整记录（一棵执行树，根是用户请求）；**Run（运行/步骤）**：Trace 中的一个原子执行单元（一次模型调用、一次工具执行），记录输入输出、token、延迟、错误。层级：**Thread → Trace → Run**。

**解析**：Tracing 概念是地基。瀑布视图（waterfall view）展开 trace、点进任意 run 看细节，是调试复杂 Agent 的核心视图。（深挖见详解版 Q44）

### 3. 如何接入 LangSmith？
**答案**：三步——① 建项目拿 API Key；② `pip install langsmith`；③ 配置环境变量：`LANGCHAIN_TRACING_V2=true` + `LANGCHAIN_API_KEY`（+ 可选 `LANGCHAIN_PROJECT`）。LangChain/LangGraph 应用设置后**自动上报** trace（底层走 Callbacks 机制），业务代码零侵入；非 LangChain 代码用 `@traceable` 装饰器手动打点。

**解析**：落地题。生产注意：API key 走密钥管理；用 `LANGCHAIN_TRACING_SAMPLING_RATE` 采样控成本；私有环境用私有化部署或自建。（深挖见详解版 Q45）

---

## 🎯 Level 2：进阶题

### 4. Dataset 与 Experiment 是什么？评测流程是怎样的？
**答案**：**Dataset（数据集）**= 考卷（输入 + 期望输出 + 标签）；**Experiment（实验）**= 成绩单（目标函数在 Dataset 上批量跑完 + 评估器打分）。流程：Dataset → 目标函数（待测链/Agent）→ 批量运行 → 评估器打分 → Experiment 报告 → 横向对比迭代（换模型 / 换 prompt / 换检索参数）。

**解析**：评估器三态：**代码型**（确定性规则，格式/实体）、**LLM-as-Judge**（语义评分）、**人工**（复杂轨迹兜底）。核心工程观：评测集要从生产坏例沉淀，不能只靠手写理想用例。（深挖见详解版 Q46）

### 5. 什么是 LLM-as-Judge？如何校准？
**答案**：LLM-as-Judge 是用大模型给模型输出评分，适合无标准答案的语义评价（相关性/忠实度/有用性）。**校准四法**：① 评分锚点（每档行为描述）+ JSON 输出约束；② few-shot 对齐；③ 人类反馈回灌（偏好数据调裁判）；④ 防漂移（固定裁判模型 + temperature=0 + 定期复跑黄金样本）。

**解析**：校准是面试重点。要知道局限：裁判与被评模型同圈有 self-bias、成本高、复杂任务裁判不可靠——实践中"规则 + 裁判 + 人工抽样"三层互补。（深挖见详解版 Q47）

### 6. 如何写一个自定义评估器？
**答案**：签名 `(run: Run, example: Example | None) -> dict`，返回 `{"key": 指标名, "score": 分数(0-1/布尔), "comment": 说明}`。代码型评估器做确定性校验（JSON 合法、必含实体、长度、正则）；RAG 场景可复用官方 `rag_answer_faithfulness` / `rag_answer_relevance` / `rag_context_accuracy`。

**解析**：评估器返回结构（key/score/comment）是必考；轨迹级评估器可遍历 Run 树断言"是否调用了某工具"（Agent 场景）。多个评估器组合 `evaluators=[规则, 裁判, ...]`。（深挖见详解版 Q48）

---

## 🎯 Level 3：高级题

### 7. Prompt Hub 有什么价值？如何与生产结合？
**答案**：Prompt Hub 是提示词中心化版本管理仓库：① 版本化 + 回滚；② 团队协作（评论/评星/共享）；③ **热更新**（运行时按 tag 拉取 `hub.pull("org/prompt:prod")`，改 prompt 不重启服务）；④ 环境隔离（dev/prod 不同 tag）；⑤ 绑定评测（版本与 Experiment 分数关联）。发布纪律：先建版本 → 跑评测 → 通过切 tag → 监控回归 → 秒回滚。

**解析**：考"提示词也是资产"的工程观。最佳实践：代码内保留默认 prompt 兜底 + 优先从 Hub 拉 tag。（深挖见详解版 Q49）

### 8. 什么是 Annotation Queue？如何形成"生产反馈 → 评测数据"闭环？
**答案**：Annotation Queue（标注队列）是人工评审工作流：把生产 trace 按规则筛选入队，分派专家打标。**闭环**：生产 trace → Automation 规则筛选（负反馈/低分/异常）→ 入队 → 人工标注 → 沉淀 Dataset → 定期 Experiment 评测 → 驱动改进 → 上线监控回归 → 再筛选。配合 **Feedback API**（`client.create_feedback(run_id, key, score)`）给任意 run 挂结构化反馈。

**解析**：这是 LangSmith 与朴素追踪工具的分水岭——评测不是一次性活动而是生产闭环。负反馈自动入队评审 = 数据飞轮的燃料。（深挖见详解版 Q50）

### 9. Automation 规则能做什么？
**答案**：Automation 是**条件 + 动作**的事件驱动规则引擎：负反馈/低分 → 加入标注队列；错误率/p95 延迟超限 → 触发 Webhook（通知 Slack/钉钉/自建）；特定关键词 → 创建 Dataset 条目；单 run 超预算 → 告警。价值：**把失败自动变成"待评审项 + 评测集素材 + 即时告警"**，可观测从"人盯"进化到"系统自动发现"。

**解析**：要谈防告警疲劳：分级阈值、冷却窗口、聚合去重。（深挖见详解版 Q51）

---

## 🎯 Level 4：专家题

### 10. 生产监控主要看哪些指标？如何定位线上问题？
**答案**：三层指标——① **质量**：在线评估（auto-eval）、用户反馈、错误样本率（发现隐性回归）；② **健康**：错误率、p95/p99 延迟、工具失败率（显性问题）；③ **成本**：每 run 价格、token 趋势、单用户成本异常。定位：监控告警 → 打开 trace 瀑布视图 → 定位模型/检索/工具 → 修复 → 样本入评测集回归。

**解析**：方法论比记 UI 重要。要能串起"自动发现 → 人工确认 → 数据回流"的运维闭环。（深挖见详解版 Q52）

### 11. LangSmith 与 Langfuse 如何选型？
**答案**：差异主轴是**生态绑定 vs 开放自托管**——LangSmith：LangChain 生态零侵入、评测体系最全（含部署）、但平台闭源有供应商锁定；Langfuse：**开源可自托管**、框架无关、数据自主、但评测/部署能力较弱。选型：LangChain 栈 + 接受协同 → LangSmith；多框架混用/数据主权要求 → Langfuse 或自建。

**解析**：选型题要讲权衡而非站队。数据可导出、但 trace 与标注体系绑定平台是最大的隐性成本。（深挖见详解版 Q53）

### 12. LangSmith 如何评测多轮对话 / Agent 轨迹？
**答案**：三层策略——① **轨迹级 LLM-Judge**：把完整执行过程 + 结果喂给裁判，按"任务达成度 / 工具使用合理性 / 路径效率"评分；② **代码断言**：必须调用了某工具、步骤数上限、终态条件；③ **人工 Annotation**：复杂轨迹自动评分不可靠时兜底。**起点是先定义成功标准**（任务达成 / 成本 / 安全），再选评估器组合。

**解析**：Agent 评测前沿考点。区分"运行级评估器"与"最后一步评估器"；生产上自动规则 + 抽样人工复核最务实。（深挖见详解版 Q54）

### 13. 评测如何融入 CI/CD（持续评测）？
**答案**：把跑评测变成流水线 gate——PR → CI 中 `evaluate` 在固定 Dataset 跑目标函数 → **对比 baseline**，低于阈值阻断合并 → 环境分级（开发全量、生产发布前关键子集）→ 生产负反馈持续入集让评测集"活"起来。防 LLM 随机抖动：固定裁判模型/温度、均值 ± 容差、阈值先松后紧。

**解析**：把 LLM 质量变成"可自动验证的工程约束"。评测集要有版本管理（样本增删走 review）。（深挖见详解版 Q55）

### 14. 如何用 LangSmith 定位一个"回答错误"的线上问题？
**答案**：五步——① 收 trace：按 Thread 找到对应执行；② 回看输入：上下文是否含检索片段、问题是否被截断；③ 三处归因：**检索问题**（空召回 → 模型只能编造）、**生成问题**（prompt 被注入/系统指令被覆盖）、**校验问题**（相关性阈值放行低分片段）；④ 样本入 Dataset → 本地复现修复 → Experiment 对比；⑤ Automation 监控同类错误回归。

**解析**：综合应用题，考察"Trace 数据 → 归因 → 修复 → 回归"完整闭环能力。空召回是幻觉高频源头。（深挖见详解版 Q56）

---

## 📖 学习资源

### 推荐项目
- [LangSmith 官方文档](https://docs.langchain.com/langsmith)（Tracing / Evaluation / Prompt Hub）
- [LangSmith Evaluation Concepts](https://docs.langchain.com/langsmith/evaluation-concepts)
- [LangSmith 实战教程](../../../2-learning/stacks/18-langsmith/README.md)（本仓库 18-langsmith 五层体系）

### 学习路径
1. Level 1：平台定位 → Trace 概念 → 接入
2. Level 2：Dataset/Experiment → LLM-as-Judge → 自定义评估器
3. Level 3：Prompt Hub → 反馈闭环 → Automation
4. Level 4：生产监控 → 选型 → 轨迹评测 → CI 持续评测

### 关联专题
- [LangChain 面试题](../langchain/interview-questions.md)（应用构建）
- [LangGraph 面试题](../langgraph/interview-questions.md)（Agent 运行时）
- [三合一面试 QA 详解版](../../../LangChain_LangGraph_LangSmith_面试QA_详解版.md)（每题的面试官意图 / 深挖 / 扣分项）
- [三合一面试 QA 背诵版](../../../LangChain_LangGraph_LangSmith_面试QA_背诵版.md)（考前速查）