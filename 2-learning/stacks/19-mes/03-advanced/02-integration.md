# MES 与 ERP 集成 — API · MQ · 事件驱动

> 本篇聚焦 MES 与 ERP 的系统集成设计：接口标准化、消息可靠性、数据一致性。
> 配套 Mermaid 源文件：[`_assets/diagrams/mes/mes-erp-sequence.mmd`](../../../_assets/diagrams/mes/mes-erp-sequence.mmd)

---

## 一、MES↔ERP 集成全景

```
┌─────────────────────────────────────────────────────────────────────┐
│                    MES ↔ ERP 集成架构                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────┐                           ┌─────────┐                │
│  │   ERP   │                           │   MES   │                │
│  │         │   ┌───────────────────┐   │         │                │
│  │ 生产订单 │──→│   API Gateway    │←──│ 工单状态 │                │
│  │ BOM     │   │  路由 + 限流 + JWT │   │ 报工数据 │                │
│  │ 库存    │←──│                   │──→│ 质检结果 │                │
│  │ 财务    │   └───────────────────┘   │ 追溯数据 │                │
│  │         │            │              │         │                │
│  │         │   ┌────────┴────────┐    │         │                │
│  │         │   │    RocketMQ     │    │         │                │
│  │         │──→│  Topic: erp.*   │←───│         │                │
│  │         │←──│  Topic: mes.*   │────│         │                │
│  │         │   └─────────────────┘    │         │                │
│  └─────────┘                           └─────────┘                │
│                                                                     │
│  集成模式：                                                          │
│  1. 实时同步：REST API（工单创建、状态查询）                            │
│  2. 异步事件：MQ（完工回报、库存同步、成本归集）                         │
│  3. 批量同步：定时任务（日报、OEE 统计、成本核算）                       │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、集成接口清单

### 2.1 ERP → MES（下行接口）

| 接口 | 触发方式 | 数据 | 频率 |
|------|---------|------|------|
| 生产订单创建 | ERP 保存订单后推送 | 工单号、BOM、数量、计划日期 | 实时 |
| BOM 变更通知 | ERP 发布新 BOM 后推送 | BOM 编码、版本、子件清单 | 实时 |
| 物料主数据同步 | ERP 修改物料后推送 | 物料编码、名称、规格、单位 | 实时 |
| 原料预留通知 | ERP MRP 计算后推送 | 物料编码、预留数量、仓库 | 实时 |
| 库存同步 | ERP 出入库后推送 | 物料编码、仓库、数量、批次 | 异步 |

### 2.2 MES → ERP（上行接口）

| 接口 | 触发方式 | 数据 | 频率 |
|------|---------|------|------|
| 工单状态回报 | 工单状态变更时 | 工单号、状态、实际数量 | 实时 |
| 完工入库回报 | FQC 通过后 | 成品 SN/批次、数量、仓库 | 实时 |
| 领料消耗回报 | 工单领料时 | 原料批次、消耗数量 | 实时 |
| 质检结果回报 | 质检完成时 | 质检单号、结果、不良数 | 实时 |
| 设备 OEE 日报 | 每日定时 | 设备编码、OEE、停机时长 | 批量 |
| 工单成本数据 | 工单关闭时 | 工单号、材料成本、人工成本 | 实时 |

---

## 三、接口标准化设计

### 3.1 统一消息格式

```json
{
  "messageId": "MSG-20260831-000001",
  "messageType": "work_order.completed",
  "source": "MES",
  "target": "ERP",
  "timestamp": "2026-08-31T14:30:00+08:00",
  "version": "1.0",
  "payload": {
    "workOrderNo": "WO-20260831-001",
    "erpOrderNo": "PO-20260828-005",
    "status": "COMPLETED",
    "actualQty": 998,
    "scrapQty": 2,
    "actualStart": "2026-08-31T08:00:00+08:00",
    "actualEnd": "2026-08-31T14:30:00+08:00",
    "costBreakdown": {
      "materialCost": 12500.00,
      "laborCost": 3200.00,
      "overheadCost": 800.00
    }
  }
}
```

### 3.2 接口幂等设计

所有写操作接口必须实现幂等性：

```java
// MES 完工回报接口
@PostMapping("/api/mes/v1/work-order/complete")
public Result completeWorkOrder(@RequestBody WorkOrderCompleteDTO dto) {
    // 1. 幂等检查：messageId 唯一
    if (messageLogService.exists(dto.getMessageId())) {
        return Result.success("已处理，跳过重复消息");
    }

    // 2. 业务处理
    WorkOrder wo = workOrderService.complete(
        dto.getWorkOrderNo(),
        dto.getActualQty(),
        dto.getScrapQty()
    );

    // 3. 记录消息日志
    messageLogService.log(dto.getMessageId(), "COMPLETED");

    // 4. 发送下游事件
    eventPublisher.publish("mes.work_order.completed", wo);

    return Result.success(wo);
}
```

### 3.3 ERP 适配层

MES 不应直接依赖特定 ERP 的 API 格式，应通过适配层解耦：

```
┌─────────────────────────────────────────────────────┐
│                ERP 适配层（Adapter）                  │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐      │
│  │ ERPNext   │  │ SAP       │  │ 用友/金蝶  │      │
│  │ Adapter   │  │ Adapter   │  │ Adapter   │      │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘      │
│        │              │              │              │
│        └──────────────┼──────────────┘              │
│                       │                             │
│              ┌────────┴────────┐                    │
│              │  统一接口契约     │                    │
│              │  ERPDataAdapter │                    │
│              └─────────────────┘                    │
│                                                     │
└─────────────────────────────────────────────────────┘

interface ERPDataAdapter {
    void createProductionOrder(ProductionOrderDTO dto);
    void syncInventory(InventorySyncDTO dto);
    void reportWorkOrderCompletion(WorkOrderCompleteDTO dto);
    void reportCost(CostReportDTO dto);
}
```

---

## 四、消息可靠性设计

### 4.1 Outbox 模式

保证"写库 + 发消息"的原子性：

```
┌─────────────────────────────────────────────────────┐
│                Outbox 模式流程                       │
├─────────────────────────────────────────────────────┤
│                                                     │
│  1. MES 完工                                         │
│     ├── 更新 mes_work_order 状态 = COMPLETED        │
│     └── 插入 mes_outbox（message, status=PENDING）   │
│     （同一个数据库事务）                               │
│                                                     │
│  2. 后台线程（每 5 秒）                               │
│     ├── SELECT * FROM mes_outbox                    │
│     │   WHERE status = 'PENDING'                    │
│     │   ORDER BY create_time                        │
│     │   LIMIT 100                                   │
│     └── 逐条投递到 RocketMQ                          │
│                                                     │
│  3. 投递成功                                         │
│     └── UPDATE mes_outbox SET status = 'SENT'       │
│                                                     │
│  4. ERP 消费确认                                     │
│     └── UPDATE mes_outbox SET status = 'ACKED'      │
│                                                     │
│  5. 失败重试                                         │
│     ├── 重试 3 次                                    │
│     ├── 超过 3 次 → status = 'FAILED'               │
│     └── 告警通知运维处理                              │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 4.2 消息幂等

ERP 消费端必须实现幂等：

```java
// ERP 消费 MES 完工事件
@RocketMQMessageListener(topic = "mes.work_order.completed")
public class WorkOrderCompletedListener implements RocketMQListener<MessageExt> {
    @Override
    public void onMessage(MessageExt msg) {
        String messageId = msg.getMsgId();
        String body = new String(msg.getBody());

        // 1. 幂等检查
        if (messageLogService.exists(messageId)) {
            log.info("消息已处理，跳过: {}", messageId);
            return;
        }

        // 2. 解析消息
        WorkOrderCompleteDTO dto = JSON.parseObject(body, WorkOrderCompleteDTO.class);

        // 3. 业务处理：更新 ERP 生产订单状态 + 入库 + 成本归集
        erpProductionOrderService.complete(dto);

        // 4. 记录消息日志
        messageLogService.log(messageId, "PROCESSED");
    }
}
```

### 4.3 重试与死信队列

```
投递失败策略：
├── 第 1 次重试：延迟 5 秒
├── 第 2 次重试：延迟 30 秒
├── 第 3 次重试：延迟 5 分钟
├── 超过 3 次：进入死信队列（DLQ）
└── 死信队列：人工介入处理

监控指标：
├── 消息投递成功率（目标 > 99.9%）
├── 消息投递延迟 P99（目标 < 3 秒）
├── 死信队列积压数（告警阈值 > 10）
└── ERP 消费延迟 P99（目标 < 5 秒）
```

---

## 五、数据一致性保障

### 5.1 最终一致性方案

| 场景 | 一致性方案 | 说明 |
|------|-----------|------|
| 工单完工 → ERP 入库 | Outbox + MQ + 幂等 | MES 写库 + 发消息，ERP 幂等消费 |
| BOM 变更 → MES 同步 | REST + 重试 + 对账 | ERP 推送 + MES 确认 + 日终对账 |
| 库存同步 | MQ + 定时对账 | 实时异步 + 每日批量校准 |
| 成本归集 | 定时批量 | 每日日终跑批，MES→ERP 成本数据 |

### 5.2 对账机制

```sql
-- 每日对账：MES 工单 vs ERP 生产订单
SELECT
    mes.order_no AS mes_order_no,
    erp.order_no AS erp_order_no,
    mes.actual_qty AS mes_qty,
    erp.completed_qty AS erp_qty,
    CASE WHEN mes.actual_qty != erp.completed_qty THEN '数量不一致' ELSE '一致' END AS check_result
FROM mes_work_order mes
LEFT JOIN erp_production_order erp ON mes.erp_order_no = erp.order_no
WHERE mes.status = 'COMPLETED'
  AND DATE(mes.actual_end) = CURDATE() - INTERVAL 1 DAY;
```

---

## 六、集成面试题

### Q1：MES 和 ERP 集成时，如何保证消息不丢？

**考察点**：消息可靠性设计

**参考答案**：
核心方案是 Outbox 模式：
1. MES 完工时，在同一个数据库事务中更新工单状态 + 写入 Outbox 表（status=PENDING）
2. 后台线程定期扫描 PENDING 记录，投递到 MQ
3. 投递成功后更新 Outbox 状态为 SENT
4. ERP 消费确认后更新为 ACKED
5. 失败重试 3 次后进入死信队列，人工处理

关键点：Outbox 写入与业务更新在同一事务中，保证原子性；MQ 投递是异步的，不影响业务响应时间。

### Q2：为什么不用分布式事务（如 Seata）？

**考察点**：架构权衡能力

**参考答案**：
1. MES 和 ERP 通常是独立系统，甚至由不同厂商提供，分布式事务的侵入性太高
2. Seata 的 AT 模式需要数据库代理，增加运维复杂度
3. 生产场景对实时性要求不高（秒级延迟可接受），最终一致性完全够用
4. Outbox + MQ 的方案更轻量、更可靠、更易排查问题
5. 唯一需要用分布式事务的场景：MES 和 ERP 共享同一个数据库（极少见）

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← 生产追溯引擎](./01-engine-pattern.md) | [📚 19-MES](../../README.md) | [AI 增强 →](./03-ai-enhancement.md) |
