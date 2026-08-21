# mall-ai 项目群深度面试分析

> 项目路径：`D:\code\codeClaudeCode\demo-practicalTrainingProject\mall-ai`
> 包含子项目：text2sql（AI Text-to-SQL）、mall-Ai（MyBatis-Plus实训）、code-generate（代码生成器）

---

## 📊 项目一：text2sql — AI 自然语言转 SQL 系统

### 项目架构

```
text2sql（多模块 Spring Boot 3.x + Spring AI + DeepSeek LLM）
│
├── text2sql-web          → Web 层：控制器 + 会话管理 + 异常处理
│   ├── Text2SQLController    → 核心 SQL 生成接口
│   ├── ConversationController → 多轮对话管理
│   └── Text2SQLFrontendController → 前端页面支持
│
├── text2sql-ai           → AI 核心：LLM 客户端 + RAG + Embedding + 向量检索
│   ├── DeepSeekLLMClient     → DeepSeek 大模型 API 封装
│   ├── RAGRetrievalService   → 混合检索（向量+关键词）
│   ├── VectorStoreService    → 向量存储与检索
│   ├── EmbeddingService      → 向量化服务
│   ├── PromptBuilder         → 提示词构建
│   ├── SQLGeneratorService   → SQL 生成服务
│   └── SQLExampleLibrary     → SQL 示例库
│
├── text2sql-core         → 核心领域模型
│   ├── MSchema / MSchemaColumn → 数据库 Schema 模型
│   ├── NaturalLanguageQuery    → 自然语言查询模型
│   ├── GeneratedSQL / DataSample → SQL 结果模型
│   └── CacheManager           → 缓存管理
│
├── text2sql-schema       → Schema 检索与增强
│   ├── TableRetrievalService  → 表检索
│   └── SchemaEnhancer         → Schema 增强
│
├── text2sql-validator    → SQL 验证与执行
│   ├── 使用 jsqlparser 解析 SQL
│   └── 验证 SQL 语法正确性
│
├── text2sql-common       → 公共模块
│   ├── 枚举：QueryIntent / QueryComplexity / SQLErrorType
│   ├── 异常：Text2SQLException
│   └── 响应：ApiResponse
│
└── frontend              → React + TypeScript + Vite 前端
    ├── ChatGPT 风格对话界面
    ├── ECharts 图表展示
    └── AG-Grid 数据表格
```

### 技术栈全景

| 分类 | 技术 | 用途 |
|------|------|------|
| 框架 | Spring Boot 3.x + Spring Data JPA | 基础框架 |
| AI | Spring AI + DeepSeek LLM | 大模型接入 |
| RAG | 向量检索 + 关键词检索 + Schema 检索 | 增强检索 |
| 向量存储 | VectorStoreService（自定义实现） | 向量化存储 |
| 缓存 | Caffeine | 本地缓存加速 |
| 数据库 | MySQL + H2（测试） | 持久化 |
| 前端 | React 18 + TypeScript + Vite | 用户界面 |
| 图表 | ECharts + Recharts | 数据可视化 |
| 表格 | AG-Grid | 数据展示 |
| 状态管理 | Zustand | 前端状态管理 |
| 文档 | SpringDoc OpenAPI (Swagger) | API 接口文档 |
| SQL 解析 | jsqlparser | SQL 语法验证 |
| 映射 | MapStruct | 对象映射 |

---

### 🎯 核心考点：RAG 检索增强生成

#### 题目 1：text2sql 中 RAG 是怎么实现的？向量检索 + 关键词检索为什么都要？

**答案**：

```java
// RAGRetrievalService.java 中混合检索策略
public List<MSchema> retrieveRelevantSchemas(String query, int topK) {
    // 1. 向量检索（语义相似度）
    List<RetrievedSchema> vectorResults = vectorStoreService.searchSimilarSchemas(query, topK);
    
    // 2. 关键词检索（精确匹配表名/列名）
    List<String> keywordResults = tableRetrievalService.retrieveRelevantTables(query, topK);
    
    // 3. 融合结果：向量优先 + 关键词补充
    Set<String> uniqueTables = new LinkedHashSet<>();
    vectorResults.stream().map(RetrievedSchema::getTableName).forEach(uniqueTables::add);
    uniqueTables.addAll(keywordResults);
    
    // 4. 生成完整 M-Schema 上下文
    List<MSchema> schemas = finalTables.stream()
        .map(tableName -> schemaEnhancer.generateMSchema(tableName))
        .collect(Collectors.toList());
    return schemas;
}
```

**为什么混合检索**：
- **向量检索**：理解语义，如"查询入职满一年的员工"→ 匹配到 `employee` 表（即使不包含"员工"二字）
- **关键词检索**：精确匹配，如"员工表"→ 直接命中 `employee` 表
- 单一检索方式召回不全，混合互补效果好

**追问**：向量检索的向量是怎么来的？Embedding 模型怎么选？
- 答：`EmbeddingService` 调用 DeepSeek API 生成向量，或者使用本地 Embedding 模型
- 选型考虑：中文能力、向量维度、最大序列长度、推理性能

#### 题目 2：意图分析（IntentAnalysisService）是怎么做的？

**答案**：

```java
// 意图分析流程
public IntentAnalysisResult analyzeIntent(String prompt) {
    // 1. 识别业务领域（关键词匹配）
    String businessDomain = identifyBusinessDomain(prompt);
    // 2. 提取实体（正则匹配："XXX表"、"XXX中"、"XXX的"）
    List<String> entities = extractEntities(prompt);
    // 3. 匹配数据库表（多维度打分）
    List<TableMatch> matchedTables = matchTables(prompt, entities);
    // 4. 识别 SQL 操作类型
    List<String> sqlOperations = identifySQLOperations(prompt);
    // 5. 检测是否需要 JOIN
    boolean needsJoin = detectJoinRequirement(prompt);
    // 6. 提取筛选条件
    List<String> filters = extractFilters(prompt);
}
```

**表匹配的评分机制**：
| 维度 | 分值 | 说明 |
|------|------|------|
| 表名匹配 | +50 | 实体命中表名 |
| 表注释匹配 | +40 | 实体命中表注释 |
| 关键词匹配 | +30 | 如"员工"→employee表 |
| 列名匹配 | +10 | 列名在提示词中 |
| 列注释匹配 | +15 | 列注释命中实体 |

**追问**：如果用户说"查一下最近入职的员工"，系统如何识别"入职"对应 hire_date 列？
- 答：在 `matchTables()` 方法中有特殊字段映射：`"入职" → hire_date/join_date/created_date/entry_date`，匹配到列名后加 20 分

---

### 🎯 核心考点：AI 模型集成

#### 题目 3：DeepSeek LLM 是怎么接进来的？怎么构造 Prompt？

**答案**：

```java
// DeepSeekLLMClient.java — 封装 DeepSeek API
// 使用 Spring AI 的 OpenAI 兼容 API
// spring-ai-openai-spring-boot-starter 自动配置

// PromptBuilder.java — 构建提示词
// 包含：系统角色定义 + Schema 上下文 + SQL 示例 + 用户查询
// 利用 RAG 检索到的 Schema 信息作为上下文
```

**Prompt 构建策略**：
```
系统角色：你是一个 SQL 专家，根据数据库 Schema 生成 SQL
    ↓
Schema 上下文：RAG 检索到的相关表结构
    ↓
SQL 示例：few-shot 示例（从 SQLExampleLibrary 检索）
    ↓
用户查询：自然语言问题
    ↓
输出格式：限制为纯 SQL + 简短说明
```

**追问**：DeepSeek 和 OpenAI 的 API 兼容性怎么处理的？
- 答：Spring AI 的 `openai-spring-boot-starter` 可以配置不同的 base-url，DeepSeek 兼容 OpenAI 接口格式，只需改配置就能切换

#### 题目 4：SQL 生成后怎么验证？出错了怎么办？

**答案**：
- **text2sql-validator** 模块使用 `jsqlparser` 解析 SQL 语法，检查合法性
- **SQLExecutionService** 实际执行 SQL 并返回结果
- **ErrorFixService** 自动修复错误：解析错误信息 → 调整 SQL → 重新执行
- **IntelligentResponseService** 将执行结果智能化呈现（表格/图表/文字）

---

### 🎯 核心考点：系统设计

#### 题目 5：多轮对话的上下文怎么管理的？

**答案**：
- **ConversationService** 管理会话会话
- **ConversationSession** 和 **ConversationTurn** 存储对话历史（JPA 持久化）
- **CompressedContext** 压缩上下文，避免超出 LLM 上下文窗口
- 每次查询时，将历史对话作为上下文传入 LLM

#### 题目 6：缓存怎么设计的？Caffeine 用在哪？

**答案**：
- **RAGRetrievalService** 的 `@Cacheable(value = "rag-retrieval", key = "#query + '-' + #topK")`
- **CacheManager** 统一管理缓存策略
- **Caffeine** 本地缓存：高性能、可配置过期时间、最大容量
- 缓存命中时直接返回，避免重复调用 LLM API（节省成本）

---

## 📊 项目二：mall-Ai — MyBatis-Plus 实训项目

### 项目概述

Spring Boot 3.1.5 + MyBatis-Plus + MySQL + Druid 的 MyBatis 学习项目，分 6 个阶段（p1-p6），从基础到高级。

### 技术栈
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.1.5 | 基础框架 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| MySQL | 8.x | 数据库 |
| Druid | 1.2.20 | 连接池 |
| Hutool | 5.8.25 | 工具类 |
| Lombok | 1.18.30 | 代码简化 |

### 🎯 面试题

#### 题目 7：MyBatis-Plus 和 MyBatis 的区别？为什么用 MyBatis-Plus？

**答案**：
| 特性 | MyBatis | MyBatis-Plus |
|------|---------|--------------|
| 基础 CRUD | 手写 SQL | 内置 BaseMapper |
| 分页 | 手写分页 | 内置 Page 插件 |
| 条件查询 | 手写 SQL | LambdaQueryWrapper |
| 代码生成 | 无 | MyBatis-Plus Generator |
| 注解 | 基础 | 丰富（@TableId/@TableField） |

**追问**：LambdaQueryWrapper 和普通 QueryWrapper 有什么区别？
- 答：LambdaQueryWrapper 使用 Lambda 表达式（`User::getName`），避免硬编码字段名，编译期即可检查

#### 题目 8：Hutool 工具类在项目中怎么用的？

**答案**：
- 字符串处理：`StrUtil.isBlank()`、`StrUtil.format()`
- 集合操作：`CollUtil.isEmpty()`
- 日期处理：`DateUtil.format()`
- 加密解密：`SecureUtil.md5()`
- JSON 处理：`JSONUtil.toJsonStr()`

---

## 📊 项目三：code-generate — 代码生成器

### 项目概述

MyBatis-Plus Generator + Velocity 模板引擎的代码生成工具，Java 1.8，从数据库表生成 Entity/Mapper/Service/Controller。

### 🎯 面试题

#### 题目 9：代码生成器怎么实现的？

**答案**：
- **MyBatis-Plus Generator**：扫描数据库表 → 生成各层代码
- **Velocity 模板引擎**：自定义模板文件（`entity.java.vm`、`mapper.java.vm` 等）
- 配置：`generator.properties` 设置包名、表名、策略

#### 题目 10：对比 MyBatis-Plus Generator 和手动写代码，有什么优缺点？

**答案**：
| 对比 | 自动生成 | 手动编写 |
|------|----------|----------|
| 效率 | 高（秒级） | 低 |
| 代码质量 | 统一规范 | 因人而异 |
| 灵活性 | 低（模板限制） | 高 |
| 调试 | 容易（生成后修改） | 容易 |
| 推荐 | 常规 CRUD | 复杂业务逻辑 |

---

## 🎯 项目经验包装建议

### 项目定位（30 秒介绍）

#### text2sql
> 这是一个基于 **Spring AI + DeepSeek LLM + RAG** 的自然语言转 SQL 系统，采用多模块架构，包含 **意图分析 → Schema 检索 → SQL 生成 → 验证执行** 完整流程，支持多轮对话和智能纠错。

**关键词**：Spring AI、RAG（混合检索）、DeepSeek LLM、意图分析、SQL 验证

#### mall-Ai
> 这是一个基于 **Spring Boot 3.1.5 + MyBatis-Plus** 的电商系统学习项目，包含 6 个递进阶段的 MyBatis 实践，覆盖单表/多表/复杂查询/分页等核心场景。

**关键词**：MyBatis-Plus、LambdaQueryWrapper、分页插件、Druid

### 面试追问链

```
面试官：text2sql 的 RAG 怎么做的？
    ↓
你：向量检索 + 关键词检索混合，向量优先、关键词补充
    ↓
面试官：向量从哪里来？Embedding 模型用什么？
    ↓
你：DeepSeek API 生成向量，后续可以本地部署 BGE 模型
    ↓
面试官：RAG 检索不到正确表怎么办？
    ↓
你：SQL 示例库兜底 + 通用示例 + 结果验证后自动修正
    ↓
面试官：DeepSeek 和 OpenAI 选型怎么考虑的？
    ↓
你：API 兼容 + 性价比高 + 中文能力强
```

---

## 📎 配套文件

- 自动生成题（32 题）：`interview-project-qa/text2sql-web-interview-questions.md`
- 本题深度分析：`interview-project-qa/mall-ai-deep-dive.md`
- 自动出题工具：`interview-note/interview-tools/question-generator/question_generator.py`

---

> 💡 建议：text2sql 项目涉及 RAG + LLM + 意图分析，是**AI 工程师面试的最佳切入点**，面试时重点准备 RAG 混合检索和 Prompt 构建策略。