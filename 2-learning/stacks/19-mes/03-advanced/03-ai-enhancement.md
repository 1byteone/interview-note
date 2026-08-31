# MES AI 增强 — 智能排产 · 质量根因 · 预测性维护

> 本篇探讨 AI/LLM 在 MES 中的应用场景：智能排产（APS）、质量根因分析、预测性维护。
> 这些是制造业数字化的"加分项"，也是简历项目的差异化亮点。

---

## 一、AI 在 MES 中的三大场景

```
┌─────────────────────────────────────────────────────────────────────┐
│                    MES AI 增强全景                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐   │
│  │ 智能排产（APS）    │ │ 质量根因分析       │ │ 预测性维护        │   │
│  │                  │ │                  │ │                  │   │
│  │ 输入：            │ │ 输入：            │ │ 输入：            │   │
│  │ · 工单列表        │ │ · 质检数据        │ │ · 设备运行数据    │   │
│  │ · 设备产能        │ │ · 设备日志        │ │ · 历史故障记录    │   │
│  │ · 交期约束        │ │ · 工艺参数        │ │ · 保养记录        │   │
│  │ · 物料齐套        │ │ · 环境数据        │ │ · 环境数据        │   │
│  │                  │ │                  │ │                  │   │
│  │ 输出：            │ │ 输出：            │ │ 输出：            │   │
│  │ · 排产甘特图      │ │ · 异常根因        │ │ · 故障预测        │   │
│  │ · 设备分配方案     │ │ · 改进建议        │ │ · 保养建议        │   │
│  │ · 物料需求计划     │ │ · 趋势预警        │ │ · 备件预警        │   │
│  └──────────────────┘ └──────────────────┘ └──────────────────┘   │
│                                                                     │
│  技术方案：                                                          │
│  · 传统：启发式算法 / 约束求解 / SPC 统计                             │
│  · AI 增强：LLM Agent + RAG（知识库）+ 传统算法混合                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、智能排产（AI APS）

### 2.1 传统排产 vs AI 排产

| 维度 | 传统 APS | AI APS |
|------|---------|--------|
| 算法 | 启发式规则 + 约束求解 | LLM Agent + 约束建模 + 求解器 |
| 输入 | 固定约束条件 | 自然语言描述 + 结构化数据 |
| 输出 | 甘特图 + 排产表 | 甘特图 + 排产表 + 人可读解释 |
| 调整 | 重新运行求解器 | 自然语言指令调整（"把 3 号线的紧急订单提前"） |
| 适用 | 大规模离散制造 | 中小企业 + 灵活调度 |

### 2.2 AI APS 架构

```
用户输入："本周要生产 500 台智能手表，3 号线优先，
          屏幕模组库存只有 200 个，需要先采购"
          ↓
┌─────────────────────────────────────────────────────┐
│                LLM Agent（排产助手）                  │
├─────────────────────────────────────────────────────┤
│                                                     │
│  1. 理解意图                                         │
│     · 解析自然语言 → 结构化约束                        │
│     · 产品: SW-001, 数量: 500, 优先级: 3号线          │
│     · 瓶颈: 屏幕模组库存不足                          │
│                                                     │
│  2. 查询上下文（RAG）                                 │
│     · 从 MES 数据库查询设备产能                        │
│     · 从 ERP 查询屏幕模组采购到货时间                   │
│     · 从知识库查询历史排产经验                          │
│                                                     │
│  3. 生成排产方案                                      │
│     · 调用约束求解器（OR-Tools / OptaPlanner）         │
│     · 生成甘特图数据                                   │
│     · 生成人可读的排产说明                              │
│                                                     │
│  4. 输出                                              │
│     · 甘特图 JSON（前端渲染）                          │
│     · 排产说明："建议分两批生产..."                     │
│     · 物料需求："屏幕模组需紧急采购 300 个"              │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 2.3 排产约束建模（示例）

```python
# 用 LLM 生成 OR-Tools 约束模型
from ortools.sat.python import cp_model

def create_scheduling_model(orders, machines, horizon):
    model = cp_model.CpModel()

    # 决策变量：每个工单在每台设备上的开始时间
    start_vars = {}
    for order in orders:
        for machine in machines:
            start_vars[(order.id, machine.id)] = model.NewIntVar(
                0, horizon, f'start_{order.id}_{machine.id}'
            )

    # 约束 1：每台设备同一时间只处理一个工单（互斥）
    for machine in machines:
        intervals = []
        for order in orders:
            interval = model.NewIntervalVar(
                start_vars[(order.id, machine.id)],
                order.duration,
                start_vars[(order.id, machine.id)] + order.duration,
                f'interval_{order.id}_{machine.id}'
            )
            intervals.append(interval)
        model.AddNoOverlap(intervals)

    # 约束 2：交期优先（越紧急的越先排）
    for order in orders:
        model.Add(
            start_vars[(order.id, order.preferred_machine)] <= order.due_date
        ).OnlyEnforceIf(order.is_urgent)

    # 约束 3：物料齐套（原料到位才能开工）
    for order in orders:
        if not order.materials_ready:
            model.Add(
                start_vars[(order.id, order.preferred_machine)] >= order.material_arrival_date
            )

    # 目标：最小化总延迟
    delays = []
    for order in orders:
        delay = model.NewIntVar(0, horizon, f'delay_{order.id}')
        model.AddMaxEquality(delay, [
            start_vars[(order.id, order.preferred_machine)] + order.duration - order.due_date,
            0
        ])
        delays.append(delay)
    model.Minimize(sum(delays))

    return model
```

---

## 三、质量根因分析

### 3.1 场景描述

当 IPQC 检测到不良率突增时，传统方式靠人工排查设备日志、工艺参数、操作记录，耗时长且依赖经验。

AI 方案：LLM Agent 自动分析多维数据，定位根因。

### 3.2 AI 质量分析架构

```
质量异常事件
    ↓
┌─────────────────────────────────────────────────────┐
│                质量根因分析 Agent                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Step 1: 收集上下文                                  │
│  · 查询质检数据：不良类型、数量、时间分布              │
│  · 查询设备日志：异常停机、温度、压力                  │
│  · 查询操作记录：换班、操作员、工艺参数                │
│  · 查询物料批次：供应商变更、批次质量                  │
│                                                     │
│  Step 2: LLM 分析                                    │
│  · 将多维数据喂给 LLM                                │
│  · Prompt: "分析以下质检异常数据，定位根因..."          │
│  · LLM 结合 RAG 知识库（历史案例、工艺文档）           │
│                                                     │
│  Step 3: 输出根因报告                                │
│  · 根因：设备 EQ-003 温度波动导致焊接不良              │
│  · 证据：温度日志显示 14:00-15:00 波动 ±5°C          │
│  · 建议：检查温控器、更换加热元件                      │
│  · 影响范围：WO-20260831-001 ~ WO-20260831-005       │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 3.3 质量分析 Prompt 示例

```python
quality_analysis_prompt = """
你是一位制造业质量工程师。请分析以下质检异常数据，定位根因。

## 异常概况
- 工单: {work_order_no}
- 产品: {product_name}
- 异常时间: {anomaly_time}
- 不良率: {defect_rate}%（正常值: {normal_rate}%）
- 不良类型: {defect_type}

## 设备日志（异常时段）
{device_logs}

## 工艺参数
{process_params}

## 操作记录
{operation_records}

## 物料批次信息
{material_info}

请输出：
1. 最可能的根因（按可能性排序）
2. 每个根因的支撑证据
3. 建议的处置措施
4. 影响范围评估
"""
```

---

## 四、预测性维护

### 4.1 场景描述

传统维护：定期保养（日/周/月）或故障后维修。
预测性维护：基于设备运行数据，预测故障发生时间，提前安排维护。

### 4.2 预测性维护数据流

```
设备传感器数据 → 时序库(TDengine) → 特征工程 → ML 模型 → 故障预测
                                                          ↓
                                              ┌───────────────────┐
                                              │ 未来 7 天故障概率   │
                                              │ > 80% → 告警      │
                                              │ > 60% → 预警      │
                                              │ < 60% → 正常      │
                                              └───────────────────┘
```

### 4.3 特征工程

| 特征 | 来源 | 说明 |
|------|------|------|
| 温度趋势 | 传感器 | 近 24h 温度均值、方差、趋势 |
| 振动频谱 | 传感器 | FFT 分析，异常频率成分 |
| 运行时长 | 设备日志 | 距上次保养的运行时长 |
| 历史故障 | 故障记录 | 同型号设备的平均故障间隔（MTBF） |
| 环境温湿度 | 环境传感器 | 车间环境对设备的影响 |

---

## 五、AI 增强的简历包装

### 亮点话术

> "负责 MES 系统 AI 增强模块开发，实现智能排产（基于 OR-Tools 约束求解 + LLM 自然语言交互）和质量根因分析（LLM Agent + 多维数据关联分析），不良率定位时间从 2 小时缩短至 10 分钟。"

### 技术栈

```
LLM: GPT-4o / DeepSeek-V3 / Qwen-2.5
Agent: LangChain / LangGraph
RAG: ChromaDB / Milvus
求解器: Google OR-Tools / OptaPlanner
时序库: TDengine / InfluxDB
可视化: ECharts 甘特图
```

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← ERP 集成](./02-integration.md) | [📚 19-MES](../../README.md) | [开源项目剖析 →](../04-project/01-open-source-review.md) |
