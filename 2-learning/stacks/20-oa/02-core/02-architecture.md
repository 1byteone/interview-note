# OA 架构设计 — 分层架构与 Flowable 集成

> 本篇聚焦 OA 系统的架构设计：如何分层、如何集成 Flowable、如何保证权限性能。

---

## 一、OA 架构分层

```
┌─────────────────────────────────────────────────────────────────────┐
│                    OA 架构分层                                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    展示层                                      │  │
│  │  Vue3 + Vben Admin + Ant Design Vue                           │  │
│  │  · 审批管理 · 流程设计 · 表单设计 · 消息中心                     │  │
│  └───────────────────────────────────────────────────────────────┘  │
│              ↕  HTTP / WebSocket                                    │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    接入层（Gateway）                            │  │
│  │  Spring Cloud Gateway · JWT · RBAC 校验 · 限流                 │  │
│  └───────────────────────────────────────────────────────────────┘  │
│              ↕                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    业务服务层                                   │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │  │
│  │  │ 审批服务  │ │ 表单服务  │ │ 权限服务  │ │ 消息服务  │        │  │
│  │  │ Flowable │ │ 动态表单  │ │ RBAC     │ │ 站内信   │        │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐                     │  │
│  │  │ 文档服务  │ │ 组织服务  │ │ 日程服务  │                     │  │
│  │  │ MinIO    │ │ 部门/岗位 │ │ 日历     │                     │  │
│  │  └──────────┘ └──────────┘ └──────────┘                     │  │
│  └───────────────────────────────────────────────────────────────┘  │
│              ↕                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    Flowable 引擎层                             │  │
│  │  RepositoryService · RuntimeService · TaskService · History    │  │
│  └───────────────────────────────────────────────────────────────┘  │
│              ↕                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    基础设施层                                   │  │
│  │  MySQL · Redis · RocketMQ · MinIO                             │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、Flowable 集成方案

### 2.1 Flowable 与 Spring Boot 集成

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>6.8.0</version>
</dependency>
```

### 2.2 流程定义部署

```java
@Service
public class ProcessDeployService {

    @Autowired
    private RepositoryService repositoryService;

    public void deployProcess(String bpmnResource) {
        repositoryService.createDeployment()
            .addClasspathResource(bpmnResource)
            .name("请假审批流程")
            .deploy();
    }

    // 查询流程定义
    public ProcessDefinition getProcessDefinition(String processKey) {
        return repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(processKey)
            .latestVersion()
            .singleResult();
    }
}
```

### 2.3 流程实例启动

```java
@Service
public class ProcessInstanceService {

    @Autowired
    private RuntimeService runtimeService;

    public String startProcess(String processKey, Map<String, Object> variables) {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            processKey,
            variables  // 流程变量：如 {days: 5, reason: "年假"}
        );
        return instance.getId();
    }
}
```

### 2.4 任务查询与处理

```java
@Service
public class TaskService {

    @Autowired
    private FlowableTaskService taskService;

    // 查询待办任务
    public List<Task> getMyTasks(String assignee) {
        return taskService.createTaskQuery()
            .taskAssignee(assignee)
            .orderByTaskCreateTime().desc()
            .list();
    }

    // 审批通过
    public void approve(String taskId, String comment) {
        // 添加审批意见
        taskService.addComment(taskId, null, comment);
        // 完成任务，流程流转到下一个节点
        taskService.complete(taskId);
    }

    // 驳回
    public void reject(String taskId, String reason) {
        taskService.addComment(taskId, null, "驳回：" + reason);
        // 使用跳转 API 退回到发起人节点
        taskService.createChangeActivityStateBuilder()
            .changeStateOf(taskId).toActivity("startEvent")
            .changeState();
    }
}
```

---

## 三、权限与 Flowable 联动

### 3.1 审批人自动分配

```java
// 流程监听器：任务创建时自动分配审批人
@Component
public class TaskCreateListener implements TaskListener {

    @Autowired
    private DeptService deptService;

    @Override
    public void notify(DelegateTask delegateTask) {
        String processKey = delegateTask.getProcessDefinitionId();

        // 获取发起人
        String initiator = (String) delegateTask.getVariable("initiator");

        if ("leave-approve".equals(processKey)) {
            // 请假审批：审批人 = 发起人的部门负责人
            Long deptId = deptService.getDeptIdByUserId(initiator);
            Long leaderId = deptService.getLeaderId(deptId);
            delegateTask.setAssignee(String.valueOf(leaderId));
        }
    }
}
```

### 3.2 审批后的业务联动

```java
@Component
public class TaskCompleteListener implements TaskListener {

    @Autowired
    private LeaveRequestService leaveService;

    @Override
    public void notify(DelegateTask delegateTask) {
        String taskId = delegateTask.getId();
        String processInstanceId = delegateTask.getProcessInstanceId();

        // 查询是否是最后一个审批节点
        long pendingTasks = runtimeService.createTaskQuery()
            .processInstanceId(processInstanceId)
            .count();

        if (pendingTasks == 0) {
            // 流程结束，更新业务状态
            Long leaveId = (Long) delegateTask.getVariable("leaveId");
            leaveService.approve(leaveId);

            // 通知 ERP 更新考勤
            eventPublisher.publish("oa.leave.approved", leaveId);
        }
    }
}
```

---

## 四、性能优化

### 4.1 权限查询优化

| 优化手段 | 说明 |
|---------|------|
| Redis 缓存 | 权限数据缓存到 Redis，减少数据库查询 |
| 批量加载 | 登录时一次性加载全部权限 |
| 本地缓存 | Caffeine 二级缓存，减少 Redis 网络开销 |
| 权限树预加载 | 前端路由表预加载，避免每次菜单查询 |

### 4.2 Flowable 历史表优化

Flowable 的历史表会快速增长：

```sql
-- 定期清理 3 个月前的历史数据
DELETE FROM ACT_HI_PROCINST WHERE END_TIME_ < DATE_SUB(NOW(), INTERVAL 3 MONTH);
DELETE FROM ACT_HI_TASKINST WHERE END_TIME_ < DATE_SUB(NOW(), INTERVAL 3 MONTH);
DELETE FROM ACT_HI_ACTINST WHERE END_TIME_ < DATE_SUB(NOW(), INTERVAL 3 MONTH);
```

### 4.3 流程查询优化

```sql
-- 待办任务查询索引
CREATE INDEX idx_task_assignee ON ACT_RU_TASK(ASSIGNEE_);
CREATE INDEX idx_task_create_time ON ACT_RU_TASK(CREATE_TIME_);

-- 历史流程查询索引
CREATE INDEX idx_proc_inst_start_time ON ACT_HI_PROCINST(START_TIME_);
CREATE INDEX idx_proc_inst_end_time ON ACT_HI_PROCINST(END_TIME_);
```

---

## 五、面试架构题

### Q1：Flowable 的数据存在哪些表中？

**参考答案**：
Flowable 使用三组表：
1. **ACT_RE_*（Repository）**：流程定义、部署信息（模板级）
2. **ACT_RU_*（Runtime）**：运行中的流程实例、任务（运行级，数据量小）
3. **ACT_HI_*（History）**：历史流程、历史任务（历史级，数据量大，需定期清理）

另外还有通用表：
- `ACT_GE_*`：通用属性
- `ACT_ID_*`：身份信息（可选）
- `ACT_EVT_*`：事件日志

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← 核心业务建模](./01-domain-model.md) | [📚 20-OA](../../README.md) | [数据库设计 →](./03-data-design.md) |
