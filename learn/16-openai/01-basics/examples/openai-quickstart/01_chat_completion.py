"""
OpenAI Chat Completions API 快速入门
=====================================

演示内容：
- OpenAI 客户端初始化
- Chat Completions 基础用法
- 流式响应（Streaming）
- System / User / Assistant 角色
- Temperature、max_tokens 等参数

使用前：
  1. pip install openai python-dotenv
  2. 在同目录创建 .env 文件，写入：OPENAI_API_KEY=sk-xxxxxxxxxxxxx
  3. python 01_chat_completion.py
"""

import os
from dotenv import load_dotenv
from openai import OpenAI

# ============================================================
# 1. 客户端初始化
# ============================================================
# 从 .env 文件加载 API Key（推荐方式，避免硬编码）
load_dotenv()

# ⚠️ 核心：需要有效的 OpenAI API Key
# 方式一：从环境变量读取（推荐）
client = OpenAI()  # 自动读取 OPENAI_API_KEY 环境变量

# 方式二：显式传入（不推荐，有泄露风险）
# client = OpenAI(api_key="sk-xxxxxxxxxxxxx")

# 方式三：使用自定义 base_url（兼容第三方代理/本地部署）
# client = OpenAI(base_url="https://your-proxy.com/v1", api_key="sk-xxx")


# ============================================================
# 2. 基础对话：最简单的 Chat Completions
# ============================================================
def basic_chat():
    """最基础的对话请求"""
    print("=" * 60)
    print("【基础对话】")
    print("=" * 60)

    # messages 是一个列表，每个元素是一个 dict，包含 role 和 content
    response = client.chat.completions.create(
        model="gpt-4o",              # 模型名称
        messages=[
            # User 角色：用户发送的消息
            {"role": "user", "content": "用一句话介绍什么是大语言模型？"}
        ],
    )

    # 返回的是 ChatCompletion 对象
    # response.choices[0].message.content 包含模型回复的文本
    answer = response.choices[0].message.content
    print(f"回复: {answer}\n")

    # 查看 token 用量
    usage = response.usage
    print(f"Token 用量 — 输入: {usage.prompt_tokens}, "
          f"输出: {usage.completion_tokens}, "
          f"总计: {usage.total_tokens}\n")


# ============================================================
# 3. 多轮对话：System / User / Assistant 角色
# ============================================================
def multi_turn_chat():
    """
    三种角色说明：
    - system   : 系统提示，设定 AI 的行为和身份，通常放在最前面
    - user     : 用户的消息
    - assistant: AI 之前生成的回复（用于维持上下文）
    """
    print("=" * 60)
    print("【多轮对话 — 角色演示】")
    print("=" * 60)

    messages = [
        # system 角色：设定角色人设
        {
            "role": "system",
            "content": "你是一位精通中国历史的学者，回答简洁准确，使用中文。"
        },
        # 第一轮 user + assistant
        {"role": "user", "content": "唐朝的开国皇帝是谁？"},
        {"role": "assistant", "content": "唐朝的开国皇帝是唐高祖李渊。"},
        # 第二轮 user（AI 会根据上下文继续回答）
        {"role": "user", "content": "他是在哪一年称帝的？"},
    ]

    response = client.chat.completions.create(
        model="gpt-4o",
        messages=messages,
    )

    answer = response.choices[0].message.content
    print(f"回复: {answer}\n")


# ============================================================
# 4. 流式响应（Streaming）
# ============================================================
def streaming_chat():
    """
    流式输出：模型每生成一个 token 就立即返回，而非等全部生成完毕。
    适用场景：实时聊天界面、打字机效果、长文本生成。
    """
    print("=" * 60)
    print("【流式响应 — 打字机效果】")
    print("=" * 60)

    # stream=True 开启流式模式
    stream = client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {"role": "user", "content": "写一首关于 Spring Boot 的七言绝句，用中文。"}
        ],
        stream=True,  # ← 关键参数
    )

    # 遍历流式 chunk，每个 chunk 包含一个增量 token
    print("回复: ", end="", flush=True)
    for chunk in stream:
        # chunk.choices[0].delta.content 可能为 None（首尾 chunk）
        if chunk.choices[0].delta.content is not None:
            print(chunk.choices[0].delta.content, end="", flush=True)
    print("\n")  # 换行


# ============================================================
# 5. 关键参数详解
# ============================================================
def parameter_demo():
    """演示常用的请求参数"""
    print("=" * 60)
    print("【关键参数演示】")
    print("=" * 60)

    response = client.chat.completions.create(
        model="gpt-4o",

        messages=[
            {"role": "system", "content": "你是一个创意写作助手。"},
            {"role": "user", "content": "给我三个 Spring Boot 项目名称的创意。"},
        ],

        # --- 核心参数 ---

        # temperature: 控制随机性（0 ~ 2）
        #   0     = 确定性输出，每次结果几乎一样
        #   0.7   = 适度创意（推荐一般场景）
        #   1.0+  = 高度随机，更有创意但可能离谱
        temperature=0.7,

        # max_tokens: 限制回复的最大 token 数（防止输出过长）
        max_tokens=256,

        # top_p: 另一种采样控制，和 temperature 二选一使用更佳
        # top_p=0.9,

        # n: 生成多少个候选回复（返回 choices 列表长度）
        n=1,

        # stop: 遇到指定字符串时停止生成
        # stop=["\n\n"],

        # presence_penalty: 惩罚已出现的 token，鼓励话题多样性（-2.0 ~ 2.0）
        # presence_penalty=0.5,

        # frequency_penalty: 惩罚高频 token，减少重复（-2.0 ~ 2.0）
        # frequency_penalty=0.3,
    )

    for i, choice in enumerate(response.choices):
        print(f"候选 {i + 1}: {choice.message.content}")
    print()


# ============================================================
# 6. 模型选择速查
# ============================================================
def model_comparison():
    """常见模型对比（截至 2025 年初）"""
    print("=" * 60)
    print("【模型选择速查】")
    print("=" * 60)
    models = {
        "gpt-4o":          "旗舰多模态模型，性价比最高",
        "gpt-4o-mini":     "轻量版，速度快、价格低",
        "gpt-4-turbo":     "支持 128K 上下文的 GPT-4 变体",
        "o1":              "推理模型，擅长数学/逻辑/代码",
        "o3-mini":         "轻量推理模型，成本更低",
    }
    for name, desc in models.items():
        print(f"  {name:<16s} — {desc}")
    print()


# ============================================================
# 主函数
# ============================================================
if __name__ == "__main__":
    # ⚠️ 运行前请确保 .env 文件中已配置 OPENAI_API_KEY
    print("⚠️  请确保已在 .env 文件中配置 OPENAI_API_KEY\n")

    basic_chat()           # 基础对话
    multi_turn_chat()      # 多轮对话
    streaming_chat()       # 流式响应
    parameter_demo()       # 参数演示
    model_comparison()     # 模型对比
