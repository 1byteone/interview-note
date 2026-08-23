"""
OpenAI Function Calling（工具调用）完整演示
============================================

演示内容：
- 工具/函数定义（tools schema）
- Tool Calling 完整流程
- 多函数调用
- 处理函数执行结果并回传

核心流程：
  1. 用户发送消息 + 定义可用 tools
  2. 模型决定是否调用 tool（返回 tool_calls）
  3. 应用端执行真实函数，拿到结果
  4. 将结果以 tool 角色消息发回给模型
  5. 模型基于结果生成最终回复

使用前：
  1. pip install openai python-dotenv
  2. 在同目录创建 .env 文件，写入：OPENAI_API_KEY=sk-xxxxxxxxxxxxx
  3. python 02_function_calling.py
"""

import json
import os
from datetime import datetime
from dotenv import load_dotenv
from openai import OpenAI

# ============================================================
# 客户端初始化
# ============================================================
load_dotenv()
# ⚠️ 需要有效的 OpenAI API Key
client = OpenAI()


# ============================================================
# 1. 模拟的业务函数（实际项目中会调用真实 API/数据库）
# ============================================================

def get_weather(city: str, unit: str = "celsius") -> dict:
    """模拟获取天气数据（实际应调用天气 API）"""
    # 这里用假数据模拟，实际项目中调用真实 API
    mock_data = {
        "北京": {"temp": 22, "condition": "晴", "humidity": 45},
        "上海": {"temp": 26, "condition": "多云", "humidity": 70},
        "深圳": {"temp": 30, "condition": "阵雨", "humidity": 85},
    }
    data = mock_data.get(city, {"temp": 25, "condition": "未知", "humidity": 50})

    temp = data["temp"]
    if unit == "fahrenheit":
        temp = round(temp * 9 / 5 + 32, 1)

    return {
        "city": city,
        "temperature": temp,
        "unit": unit,
        "condition": data["condition"],
        "humidity": data["humidity"],
    }


def calculate(expression: str) -> dict:
    """安全地计算数学表达式"""
    try:
        # 注意：生产环境应使用 ast.literal_eval 或专用数学库，而非 eval
        # 此处仅为演示
        allowed_chars = set("0123456789+-*/().% ")
        if not all(c in allowed_chars for c in expression):
            return {"error": "表达式包含不允许的字符", "expression": expression}
        result = eval(expression)
        return {"expression": expression, "result": result}
    except Exception as e:
        return {"expression": expression, "error": str(e)}


def get_current_time(timezone: str = "Asia/Shanghai") -> dict:
    """获取当前时间"""
    now = datetime.now()
    return {
        "datetime": now.strftime("%Y-%m-%d %H:%M:%S"),
        "timezone": timezone,
        "weekday": ["周一", "周二", "周三", "周四", "周五", "周六", "周日"][now.weekday()],
    }


def search_knowledge(query: str, top_k: int = 3) -> dict:
    """模拟知识库搜索（实际应接向量数据库）"""
    # 模拟搜索结果
    mock_results = [
        {"title": "Spring Boot 快速入门", "score": 0.95, "snippet": "Spring Boot 是基于 Spring 框架的脚手架..."},
        {"title": "Spring Cloud 微服务架构", "score": 0.88, "snippet": "Spring Cloud 提供了一整套微服务解决方案..."},
        {"title": "Spring Security 安全框架", "score": 0.82, "snippet": "Spring Security 是功能强大的安全框架..."},
    ]
    results = [r for r in mock_results if query.lower() in r["title"].lower() or query in r["snippet"]]
    return {"query": query, "results": results[:top_k], "total": len(results)}


# ============================================================
# 2. 工具定义（JSON Schema 格式）
# ============================================================
# tools 参数告诉模型有哪些函数可以调用，以及每个函数的参数格式

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "获取指定城市的当前天气信息，包括温度、天气状况和湿度",
            "parameters": {
                "type": "object",
                "properties": {
                    "city": {
                        "type": "string",
                        "description": "城市名称，如：北京、上海、深圳",
                    },
                    "unit": {
                        "type": "string",
                        "enum": ["celsius", "fahrenheit"],
                        "description": "温度单位，默认摄氏度",
                    },
                },
                "required": ["city"],  # 必填参数
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "calculate",
            "description": "计算数学表达式，支持加减乘除和括号",
            "parameters": {
                "type": "object",
                "properties": {
                    "expression": {
                        "type": "string",
                        "description": "要计算的数学表达式，如 '(3 + 5) * 2'",
                    },
                },
                "required": ["expression"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_current_time",
            "description": "获取当前日期和时间",
            "parameters": {
                "type": "object",
                "properties": {
                    "timezone": {
                        "type": "string",
                        "description": "时区，默认 Asia/Shanghai",
                    },
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_knowledge",
            "description": "搜索知识库，查找相关技术文档和资料",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "搜索关键词",
                    },
                    "top_k": {
                        "type": "integer",
                        "description": "返回结果数量，默认3",
                    },
                },
                "required": ["query"],
            },
        },
    },
]

# 函数名到实际函数的映射
FUNCTION_MAP = {
    "get_weather": get_weather,
    "calculate": calculate,
    "get_current_time": get_current_time,
    "search_knowledge": search_knowledge,
}


# ============================================================
# 3. 核心：Tool Calling 循环
# ============================================================

def run_conversation(user_message: str):
    """
    完整的工具调用流程：
    用户消息 → 模型决策 → 执行工具 → 返回结果 → 模型生成最终回复
    """
    print(f"\n{'=' * 60}")
    print(f"用户: {user_message}")
    print(f"{'=' * 60}")

    messages = [
        {"role": "system", "content": "你是一个智能助手，可以查询天气、计算数学、获取时间和搜索知识库。请用中文回答。"},
        {"role": "user", "content": user_message},
    ]

    # --- 第一次请求：让模型决定是否调用工具 ---
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=messages,
        tools=TOOLS,
        tool_choice="auto",  # auto=模型自动决定, required=必须调用, none=不调用
    )

    # 获取模型回复
    assistant_message = response.choices[0].message
    messages.append(assistant_message)  # 将 assistant 消息加入上下文

    # --- 检查模型是否要求调用工具 ---
    if assistant_message.tool_calls:
        print(f"\n🔧 模型请求调用 {len(assistant_message.tool_calls)} 个工具:")

        for tool_call in assistant_message.tool_calls:
            function_name = tool_call.function.name
            function_args = json.loads(tool_call.function.arguments)
            print(f"   → {function_name}({function_args})")

            # 执行对应的函数
            func = FUNCTION_MAP.get(function_name)
            if func:
                result = func(**function_args)
            else:
                result = {"error": f"未知函数: {function_name}"}

            print(f"   ← 结果: {result}")

            # 将工具执行结果作为 tool 角色消息加入上下文
            messages.append({
                "role": "tool",
                "tool_call_id": tool_call.id,   # 必须匹配对应的 tool_call
                "content": json.dumps(result, ensure_ascii=False),
            })

        # --- 第二次请求：让模型基于工具结果生成最终回复 ---
        final_response = client.chat.completions.create(
            model="gpt-4o",
            messages=messages,
        )
        final_answer = final_response.choices[0].message.content
    else:
        # 模型认为不需要调用工具，直接回复
        final_answer = assistant_message.content

    print(f"\n💬 最终回复: {final_answer}")
    return final_answer


# ============================================================
# 4. 多函数调用示例（同时调用多个工具）
# ============================================================
def multi_tool_demo():
    """
    模型可以在一次回复中同时请求调用多个工具。
    例如用户问"北京天气和现在几点了"，模型会同时调用 get_weather 和 get_current_time。
    """
    print(f"\n{'#' * 60}")
    print("【多函数并行调用示例】")
    print(f"{'#' * 60}")
    run_conversation("帮我查一下北京的天气，同时告诉我现在几点了。")


# ============================================================
# 5. 工具调用链式示例（多轮工具调用）
# ============================================================
def chained_tool_demo():
    """
    有时需要多轮工具调用：模型根据第一次工具的结果，
    决定是否需要再次调用其他工具。
    """
    print(f"\n{'#' * 60}")
    print("【链式工具调用示例】")
    print(f"{'#' * 60}")
    run_conversation("北京和上海哪个城市更热？请查一下两个城市的天气，然后告诉我差多少度。")


# ============================================================
# 主函数
# ============================================================
if __name__ == "__main__":
    print("⚠️  请确保已在 .env 文件中配置 OPENAI_API_KEY\n")

    # 单工具调用
    run_conversation("深圳今天天气怎么样？")

    # 多工具并行调用
    multi_tool_demo()

    # 链式工具调用
    chained_tool_demo()

    # 其他场景
    run_conversation("请帮我计算 (23 * 45 + 100) / 7 的结果")
    run_conversation("Spring Boot 和 Spring Cloud 的区别是什么？")
