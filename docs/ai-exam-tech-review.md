# AI_EXAM 教材技术审核清单

> 该清单用于教程改写前的事实核验。未完成核验的内容不得写成无条件成立的稳定 API 或性能结论。

## 必须核验

| 主题 | 核验内容 | 发布规则 |
|---|---|---|
| LangChain | `create_agent` 包路径、签名、版本 | 绑定依赖版本；未确认则用伪代码 |
| LangGraph | StateGraph 入口、结束节点、检查点 API | 说明版本和恢复语义 |
| Memory | `InMemorySaver`/SQLite/Redis/PostgreSQL 边界 | 不把内存保存器描述为生产持久化 |
| Agent Middleware | `before_model` 等钩子 | 仅引用官方 API |
| RAG 分数 | 向量、BM25、RRF、Reranker 分数含义 | 异构分数不直接比较 |
| 分块参数 | 512/64、1000/200 等参数来源 | 标记教学示例或历史配置 |
| RedisStack | RediSearch/RedisJSON/向量索引版本 | 绑定部署方式 |
| Neo4j | APOC、初始化脚本、Cypher 方向和扫描代价 | 禁止明文密码和错误自动初始化说法 |
| MCP | Host/Client/Server、Tools/Resources/Prompts、SDK/传输 | 以官方协议文档为准 |
| FastAPI/SSE | 事件格式、POST fetch 重连、代理缓冲 | 明确断线和取消语义 |
| Ollama/Harness | 官方仓库、CLI、插件和沙箱边界 | 未实际验证不写成可运行命令 |
| MaaS | 模型名、配额、价格和 API | 不写固定免费额度，注明时效性 |

## 现有文档高优先修订项

- Docker 多阶段构建示例补充 `AS builder`。
- Elasticsearch 深分页补充 PIT，并标注 scroll 的版本状态。
- MySQL 补充 RC/RR 下 ReadView 生成时机。
- JVM 偏向锁默认状态按 JDK 版本改正。
- Spring Cloud Sleuth 迁移到 Micrometer Tracing 的版本说明。
- OpenFeign 统一 Ribbon 与 Spring Cloud LoadBalancer 的历史边界。
- mall 项目统一 RabbitMQ/RocketMQ、服务数量和秒杀层数口径。
- CropWise 区分 384/1024 Embedding、0.32/0.6 阈值及运行模式。
- Agent 与 Agentic AI 题库改为主线/进阶交叉引用，避免重复维护。

## 禁止直接发布的内容

- 无来源的延迟、吞吐、准确率和“生产就绪度”数字；
- 明文 API Key、数据库密码、内网地址和个人绝对路径；
- 将模型生成内容等同于业务事实；
- 将 `version: 3.8` Compose 配置与“字段已废弃”混写而不说明版本；
- 将 Gateway 鉴权、Sentinel 限流和 Redis 限流混为同一机制。
