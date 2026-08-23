# 面试工具

## 📚 工具概述

面试工具是本文档库的核心组件，提供题目生成、模拟面试、评估等功能。

## 🛠️ 工具列表

### 1. 题目生成器

题目生成器能够基于 Java/AI 项目自动生成各种类型的面试题。

#### 功能特性
- 支持多种题型：选择题、简答题、代码题、Bug题、场景题、设计题、深挖题
- 支持从项目代码自动分析技术栈
- 支持自定义题目难度和数量
- 支持导出为多种格式

#### 使用方法
```bash
# 基本用法
python question-generator/main.py --project-path ./my-project

# 指定题型
python question-generator/main.py --project-path ./my-project --type choice,scenario

# 指定难度
python question-generator/main.py --project-path ./my-project --difficulty senior

# 导出为 Markdown
python question-generator/main.py --project-path ./my-project --output ./output --format markdown
```

#### 目录结构
```
question-generator/
├── choice/                    # 选择题
├── short-answer/              # 简答题
├── coding/                    # 代码题
├── bug/                       # Bug题
├── scenario/                  # 场景题
├── design/                    # 设计题
├── deep-dive/                 # 深挖题
└── main.py                    # 主入口
```

### 2. 模拟面试系统

模拟面试系统提供 AI 面试官，能够进行交互式面试。

#### 功能特性
- 支持多种角色：Java Backend、AI Engineer、Full Stack 等
- 支持多种难度：Junior、Mid、Senior、Lead
- 支持追问和反馈
- 支持生成评估报告

#### 使用方法
```bash
# 基本用法
python mock-interview/main.py

# 指定角色
python mock-interview/main.py --role "Java Backend"

# 指定难度
python mock-interview/main.py --role "AI Engineer" --difficulty senior

# 交互模式
python mock-interview/main.py --interactive
```

#### 目录结构
```
mock-interview/
├── interview-agent/           # 面试 Agent
├── evaluation/                # 评估系统
└── main.py                    # 主入口
```

## 📋 题型说明

### 选择题
适用于考察基础知识和概念理解。

### 简答题
适用于考察理解和表达能力。

### 代码题
适用于考察编程能力和代码质量。

### Bug题
适用于考察调试和问题解决能力。

### 场景题
适用于考察实际应用和问题分析能力。

### 设计题
适用于考察架构设计和系统思维能力。

### 深挖题
适用于考察项目经验和深度理解。

## 🎯 使用场景

### 1. 个人学习
- 根据项目自动生成练习题
- 进行模拟面试练习
- 查漏补缺

### 2. 技术面试准备
- 针对目标岗位生成面试题
- 进行全真模拟面试
- 获取改进建议

### 3. 技术团队培训
- 根据项目生成培训材料
- 进行技术评估
- 制定学习路线

## 🔧 开发指南

### 添加新题型
1. 在 `question-generator/` 下创建新目录
2. 实现题目生成逻辑
3. 在 `main.py` 中注册

### 扩展面试官角色
1. 在 `mock-interview/interview-agent/` 下创建新角色
2. 实现角色特定的面试逻辑
3. 在 `main.py` 中注册

## 📖 相关资源

- [LangGraph 教程](https://langchain-ai.github.io/langgraph/)
- [LangChain 文档](https://python.langchain.com/)
- [OpenAI API 文档](https://platform.openai.com/docs)
