# 第二十四章：学习路径（P0 精通）

> 📖 **参考资料**：[Roadmap.sh](https://roadmap.sh/backend) | [Python 能力模型](https://www.python.org/dev/) | [系统设计面试](https://github.com/donnemartin/system-design-primer)

---

## 24.1 五阶段里程碑计划

```
  Phase 1          Phase 2          Phase 3          Phase 4          Phase 5
  ┌──────┐        ┌──────┐        ┌──────┐        ┌──────┐        ┌──────┐
  │ 基础 │──────▶ │ 框架 │──────▶ │ 进阶 │──────▶ │ 架构 │──────▶ │ 专家 │
  │ 3个月 │        │ 4个月 │        │ 4个月 │        │ 3个月 │        │ 持续 │
  └──────┘        └──────┘        └──────┘        └──────┘        └──────┘
   Python         FastAPI          消息队列         微服务           技术领导
   SQL            ORM              缓存             CQRS             架构决策
   Git            测试             CI/CD            DDD              性能调优
```

| 阶段 | 目标 | 核心技能 | 产出物 | 预计时长 |
|------|------|---------|--------|---------|
| **Phase 1：基础** | 能独立写 Python 后端 | Python 3.12+, SQL, Git, HTTP 基础, 命令行 | CRUD API 项目 | 3 个月 |
| **Phase 2：框架** | 能用 FastAPI 构建完整 API | FastAPI, Pydantic, SQLAlchemy, 认证授权, 测试 | 生产级 REST API | 4 个月 |
| **Phase 3：进阶** | 能处理复杂业务场景 | Redis, 消息队列, Celery, Docker, CI/CD, 日志监控 | 含异步任务的完整服务 | 4 个月 |
| **Phase 4：架构** | 能设计多服务系统 | 微服务, DDD, CQRS, K8s, API Gateway, 分布式事务 | 微服务电商系统 | 3 个月 |
| **Phase 5：专家** | 能领导技术方向 | 系统设计, 性能调优, 技术选型, 团队管理, ADR | 架构决策文档集 | 持续 |

---

## 24.2 技能矩阵：Junior → Staff

| 技能维度 | Junior (P4) | Mid (P3) | Senior (P2) | Staff (P1) |
|---------|-------------|----------|-------------|------------|
| **Python 基础** | 能写脚本 | 熟练使用 async/await | 理解 GIL、元类、描述符 | 能贡献 CPython |
| **Web 框架** | 能用 Flask/FastAPI 路由 | 能写中间件、依赖注入 | 能设计 RESTful 规范、gRPC | 能选型并论证框架决策 |
| **数据库** | 会写基础 SQL | ORM 调优、索引设计 | 读写分离、分库分表 | 数据架构设计、NewSQL 评估 |
| **缓存** | 会用 Redis GET/SET | 缓存策略、过期机制 | 缓存穿透/雪崩解决方案 | 多级缓存架构设计 |
| **消息队列** | 知道 MQ 概念 | 能用 Celery/RQ | 能用 Kafka/RabbitMQ | 事件驱动架构设计 |
| **测试** | 能写单元测试 | 集成测试 + CI 覆盖 | Contract Test + TDD | 测试策略与质量门禁设计 |
| **部署** | 能用 Docker | Docker Compose + CI | K8s 部署 + HPA | 多集群部署架构 |
| **安全** | 知道 HTTPS | OAuth2 + JWT | 威胁建模 + 安全审计 | 安全架构设计 |
| **监控** | 会看日志 | 结构化日志 + ELK | Prometheus + Grafana + 告警 | 可观测性体系设计 |
| **架构** | 按模板写代码 | 分层架构、设计模式 | 微服务 + DDD + CQRS | 企业架构、技术战略 |
| **协作** | 能完成分配的任务 | 能拆分任务、Review PR | 能设计系统、指导 Junior | 跨团队技术影响力 |

### 能力进阶关键指标

```text
Junior ───────────────────────────────────────────────────▶ Staff

  ▪ 能独立完成模块          ▪ 能独立负责系统
  ▪ 遇到问题会搜索         ▪ 遇到问题能定义问题
  ▪ 写"能跑"的代码          ▪ 写"可维护"的代码
  ▪ 用现成方案              ▪ 评估方案并做取舍
  ▪ 单兵作战                ▪ 带领团队交付
  ▪ 关注功能实现            ▪ 关注业务价值
```

---

## 24.3 核心学习链

```text
                    ┌─────────────────────┐
                    │    Python 基础      │
                    │  数据类型 / 控制流   │
                    │  函数 / 类 / 模块   │
                    └─────────┬───────────┘
                              │
                    ┌─────────▼───────────┐
                    │   异步编程基础       │
                    │  asyncio / 事件循环  │
                    │  async/await 模式    │
                    └─────────┬───────────┘
                              │
                    ┌─────────▼───────────┐
                    │   HTTP & REST       │
                    │  请求/响应模型       │
                    │  状态码 / Header     │
                    └─────────┬───────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
    ┌─────────▼──────┐ ┌─────▼──────┐ ┌──────▼────────┐
    │   数据库 & ORM  │ │   测试     │ │  身份认证      │
    │  SQL / 复杂查询 │ │  pytest    │ │  OAuth2/JWT   │
    │  SQLAlchemy     │ │  fixtures  │ │  RBAC         │
    └─────────┬──────┘ └─────┬──────┘ └──────┬────────┘
              │               │               │
              └───────────────┼───────────────┘
                              │
                    ┌─────────▼───────────┐
                    │   容器化 & CI/CD     │
                    │  Docker / Compose   │
                    │  GitHub Actions     │
                    └─────────┬───────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
    ┌─────────▼──────┐ ┌─────▼──────┐ ┌──────▼────────┐
    │   消息队列      │ │   缓存     │ │  日志 & 监控   │
    │  RabbitMQ/Kafka │ │  Redis     │ │  StructLog    │
    │  Celery        │ │  策略设计  │ │  Prometheus   │
    └─────────┬──────┘ └─────┬──────┘ └──────┬────────┘
              │               │               │
              └───────────────┼───────────────┘
                              │
                    ┌─────────▼───────────┐
                    │   架构设计          │
                    │  DDD / CQRS / ES   │
                    │  微服务 / K8s       │
                    └─────────────────────┘
```

**学习链依赖关系**：每个节点是下一个节点的前置条件，建议按箭头顺序推进，避免跳跃式学习。

---

## 24.4 实战项目建议

| 阶段 | 项目 | 技术栈 | 核心练习 | 预计耗时 |
|------|------|--------|---------|---------|
| **Phase 1** | 个人博客 API | FastAPI + SQLite + Jinja2 | CRUD、模板渲染、分页 | 2 周 |
| **Phase 2** | 待办事项 SaaS | FastAPI + PostgreSQL + Redis + JWT | 认证授权、缓存、数据库迁移 | 4 周 |
| **Phase 3** | 实时聊天系统 | FastAPI + WebSocket + Redis Pub/Sub + Celery | 实时通信、后台任务、文件上传 | 6 周 |
| **Phase 4** | 电商平台（微服务版） | FastAPI × 5 服务 + Kafka + K8s | 服务拆分、事件驱动、分布式事务 | 10 周 |
| **Phase 5** | 开源贡献 / 技术博客 | 因项目而异 | 代码评审、文档、社区协作 | 持续 |

### 项目评估维度

```
每个项目完成后，用以下维度自评（1-5 分）：

┌──────────────────┬──────┬───────────────────────────────┐
│ 维度             │ 评分  │ 说明                          │
├──────────────────┼──────┼───────────────────────────────┤
│ 代码质量         │ __   │ 类型注解、命名、注释、文档     │
│ 测试覆盖         │ __   │ 单元测试、集成测试、覆盖率     │
│ API 设计         │ __   │ RESTful 规范、版本管理、错误码 │
│ 错误处理         │ __   │ 全局异常、日志记录、用户友好   │
│ 性能             │ __   │ 响应时间、吞吐量、资源占用     │
│ 部署就绪         │ __   │ Docker、CI/CD、环境配置       │
│ 安全             │ __   │ 输入校验、认证、敏感信息处理   │
│ 可维护性         │ __   │ 模块化、可读性、扩展性         │
└──────────────────┴──────┴───────────────────────────────┘
```

### 推荐学习资源清单

| 类别 | 资源 | 适合阶段 | 说明 |
|------|------|---------|------|
| 书籍 | *Python Crash Course* | Phase 1 | Python 入门经典 |
| 书籍 | *Architecture Patterns with Python* | Phase 3-4 | DDD + 事件驱动 |
| 书籍 | *Designing Data-Intensive Applications* | Phase 4-5 | 分布式系统圣经 |
| 书籍 | *System Design Interview* | Phase 4-5 | 系统设计面试实战 |
| 课程 | FastAPI 官方教程 | Phase 2 | 最权威的 FastAPI 教程 |
| 课程 | CS50 Web (Harvard) | Phase 1-2 | Web 开发基础 |
| 练习 | Advent of Code | Phase 1-2 | 算法思维训练 |
| 练习 | Build your own X | Phase 3-4 | 造轮子学原理 |
| 社区 | r/FastAPI | 全阶段 | Reddit FastAPI 社区 |
| 社区 | Python Discord | 全阶段 | 活跃的 Python 社区 |

---

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| Backend Roadmap | https://roadmap.sh/backend | 后端开发者路线图 |
| System Design Primer | https://github.com/donnemartin/system-design-primer | 系统设计入门 |
| Architecture Patterns with Python | https://www.cosmicpython.com/ | Python 架构模式（免费在线） |
| The Pragmatic Programmer | https://pragprog.com/titles/tpp20/ | 程序员修炼之道 |
| 12-Factor App | https://12factor.net/ | SaaS 应用设计方法论 |
| Staff Engineer Path | https://www.oreilly.com/library/view/the-staff-engineers/9781492085980/ | Staff 工程师成长指南 |
