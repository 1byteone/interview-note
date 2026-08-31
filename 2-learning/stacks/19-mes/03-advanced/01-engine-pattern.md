# MES 生产追溯引擎 — 批次 · SN · 正反向追溯

> 本篇深入 MES 追溯体系的设计与实现。追溯是制造业合规的刚需——食品召回、药品追溯、电子产品质量追踪，都依赖这套引擎。
> 配套 Mermaid 源文件：[`_assets/diagrams/mes/traceability-flow.mmd`](../../../_assets/diagrams/mes/traceability-flow.mmd)

---

## 一、追溯体系全景

```
┌─────────────────────────────────────────────────────────────────────┐
│                    MES 追溯体系                                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐  │
│  │ 原料批次  │────→│ 生产批次  │────→│ 成品批次  │────→│ 客户交付  │  │
│  │ IQC 入库  │     │ 工单生产  │     │ FQC 入库  │     │ 销售出库  │  │
│  └──────────┘     └──────────┘     └──────────┘     └──────────┘  │
│       │                │                │                │          │
│       ▼                ▼                ▼                ▼          │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │              追溯关系图（Trace Graph）                         │  │
│  │                                                              │  │
│  │  原料批次A ──→ 工单1 ──→ 成品批次X ──→ 客户1                 │  │
│  │  原料批次A ──→ 工单2 ──→ 成品批次Y ──→ 客户2                 │  │
│  │  原料批次B ──→ 工单1 ──→ 成品批次X ──→ 客户1                 │  │
│  │                                                              │  │
│  │  正向追溯：原料批次A → 哪些成品用了它？（→客户1,客户2）        │  │
│  │  反向追溯：客户1退货 → 这批成品用了哪些原料？（→批次A,批次B）  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、批次管理

### 2.1 批次号生成规则

```
批次号格式：{工厂}{日期}{流水号}
示例：SH20260831001
      │   │        │
      │   │        └── 当日第 1 批
      │   └── 日期 2026-08-31
      └── 工厂代码（上海）

规则可配置：
- 前缀：工厂/车间/产品线
- 日期格式：YYYYMMDD / YYYYMM / YYMM
- 流水号位数：3-6 位
- 随机号：可选，用于防猜测
```

### 2.2 批次生命周期

```
已创建 → 已入库 → 生产中 → 已完工 → 已出库
   ↓        ↓        ↓        ↓        ↓
已锁定   已锁定   已锁定   已锁定   已锁定
```

| 状态 | 触发条件 | 说明 |
|------|---------|------|
| 已创建 | IQC 入库 / 生产领料 | 批次产生 |
| 生产中 | 工单开工 | 批次投入生产 |
| 已完工 | FQC 通过 | 批次完成生产 |
| 已出库 | 销售出库 / 发货 | 批次离开仓库 |
| 已锁定 | 质检不合格 / 客户投诉 | 批次暂停流转 |

### 2.3 批次追溯数据模型

```sql
-- 批次表
CREATE TABLE mes_trace_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_no VARCHAR(32) NOT NULL UNIQUE,
    material_id BIGINT NOT NULL,
    work_order_id BIGINT,
    quantity DECIMAL(10,2),
    warehouse_id BIGINT,
    supplier_batch_no VARCHAR(64),  -- 供应商批次号（IQC 来料用）
    produce_date DATE,
    expire_date DATE,
    status ENUM('ACTIVE','LOCKED','SCRAPPED') DEFAULT 'ACTIVE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 批次关系表（谁用了谁）
CREATE TABLE mes_batch_relation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    input_batch_id BIGINT NOT NULL,   -- 输入批次（原料）
    output_batch_id BIGINT NOT NULL,  -- 输出批次（成品/半成品）
    work_order_id BIGINT NOT NULL,    -- 关联工单
    consumed_qty DECIMAL(10,2),       -- 消耗数量
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_input (input_batch_id),
    KEY idx_output (output_batch_id)
);
```

---

## 三、SN（序列号）管理

### 3.1 SN 生成规则

```
SN 格式：{产品码}{日期}{流水号}
示例：SW20260831000001
      │  │        │
      │  │        └── 6 位流水号
      │  └── 日期
      └── 产品编码（智能手表）

高级规则：
- 条码类型：Code128 / QR Code / DataMatrix
- 打印时机：FQC 通过后自动打印
- 标签内容：SN + 批次号 + 产品名 + 生产日期
```

### 3.2 SN 与批次的关系

```
一个批次包含多个 SN
一个 SN 属于一个批次

批次: BATCH-20260831-001
├── SN: SW20260831000001
├── SN: SW20260831000002
├── SN: SW20260831000003
└── SN: SW20260831000004
```

### 3.3 SN 追溯数据模型

```sql
CREATE TABLE mes_trace_sn (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    serial_no VARCHAR(64) NOT NULL UNIQUE,
    batch_id BIGINT,
    material_id BIGINT NOT NULL,
    work_order_id BIGINT,
    status ENUM('AVAILABLE','LOCKED','SCRAPPED') DEFAULT 'AVAILABLE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- SN 关系表（成品 SN ↔ 原料 SN）
CREATE TABLE mes_sn_relation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_sn_id BIGINT NOT NULL,  -- 成品 SN
    child_sn_id BIGINT NOT NULL,   -- 原料 SN
    work_order_id BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_parent (parent_sn_id),
    KEY idx_child (child_sn_id)
);
```

---

## 四、正向追溯与反向追溯

### 4.1 正向追溯（Forward Traceability）

> 从原料出发，追踪它被用在了哪些成品中，最终交付给了哪些客户。

**应用场景**：供应商原料质量投诉 → 紧急召回受影响的成品。

```sql
-- 正向追溯：原料批次 → 哪些成品批次用了它
WITH RECURSIVE forward_trace AS (
    -- 第一层：直接消耗该原料批次的成品批次
    SELECT output_batch_id, work_order_id, consumed_qty, 1 AS depth
    FROM mes_batch_relation
    WHERE input_batch_id = ?

    UNION ALL

    -- 递归：半成品批次继续向上追溯
    SELECT r.output_batch_id, r.work_order_id, r.consumed_qty, t.depth + 1
    FROM mes_batch_relation r
    JOIN forward_trace t ON r.input_batch_id = t.output_batch_id
    WHERE t.depth < 10  -- 防止死循环
)
SELECT * FROM forward_trace;
```

### 4.2 反向追溯（Backward Traceability）

> 从成品出发，追溯它使用了哪些原料批次，以及这些原料的供应商和生产日期。

**应用场景**：客户退货 → 检查该成品用了哪些原料 → 判断是否为系统性问题。

```sql
-- 反向追溯：成品批次 → 使用了哪些原料批次
WITH RECURSIVE backward_trace AS (
    SELECT input_batch_id, work_order_id, consumed_qty, 1 AS depth
    FROM mes_batch_relation
    WHERE output_batch_id = ?

    UNION ALL

    SELECT r.input_batch_id, r.work_order_id, r.consumed_qty, t.depth + 1
    FROM mes_batch_relation r
    JOIN backward_trace t ON r.output_batch_id = t.input_batch_id
    WHERE t.depth < 10
)
SELECT * FROM backward_trace;
```

### 4.3 追溯查询性能优化

| 优化手段 | 说明 |
|---------|------|
| 递归 CTE 深度限制 | `WHERE depth < 10` 防止死循环和性能爆炸 |
| 索引覆盖 | `idx_input` 和 `idx_output` 覆盖递归查询 |
| 缓存热门追溯结果 | 客户投诉场景频繁查询同一成品，用 Redis 缓存 |
| 预计算追溯快照 | 每日批量计算追溯关系，写入 `mes_trace_snapshot` 表 |
| ES 全文检索 | 追溯日志写入 ES，支持模糊搜索（如按供应商名搜索） |

---

## 五、追溯场景实战

### 场景一：供应商原料投诉

```
1. 供应商通知：原料批次 RM-20260801-003 存在质量问题
2. 正向追溯：查出该批次被用于哪些成品
   → 成品批次 FG-20260815-001, FG-20260818-002
3. 锁定批次：将受影响的成品批次状态改为 LOCKED
4. 通知客户：哪些客户的订单受影响
5. 处置决策：召回 / 让步接收 / 报废
```

### 场景二：客户退货追溯

```
1. 客户退回成品 SN: SW20260815000123
2. 反向追溯：查出该 SN 使用了哪些原料
   → 原料批次: RM-20260801-003, RM-20260805-007
3. 检查原料批次的质检记录
   → RM-20260801-003: IQC 合格
   → RM-20260805-007: IQC 合格
4. 检查生产过程中的 IPQC 记录
   → 工单 WO-20260812-005: IPQC 合格
5. 检查设备日志
   → 设备 EQ-003 在生产期间有 2 次异常停机
6. 根因定位：设备异常导致产品缺陷
```

---

## 六、面试追溯题

### Q1：正向追溯和反向追溯的核心区别？

**考察点**：追溯方向与应用场景

**参考答案**：
- 正向追溯：原料 → 成品 → 客户。用于供应商原料出问题时，快速定位受影响的成品和客户，启动召回。
- 反向追溯：成品 → 原料。用于客户退货时，追溯该成品用了哪些原料、在哪些设备上生产、经过哪些工序，定位质量问题根因。
- 两者共享同一套追溯数据模型（批次关系表），只是查询方向不同。

### Q2：追溯系统如何保证数据完整性？

**考察点**：数据一致性设计

**参考答案**：
1. **领料时建立追溯关系**：工单领料时，自动记录原料批次与工单的关联
2. **报工时绑定 SN**：每道工序报工时，记录 SN 的过站信息
3. **完工时建立成品追溯**：成品入库时，建立成品批次与原料批次的完整关系链
4. **不可变追溯记录**：追溯关系一旦建立，不允许修改或删除（审计要求）
5. **定期校验**：每日跑批校验追溯链的完整性，发现断裂告警

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← 数据库设计](../02-core/03-data-design.md) | [📚 19-MES](../../README.md) | [ERP 集成 →](./02-integration.md) |
