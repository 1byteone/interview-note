# 程序员鱼皮 (liyupi) 开源生态全景剖析

> 分析日期：2026-08-22 | 数据来源：GitHub API + Web Research

---

## 一、人物画像

| 维度 | 信息 |
|------|------|
| **GitHub** | [github.com/liyupi](https://github.com/liyupi) |
| **真名** | 李玉平 (Yupi Li) |
| **网名** | 程序员鱼皮 |
| **所在地** | 中国 上海 |
| **职业背景** | 前腾讯全栈开发 → 科技公司创始人 |
| **标语** | "speak less, do more" |
| **主站** | [codefather.cn](https://www.codefather.cn) — 编程导航 |
| **粉丝** | 24,300+ GitHub Followers |
| **公开仓库** | 109+ |
| **总 Star** | **72,456+** |
| **总 Fork** | **14,245+** |

---

## 二、核心数据总览

### 2.1 仓库规模

| 指标 | 数值 |
|------|------|
| 总仓库数 | 110 |
| 原创仓库 | 93 |
| Fork 仓库 | 17 |
| 已归档 | 0 |
| 首次注册 | 2017-02-26 |
| 最近活跃 | 2026-08-21 |

### 2.2 语言分布 (93 个原创仓库)

```
Java         ████████████████  28 (30.1%)   ← 核心后端
JavaScript   ████████████      14 (15.1%)
TypeScript   ████████████      14 (15.1%)
Vue          ████████           9 ( 9.7%)
C++          ████               4 ( 4.3%)
HTML         ███                3 ( 3.2%)
Python       ███                3 ( 3.2%)
Shell/CSS/PS ██                 3 ( 3.2%)
纯文档/无代码 ██                15 (16.1%)
```

**关键洞察**: Java 是技术项目的核心语言，前端 JS/TS/Vue 合计占 39.9%，呈现典型的 **Java 后端 + Vue/React 前端** 全栈架构。

### 2.3 Topic 标签热度 Top 15

| 标签 | 出现次数 | 标签 | 出现次数 |
|------|---------|------|---------|
| frontend | 35 | react | 9 |
| backend | 33 | web | 9 |
| java | 33 | deepseek | 8 |
| ai | 23 | redis | 8 |
| javascript | 22 | css | 7 |
| vue | 21 | algorithm | 7 |
| springboot | 20 | ant-design | 7 |
| typescript | 14 | vibe-coding | 6 |

---

## 三、项目矩阵 — Star Top 25

### 3.1 天花板级项目 (>3000 ⭐)

| # | 项目 | ⭐ | Fork | 语言 | 创建 | 最后推送 | 定位 |
|---|------|-----|------|------|------|---------|------|
| 1 | **ai-guide** | 19,013 | 2,154 | JS | 2025-02 | 2026-08-21 | AI 资源大全 + Vibe Coding 教程 |
| 2 | **codefather** | 8,321 | 1,330 | TS | 2021-04 | 2026-07-11 | 编程学习路线图 2026 |
| 3 | **mianshiya** | 5,771 | 1,226 | TS | 2022-01 | 2026-02-04 | 面试题库 "面试鸭" |
| 4 | **sql-mother** | 4,326 | 431 | TS | 2023-08 | 2025-07-01 | 闯关式 SQL 自学教程 |
| 5 | **free-programming-resources** | 3,665 | 553 | HTML | 2021-02 | 2026-03-23 | 免费编程资源合集 |
| 6 | **sql-generator** | 3,427 | 691 | Vue | 2022-05 | 2024-01-17 | SQL 生成器 |

### 3.2 明星级项目 (1000-3000 ⭐)

| # | 项目 | ⭐ | 语言 | 定位 |
|---|------|-----|------|------|
| 7 | **code-nav** | 2,730 | JS | 编程导航社区前端 |
| 8 | **yu-ai-agent** | 2,622 | Java | Spring Boot 3 + Spring AI ReAct Agent |
| 9 | **yuindex** | 2,124 | TS | 极客范浏览器主页 (Vue3+Node) |
| 10 | **sql-father-backend** | 2,098 | Java | SQL 父亲后端 |
| 11 | **yu-ai-code-mother** | 1,892 | Java | AI 代码生成平台 (微服务) |
| 12 | **yulegeyu** | 1,830 | TS | 乐格域项目 |
| 13 | **sql-father-frontend** | 1,496 | TS | SQL 父亲前端 |
| 14 | **daxigua** | 1,430 | JS | 大西瓜游戏 |
| 15 | **liyupi** (Profile) | 1,089 | — | 个人主页 README |
| 16 | **yu-picture** | 1,033 | Java | 图片生成平台 |

### 3.3 成长级项目 (500-1000 ⭐)

| 项目 | ⭐ | 语言 | 定位 |
|------|-----|------|------|
| yu-auto-reply | 891 | Java | ChatGPT 自动回复 |
| ai-code-helper | 733 | Vue | AI 编程助手 |
| yupi-hot-monitor | 698 | TS | AI 热点监控 |
| yuzi-generator | 669 | Java | 代码生成器 |
| yu-rpc | 603 | Java | 自研 RPC 框架 |
| mianshiya-next | 511 | Java | 面试鸭后端 v2 |

---

## 四、项目分类体系

### 4.1 🤖 AI 应用类 (28 个仓库，2025-2026 核心方向)

这是鱼皮 2025 年后的 **战略转型方向**。

| 层级 | 项目 | 技术栈 | 功能 |
|------|------|--------|------|
| **AI 基础设施** | ai-guide | JS/文档 | AI 资源大全 + Vibe Coding 教程 |
| **AI Agent** | yu-ai-agent | Spring Boot 3 + Spring AI | ReAct 模式 Agent |
| **AI 代码生成** | yu-ai-code-mother | Spring Boot 3 + LangChain4j + LangGraph4j | 大厂级 AI 应用生成平台 |
| **AI 编程助手** | ai-code-helper | Spring Boot 3.5 + LangChain4j | AI 编程辅助 |
| **AI 自动回复** | yu-auto-reply | Java + ChatGPT API | 微信自动回复 |
| **AI 热点监控** | yupi-hot-monitor | Node.js + OpenRouter | AI 热点追踪 |
| **AI 知识提炼** | yupi-skill | Agent Skill | AI 编程技能提炼 |
| **AI 翻译** | ai-translator-extension | Chrome Extension | AI 翻译插件 |
| **AI 投资** | ai-investor | OpenAI Agents SDK | AI 副业验证 |
| **AI 视频** | niulai-video-generator | Three.js + DeepSeek | 3D 动画生成 |
| **AI 桌宠** | dsh-kun-like-pet | DeepSeek Harness | 桌面宠物插件 |
| **AI 学习** | yu-ai-learn | Taro + React + FastAPI | AI 学习小程序 |

### 4.2 🎓 教育/资源类

| 项目 | ⭐ | 类型 |
|------|-----|------|
| codefather | 8,321 | 编程学习路线图 |
| mianshiya | 5,771 | 面试题库 |
| free-programming-resources | 3,665 | 免费资源合集 |
| sql-mother | 4,326 | SQL 闯关学习 |
| openclaw-guide | 186 | OpenClaw 中文文档 |
| coder-test | 200 | 程序员性格测试 |

### 4.3 🛠️ 工具/平台类

| 项目 | ⭐ | 类型 |
|------|-----|------|
| sql-generator | 3,427 | SQL 生成器 |
| code-nav | 2,730 | 编程导航社区 |
| yuindex | 2,124 | 极客浏览器主页 |
| yu-picture | 1,033 | 图片生成 |
| yu-auto-reply | 891 | 自动回复 |
| yuzi-generator | 669 | 代码生成器 |
| zhuanglema | 106 | AI 软件安装助手 |
| github-claw | 68 | GitHub AI Agent |

### 4.4 🎮 趣味/实验类

| 项目 | ⭐ | 类型 |
|------|-----|------|
| daxigua | 1,430 | 大西瓜游戏 |
| yulegeyu | 1,830 | 乐格域 |
| binding-of-isaac-webgame | 65 | 扎楼风格 Roguelike |
| cbti-test | 42 | 程序员性格测试 |

### 4.5 🔧 Java 底层/框架类

| 项目 | ⭐ | 类型 |
|------|-----|------|
| yu-rpc | 603 | 自研 RPC 框架 |
| sql-father-backend | 2,098 | SQL 后端 |
| yu-ai-code-mother | 1,892 | 微服务 AI 平台 |
| mianshiya-next | 511 | 面试鸭后端 v2 |
| yudada | 389 | ChatGLM 对话 |
| springboot-guide | — | Spring Boot 指南 |
| java-concurrent | — | Java 并发编程 |
| Design-Model | — | 设计模式 |

---

## 五、职业发展时间线

```
2017 ─── 入门期：Flappy Bird, 个人网站
  │
2018 ─── 学习期：设计模式, Spring IOC/MVC, ES, RabbitMQ (15 repos)
  │
2019 ─── 刷题期：LeetCode C++, POJ, BI-Learning
  │
2020 ─── 求职期：后端面试, Better-Coder
  │
2021 ─── 爆发期：★ 24 repos — code-nav, daxigua, sql-generator
  │         ↑ 入职腾讯 → 社区工具爆发
  │
2022 ─── 独立期：mianshiya, yuindex, yulegeyu, sql-father
  │         ↑ 离开腾讯，全职创业
  │
2023 ─── 产品期：yu-auto-reply, ceshiya, sql-mother
  │
2024 ─── 深耕期：yu-rpc, yu-picture, mianshiya-next, yudada
  │
2025 ─── ★ AI 转型：ai-guide, yu-ai-agent, yu-ai-code-mother
  │         ↑ 全面拥抱 AI，LangChain4j / Spring AI
  │
2026 ─── AI 爆发：★ 15 repos — ai-guide 19K⭐, MCP/A2A/Agent Skills
           ↑ 从教育者进化为 AI 应用架构师
```

---

## 六、商业生态模型

```
                    ┌─────────────────────────┐
                    │     编程导航 codefather.cn  │
                    │   (付费会员 + 课程 + 社区)   │
                    └────────────┬────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
    ┌─────────▼────────┐ ┌──────▼──────┐ ┌────────▼────────┐
    │  免费开源项目      │ │  B站视频内容  │ │  SaaS 工具       │
    │  GitHub 引流      │ │  品牌建设     │ │  老鱼简历        │
    │  (72K+ stars)    │ │  (100K+ 粉)  │ │  面试鸭          │
    └──────────────────┘ └─────────────┘ │  AI 模拟面试      │
                                         └─────────────────┘
```

**变现路径**: 开源引流 → 社区沉淀 → 付费会员 → SaaS 工具 → AI 产品

---

## 七、核心价值与启示

### 7.1 对 Java 后端开发者的学习价值

| 价值维度 | 具体项目 | 学什么 |
|---------|---------|--------|
| **Spring Boot 3 + AI** | yu-ai-code-mother | LangChain4j 集成、Tool Calling、SSE 流式、LangGraph4j 工作流 |
| **Spring Cloud 微服务** | yu-ai-code-mother | Gateway、Nacos、Sentinel、Seata、监控 |
| **自研框架** | yu-rpc | RPC 原理、序列化、网络通信、SPI 扩展 |
| **数据库实战** | sql-father, sql-mother | SQL 教学设计、在线执行引擎 |
| **全栈架构** | yuindex, yu-picture | Vue 3 + Spring Boot + Node.js 全链路 |
| **面试准备** | mianshiya | 10,000+ 题库覆盖 Java/算法/系统设计 |

### 7.2 鱼皮的成功公式

```
技术深度 × 内容输出 × 社区运营 × 商业闭环 = 开发者影响力
```

1. **技术深度**: 从 Java 基础 → 微服务 → AI Agent，持续迭代
2. **内容输出**: GitHub README 即教程，每个项目都有完整文档
3. **社区运营**: 编程导航 + 知识星球 + B站 + 微信公众号
4. **商业闭环**: 免费引流 → 付费转化 → SaaS 变现

---

*文档由 DeepSeek-V4-Pro 自动生成*
