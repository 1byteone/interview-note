# 技术栈深度剖析总索引

> 面向面试的技术栈分析文档系统：以真实 GitHub 项目代码为锚点，逐项拆解各项目使用的技术栈，覆盖面试高频考点，以问答式结构呈现。

## 一、项目一览表

| 项目 | 定位 | 技术栈数量 | GitHub 仓库 |
| --- | --- | --- | --- |
| [ruoyi-ai](./ruoyi-ai/) | 企业级AI应用开发框架 | ~15 项 | https://github.com/1byteone/ruoyi-ai |
| [ai-passage-creator](./ai-passage-creator/) | AI多Agent图文创作平台 | ~10 项 | https://github.com/1byteone/ai-passage-creator-demo |
| [mewpaw-code](./mewpaw-code/) | Java CLI编码Agent | ~6 项 | https://github.com/1byteone/mewpaw-code |
| [zznursing](./zznursing/) | 养老机构物联网平台 | ~8 项 | https://github.com/1byteone/zznursing |
| [work-management-system](./work-management-system/) | 目标管理与成果验收系统原型 | 业务设计 + AI 应用架构 | — |
| [cross-cutting](./cross-cutting/) | 跨项目主题（对比与模式） | — | — |

## 二、目录结构导航

```
docs/tech-stack-analysis/
├── README.md                        # 本文件：总索引
├── ruoyi-ai/                        # 企业级AI应用开发框架
│   └── code-examples/
│       ├── multi-llm-factory/       # 多模型工厂模式示例
│       ├── rag-pipeline/            # RAG 流水线示例
│       └── supervisor-agent/        # 监督者 Agent 示例
├── ai-passage-creator/              # AI多Agent图文创作平台
│   └── code-examples/
│       ├── state-graph/             # Agent 状态图示例
│       └── image-strategy/          # 图片生成策略示例
├── mewpaw-code/                     # Java CLI编码Agent
│   └── code-examples/
│       ├── react-loop/              # ReAct 循环示例
│       └── sandbox/                 # 沙箱执行示例
├── zznursing/                       # 养老机构物联网平台
│   └── code-examples/
│       ├── iot-device/              # IoT 设备接入示例
│       └── qianfan-integration/     # 千帆大模型集成示例
├── work-management-system/           # 目标管理与成果验收系统原型
│   ├── design-doc.md                  # 业务建模、规则与面试表达
│   ├── prototype-overview.html        # AI 应用开发工程师版综合原型图
│   ├── prototype-overview.svg         # AI 应用架构图矢量交付
│   ├── prototype-composite.svg        # 九张原型聚合图矢量交付
│   └── *.html / *.png                 # 架构、流程、时序、状态、ER 与看板细节图
└── cross-cutting/                     # 跨项目主题
```

- 每个项目目录下包含：主 README（技术栈总览）、每项技术栈的独立分析文档、以及 `code-examples/` 下的真实代码片段（标注了所在仓库的源文件路径）。
- `cross-cutting/` 目录汇总跨项目的对比与归纳主题，作为进阶阅读。
- 各项目文档右上角均提供返回本总索引的导航链接。

## 三、建议阅读顺序

按「由宏观到微观、由通用到专用」的顺序阅读，形成完整知识闭环：

1. **ruoyi-ai**（企业级AI应用开发框架，~15 项）— 覆盖面最广，先建立整体技术栈认知
2. **ai-passage-creator**（AI多Agent图文创作平台，~10 项）— 聚焦多 Agent 编排与内容生成链路
3. **mewpaw-code**（Java CLI编码Agent，~6 项）— 体量最小，快速掌握 LLM 应用最小闭环
4. **zznursing**（养老机构物联网平台，~8 项）— 补充 IoT + 大模型集成视角
5. **cross-cutting**（跨项目主题）— 最后横向对比，沉淀可迁移的面试表达

## 四、面试导向教程使用指南

本系统专为「面试前冲刺」设计，遵循以下三个原则：

1. **聚焦面试高频考点**：每个技术栈文档只讲面试中最常被追问的考点（如：为什么用 Redis 而不用本地缓存、分布式事务怎么选型、RAG 检索哪一步最影响效果），不展开生产环境才需要的冷门细节。
2. **绑定项目真实代码**：每个考点都锚定 `code-examples/` 中的真实代码片段，并标注源文件路径（如 `ruoyi-ai/.../xxx.java`），回答时可以理直气壮地说「这是我们项目里的实际实现」。
3. **问答式结构**：每个文档按「面试官提问 → 参考答案 → 追问应对」组织，直接可背诵、可演练，方便自测与互相模拟。

**推荐用法：**

- 第一轮：按阅读顺序通读全部文档，建立技术栈全景。
- 第二轮：只看每篇的「面试官提问」，尝试用自己的话作答，再对照参考答案。
- 第三轮（冲刺）：重点演练 `cross-cutting/` 中的对比类问题和 STAR 亮点表达，把单个项目的知识点串成体系。
- 面试现场：项目中的每一项技术栈，都应能回答「为什么选它 / 踩过什么坑 / 怎么验证效果」这三个问题，本系统的每个考点均覆盖此三角。

## 五、跨项目主题（cross-cutting）

| 主题 | 内容 | 适用场景 |
| --- | --- | --- |
| Java AI 框架对比 | 对比多项目使用的 LLM 框架/接入方式（多模型工厂 vs 直接 SDK 等），分析选型依据与取舍 | 「框架怎么选」类问题 |
| 企业架构模式 | 沉淀各项目共用的架构模式（分层、代理、策略、事件驱动等），总结适用边界 | 「架构设计」类问题 |
| 综合 STAR 亮点 | 汇总各项目最有卖点的 2~3 个 STAR 故事，可直接套用到简历与自我介绍 | 自我介绍、项目亮点陈述 |