# OA 面试场景题（5 个综合实战场景）

---

## 场景一：多级采购审批流程设计

### 场景描述

公司采购审批按金额分级：
- ≤1 万：部门经理审批即可
- 1-5 万：部门经理 + 财务审批
- \>5 万：部门经理 + 财务 + 总经理审批

采购申请表包含：物料名称、数量、单价、总金额、供应商、紧急程度。

### 技术方案

```xml
<!-- BPMN 排他网关 -->
<exclusiveGateway id="amountGateway"/>

<sequenceFlow sourceRef="amountGateway" targetRef="leaderApprove">
    <conditionExpression>${amount <= 10000}</conditionExpression>
</sequenceFlow>

<sequenceFlow sourceRef="amountGateway" targetRef="financeApprove">
    <conditionExpression>${amount > 10000 && amount <= 50000}</conditionExpression>
</sequenceFlow>

<sequenceFlow sourceRef="amountGateway" targetRef="gmApprove">
    <conditionExpression>${amount > 50000}</conditionExpression>
</sequenceFlow>
```

### 面试官考察点

- 排他网关条件设计
- 审批人分配策略
- 审批后的业务联动（ERP 创建 PO）

---

## 场景二：请假审批全流程

### 场景描述

请假审批流程：
1. 员工提交请假单（天数、类型、原因）
2. 部门经理审批
3. >3 天：HR 审批
4. \>7 天：总经理审批
5. 审批通过后更新考勤系统

### 技术方案

```java
// 启动流程
Map<String, Object> vars = Map.of(
    "days", 5,
    "leaveType", "ANNUAL",
    "initiator", "zhangsan",
    "leaveId", leaveRequestId
);
runtimeService.startProcessInstanceByKey("leave-process", vars);

// 部门经理审批
taskService.complete(taskId, Map.of("approved", "true"));

// 流程结束时自动更新考勤
// ExecutionListener: leaveService.approve(leaveId);
```

### 面试官考察点

- 条件分支（天数决定是否需要 HR/总经理审批）
- 审批后的业务联动
- 驳回后回到发起人

---

## 场景三：OA 与 ERP 实时联动

### 场景描述

OA 采购审批通过后，需要自动在 ERP 中创建采购订单。要求：
- 审批通过后 5 秒内 ERP 收到事件
- 消息不丢（100% 投递成功）
- ERP 消费失败时 OA 能感知

### 技术方案

```
OA 审批通过
  ├── 事务内：更新审批状态 + 写 Outbox 表
  └── 后台线程：每 5 秒扫描 → 投递 RocketMQ
        ↓
ERP 消费
  ├── 幂等检查（messageId）
  ├── 创建采购订单
  └── 确认消费（更新 Outbox 状态）
        ↓
监控
  ├── 死信队列告警
  └── 每日对账
```

### 面试官考察点

- Outbox 模式保证可靠性
- 幂等消费设计
- 监控与告警

---

## 场景四：数据权限设计

### 场景描述

公司组织架构：
```
总经理
├── 技术部
│   ├── 后端组
│   └── 前端组
├── 业务部
│   ├── 销售组
│   └── 运营组
└── 职能部
    ├── HR
    └── 财务
```

要求：
- 总经理看所有数据
- 部门经理看本部门及下级数据
- 组长看本组数据
- 普通员工只看自己的数据

### 技术方案

```sql
-- 部门经理查询：本部门及下级
SELECT * FROM oa_leave_request
WHERE dept_id IN (
    WITH RECURSIVE dept_tree AS (
        SELECT id FROM sys_dept WHERE id = ?
        UNION ALL
        SELECT d.id FROM sys_dept d JOIN dept_tree t ON d.parent_id = t.id
    )
    SELECT id FROM dept_tree
);

-- 普通员工查询：仅本人
SELECT * FROM oa_leave_request WHERE user_id = ?;
```

### 面试官考察点

- 数据权限的五种范围
- 递归查询部门树
- MyBatis 拦截器实现

---

## 场景五：企业知识问答系统（RAG）

### 场景描述

员工频繁咨询 HR 关于制度的问题（年假、报销、考勤），HR 重复回答量大。需要构建 RAG 知识问答系统。

### 技术方案

```
制度文档（PDF/Word）
    ↓ 文档解析 + 切片
    ↓ 向量化（text-embedding-3-small）
    ↓ 存入 ChromaDB
    ↓
员工提问
    ↓ 向量检索 Top-5 文档片段
    ↓ + LLM 生成回答
    ↓ 答案 + 来源引用
    ↓
返回给员工
```

### 面试官考察点

- RAG 架构设计
- 文档切片策略
- 答案准确性保证（来源引用 + 人工审核）

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← 面试深挖](./deep-dive.md) | [📚 20-OA](../../README.md) | [📚 总目录](../../../README-learning.md) |
