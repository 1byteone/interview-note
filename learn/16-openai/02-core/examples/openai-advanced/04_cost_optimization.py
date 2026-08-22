"""
OpenAI 成本优化与 Token 管理
=============================

演示内容：
1. Token 计数（tiktoken）
2. 成本估算
3. Prompt 缓存策略
4. 模型选择逻辑

核心思路：
  1. 知道每次请求花多少钱
  2. 知道哪些地方可以省钱
  3. 自动选择最优模型/策略

运行方式：
    python 04_cost_optimization.py

注意：
  本示例完全离线运行，不需要 API Key。
  但需要安装 tiktoken: pip install tiktoken
"""

import tiktoken

# ============================================================
# 1. Token 计数基础
# ============================================================
print("=" * 60)
print("1. Token 计数基础")
print("=" * 60)


def count_tokens(text: str, model: str = "gpt-4o") -> int:
    """
    使用 tiktoken 统计文本的 token 数量。

    不同模型使用不同的编码器：
    - gpt-4o, gpt-4-turbo, gpt-3.5-turbo → cl100k_base
    - o1 系列 → o200k_base
    - gpt-4 (旧版) → cl100k_base
    """
    try:
        encoding = tiktoken.encoding_for_model(model)
    except KeyError:
        # 模型不在已知列表时，使用默认编码
        encoding = tiktoken.get_encoding("cl100k_base")

    tokens = encoding.encode(text)
    return len(tokens)


# 对比中英文的 token 消耗
texts = {
    "中文": "Python 是一种高级编程语言，广泛应用于人工智能和数据科学领域。",
    "英文": "Python is a high-level programming language widely used in AI and data science.",
    "混合": "Python 是一种高级编程语言，widely used in AI and data science 等领域。",
    "长文本": "Spring Boot 的自动配置原理是基于 @Conditional 条件注解和 AutoConfiguration 机制。"
             "在启动时，Spring Boot 会根据 classpath 中的依赖、配置属性等条件，"
             "自动配置相应的 Bean。这种机制大幅减少了手动配置的工作量。"
             "开发者只需要在 application.yml 中设置少量配置，"
             "Spring Boot 就能自动完成数据源、Web 服务器、安全等组件的配置。"
             "这也是 Spring Boot 被称为「约定优于配置」的原因。",
}

print(f"{'语言':<8} {'字符数':<8} {'Token数':<8} {'比例(字符/Token)':<15}")
print("-" * 40)
for lang, text in texts.items():
    token_count = count_tokens(text)
    char_count = len(text)
    ratio = char_count / token_count if token_count > 0 else 0
    print(f"{lang:<8} {char_count:<8} {token_count:<8} {ratio:.2f}x")
print()

# 结论：中文每个 token 约 1.5-2 个字符，英文约 4 个字符
# 同样的内容，中文消耗的 token 更多


# ============================================================
# 2. 成本估算
# ============================================================
print("=" * 60)
print("2. 成本估算")
print("=" * 60)

# OpenAI 各模型定价（美元/1M tokens，截至 2025 年）
MODEL_PRICING = {
    "gpt-4o": {"input": 2.50, "output": 10.00, "cached_input": 1.25},
    "gpt-4o-mini": {"input": 0.15, "output": 0.60, "cached_input": 0.075},
    "gpt-4-turbo": {"input": 10.00, "output": 30.00, "cached_input": None},
    "o1": {"input": 15.00, "output": 60.00, "cached_input": None},
    "o3-mini": {"input": 1.10, "output": 4.40, "cached_input": None},
    "text-embedding-3-small": {"input": 0.02, "output": 0.00, "cached_input": None},
    "text-embedding-3-large": {"input": 0.13, "output": 0.00, "cached_input": None},
}


def estimate_cost(
    model: str,
    input_tokens: int,
    output_tokens: int,
    cached_input_tokens: int = 0,
) -> dict:
    """
    估算单次请求的成本。

    参数:
        model: 模型名称
        input_tokens: 输入 token 数
        output_tokens: 输出 token 数
        cached_input_tokens: 命中缓存的输入 token 数

    返回:
        包含各项成本的字典
    """
    pricing = MODEL_PRICING.get(model)
    if pricing is None:
        return {"error": f"未知模型: {model}"}

    # 缓存命中部分按半价计算
    regular_input = input_tokens - cached_input_tokens
    cached_input = cached_input_tokens

    input_cost = (regular_input * pricing["input"] + cached_input * (pricing["cached_input"] or pricing["input"])) / 1_000_000
    output_cost = (output_tokens * pricing["output"]) / 1_000_000

    return {
        "model": model,
        "input_cost": round(input_cost, 6),
        "output_cost": round(output_cost, 6),
        "total_cost": round(input_cost + output_cost, 6),
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "cached_hit": cached_input_tokens,
    }


# 模拟一个典型场景：用户提问，模型回答
scenarios = [
    {"name": "简单问答（gpt-4o-mini）", "model": "gpt-4o-mini", "input": 200, "output": 150},
    {"name": "复杂推理（gpt-4o）", "model": "gpt-4o", "input": 2000, "output": 500},
    {"name": "长文档分析（gpt-4o-mini）", "model": "gpt-4o-mini", "input": 8000, "output": 1000},
    {"name": "长文档分析（gpt-4o）", "model": "gpt-4o", "input": 8000, "output": 1000},
    {"name": "大规模批量处理（gpt-4o-mini）", "model": "gpt-4o-mini", "input": 500_000, "output": 100_000},
]

print(f"{'场景':<30} {'模型':<16} {'输入token':<10} {'输出token':<10} {'预估成本':<12}")
print("-" * 80)
for sc in scenarios:
    cost = estimate_cost(sc["model"], sc["input"], sc["output"])
    cost_str = f"${cost['total_cost']:.6f}"
    print(f"{sc['name']:<30} {sc['model']:<16} {sc['input']:<10} {sc['output']:<10} {cost_str:<12}")
print()

# 批量处理 100 万次请求的成本
print("【批量对比】100 万次简单问答的成本:")
mini_cost = estimate_cost("gpt-4o-mini", 200, 150)
gpt4o_cost = estimate_cost("gpt-4o", 200, 150)
print(f"  gpt-4o-mini: ${mini_cost['total_cost'] * 1_000_000:.2f}")
print(f"  gpt-4o:      ${gpt4o_cost['total_cost'] * 1_000_000:.2f}")
print(f"  节省:        ${(gpt4o_cost['total_cost'] - mini_cost['total_cost']) * 1_000_000:.2f}")
print()


# ============================================================
# 3. Prompt 缓存策略
# ============================================================
print("=" * 60)
print("3. Prompt 缓存策略")
print("=" * 60)


def analyze_prompt_cache(prompt: str, system_prompt: str = "") -> dict:
    """
    分析 prompt 中哪些部分可以缓存。

    缓存策略：
    1. System Prompt — 几乎不变，100% 缓存命中
    2. 对话历史 — 多轮对话中，历史消息可缓存
    3. Few-shot 示例 — 固定示例可缓存
    4. 用户输入 — 通常不可缓存（变化频繁）
    """
    system_tokens = count_tokens(system_prompt)
    prompt_tokens = count_tokens(prompt)

    # 假设 system prompt 总是命中缓存
    # 用户输入部分不命中缓存
    return {
        "total_input_tokens": system_tokens + prompt_tokens,
        "cached_tokens": system_tokens,  # system prompt 可缓存
        "cache_hit_rate": round(system_tokens / (system_tokens + prompt_tokens) * 100, 1),
    }


# 场景 1：短 system prompt
system_short = "你是一个 AI 助手。"
user_short = "今天天气怎么样？"
cache_analysis = analyze_prompt_cache(user_short, system_short)
print(f"【短 system prompt】")
print(f"  System: '{system_short}' ({count_tokens(system_short)} tokens)")
print(f"  User:   '{user_short}' ({count_tokens(user_short)} tokens)")
print(f"  缓存命中率: {cache_analysis['cache_hit_rate']}%")
print(f"  缓存可节省: {cache_analysis['cached_tokens']} tokens\n")

# 场景 2：长 system prompt（常见场景）
system_long = (
    "你是一位资深的 Java 后端架构师，精通 Spring Boot 3.x、Spring Cloud 微服务、"
    "Spring Data JPA、MyBatis 等技术栈。你有 10 年以上的企业级应用开发经验。"
    "你的职责是回答技术问题，提供代码示例和最佳实践建议。"
    "回答时请注意：1. 使用中文回答 2. 提供具体的代码示例 3. 解释原理而不是只给结论"
    "4. 如果涉及安全问题，特别提醒 5. 给出性能优化建议"
)
user_long = "如何优化 Spring Boot 应用的启动时间？"
cache_analysis2 = analyze_prompt_cache(user_long, system_long)
print(f"【长 system prompt（常见场景）】")
print(f"  System: {count_tokens(system_long)} tokens")
print(f"  User: {count_tokens(user_long)} tokens")
print(f"  缓存命中率: {cache_analysis2['cache_hit_rate']}%")
print(f"  缓存可节省: {cache_analysis2['cached_tokens']} tokens")

# 多轮对话场景
print(f"\n【多轮对话缓存分析】")
history_tokens = 0
for turn in range(5):
    user_msg = f"第 {turn + 1} 轮用户消息"
    assistant_msg = f"第 {turn + 1} 轮 AI 回复"
    history_tokens += count_tokens(user_msg) + count_tokens(assistant_msg)
    total_tokens = count_tokens(system_long) + history_tokens + count_tokens("最新问题")
    cached = count_tokens(system_long) + history_tokens - count_tokens(assistant_msg) - count_tokens(user_msg)
    cache_rate = cached / total_tokens * 100
    print(f"  第 {turn + 1} 轮: 总 {total_tokens} tokens, 可缓存 {cached} tokens ({cache_rate:.0f}%)")
print()


# ============================================================
# 4. 模型选择逻辑
# ============================================================
print("=" * 60)
print("4. 模型选择逻辑")
print("=" * 60)


class ModelSelector:
    """
    根据任务类型自动选择最合适的模型。

    选择原则：
    - 简单任务 → 小模型（便宜、快）
    - 复杂任务 → 大模型（贵、准确）
    - 创意任务 → 高 temperature
    - 精确任务 → 低 temperature
    """

    TASK_ROUTING = {
        "simple_qa": {  # 简单问答
            "model": "gpt-4o-mini",
            "temperature": 0.3,
            "max_tokens": 500,
            "reason": "简单问答不需要强推理能力，用小模型更经济",
        },
        "translation": {  # 翻译
            "model": "gpt-4o-mini",
            "temperature": 0.1,
            "max_tokens": 1000,
            "reason": "翻译需要精确性，低 temperature 保证一致性",
        },
        "code_generation": {  # 代码生成
            "model": "gpt-4o",
            "temperature": 0.2,
            "max_tokens": 4000,
            "reason": "代码生成需要较强的逻辑推理能力",
        },
        "complex_reasoning": {  # 复杂推理
            "model": "o3-mini",
            "temperature": 0.5,
            "max_tokens": 8000,
            "reason": "复杂推理任务需要更强的推理模型",
        },
        "creative_writing": {  # 创意写作
            "model": "gpt-4o",
            "temperature": 0.9,
            "max_tokens": 2000,
            "reason": "创意写作需要高 temperature 以产生多样性",
        },
        "data_extraction": {  # 数据提取
            "model": "gpt-4o-mini",
            "temperature": 0,
            "max_tokens": 1000,
            "reason": "结构化提取需要确定性输出，低 temperature + 小模型",
        },
        "summarization": {  # 文本总结
            "model": "gpt-4o-mini",
            "temperature": 0.3,
            "max_tokens": 1000,
            "reason": "总结任务用 mini 模型性价比最高",
        },
    }

    @classmethod
    def select_model(cls, task_type: str, input_length: int = 0) -> dict:
        """
        根据任务类型和输入长度选择模型。

        如果输入很长（> 6000 tokens），自动升级到大模型以保证质量。
        """
        config = cls.TASK_ROUTING.get(task_type)
        if config is None:
            return {
                "model": "gpt-4o-mini",
                "temperature": 0.7,
                "max_tokens": 1000,
                "reason": "未知任务类型，使用默认模型",
            }

        # 长输入自动升级
        if input_length > 6000 and config["model"] == "gpt-4o-mini":
            upgraded = {
                "model": "gpt-4o",
                "temperature": config["temperature"],
                "max_tokens": config["max_tokens"],
                "reason": f"输入较长 ({input_length} tokens)，从 {config['model']} 升级到 gpt-4o",
            }
            return upgraded

        return config


# 模拟不同场景的模型选择
task_examples = [
    ("simple_qa", "什么是 Java 中的泛型？", 150),
    ("translation", "把 Hello World 翻译成中文", 50),
    ("code_generation", "写一个 Spring Boot Controller 的 CRUD 示例", 200),
    ("complex_reasoning", "解释分布式事务的 CAP 定理和 Seata 的实现原理", 500),
    ("creative_writing", "写一首关于编程的诗", 100),
    ("data_extraction", "从文本中提取人名、日期、金额", 200),
    ("summarization", "把这篇 10000 字的文章总结成 3 句话", 8000),  # 长输入 → 自动升级
]

print(f"{'任务类型':<18} {'输入长度':<10} {'推荐模型':<16} {'理由':<30}")
print("-" * 75)
for task_type, example, token_count in task_examples:
    config = ModelSelector.select_model(task_type, token_count)
    print(f"{task_type:<18} {token_count:<10} {config['model']:<16} {config['reason'][:30]}")
print()


# ============================================================
# 5. 综合成本优化建议
# ============================================================
print("=" * 60)
print("5. 综合成本优化建议")
print("=" * 60)

tips = [
    ("模型选择", "能用 gpt-4o-mini 就别用 gpt-4o，成本差 10-20 倍"),
    ("System Prompt", "把不变的 system prompt 放在最前面，利用 Prompt Caching 节省 50% 输入成本"),
    ("对话历史", "多轮对话中只保留最近的 N 轮（如 10 轮），而不是全部历史"),
    ("Token 限制", "设置合理的 max_tokens，避免模型输出过长"),
    ("Batch API", "非实时请求使用 Batch API，价格打 5 折"),
    ("流式输出", "stream=True 不改变成本，但改善用户体验"),
    ("温度控制", "简单任务用 temperature=0，减少无意义的 token 消耗"),
    ("输入压缩", "去除 prompt 中的冗余信息，精简输入"),
    ("缓存策略", "相同的查询结果可以缓存，避免重复调用 API"),
    ("监控告警", "生产环境务必监控 API 用量和成本，设置预算告警"),
]

for i, (title, tip) in enumerate(tips, 1):
    print(f"  {i}. [{title}] {tip}")

print()
print("=" * 60)
print("演示完成！")
print("=" * 60)
print("\n运行方式: python 04_cost_optimization.py")
print("无需 API Key，完全离线运行")