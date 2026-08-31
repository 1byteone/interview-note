# ERP 速查表（Cheat Sheet）

> 一页掌握 ERP 核心概念、业务链路与集成模式。

---

## 核心概念对照

| 概念 | 一句话 | 链路 |
|------|--------|------|
| O2C | Order to Cash，从销售订单到收款的完整链路 | 销售→发货→开票→收款 |
| P2P | Procure to Pay，从采购申请到付款的完整链路 | 采购→收货→开票→付款 |
| MRP | 物料需求计划，BOM 展开 + 净需求计算 | 需求→BOM 爆炸→采购/生产建议 |
| BOM | 物料清单，成品由哪些子件组成 | 主数据 |
| SKU | 库存量最小单位 | 主数据 |
| GL | 总账（General Ledger） | 财务 |
| AR | 应收账款（Accounts Receivable） | 财务 |
| AP | 应付账款（Accounts Payable） | 财务 |

## O2C 链路

```
客户下单 → 销售报价 → 销售订单 → 发货 → 开票 → 收款
  CRM        SO        SO      DN     INV     Receipt
```

## P2P 链路

```
采购申请 → 采购订单 → 收货 → 发票校验 → 付款
   PR        PO       GRN     Invoice    Payment
```

## MRP 核心逻辑

```
毛需求（销售订单 + 预测）
  - 现有库存
  - 在途订单（已下未到）
  - 已分配量（已预留未出库）
  = 净需求
  → 采购申请（外购件）/ 生产工单（自制件）
```

## ERP 核心表速查

| 表名 | 用途 | 关键字段 |
|------|------|---------|
| `erp_sales_order` | 销售订单 | so_no, customer_id, total_amount, status |
| `erp_purchase_order` | 采购订单 | po_no, supplier_id, total_amount, status |
| `erp_stock` | 库存 | material_id, warehouse_id, qty |
| `erp_stock_entry` | 出入库记录 | entry_type, material_id, qty, batch_no |
| `erp_gl_entry` | 总账凭证 | account_id, debit, credit, posting_date |
| `erp_ar_invoice` | 应收发票 | invoice_no, customer_id, amount |
| `erp_ap_invoice` | 应付发票 | invoice_no, supplier_id, amount |

## ERP 与 MES/OA 集成接口

| 方向 | 接口 | 数据 |
|------|------|------|
| ERP → MES | 生产订单下发 | 工单号, BOM, 数量, 日期 |
| MES → ERP | 完工入库回报 | 成品批次, 数量, 成本 |
| OA → ERP | 采购审批通过 | 创建 PO |
| OA → ERP | 付款审批通过 | 执行付款 |
| ERP → OA | PO 状态变更 | 通知 OA 更新审批单状态 |

## 高频面试速答

1. **Q**: O2C 和 P2P 的区别？ → **A**: O2C 是"卖东西"的链路（销售→收款），P2P 是"买东西"的链路（采购→付款）。两者都涉及库存和财务。

2. **Q**: MRP 的核心逻辑是什么？ → **A**: 毛需求 - 现有库存 - 在途 + 已分配 = 净需求。净需求 > 0 就生成采购申请或生产工单。

3. **Q**: ERP 的库存管理怎么保证账实一致？ → **A**: 每笔出入库都生成库存变动记录（Stock Entry），实时更新库存数量。定期盘点校准。

4. **Q**: ERP 和 MES 的库存管理有什么区别？ → **A**: ERP 管"财务视角的库存"（金额+数量），MES 管"执行视角的库存"（批次+SN+工位）。

5. **Q**: 什么是成本核算？ → **A**: 计算每个产品的实际成本 = 材料成本 + 人工成本 + 制造费用。ERP 从 MES 获取报工数据（工时）和领料数据（材料消耗）进行成本归集。
