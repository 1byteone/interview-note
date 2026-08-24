# CLAUDE.md - 项目面试准备

## 项目源码路径

| 项目 | 本地路径 | GitHub |
|------|---------|--------|
| 分布式微云商城 | `D:/code/codeClaudeCode/demo-practicalTrainingProject/mall-ai/mall-micro-cloud` | [mall-micro-cloud](https://github.com/1byteone/mall-micro-cloud) |
| 灵犀智能写作 | `D:/code/codeJava/codeYuJavaAi/ai-passage-creator` | [ai-passage-creator](https://github.com/1byteone/ai-passage-creator) |
| 农业知识库问答智能体 | `D:/code/codeByCursor/AI_EXAM/agri-qa-assistant` | [agri-qa-assistant](https://github.com/1byteone/agri-qa-assistant) |
| 智颐养老护理系统 | `D:/code/codeJava/heima-phase4/zznursing` | [zznursing](https://github.com/1byteone/zznursing) |
| 传习教育实习 | `D:/code/实习/传习教育` | [ai-search-rag-internship](https://github.com/1byteone/ai-search-rag-internship) |

## 项目技术栈速查

### 1. mall-micro-cloud
- **框架**: Spring Boot 3 + Spring Cloud Alibaba + MyBatis-Plus
- **微服务模块**: 12 个（seckill/cart/order/pay/product/es/aisearch/user/consumer/scheduler/oss/test/common）
- **核心组件**: Nacos（注册+配置）、Gateway、Sentinel、OpenFeign、Redis/Redisson、RocketMQ、Elasticsearch、Elastic-Job、Seata
- **关键实现类**:
  - 秒杀库存扣减: `ProductServiceImpl.decreaseStock()` — 布隆过滤器→Redisson 锁→Redis 原子扣减→MQ 异步落库
  - 库存恢复: `ProductServiceImpl.restoreStock()` — 分布式锁→Redis 原子自增→MQ 消息
  - MQ 消费: `StockDeductConsumer` — 幂等校验（Redis SETNX 30s）→写流水→同步库存
  - AI 搜索: `AiSearchSeriveImpl` — OpenFeign 调用 Python AI 服务（recommend/extract）
  - ES 搜索: `ProductServiceImpl`/`SkuInfoServiceImpl` — ES DSL 查询 + IK 分词

### 2. agri-qa-assistant (CropWise)
- **框架**: FastAPI + LangGraph Agent + Next.js 14
- **向量库**: ChromaDB（BGE-M3 Embedding 1024d）
- **知识图谱**: Neo4j（12 类实体 + 16 类关系）
- **检索**: Hybrid RAG = Vector + BM25 + RRF 融合 + BGE-Reranker 重排序
- **关键实现**:
  - Agent: `agent.py` — AgricultureAgent 类，LangChain ChatOpenAI + 工具调度
  - 文档摄入: `document_ingestion.py` — PDF/DOCX/HTML 解析 + 文本清洗
  - 领域守卫: `domain_guard.py` — 农业术语表 + 非农业问题拒绝
  - 检索管道: `agriir_pipeline.py` — 多路召回 + RRF 融合

### 3. ai-passage-creator (灵犀智能写作)
- **框架**: Spring Boot 3.5 + Java 21 + Spring AI Alibaba (StateGraph)
- **Agent 编排**: StateGraph（START→Title→Outline→Content→Image→Merge→END）
- **异步体系**: 4 个线程池（article/skill/card/rag）+ SSE 流式输出
- **关键实现**:
  - 编排器: `ArticleAgentOrchestrator` — StateGraph 构建 + 阶段执行
  - Agent 列表: TitleGenerator/OutlineGenerator/ContentGenerator/ImageAnalyzer/ContentMerger
  - 异步配置: `AsyncConfig` — article(5-10)/skill(5-15)/card(2-4)/rag(2-4)
  - 并行图片: `ParallelImageGenerator`
  - 速率限制: `GuestRateLimiter`

### 4. zznursing (智颐养老)
- **框架**: Spring Boot 2.5 + MyBatis-Plus + Spring Security JWT
- **多模块**: 10 个模块（nursing-platform/admin/common/framework/system/oss/quartz/generator/miniapp/ui）
- **外部集成**: 百度千帆 AI、华为云 IoTDA、阿里云 OSS、微信小程序、Apache Qpid JMS
- **关键实现**:
  - 健康评估: `HealthAssessmentServiceImpl` — PDF→Redis 缓存→千帆 AI→结构化输出
  - IoT 设备: `DeviceServiceImpl` — 华为云产品同步→Redis→AMQP 消息消费
  - 设备数据: `DeviceDataServiceImpl` — 批量入库 + Redis Hash 缓存最新数据
  - 小程序鉴权: 拦截器 + ThreadLocal + JWT
