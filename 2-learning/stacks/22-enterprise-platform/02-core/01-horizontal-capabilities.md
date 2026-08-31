# 企业平台横向技术能力 — 权限 · 缓存 · 消息 · 监控

> 本篇汇总三域共享的横向技术能力，这些是简历中"技术深度"的体现。

---

## 一、统一权限体系

### RBAC + 数据权限

```
三域统一权限模型：
┌──────────────────────────────────────────────────┐
│  用户(User) ──→ 角色(Role) ──→ 权限(Permission)   │
│                  │                                │
│                  ├── OA 角色：审批员、HR、行政       │
│                  ├── ERP 角色：销售、采购、财务      │
│                  └── MES 角色：操作员、质检员、设备员 │
│                                                    │
│  数据权限范围：                                      │
│  · ALL：管理员看所有域数据                            │
│  · DEPT：部门经理看本部门数据（跨域）                  │
│  · SELF：普通员工只看自己数据                         │
└──────────────────────────────────────────────────┘
```

### 关键实现

| 能力 | 实现方案 | 说明 |
|------|---------|------|
| 菜单权限 | 前端路由守卫 | 动态路由 + 按钮权限标识 |
| 接口权限 | Spring Security + 自定义注解 | `@RequiresPermission("erp:so:create")` |
| 数据权限 | MyBatis 拦截器 | 自动拼接 WHERE 条件 |
| 缓存 | Redis + Caffeine | 登录时加载，变更时清除 |

---

## 二、统一缓存策略

### 缓存分层

| 数据类型 | 缓存策略 | TTL | 说明 |
|---------|---------|-----|------|
| 权限数据 | Caffeine + Redis 二级 | 30 分钟 | 读多写少 |
| 库存数量 | Redis + 数据库 | 5 秒 | 实时性要求高 |
| 工单状态 | Redis | 5 分钟 | 高频查询 |
| 审批任务列表 | Redis | 10 秒 | 变更频繁 |
| BOM 数据 | Caffeine | 1 小时 | 变更少 |

### 分布式锁场景

| 场景 | 锁粒度 | 说明 |
|------|--------|------|
| 报工防重复 | `mes:report:lock:{jobCardId}` | 防并发报工 |
| 库存扣减 | `erp:stock:lock:{materialId}` | 防超卖 |
| 审批提交 | `oa:approve:lock:{taskId}` | 防重复审批 |

---

## 三、统一体息架构

### 消息场景

| Topic | 生产者 | 消费者 | 可靠性 |
|-------|--------|--------|--------|
| `erp.po.created` | ERP | MES（领料） | Outbox |
| `mes.work_order.completed` | MES | ERP（入库+成本） | Outbox |
| `oa.purchase.approved` | OA | ERP（创建 PO） | Outbox |
| `oa.leave.approved` | OA | HR（更新考勤） | Outbox |
| `mes.quality.abnormal` | MES | OA（异常处置审批） | Outbox |

### 统一消息规范

```json
{
  "messageId": "MSG-{日期}-{流水号}",
  "messageType": "域.实体.事件",
  "source": "OA/ERP/MES",
  "timestamp": "ISO8601",
  "payload": { }
}
```

---

## 四、统一监控

| 监控维度 | 工具 | 指标 |
|---------|------|------|
| 系统健康 | Prometheus + Grafana | CPU/内存/JVM/GC |
| 业务指标 | Grafana 大屏 | 工单数/库存周转率/OEE |
| 消息积压 | RocketMQ 控制台 | 各 Topic 积压数 |
| 错误告警 | AlertManager | 错误率>1% 告警 |
| 链路追踪 | SkyWalking | 跨域调用链路 |

---

## 五、简历横向能力话术

```
技术亮点：
· 实现 RBAC + 数据权限三层控制（菜单/按钮/数据），MyBatis 拦截器自动拼接条件
· 设计 Redis + Caffeine 二级缓存，权限查询 P99 < 50ms
· 采用 Outbox + RocketMQ 实现三域事件驱动集成，消息投递成功率 99.97%
· Prometheus + Grafana 监控三域核心指标，Grafana 数字大屏实时展示
```

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← README](../README.md) | [📚 22-企业平台](../README.md) | [架构蓝图 →](../04-project/01-architecture-blueprint.md) |
