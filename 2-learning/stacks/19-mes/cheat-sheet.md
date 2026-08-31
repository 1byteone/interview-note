# MES 速查表（Cheat Sheet）

> 一页掌握 MES 核心概念、数据模型与集成模式。

---

## 核心概念对照

| 概念 | 一句话 | 层级 |
|------|--------|------|
| ISA-95 | 制造业信息系统集成国际标准，定义 L0-L4 五层架构 | 标准 |
| MES | 制造执行系统，管理车间层的生产执行与数据采集 | L3 |
| MOM | 制造运营管理，MES 的超集，含 MES + 质量 + 维护 + 库存 | L3 |
| BOM | 物料清单，成品由哪些半成品/原料组成 | 主数据 |
| 工艺路线 | 产品经过哪些工序、在哪些工位上加工 | 主数据 |
| 工单 | 一次具体的生产任务，绑定 BOM + 数量 + 日期 | 执行 |
| 排产 | 把工单分配到具体产线/设备/时段 | 计划 |
| 报工 | 操作员上报实际完成数量、耗时、不良数 | 执行 |
| 工序过站 | 在制品（WIP）经过某道工序的记录 | 执行 |
| SN | 序列号，单品唯一标识 | 追溯 |
| 批次 | 一批相同条件生产的物料标识 | 追溯 |
| OEE | 设备综合效率 = 可用率 × 性能率 × 质量率 | 设备 |
| IQC/IPQC/FQC | 来料检/过程检/成品检 | 质量 |
| APS | 高级计划与排程，有限能力约束下的优化排产 | 计划 |

## MES vs ERP 边界

| 维度 | ERP | MES |
|------|-----|-----|
| 管什么 | 计划层：订单、采购、财务 | 执行层：工单、排产、报工 |
| 时间粒度 | 天/周/月 | 分钟/秒 |
| 数据来源 | 人工录入为主 | 设备采集 + 人工上报 |
| 核心实体 | SalesOrder, PurchaseOrder | WorkOrder, JobCard, Operation |
| 典型集成 | → MES（下发生产订单） | ← ERP（接收订单） / → ERP（回报完工） |

## 核心表结构速查

| 表名 | 用途 | 关键字段 |
|------|------|---------|
| `mes_work_order` | 生产工单 | order_no, bom_id, qty, status, plan_start, plan_end |
| `mes_operation` | 工序定义 | op_name, workstation_id, seq_no, std_time |
| `mes_job_card` | 作业卡（工序级执行） | work_order_id, operation_id, status, actual_qty, scrap_qty |
| `mes_report` | 报工记录 | job_card_id, operator_id, start_time, end_time, qty |
| `mes_quality_inspection` | 质检记录 | inspection_type(IQC/IPQC/FQC), result, inspector |
| `mes_trace_batch` | 批次追溯 | batch_no, material_id, work_order_id, parent_batch_no |
| `mes_trace_sn` | SN 追溯 | serial_no, batch_no, material_id, status |
| `mes_equipment` | 设备档案 | equip_code, equip_name, workstation_id, status |

## 状态机速查

### 工单状态

```
已创建 → 已下达 → 已领料 → 生产中 → 已完工 → 已入库
                ↓              ↓           ↓
            已暂停          已暂停       已关闭
```

### 报工状态

```
待开始 → 进行中 → 已完成
              ↓
           已暂停/异常
```

## 与 ERP 的集成接口

| 方向 | 接口/事件 | 数据 |
|------|----------|------|
| ERP → MES | REST / MQ: `production_order.created` | 工单号, BOM, 数量, 计划日期 |
| MES → ERP | REST / MQ: `work_order.completed` | 工单号, 完工数量, 不良数, 耗时 |
| MES → ERP | REST / MQ: `stock_entry.manufacture` | 成品 SN/批次, 入库仓库, 数量 |
| ERP → MES | REST / MQ: `material.reserved` | 原料编码, 预留数量, 仓库 |

## 高频面试速答

1. **Q**: MES 和 ERP 的核心区别？ → **A**: ERP 管"计划"（天级），MES 管"执行"（分钟级）。ERP 告诉你"本周要生产 1000 台"，MES 告诉你"3 号线当前正在生产第 387 台，已耗时 2h15m"。

2. **Q**: BOM 多级展开是什么？ → **A**: 成品 BOM 引用半成品子件，半成品又有自己的 BOM。MRP 展开时逐级爆炸，计算出每层原材料的净需求量。ERPNext 支持原生多级 BOM 爆炸。

3. **Q**: 正向追溯和反向追溯有什么区别？ → **A**: 正向：原料批次 → 哪些成品用了它（供应商投诉时用）。反向：成品 SN → 它用了哪些原料批次（客户退货时用）。两者共享同一套追溯数据模型。

4. **Q**: 什么是 Outbox 模式？ → **A**: MES 完工后先写本地消息表（Outbox），再由后台线程投递到 MQ，保证"写库 + 发消息"的原子性，避免数据不一致。

5. **Q**: OEE 怎么算？ → **A**: OEE = 可用率（实际运行时间/计划运行时间）× 性能率（实际产出/理论产出）× 质量率（良品数/总产出）。世界级 OEE > 85%。
