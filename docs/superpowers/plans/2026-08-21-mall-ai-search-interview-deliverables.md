# 云商城智能搜索项目面试交付物实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于指定 Word 教程和 `mall-ai-search` 项目真实源码，生成一套证据驱动、区分“已实现/设计/规划”的 Java + AI 后端面试交付物。

**Architecture:** 采用文档库已有 `projects/ai-mall/` 目录，在项目深挖、知识图谱、题库三个职责清晰的 Markdown 文件中分别沉淀分析结论、知识关系与题目答案。所有项目特定结论必须关联源码路径、方法或 Word 章节；不修改目标项目源码，不复制任何密钥、密码或完整连接串。

**Tech Stack:** Markdown；Word/DOCX 只读分析；Python/FastAPI；LangChain/LangGraph；Redis Vector Store；MySQL；OpenAI-compatible Embedding/LLM；Java/Spring Cloud/OpenFeign/Gateway 概念；现有 `interview-note` 文档库。

**Spec:** 用户已确认的聊天设计：专业 Goal、三份标准化 Markdown 交付物、五级难度、十类题型、实现与规划边界、事实一致性和敏感信息校验。

## Global Constraints

- 只新增 `interview-note/projects/ai-mall/` 下的交付文档与必要索引，不修改 `mall-ai-search` 目标项目源码。
- 不在输出中复述 `.env` 的 API Key、Token、密码、完整数据库连接串或 Redis 认证信息。
- Redis Vector Store、不是本模块内的 Elasticsearch；当前无独立 Rerank、BM25、消息队列和业务结果缓存实现。
- 明确区分源码已确认、Word 文档描述、架构规划、推断/待验证；不能把规划能力包装为已实现能力。
- 题目必须覆盖 Level 1—5 和选择、判断、简答、原理、代码、Bug、场景、项目深挖、系统设计、综合追问十类题型。
- 使用相对可复核路径并保留方法/接口名；外部绝对路径只作为分析来源说明，不泄露敏感值。
- 交付物完成前执行 Markdown 结构、敏感信息模式和关键事实一致性检查。

---

### Task 1: 建立项目事实分析报告

**Files:**
- Create: `projects/ai-mall/mall-ai-search-project-analysis.md`
- Reference: `D:\code\codeClaudeCode\demo-practicalTrainingProject\第九章 云商城智能搜索的设计与实现.docx`
- Reference: `D:\code\codeClaudeCode\demo-practicalTrainingProject\mall-ai\mall-ai-search`

**Interfaces:**
- Consumes: 已完成的 Word 分析报告与源码分析报告，包括模块、类、方法、接口、配置边界和文档矛盾。
- Produces: 后续题库可引用的事实标签、源码证据表、架构流程和风险清单。

- [ ] **Step 1: 写入报告骨架和事实标签规则**

创建以下固定章节：

```markdown
# 云商城智能搜索项目深度分析
## 1. 分析范围与证据等级
## 2. 一句话项目定位
## 3. 业务目标与用户价值
## 4. 全链路架构
## 5. 目录与模块职责
## 6. 技术栈与真实使用边界
## 7. 商品向量同步链路
## 8. AI 搜索与推荐链路
## 9. 查询条件提取链路
## 10. Agent、Tool Calling 与会话记忆
## 11. Provider 抽象与外部依赖
## 12. FastAPI 接口与前端协作
## 13. 异常、超时、性能和可靠性
## 14. 测试覆盖与验证局限
## 15. 文档与源码一致性核验
## 16. 生产化改造路线
## 17. 面试表达版本
```

每个关键结论使用以下标签之一：`[源码已确认]`、`[Word描述]`、`[架构规划]`、`[待验证]`。

- [ ] **Step 2: 写入真实架构与核心链路**

将以下事实落入报告并关联证据：

```text
MySQL sku_info
→ SQLDatabaseLoader.lazy_load()
→ page_content / metadata 映射
→ RecursiveCharacterTextSplitter
→ OpenAI-compatible Embedding
→ RedisVectorStore.add_documents()
→ LangGraph Agent
→ vector_search_tool
→ similarity_search(query, k=10)
→ ProductRecommendResponse
```

明确文件证据：

- `ai-backend/mall-micro-ai-search/src/smart_search/core/vector_sync_service.py`
- `ai-backend/mall-micro-ai-search/src/smart_search/core/search_service.py`
- `ai-backend/mall-micro-ai-search/src/smart_search/config/tools.py`
- `ai-backend/mall-micro-ai-search/src/smart_search/models/schemas.py`
- `ai-backend/mall-micro-ai-search/src/smart_search/api/v1.py`
- `ai-backend/mall-micro-ai-search/src/smart_search/main.py`
- `frontend/ai_search.html`

- [ ] **Step 3: 写入实现边界和风险清单**

至少包含以下核验结论：

1. `RedisVectorStore` 是当前模块真实向量存储；本模块没有 Elasticsearch 客户端或 DSL。
2. `similarity_search(..., k=10)` 没有价格、品牌、库存、上下架等 metadata 硬过滤。
3. `ProductRecommendResponse` 仅做结构校验，没有商品 ID 白名单或 MySQL 二次回查。
4. `InMemorySaver` 不支持跨进程/跨 Pod 会话共享；虽声明 Redis checkpoint 依赖但源码未使用。
5. `/sync` 同步扫描全量有效 SKU，没有增量、删除同步、分布式锁和断点续传。
6. 全局异常响应体含 `code=500`，但 `JSONResponse` 未明确 `status_code=500`，需核验 HTTP 层行为。
7. 前端 `/v1/search/recommend` 与后端 `/api/v1/recommend` 的映射未在模块内得到证明。
8. 前端 `threadId` 与后端 `thread_id` 的绑定存在风险。
9. 文档文字 top5 与源码 `k=10` 不一致。
10. 文档概念上描述条件提取参与推荐，但 `/recommend` 实际仅接收原始 query。

- [ ] **Step 4: 写入生产化改造路线**

按优先级整理：

```text
P0：密钥治理、HTTP 状态码、参数校验、threadId 映射、推荐结果业务回查
P1：增量/删除同步、同步任务异步化、幂等键、分布式锁、失败重试
P2：Redis Checkpoint、多实例会话、混合检索、Rerank、库存/上下架过滤
P3：离线评估、可观测性、SSE、成本控制、多 Provider 故障转移
```

每项说明解决的问题、改造位置和验收指标，不声称这些能力当前已经存在。

- [ ] **Step 5: 加入 30 秒、3 分钟和 10 分钟项目表达**

30 秒版本必须包含：业务、技术链路、个人可讲亮点、边界；3 分钟版本补充数据同步和推荐流程；10 分钟版本主动指出实现缺口并给出生产化方案。

- [ ] **Step 6: 进行报告事实审查**

检查每个“已实现”结论是否有源码证据；把无证据的 ES、Rerank、消息队列、分布式高可用表述改为“未发现/规划”。

---

### Task 2: 建立知识图谱与题目覆盖矩阵

**Files:**
- Create: `projects/ai-mall/mall-ai-search-knowledge-map.md`
- Reference: `projects/ai-mall/mall-ai-search-project-analysis.md`

**Interfaces:**
- Consumes: Task 1 的事实标签、核心路径和风险清单。
- Produces: 题库章节规划、题目编号映射、知识依赖关系和复习路线。

- [ ] **Step 1: 写入知识图谱**

用 Mermaid 或 Markdown 树表达以下关系：

```text
云商城智能搜索
├── 业务层：自然语言商品搜索 / 推荐解释 / 传统分页兜底
├── 数据层：MySQL sku_info / Redis Vector Index
├── RAG：page_content / metadata / chunk / embedding / top-k
├── Agent：LangGraph / vector_search_tool / structured response
├── 会话：thread_id / InMemorySaver / Redis checkpoint 改造
├── 服务层：FastAPI / OpenAI-compatible providers
├── Java 集成：Gateway / Java service / OpenFeign / DTO / timeout
└── 生产化：一致性 / 幂等 / 降级 / 安全 / 可观测性 / 评估
```

- [ ] **Step 2: 写入事实证据矩阵**

表格字段固定为：

| 知识点 | 项目证据 | Word 章节 | 状态 | 对应题目 | 常见误区 |
|---|---|---|---|---|---|

至少覆盖：商品加载、切分、Embedding、Redis Vector、Agent、结构化输出、Checkpoint、Provider、异常、前端协作、测试和生产化缺口。

- [ ] **Step 3: 写入题目覆盖矩阵**

按 Level 1—5 统计题目数量，并按十类题型统计数量。题号必须与 Task 3 完全一致，目标数量为：Level 1 12 题、Level 2 18 题、Level 3 14 题、Level 4 12 题、Level 5 4 题，总计约 60 题。

- [ ] **Step 4: 写入推荐复习路线**

安排为：

```text
项目定位
→ MySQL 到 Redis 的向量同步
→ Embedding / Chunk / Top-K
→ Agent / Tool / Schema
→ 会话与微服务集成
→ 异常和性能
→ 一致性与生产化架构
```

每阶段给出必须能回答的题号范围和自测标准。

- [ ] **Step 5: 审查矩阵完整性**

确认每个核心知识点至少有一道基础题、一道原理题或场景题、一道项目深挖/架构题；确认所有题号只出现一次且无断号。

---

### Task 3: 生成项目专项面试题

**Files:**
- Create: `projects/ai-mall/mall-ai-search-interview-questions.md`
- Reference: `projects/ai-mall/mall-ai-search-project-analysis.md`
- Reference: `projects/ai-mall/mall-ai-search-knowledge-map.md`

**Interfaces:**
- Consumes: Task 1 的事实证据和 Task 2 的题号矩阵。
- Produces: 60 道左右含标准答案、解析、证据、追问和评估要点的专项题库。

- [ ] **Step 1: 写入题库说明和项目 Goal**

开头包含：项目定位、适用岗位、阅读方式、证据等级、实现边界声明和推荐答题策略。

- [ ] **Step 2: 生成 Level 1 基础题 12 题**

覆盖选择题、判断题、简答题，主题至少包括：FastAPI、Pydantic、Embedding、向量数据库、Redis Vector、RAG、Agent、Tool、metadata、Top-K、OpenAI-compatible API、OpenFeign。每题必须给正确答案和基于项目的解析。

- [ ] **Step 3: 生成 Level 2 原理题 18 题**

覆盖：

- `lazy_load()` 与批量写入；
- `chunk_size=256`、`chunk_overlap=25`；
- Token 与字符切分；
- page_content 与 metadata；
- MD5 文档 ID；
- 向量相似度检索；
- Top-K 与阈值；
- Embedding 维度和索引；
- RAG 与普通 LLM；
- Agent 与固定 Chain；
- Tool Calling；
- Pydantic 结构化输出；
- Prompt 约束与幻觉；
- InMemorySaver；
- Provider 抽象；
- HTTP 状态码与业务码；
- Feign 超时；
- `Promise.allSettled`。

- [ ] **Step 4: 生成 Level 3 实战题 14 题**

至少包含 3 道代码题、3 道 Bug 题、5 道场景题和 3 道测试/排障题。题目必须绑定实际代码问题，例如：

1. 为向量同步增加批次失败统计；
2. 为搜索条件增加 `min_price <= max_price` 校验；
3. 修复异常处理 HTTP 状态码；
4. 修复 `threadId` / `thread_id` 映射；
5. 排查旧向量残留；
6. 排查 Agent 空召回仍生成商品；
7. 测试 Agent tool call 和结构化输出失败；
8. 设计外部 Embedding 超时重试。

代码题答案以 Python/FastAPI/LangChain 伪实现或 Java/Spring Cloud 代码片段为主，避免声称未在源码中存在的 API。

- [ ] **Step 5: 生成 Level 4 项目深挖题 12 题**

每题必须包含源码证据和 3—4 轮追问。重点覆盖：

- 为什么 Redis Vector 而不是 ES；
- `/recommend` 与 `/extract` 的职责拆分；
- 条件提取是否真正参与硬过滤；
- Agent 每次重建；
- Redis checkpoint 改造；
- 推荐商品白名单与二次回查；
- 向量同步幂等与删除；
- 前后端路径/参数不一致；
- 长耗时 AI 调用；
- Java 中间微服务的价值与额外延迟；
- 当前项目测试可信度；
- 如何诚实介绍“高可用”。

- [ ] **Step 6: 生成 Level 5 架构设计题 4 题**

设计题覆盖：

1. 亿级商品的增量向量同步平台；
2. 传统关键词 + 向量召回 + Rerank 的混合搜索；
3. 面向生产的 AI 搜索微服务高可用与降级；
4. 支持多轮会话、SSE、成本控制和安全校验的智能搜索系统。

每题必须包含：目标、约束、架构、数据流、接口、存储、故障处理、监控指标、容量估算思路和取舍。

- [ ] **Step 7: 为每道题补全统一答案模板**

每题必须包含：

```markdown
### 题目 X：...
**难度**：...
**类型**：...
**考察点**：...
**项目证据**：...
**问题**：...
**标准答案**：...
**深入解析**：...
**面试官追问**：...
**优秀回答应包含**：...
**常见误答**：...
```

选择题可将“答案”放在选项后；代码题和 Bug 题还必须附最小可读代码与修复说明。

- [ ] **Step 8: 补充项目包装和答题评分标准**

结尾加入：30 秒项目介绍、3 分钟架构介绍、10 分钟深挖策略，以及 100 分评分维度：业务理解 15、链路准确性 25、AI 原理 20、Java 微服务 15、工程可靠性 15、边界诚实性 10。

- [ ] **Step 9: 题库一致性审查**

检查题号、难度、题型计数和证据引用；删除泛化且与项目无关的题；对所有“当前实现”表述回查 Task 1；确认没有把 Rerank、ES、MQ、高可用写成已有实现。

---

### Task 4: 执行交付物质量校验

**Files:**
- Modify: `projects/ai-mall/mall-ai-search-project-analysis.md`（仅在校验发现问题时修正）
- Modify: `projects/ai-mall/mall-ai-search-knowledge-map.md`（仅在校验发现问题时修正）
- Modify: `projects/ai-mall/mall-ai-search-interview-questions.md`（仅在校验发现问题时修正）

**Interfaces:**
- Consumes: 三份已生成 Markdown 文件。
- Produces: 可交付、结构完整、事实边界清晰且无敏感信息的文档集。

- [ ] **Step 1: 检查文件存在和标题结构**

运行：

```bash
python - <<'PY'
from pathlib import Path
files = [
    Path('projects/ai-mall/mall-ai-search-project-analysis.md'),
    Path('projects/ai-mall/mall-ai-search-knowledge-map.md'),
    Path('projects/ai-mall/mall-ai-search-interview-questions.md'),
]
for path in files:
    text = path.read_text(encoding='utf-8')
    assert text.startswith('# '), path
    assert len(text.strip()) > 1000, path
    print(path, 'OK', len(text))
PY
```

预期：三个文件均输出 `OK`。

- [ ] **Step 2: 检查题号和分级统计**

运行 Python 脚本提取 `### 题目 N`，检查：无重复、从 1 开始、无断号；检查 Level 1—5 标题或题目标签均存在；将实际统计写入知识图谱矩阵。

- [ ] **Step 3: 检查敏感信息模式**

运行：

```bash
rg -n -i 'api[_-]?key\s*[:=]\s*[^`\s]+|password\s*[:=]\s*[^`\s]+|secret\s*[:=]\s*[^`\s]+|Bearer\s+[A-Za-z0-9._-]{12,}|redis://[^`\s]*@' projects/ai-mall/mall-ai-search-*.md
```

预期：无真实凭据匹配；允许出现“不要泄露密钥”等说明文字，但不能出现值。

- [ ] **Step 4: 检查关键事实边界**

运行并人工审查搜索结果：

```bash
rg -n 'RedisVectorStore|similarity_search\(query, k=10\)|InMemorySaver|Rerank|Elasticsearch|消息队列|top5|Top 5|top10|k=10|threadId|thread_id|/api/v1|/v1/search' projects/ai-mall/mall-ai-search-*.md
```

确认所有 ES/Rerank/MQ/高可用表述均带“未发现、规划、改造或待验证”边界；确认 `k=10` 与 top5 展示的差异已解释。

- [ ] **Step 5: 检查交付索引和链接**

在 `projects/ai-mall/` 既有文档中补充三份文件入口（若索引文件存在对应章节），并检查相对链接可定位。

- [ ] **Step 6: 最终人工审阅与交付说明**

输出时明确列出：新增文件、题目数量、事实核验范围、未执行的外部依赖测试，以及文档中保留的待验证项。不得声称运行了未实际运行的服务或集成测试。

---

## Self-Review Checklist

- [ ] 三份交付物均有明确单一职责，且路径与现有 `projects/ai-mall/` 结构一致。
- [ ] Task 1 覆盖业务、架构、代码、测试、风险和项目表达。
- [ ] Task 2 覆盖知识图谱、证据矩阵、题目矩阵和复习路线。
- [ ] Task 3 覆盖五级难度、十类题型、答案、证据、追问、误答和评分。
- [ ] Task 4 覆盖 Markdown、题号、敏感信息和事实一致性检查。
- [ ] 没有使用 TODO、TBD 或未定义的接口名作为交付要求。
- [ ] 计划没有要求修改目标项目源码，也没有要求启动需要外部凭据的服务。
- [ ] 计划明确：当前 Redis Vector Store 不等于 Elasticsearch，LLM 生成顺序不等于 Rerank，InMemorySaver 不等于生产高可用。
