# OA 企业集成 — OA↔ERP · OA↔MES 联动

> 本篇聚焦 OA 与 ERP、MES 的集成设计：审批事件如何驱动业务流转。

---

## 一、OA 集成全景

```
┌─────────────────────────────────────────────────────────────────────┐
│                    OA 企业集成架构                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────┐     ┌──────────────────┐     ┌─────────┐             │
│  │   OA    │     │  事件总线         │     │   ERP   │             │
│  │         │     │                  │     │         │             │
│  │ 采购审批 │────→│ oa.purchase.*    │────→│ 创建PO  │             │
│  │ 付款审批 │────→│ oa.payment.*     │────→│ 执行付款 │             │
│  │ 合同审批 │────→│ oa.contract.*    │────→│ 合同归档 │             │
│  │         │     │                  │     │         │             │
│  │←────────│←────│ erp.po.received  │←────│ PO 状态  │             │
│  │ 通知    │     │ erp.stock.sync   │     │ 库存同步 │             │
│  └─────────┘     └──────────────────┘     └─────────┘             │
│       │                                                  │         │
│       │          ┌──────────────────┐                    │         │
│       │          │                  │                    │         │
│       └─────────→│   MES            │←───────────────────┘         │
│                  │                  │                               │
│  ┌───────────┐  │ 工单审批通过      │  ┌───────────┐               │
│  │ 工单审批   │──│ → 下达工单       │  │ 完工回报   │               │
│  │ 设备采购   │  │                  │  │ 通知 OA   │               │
│  └───────────┘  └──────────────────┘  └───────────┘               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、OA→ERP 集成场景

### 2.1 采购审批 → 创建采购订单

```
1. 员工提交采购申请（OA 表单）
   · 物料编码、数量、预算
   ↓
2. OA 审批流
   · 部门经理审批 → 财务审批 → 总经理审批（按金额）
   ↓
3. 审批通过事件
   → MQ: topic=oa.purchase.approved
   → payload: {申请单号, 物料, 数量, 供应商, 金额}
   ↓
4. ERP 消费事件
   · 创建采购订单（PO）
   · 更新预算
   · 通知供应商
   ↓
5. ERP 回写状态
   → MQ: topic=erp.po.created
   → OA 更新申请单状态为"已下单"
```

### 2.2 付款审批 → 执行付款

```
1. 供应商发票到达（ERP 录入）
   ↓
2. 生成付款申请（OA 表单）
   · 供应商、金额、发票号
   ↓
3. OA 审批流
   · 财务审核 → 总经理审批
   ↓
4. 审批通过事件
   → MQ: topic=oa.payment.approved
   → payload: {付款单号, 供应商, 金额, 银行账号}
   ↓
5. ERP 消费事件
   · 执行付款（对接银行接口）
   · 更新应付账款
   · 记录财务凭证
```

---

## 三、OA→MES 集成场景

### 3.1 工单审批 → 下达工单

```
1. 生产计划员创建工单（MES）
   ↓
2. 工单审批流（OA）
   · 生产主管审批 → 质量主管审批
   ↓
3. 审批通过事件
   → MQ: topic=oa.workorder.approved
   → payload: {工单号, BOM, 数量, 计划日期}
   ↓
4. MES 消费事件
   · 工单状态 → RELEASED（已下达）
   · 通知仓库准备领料
   · 通知排产员排产
```

### 3.2 设备采购审批

```
1. 设备管理员提交设备采购申请（OA）
   ↓
2. 审批流
   · 设备主管 → 财务 → 总经理
   ↓
3. 审批通过 → ERP 创建采购订单
4. 设备到货 → MES 录入设备档案
```

---

## 四、集成实现

### 4.1 事件发布

```java
// OA 审批通过后发布事件
@Component
public class ApprovalEventPublisher {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public void publishPurchaseApproved(PurchaseApprovalDTO dto) {
        Message<ApprovalEvent> message = MessageBuilder
            .withPayload(new ApprovalEvent("oa.purchase.approved", dto))
            .build();
        rocketMQTemplate.send("oa.purchase.approved", message);
    }

    public void publishWorkOrderApproved(String workOrderNo) {
        Message<ApprovalEvent> message = MessageBuilder
            .withPayload(new ApprovalEvent("oa.workorder.approved",
                Map.of("workOrderNo", workOrderNo)))
            .build();
        rocketMQTemplate.send("oa.workorder.approved", message);
    }
}
```

### 4.2 事件消费（ERP 侧）

```java
@RocketMQMessageListener(topic = "oa.purchase.approved")
public class PurchaseApprovalConsumer implements RocketMQListener<MessageExt> {

    @Autowired
    private PurchaseOrderService poService;

    @Override
    public void onMessage(MessageExt msg) {
        ApprovalEvent event = JSON.parseObject(msg.getBody(), ApprovalEvent.class);
        PurchaseApprovalDTO dto = (PurchaseApprovalDTO) event.getPayload();

        // 幂等检查
        if (messageLogService.exists(msg.getMsgId())) {
            return;
        }

        // 创建采购订单
        poService.createFromApproval(dto);

        // 记录消息日志
        messageLogService.log(msg.getMsgId(), "PROCESSED");
    }
}
```

---

## 五、集成面试题

### Q1：OA 审批通过后如何保证 ERP 一定收到事件？

**参考答案**：
采用 Outbox 模式：
1. OA 审批通过时，在同一事务中更新审批状态 + 写入 Outbox 表
2. 后台线程扫描 PENDING 记录，投递到 RocketMQ
3. ERP 消费确认后更新 Outbox 状态
4. 失败重试 + 死信队列 + 告警

### Q2：OA 和 ERP 的编码体系不同怎么办？

**参考答案**：
建立映射表：
1. OA 侧维护"编码映射表"（`oa_code_mapping`），记录 OA 编码→ERP 编码的对应关系
2. 集成时通过映射表转换
3. 映射表由管理员维护，支持 OA 和 ERP 编码独立演进

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← Flowable 深入](./01-flowable-deep.md) | [📚 20-OA](../../README.md) | [AI 增强 →](./03-ai-enhancement.md) |
