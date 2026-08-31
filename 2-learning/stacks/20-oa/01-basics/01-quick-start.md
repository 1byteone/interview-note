# OA 快速上手 — OA 系统定位与 RBAC 权限模型

> 本篇是 OA 教程的起点。搞清楚 OA 系统的核心模块和权限体系设计。

---

## 一、OA 系统定位

### 1.1 OA 是什么

OA（Office Automation）= 办公自动化系统，核心解决三件事：
1. **审批流**：请假、报销、采购、合同等业务的电子化审批
2. **权限控制**：谁能看什么、能操作什么、能看到哪些数据
3. **协作效率**：文档管理、消息通知、日程安排

### 1.2 OA vs ERP vs MES

| 维度 | OA | ERP | MES |
|------|-----|-----|-----|
| 核心价值 | 审批效率 | 资源计划 | 生产执行 |
| 核心用户 | 全员 | 财务/采购/销售 | 车间人员 |
| 核心功能 | 审批流+权限+文档 | 订单+库存+财务 | 工单+排产+报工 |
| 技术重点 | 工作流引擎 | 业务建模 | 实时采集 |

### 1.3 OA 核心模块

```
┌─────────────────────────────────────────────────────┐
│                    OA 核心模块                        │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ 权限管理  │ │ 审批流程  │ │ 表单管理  │           │
│  │ RBAC     │ │ Flowable │ │ 动态表单  │           │
│  └──────────┘ └──────────┘ └──────────┘           │
│                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ 文档管理  │ │ 消息通知  │ │ 日程管理  │           │
│  │ MinIO    │ │ 站内信    │ │ 日历     │           │
│  └──────────┘ └──────────┘ └──────────┘           │
│                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ 考勤管理  │ │ 公告管理  │ │ 系统管理  │           │
│  │ 打卡     │ │ 发布     │ │ 用户/角色 │           │
│  └──────────┘ └──────────┘ └──────────┘           │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## 二、RBAC 权限模型

### 2.1 什么是 RBAC

RBAC（Role-Based Access Control）= 基于角色的访问控制。

核心思想：**不直接给用户分配权限，而是通过"角色"做中介**。

```
用户(User) ──→ 角色(Role) ──→ 权限(Permission)
   │              │              │
 张三          部门经理      审批请假单
 李四          普通员工      提交请假单
 王五          HR           查看考勤报表
```

### 2.2 RBAC 三层模型

| 层 | 实体 | 说明 |
|----|------|------|
| 用户层 | User | 系统使用者 |
| 角色层 | Role | 权限集合（如"部门经理""HR""普通员工"） |
| 权限层 | Permission | 具体操作（如"审批请假""查看报表"） |

### 2.3 权限的三个维度

```
权限体系
├── 菜单权限：能看到哪些页面
│   └── 如：部门经理能看到"审批管理"菜单
├── 按钮权限：能操作哪些按钮
│   └── 如：部门经理能点"通过"按钮，普通员工不能
└── 数据权限：能看到哪些数据
    └── 如：部门经理只能看到本部门的请假单
```

### 2.4 RBAC 数据库设计

```sql
-- 用户表
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    dept_id BIGINT COMMENT '所属部门',
    status TINYINT DEFAULT 1
);

-- 角色表
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    data_scope VARCHAR(16) DEFAULT 'SELF'
    -- ALL:全部 DEPT:本部门 DEPT_TREE:本部门及下级 SELF:仅本人 CUSTOM:自定义
);

-- 权限表（菜单/按钮）
CREATE TABLE sys_permission (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    perm_name VARCHAR(64) NOT NULL,
    perm_type VARCHAR(16) NOT NULL, -- MENU/BUTTON
    path VARCHAR(256),              -- 前端路由
    perms VARCHAR(128),             -- 后端权限标识 如 system:user:list
    icon VARCHAR(64)
);

-- 用户-角色关联表
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 角色-权限关联表
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

-- 部门表
CREATE TABLE sys_dept (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    dept_name VARCHAR(64) NOT NULL,
    leader_id BIGINT COMMENT '部门负责人',
    sort_order INT DEFAULT 0
);
```

---

## 三、数据权限实现

### 3.1 五种数据权限范围

| 范围 | 说明 | 适用角色 |
|------|------|---------|
| 全部数据（ALL） | 看所有数据 | 超级管理员 |
| 本部门及下级（DEPT_TREE） | 看自己和子部门数据 | 总监级 |
| 本部门数据（DEPT） | 只看本部门数据 | 部门经理 |
| 仅本人数据（SELF） | 只看自己创建的数据 | 普通员工 |
| 自定义（CUSTOM） | 按角色指定可见部门 | HR/财务 |

### 3.2 MyBatis 拦截器实现

```java
// 数据权限拦截器
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class DataScopeInterceptor implements Interceptor {
    @Override
    public Object invoke(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = handler.getBoundSql();
        String originalSql = boundSql.getSql();

        // 获取当前用户的数据权限范围
        DataScope scope = getCurrentUserScope();
        String dataScopeSql = scope.generateSql();

        // 在原始 SQL 后追加数据权限条件
        String newSql = originalSql + " AND " + dataScopeSql;

        // 反射替换 SQL
        Field sqlField = BoundSql.class.getDeclaredField("sql");
        sqlField.setAccessible(true);
        sqlField.set(boundSql, newSql);

        return invocation.proceed();
    }
}
```

---

## 四、工作流引擎概念

### 4.1 为什么需要工作流引擎

没有工作流引擎时：
```java
// 硬编码审批逻辑
if (leaveRequest.getDays() <= 3) {
    leaveRequest.setStatus("部门经理审批");
} else if (leaveRequest.getDays() <= 7) {
    leaveRequest.setStatus("HR审批");
} else {
    leaveRequest.setStatus("总经理审批");
}
```

问题：审批流程变更时需要改代码、重新部署。

有工作流引擎时：
```java
// 流程定义在数据库/配置文件中
processEngine.getRepositoryService()
    .createDeployment()
    .addClasspathResource("leave-process.bpmn20.xml")
    .deploy();

// 启动流程实例
processEngine.getRuntimeService()
    .startProcessInstanceByKey("leave-process", variables);
```

优势：审批流程变更只需修改 BPMN 文件，无需改代码。

### 4.2 主流工作流引擎对比

| 引擎 | 特点 | 推荐度 |
|------|------|--------|
| Flowable | 轻量、高性能、社区活跃 | ⭐⭐⭐⭐⭐ |
| Activiti | 老牌、功能全、但维护变慢 | ⭐⭐⭐ |
| Camunda | 企业级、支持 BPMN + CMMN + DMN | ⭐⭐⭐⭐ |

**推荐**：新项目用 Flowable，已有 Activiti 项目可以继续用。

---

## 五、学习建议

| 阶段 | 文件 | 建议时长 |
|------|------|---------|
| ① 概念 | 01-basics/01-quick-start.md | 1 天 |
| ② 建模 | 02-core/01-domain-model.md | 2 天 |
| ③ 架构 | 02-core/02-architecture.md | 1 天 |
| ④ 数据库 | 02-core/03-data-design.md | 1 天 |
| ⑤ Flowable | 03-advanced/01-flowable-deep.md | 2 天 |
| ⑥ 集成 | 03-advanced/02-integration.md | 1 天 |
| ⑦ AI 增强 | 03-advanced/03-ai-enhancement.md | 1 天 |
| ⑧ 项目剖析 | 04-project/01-open-source-review.md | 2 天 |
| ⑨ 简历包装 | 04-project/02-resume-project.md | 1 天 |
| ⑩ 面试准备 | 05-interview/* | 2 天 |

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← README](../README.md) | [📚 20-OA](../README.md) | [核心业务建模 →](../02-core/01-domain-model.md) |
