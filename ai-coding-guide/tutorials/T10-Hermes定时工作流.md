# T10: Hermes Agent 定时自动化工作流

> **[← 教程目录](README.md) | 工具: Hermes Agent | 时长: ~20min**

---

## Goal

配置 Hermes Agent **每天自动执行**代码质量检查，并将报告推送到 Slack。

## 前置条件

- Hermes 已安装并配置好记忆（见 T09）
- Slack Webhook URL（可选）

## Step 1: 创建每日检查 Skill

```bash
cat > ~/.hermes/skills/daily-code-review/SKILL.md << 'EOF'
---
name: daily-code-review
description: 每日代码质量检查，扫描新增代码并生成报告
triggers: daily review, code quality, PR review
---

# 每日代码质量检查

## 执行流程

1. **获取昨日提交**
   - 运行 `git log --since="yesterday" --oneline`
   - 获取所有新增/修改的文件

2. **逐文件审查**
   对每个变更的 .java 文件：
   - 检查 NPE 风险
   - 检查异常处理完整性
   - 检查 SQL 注入
   - 检查日志规范
   - 检查并发安全

3. **生成报告**
   输出格式：
   ```
   # Daily Code Review Report - {date}
   
   ## 提交摘要
   - 新增文件: X 个
   - 修改文件: Y 个
   - 涉及服务: user-service, order-service
   
   ## 发现问题
   | 严重度 | 文件 | 行号 | 问题 | 建议 |
   |--------|------|------|------|------|
   | ERROR | OrderService.java | L45 | NPE 风险 | 使用 Optional |
   
   ## 统计
   - ERROR: X 个
   - WARNING: Y 个
   - INFO: Z 个
   ```

4. **保存报告**
   - 保存到 ~/reports/code-review-{date}.md
   - 如配置 Slack Webhook，推送摘要
EOF
```

## Step 2: 配置 Hermes 定时任务

在 Hermes CLI 中：

```
/schedule create
  name: daily-code-review
  cron: 0 9 * * 1-5
  skill: daily-code-review
  working_dir: ~/code/order-service
```

或通过配置文件：

```yaml
# ~/.hermes/config.yaml
schedules:
  - name: daily-code-review
    cron: "0 9 * * 1-5"  # 每天早9点，工作日
    skill: daily-code-review
    working_dir: ~/code/order-service
    notify:
      - type: chat
      - type: slack
        webhook: https://hooks.slack.com/services/xxx
```

## Step 3: 手动触发测试

```
/schedule run daily-code-review
```

Hermes 会：
1. 进入项目目录
2. 执行 git log 获取昨日提交
3. 逐文件审查
4. 生成报告
5. 保存到 ~/reports/
6. 推送 Slack 通知（如果配置了）

## Step 4: 查看历史报告

```bash
# 查看今天的报告
cat ~/reports/code-review-$(date +%Y-%m-%d).md

# 或在 Hermes 中
hermes sessions search "daily-code-review"
```

## Step 5: 扩展——创建更多定时任务

```yaml
# ~/.hermes/config.yaml
schedules:
  # 每日代码审查
  - name: daily-code-review
    cron: "0 9 * * 1-5"
    skill: daily-code-review
    working_dir: ~/code/order-service

  # 每周依赖安全检查
  - name: weekly-security-audit
    cron: "0 10 * * 1"
    skill: security-audit
    working_dir: ~/code/order-service

  # 每日测试覆盖率检查
  - name: daily-coverage-check
    cron: "0 11 * * 1-5"
    skill: coverage-check
    working_dir: ~/code/order-service
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| 定时任务没执行 | `hermes schedule list` 检查状态 |
| Slack 通知没收到 | 测试 Webhook: `curl -X POST <webhook> -d '{"text":"test"}'` |
| 想暂停某个任务 | `hermes schedule disable daily-code-review` |
| 想修改执行时间 | `hermes schedule edit daily-code-review cron="0 10 * * 1-5"` |

## 延伸

- → [T09: 记忆与 Skill](T09-Hermes记忆与Skill.md)
- → [05-Hermes 详解](../05-Hermes.md)
