# OA 速查表（Cheat Sheet）

> 一页掌握 OA 核心概念、权限模型与工作流设计。

---

## 核心概念对照

| 概念 | 一句话 | 层级 |
|------|--------|------|
| OA | 办公自动化系统，管理审批、权限、文档、消息 | 应用 |
| RBAC | 基于角色的访问控制，用户→角色→权限三层模型 | 权限 |
| 数据权限 | 控制用户能看到哪些数据（按部门/岗位/自定义） | 权限 |
| BPMN 2.0 | 业务流程建模标准，定义流程、网关、事件、任务 | 标准 |
| 流程定义 | 模板级：描述审批流程的走向（审批人、条件、分支） | 工作流 |
| 流程实例 | 运行级：一次具体的审批（如张三的请假单） | 工作流 |
| 任务 | 流程中的一个审批节点，等待某人处理 | 工作流 |
| 审批人分配 | 指定谁来审批：角色/部门负责人/发起人自选/会签 | 工作流 |

## RBAC 三层模型

```
用户(User) ──→ 角色(Role) ──→ 权限(Permission)
                 │
                 ├── 菜单权限（能看到哪些页面）
                 ├── 按钮权限（能操作哪些按钮）
                 └── 数据权限（能看到哪些数据）
```

## 数据权限范围

| 范围 | 说明 | SQL 实现 |
|------|------|---------|
| 全部数据 | 超级管理员 | 无过滤 |
| 本部门数据 | 只看自己部门 | `WHERE dept_id = ?` |
| 本部门及下级 | 看自己和子部门 | `WHERE dept_id IN (递归查询)` |
| 仅本人数据 | 只看自己创建的 | `WHERE create_by = ?` |
| 自定义 | 按角色指定可见部门 | `WHERE dept_id IN (指定列表)` |

## Flowable 核心 API

| API | 用途 | 常用方法 |
|-----|------|---------|
| `ProcessDefinition` | 流程定义（模板） | `repositoryService.createProcessDefinitionQuery()` |
| `ProcessInstance` | 流程实例（运行中） | `runtimeService.startProcessInstanceByKey()` |
| `Task` | 审批任务 | `taskService.createTaskQuery()` / `taskService.complete()` |
| `HistoricProcessInstance` | 历史流程 | `historyService.createHistoricProcessInstanceQuery()` |

## 审批模式速查

| 模式 | 说明 | Flowable 实现 |
|------|------|--------------|
| 串行审批 | A→B→C 依次审批 | 多个 UserTask 顺序排列 |
| 并行审批 | A、B、C 同时审批 | Parallel Gateway 分支 |
| 会签（多人通过） | 所有人都要通过 | `CompletionCondition: ${nrOfCompletedInstances == nrOfInstances}` |
| 或签（一人通过） | 任意一人通过即可 | `CompletionCondition: ${nrOfCompletedInstances >= 1}` |
| 条件分支 | 按金额/类型走不同审批 | Exclusive Gateway + 条件表达式 |

## OA 与 ERP/MES 集成接口

| 审批事件 | 触发动作 | 目标系统 |
|---------|---------|---------|
| 采购审批通过 | 创建采购订单 | ERP |
| 付款审批通过 | 执行付款 | ERP |
| 工单审批通过 | 下达工单 | MES |
| 请假审批通过 | 扣减薪资 | ERP/HR |
| 合同审批通过 | 归档合同 | 文档系统 |

## 高频面试速答

1. **Q**: RBAC 和 ABAC 的区别？ → **A**: RBAC 按角色控制（粗粒度），ABAC 按属性控制（细粒度，如"只能看本部门数据"）。实际项目通常 RBAC + 数据权限混合使用。

2. **Q**: Flowable 和 Activiti 的区别？ → **A**: Flowable 是 Activiti 的分支（Alfresco 团队离开后创建），API 更轻量、性能更好、社区更活跃。新项目推荐 Flowable。

3. **Q**: 会签和或签的区别？ → **A**: 会签 = 所有人都要通过（如董事会决议），或签 = 任意一人通过即可（如部门负责人审批）。

4. **Q**: 审批流中如何实现"条件分支"？ → **A**: 用排他网关（Exclusive Gateway），配置条件表达式（如 `${amount > 10000}` 走总经理审批）。

5. **Q**: 数据权限怎么实现？ → **A**: 两种方案：① SQL 拦截器（MyBatis 拦截器自动拼接 WHERE 条件）；② 注解 + AOP（`@DataScope(deptAlias="d")` 注解声明数据范围）。
