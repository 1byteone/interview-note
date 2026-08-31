# ERP 核心业务建模 — 进销存 · 财务 · BOM/MRP

> 本篇建立 ERP 的业务领域模型。

---

## 一、ERP 核心领域模型

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ERP 核心领域模型                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐     │
│  │ 销售管理  │    │ 采购管理  │    │ 库存管理  │    │ 财务管理  │     │
│  │ SO/DN    │    │ PR/PO    │    │ Stock    │    │ GL/AR/AP │     │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘     │
│       │               │               │               │             │
│       └───────────────┼───────────────┼───────────────┘             │
│                       ▼               │                             │
│              ┌──────────────────┐     │                             │
│              │   生产管理        │     │                             │
│              │ BOM / MRP / PO   │─────┘                             │
│              └──────────────────┘                                   │
│                       │                                             │
│                       ▼                                             │
│              ┌──────────────────┐                                   │
│              │ MES 执行层        │                                   │
│              │ 工单/报工/质检    │                                   │
│              └──────────────────┘                                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、销售管理

### 核心实体

| 实体 | 说明 | 关键属性 |
|------|------|---------|
| Customer | 客户 | 名称、联系方式、信用额度 |
| Sales Quotation | 销售报价 | 产品、数量、单价、有效期 |
| Sales Order | 销售订单 | 客户、产品、数量、价格、交期 |
| Delivery Note | 发货单 | 订单、发货仓库、物流信息 |
| Sales Invoice | 销售发票 | 订单、金额、税率、付款条件 |

### O2C 状态机

```
报价单 → 已确认 → 已转订单
                    ↓
销售订单 → 已确认 → 已发货 → 已完成
              ↓
          已取消
```

---

## 三、采购管理

### 核心实体

| 实体 | 说明 | 关键属性 |
|------|------|---------|
| Supplier | 供应商 | 名称、联系方式、账期 |
| Purchase Requisition | 采购申请 | 部门、物料、数量、预算 |
| Purchase Order | 采购订单 | 供应商、物料、数量、价格、交期 |
| Goods Receipt Note | 收货单 | 订单、实收数量、质检状态 |
| Purchase Invoice | 采购发票 | 订单、金额、税率 |

---

## 四、库存管理

### 库存数据模型

```sql
-- 库存主表（实时库存）
CREATE TABLE erp_stock (
    id BIGINT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    batch_no VARCHAR(32),
    qty DECIMAL(10,2) DEFAULT 0,
    reserved_qty DECIMAL(10,2) DEFAULT 0 COMMENT '预留数量',
    available_qty DECIMAL(10,2) GENERATED ALWAYS AS (qty - reserved_qty) STORED,
    avg_cost DECIMAL(10,4) COMMENT '移动加权平均成本',
    UNIQUE KEY uk_material_warehouse_batch (material_id, warehouse_id, batch_no)
);

-- 出入库记录（流水）
CREATE TABLE erp_stock_entry (
    id BIGINT PRIMARY KEY,
    entry_no VARCHAR(32) NOT NULL UNIQUE,
    entry_type VARCHAR(32) NOT NULL COMMENT 'PURCHASE_IN/SALES_OUT/PRODUCE_IN/MATERIAL_OUT/TRANSFER',
    material_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    qty DECIMAL(10,2) NOT NULL,
    batch_no VARCHAR(32),
    unit_cost DECIMAL(10,4),
    total_cost DECIMAL(10,2),
    biz_type VARCHAR(32) COMMENT '业务类型',
    biz_id BIGINT COMMENT '业务单据ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 库存扣减策略

```java
// 防超卖：乐观锁
public void deductStock(Long materialId, Long warehouseId, BigDecimal deductQty) {
    int rows = stockMapper.deductQty(materialId, warehouseId, deductQty);
    if (rows == 0) {
        throw new BusinessException("库存不足");
    }
}

// SQL: UPDATE erp_stock SET qty = qty - ? WHERE material_id = ? AND warehouse_id = ? AND qty >= ?
```

---

## 五、财务模块

### 核心概念

| 概念 | 说明 |
|------|------|
| 总账（GL） | 所有财务交易的汇总账本 |
| 应收账款（AR） | 客户欠我们的钱 |
| 应付账款（AP） | 我们欠供应商的钱 |
| 科目 | 资产/负债/权益/收入/费用 |
| 借贷记账 | 有借必有贷，借贷必相等 |

### 会计分录示例

```
采购入库：
  借：库存商品    100,000
  贷：应付账款    100,000

销售出库：
  借：应收账款    150,000
  贷：主营业务收入  150,000

  借：主营业务成本  100,000
  贷：库存商品    100,000

收款：
  借：银行存款    150,000
  贷：应收账款    150,000
```

---

## 六、MRP（物料需求计划）

### MRP 核心公式

```
净需求 = 毛需求 - 现有库存 - 在途订单 + 已分配量

其中：
  毛需求 = 销售订单数量 × BOM 用量（含损耗）
  现有库存 = 当前仓库库存
  在途订单 = 已下未到的采购订单数量
  已分配量 = 已预留未出库的数量
```

### MRP 输出

| 输出 | 适用物料 | 说明 |
|------|---------|------|
| 采购申请 | 外购件 | 净需求 > 0 → 生成 PR → 转 PO |
| 生产工单 | 自制件 | 净需求 > 0 → 生成工单 → 下发 MES |
| 无需求 | 库存充足 | 净需求 ≤ 0 → 不生成单据 |

### MRP 展开示例

```
销售订单：1000 台智能手表
    ↓
BOM 展开：
  表壳（自制件）×1 → 生产工单
    ├── 不锈钢板（外购件）×0.05kg → 采购申请
    └── 螺丝（外购件）×4 → 采购申请
  屏幕模组（外购件）×1 → 采购申请
  电池（外购件）×1 → 采购申请
  表带（外购件）×1 → 采购申请
```

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← 快速上手](../01-basics/01-quick-start.md) | [📚 21-ERP](../../README.md) | [架构设计 →](./02-architecture.md) |
