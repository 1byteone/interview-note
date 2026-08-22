# mini-blog：新闻摘要 Agent

> 一个基于 LangChain 的轻量级新闻摘要 Agent，支持搜索新闻、LLM 摘要生成、结构化输出解析。

---

## 项目概述

**mini-blog** 是一个独立小项目，核心功能：

1. 搜索指定主题的最新新闻
2. 用 LLM 对新闻进行智能摘要
3. 将摘要解析为结构化格式（标题、要点、分类）
4. 输出可直接用于博客发布的 Markdown 内容

**技术栈：** LangChain + OpenAI + SerpAPI（或 DuckDuckGo 搜索）

---

## 项目结构

```
mini-blog/
├── README.md              # 项目说明（本文档）
├── requirements.txt       # 依赖清单
├── main.py                # 入口：组装 Agent 并运行
├── agent/
│   ├── __init__.py
│   ├── news_agent.py      # 新闻搜索 Agent 定义
│   └── tools.py           # 自定义工具（搜索、解析）
├── chains/
│   ├── __init__.py
│   └── summary_chain.py   # 摘要生成 Chain + 输出解析器
├── models/
│   ├── __init__.py
│   └── schemas.py         # Pydantic 输出模型
└── utils/
    ├── __init__.py
    └── formatter.py       # Markdown 格式化工具
```

---

## 步骤说明

### 步骤 1：定义数据模型

使用 Pydantic 定义摘要的结构化输出格式，确保 LLM 输出可被解析。

```python
# models/schemas.py
from pydantic import BaseModel, Field
from typing import List

class NewsSummary(BaseModel):
    """单条新闻摘要"""
    title: str = Field(description="新闻标题")
    key_points: List[str] = Field(description="核心要点（3-5 条）")
    category: str = Field(description="新闻分类：科技/财经/社会/国际")
    sentiment: str = Field(description="情感倾向：正面/负面/中性")

class DigestReport(BaseModel):
    """汇总报告"""
    topic: str = Field(description="搜索主题")
    date: str = Field(description="生成日期")
    total_articles: int = Field(description="文章数量")
    summaries: List[NewsSummary] = Field(description="新闻摘要列表")
```

### 步骤 2：构建搜索工具

```python
# agent/tools.py
from langchain.tools import tool
import requests

@tool
def search_news(query: str, max_results: int = 5) -> str:
    """搜索指定主题的最新新闻，返回标题和链接列表"""
    # 使用 DuckDuckGo 搜索（无需 API Key）
    url = "https://api.duckduckgo.com/"
    params = {"q": query, "format": "json", "max_results": max_results}
    try:
        resp = requests.get(url, params=params, timeout=10)
        data = resp.json()
        results = []
        for item in data.get("Results", [])[:max_results]:
            results.append(f"- [{item['Title']}]({item['FirstURL']})")
        return "\n".join(results) if results else "未找到相关新闻"
    except Exception as e:
        return f"搜索失败：{str(e)}"
```

### 步骤 3：构建摘要 Chain + 输出解析器

```python
# chains/summary_chain.py
from langchain.chains import LLMChain
from langchain.output_parsers import PydanticOutputParser
from langchain.prompts import ChatPromptTemplate, HumanMessagePromptTemplate
from langchain_openai import ChatOpenAI
from models.schemas import DigestReport

def build_summary_chain() -> LLMChain:
    """构建带输出解析的摘要链"""
    parser = PydanticOutputParser(pydantic_object=DigestReport)

    prompt = ChatPromptTemplate.from_messages([
        HumanMessagePromptTemplate.from_template(
            "你是一个新闻编辑。请根据以下原始新闻内容生成结构化摘要报告。\n\n"
            "主题：{topic}\n"
            "原始新闻内容：\n{news_content}\n\n"
            "请按照以下格式输出：\n{format_instructions}"
        )
    ])

    chain = LLMChain(
        llm=ChatOpenAI(model="gpt-4", temperature=0.3),
        prompt=prompt.partial(format_instructions=parser.get_format_instructions()),
        output_parser=parser,
        output_key="digest"
    )
    return chain
```

### 步骤 4：组装 Agent

```python
# agent/news_agent.py
from langchain.agents import create_react_agent, AgentExecutor
from langchain.prompts import PromptTemplate
from langchain_openai import ChatOpenAI
from agent.tools import search_news
from chains.summary_chain import build_summary_chain

class NewsDigestAgent:
    def __init__(self):
        self.llm = ChatOpenAI(model="gpt-4", temperature=0)
        self.tools = [search_news]
        self.summary_chain = build_summary_chain()

        prompt = PromptTemplate.from_template(
            "你是一个新闻摘要助手。\n"
            "可用工具：{tools}\n"
            "工具名称：{tool_names}\n"
            "用户请求：{input}\n"
            "思考过程：{agent_scratchpad}"
        )

        agent = create_react_agent(self.llm, self.tools, prompt)
        self.executor = AgentExecutor(
            agent=agent,
            tools=self.tools,
            verbose=True,
            max_iterations=3,
            handle_parsing_errors=True
        )

    def run(self, topic: str) -> str:
        """执行：搜索 -> 摘要 -> 格式化输出"""
        # 第一步：搜索新闻
        raw_result = self.executor.invoke({"input": f"搜索关于「{topic}」的最新新闻"})
        news_content = raw_result["output"]

        # 第二步：生成结构化摘要
        digest = self.summary_chain.invoke({
            "topic": topic,
            "news_content": news_content
        })

        return digest["digest"]
```

### 步骤 5：格式化输出

```python
# utils/formatter.py
from models.schemas import DigestReport

def format_to_markdown(report: DigestReport) -> str:
    """将摘要报告格式化为 Markdown"""
    lines = [
        f"# 📰 {report.topic} 新闻摘要",
        f"**生成日期：** {report.date}  |  **文章数：** {report.total_articles}",
        "---",
        ""
    ]
    for i, s in enumerate(report.summaries, 1):
        lines.append(f"## {i}. {s.title}")
        lines.append(f"**分类：** {s.category}  |  **情感：** {s.sentiment}")
        lines.append("")
        lines.append("### 核心要点")
        for point in s.key_points:
            lines.append(f"- {point}")
        lines.append("")
    return "\n".join(lines)
```

### 步骤 6：主入口

```python
# main.py
from agent.news_agent import NewsDigestAgent
from utils.formatter import format_to_markdown

def main():
    agent = NewsDigestAgent()

    topic = input("请输入新闻主题（如：AI 大模型、新能源车）：").strip()
    if not topic:
        topic = "AI 大模型"

    print(f"\n正在搜索「{topic}」相关新闻...\n")
    report = agent.run(topic)

    markdown = format_to_markdown(report)
    print("\n" + "=" * 50)
    print(markdown)

    # 保存到文件
    filename = f"output_{topic.replace(' ', '_')}.md"
    with open(filename, "w", encoding="utf-8") as f:
        f.write(markdown)
    print(f"\n已保存到：{filename}")

if __name__ == "__main__":
    main()
```

### 步骤 7：依赖清单

```txt
# requirements.txt
langchain>=0.3.0
langchain-openai>=0.1.0
pydantic>=2.0.0
requests>=2.31.0
openai>=1.0.0
```

---

## 运行方式

```bash
# 1. 安装依赖
pip install -r requirements.txt

# 2. 设置环境变量
export OPENAI_API_KEY="your-api-key"

# 3. 运行
python main.py
# 输入：AI 大模型
```

---

## 运行示例输出

```
正在搜索「AI 大模型」相关新闻...

==================================================
# 📰 AI 大模型 新闻摘要
**生成日期：** 2026-08-22  |  **文章数：** 3

---

## 1. OpenAI 发布 GPT-5，推理能力大幅提升
**分类：** 科技  |  **情感：** 正面

### 核心要点
- GPT-5 在数学推理和代码生成任务上提升显著
- 支持更长的上下文窗口（1M tokens）
- API 价格与 GPT-4 保持一致

## 2. 国内大模型厂商加速出海布局
**分类：** 科技  |  **情感：** 中性

### 核心要点
- 多家国产大模型厂商在东南亚建立数据中心
- 本地化合规成为主要挑战
- 预计 2027 年海外收入占比将达 30%

已保存到：output_AI_大模型.md
```

---

## 扩展方向

- **多源搜索：** 接入 SerpAPI、NewsAPI 等专业新闻源
- **定时任务：** 结合 Cron 每日自动生成摘要
- **多语言支持：** 通过 LLM 翻译实现多语言新闻摘要
- **RSS 订阅：** 解析 RSS Feed 作为输入源
- **Web 界面：** 用 Gradio 或 Streamlit 搭建可视化界面