# OA 开源项目剖析 — JeecgBoot · RuoYi · Flowable

> 本篇深度剖析三个代表性开源 OA 项目/框架，提炼架构亮点。

---

## 一、JeecgBoot — 企业级 AI 低代码平台

### 1.1 项目概况

| 维度 | 信息 |
|------|------|
| GitHub | [jeecgboot/JeecgBoot](https://github.com/jeecgboot/JeecgBoot) |
| Stars | 47.6k |
| 技术栈 | Spring Boot + MyBatis-Plus + Shiro/JWT + Vue3 + Ant Design |
| 定位 | 企业级 AI 低代码开发平台 |
| 核心能力 | 代码生成 + 零代码搭建 + BPM 工作流 + AI 知识库 |

### 1.2 架构亮点

```
┌─────────────────────────────────────────────────────┐
│                JeecgBoot 架构                        │
├─────────────────────────────────────────────────────┤
│                                                     │
│  前端：Vue3 + Vben Admin + Ant Design Vue            │
│       ├── 低代码表单设计器                            │
│       ├── 在线流程设计器                              │
│       └── 报表大屏                                   │
│                                                     │
│  后端：Spring Boot + MyBatis-Plus                    │
│       ├── 代码生成器（一键生成 CRUD）                  │
│       ├── Flowable 工作流集成                         │
│       ├── AI 应用平台（聊天/知识库/流程编排）           │
│       └── MCP 插件支持                               │
│                                                     │
│  支持：单体 / 微服务 / Docker                         │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 1.3 重点学习

1. **低代码表单设计器**：拖拽式生成表单，字段类型丰富
2. **Flowable 深度集成**：页面配置流程转向，简化 BPM 开发
3. **AI Skills**：一句话画流程、设计表单、生成报表
4. **代码生成器**：根据数据库表一键生成前后端代码

---

## 二、RuoYi（若依）— 权限管理框架

### 2.1 项目概况

| 维度 | 信息 |
|------|------|
| 官网 | [ruoyi.vip](https://www.ruoyi.vip/) |
| 技术栈 | Spring Boot + MyBatis + Shiro + Thymeleaf + Vue |
| 定位 | 基于 Spring Boot 的权限管理系统 |
| 特点 | 界面简洁、文档齐全、社区活跃 |

### 2.2 RuoYi Office（工作流版）

基于 Spring Cloud Alibaba + Vue 3 + Vben Admin 构建：
- OA + HRM + CRM + ERP 等 14 大业务模块
- Flowable 工作流引擎深度集成
- 支持在线流程设计器

### 2.3 重点学习

1. **RBAC 权限体系**：RuoYi 的权限设计非常完整，是学习 RBAC 的最佳实践
2. **数据权限**：支持按部门/岗位/自定义数据范围
3. **代码生成器**：一键生成 Controller/Service/Mapper/Vue
4. **Flowable 集成**：RuoYi Office 版本的 BPM 落地经验

---

## 三、Flowable — 工作流引擎

### 3.1 引擎对比

| 维度 | Flowable | Activiti | Camunda |
|------|---------|---------|---------|
| 维护状态 | 活跃 | 变慢 | 活跃 |
| 性能 | 高 | 中 | 中 |
| 文档 | 完善 | 完善 | 完善 |
| 社区 | 活跃 | 一般 | 活跃 |
| 版本 | 6.8+ | 5.x/6.x | 7.x |
| 推荐 | ✅ 新项目首选 | ⚠️ 老项目继续用 | ✅ 企业级 |

### 3.2 核心特性

| 特性 | 说明 |
|------|------|
| BPMN 2.0 | 完整支持流程建模标准 |
| CMMN | 案例管理模型（条件驱动的流程） |
| DMN | 决策表（规则引擎） |
| 事件注册 | 事件驱动架构支持 |
| REST API | HTTP 接口操作流程 |
| Spring Boot | 无缝集成 Spring Boot |

---

## 四、推荐学习路径

```
1. 先学 RuoYi 的 RBAC 权限体系（打基础）
        ↓
2. 再学 Flowable 核心 API（掌握工作流）
        ↓
3. 研究 JeecgBoot 的 Flowable 集成（落地经验）
        ↓
4. 用 Spring Boot + Flowable 自己实现 OA 审批系统
```

---

## 五、三项目对比

| 维度 | JeecgBoot | RuoYi | Flowable |
|------|-----------|-------|---------|
| 定位 | 低代码平台 | 权限框架 | 工作流引擎 |
| 适用场景 | 快速开发企业应用 | 权限管理基础 | 审批流程引擎 |
| 学习价值 | 低代码 + AI + BPM | RBAC + 数据权限 | BPMN + 流程引擎 |
| 简历参考 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← AI 增强](../03-advanced/03-ai-enhancement.md) | [📚 20-OA](../../README.md) | [简历项目包装 →](./02-resume-project.md) |
