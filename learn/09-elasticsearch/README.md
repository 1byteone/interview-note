# Elasticsearch — 搜索 · 分析 · 集群 · 数据同步

> 面向 Java 后端开发者的 Elasticsearch 实战教程，覆盖全文搜索、聚合分析、集群部署、数据同步等核心场景。
> 场景项目：AI 智能商城（mall-micro-cloud 商品搜索 + 日志分析 + 推荐系统）

---

## 学习路径图

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                      Elasticsearch 学习路径（双轨制）                         │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  01-basics 👶                                                               │
│  ┌──────────────────────────┐  ┌──────────────────────────────┐             │
│  │ ES 快速入门                │  │ DSL 搜索语法                  │             │
│  │ 安装 · 核心概念             │  │ 全文搜索 · 精确查询           │             │
│  │ CRUD · Mapping 映射       │  │ 复合查询 · 聚合分析           │             │
│  └────────────┬─────────────┘  └──────────────┬───────────────┘             │
│               │                                │                             │
│               ▼                                ▼                             │
│  02-core 👶→🎯                                                              │
│  ┌──────────────────────────┐  ┌──────────────────────────────┐             │
│  │ 索引与映射                  │  │ 分词与中文分析                │             │
│  │ 动态映射 · 显式映射         │  │ IK 分词 · 自定义词典         │             │
│  │ 字段类型 · 参数优化         │  │ 拼音分词 · 同义词            │             │
│  └────────────┬─────────────┘  └──────────────┬───────────────┘             │
│               │                                │                             │
│               ▼                                ▼                             │
│  03-advanced 🎯                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐                      │
│  │ 集群架构  │ │ 性能调优  │ │ 数据同步  │ │ ES 对比  │                      │
│  │ 节点角色  │ │ 分片策略  │ │ Logstash │ │ vs Solr  │                      │
│  │ 故障转移  │ │ 缓存配置  │ │ Canal    │ │ vs Meili │                      │
│  │ 水平扩展  │ │ 慢查询   │ │ 数据管道  │ │ AI 选型  │                      │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘                      │
│       │            │            │            │                              │
│       ▼            ▼            ▼            ▼                              │
│  04-projects 🎯                                                              │
│  ┌──────────────────────────┐  ┌──────────────────────────────┐             │
│  │ AI 商城集成               │  │ 迷你商品搜索系统              │             │
│  │ 商品搜索 · 日志分析        │  │ 索引设计 · 搜索 API         │             │
│  │ 推荐数据 · 搜索优化       │  │ 高亮 · 分页 · 排序           │             │
│  └──────────────────────────┘  └──────────────────────────────┘             │
│                                                                              │
│  05-interview 📝                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │ 速记 · 深挖 · 场景 · 代码                                               │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 前置知识

- **Java**：ES 客户端（RestHighLevelClient / Elasticsearch Java Client）基于 Java
- **REST API**：ES 所有操作均通过 RESTful API 暴露，需熟悉 HTTP 动词与 JSON 格式
- **JSON 数据结构**：文档以 JSON 格式存储，嵌套对象、数组是常见结构
- 了解 **MySQL** 的基本索引概念有助于理解 ES 的倒排索引

建议先完成本系列的 **Spring Boot** 教程（03-spring-boot）和 **MySQL** 教程（06-mysql），再开始学习 Elasticsearch。

---

## 面试高频考点一览表

| 考点 | 重要程度 | 频次 | 说明 | 章节 |
|------|----------|------|------|------|
| 倒排索引原理 | ⭐⭐⭐⭐⭐ | 高频 | 词项 → 文档的映射结构，与 B+Tree 对比 | 01-basics/01 |
| 全文搜索 DSL | ⭐⭐⭐⭐⭐ | 高频 | match / match_phrase / multi_match 区别 | 01-basics/02 |
| 复合查询 (bool) | ⭐⭐⭐⭐⭐ | 高频 | must / should / filter / must_not 组合 | 01-basics/02 |
| 聚合分析 | ⭐⭐⭐⭐ | 中频 | terms / avg / date_histogram / 嵌套聚合 | 01-basics/02 |
| Mapping 与字段类型 | ⭐⭐⭐⭐⭐ | 高频 | text vs keyword、dynamic mapping、显式映射 | 02-core/01 |
| 分词器与中文分词 | ⭐⭐⭐⭐ | 中频 | IK 分词、自定义词典、拼音分词 | 02-core/02 |
| 集群架构与节点角色 | ⭐⭐⭐⭐ | 中频 | master / data / coordinating / ingest 节点 | 03-advanced/01 |
| 分片与路由策略 | ⭐⭐⭐⭐ | 中频 | 分片数设置、routing 参数、不可变分片数 | 03-advanced/02 |
| 数据同步方案 | ⭐⭐⭐⭐ | 中频 | Logstash、Canal、Canal + MQ 方案对比 | 03-advanced/03 |
| 深度分页问题 | ⭐⭐⭐ | 中频 | from+size 性能、search_after、scroll | 03-advanced/02 |
| 写入性能优化 | ⭐⭐⭐ | 中频 | 批量写入、refresh_interval、translog | 03-advanced/02 |
| ES 选型对比 | ⭐⭐ | 低频 | ES vs Solr vs MeiliSearch | 03-advanced/04 |

---

## ES 在 AI 商城的角色

mall-micro-cloud 是一个基于微服务架构的 AI 商城系统，Elasticsearch 在其中承担**搜索与分析**核心角色：

| 场景 | 技术方案 | 说明 |
|------|----------|------|
| **商品搜索** | 全文搜索 + 聚合 | 用户输入关键词，ES 基于倒排索引快速返回匹配商品，支持分类/品牌/价格区间聚合 |
| **搜索推荐** | 搜索建议 + 高亮 | 基于 completion suggester 实现搜索联想，高亮展示匹配片段 |
| **日志分析** | 结构化日志 + 聚合 | 各微服务日志统一采集到 ES，通过 Kibana 实现可视化监控与排障 |
| **用户行为分析** | 时序聚合 | 分析用户点击、收藏、购买行为，为推荐系统提供数据支撑 |
| **商品评论搜索** | 中文分词 + 情感分析 | 用户评论写入 ES，支持按关键词检索评论，结合情感分析结果排序 |
| **库存/价格筛选** | 精确查询 + 范围过滤 | 结合 bool 查询实现多条件筛选（品牌/价格/库存/评分） |

---

## 目录导航

| 章节 | 内容 | 难度 |
|------|------|------|
| 01-basics/01-quick-start.md | 安装、核心概念、REST API 操作、商品索引最小案例 | 👶 |
| 01-basics/02-dsl-search.md | 全文搜索、精确查询、复合查询、聚合分析 | 👶 |
| 02-core/01-index-and-mapping.md | 动态映射、显式映射、字段类型、Mapping 参数最佳实践 | 👶→🎯 |
| 02-core/02-analysis-and-tokenizer.md | 分词原理、IK 分词、自定义词典、拼音/同义词 | 👶→🎯 |
| 03-advanced/01-cluster-architecture.md | 节点角色、集群部署、故障转移、水平扩展 | 🎯 |
| 03-advanced/02-performance-tuning.md | 分片策略、缓存配置、写入优化、深度分页、慢查询 | 🎯 |
| 03-advanced/03-data-sync.md | Logstash、Canal、Canal + MQ 数据同步方案 | 🎯 |
| 03-advanced/04-es-compare.md | ES vs Solr vs MeiliSearch，AI 场景选型建议 | 🎯 |
| 04-projects/mall-integration.md | AI 商城商品搜索、日志分析、推荐数据集成 | 🎯 |
| 04-projects/mini-blog/README.md | 迷你商品搜索系统小项目 | 🎯 |
| 05-interview/ | 面试四件套（速记/深挖/场景/代码） | 📝 |
| resources.md | 推荐资源 | - |

---

## 学习建议

- **初学者**：从 01-basics 开始，掌握 ES 核心概念和 DSL 搜索语法
- **有经验者**：直接进入 02-core 掌握 Mapping 和分词，再进入 03-advanced 学习集群与数据同步
- **面试冲刺**：优先掌握倒排索引、全文搜索 DSL、复合查询、聚合分析、Mapping 设计
- **动手实践**：每学完一个章节，在本地 Docker ES 中运行示例请求

---

## 项目剖析深度参考

本 learn 文档提供理论基础，以下 `docs/tech-stack-analysis/` 文档提供**真实项目中的落地代码**：

| 本 learn 核心内容 | 对应项目剖析 | 重点看什么 |
|------------------|------------|-----------|
| ES 商品搜索架构 | [08-ES-SEARCH.md](../../docs/tech-stack-analysis/mall-micro-cloud/08-ES-SEARCH.md) | mall-es-service 搜索接口 |
| 向量搜索 (RedisVL) | [06-VECTOR-STORE.md](../../docs/tech-stack-analysis/mall-ai-search/06-VECTOR-STORE.md) | HNSW 索引 + Embedding |
| ES vs 向量搜索对比 | [00-OVERVIEW.md](../../docs/tech-stack-analysis/mall-micro-cloud/00-OVERVIEW.md) | 关键词搜索 vs 语义搜索 |
| MySQL→ES 数据同步 | [09-DATA-SYNC.md](../../docs/tech-stack-analysis/mall-ai-search/09-DATA-SYNC.md) | SQLAlchemy + tiktoken + 向量化 |

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← RocketMQ](../08-rocketmq/README.md) | [📚 总目录](../README.md) | [Docker →](../10-docker/README.md) |

**相关技术栈：**
- [15-RAG](../15-rag/README.md) — RAG 系统常使用 ES 作为向量存储与 BM25 检索的后端
- [06-MySQL](../06-mysql/README.md) — ES 与 MySQL 通过 CDC 同步，构建搜索与存储分离架构