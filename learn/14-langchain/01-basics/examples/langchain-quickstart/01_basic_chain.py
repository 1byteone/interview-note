# -*- coding: utf-8 -*-
"""
01_basic_chain.py
=================
LangChain 快速上手：LCEL（LangChain Expression Language）基础用法

演示内容：
1. ChatOpenAI 模型初始化（API Key 说明见下）
2. PromptTemplate 提示词模板
3. StrOutputParser 字符串输出解析器
4. 用 | 运算符（LCEL 管道）组合成简单 LLMChain
5. RunnableParallel 并行执行
6. RunnablePassthrough 透传

【环境准备】
- 安装依赖：pip install -r requirements.txt
- 复制 .env.example 为 .env，填入你的 OPENAI_API_KEY
- 也可以直接在环境变量中设置 OPENAI_API_KEY
- 该示例使用 OpenAI 兼容接口，可无缝替换为 DeepSeek、Qwen 等
  使用 OpenAI 协议的服务（只需修改 base_url 和 model 名称）

运行方式：
    python 01_basic_chain.py
"""

import os

from dotenv import load_dotenv

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import PromptTemplate
from langchain_core.runnables import RunnableParallel, RunnablePassthrough
from langchain_openai import ChatOpenAI

# ---- 加载 .env 中的环境变量（OPENAI_API_KEY 等）----
load_dotenv()

# 注意：这里需要真实的 API Key 才能运行。
# 如果没有 Key，程序会在运行时抛出 AuthenticationError。
# 建议在 .env 文件中配置，而不是硬编码在代码里。
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

# ---- 1. 初始化 ChatOpenAI ----
# model：模型名称（OpenAI 为 "gpt-4o-mini" 等，兼容服务可填 "deepseek-chat" 等）
# temperature：生成随机性，0 表示尽量确定，1 表示较发散
# 若使用兼容服务，可传入 base_url，例如：
#   base_url="https://api.deepseek.com/v1"
llm = ChatOpenAI(
    model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
    temperature=0.7,
    api_key=OPENAI_API_KEY,  # 留为 None 时会自动读取环境变量
)


# ---- 2. 定义 PromptTemplate 提示词模板 ----
# 使用 {topic} 占位符，运行时需要传入参数
prompt = PromptTemplate(
    template="用一句话介绍{topic}，要求简洁有趣。",
    input_variables=["topic"],
)


# ---- 3. 定义输出解析器 ----
# StrOutputParser 把模型输出的 AIMessage 解析为纯字符串
# 在链式调用中非常常用
parser = StrOutputParser()


# =====================================================================
# 4. 使用 LCEL 的 | 运算符组合链
#    prompt -> llm -> parser，每个环节的输出作为下一环节的输入
# =====================================================================
# 方式一：直接链式调用（推荐，最简洁）
simple_chain = prompt | llm | parser

# 方式二：调用链对象
# chain 是一个 Runnable，调用方式为 chain.invoke({"topic": "..."})
result = simple_chain.invoke({"topic": "人工智能"})
print("=" * 60)
print("【简单链】自我介绍生成结果：")
print(result)
print()


# =====================================================================
# 5. RunnablePassthrough 透传
#    不改变输入，原样传递，常用于：
#    - 把原始输入一并传给后续节点（拼接上下文）
#    - 在并行分支里保留原始输入
# =====================================================================
passthrough_chain = (
    {"topic": RunnablePassthrough(), "prefix": RunnablePassthrough()}
    | PromptTemplate(
        template="{prefix}：请用一句话介绍{topic}。",
        input_variables=["prefix", "topic"],
    )
    | llm
    | parser
)
result2 = passthrough_chain.invoke("Deep Learning")
print("=" * 60)
print("【RunnablePassthrough】透传演示：")
print(result2)
print()


# =====================================================================
# 6. RunnableParallel 并行执行
#    多个分支同时跑（LangChain 内部会并发执行），最后合并成一个 dict
# =====================================================================
# 定义两个并行分支：分别生成 "overview" 与 "fun_fact"
branch_1 = RunnablePassthrough()  # 原样透传
branch_2 = (
    PromptTemplate(template="关于{topic}，说一个有趣的小知识。")
    | llm
    | parser
)

parallel_chain = RunnableParallel(
    overview=branch_1,
    fun_fact=branch_2,
)

result3 = parallel_chain.invoke({"topic": "区块链"})
print("=" * 60)
print("【RunnableParallel】并行执行结果：")
print(f"overview(原始输入): {result3['overview']}")
print(f"fun_fact: {result3['fun_fact']}")
print()


# =====================================================================
# 7. 组合用法：并行分支 + 汇总链
#    两个模型各自输出，再用一个 Prompt 汇总，体现可组合性
# =====================================================================
summary_prompt = PromptTemplate(
    template=(
        "已有两段描述：\n"
        "1. {desc_one}\n"
        "2. {desc_two}\n"
        "请把以上两段合成一段连贯的介绍。"
    ),
    input_variables=["desc_one", "desc_two"],
)

full_chain = (
    RunnableParallel(
        desc_one=PromptTemplate(template="介绍{topic}的定义。") | llm | parser,
        desc_two=PromptTemplate(template="介绍{topic}的应用场景。") | llm | parser,
    )
    | summary_prompt
    | llm
    | parser
)
result4 = full_chain.invoke({"topic": "大语言模型"})
print("=" * 60)
print("【RunnableParallel + 汇总链】组合演示：")
print(result4)
print()


# =====================================================================
# 附：Streaming 流式输出演示（LLM 逐字输出）
# =====================================================================
print("=" * 60)
print("【流式输出】逐 token 打印：")
for chunk in simple_chain.stream({"topic": "微服务"}):
    print(chunk, end="", flush=True)
print("\n")