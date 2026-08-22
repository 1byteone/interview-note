# MySQL — 索引 · 事务 · 优化 · 分库分表

> 双轨学习路径：👶 初学者路线 · 🎯 面试/实战进阶路线
> 关联项目：mall-micro-cloud（AI 商城核心业务数据存储）

---

## 学习路径图

```
┌──────────────────────────────────────────────────────────────────┐
│                    MySQL 学习路径（双轨制）                       │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  👶 初学者路线（1-2周）                                           │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────────┐       │
│  │ 基础 SQL  │───→│ 表设计 & 范式 │───→│ JOIN & 子查询    │       │
│  └──────────┘    └──────────────┘    └────────┬─────────┘       │
│                                               │                 │
│  🎯 进阶路线（2-4周）                          │                 │
│  ┌───────────────────────────────────────────────┘               │
│  │                                                               │
│  ├──→ 索引原理 (B+Tree) ──→ 事务 & MVCC ──→ SQL 优化            │
│  ├──→ 分库分表 ──→ 主从复制 ──→ 高可用架构                      │
│  └──→ 面试突击 (速记 + 深挖 + 场景 + 编码)                       │
│                                                                  │
│  实战项目：mall-micro-cloud 订单系统 + 博客系统数据库设计        │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 前置知识

- **SQL 基础**：SELECT / INSERT / UPDATE / DELETE 基本语法
- **数据库概念**：表、字段、主键、外键
- **基本数据类型**：INT、VARCHAR、DATETIME、DECIMAL
- 推荐先完成 [01-backend-development](../01-backend-development/README.md) 基础篇

---

## 面试高频考点一览表

| 考点 | 重要程度 | 学习章节 | 常见问法 |
|------|----------|----------|----------|
| B+Tree 索引结构 | ★★★★★ | 02-core/01-index-principles | "B+Tree 和 B-Tree 区别" |
| 最左前缀原则 | ★★★★★ | 02-core/01-index-principles | "联合索引 (a,b,c) 哪些查询走索引" |
| 索引失效场景 | ★★★★★ | 02-core/01-index-principles | "哪些情况会导致索引失效" |
| Explain 执行计划 | ★★★★☆ | 02-core/01-index-principles | "type 字段各值的含义" |
| 事务 ACID 特性 | ★★★★★ | 02-core/02-transaction-and-mvcc | "事务四大特性分别靠什么保证" |
| MVCC 原理 | ★★★★★ | 02-core/02-transaction-and-mvcc | "MVCC 如何实现可重复读" |
| 隔离级别 | ★★★★★ | 02-core/02-transaction-and-mvcc | "MySQL 默认隔离级别及解决幻读" |
| 锁机制 | ★★★★★ | 02-core/02-transaction-and-mvcc | "间隙锁加锁规则" |
| 慢查询优化 | ★★★★☆ | 02-core/03-sql-optimization | "一条 SQL 从 5s 优化到 50ms" |
| 分库分表 | ★★★★☆ | 03-advanced/01-sharding | "分片键如何选择" |
| 主从复制 | ★★★★☆ | 03-advanced/02-master-slave | "binlog 三种格式区别" |
| 大表设计 | ★★★☆☆ | 03-advanced/03-architecture-design | "亿级数据表如何设计" |

---

## MySQL 在 AI 商城的角色

mall-micro-cloud 是一个基于微服务架构的 AI 商城系统，MySQL 在其中承担**核心业务数据存储**角色：

| 业务域 | 核心表 | 数据量级 | 关键诉求 |
|--------|--------|----------|----------|
| 用户中心 | user | 百万级 | 高并发写入、唯一索引 |
| 商品中心 | product, sku, category | 十万级 | 多维度搜索、分类查询 |
| 订单中心 | order_info, order_item | 亿级 | 分库分表、事务一致性 |
| 支付中心 | payment, refund | 千万级 | 事务强一致性、对账 |
| 购物车 | cart | 百万级 | 实时读写、高并发 |
| 库存中心 | inventory | 十万级 | 库存扣减、乐观锁 |

---

## 目录结构

```
06-mysql/
├── README.md                          # 本文件：技术栈总览
├── 01-basics/                         # 👶 基础篇
│   ├── 01-quick-start.md              # 安装配置 + 基本 SQL + 表设计
│   └── 02-advanced-sql.md             # JOIN + 子查询 + 窗口函数
├── 02-core/                           # 👶→🎯 核心原理
│   ├── 01-index-principles.md         # 索引数据结构 + Explain + 最佳实践
│   ├── 02-transaction-and-mvcc.md     # 事务 + MVCC + 锁机制
│   └── 03-sql-optimization.md         # SQL 优化 + 慢查询 + 实战
├── 03-advanced/                       # 🎯 进阶架构
│   ├── 01-sharding.md                 # 分库分表 + ShardingSphere
│   ├── 02-master-slave.md             # 主从复制 + 读写分离 + 高可用
│   └── 03-architecture-design.md      # 大表设计 + 冷热分离 + 归档
├── 04-projects/                       # 实战项目
│   ├── mall-integration.md            # AI 商城 MySQL 应用
│   └── mini-blog/README.md            # 博客系统数据库设计
├── 05-interview/                      # 面试突击
│   ├── quick-revision.md              # 速记版：各模块 10 个考点
│   ├── deep-dive.md                   # 深挖题：MVCC 源码级、B+Tree 层数
│   ├── scenario.md                    # 场景题：慢查询排查、死锁排查
│   └── coding.md                      # 代码题：SQL 优化、表设计、Explain
└── resources.md                       # 推荐资源
```

---

## 学习建议

- **初学者**：从 01-basics 开始，掌握 SQL 基础后进入 02-core 核心原理
- **有经验者**：直接进入 02-core 和 03-advanced，结合 04-projects 实战
- **面试冲刺**：优先学习 02-core 核心章节，配合 05-interview 刷题
- **动手实践**：每学完一个章节，在本地 Docker MySQL 中运行示例 SQL

---

> 下一篇：[07-redis](../07-redis/README.md) — 缓存 · 分布式锁 · 数据结构

---

## 项目剖析深度参考

本 learn 文档提供理论基础，以下 `docs/tech-stack-analysis/` 文档提供**真实项目中的落地代码**：

| 本 learn 核心内容 | 对应项目剖析 | 重点看什么 |
|------------------|------------|-----------|
| SPU/SKU 表设计 | [03-PRODUCT-MYBATISPLUS.md](../../docs/tech-stack-analysis/mall-micro-cloud/03-PRODUCT-MYBATISPLUS.md) | 分类体系 + MyBatis-Plus 多表关联 |
| 分布式事务 | [04-ORDER-SEATA.md](../../docs/tech-stack-analysis/mall-micro-cloud/04-ORDER-SEATA.md) | Seata AT 下单+扣库存事务 |
| 缓存穿透/击穿/雪崩 | [04-REDIS-CACHE.md](../../docs/tech-stack-analysis/mall-exercise/04-REDIS-CACHE.md) | Cache-Aside + 分布式锁 + 空值缓存 |
| MySQL→向量化同步 | [09-DATA-SYNC.md](../../docs/tech-stack-analysis/mall-ai-search/09-DATA-SYNC.md) | SQLAlchemy lazy_load + tiktoken 切片 |