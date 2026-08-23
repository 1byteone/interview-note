# 主从复制与高可用 — binlog · 读写分离 · 集群方案

> 🎯 进阶路线 · 预计阅读时间：45 分钟

---

## 一、主从复制原理

### 1.1 复制架构

```
                   ┌──────────────┐
                   │   Master     │
                   │  (主库，写)   │
                   └──────┬───────┘
                          │ binlog
                          │
          ┌───────────────┼───────────────┐
          │               │               │
    ┌─────▼──────┐  ┌────▼─────┐  ┌─────▼──────┐
    │  Slave 1   │  │ Slave 2  │  │  Slave 3   │
    │ (从库，读)  │  │ (从库，读)│  │ (从库，读)  │
    └────────────┘  └──────────┘  └────────────┘
```

### 1.2 复制流程（三步走）

```
1. Master 事务提交 → 写入 binlog
2. Slave I/O 线程 → 拉取 binlog → 写入 relay log
3. Slave SQL 线程 → 回放 relay log → 写入 Slave 数据
```

**三个线程：**
| 线程 | 位置 | 职责 |
|------|------|------|
| Binlog dump 线程 | Master | 发送 binlog 给 Slave |
| I/O 线程 | Slave | 从 Master 拉取 binlog 写入 relay log |
| SQL 线程 | Slave | 读取 relay log 并执行 |

### 1.3 binlog 三种格式

| 格式 | 说明 | 优点 | 缺点 |
|------|------|------|------|
| STATEMENT | 记录 SQL 语句 | 日志量小 | 非确定性函数结果不同（如 NOW()） |
| ROW | 记录行变更（默认） | 最精确 | 日志量大 |
| MIXED | 混合模式 | 默认 STATEMENT，非确定时用 ROW | - |

```sql
-- 查看 binlog 格式
SHOW VARIABLES LIKE 'binlog_format';

-- 建议使用 ROW 格式
SET GLOBAL binlog_format = 'ROW';
```

### 1.4 复制模式

| 模式 | 一致性 | 延迟 | 适用场景 |
|------|--------|------|----------|
| 异步复制 | 可能丢失数据 | 低 | 大部分场景 |
| 半同步复制 | 至少一个从库确认 | 中 | 金融、支付 |
| 组复制（Group Replication） | 强一致 | 高 | 高一致性要求 |

**半同步复制流程：**

```
Master 提交事务
    ↓
发送 binlog 到 Slave
    ↓
等待至少一个 Slave 确认收到 (默认超时 10s)
    ↓
返回客户端成功
    ↓
Slave 异步回放
```

---

## 二、读写分离

### 2.1 为什么要读写分离

```sql
-- 读操作（高频）
SELECT * FROM order_info WHERE user_id = ?;  -- 走从库
SELECT * FROM product WHERE id = ?;          -- 走从库

-- 写操作（低频）
INSERT INTO order_info ...;                  -- 走主库
UPDATE product SET stock = ...;              -- 走主库
```

**收益：**
- 从库分担读压力，主库专注写
- 读能力水平扩展（增加从库）
- 主库故障时从库可提升为主库

### 2.2 实现方式

**方案 1：应用层配置（ShardingSphere）**

```yaml
spring:
  shardingsphere:
    datasource:
      names: master, slave1, slave2
      master:
        jdbc-url: jdbc:mysql://192.168.1.10:3306/mall
      slave1:
        jdbc-url: jdbc:mysql://192.168.1.11:3306/mall
      slave2:
        jdbc-url: jdbc:mysql://192.168.1.12:3306/mall
    rules:
      readwrite-splitting:
        data-sources:
          order_ds:
            write-data-source-name: master
            read-data-source-names: slave1, slave2
            load-balancer-algorithm-name: round_robin
        load-balancers:
          round_robin:
            type: ROUND_ROBIN
```

**方案 2：中间件（Proxy）**

```
应用 → Proxy（MySQL Router / ProxySQL / MyCat） → 主库 / 从库
```

### 2.3 主从延迟问题

**延迟原因：**
- 从库 SQL 线程单线程回放（MySQL 5.7 之前）
- 主库写入压力大
- 网络延迟

**延迟解决方案：**

| 方案 | 说明 |
|------|------|
| 强制读主 | 关键数据（支付结果）直接从主库读取 |
| 延迟检测 | 从库延迟超过阈值时切到主库 |
| 并行复制 | MySQL 5.7+ 支持多线程回放 |
| 缓存补偿 | 写主后写缓存，读从前先查缓存 |

```sql
-- 检查主从延迟（Seconds_Behind_Master）
SHOW SLAVE STATUS\G
-- 关注：Seconds_Behind_Master（秒级延迟）
-- 重点关注：Slave_IO_Running: Yes, Slave_SQL_Running: Yes
```

---

## 三、高可用方案

### 3.1 MHA（Master High Availability）

**架构：**
```
Manager 节点监控 Master
    └── 检测到 Master 故障
        ├── 选新 Master（数据最新的 Slave）
        ├── 补全缺失的 binlog
        ├── 提升为 Master
        └── 其他 Slave 连接新 Master
```

**优缺点：**
- 优点：成熟稳定，30 秒内完成切换
- 缺点：需要额外 Manager 节点，不保证不丢数据

### 3.2 Orchestrator

**特点：**
- Go 语言开发，轻量级
- Web 管理界面
- 支持自动故障恢复
- 支持拓扑可视化

```bash
# 部署 Orchestrator
docker run -d \
  --name orchestrator \
  -p 3000:3000 \
  -v /path/to/orchestrator.conf.json:/etc/orchestrator.conf.json \
  orchestrator/orchestrator
```

### 3.3 MySQL InnoDB Cluster

**组件：**
- **MySQL Shell**：管理集群
- **Group Replication**：组复制（Paxos 协议）
- **MySQL Router**：自动路由

**特点：**
- 官方方案，原生支持
- 自动故障切换
- 强一致性（Paxos）
- 读写分离内置

```bash
# 创建集群
mysqlsh --uri root@localhost:3306
> dba.createCluster('myCluster')
> cluster.addInstance('root@192.168.1.11:3306')
> cluster.addInstance('root@192.168.1.12:3306')
```

### 3.4 方案对比

| 方案 | 部署复杂度 | 切换时间 | 一致性 | 社区活跃度 |
|------|-----------|----------|--------|-----------|
| MHA | 中 | 10-30s | 最终一致 | 低（停止维护） |
| Orchestrator | 低 | 5-10s | 最终一致 | 高 |
| InnoDB Cluster | 中 | 1-5s | 强一致 | 高（官方） |
| 半同步复制 | 低 | 手动 | 最多丢一个 | 内置 |

---

## 四、Docker 搭建主从复制

### 4.1 启动主库

```bash
docker run -d \
  --name mysql-master \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -p 3307:3306 \
  mysql:8.0 \
  --server-id=1 \
  --log-bin=mysql-bin \
  --binlog-format=ROW
```

### 4.2 启动从库

```bash
docker run -d \
  --name mysql-slave \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -p 3308:3306 \
  mysql:8.0 \
  --server-id=2 \
  --log-bin=mysql-bin \
  --relay-log=relay-bin \
  --read-only=ON
```

### 4.3 配置主从

```sql
-- 主库：创建复制用户
CREATE USER 'repl'@'%' IDENTIFIED BY 'repl123';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
SHOW MASTER STATUS;  -- 记下 File 和 Position

-- 从库：配置连接
CHANGE MASTER TO
  MASTER_HOST='192.168.1.10',
  MASTER_PORT=3307,
  MASTER_USER='repl',
  MASTER_PASSWORD='repl123',
  MASTER_LOG_FILE='mysql-bin.000001',
  MASTER_LOG_POS=154;

START SLAVE;
SHOW SLAVE STATUS\G
-- 确认 Slave_IO_Running: Yes, Slave_SQL_Running: Yes
```

---

## 总结

| 知识点 | 一句话概括 |
|--------|-----------|
| 主从复制 | Master 写 binlog，Slave 拉取回放 |
| binlog 格式 | ROW 最精确，推荐使用 |
| 读写分离 | 主库写，从库读，ShardingSphere 或 Proxy 实现 |
| 主从延迟 | 关键数据读主库，并行复制缓解 |
| 高可用方案 | InnoDB Cluster 官方推荐，Orchestrator 轻量替代 |

> 下一步：[03-architecture-design.md](./03-architecture-design.md) — 大表设计与冷热分离