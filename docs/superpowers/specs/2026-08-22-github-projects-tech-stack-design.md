# 4个GitHub项目技术栈深度剖析与面试导向教程 · 设计文档

> 议题：批量拆解分析 4 个 GitHub 项目，按项目维度组织技术栈教程，产出面试导向的可交付成果
> 日期：2026-08-22
> 状态：设计阶段

---

## 一、项目范围

### 1.1 目标项目

| 项目 | 仓库 | 定位 | 技术栈规模 |
|------|------|------|-----------|
| **ruoyi-ai** | `1byteone/ruoyi-ai` | 企业级AI应用开发框架 | ~15个 |
| **ai-passage-creator** | `1byteone/ai-passage-creator-demo` | AI多Agent图文创作平台 | ~10个 |
| **mewpaw-code** | `1byteone/mewpaw-code` | Java CLI编码Agent | ~6个 |
| **zznursing** | `1byteone/zznursing` | 养老机构物联网平台 | ~8个 |
| **合计** | | | **~39个技术栈分析文档** |

### 1.2 交付优先级

按用户指定的顺序：

1. **Phase 1 — 面试导向教程（优先执行）**：每个技术栈面试高频题 + STAR项目亮点 + 面试可直接用的回答框架
2. **Phase 2 — 深度系列教程**：每个技术栈从0到1的项目驱动教程，5-8篇/系列
3. **Phase 3 — 长篇大论收尾**：万字长文覆盖所有知识点

---

## 二、目录结构设计

```
docs/tech-stack-analysis/
├── README.md                         # 总索引 + 导航
├── ruoyi-ai/                         # 📦 项目1: 企业级AI开发框架
│   ├── 00-PROJECT-OVERVIEW.md        # 项目全景：架构图、模块划分、技术栈总表
│   ├── 01-spring-boot-langchain4j.md # Spring Boot 3.5 + LangChain4j 集成
│   ├── 02-multi-llm-factory.md       # 多厂商大模型工厂模式（策略+工厂）
│   ├── 03-rag-pipeline.md            # RAG全链路：文档解析→切分→Embedding→检索→Rerank
│   ├── 04-vector-store-strategy.md   # 向量数据库工厂策略（Milvus/Weaviate/Qdrant）
│   ├── 05-langgraph-flow-engine.md   # langgraph4j 流程编排引擎（11种节点类型）
│   ├── 06-supervisor-agent.md        # Supervisor多智能体调度（Skills/WebSearch/SQL/Chart）
│   ├── 07-mcp-protocol.md            # MCP协议实现（内置工具 + SSE MCP Clients）
│   ├── 08-mybatis-plus-mysql.md      # MyBatis-Plus + MySQL多数据源 + Dynamic-Datasource
│   ├── 09-redis-redisson.md          # Redis + Redisson 分布式锁/缓存/Lock4j
│   ├── 10-sa-token-auth.md           # Sa-Token + JWT 双重认证鉴权
│   ├── 11-bpm-workflow.md            # Warm-Flow BPM审批引擎
│   ├── 12-sse-websocket.md           # SSE + WebSocket 实时通信
│   ├── 13-monitor-deploy.md          # Spring Boot Admin + Docker Compose 部署
│   ├── 14-interview-questions.md     # 面试题汇总（选择/判断/简答/场景/深挖）
│   ├── 15-star-highlights.md         # STAR法则项目亮点
│   └── code-examples/                # 可运行代码示例
│       ├── multi-llm-factory/        # 多模型工厂实现示例
│       ├── rag-pipeline/             # RAG管线关键代码
│       └── supervisor-agent/         # 多Agent调度示例
│
├── ai-passage-creator/               # 📦 项目2: AI文章生成器
│   ├── 00-PROJECT-OVERVIEW.md        # 项目全景
│   ├── 01-spring-ai-alibaba.md       # Spring AI Alibaba + StateGraph 编排
│   ├── 02-multi-agent-orchestration.md # 5Agent协作流程（生成→分析→配图→合成）
│   ├── 03-image-strategy-pattern.md  # 6种配图方式策略模式 + 降级机制
│   ├── 04-sse-streaming.md           # SSE实时流式输出（大纲/正文/事件）
│   ├── 05-human-in-loop.md           # 人机协作三阶段创作流程
│   ├── 06-mybatis-flex-mysql.md      # MyBatis-Flex + MySQL
│   ├── 07-stripe-payment.md          # Stripe 支付 + VIP会员体系
│   ├── 08-redis-redisson.md          # Redis + Redisson 分布式锁
│   ├── 09-vue3-antd.md               # Vue 3.5 + Ant Design Vue 4.2 前端
│   ├── 10-interview-questions.md     # 面试题汇总
│   ├── 11-star-highlights.md         # STAR法则项目亮点
│   └── code-examples/
│       ├── state-graph/              # StateGraph编排示例
│       └── image-strategy/           # 配图策略模式示例
│
├── mewpaw-code/                      # 📦 项目3: CLI编码Agent
│   ├── 00-PROJECT-OVERVIEW.md        # 项目全景
│   ├── 01-java21-springboot.md       # Java 21 + Spring Boot CLI 应用
│   ├── 02-react-agent-loop.md        # ReAct Agent循环（Thought→Action→Observation）
│   ├── 03-langchain4j-tools.md       # LangChain4j 6种内置工具设计
│   ├── 04-security-sandbox.md        # 5层安全沙箱实现
│   ├── 05-tui-repl.md                # TUI/REPL交互模式
│   ├── 06-interview-questions.md     # 面试题汇总
│   ├── 07-star-highlights.md         # STAR法则项目亮点
│   └── code-examples/
│       ├── react-loop/               # ReAct循环实现示例
│       └── sandbox/                  # 安全沙箱实现示例
│
├── zznursing/                        # 📦 项目4: 养老机构物联网平台
│   ├── 00-PROJECT-OVERVIEW.md        # 项目全景
│   ├── 01-spring-boot-iot.md         # Spring Boot IoT后端架构
│   ├── 02-baidu-qianfan-ai.md        # 百度千帆AI集成（LLM/语音/视觉）
│   ├── 03-huawei-iotda.md            # 华为云IoTDA设备接入与管理
│   ├── 04-wechat-miniapp.md          # 微信小程序开发
│   ├── 05-vue3-admin.md              # Vue 3 管理后台
│   ├── 06-mysql-redis.md             # MySQL + Redis 数据架构
│   ├── 07-iot-device-protocol.md     # IoT设备协议（MQTT/CoAP）
│   ├── 08-interview-questions.md     # 面试题汇总
│   ├── 09-star-highlights.md         # STAR法则项目亮点
│   └── code-examples/
│       ├── iot-device/               # IoT设备接入示例
│       └── qianfan-integration/      # 千帆AI集成示例
│
└── cross-cutting/                    # 📦 跨项目综合
    ├── java-ai-ecosystem-comparison.md  # 4项目AI框架对比（LangChain4j vs Spring AI Alibaba vs 千帆）
    ├── enterprise-architecture-patterns.md # 企业架构模式提炼（工厂模式/策略模式/Agent模式）
    └── overall-star-highlights.md     # 综合STAR亮点（面试自我介绍用）
```

### 2.1 总计文档量

| 项目 | 技术栈文档 | 面试题 | STAR亮点 | 代码示例集 | 合计 |
|------|-----------|--------|---------|-----------|------|
| ruoyi-ai | 13 | 1 | 1 | 3 | 18 |
| ai-passage-creator | 9 | 1 | 1 | 2 | 13 |
| mewpaw-code | 5 | 1 | 1 | 2 | 9 |
| zznursing | 7 | 1 | 1 | 2 | 11 |
| 跨项目 | 2 | 0 | 1 | 0 | 3 |
| **总计** | **36** | **4** | **5** | **9** | **54个交付物** |

---

## 三、每篇文档模板（面试导向 Phase 1）

### 3.1 技术栈分析文档模板

```markdown
# N · 标题：技术栈名称

> 一句话描述该技术栈在项目中的角色和定位

## 一、你必须知道的3个核心概念

- 概念1：一句话 + 项目中的具体体现
- 概念2：一句话 + 项目中的具体体现
- 概念3：一句话 + 项目中的具体体现

## 二、项目中的实战应用

### 2.1 解决了什么问题
[项目中的真实问题描述]

### 2.2 核心实现（关键代码片段）
```java
// 关键代码 + 逐行注释
```

### 2.3 设计亮点
[3-5个设计上的亮点]

## 三、面试高频题

### Q1: [面试题]
**考察点：** [面试官想考察什么]
**回答框架：** 
- 背景：项目中遇到了什么场景
- 方案：你的实现方案
- 深度：为什么这样设计，对比其他方案
- 扩展：如果是百万级并发怎么优化

### Q2: [面试题]
...

## 四、面试避坑指南

- ❌ 常见错误回答
- ✅ 正确回答思路
- ⚡ 加分项（能体现技术深度的点）

## 五、参考资料与扩展阅读
```

### 3.2 STAR亮点模板

```markdown
# 项目名 · STAR法则亮点

## 亮点1：[标题]
- **S (Situation):** 项目背景和面临的挑战
- **T (Task):** 你的具体任务和目标
- **A (Action):** 你采取的技术方案和行动
- **R (Result):** 量化成果（性能提升X%、效率提升Y倍等）
- **技术深挖：** 面试官可能追问的3个方向
```

---

## 四、Phase 1 执行策略

### 4.1 核心原则

1. **面试驱动**：每个技术栈文档聚焦面试高频考点，而非教科书式罗列
2. **项目绑定**：所有知识点落实到项目代码中，拒绝纯理论
3. **STAR主线**：每个技术栈产出可面试直接使用的STAR素材
4. **干中学**：一边分析项目代码，一边提炼可复用知识点

### 4.2 资料搜索策略

每个项目启动前，用 `anysearch` skill 搜索：
1. **项目GitHub分析**：仓库结构、技术栈细节、创新点
2. **同类技术比对**：该技术栈在行业中的位置、竞品对比
3. **面试高频考点**：该技术栈面试中常问的问题
4. **最新版本特性**：确保教程基于最新版本

### 4.3 执行顺序

```
Phase 1 执行顺序（按项目复杂度从高到低，先啃硬骨头）:

ruoyi-ai (最大、技术栈最全)
  → ai-passage-creator (AI编排有特色)
  → mewpaw-code (CLI Agent，相对独立)
  → zznursing (IoT，已有一些分析基础)
  → cross-cutting (跨项目综合)
```

---

## 五、质量标准

### 5.1 每个技术栈文档必须回答

- 这个技术栈在项目中**解决了什么具体问题**？
- 项目**为什么选这个技术栈**而不是其他替代方案？
- 代码中**最关键的实现**是什么？（配代码片段）
- 如果面试官问到这个技术栈，**3句话内怎么讲清楚**？
- 面试官可能**深挖的3个方向**是什么？

### 5.2 验收标准

- [ ] 每个技术栈文档 ≥ 1个核心代码片段（带注释）
- [ ] 每个技术栈文档 ≥ 2道面试题（含回答框架）
- [ ] 每个项目 ≥ 3个STAR亮点
- [ ] 所有代码示例可运行（或至少是完整可编译的片段）
- [ ] 所有文档使用统一模板和风格
- [ ] 跨项目对比覆盖：相同技术栈在不同项目中的差异化实现

---

## 六、风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| 某些项目代码未公开，无法深入分析 | 技术栈分析只能基于README | 联系用户获取私有仓库权限，或基于公开信息做合理推断 |
| 技术栈数量过多，执行周期长 | 交付延迟 | 严格按优先级执行，Phase 1面试导向先出，快速交付价值 |
| anysearch搜索不到高价值资料 | 教程深度不足 | 切换为WebSearch + context7 MCP直接搜索官方文档 |
| 代码示例编写耗时 | 进度放缓 | 先产出文档+面试题，代码示例作为独立交付件后补 |