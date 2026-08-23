"""
OpenAI Structured Outputs（结构化输出）完整演示
=================================================

演示内容：
- JSON Mode：强制模型输出合法 JSON
- Pydantic Structured Outputs：用数据类定义输出结构
- 响应格式验证与重试

核心价值：
  传统 API 返回自由文本，解析容易出错。
  Structured Outputs 保证输出一定是合法 JSON 并符合你定义的 schema。

使用前：
  1. pip install openai pydantic python-dotenv
  2. 在同目录创建 .env 文件，写入：OPENAI_API_KEY=sk-xxxxxxxxxxxxx
  3. python 01_structured_output.py
"""

import os
import json
from typing import Optional
from enum import Enum
from dotenv import load_dotenv
from openai import OpenAI
from pydantic import BaseModel, Field

# ============================================================
# 客户端初始化
# ============================================================
load_dotenv()
# ⚠️ 需要有效的 OpenAI API Key
client = OpenAI()


# ============================================================
# 1. JSON Mode：最基础的结构化输出
# ============================================================
def json_mode_demo():
    """
    JSON Mode：response_format={"type": "json_object"}
    强制模型输出合法 JSON（但不保证字段内容，只是格式合法）
    """
    print("=" * 60)
    print("【JSON Mode 基础演示】")
    print("=" * 60)

    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {
                "role": "system",
                # 在 system prompt 中明确要求 JSON 格式
                "content": "你是一个信息提取助手。请以 JSON 格式输出结果。"
            },
            {
                "role": "user",
                "content": "从以下文本中提取人名、年龄和职业：张三，今年28岁，是一名Spring Boot后端工程师。"
            },
        ],
        response_format={"type": "json_object"},  # ← 关键：强制 JSON 输出
        temperature=0,
    )

    # 解析 JSON
    result = json.loads(response.choices[0].message.content)
    print(f"提取结果: {json.dumps(result, ensure_ascii=False, indent=2)}\n")


# ============================================================
# 2. Pydantic Structured Outputs（推荐方式）
# ============================================================
# 用 Pydantic 模型定义输出结构，SDK 自动转为 JSON Schema

class Priority(str, Enum):
    """任务优先级枚举"""
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    URGENT = "urgent"


class TaskItem(BaseModel):
    """单个任务项"""
    title: str = Field(description="任务标题")
    description: str = Field(description="任务详细描述")
    priority: Priority = Field(description="优先级：low/medium/high/urgent")
    estimated_hours: float = Field(description="预估工时（小时）")
    tags: list[str] = Field(description="标签列表")


class TaskExtractionResult(BaseModel):
    """任务提取结果"""
    project_name: str = Field(description="项目名称")
    total_tasks: int = Field(description="任务总数")
    tasks: list[TaskItem] = Field(description="任务列表")
    summary: str = Field(description="项目概要说明")


def pydantic_structured_output_demo():
    """
    使用 Pydantic 定义输出结构，模型输出保证符合 schema。
    """
    print("=" * 60)
    print("【Pydantic Structured Outputs 演示】")
    print("=" * 60)

    response = client.beta.chat.completions.parse(
        model="gpt-4o",
        messages=[
            {
                "role": "system",
                "content": "你是一个项目任务拆解助手。根据用户描述拆解为具体任务。"
            },
            {
                "role": "user",
                "content": (
                    "帮我规划'用户认证微服务'项目：需要实现 JWT 登录、OAuth2 第三方登录、"
                    "密码重置、用户信息 CRUD、权限管理。预估2周完成。"
                ),
            },
        ],
        # 关键参数：指定响应格式为 Pydantic 模型
        response_format=TaskExtractionResult,
        temperature=0.3,
    )

    # response.choices[0].message.parsed 直接返回 Pydantic 对象
    result = response.choices[0].message.parsed

    if result is None:
        print("⚠️ 解析失败，模型输出不符合预期 schema")
        return

    # 直接使用类型安全的对象
    print(f"项目: {result.project_name}")
    print(f"任务数: {result.total_tasks}")
    print(f"概要: {result.summary}\n")

    for i, task in enumerate(result.tasks, 1):
        print(f"  任务 {i}: {task.title}")
        print(f"    描述: {task.description}")
        print(f"    优先级: {task.priority.value}")
        print(f"    预估工时: {task.estimated_hours}h")
        print(f"    标签: {', '.join(task.tags)}")
        print()


# ============================================================
# 3. 嵌套结构 + 可选字段
# ============================================================

class Reviewer(BaseModel):
    """代码审查者"""
    name: str = Field(description="审查者姓名")
    role: str = Field(description="角色，如：前端/后端/全栈/架构师")
    expertise: list[str] = Field(description="擅长领域")


class CodeReviewIssue(BaseModel):
    """代码审查问题"""
    severity: str = Field(description="严重程度: info/warning/error/critical")
    category: str = Field(description="分类: performance/security/quality/style")
    file_path: Optional[str] = Field(default=None, description="相关文件路径")
    line: Optional[int] = Field(default=None, description="行号")
    message: str = Field(description="问题描述")
    suggestion: str = Field(description="修改建议")


class CodeReviewResult(BaseModel):
    """代码审查结果"""
    overall_score: int = Field(description="总体评分 0-100")
    reviewers: list[Reviewer] = Field(description="审查者信息")
    issues: list[CodeReviewIssue] = Field(description="发现的问题列表")
    summary: str = Field(description="审查总结")


def nested_struct_demo():
    """嵌套复杂结构 + 可选字段演示"""
    print("=" * 60)
    print("【嵌套结构 + 可选字段演示】")
    print("=" * 60)

    response = client.beta.chat.completions.parse(
        model="gpt-4o",
        messages=[
            {
                "role": "system",
                "content": "你是一位资深代码审查专家，对 Java/Spring Boot 代码进行审查。"
            },
            {
                "role": "user",
                "content": (
                    "请审查以下 Spring Boot Controller 代码并给出改进建议：\n"
                    "```java\n"
                    "@RestController\n"
                    "public class UserController {\n"
                    "    @Autowired\n"
                    "    private UserService userService;\n"
                    "\n"
                    "    @GetMapping(\"/users/{id}\")\n"
                    "    public Object getUser(@PathVariable String id) {\n"
                    "        return userService.findById(id);\n"
                    "    }\n"
                    "}\n"
                    "```"
                ),
            },
        ],
        response_format=CodeReviewResult,
        temperature=0.2,
    )

    result = response.choices[0].message.parsed
    if result is None:
        print("⚠️ 解析失败\n")
        return

    print(f"总体评分: {result.overall_score}/100")
    print(f"总结: {result.summary}\n")

    print("发现的问题:")
    for issue in result.issues:
        severity_icon = {"info": "💡", "warning": "⚠️", "error": "❌", "critical": "🚨"}
        icon = severity_icon.get(issue.severity, "❓")
        print(f"  {icon} [{issue.severity}] {issue.message}")
        print(f"     建议: {issue.suggestion}")
        if issue.file_path:
            location = issue.file_path
            if issue.line:
                location += f":{issue.line}"
            print(f"     位置: {location}")
        print()


# ============================================================
# 4. 响应验证 + 重试策略
# ============================================================
def validated_request(user_input: str, max_retries: int = 3):
    """
    实际项目中的最佳实践：
    1. 定义明确的 schema
    2. 设置低 temperature 保证一致性
    3. 失败时自动重试
    """
    print("=" * 60)
    print("【验证 + 重试策略演示】")
    print("=" * 60)

    for attempt in range(max_retries):
        try:
            response = client.beta.chat.completions.parse(
                model="gpt-4o",
                messages=[
                    {
                        "role": "system",
                        "content": "从用户输入中提取结构化的日志信息。"
                    },
                    {"role": "user", "content": user_input},
                ],
                response_format=TaskExtractionResult,
                temperature=0,  # 降低随机性
            )

            result = response.choices[0].message.parsed
            if result is not None:
                print(f"✅ 第 {attempt + 1} 次尝试成功")
                print(f"   结果: {result.project_name} — {result.total_tasks} 个任务\n")
                return result
            else:
                print(f"⚠️ 第 {attempt + 1} 次尝试: 解析失败，重试中...")
        except Exception as e:
            print(f"❌ 第 {attempt + 1} 次尝试异常: {e}")

    print("⛔ 所有重试均失败")
    return None


# ============================================================
# 主函数
# ============================================================
if __name__ == "__main__":
    print("⚠️  请确保已在 .env 文件中配置 OPENAI_API_KEY\n")

    # 1. JSON Mode
    json_mode_demo()

    # 2. Pydantic Structured Outputs
    pydantic_structured_output_demo()

    # 3. 嵌套结构
    nested_struct_demo()

    # 4. 验证 + 重试
    validated_request("用户认证微服务需要 JWT 登录和 OAuth2 支持")
