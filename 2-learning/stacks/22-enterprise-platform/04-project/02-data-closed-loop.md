# 企业平台数据闭环详解 — 三域联动 + 简历包装

> 本篇详解 OA↔ERP↔MES 的完整数据闭环，以及如何包装成简历项目。

---

## 一、端到端数据闭环

### 核心闭环：O2C + P2P + MRP + MES

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    企业平台数据闭环                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  客户下单 ──────────────────────────────────────────────────────→ 收款      │
│     │                                                                    │
│     │ SO                                                               │
│     ▼                                                                    │
│  ┌─────────┐                                                             │
│  │  ERP    │                                                             │
│  │ 库存检查 │──→ 有库存 ──→ 直接发货                                      │
│  │ MRP     │──→ 缺料 ──→ 采购申请 ──→ OA 审批 ──→ ERP PO ──→ 入库        │
│  │ 生产计划 │──→ 自制件 ──→ 生产工单 ──→ OA 审批 ──→ MES 执行             │
│  └─────────┘                                    │    │                    │
│                                                │    │                    │
│  ┌─────────┐                                    │    │                    │
│  │  MES    │←── 工单 ──→ 报工 ──→ 质检 ──→ 完工回报 ──→ ERP 入库         │
│  └─────────┘                                    │                        │
│                                                ▼                        │
│  ┌─────────┐                              成品入库 ──→ 发货 ──→ 开票      │
│  │  OA     │←── 采购审批 ──→ ERP PO                                        │
│  │         │←── 工单审批 ──→ MES 执行                                      │
│  │         │←── 异常审批 ──→ ERP 处置                                      │
│  └─────────┘                                                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、关键数据流详细设计

### 2.1 OA 审批 → ERP 创建采购单

```
触发：OA 采购审批通过
消息：oa.purchase.approved
Payload：{
  "approvalNo": "AP-20260831-001",
  "items": [
    {"materialCode": "RAW-001", "qty": 100, "supplierId": "S001"}
  ],
  "totalAmount": 50000,
  "applicant": "zhangsan"
}

ERP 消费：
1. 幂等检查（messageId）
2. 创建采购申请 PR
3. 自动转采购订单 PO
4. 通知供应商
5. 更新 OA 审批单状态为"已下单"
```

### 2.2 ERP 生产订单 → MES 工单执行

```
触发：MRP 生成生产订单
消息：erp.production_order.created
Payload：{
  "orderNo": "WO-20260831-001",
  "bomId": "BOM-001",
  "productCode": "FG-001",
  "qty": 500,
  "planStart": "2026-09-01",
  "planEnd": "2026-09-05"
}

MES 消费：
1. 创建工单（status=CREATED）
2. BOM 展开 → 领料清单
3. 通知仓库准备原料
4. 通知排产员排产
```

### 2.3 MES 完工 → ERP 入库 + 成本归集

```
触发：MES 工单完工
消息：mes.work_order.completed
Payload：{
  "workOrderNo": "WO-20260831-001",
  "erpOrderNo": "PO-20260828-005",
  "actualQty": 498,
  "scrapQty": 2,
  "batchNo": "FG-20260905-001",
  "costBreakdown": {
    "materialCost": 62500,
    "laborCost": 16000,
    "overheadCost": 4000
  }
}

ERP 消费：
1. 幂等检查
2. 创建成品入库单（Stock Entry: Manufacture）
3. 更新库存（成品 +498）
4. 归集成本到产品
5. 生成会计分录（借：库存商品 / 贷：在制品）
6. 通知 OA 更新工单审批状态
```

---

## 三、日终对账

```sql
-- 每日对账：MES 工单 vs ERP 生产订单
SELECT
    mes.order_no AS mes_wo,
    erp.order_no AS erp_wo,
    mes.actual_qty AS mes_qty,
    erp.completed_qty AS erp_qty,
    CASE
        WHEN mes.actual_qty = erp.completed_qty THEN '一致'
        ELSE '不一致'
    END AS status
FROM mes_work_order mes
LEFT JOIN erp_production_order erp ON mes.erp_order_no = erp.order_no
WHERE DATE(mes.actual_end) = CURDATE() - INTERVAL 1 DAY;
```

---

## 四、简历完整包装

### 项目名称

> 企业数字化运营平台（OA + ERP + MES 三域整合）

### 项目描述（4 行）

```
企业数字化运营平台 — 三域整合（OA + ERP + MES）
技术栈：Spring Boot 3 + Flowable + MyBatis-Plus + RocketMQ + Redis + MySQL + Vue3

· 设计 OA 审批驱动 ERP 采购/生产、ERP 下发 MES 工单、MES 完工回报 ERP 的完整数据闭环
· 实现 MRP 引引擎（BOM 展开 + 净需求计算），缺料率从 15% 降至 2%
· 采用 Outbox + RocketMQ 实现三域事件驱动集成，消息投递成功率 99.97%
· 基于 RAG 构建企业知识问答 + AI BI 自然语言查报表，HR 咨询量减少 60%
```

### 面试话术（2 分钟版）

> "我做的是一个企业数字化运营平台，整合了 OA、ERP、MES 三个子系统。
>
> 核心价值是**三域数据闭环**：OA 审批通过后自动触发 ERP 创建采购单或生产单，ERP 的生产计划下发给 MES 执行，MES 完工后回报 ERP 更新库存和财务。
>
> 技术上有四个亮点：
>
> 第一，**OA 审批引擎**——基于 Flowable 设计动态审批流，支持条件分支、会签/或签，20 多种审批类型全靠配置。
>
> 第二，**MRP 引擎**——实现 BOM 多级展开和净需求计算，自动识别缺料并生成采购建议，缺料率从 15% 降到 2%。
>
> 第三，**三域集成**——用 Outbox + RocketMQ 保证消息不丢，MES 完工回报 ERP 的投递成功率 99.97%，每日对账自动校验一致性。
>
> 第四，**AI 增强**——MES 做了智能排产（OR-Tools + LLM），OA 做了 RAG 企业知识问答，ERP 做了 AI BI 自然语言查报表。
>
> 这个项目最大的技术挑战是三域数据一致性，我的方案是事件驱动 + 幂等 + 对账三件套。"

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← 架构蓝图](./01-architecture-blueprint.md) | [📚 22-企业平台](../README.md) | [面试速记 →](../05-interview/quick-revision.md) |
