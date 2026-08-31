# ERP 开源项目剖析 — ERPNext · SpringERP · Meridian ERP

---

## 一、ERPNext — 最成熟的开源 ERP

| 维度 | 信息 |
|------|------|
| GitHub | [frappe/erpnext](https://github.com/frappe/erpnext) |
| Stars | 22k+ |
| 技术栈 | Python/Frappe Framework + MariaDB |
| 许可证 | GPL-3.0 |

### 核心模块

| 模块 | 功能 | 学习价值 |
|------|------|---------|
| CRM | 客户管理、销售漏斗 | 客户关系建模 |
| Sales | 报价、订单、发货、发票 | O2C 全链路 |
| Purchasing | 采购申请、订单、收货、发票 | P2P 全链路 |
| Inventory | 仓库、库存、批次/SN | 库存管理模型 |
| Manufacturing | BOM、工单、MRP、Job Card | 制造核心 |
| Accounting | 总账、应收、应付、成本 | 财务建模 |

### 重点学习

1. **BOM 设计**：多级 BOM + 版本控制 + 成本 Rollup
2. **MRP 逻辑**：Production Plan 的需求爆炸 + 净需求计算
3. **Job Card**：工序级执行卡片，平板操作
4. **成本核算**：标准成本 + 实际成本对比

---

## 二、SpringERP — Java 后端参考

| 维度 | 信息 |
|------|------|
| GitHub | [AronnoDIU/SpringERP](https://github.com/AronnoDIU/SpringERP) |
| 技术栈 | Spring Boot 3 + JWT + JPA + MySQL + React |
| 模块 | Customer, Supplier, Product, Invoice, Employee, Accounting |

### 重点学习

1. **Spring Boot 3 实现**：Java 后端 ERP 的参考实现
2. **JWT 认证**：无状态认证方案
3. **Docker 部署**：容器化部署实践

---

## 三、Meridian ERP — Spring Modulith 实践

| 维度 | 信息 |
|------|------|
| GitHub | [msiShariful/meridian-erp](https://github.com/msiShariful/meridian-erp) |
| 技术栈 | Java 21 + Spring Boot 3.4 + Spring Modulith + JPA + Thymeleaf |
| 模块 | CRM, HRM, Inventory, E-Commerce, Accounting, Procurement, Projects |

### 重点学习

1. **Spring Modulith**：模块化单体架构的最佳实践
2. **模块间事件通信**：ApplicationEvent + Outbox
3. **演进路径**：单体 → Modulith → 微服务

---

## 四、推荐学习路径

```
1. ERPNext（理解完整 ERP 业务模型，不看代码看业务）
        ↓
2. SpringERP（Java 实现参考，学习技术架构）
        ↓
3. Meridian ERP（学习 Spring Modulith 模块化设计）
        ↓
4. 自己用 Spring Boot 实现简化版 ERP
```

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← AI 增强](../03-advanced/03-ai-enhancement.md) | [📚 21-ERP](../../README.md) | [简历项目包装 →](./02-resume-project.md) |
