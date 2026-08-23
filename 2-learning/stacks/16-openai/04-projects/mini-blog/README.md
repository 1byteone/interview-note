# Mini Blog — 智能写作助手

## 项目概述

一个基于 OpenAI API 的智能写作助手，支持文章生成、内容优化、大纲规划等功能。通过 Prompt 设计、Function Calling 和流式输出，提供流畅的写作体验。

## 功能列表

| 功能 | 说明 | 技术要点 |
|------|------|----------|
| 文章生成 | 根据主题生成完整文章 | Prompt 设计 + 流式输出 |
| 大纲规划 | 生成文章大纲和结构 | Structured Output |
| 内容优化 | 优化语法、风格、可读性 | Function Calling |
| 多语言翻译 | 翻译文章到其他语言 | 模型多语言能力 |
| 续写功能 | 从指定位置继续写作 | 上下文管理 |
| 关键词提取 | 自动提取文章关键词 | Function Calling |

## 技术架构

```
用户输入
    │
    ▼
┌──────────────────────┐
│  写作助手 Service     │
│  ┌────────────────┐  │
│  │ Prompt 管理器   │  │  ← 场景化 Prompt 模板
│  └────────────────┘  │
│  ┌────────────────┐  │
│  │ Function 调度器  │  │  ← 工具调用管理
│  └────────────────┘  │
│  ┌────────────────┐  │
│  │ 流式输出管理器  │  │  ← SSE 流式响应
│  └────────────────┘  │
└──────────┬───────────┘
           │
           ▼
    OpenAI API (GPT-4o-mini / GPT-4o)
```

## 核心实现

### 1. Prompt 设计

```python
SYSTEM_PROMPTS = {
    "article": (
        "你是一个专业的写作助手。请根据用户需求生成高质量文章。\n"
        "要求：\n"
        "- 文章结构清晰，有引言、正文、结论\n"
        "- 语言流畅，符合中文表达习惯\n"
        "- 内容准确，不编造事实\n"
        "- 根据用户指定的风格调整语气\n"
        "- 适当使用小标题分段"
    ),
    "outline": (
        "你是一个写作规划师。请根据主题生成详细的文章大纲。\n"
        "要求：\n"
        "- 包含 3-5 个主要章节\n"
        "- 每个章节包含 2-3 个子要点\n"
        "- 标记每个部分的重点内容\n"
        "- 预估每个部分的字数"
    ),
    "optimize": (
        "你是一个文字编辑。请优化以下文本。\n"
        "检查维度：\n"
        "- 语法错误和错别字\n"
        "- 表达是否清晰流畅\n"
        "- 逻辑是否连贯\n"
        "- 用词是否准确\n"
        "- 提供优化建议和修改版本"
    ),
}
```

### 2. Function Calling 定义

```python
WRITING_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "extract_keywords",
            "description": "从文本中提取关键词",
            "parameters": {
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "要提取关键词的文本"},
                    "max_keywords": {
                        "type": "integer",
                        "description": "最大关键词数量",
                        "default": 5,
                    },
                },
                "required": ["text"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "check_grammar",
            "description": "检查文本语法和错别字",
            "parameters": {
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "要检查的文本"},
                },
                "required": ["text"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "generate_tags",
            "description": "为文章生成标签",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": {"type": "string", "description": "文章标题"},
                    "content": {"type": "string", "description": "文章内容摘要"},
                    "max_tags": {
                        "type": "integer",
                        "description": "最大标签数量",
                        "default": 5,
                    },
                },
                "required": ["title", "content"],
                "additionalProperties": False,
            },
        },
    },
]
```

### 3. 流式输出

```python
from openai import OpenAI
from typing import Generator


class WritingAssistant:
    """智能写作助手"""

    def __init__(self, api_key: str):
        self.client = OpenAI(api_key=api_key)

    def write_article(
        self,
        topic: str,
        style: str = "正式",
        word_count: int = 1000,
    ) -> Generator[str, None, None]:
        """流式生成文章"""
        messages = [
            {"role": "system", "content": SYSTEM_PROMPTS["article"]},
            {
                "role": "user",
                "content": (
                    f"主题：{topic}\n"
                    f"风格：{style}\n"
                    f"字数：约{word_count}字\n"
                    "请开始写作。"
                ),
            },
        ]

        stream = self.client.chat.completions.create(
            model="gpt-4o-mini",
            messages=messages,
            stream=True,
            temperature=0.8,
            max_tokens=word_count * 2,
        )

        for chunk in stream:
            if chunk.choices[0].delta.content:
                yield chunk.choices[0].delta.content

    def generate_outline(self, topic: str) -> dict:
        """生成文章大纲"""
        response = self.client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": SYSTEM_PROMPTS["outline"]},
                {"role": "user", "content": f"主题：{topic}"},
            ],
            response_format={"type": "json_object"},
        )
        return json.loads(response.choices[0].message.content)

    def optimize_text(self, text: str) -> dict:
        """优化文本"""
        response = self.client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": SYSTEM_PROMPTS["optimize"]},
                {"role": "user", "content": text},
            ],
            tools=WRITING_TOOLS,
            tool_choice="auto",
        )
        return response.choices[0].message.content
```

## 使用示例

```python
assistant = WritingAssistant(api_key="sk-xxx")

# 1. 生成大纲
outline = assistant.generate_outline("人工智能在电商中的应用")
print(json.dumps(outline, ensure_ascii=False, indent=2))

# 2. 流式生成文章
print("正在生成文章...")
for text in assistant.write_article(
    "人工智能在电商中的应用",
    style="专业",
    word_count=800,
):
    print(text, end="", flush=True)
```

## 扩展建议

1. **Markdown 格式**: 支持 Markdown 格式输出，直接用于博客发布
2. **SEO 优化**: 集成关键词密度检查、标题优化
3. **多轮对话**: 允许用户通过对话逐步完善文章
4. **模板管理**: 预置多种文章模板（产品评测、技术教程、新闻稿）
5. **版本对比**: 保存多个版本，支持对比和回退
6. **导出功能**: 支持导出为 Markdown、HTML、PDF 格式