# Flowable 深入 — BPMN 高级 · 监听器 · 动态流程

> 本篇深入 Flowable 引擎的高级用法：BPMN 2.0 网关、监听器机制、动态流程调整。

---

## 一、BPMN 2.0 核心元素

### 1.1 网关类型

| 网关 | 语法 | 说明 |
|------|------|------|
| 排他网关（Exclusive） | 菱形 + X | 只走一个分支，条件互斥 |
| 并行网关（Parallel） | 菱形 + + | 所有分支同时执行 |
| 包含网关（Inclusive） | 菱形 + O | 满足条件的所有分支都执行 |

### 1.2 排他网关示例

```xml
<exclusiveGateway id="amountGateway" name="金额审批网关"/>

<sequenceFlow id="flow1" sourceRef="startEvent" targetRef="amountGateway"/>

<sequenceFlow id="flow_high" sourceRef="amountGateway" targetRef="managerApprove">
    <conditionExpression>${amount > 10000}</conditionExpression>
</sequenceFlow>

<sequenceFlow id="flow_low" sourceRef="amountGateway" targetRef="leaderApprove">
    <conditionExpression>${amount <= 10000}</conditionExpression>
</sequenceFlow>
```

### 1.3 并行网关示例（会签）

```xml
<!-- 并行网关：所有分支同时执行 -->
<parallelGateway id="parallelStart"/>

<!-- 会签条件：所有人都完成 -->
<userTask id="countersign" name="会签审批">
    <multiInstanceLoopCharacteristics isSequential="false">
        <completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>
    </multiInstanceLoopCharacteristics>
</userTask>

<!-- 或签条件：任意一人完成 -->
<userTask id="orsign" name="或签审批">
    <multiInstanceLoopCharacteristics isSequential="false">
        <completionCondition>${nrOfCompletedInstances >= 1}</completionCondition>
    </multiInstanceLoopCharacteristics>
</userTask>
```

---

## 二、监听器机制

### 2.1 三种监听器

| 监听器 | 触发时机 | 用途 |
|--------|---------|------|
| 执行监听器（ExecutionListener） | 流程节点进入/离开 | 流程状态变更通知 |
| 任务监听器（TaskListener） | 任务创建/完成/删除 | 审批人分配、业务联动 |
| 事件监听器（EventDispatcher） | 全局事件 | 日志记录、消息发送 |

### 2.2 任务监听器实战

```java
@Component
public class ApprovalTaskListener implements TaskListener {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ProcessLogService logService;

    @Override
    public void notify(DelegateTask delegateTask) {
        String eventType = delegateTask.getEventName();

        switch (eventType) {
            case TASK_EVENT_CREATE:
                // 任务创建：通知审批人有新任务
                String assignee = delegateTask.getAssignee();
                notificationService.send(assignee,
                    "您有新的审批任务：" + delegateTask.getName());
                break;

            case TASK_EVENT_COMPLETE:
                // 任务完成：记录审批日志
                String action = (String) delegateTask.getVariable("action");
                String comment = (String) delegateTask.getVariable("comment");
                logService.log(
                    delegateTask.getProcessInstanceId(),
                    delegateTask.getId(),
                    delegateTask.getName(),
                    delegateTask.getAssignee(),
                    action,
                    comment
                );
                // 通知发起人审批结果
                String initiator = (String) delegateTask.getVariable("initiator");
                notificationService.send(initiator,
                    "您的申请已被" + action + "：" + comment);
                break;
        }
    }
}
```

### 2.3 执行监听器实战

```java
@Component
public class ProcessEndListener implements ExecutionListener {

    @Autowired
    private LeaveRequestService leaveService;

    @Override
    public void notify(DelegateExecution execution) {
        if ("endEvent".equals(execution.getCurrentActivityId())) {
            // 流程结束：更新业务状态
            Long leaveId = (Long) execution.getVariable("leaveId");
            String approved = (String) execution.getVariable("approved");

            if ("true".equals(approved)) {
                leaveService.approve(leaveId);
            } else {
                leaveService.reject(leaveId);
            }
        }
    }
}
```

---

## 三、动态流程调整

### 3.1 转办（委托任务）

```java
public void transferTask(String taskId, String targetUser) {
    taskService.setAssignee(taskId, targetUser);
    // 记录转办日志
    taskService.addComment(taskId, null, "转办给 " + targetUser);
}
```

### 3.2 加签（临时增加审批人）

```java
public void addSign(String taskId, String additionalUser) {
    // 获取当前任务
    Task task = taskService.createTaskQuery().taskId(taskId).singleResult();

    // 动态创建子任务
    taskService.newSubTask()
        .parentTaskId(taskId)
        .name("加签任务")
        .assignee(additionalUser)
        .save();
}
```

### 3.3 退签（退回到上一步）

```java
public void rejectToPrevious(String taskId) {
    // 使用 Flowable 6.8+ 的变更状态 API
    processInstanceService.createChangeActivityStateBuilder()
        .changeStateOf(taskId)
        .toActivity("previousTaskId")  // 目标节点
        .changeState();
}
```

### 3.4 跳签（跳过当前节点）

```java
public void skipTask(String taskId) {
    processInstanceService.createChangeStateBuilder()
        .changeStateOf(taskId)
        .toActivity("nextTaskId")  // 跳到下一个节点
        .changeState();
}
```

---

## 四、流程变量与条件表达式

### 4.1 流程变量传递

```java
// 启动流程时传入变量
Map<String, Object> variables = new HashMap<>();
variables.put("days", 5);
variables.put("amount", 15000);
variables.put("initiator", "zhangsan");
runtimeService.startProcessInstanceByKey("leave-process", variables);

// 审批时更新变量
taskService.setVariable(taskId, "approved", "true");
```

### 4.2 条件表达式

```xml
<!-- 排他网关条件 -->
<conditionExpression>${days > 3}</conditionExpression>
<conditionExpression>${amount > 10000 && amount <= 50000}</conditionExpression>

<!-- 会签完成条件 -->
<completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>

<!-- 或签完成条件 -->
<completionCondition>${nrOfCompletedInstances >= 1}</completionCondition>
```

---

## 五、面试 Flowable 题

### Q1：Flowable 的流程实例和任务是什么关系？

**参考答案**：
流程实例 = 一次完整的审批流程运行。任务 = 流程中的一个审批节点。
一个流程实例包含多个任务（串行）或同时产生多个任务（并行）。
任务完成后，流程自动流转到下一个节点，直到所有节点完成，流程实例结束。

### Q2：如何实现"审批驳回后回到发起人重新提交"？

**参考答案**：
方案一：退签 API（Flowable 6.8+），`changeStateOf(taskId).toActivity("startEvent")`
方案二：在 BPMN 中设计"驳回"连线，从审批节点连回开始节点
方案三：使用 Error Event，审批人抛出业务异常，流程捕获后跳转

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← 数据库设计](../02-core/03-data-design.md) | [📚 20-OA](../../README.md) | [企业集成 →](./02-integration.md) |
