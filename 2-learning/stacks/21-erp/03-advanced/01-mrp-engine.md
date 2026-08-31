# ERP MRP 引擎 — BOM 展开与净需求计算

---

## 一、MRP 核心流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                    MRP 核心流程                                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  输入                                                               │
│  ├── 销售订单（确定需求）                                             │
│  ├── 预测数据（计划需求）                                             │
│  └── 现有库存 + 在途订单                                             │
│                                                                     │
│  处理                                                               │
│  ├── Step 1: 毛需求计算                                              │
│  │   └── 销售订单数量 × BOM 用量                                     │
│  ├── Step 2: BOM 多级展开                                            │
│  │   └── 成品 → 半成品 → 原料（递归）                                 │
│  ├── Step 3: 净需求计算                                              │
│  │   └── 毛需求 - 现有库存 - 在途 + 已分配                            │
│  └── Step 4: 生成建议                                                │
│      ├── 外购件 → 采购申请（PR）                                      │
│      └── 自制件 → 生产工单（WO）→ 下发 MES                            │
│                                                                     │
│  输出                                                               │
│  ├── 采购申请清单                                                    │
│  ├── 生产工单清单                                                    │
│  └── 库存预警（低于安全库存）                                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、BOM 多级展开实现

```java
@Service
public class BOMService {

    /**
     * BOM 多级展开（递归 CTE）
     */
    public List<BOMItemDTO> explodeBOM(Long productId, BigDecimal requiredQty) {
        // MySQL 8.0 递归 CTE
        String sql = """
            WITH RECURSIVE bom_tree AS (
                SELECT child_item_id, quantity, scrap_rate, 1 as depth
                FROM erp_bom_item
                WHERE parent_item_id = :productId
                UNION ALL
                SELECT b.child_item_id, b.quantity, b.scrap_rate, t.depth + 1
                FROM erp_bom_item b
                JOIN bom_tree t ON b.parent_item_id = t.child_item_id
                WHERE t.depth < 10
            )
            SELECT
                child_item_id as materialId,
                SUM(quantity * (1 + scrap_rate)) as totalQty,
                depth
            FROM bom_tree
            GROUP BY child_item_id, depth
            ORDER BY depth, child_item_id
        """;
        return jdbcTemplate.query(sql, Map.of("productId", productId),
            (rs, rowNum) -> new BOMItemDTO(
                rs.getLong("materialId"),
                rs.getBigDecimal("totalQty").multiply(requiredQty),
                rs.getInt("depth")
            ));
    }
}
```

---

## 三、净需求计算

```java
@Service
public class MRPService {

    public List<MRPResultDTO> calculateMRP(Long productId, BigDecimal salesOrderQty) {
        // 1. BOM 展开
        List<BOMItemDTO> bomItems = bomService.explodeBOM(productId, salesOrderQty);

        List<MRPResultDTO> results = new ArrayList<>();

        for (BOMItemDTO item : bomItems) {
            // 2. 查询现有库存
            BigDecimal stockQty = stockService.getAvailableQty(item.getMaterialId());

            // 3. 查询在途订单（已下未到的 PO）
            BigDecimal inboundQty = purchaseService.getInboundQty(item.getMaterialId());

            // 4. 查询已分配量（已预留未出库）
            BigDecimal allocatedQty = stockService.getAllocatedQty(item.getMaterialId());

            // 5. 净需求 = 毛需求 - 现有库存 - 在途 + 已分配
            BigDecimal netRequirement = item.getTotalQty()
                .subtract(stockQty)
                .subtract(inboundQty)
                .add(allocatedQty);

            if (netRequirement.compareTo(BigDecimal.ZERO) > 0) {
                // 6. 判断是外购还是自制
                MaterialDTO material = materialService.getById(item.getMaterialId());
                if ("RAW".equals(material.getMaterialType())) {
                    // 外购件 → 采购申请
                    results.add(new MRPResultDTO(
                        item.getMaterialId(), "PURCHASE_REQUISITION", netRequirement));
                } else if ("SEMI".equals(material.getMaterialType())) {
                    // 自制件 → 生产工单
                    results.add(new MRPResultDTO(
                        item.getMaterialId(), "PRODUCTION_ORDER", netRequirement));
                }
            }
        }
        return results;
    }
}
```

---

## 四、安全库存预警

```sql
-- 查询低于安全库存的物料
SELECT
    m.material_code,
    m.material_name,
    COALESCE(SUM(s.qty), 0) as current_stock,
    m.safety_stock,
    m.safety_stock - COALESCE(SUM(s.qty), 0) as shortage
FROM erp_material m
LEFT JOIN erp_stock s ON m.id = s.material_id
WHERE m.status = 'ACTIVE'
GROUP BY m.id
HAVING current_stock < m.safety_stock
ORDER BY shortage DESC;
```

---

## 五、面试 MRP 题

### Q1：MRP 的核心逻辑是什么？
**参考答案**：MRP = BOM 展开 + 净需求计算。毛需求（销售订单 × BOM 用量）- 现有库存 - 在途订单 + 已分配量 = 净需求。净需求 > 0 生成采购申请（外购件）或生产工单（自制件）。

### Q2：BOM 多级展开在数据库中如何实现？
**参考答案**：MySQL 8.0 递归 CTE。邻接表存储 BOM 关系，递归查询展开所有层级。深度限制防止死循环。

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← 数据库设计](../02-core/03-data-design.md) | [📚 21-ERP](../../README.md) | [系统集成 →](./02-integration.md) |
