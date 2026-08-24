# 实战教程总索引

> **[← 主指南目录](../README.md)**

每个教程都是一个**完整的、可跟做的实际操作案例**，包含：目标 → 前置条件 → 逐步操作 → 验证 → 常见问题。

---

## 教程列表

| # | 教程 | 工具 | 场景 | 时长 |
|---|------|------|------|------|
| T01 | [Spring Boot 项目从零搭建](T01-Claude-Code搭建Spring-Boot项目.md) | Claude Code | 从 Prompt 到可运行项目 | 30min |
| T02 | [CLAUDE.md 企业级配置实战](T02-Claude-Code企业级CLAUDE-MD配置.md) | Claude Code | 大型项目上下文工程 | 20min |
| T03 | [遗留代码 50K 行渐进式重构](T03-Claude-Code遗留代码重构.md) | Claude Code | Spring Boot 2→3 迁移 | 60min |
| T04 | [Codex Full Auto 自动修复测试](T04-Codex自动修复循环.md) | Codex CLI | CI 失败自动修复 | 15min |
| T05 | [Codex AGENTS.md 自定义 Review 规则](T05-Codex自定义Review规则.md) | Codex App | PR 自动审查 | 20min |
| T06 | [Codex Workspace Agent 定时任务](T06-Codex定时自动化.md) | Codex App | 每日 Issue 分诊 | 15min |
| T07 | [DeepSeek Harness 四模式体验](T07-DeepSeek-Harness模式对比.md) | DSH | Standard/Code/Minimal/Creator | 25min |
| T08 | [DeepSeek Harness 自定义插件开发](T08-DeepSeek-Harness自定义插件.md) | DSH | 用 Creator Mode 构建插件 | 30min |
| T09 | [Hermes Agent 记忆与自进化 Skill](T09-Hermes记忆与Skill.md) | Hermes | 持久记忆 + Skill 自动创建 | 20min |
| T10 | [Hermes Agent 定时自动化工作流](T10-Hermes定时工作流.md) | Hermes | 每日代码质量检查 | 20min |
| T11 | [Cursor .mdc Rules 全流程](T11-Cursor-Rules实战.md) | Cursor | Java 规则配置 + Agent Mode | 15min |
| T12 | [Cursor Agent 多文件重构](T12-Cursor-Agent多文件重构.md) | Cursor | Service 层拆分重构 | 25min |
| T13 | [MCP Server Spring Boot 实现](T13-MCP-Server实战.md) | Spring Boot + 任意工具 | 自定义 MCP Server | 30min |
| T14 | [五工具协作全流程](T14-五工具协作全流程.md) | 全部 | 一个需求用五个工具各做一段 | 60min |
| T15 | [项目工程化 AI 编程完整实践](T15-项目工程化AI编程完整实践.md) | 全部 | 团队规范→开发→CI/CD 全链路 | 90min |
| T16 | [多仓库跨服务 AI 协作](T16-多仓库跨服务AI协作.md) | Claude + Cursor + Codex | 跨仓库 FeignClient 变更 | 60min |
| T17 | [AI 辅助生产问题排查与修复](T17-AI辅助生产问题排查与修复.md) | Claude Code + Cursor | 日志分析→根因定位→修复 | 45min |

---

## 学习路径推荐

```
新手入门 → T01 → T04 → T11 → T14（2小时）
团队规范 → T02 → T05 → T15（2小时）
高级主题 → T03 → T08 → T13 → T17（3小时）
自动化运维 → T06 → T10 → T16（1.5小时）
```

## 每个教程的标准结构

```
## Goal（目标）
  一句话描述做什么

## 前置条件
  - 环境
  - 工具
  - 项目

## Step 1-N
  每步都有：
  - 操作命令 / Prompt
  - 预期输出
  - 关键解释

## 验证
  如何确认做对了

## 常见问题
  - 问题 → 解决方案

## 延伸
  - 进阶方向
  - 相关教程
```
