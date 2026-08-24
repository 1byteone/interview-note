# T06: Codex Workspace Agent 定时自动化

> **[← 教程目录](README.md) | 工具: Codex App (ChatGPT) | 时长: ~15min**

---

## Goal

创建一个 Workspace Agent，**每天自动分诊 GitHub Issue** 并分配标签。

## 前置条件

- ChatGPT 账号（支持 Codex 的付费计划）
- GitHub 仓库有 Issue 权限

## Step 1: 创建 Workspace Agent

在 ChatGPT Desktop App 中：

1. 点击左侧 **Workspace** → **Agents** → **Create Agent**
2. 填写 Agent 配置：

```
名称: Issue Triage Bot
描述: 每天自动分类新 Issue，分配标签并给出优先级建议
```

## Step 2: 定义 Agent 行为

```
你是一个 Issue 分诊 Agent。每次执行时：

1. 获取过去 24 小时内新建的所有 GitHub Issue
2. 对每个 Issue 进行分类：

   ## 分类规则
   
   ### 类型标签
   - bug: 描述中包含"错误"、"失败"、"崩溃"、"异常"、"不工作"
   - feature: 描述中包含"新增"、"支持"、"功能"、"需求"
   - docs: 描述中包含"文档"、"说明"、"README"
   - question: 描述中包含"如何"、"为什么"、"请问"
   
   ### 优先级
   - P0 紧急: 生产环境故障、数据丢失、安全漏洞
   - P1 高: 核心功能异常、影响用户
   - P2 中: 非核心功能问题、有 workaround
   - P3 低: 体验优化、文档、非紧急
   
   ### 组件标签
   - 根据文件路径或关键词分配:
     user-service / order-service / payment-service / gateway / infra

3. 为每个 Issue 添加对应标签
4. 对 P0/P1 Issue 添加评论说明分析
5. 输出分诊报告
```

## Step 3: 设置定时执行

在 Agent 配置中：

```
定时: 每天 09:00 (工作日)
触发: Schedule → Weekdays at 9:00 AM
```

## Step 4: 验证效果

第二天检查：

1. 打开 GitHub 仓库 → Issues
2. 检查新 Issue 是否已被自动打标签
3. 检查 P0/P1 Issue 是否有分析评论

## Step 5: 创建更多自动化 Agent

```
# Agent 2: CI 失败分析
名称: CI Analyzer
触发: 每次 CI 失败时
行为: 
- 分析失败的测试
- 定位根因
- 创建 Issue 并指派

# Agent 3: 依赖更新
名称: Dependency Updater  
触发: 每周一 10:00
行为:
- 检查过时依赖
- 评估安全漏洞
- 创建升级 PR
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| Agent 无法访问 GitHub | 在 Codex 设置中连接 GitHub OAuth |
| 分类不准确 | 在 Agent 行为描述中增加更多示例 |
| 定时没触发 | 检查时区设置，确认 Agent 是 Active 状态 |
| 想手动触发一次 | 在 Chat 中发: `立即执行一次 Issue 分诊` |

## 延伸

- → [T04: 自动修复测试](T04-Codex自动修复循环.md)
- → [11-安全治理](../11-安全治理.md)
