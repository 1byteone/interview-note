# 推荐资源 — MySQL 学习资料

---

## 官方文档

| 资源 | 链接 | 说明 |
|------|------|------|
| MySQL 8.0 Reference Manual | https://dev.mysql.com/doc/refman/8.0/en/ | 官方手册，最权威的参考资料 |
| MySQL 8.0 What Is New | https://dev.mysql.com/doc/refman/8.0/en/mysql-nutshell.html | 8.0 新特性 |
| InnoDB 锁机制 | https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html | 官方锁说明 |
| InnoDB 事务 | https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html | 官方事务隔离级别说明 |

---

## 书籍推荐

| 书名 | 作者 | 适合人群 | 评分 |
|------|------|----------|------|
| 《高性能 MySQL（第4版）》 | Baron Schwartz 等 | 进阶 | ★★★★★ |
| 《MySQL 实战 45 讲》 | 林晓斌（丁奇） | 实战 | ★★★★★ |
| 《MySQL 技术内幕：InnoDB 存储引擎（第2版）》 | 姜承尧 | 进阶 | ★★★★★ |
| 《SQL 必知必会（第5版）》 | Ben Forta | 入门 | ★★★★☆ |
| 《数据库系统概念》 | Abraham Silberschatz | 理论 | ★★★★☆ |

---

## 在线课程

| 课程 | 平台 | 说明 |
|------|------|------|
| MySQL 实战 45 讲 | 极客时间 | 丁奇主讲，深入浅出 |
| SQL 优化 | 极客时间 | 实战案例驱动 |
| MySQL 必知必会 | 慕课网 | 入门好选择 |
| CMU 15-445 Database Systems | YouTube | 数据库系统经典课程，CMU 教授主讲 |

---

## 工具推荐

| 工具 | 用途 | 链接 |
|------|------|------|
| DBeaver | 通用数据库管理工具 | https://dbeaver.io/ |
| DataGrip | JetBrains 数据库 IDE | https://www.jetbrains.com/datagrip/ |
| MySQL Workbench | MySQL 官方管理工具 | https://www.mysql.com/products/workbench/ |
| pt-query-digest | Percona Toolkit 慢查询分析 | https://www.percona.com/software/database-tools/percona-toolkit |
| ShardingSphere | 分库分表中间件 | https://shardingsphere.apache.org/ |
| ProxySQL | MySQL 中间件代理 | https://proxysql.com/ |
| sysbench | 压测工具 | https://github.com/akopytov/sysbench |
| mysqldumpslow | 慢查询日志分析 | MySQL 自带 |

---

## 博客与社区

| 资源 | 说明 |
|------|------|
| MySQL 官方博客 | https://dev.mysql.com/blog-archive/ |
| Percona 数据库博客 | https://www.percona.com/blog/ |
| 阿里云数据库团队 | 阿里云数据库内核团队的技术分享 |
| CSDN MySQL 社区 | 中文技术文章 |

---

## 练习平台

| 平台 | 说明 | 链接 |
|------|------|------|
| LeetCode 数据库题 | SQL 练习题，面试必备 | https://leetcode.com/problemset/database/ |
| SQLZoo | 交互式 SQL 学习 | https://sqlzoo.net/ |
| HackerRank SQL | 在线编程挑战 | https://www.hackerrank.com/domains/sql |
| MySQL 官方文档测试 | 在自己的 Docker 环境中练习 | 建议本地搭建 |

---

## 学习建议

1. **官方文档优先**：遇到问题先查官方文档，大部分答案都在那里
2. **动手实践**：在本地 Docker 中搭建 MySQL，每个知识点都跑一遍 SQL
3. **从原理出发**：理解 B+Tree、MVCC 等底层原理，而不是死记硬背面试题
4. **关注版本**：MySQL 8.0 有很多新特性（窗口函数、CTE、不可见索引等），面试会问
5. **持续学习**：数据库技术更新快，关注社区动态

---

> 回到顶部：[README.md](./README.md) — MySQL 技术栈总览