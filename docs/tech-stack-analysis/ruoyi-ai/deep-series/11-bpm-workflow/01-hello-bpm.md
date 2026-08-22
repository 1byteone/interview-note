# 工作流引擎入门：Warm-Flow 第一个审批流程

> **深度系列 | 第11篇** | Level 1 入门
>
> 本篇目标：用最少的代码，搭起一个完整的请假审批流程。从流程定义到发起审批、逐级审核、驳回退回，跑通 Warm-Flow 的全链路。

---

## 一、项目背景

### 1.1 工作流是什么

在企业信息化系统中，**审批流程**无处不在——请假申请、报销审批、合同会签、采购验收……每个流程都涉及多个角色、多个环节，且流程规则可能随时变化。

传统做法是在代码中硬编码状态机：

```java
// 硬编码的审批状态机 —— 每个流程都要写一套，无法复用
if (leaveDays <= 3) {
    // 部门经理审批
    if (deptApproved) {
        status = "APPROVED"; // 直接通过
    }
} else if (leaveDays <= 7) {
    // 部门经理审批 → 人事审批
    if (deptApproved && hrApproved) {
        status = "APPROVED";
    }
} else {
    // 部门经理 → 人事 → 总经理
    if (deptApproved && hrApproved && gmApproved) {
        status = "APPROVED";
    }
}
```

这种硬编码的问题很明显：
- **流程变更需要改代码**：请假天数阈值从7天改成10天，要改Java代码、重新部署
- **流程逻辑散落在业务代码中**：审批逻辑和业务逻辑耦合，难以维护
- **无法可视化**：没有流程图，新同事理解流程全靠读代码
- **缺乏通用能力**：每个流程都要重新实现驳回、转办、催办等功能

**工作流引擎**就是为解决这些问题而生——它把流程定义从代码中剥离出来，用专门的描述语言（BPMN/JSON）定义流程，引擎负责流程的推进、状态管理、任务分配。业务代码只需要关注"审批通过后做什么"，而不是"怎么走完审批流程"。

### 1.2 为什么选 Warm-Flow 而非 Activiti/Flowable

| 维度 | Activiti/Flowable | Warm-Flow |
|------|-------------------|-----------|
| **部署复杂度** | 需要部署专门的流程引擎服务，依赖 Activiti 自己的表结构（50+ 张表） | 轻量级 JAR 包，只需 4 张核心表，嵌入业务应用即可 |
| **学习曲线** | 需要学习 BPMN 2.0 XML 规范、流程设计器、大量 API | 核心 API 只有 10 个左右，半小时上手 |
| **集成成本** | 需要配置 ProcessEngine、RepositoryService、RuntimeService 等一堆 Bean | 一行 `@EnableWarmFlow` 即可启用 |
| **数据库依赖** | 强依赖 Activiti 的 50+ 张表，与业务表隔离 | 只有 4 张表，可与业务表放在同一数据库 |
| **Spring Boot 适配** | 需要额外适配，配置较多 | 原生支持 Spring Boot，自动配置 |
| **社区活跃度** | 较高（但太重） | 国内新兴，轻量路线 |
| **适合场景** | 大型企业级复杂流程（千人以上、上百种流程） | 中小型项目、微服务中嵌入使用 |

**ruoyi-ai 选型原因：** 项目是 AI 应用平台，工作流主要用于审批管理（知识库发布审批、模型申请审批等），流程复杂度不高。Warm-Flow 的"嵌入式、轻量级"特点与项目高度契合——不需要部署独立的流程引擎服务，减少运维成本。

### 1.3 Warm-Flow 在 ruoyi-ai 中的角色

```
┌─────────────────────────────────────────────────┐
│                  ruoyi-ai 应用                    │
│                                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────┐│
│  │ 知识库管理    │  │ 模型管理     │  │ 系统管理   ││
│  │ 发布审批流程  │  │ 上线审批流程  │  │ 请假审批   ││
│  └──────┬──────┘  └──────┬──────┘  └─────┬─────┘│
│         │               │               │        │
│         └───────────────┼───────────────┘        │
│                         ▼                        │
│              ┌──────────────────┐                │
│              │   Warm-Flow 引擎  │                │
│              │  (嵌入式, 4张表)   │                │
│              └──────────────────┘                │
│                         │                        │
│                         ▼                        │
│              ┌──────────────────┐                │
│              │    MySQL 数据库   │                │
│              │  (flow_xxx 表)    │                │
│              └──────────────────┘                │
└─────────────────────────────────────────────────┘
```

---

## 二、核心概念

### 2.1 BPMN 核心元素

BPMN（Business Process Model and Notation）是业务流程建模的标准符号。但 Warm-Flow 不要求你学习 BPMN XML，它用 **JSON 格式**定义流程，更直观。

一个审批流程包含以下核心元素：

| 元素 | 图标 | 说明 | Warm-Flow 对应 |
|------|------|------|----------------|
| **开始事件** | ○ | 流程的起点 | `type: 0` (开始节点) |
| **结束事件** | ● | 流程的终点 | `type: 3` (结束节点) |
| **用户任务** | □ | 需要人工审批的环节 | `type: 1` (审批节点) |
| **排他网关** | ◇ | 条件分支：根据条件走不同路径 | `type: 2` (条件节点) |
| **顺序流** | → | 节点之间的连线，决定流转方向 | `skipCondition` 表达式 |

### 2.2 Warm-Flow 核心表结构

Warm-Flow 只有 4 张核心表，设计非常精简：

```sql
-- =============================================
-- 1. flow_definition —— 流程定义表
-- 记录每个流程的"模板"（如：请假审批流程定义）
-- 一个流程定义可以发起多个流程实例
-- =============================================
CREATE TABLE flow_definition (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    flow_name   VARCHAR(100)  NOT NULL COMMENT '流程名称（如：请假审批）',
    flow_json   LONGTEXT      NOT NULL COMMENT '流程定义JSON（节点/连线/条件定义）',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    version     INT           NOT NULL DEFAULT 1 COMMENT '版本号（每次修改+1）',
    create_time DATETIME      NOT NULL COMMENT '创建时间',
    update_time DATETIME      DEFAULT NULL COMMENT '更新时间'
) COMMENT '流程定义表';

-- ---------------------------------------------
-- 核心字段说明：
-- flow_json 存储的是整个流程的 JSON 定义，包含：
-- - 所有节点（开始/审批/条件/结束）
-- - 所有连线（从哪里到哪里，什么条件下跳转）
-- - 每个节点的处理人、催办时间等配置
-- 相当于把 BPMN XML 换成了 JSON 格式
-- ---------------------------------------------

-- =============================================
-- 2. flow_instance —— 流程实例表
-- 记录每一次发起的流程（如：张三8月1日发起的请假申请）
-- 一个流程实例对应一次具体的审批过程
-- =============================================
CREATE TABLE flow_instance (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    definition_id   BIGINT        NOT NULL COMMENT '流程定义ID',
    business_id     VARCHAR(64)   DEFAULT NULL COMMENT '业务ID（关联业务表）',
    node_type       INT           NOT NULL COMMENT '当前节点类型：0-开始 1-审批 2-条件 3-结束',
    node_name       VARCHAR(100)  DEFAULT NULL COMMENT '当前节点名称',
    variable        TEXT          DEFAULT NULL COMMENT '流程变量（JSON格式，存储表单数据）',
    flow_status     VARCHAR(20)   NOT NULL COMMENT '流程状态：pending-审批中 pass-通过 reject-驳回 invalid-失效',
    create_by       VARCHAR(50)   NOT NULL COMMENT '发起人',
    create_time     DATETIME      NOT NULL COMMENT '创建时间',
    update_time     DATETIME      DEFAULT NULL COMMENT '更新时间'
) COMMENT '流程实例表';

-- ---------------------------------------------
-- 核心字段说明：
-- business_id 关联业务表（如：请假单ID）
-- variable 存储表单数据，审批人可见
-- flow_status 是流程的宏观状态
-- ---------------------------------------------

-- =============================================
-- 3. flow_task —— 流程任务表
-- 记录每个审批节点的任务（如：部门经理待审批）
-- 一个流程实例可能有多个任务（串行审批时）
-- =============================================
CREATE TABLE flow_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    instance_id     BIGINT        NOT NULL COMMENT '流程实例ID',
    definition_id   BIGINT        NOT NULL COMMENT '流程定义ID',
    node_name       VARCHAR(100)  NOT NULL COMMENT '节点名称（如：部门经理审批）',
    assignee        VARCHAR(50)   DEFAULT NULL COMMENT '处理人',
    task_status     VARCHAR(20)   NOT NULL COMMENT '任务状态：0-待处理 1-已处理 2-已驳回 3-已退回',
    form_data       TEXT          DEFAULT NULL COMMENT '表单数据（JSON格式）',
    opinion         VARCHAR(500)  DEFAULT NULL COMMENT '审批意见',
    create_time     DATETIME      NOT NULL COMMENT '创建时间',
    update_time     DATETIME      DEFAULT NULL COMMENT '更新时间'
) COMMENT '流程任务表';

-- ---------------------------------------------
-- 核心字段说明：
-- assignee 是当前任务的处理人
-- task_status 区分"待处理/已通过/已驳回/已退回"
-- opinion 记录审批人的意见文字
-- ---------------------------------------------

-- =============================================
-- 4. flow_skip —— 流程跳转记录表
-- 记录每一步的流转历史（谁从哪个节点到了哪个节点）
-- 用于追溯流程的完整执行路径
-- =============================================
CREATE TABLE flow_skip (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    instance_id     BIGINT        NOT NULL COMMENT '流程实例ID',
    definition_id   BIGINT        NOT NULL COMMENT '流程定义ID',
    from_node_name  VARCHAR(100)  NOT NULL COMMENT '来源节点名称',
    to_node_name    VARCHAR(100)  NOT NULL COMMENT '目标节点名称',
    skip_type       VARCHAR(20)   NOT NULL COMMENT '流转类型：pass-通过 reject-驳回 back-退回',
    assignee        VARCHAR(50)   DEFAULT NULL COMMENT '处理人',
    opinion         VARCHAR(500)  DEFAULT NULL COMMENT '审批意见',
    create_time     DATETIME      NOT NULL COMMENT '创建时间'
) COMMENT '流程跳转记录表';
```

### 2.3 流程状态机

一个审批流程的生命周期如下：

```
                    ┌──────────────┐
                    │  开始节点     │
                    │  (发起申请)   │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
              ┌────→│ 审批节点 A    │←────┐
              │     │ (部门经理)    │     │
              │     └──────┬───────┘     │
              │            │             │
              │      通过/驳回/退回      │ 重新提交
              │            │             │
              │            ▼             │
              │     ┌──────────────┐     │
              │     │ 审批节点 B    │─────┘
              │     │  (人事审批)   │  驳回/退回
              │     └──────┬───────┘
              │            │
              │            通过
              │            │
              │            ▼
              │     ┌──────────────┐
              └─────│  结束节点     │
                    │  (审批完成)   │
                    └──────────────┘
```

**状态流转规则：**

| 操作 | 说明 | 流程状态变化 | 任务状态变化 |
|------|------|-------------|-------------|
| **发起** | 申请人提交申请，创建第一个审批任务 | draft → pending | 创建待处理任务 |
| **通过** | 当前审批人同意，流程进入下一节点 | pending → pending | 待处理 → 已处理 |
| **驳回** | 当前审批人不同意，流程结束 | pending → reject | 待处理 → 已驳回 |
| **退回** | 退回给上一节点或指定节点重新处理 | pending → pending | 待处理 → 已退回 |
| **完成** | 最后一个审批节点通过，流程结束 | pending → pass | 待处理 → 已处理 |

---

## 三、从零搭建代码

### 3.1 项目结构

```
hello-warmflow/
├── pom.xml
├── src/main/java/com/example/flow/
│   ├── HelloFlowApplication.java          # Spring Boot 启动类
│   ├── config/
│   │   └── WarmFlowConfig.java            # Warm-Flow 配置类
│   ├── controller/
│   │   └── LeaveController.java            # 请假审批 REST API
│   ├── service/
│   │   └── LeaveService.java              # 请假审批核心业务
│   └── entity/
│       └── LeaveBill.java                 # 请假单实体
├── src/main/resources/
│   ├── application.yml                    # 应用配置
│   ├── flow-definition/
│   │   └── leave-approval.json            # 请假审批流程定义
│   └── schema.sql                         # 建表 SQL
└── src/test/java/com/example/flow/
    └── LeaveFlowTest.java                 # 流程测试
```

### 3.2 Maven 依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- pom.xml —— Warm-Flow 最简项目依赖 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>hello-warmflow</artifactId>
    <version>1.0.0</version>
    <name>hello-warmflow</name>
    <description>Warm-Flow 入门示例项目</description>

    <properties>
        <java.version>17</java.version>
        <warm-flow.version>1.4.0</warm-flow.version>
    </properties>

    <dependencies>
        <!-- ========== Spring Boot 基础 ========== -->

        <!-- Spring Boot Web 起步依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- ========== Warm-Flow 工作流引擎 ========== -->

        <!-- Warm-Flow 核心依赖（snailjob 团队出品） -->
        <dependency>
            <groupId>com.snailjava.warm-flow</groupId>
            <artifactId>warm-flow-core</artifactId>
            <version>${warm-flow.version}</version>
        </dependency>

        <!-- Warm-Flow Spring Boot Starter（自动配置） -->
        <dependency>
            <groupId>com.snailjava.warm-flow</groupId>
            <artifactId>warm-flow-spring-boot-starter</artifactId>
            <version>${warm-flow.version}</version>
        </dependency>

        <!-- ========== 数据库 ========== -->

        <!-- MySQL 驱动 -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- MyBatis-Plus（Warm-Flow 内部使用 MyBatis 操作数据库） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.7</version>
        </dependency>

        <!-- ========== 工具类 ========== -->

        <!-- Lombok（简化实体类代码） -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- ========== 测试 ========== -->

        <!-- Spring Boot 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- H2 内存数据库（测试用，无需安装 MySQL） -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <spring-boot-maven-plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </spring-boot-maven-plugin>
        </plugins>
    </build>
</project>
```

### 3.3 应用配置

```yaml
# application.yml —— Warm-Flow 应用配置
# kebab-case 风格，所有配置项使用短横线分隔

server:
  port: 8080

spring:
  # ========== 数据源配置 ==========
  datasource:
    url: jdbc:mysql://localhost:3306/hello_warmflow?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: root123

  # ========== JPA 自动建表 ==========
  jpa:
    hibernate:
      ddl-auto: update                          # 自动创建/更新表结构
    show-sql: true                              # 打印 SQL 语句
    properties:
      hibernate:
        format_sql: true                        # 格式化 SQL 输出

# ========== Warm-Flow 配置 ==========
warm-flow:
  # 是否启用 Warm-Flow（默认 true）
  enabled: true
  # 流程定义文件存放路径（classpath 下的目录）
  # Warm-Flow 启动时会自动加载此目录下的所有 JSON 文件
  definition-path: classpath:flow-definition/
  # 使用 MyBatis-Plus 作为 ORM 框架
  orm: mybatis-plus
```

### 3.4 流程定义 JSON

这是 Warm-Flow 最核心的部分——用 JSON 定义流程。下面是一个**请假审批流程**的定义：

```json
{
  // =============================================
  // flow-definition/leave-approval.json —— 请假审批流程定义
  //
  // 流程说明：
  // 1. 员工发起请假申请
  // 2. 部门经理审批（如果请假天数 <= 3，直接通过；否则转人事）
  // 3. 人事审批（如果请假天数 > 3）
  // 4. 结束
  //
  // 流转条件：
  // - 部门经理审批通过 → 判断天数：<=3 天 → 结束；>3 天 → 人事审批
  // - 部门经理驳回 → 流程结束（状态：驳回）
  // - 人事审批通过 → 结束
  // - 人事审批驳回 → 流程结束（状态：驳回）
  // =============================================

  "flowName": "请假审批流程",          // 流程名称
  "version": "1.0",                     // 版本号

  // ========== 节点列表 ==========
  "nodeList": [
    {
      "nodeId": "start",                // 节点ID（唯一标识）
      "nodeName": "开始",                // 节点名称
      "type": 0,                         // 节点类型：0-开始
      "coordinate": "100,100",           // 坐标位置（仅用于可视化）
      "permissionFlag": "start"          // 权限标识：谁可以发起
    },
    {
      "nodeId": "dept_approve",          // 节点ID
      "nodeName": "部门经理审批",         // 节点名称
      "type": 1,                         // 节点类型：1-审批节点
      "coordinate": "300,100",
      "permissionFlag": "dept:approve",  // 权限标识：部门经理
      "assignee": "dept_manager",        // 处理人角色（运行时确定具体人员）
      "formData": [                      // 审批表单字段定义
        {"key": "leaveDays", "label": "请假天数", "type": "number"},
        {"key": "reason", "label": "请假原因", "type": "textarea"},
        {"key": "opinion", "label": "审批意见", "type": "textarea"}
      ]
    },
    {
      "nodeId": "hr_approve",            // 节点ID
      "nodeName": "人事审批",             // 节点名称
      "type": 1,                         // 节点类型：1-审批节点
      "coordinate": "500,100",
      "permissionFlag": "hr:approve",    // 权限标识：人事
      "assignee": "hr_manager",          // 处理人角色
      "formData": [
        {"key": "opinion", "label": "审批意见", "type": "textarea"}
      ]
    },
    {
      "nodeId": "end",                   // 节点ID
      "nodeName": "结束",                // 节点名称
      "type": 3,                         // 节点类型：3-结束节点
      "coordinate": "700,100"
    }
  ],

  // ========== 连线列表 ==========
  "skipList": [
    {
      "id": "s1",                        // 连线ID
      "name": "发起申请",                // 连线名称
      "fromNodeId": "start",             // 来源节点ID
      "toNodeId": "dept_approve",        // 目标节点ID
      "skipType": "pass",                // 流转类型：pass-通过
      "skipCondition": {}                // 无条件，发起后直接到部门经理
    },
    {
      "id": "s2",                        // 连线ID
      "name": "部门经理通过",            // 部门经理审批通过
      "fromNodeId": "dept_approve",
      "toNodeId": "hr_approve",
      "skipType": "pass",                // 流转类型：通过
      "skipCondition": {                  // 条件：请假天数 > 3 天
        "conditionType": "expression",    // 条件类型：表达式
        "expression": "leaveDays > 3"     // SpEL 表达式
      }
    },
    {
      "id": "s3",                        // 连线ID
      "name": "部门经理通过（<=3天）",    // 请假天数 <= 3，直接结束
      "fromNodeId": "dept_approve",
      "toNodeId": "end",
      "skipType": "pass",
      "skipCondition": {                  // 条件：请假天数 <= 3 天
        "conditionType": "expression",
        "expression": "leaveDays <= 3"
      }
    },
    {
      "id": "s4",                        // 连线ID
      "name": "人事审批通过",
      "fromNodeId": "hr_approve",
      "toNodeId": "end",
      "skipType": "pass",
      "skipCondition": {}                // 无条件，通过后直接结束
    },
    {
      "id": "s5",                        // 连线ID
      "name": "部门经理驳回",
      "fromNodeId": "dept_approve",
      "toNodeId": "end",
      "skipType": "reject",              // 流转类型：驳回（流程结束）
      "skipCondition": {}
    },
    {
      "id": "s6",                        // 连线ID
      "name": "人事驳回",
      "fromNodeId": "hr_approve",
      "toNodeId": "end",
      "skipType": "reject",              // 流转类型：驳回（流程结束）
      "skipCondition": {}
    }
  ]
}
```

### 3.5 核心 Java 代码

#### 3.5.1 启动类

```java
/**
 * HelloFlowApplication —— Spring Boot 启动类
 * 
 * @EnableWarmFlow 注解启用 Warm-Flow 工作流引擎
 * 该注解会：
 * 1. 自动扫描并注册 Warm-Flow 的核心 Bean（FlowService 等）
 * 2. 加载 classpath:flow-definition/ 目录下的流程定义 JSON 文件
 * 3. 初始化数据库表结构（如果不存在则自动创建）
 */
@SpringBootApplication
@EnableWarmFlow // 启用 Warm-Flow 工作流引擎
public class HelloFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloFlowApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Warm-Flow 工作流引擎已启动");
        System.out.println("  流程定义：请假审批流程 (leave-approval)");
        System.out.println("========================================");
    }
}
```

#### 3.5.2 请假单实体

```java
/**
 * LeaveBill —— 请假单实体类
 * 
 * 业务表：leave_bill
 * 与流程实例通过 business_id 关联
 * 流程引擎不直接操作业务表，业务表由业务代码管理
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("leave_bill") // MyBatis-Plus 表名映射
public class LeaveBill {

    @TableId(type = IdType.ASSIGN_ID) // 雪花算法生成主键
    private Long id;                   // 请假单ID

    private String applicant;          // 申请人

    private Integer leaveDays;         // 请假天数

    private String reason;             // 请假原因

    private String instanceId;         // 流程实例ID（关联 flow_instance 表）

    private String status;             // 业务状态：draft-草稿 pending-审批中 approved-通过 rejected-驳回

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;  // 创建时间

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;  // 更新时间
}
```

#### 3.5.3 请假审批服务

```java
/**
 * LeaveService —— 请假审批核心业务逻辑
 * 
 * 职责：
 * 1. 发起流程：创建请假单 + 发起审批流程
 * 2. 审批操作：通过/驳回/退回
 * 3. 查询待办：查询当前用户待审批的任务
 * 4. 查询已办：查询当前用户已审批的记录
 * 
 * Warm-Flow 的核心 API 通过 FlowService 提供：
 * - startFlowInstance() —— 发起流程实例
 * - skipFlowInstance() —— 流转（通过/驳回/退回）
 * - getTodoList() —— 查询待办任务
 * - getDoneList() —— 查询已办任务
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    // ========== 注入 Warm-Flow 核心 API ==========

    /**
     * FlowService —— Warm-Flow 的核心服务接口
     * 提供流程定义、流程实例、任务的所有操作 API
     * 由 @EnableWarmFlow 自动注入
     */
    private final FlowService flowService;

    /**
     * LeaveBillMapper —— 请假单数据访问层
     * 用于操作业务数据（请假单的增删改查）
     */
    private final LeaveBillMapper leaveBillMapper;

    /**
     * 发起请假申请 —— 创建请假单并启动审批流程
     * 
     * @param applicant 申请人
     * @param leaveDays 请假天数
     * @param reason    请假原因
     * @return 流程实例ID
     */
    @Transactional(rollbackFor = Exception.class) // 事务注解：业务表和流程表一起提交
    public Long startLeave(String applicant, Integer leaveDays, String reason) {

        // =========================================
        // 第一步：创建请假单（业务数据）
        // =========================================
        LeaveBill bill = new LeaveBill();
        bill.setApplicant(applicant);   // 设置申请人
        bill.setLeaveDays(leaveDays);   // 设置请假天数
        bill.setReason(reason);         // 设置请假原因
        bill.setStatus("draft");        // 初始状态：草稿
        leaveBillMapper.insert(bill);   // 保存到数据库

        // =========================================
        // 第二步：构建流程变量
        // =========================================
        // 流程变量会传递给流程引擎，用于条件判断（如：leaveDays > 3）
        // 也会存储在 flow_instance.variable 字段中
        Map<String, Object> variables = new HashMap<>();
        variables.put("applicant", applicant);   // 申请人
        variables.put("leaveDays", leaveDays);   // 请假天数（用于条件表达式判断）
        variables.put("reason", reason);         // 请假原因
        variables.put("applicantId", applicant); // 申请人ID（用于权限校验）

        // =========================================
        // 第三步：调用 Warm-Flow 发起流程
        // =========================================
        // startFlowInstance() 参数说明：
        //   参数1：流程定义ID（从 flow_definition 表中获取）
        //   参数2：业务ID（关联业务表）
        //   参数3：流程变量（用于条件判断）
        // 返回：流程实例ID
        Long definitionId = 1L; // 假设请假审批流程定义的 ID 为 1
        String businessId = String.valueOf(bill.getId()); // 业务ID = 请假单ID
        Long instanceId = flowService.startFlowInstance(
                definitionId,      // 流程定义ID
                businessId,        // 业务ID（关联业务表）
                variables          // 流程变量（用于条件表达式）
        );

        // =========================================
        // 第四步：更新业务状态
        // =========================================
        bill.setInstanceId(String.valueOf(instanceId)); // 记录流程实例ID
        bill.setStatus("pending");                      // 状态更新为：审批中
        leaveBillMapper.updateById(bill);

        System.out.println("===== 请假申请已发起 =====");
        System.out.println("申请人: " + applicant);
        System.out.println("请假天数: " + leaveDays + " 天");
        System.out.println("流程实例ID: " + instanceId);
        System.out.println("当前节点: 部门经理审批");
        System.out.println("===========================");

        return instanceId; // 返回流程实例ID
    }

    /**
     * 审批操作 —— 通过 / 驳回 / 退回
     * 
     * @param taskId     任务ID（待办任务的ID）
     * @param assignee   审批人
     * @param skipType   流转类型：pass-通过 reject-驳回 back-退回
     * @param opinion    审批意见
     * @param variables  流程变量（可选，用于更新流程数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long taskId, String assignee,
                        String skipType, String opinion,
                        Map<String, Object> variables) {

        // =========================================
        // 第一步：构建审批参数
        // =========================================
        // Warm-Flow 的 skipFlowInstance() 需要以下参数：
        // - taskId：当前待办任务的ID
        // - skipType：流转类型（pass/reject/back）
        // - assignee：审批人
        // - opinion：审批意见
        // - variables：更新后的流程变量（可选）
        FlowParams flowParams = FlowParams.build()
                .skipType(skipType)  // 设置流转类型：pass-通过 reject-驳回 back-退回
                .operator(assignee)  // 设置操作人（审批人）
                .opinion(opinion);   // 设置审批意见

        // 如果有更新流程变量，设置进去
        if (variables != null && !variables.isEmpty()) {
            flowParams.variable(variables);
        }

        // =========================================
        // 第二步：执行流转操作
        // =========================================
        // skipFlowInstance() 是 Warm-Flow 最核心的 API
        // 它会根据 skipType 和流程定义中的连线条件，自动决定下一步走向
        flowService.skipFlowInstance(taskId, flowParams);

        // =========================================
        // 第三步：输出审批结果
        // =========================================
        String actionName;
        switch (skipType) {
            case "pass":
                actionName = "通过";
                break;
            case "reject":
                actionName = "驳回";
                break;
            case "back":
                actionName = "退回";
                break;
            default:
                actionName = skipType;
        }
        System.out.println("===== 审批操作完成 =====");
        System.out.println("操作: " + actionName);
        System.out.println("审批人: " + assignee);
        System.out.println("意见: " + opinion);
        System.out.println("==========================");
    }

    /**
     * 查询待办任务 —— 当前用户待审批的任务列表
     * 
     * @param assignee 处理人（用户ID或用户名）
     * @return 待办任务列表
     */
    public List<FlowTask> getTodoList(String assignee) {
        // Warm-Flow 提供 getTodoList() 方法
        // 参数：处理人，查询此人所有待办任务
        return flowService.getTodoList(assignee);
    }

    /**
     * 查询已办任务 —— 当前用户已审批的任务列表
     */
    public List<FlowTask> getDoneList(String assignee) {
        return flowService.getDoneList(assignee);
    }

    /**
     * 查询流程实例详情
     */
    public FlowInstance getInstanceInfo(Long instanceId) {
        return flowService.getInstanceInfo(instanceId);
    }

    /**
     * 查询流程的跳转记录（完整审批轨迹）
     */
    public List<FlowSkip> getSkipList(Long instanceId) {
        return flowService.getSkipList(instanceId);
    }
}
```

#### 3.5.4 请假审批控制器

```java
/**
 * LeaveController —— 请假审批 REST API
 * 
 * 提供对外 HTTP 接口，方便前端调用
 * 完整的流程操作：发起 → 审批 → 查询
 */
@RestController
@RequestMapping("/api/leave")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    /**
     * 发起请假申请（POST /api/leave/start）
     * 
     * 请求体示例：
     * {
     *   "applicant": "张三",
     *   "leaveDays": 5,
     *   "reason": "回老家探亲"
     * }
     * 
     * @param request 发起请求
     * @return 流程实例ID
     */
    @PostMapping("/start")
    public Result<Long> startLeave(@RequestBody StartRequest request) {
        // 调用服务层发起请假流程
        Long instanceId = leaveService.startLeave(
                request.getApplicant(),  // 申请人
                request.getLeaveDays(),  // 请假天数
                request.getReason()      // 请假原因
        );
        // 返回统一的成功响应
        return Result.success(instanceId);
    }

    /**
     * 审批操作（POST /api/leave/approve）
     * 
     * 请求体示例：
     * {
     *   "taskId": 1,          // 待办任务ID
     *   "assignee": "李四",    // 审批人
     *   "skipType": "pass",   // pass-通过 reject-驳回 back-退回
     *   "opinion": "同意"     // 审批意见
     * }
     */
    @PostMapping("/approve")
    public Result<Void> approve(@RequestBody ApproveRequest request) {
        leaveService.approve(
                request.getTaskId(),    // 任务ID
                request.getAssignee(),  // 审批人
                request.getSkipType(),  // 流转类型
                request.getOpinion(),   // 审批意见
                null                    // 流程变量（不更新）
        );
        return Result.success();
    }

    /**
     * 查询待办任务（GET /api/leave/todo?assignee=李四）
     */
    @GetMapping("/todo")
    public Result<List<FlowTask>> getTodoList(@RequestParam String assignee) {
        List<FlowTask> todoList = leaveService.getTodoList(assignee);
        return Result.success(todoList);
    }

    /**
     * 查询流程详情（GET /api/leave/instance/{instanceId}）
     */
    @GetMapping("/instance/{instanceId}")
    public Result<FlowInstance> getInstance(@PathVariable Long instanceId) {
        FlowInstance instance = leaveService.getInstanceInfo(instanceId);
        return Result.success(instance);
    }

    // ========== 内部请求体类 ==========

    /**
     * 发起请假请求体
     */
    @Data
    public static class StartRequest {
        private String applicant;  // 申请人
        private Integer leaveDays; // 请假天数
        private String reason;     // 请假原因
    }

    /**
     * 审批操作请求体
     */
    @Data
    public static class ApproveRequest {
        private Long taskId;       // 待办任务ID
        private String assignee;   // 审批人
        private String skipType;   // 流转类型：pass/reject/back
        private String opinion;    // 审批意见
    }
}
```

### 3.6 单元测试

```java
/**
 * LeaveFlowTest —— 请假审批流程完整测试
 * 
 * 测试场景：张三请5天假（>3天，需要部门经理和人事两级审批）
 * 流程路径：
 * 发起 → 部门经理通过 → 人事通过 → 结束
 * 
 * 测试流程：
 * 1. 张三发起请假申请 → 创建流程实例，生成"部门经理审批"待办
 * 2. 李四（部门经理）审批通过 → 生成"人事审批"待办（因为5>3）
 * 3. 王五（人事）审批通过 → 流程结束，状态变为 pass
 */
@SpringBootTest
public class LeaveFlowTest {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private FlowService flowService;

    /**
     * 测试完整审批流程：发起 → 通过 → 通过 → 结束
     * 验证：流程状态最终为 pass
     */
    @Test
    public void testFullApprovalFlow() {
        System.out.println("========== 测试场景：请假5天，两级审批通过 ==========");

        // =========================================
        // 第一步：张三发起请假申请（5天）
        // =========================================
        Long instanceId = leaveService.startLeave(
                "张三",   // 申请人
                5,        // 请假天数（>3，需要两级审批）
                "回老家探亲" // 请假原因
        );

        // 验证：流程实例已创建
        Assertions.assertNotNull(instanceId, "流程实例ID不能为空");

        // 打印当前流程状态
        FlowInstance instance = flowService.getInstanceInfo(instanceId);
        System.out.println("当前流程状态: " + instance.getFlowStatus()); // 预期：pending
        Assertions.assertEquals("pending", instance.getFlowStatus());

        // =========================================
        // 第二步：查询李四（部门经理）的待办任务
        // =========================================
        List<FlowTask> deptTodo = flowService.getTodoList("dept_manager");
        System.out.println("部门经理待办数: " + deptTodo.size()); // 预期：1
        Assertions.assertFalse(deptTodo.isEmpty(), "部门经理应该有1个待办");

        // 获取第一个待办任务
        FlowTask deptTask = deptTodo.get(0);
        System.out.println("待办任务: " + deptTask.getNodeName()); // 预期：部门经理审批

        // =========================================
        // 第三步：李四审批通过（5 > 3，走人事审批）
        // =========================================
        leaveService.approve(
                deptTask.getId(),   // 任务ID
                "李四",             // 审批人（部门经理）
                "pass",            // 流转类型：通过
                "同意，转人事审批",  // 审批意见
                null               // 不更新流程变量
        );

        // =========================================
        // 第四步：查询王五（人事）的待办任务
        // =========================================
        List<FlowTask> hrTodo = flowService.getTodoList("hr_manager");
        System.out.println("人事待办数: " + hrTodo.size()); // 预期：1
        Assertions.assertFalse(hrTodo.isEmpty(), "人事应该有1个待办");

        FlowTask hrTask = hrTodo.get(0);
        System.out.println("待办任务: " + hrTask.getNodeName()); // 预期：人事审批

        // =========================================
        // 第五步：王五审批通过
        // =========================================
        leaveService.approve(
                hrTask.getId(),    // 任务ID
                "王五",            // 审批人（人事）
                "pass",           // 流转类型：通过
                "符合公司规定，同意", // 审批意见
                null
        );

        // =========================================
        // 第六步：验证流程已结束
        // =========================================
        instance = flowService.getInstanceInfo(instanceId);
        System.out.println("最终流程状态: " + instance.getFlowStatus()); // 预期：pass
        Assertions.assertEquals("pass", instance.getFlowStatus());

        // 验证：没有待办任务了
        List<FlowTask> remainingTasks = flowService.getTodoList("dept_manager");
        remainingTasks.addAll(flowService.getTodoList("hr_manager"));
        System.out.println("剩余待办数: " + remainingTasks.size()); // 预期：0
        Assertions.assertTrue(remainingTasks.isEmpty(), "所有审批完成，应无待办");

        // 验证：跳转记录
        List<FlowSkip> skipList = flowService.getSkipList(instanceId);
        System.out.println("流转记录数: " + skipList.size()); // 预期：3（发起→部门经理→人事→结束）
        for (FlowSkip skip : skipList) {
            System.out.println("  " + skip.getFromNodeName() + " → " + skip.getToNodeName()
                    + " (" + skip.getSkipType() + ")");
        }

        System.out.println("========== 测试通过！流程已正常结束 ==========");
    }

    /**
     * 测试驳回场景：部门经理驳回 → 流程结束
     * 验证：流程状态为 reject
     */
    @Test
    public void testRejectFlow() {
        System.out.println("========== 测试场景：部门经理驳回 ==========");

        // 1. 发起请假（3天，<=3天，部门经理审批后直接结束）
        Long instanceId = leaveService.startLeave("张三", 3, "身体不适");

        // 2. 部门经理驳回
        List<FlowTask> todoList = flowService.getTodoList("dept_manager");
        leaveService.approve(
                todoList.get(0).getId(),
                "李四",
                "reject",          // 驳回
                "本月请假人数已满，暂不批准",
                null
        );

        // 3. 验证：流程状态为 reject
        FlowInstance instance = flowService.getInstanceInfo(instanceId);
        Assertions.assertEquals("reject", instance.getFlowStatus());
        System.out.println("流程状态: " + instance.getFlowStatus()); // 预期：reject
        System.out.println("========== 测试通过！流程已驳回 ==========");
    }
}
```

**测试运行结果示例：**

```
========== 测试场景：请假5天，两级审批通过 ==========

===== 请假申请已发起 =====
申请人: 张三
请假天数: 5 天
流程实例ID: 1
当前节点: 部门经理审批
===========================

当前流程状态: pending
部门经理待办数: 1
待办任务: 部门经理审批

===== 审批操作完成 =====
操作: 通过
审批人: 李四
意见: 同意，转人事审批
==========================

人事待办数: 1
待办任务: 人事审批

===== 审批操作完成 =====
操作: 通过
审批人: 王五
意见: 符合公司规定，同意
==========================

最终流程状态: pass
剩余待办数: 0

流转记录数: 3
  开始 → 部门经理审批 (pass)
  部门经理审批 → 人事审批 (pass)
  人事审批 → 结束 (pass)

========== 测试通过！流程已正常结束 ==========
```

---

## 四、运行验证

### 4.1 环境准备

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS hello_warmflow DEFAULT CHARSET utf8mb4;"

# 2. 启动应用（Warm-Flow 会自动建表）
mvn spring-boot:run

# 3. 用 curl 测试流程 API
```

### 4.2 测试流程

```bash
# =============================================
# 测试 1：发起请假申请（5天，需要两级审批）
# =============================================
curl -X POST http://localhost:8080/api/leave/start \
  -H "Content-Type: application/json" \
  -d '{"applicant":"张三","leaveDays":5,"reason":"回老家探亲"}'

# 预期响应：
# {"code":200,"data":1,"message":"success"}
# 流程实例ID = 1

# =============================================
# 测试 2：查询部门经理的待办任务
# =============================================
curl http://localhost:8080/api/leave/todo?assignee=dept_manager

# 预期响应（简化）：
# {"code":200,"data":[{"id":1,"nodeName":"部门经理审批","taskStatus":"0"}]}

# =============================================
# 测试 3：部门经理审批通过
# =============================================
curl -X POST http://localhost:8080/api/leave/approve \
  -H "Content-Type: application/json" \
  -d '{"taskId":1,"assignee":"李四","skipType":"pass","opinion":"同意，转人事审批"}'

# 预期响应：
# {"code":200,"message":"success"}

# =============================================
# 测试 4：查询人事的待办任务
# =============================================
curl http://localhost:8080/api/leave/todo?assignee=hr_manager

# 预期响应：
# {"code":200,"data":[{"id":2,"nodeName":"人事审批","taskStatus":"0"}]}

# =============================================
# 测试 5：人事审批通过
# =============================================
curl -X POST http://localhost:8080/api/leave/approve \
  -H "Content-Type: application/json" \
  -d '{"taskId":2,"assignee":"王五","skipType":"pass","opinion":"符合公司规定，同意"}'

# =============================================
# 测试 6：查询流程详情（验证最终状态）
# =============================================
curl http://localhost:8080/api/leave/instance/1

# 预期响应：
# {"code":200,"data":{"id":1,"flowStatus":"pass","nodeName":"结束"}}
```

### 4.3 数据库验证

```sql
-- 查询流程定义
SELECT * FROM flow_definition;
-- 结果：一条记录，flow_name = '请假审批流程'

-- 查询流程实例
SELECT * FROM flow_instance;
-- 结果：一条记录，flow_status = 'pass'（审批通过）

-- 查询任务记录
SELECT * FROM flow_task;
-- 结果：两条记录，task_status = '1'（已处理）

-- 查询跳转记录
SELECT * FROM flow_skip;
-- 结果：三条记录，完整记录了审批路径
```

---

## 五、项目对照

### 5.1 ruoyi-ai 的工作流使用

ruoyi-ai 项目中的工作流主要用于以下场景：

| 场景 | 流程定义 | 审批节点 | 说明 |
|------|---------|---------|------|
| **知识库发布审批** | knowledge-release | 技术审核 → 内容审核 → 发布 | 知识库内容上线前需要审核 |
| **模型申请审批** | model-apply | 部门审批 → 技术审批 → 资源分配 | 申请使用 AI 模型需要审批 |
| **数据集申请** | dataset-apply | 部门审批 → 数据管理审批 | 访问敏感数据需要审批 |

### 5.2 最简demo与ruoyi-ai的对照

| 最简 Demo | ruoyi-ai 对应 | 增强说明 |
|-----------|--------------|---------|
| `LeaveService` 直接调用 FlowService | `FlowProcessService` 封装 | 增加了流程与业务数据的双向同步、审批通知、历史记录 |
| 纯 Java 调用 | 增加了 `FlowController` 完整 REST API | 配合前端流程设计器，支持可视化流程配置 |
| 固定流程定义 JSON | 流程定义存储在数据库 `flow_definition` 表 | 支持在线编辑流程、动态发布 |
| 固定审批人角色 | 通过 `FlowUserService` 动态分配审批人 | 根据部门、角色、岗位动态计算处理人 |
| 无通知 | 集成 `WebSocket` 推送审批通知 | 待办任务实时提醒 |

### 5.3 ruoyi-ai 实际代码示例

```java
/**
 * ruoyi-ai 中的流程审批服务（简化版）
 * 
 * 相比最简 demo，增加了：
 * 1. 动态审批人计算
 * 2. 审批通知推送
 * 3. 流程历史记录
 * 4. 业务数据与流程状态同步
 */
@Service
@RequiredArgsConstructor
public class FlowProcessService {

    private final FlowService flowService;  // Warm-Flow 核心 API

    private final WebSocketService wsService;  // 通知推送

    /**
     * 发起流程 —— 带业务数据同步
     * 
     * @param defId      流程定义ID
     * @param businessId 业务ID
     * @param variables  流程变量
     * @param userId     发起人ID
     * @return 流程实例ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long startProcess(Long defId, String businessId,
                             Map<String, Object> variables, Long userId) {
        // 1. 设置发起人信息
        variables.put("initiator", userId);

        // 2. 调用 Warm-Flow 发起流程
        Long instanceId = flowService.startFlowInstance(defId, businessId, variables);

        // 3. 查询第一个待办任务，获取审批人
        FlowInstance instance = flowService.getInstanceInfo(instanceId);
        List<FlowTask> firstTasks = flowService.getTodoListByInstanceId(instanceId);

        // 4. 推送审批通知给第一个审批人
        for (FlowTask task : firstTasks) {
            wsService.sendTodoNotification(task.getAssignee(), task);
        }

        return instanceId;
    }

    /**
     * 审批通过 —— 带动态审批人计算
     * 
     * 如果下一个节点需要动态指定审批人，在 variables 中传入
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long taskId, String operator,
                        String opinion, Map<String, Object> variables) {
        // 构建审批参数
        FlowParams params = FlowParams.build()
                .skipType("pass")       // 通过
                .operator(operator)     // 审批人
                .opinion(opinion)       // 审批意见
                .variable(variables);   // 流程变量（可包含下一个节点的审批人）

        // 执行流转
        flowService.skipFlowInstance(taskId, params);

        // 通知下一个审批人（如果有）
        // 通过查询新的待办任务，获取下一个审批人并推送通知
    }
}
```

---

## 六、面试题

### Q1: 工作流引擎（Warm-Flow）和状态机模式有什么区别？什么场景用工作流？

**参考答案：**

**状态机模式**是在代码中硬编码状态和流转逻辑，适合**固定、简单、变化少**的流程。

**工作流引擎**将流程定义从代码中剥离，适合**多变、复杂、需要可视化**的流程。

| 维度 | 状态机模式 | 工作流引擎 |
|------|-----------|-----------|
| **流程定义** | 代码硬编码（if-else 或 enum） | 外部配置（JSON/XML/数据库） |
| **变更成本** | 改代码 → 编译 → 部署 → 重启 | 修改流程定义 → 热加载 |
| **可视化** | 无 | 支持流程图展示 |
| **复杂度** | 适合 3-5 个状态的简单流程 | 适合多节点、多分支、会签等复杂流程 |
| **学习成本** | 低 | 较高（需要理解引擎概念） |
| **性能** | 高（无框架开销） | 中等（有框架调用开销） |

**选择建议：**
- 简单审批（2-3 个节点，如知识库发布审批）→ 状态机足够
- 复杂流程（多级审批、会签、转办、催办）→ 工作流引擎
- 流程经常变（业务规则频繁调整）→ 工作流引擎

ruoyi-ai 使用 Warm-Flow 的原因是：虽然单个流程不复杂，但多个流程（知识库发布、模型申请、数据集申请）共享一套审批机制，用工作流引擎统一管理比每个流程分别写状态机更高效。

### Q2: Warm-Flow 如何实现流程跳转中的条件判断？请举例说明。

**参考答案：**

Warm-Flow 在流程定义的 `skipList` 中通过 `skipCondition` 字段定义条件表达式，引擎根据表达式计算结果决定走哪条连线。

**条件判断的原理：**

1. 当审批人点击"通过"时，Warm-Flow 查找当前节点的所有出线（`fromNodeId = 当前节点`）
2. 对于每条出线，检查 `skipCondition`：
   - 如果无条件（`{}`），直接匹配
   - 如果有条件，计算表达式
3. 根据 `skipType` 过滤：
   - `pass` 类型的线：条件满足时走这条线
   - `reject` 类型的线：无条件，审批人选择驳回时走
4. 匹配到合适的线后，流程推进到目标节点

**示例：** 请假天数判断

```json
{
  "fromNodeId": "dept_approve",
  "toNodeId": "hr_approve",
  "skipType": "pass",
  "skipCondition": {
    "conditionType": "expression",
    "expression": "leaveDays > 3"
  }
}
```

当部门经理审批通过时，Warm-Flow 获取流程变量中的 `leaveDays` 值，判断 `5 > 3` 为 true，所以走这条线到人事审批。如果 `leaveDays = 2`，则 `2 > 3` 为 false，走另一条线（`leaveDays <= 3`）直接到结束。

**追问应对：** "条件表达式支持哪些语法？" 答：Warm-Flow 支持 SpEL（Spring Expression Language）表达式，可以访问流程变量中的任意字段，支持比较运算符、逻辑运算符、方法调用等。也可以扩展到自定义表达式解析器。

### Q3: 如何保证工作流操作和业务操作的事务一致性？

**参考答案：**

工作流操作（创建流程实例、更新任务状态）和业务操作（创建请假单、更新订单状态）需要保持事务一致性——要么都成功，要么都回滚。

**Warm-Flow 的解决方案：**

由于 Warm-Flow 是嵌入式引擎，流程表和业务表在同一个数据库中，所以用  `@Transactional` 即可保证：

```java
/**
 * 事务一致性保证：@Transactional 包裹业务操作和流程操作
 * 
 * 原理：
 * 1. 业务表（leave_bill）和流程表（flow_xxx）在同一个数据库
 * 2. @Transactional 开启本地事务，所有操作在一个事务中
 * 3. 任一操作失败，整个事务回滚
 * 4. 全部成功，事务提交
 */
@Service
public class LeaveService {

    @Transactional(rollbackFor = Exception.class) // 关键：事务注解
    public Long startLeave(String applicant, Integer leaveDays, String reason) {
        
        // 操作1：业务操作（业务表）
        LeaveBill bill = new LeaveBill();
        bill.setApplicant(applicant);
        leaveBillMapper.insert(bill); // 写入业务表
        
        // 操作2：流程操作（流程表）
        Long instanceId = flowService.startFlowInstance(
                definitionId, 
                String.valueOf(bill.getId()), 
                variables
        ); // 写入 flow_instance 和 flow_task 表
        
        // 如果这里抛出异常，操作1和操作2都会回滚
        // 如果全部成功，一起提交
        
        return instanceId;
    }
}
```

**如果是跨服务场景（流程操作在服务A，业务操作在服务B）：**

1. **Seata 分布式事务**：`@GlobalTransactional` 注解，底层用 AT 模式
2. **TCC 模式**：Try（预留资源）→ Confirm（确认）→ Cancel（回滚）
3. **事务消息**：先发消息，本地事务成功后消息才可见

不过对于 ruoyi-ai 这种单体架构项目，`@Transactional` 就足够了——Warm-Flow 的 4 张表和业务表在同一数据库，本地事务天然支持 ACID。

---

## 参考资料

- [Warm-Flow GitHub 仓库](https://github.com/snailjava/warm-flow) — 源码与文档
- [Warm-Flow 官方文档](https://snailjava.gitee.io/warm-flow-doc/) — 核心 API 参考
- [BPMN 2.0 规范](https://www.omg.org/spec/BPMN/2.0/) — 业务流程建模标准
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — 工作流模块源码