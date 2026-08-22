# -*- coding: utf-8 -*-
"""
02_lcel_advanced.py
===================
LangChain 高级 LCEL（LangChain Expression Language）用法

演示内容：
1. RunnableBranch 条件路由（类似 if/else）
2. RunnableMap 并行执行（RunnableParallel 的别名）
3. 自定义 RunnableLambda 函数式节点
4. 复杂链的组合与嵌套

设计说明：
- 本示例内置"假 LLM"（FakeLLM），不需要 API Key 即可完整运行，
  以便专注理解 LCEL 的结构化组合能力。
- 想要真实调用时，只需把 FakeLLM 换成 ChatOpenAI 即可。

运行方式：
    python 02_lcel_advanced.py
"""

from typing import Any, Dict, Iterable, List, Optional

from langchain_core.callbacks.manager import (
    AsyncCallbackManagerForLLMRun,
    CallbackManagerForLLMRun,
)
from langchain_core.language_models import LLM
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import PromptTemplate
from langchain_core.runnables import (
    RunnableBranch,
    RunnableLambda,
    RunnableMap,
)


# =====================================================================
# 0. 备用：一个最简单的"假 LLM"（离线运行用，无需 API Key）
#    继承 langchain_core.language_models.LLM，只实现 _call 即可
# =====================================================================
class FakeLLM(LLM):
    """离线假模型：固定返回一段文本，用于演示 LCEL 的组合能力。"""

    @property
    def _llm_type(self) -> str:
        return "fake-llm"

    def _call(
        self,
        prompt: str,
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> str:
        # 通过 prompt 中包含的关键词"模拟"不同的模型能力
        if "翻译成" in prompt:
            return "[fake翻译] 你输入的文本已被成功翻译。"
        if "总结" in prompt:
            return "[fake总结] 这是一段针对输入内容的简短总结。"
        return "[fake] 已收到你的消息。"

    async def _acall(
        self,
        prompt: str,
        stop: Optional[List[str]] = None,
        run_manager: Optional[AsyncCallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> str:
        return self._call(prompt, stop, run_manager, **kwargs)


# 用 FakeLLM 实例充当"模型"，替换为 ChatOpenAI 即可接入真实 LLM
llm = FakeLLM()

# 常用组件
parser = StrOutputParser()


# =====================================================================
# 1. RunnableLambda：把普通 Python 函数包装成 LCEL 节点
#    函数可以是任何形状：输入 -> 输出
# =====================================================================
def normalize_text(text: str) -> str:
    """清洗文本：去空白、转小写。"""
    return " ".join(text.split()).lower()


def count_words(text: str) -> int:
    """统计词数。"""
    return len(text.split(" "))


normalizer = RunnableLambda(normalize_text)
word_counter = RunnableLambda(count_words)

print("=" * 60)
print("【1. RunnableLambda 函数式节点】")
print("=" * 60)
demo_chain = normalizer | word_counter
print(f"'  Hello   LangChain  ' 清洗后：{normalizer.invoke('  Hello   LangChain  ')}")
print(f"词数统计：{demo_chain.invoke('Hello LangChain LCEL')}")
print()


# =====================================================================
# 2. RunnableBranch：条件路由（if / elif / else）
#    每个分支 = (条件函数, 运行单元)，按顺序匹配第一个为 True 的分支
# =====================================================================
print("=" * 60)
print("【2. RunnableBranch 条件路由】")
print("=" * 60)

# 定义三个模板，分别负责不同任务
en_template = PromptTemplate.from_template("把下面文本翻译成英文：{text}")
sum_template = PromptTemplate.from_template("用一句话总结下面的文本：{text}")
default_template = PromptTemplate.from_template("回答关于『{text}』的问题")

# 条件函数：输入 dict，返回 bool
def is_translate_request(inputs: dict) -> bool:
    return "translate" in inputs.get("text", "").lower() or "翻译" in inputs.get("text", "")


def is_summarize_request(inputs: dict) -> bool:
    return "summar" in inputs.get("text", "").lower() or "总结" in inputs.get("text", "")


# RunnableBranch( (条件, 子链), ..., 默认链 )
branch_chain = (
    RunnableBranch(
        (is_translate_request, en_template | llm | parser),
        (is_summarize_request, sum_template | llm | parser),
        default_template | llm | parser,
    )
)

print("翻译请求：" + branch_chain.invoke({"text": "please translate this"}))
print("总结请求：" + branch_chain.invoke({"text": "summarize this article"}))
print("普通请求：" + branch_chain.invoke({"text": "什么是 LCEL？"}))
print()


# =====================================================================
# 3. RunnableMap：并行执行多个分支（与 RunnableParallel 等价）
#    输入只有一个，同时喂给多个子链，输出为 {key: 各分支结果}
# =====================================================================
print("=" * 60)
print("【3. RunnableMap 并行执行】")
print("=" * 60)

# 三个并行分支
branch_a = PromptTemplate.from_template("为『{topic}』写一句宣传语") | llm | parser
branch_b = PromptTemplate.from_template("为『{topic}』列三个相关关键词") | llm | parser
branch_c = RunnableLambda(lambda x: f"主题长度：{len(x['topic'])} 个字符")  # 纯函数分支

parallel_map = RunnableMap(
    slogan=branch_a,
    keywords=branch_b,
    meta=branch_c,
)

pm_result = parallel_map.invoke({"topic": "LangChain"})
print(f"宣传语  ：{pm_result['slogan']}")
print(f"关键词  ：{pm_result['keywords']}")
print(f"附加信息：{pm_result['meta']}")
print()


# =====================================================================
# 4. 组合：把上述知识点串成一个完整流水线
#    清洗 -> 并行(翻译/统计) -> 路由 -> 最终输出
# =====================================================================
print("=" * 60)
print("【4. 完整组合链】")
print("=" * 60)

full_chain = (
    # 第一步：输入 dict，先做文本清洗
    RunnableMap(
        cleaned=RunnableLambda(lambda inputs: normalize_text(inputs["text"])),
        meta=RunnableLambda(lambda inputs: f"原文长度：{len(inputs['text'])}"),
    )
    # 第二步：并行分支
    | RunnableMap(
        # 分支 1：翻译
        translated=lambda outputs: (
            PromptTemplate.from_template("翻译成英文：{cleaned}")
            | llm
            | parser
        ).invoke(outputs),
        # 分支 2：词数统计（纯函数）
        word_count=lambda outputs: count_words(outputs["cleaned"]),
        # 分支 3：透传第一步的元信息
        meta=lambda outputs: outputs["meta"],
    )
)

full_result = full_chain.invoke({"text": "  Hello   LangChain   Expression   Language  "})
print("完整链输出结构：", full_result)