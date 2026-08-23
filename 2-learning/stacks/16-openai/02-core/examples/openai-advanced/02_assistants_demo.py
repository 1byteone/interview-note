"""
OpenAI Assistants API 演示
============================

演示内容：
1. 创建 Assistant（助手）
2. 创建 Thread（对话线程）
3. 消息管理（添加用户消息）
4. 运行 Run（执行助手）
5. 文件上传与搜索（File Search）
6. 代码解释器（Code Interpreter）

使用前：
  1. pip install openai python-dotenv
  2. 在同目录创建 .env 文件，写入：OPENAI_API_KEY=sk-xxxxxxxxxxxxx
  3. python 02_assistants_demo.py

重要：
  - Assistants API 需要 OpenAI 付费账户
  - 运行会创建实际资源，注意清理（代码末尾包含清理逻辑）
"""

import os
import time
from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()
# ⚠️ 需要有效的 OpenAI API Key（Assistants API 需要付费账户）
client = OpenAI()

# ============================================================
# 1. 创建 Assistant
# ============================================================
def create_tutor_assistant() -> str:
    """
    创建一个名为"Java 导师"的 Assistant。
    返回 assistant_id，后续所有对话都基于这个 ID。
    """
    print("=" * 60)
    print("【1. 创建 Assistant】")
    print("=" * 60)

    assistant = client.beta.assistants.create(
        name="Java 导师",
        instructions=(
            "你是一位资深的 Java 后端架构师，擅长 Spring Boot 和微服务架构。"
            "你的职责是回答 Java 技术问题，提供代码示例和最佳实践建议。"
            "回答使用中文，简洁专业。"
        ),
        model="gpt-4o",
        # 启用工具：File Search 和 Code Interpreter
        tools=[
            {"type": "file_search"},     # 文件搜索：从上传的文件中检索信息
            {"type": "code_interpreter"}, # 代码解释器：运行 Python 代码
        ],
        temperature=0.3,
    )

    print(f"  ✅ Assistant 创建成功")
    print(f"  ID: {assistant.id}")
    print(f"  名称: {assistant.name}")
    print(f"  模型: {assistant.model}")
    print(f"  工具: {[t.type for t in assistant.tools]}\n")

    return assistant.id


# ============================================================
# 2. 创建 Thread（对话线程）
# ============================================================
def create_thread() -> str:
    """
    创建一个新的对话线程。
    每个用户会话创建一个独立的 Thread，消息都在 Thread 中管理。
    """
    print("=" * 60)
    print("【2. 创建 Thread（对话线程）】")
    print("=" * 60)

    thread = client.beta.threads.create()
    print(f"  ✅ Thread 创建成功")
    print(f"  ID: {thread.id}\n")
    return thread.id


# ============================================================
# 3. 添加消息并运行
# ============================================================
def add_message_and_run(thread_id: str, assistant_id: str, content: str):
    """
    在 Thread 中添加用户消息，然后运行 Assistant。
    等待运行完成，打印回复。
    """
    print(f"  👤 用户: {content}")

    # 添加用户消息
    message = client.beta.threads.messages.create(
        thread_id=thread_id,
        role="user",
        content=content,
    )
    print(f"  ✅ 消息已添加 (ID: {message.id})")

    # 创建 Run
    run = client.beta.threads.runs.create(
        thread_id=thread_id,
        assistant_id=assistant_id,
    )
    print(f"  ⏳ 运行中... (ID: {run.id})")

    # 轮询等待运行完成
    start_time = time.time()
    while run.status in ("queued", "in_progress", "requires_action"):
        time.sleep(1)
        run = client.beta.threads.runs.retrieve(
            thread_id=thread_id,
            run_id=run.id,
        )
        elapsed = time.time() - start_time
        if run.status == "requires_action":
            print(f"  🔧 需要工具调用... (已过 {elapsed:.0f}s)")
        elif run.status == "in_progress":
            pass  # 继续等待

    elapsed = time.time() - start_time
    print(f"  ✅ 运行完成 (状态: {run.status}, 耗时: {elapsed:.1f}s)")

    if run.status == "completed":
        # 获取回复消息
        messages = client.beta.threads.messages.list(
            thread_id=thread_id,
            order="desc",
            limit=1,
        )
        for msg in messages:
            if msg.role == "assistant":
                for content_block in msg.content:
                    if content_block.type == "text":
                        print(f"  🤖 助手: {content_block.text.value[:200]}")
                    elif content_block.type == "image_file":
                        print(f"  📊 [代码解释器生成的图片]")
    elif run.status == "failed":
        print(f"  ❌ 运行失败: {run.last_error}")
    print()


# ============================================================
# 4. 文件上传与搜索
# ============================================================
def upload_file_and_search(assistant_id: str, thread_id: str):
    """
    上传一个示例文件到 Assistant，然后使用 File Search 工具检索。
    注意：File Search 需要将文件上传到 Vector Store。
    """
    print("=" * 60)
    print("【4. 文件上传与搜索】")
    print("=" * 60)

    # 创建一个示例文件
    sample_file_path = "sample_java_guide.txt"
    with open(sample_file_path, "w", encoding="utf-8") as f:
        f.write(
            "Spring Boot 3.0 最佳实践\n"
            "======================\n"
            "1. 使用 @ConfigurationProperties 替代 @Value 注入配置\n"
            "2. 使用 @Validated 进行参数校验\n"
            "3. 使用 @ControllerAdvice 统一异常处理\n"
            "4. 使用 @Slf4j 进行日志记录\n"
            "5. 使用 @Transactional 管理事务\n"
            "6. 使用 @Async 实现异步方法\n"
            "7. 使用 @Scheduled 实现定时任务\n"
            "8. 使用 @Retryable 实现重试机制\n"
            "9. 使用 @Cacheable 实现缓存\n"
            "10. 使用 @Profile 实现环境隔离\n"
        )

    # 上传文件
    file = client.files.create(
        file=open(sample_file_path, "rb"),
        purpose="assistants",
    )
    print(f"  ✅ 文件已上传 (ID: {file.id}, 名称: {sample_file_path})")

    # 创建 Vector Store 并关联文件
    vector_store = client.beta.vector_stores.create(
        name="Java 最佳实践知识库",
        file_ids=[file.id],
    )
    print(f"  ✅ Vector Store 已创建 (ID: {vector_store.id})")

    # 更新 Assistant，关联 Vector Store
    updated_assistant = client.beta.assistants.update(
        assistant_id=assistant_id,
        tool_resources={
            "file_search": {"vector_store_ids": [vector_store.id]}
        },
    )
    print(f"  ✅ Assistant 已关联 Vector Store\n")

    # 测试文件搜索
    add_message_and_run(
        thread_id=thread_id,
        assistant_id=assistant_id,
        content="根据上传的文档，Spring Boot 3.0 的最佳实践有哪些？请列出 5 条。",
    )

    # 清理
    # os.remove(sample_file_path)  # 可选：删除临时文件
    return vector_store.id


# ============================================================
# 5. 代码解释器演示
# ============================================================
def code_interpreter_demo(assistant_id: str, thread_id: str):
    """
    让 Assistant 使用 Code Interpreter 工具运行代码来分析数据。
    """
    print("=" * 60)
    print("【5. 代码解释器演示】")
    print("=" * 60)

    add_message_and_run(
        thread_id=thread_id,
        assistant_id=assistant_id,
        content=(
            "请写一个 Python 程序，计算 1 到 100 中所有偶数的平方和，"
            "并输出结果。请使用代码解释器运行并展示结果。"
        ),
    )


# ============================================================
# 6. 清理资源
# ============================================================
def cleanup(assistant_id: str, thread_id: str, vector_store_id: str = None):
    """
    清理所有创建的资源，避免产生持续费用。
    """
    print("=" * 60)
    print("【6. 清理资源】")
    print("=" * 60)

    # 删除 Assistant
    if assistant_id:
        client.beta.assistants.delete(assistant_id)
        print(f"  ✅ Assistant 已删除: {assistant_id}")

    # 删除 Thread
    if thread_id:
        client.beta.threads.delete(thread_id)
        print(f"  ✅ Thread 已删除: {thread_id}")

    # 删除 Vector Store
    if vector_store_id:
        client.beta.vector_stores.delete(vector_store_id)
        print(f"  ✅ Vector Store 已删除: {vector_store_id}")

    # 列出并删除相关文件（清理残留）
    print("  🔍 检查残留文件...")
    for file in client.files.list(purpose="assistants"):
        try:
            client.files.delete(file.id)
            print(f"  ✅ 文件已删除: {file.id}")
        except Exception:
            pass


# ============================================================
# 主函数
# ============================================================
if __name__ == "__main__":
    print("⚠️  请确保已在 .env 文件中配置 OPENAI_API_KEY\n")
    print("⚠️  注意：Assistants API 会产生费用，运行后会自动清理资源\n")

    assistant_id = None
    thread_id = None
    vector_store_id = None

    try:
        # 1. 创建 Assistant
        assistant_id = create_tutor_assistant()

        # 2. 创建 Thread
        thread_id = create_thread()

        # 3. 基本对话
        print("=" * 60)
        print("【3. 基本对话演示】")
        print("=" * 60)
        add_message_and_run(thread_id, assistant_id, "请解释一下 Spring Boot 的自动配置原理？")

        # 4. 文件搜索
        vector_store_id = upload_file_and_search(assistant_id, thread_id)

        # 5. 代码解释器
        code_interpreter_demo(assistant_id, thread_id)

    except Exception as e:
        print(f"❌ 发生错误: {e}")
        print("  请检查 API Key 和账户权限")
    finally:
        # 清理资源
        cleanup(assistant_id, thread_id, vector_store_id)