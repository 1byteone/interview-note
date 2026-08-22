# -*- coding: utf-8 -*-
"""
02_memory_demo.py
=================
LangChain Memory（记忆）机制演示

演示内容：
1. ConversationBufferMemory 对话缓冲记忆
2. ConversationChain 配合 memory 实现多轮对话
3. ChatMessageHistory 手工管理消息历史

★ 重要说明：在哪里需要 API Key？ ★
- 本文件中凡是调用了 llm / chain.predict 的地方都需要有效的 API Key
- 只有 "ChatMessageHistory 手工消息历史"（后半部分）可以完全离线运行，
  因为那段只涉及消息对象的增删，不调用模型
- 推荐在 .env 中配置 OPENAI_API_KEY，程序启动时会自动加载

运行方式：
    python 02_memory_demo.py
"""

import os

from dotenv import load_dotenv

from langchain_core.messages import (
    AIMessage,
    HumanMessage,
    SystemMessage,
)
from langchain.memory import ConversationBufferMemory, ChatMessageHistory
from langchain_openai import ChatOpenAI

# ---- 加载环境变量 ----
load_dotenv()

# ⚠️ 这里需要真实的 API Key。
# 未配置时模型调用会失败；ChatMessageHistory 部分不受影响。
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

llm = ChatOpenAI(
    model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
    temperature=0.7,
    api_key=OPENAI_API_KEY,
)


# =====================================================================
# 1. ConversationBufferMemory 对话缓冲记忆
#    把所有历史对话按文本形式缓存在内存中
# =====================================================================
print("=" * 60)
print("【1. ConversationBufferMemory 基础用法】")
print("=" * 60)

memory = ConversationBufferMemory()

# 手动往记忆里添加一段 "对话"（模拟历史）
memory.chat_memory.add_user_message("我叫小明，是后端工程师。")
memory.chat_memory.add_ai_message("你好小明！很高兴认识你。")

# 打印当前缓冲的历史字符串，可以看到历史被拼进一句提示词里
print("当前记忆缓冲内容：")
print(memory.buffer)
print()


# =====================================================================
# 2. ConversationChain 结合记忆实现多轮对话
#    ConversationChain 会自动把记忆拼接到 prompt 中，
#    让模型"记得"之前的对话内容
# =====================================================================
print("=" * 60)
print("【2. ConversationChain 多轮对话演示】（需要 API Key）")
print("=" * 60)

conversation_memory = ConversationBufferMemory()
chain = ConversationChain(
    llm=llm,
    memory=conversation_memory,
    verbose=False,  # 设为 True 可以看到每条 prompt 实际长什么样
)

# 第一轮：告诉模型自己的名字
response1 = chain.predict(input="我叫小明，我是后端工程师。")
print(f"用户：我叫小明，我是后端工程师。")
print(f"AI  ：{response1}")
print()

# 第二轮：不再重复自我介绍，测试模型是否"记得"
response2 = chain.predict(input="我叫什么名字？我的职业是什么？")
print(f"用户：我叫什么名字？我的职业是什么？")
print(f"AI  ：{response2}")
print()

# 查看记忆自动记录的内容（包括本轮）
print("对话结束后，记忆缓冲中的内容：")
print(conversation_memory.buffer)
print()


# =====================================================================
# 3. ChatMessageHistory 手工管理消息历史（离线，无需 API Key）
#    这是最底层、最灵活的方式：
#    你自己维护一个消息列表，然后手动构造 prompt 时引用它
# =====================================================================
print("=" * 60)
print("【3. ChatMessageHistory 手工消息历史】（本段无需 API Key）")
print("=" * 60)

# 创建消息历史对象
history = ChatMessageHistory()

# 添加系统消息：设定助手角色
history.add_message(SystemMessage(content="你是一个资深的 Java 架构师。"))

# 添加用户消息
history.add_message(HumanMessage(content="请解释一下 Spring 的 IoC 是什么？"))

# 添加 AI 消息（模拟模型回复，此处是手写占位）
history.add_ai_message("IoC 即控制反转，将对象创建与依赖注入交给容器管理……")

# 再补一轮
history.add_user_message("配合一个简单例子说明。")

print("历史消息总数：", len(history.messages))
print()
for idx, msg in enumerate(history.messages, start=1):
    # type 是消息类型：system / human / ai
    print(f"[{idx}] {msg.type.upper()}: {msg.content}")
print()

# 常用操作演示
# history.clear()          # 清空全部历史
# 你也可以这样直接追加：
history.add_messages([HumanMessage(content="最后一问"), AIMessage(content="?")])
print("追加两条消息后数量变为：", len(history.messages))
print()


# =====================================================================
# 4. 进阶：如何把 ChatMessageHistory 应用到真实请求
#    手工把历史消息 + 新问题一起打包发给 LLM
# =====================================================================
print("=" * 60)
print("【4. 手工历史 + LLM 请求】（需要 API Key）")
print("=" * 60)

custom_history = ChatMessageHistory()
custom_history.add_system_message("你是一个简短的助手，回答不超过 50 字。")
custom_history.add_human_message("今天天气怎么样？")
custom_history.add_ai_message("晴，25 度，适合出门。")

# 追加用户新问题
custom_history.add_user_message("那明天呢？")

# 直接把全部消息发给模型（模型能"看到"完整上下文）
resp = llm.invoke(custom_history.messages)
print(f"AI 回复：{resp.content}")