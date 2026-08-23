"""
OpenAI Batch API 演示
=======================

演示内容：
1. JSONL 文件准备（每行一个请求）
2. 上传输入文件
3. 创建 Batch Job
4. 轮询监控状态
5. 下载结果并解析

Batch API 优势：
- 价格比同步 API 便宜 50%
- 适合大量非实时请求
- 24 小时内完成

使用前：
  1. pip install openai python-dotenv
  2. 在同目录创建 .env 文件，写入：OPENAI_API_KEY=sk-xxxxxxxxxxxxx
  3. python 03_batch_demo.py

注意：
- 本示例默认不实际调用 API（DRY_RUN=true）
- 如需真实运行，设置 DRY_RUN=false 并确认账户已开通 Batch API 权限
"""

import json
import os
import time
from pathlib import Path
import requests

from dotenv import load_dotenv

load_dotenv()
# ⚠️ 需要有效的 OpenAI API Key（Batch API 需要付费账户）
client_placeholder = None  # 仅在真实运行模式使用

# 是否真实调用 API（默认演示模式）
DRY_RUN = os.getenv("DRY_RUN", "true").lower() in ("true", "1", "yes")

if not DRY_RUN:
    from openai import OpenAI
    client = OpenAI()
else:
    print("⚠️  [演示模式] 不会真实调用 Batch API，仅展示完整代码流程\n")
    print("   如需真实运行: 设置环境变量 DRY_RUN=false\n")


# ============================================================
# 1. 准备 JSONL 文件
# ============================================================
def prepare_jsonl_file(filename: str = "batch_input.jsonl"):
    """
    生成 Batch API 输入文件。
    每一行是一个 JSON 对象，包含 custom_id、method、url、body。
    """
    print("=" * 60)
    print("【1. 准备 JSONL 文件】")
    print("=" * 60)

    requests_data = [
        {
            "custom_id": "request-1",
            "method": "POST",
            "url": "/v1/chat/completions",
            "body": {
                "model": "gpt-4o-mini",
                "messages": [
                    {"role": "system", "content": "你是一个翻译助手，把中文翻译成英文。"},
                    {"role": "user", "content": "你好，世界！"},
                ],
                "temperature": 0.7,
                "max_tokens": 100,
            },
        },
        {
            "custom_id": "request-2",
            "method": "POST",
            "url": "/v1/chat/completions",
            "body": {
                "model": "gpt-4o-mini",
                "messages": [
                    {"role": "system", "content": "你是一个总结助手，把文本总结成三句话。"},
                    {"role": "user", "content": "Spring Boot 是一个基于 Java 的框架，提供自动化配置、内嵌服务器等特性，大幅简化了企业级应用开发。微服务架构中，它常与 Spring Cloud 配合使用。开发效率极高。"},
                ],
                "temperature": 0.3,
                "max_tokens": 200,
            },
        },
        {
            "custom_id": "request-3",
            "method": "POST",
            "url": "/v1/chat/completions",
            "body": {
                "model": "gpt-4o-mini",
                "messages": [
                    {"role": "system", "content": "你是一个中文文本分类助手。"},
                    {"role": "user", "content": "请把这段文本分类为：正面/负面/中性打了开你的心情好多了今天天气不错"},
                ],
                "temperature": 0,
                "max_tokens": 100,
            },
        },
    ]

    # 写入 JSONL 文件（每行一个 JSON）
    with open(filename, "w", encoding="utf-8") as f:
        for req in requests_data:
            f.write(json.dumps(req, ensure_ascii=False) + "\n")

    print(f"  ✅ 已创建 {filename}，共 {len(requests_data)} 条请求")
    print(f"  文件格式示例:")
    with open(filename, "r", encoding="utf-8") as f:
        for i, line in enumerate(f):
            if i == 0:
                print(f"    {line.strip()[:150]}...")
    return filename


# ============================================================
# 2. 上传输入文件
# ============================================================
def upload_input_file(filename: str) -> str:
    """上传文件到 OpenAI，获取 file_id。"""
    print("\n" + "=" * 60)
    print("【2. 上传输入文件】")
    print("=" * 60)

    if DRY_RUN:
        print("  [模拟] 文件上传成功，返回 file_id: file-demo-123")
        return "file-demo-123"

    with open(filename, "rb") as f:
        response = client.files.create(
            file=f,
            purpose="batch",  # Batch API 专用 purpose
        )
    print(f"  ✅ 文件上传成功 (ID: {response.id})")
    return response.id


# ============================================================
# 3. 创建 Batch Job
# ============================================================
def create_batch_job(input_file_id: str) -> str:
    """创建 Batch 任务，获得 batch_id。"""
    print("\n" + "=" * 60)
    print("【3. 创建 Batch Job】")
    print("=" * 60)

    if DRY_RUN:
        print("  [模拟] Batch Job 创建成功，返回 batch_id: batch-demo-456")
        return "batch-demo-456"

    batch = client.batches.create(
        input_file_id=input_file_id,
        endpoint="/v1/chat/completions",
        completion_window="24h",  # 完成窗口：24 小时
    )
    print(f"  ✅ Batch 创建成功 (ID: {batch.id})")
    return batch.id


# ============================================================
# 4. 监控 Batch 状态
# ============================================================
def monitor_batch(batch_id: str, interval: int = 5, max_wait: int = 60):
    """轮询 Batch 状态直到完成或超时。"""
    print("\n" + "=" * 60)
    print("【4. 监控 Batch 状态】")
    print("=" * 60)

    if DRY_RUN:
        print(f"  [模拟] Batch {batch_id} 状态: completed")
        print(f"  [模拟] 完成请求: 3, 失败请求: 0")
        return "completed", "output-file-demo-789", "error-file-demo-000"

    start_time = time.time()
    while time.time() - start_time < max_wait:
        batch = client.batches.retrieve(batch_id)
        status = batch.status
        print(f"  ⏳ 状态: {status} "
              f"(完成: {batch.request_counts.completed}/"
              f"{batch.request_counts.total}, "
              f"失败: {batch.request_counts.failed})")

        if status in ("completed", "failed", "expired", "cancelled"):
            return status, batch.output_file_id, batch.error_file_id
        time.sleep(interval)

    print("  ⏰ 超过等待时间，检查结果...")
    batch = client.batches.retrieve(batch_id)
    return batch.status, batch.output_file_id, batch.error_file_id


# ============================================================
# 5. 下载并解析结果
# ============================================================
def download_and_parse_results(output_file_id: str):
    """下载 Batch 输出文件并解析每个请求的结果。"""
    print("\n" + "=" * 60)
    print("【5. 下载并解析结果】")
    print("=" * 60)

    if DRY_RUN:
        # 演示结果解析逻辑
        print("  [模拟] 输出文件内容示例:")
        mock_output = {
            "id": "batch_req-demo",
            "custom_id": "request-1",
            "response": {
                "status_code": 200,
                "body": {
                    "id": "chatcmpl-demo",
                    "choices": [{"message": {"content": "Hello world!"}}],
                },
            },
        }
        print(f"    {json.dumps(mock_output, ensure_ascii=False)[:180]}...")
        print("\n  [模拟] 解析结果:")
        print("    ✅ request-1 翻译结果: Hello world!")
        print("    ✅ request-2 总结结果: Spring Boot 简化企业级应用开发...")
        print("    ✅ request-3 分类结果: 负面")
        return

    # 真实运行：下载输出文件
    output = client.files.content(output_file_id)

    results = []
    for line in output.iter_lines():
        if line:
            item = json.loads(line)
            custom_id = item.get("custom_id")
            status_code = item.get("response", {}).get("status_code")
            body = item.get("response", {}).get("body", {})
            content = body.get("choices", [{}])[0].get("message", {}).get("content", "")
            results.append((custom_id, status_code, content))

    print(f"  共收到 {len(results)} 条结果:")
    for custom_id, status_code, content in results:
        print(f"    [{custom_id}] status={status_code}")
        print(f"      内容: {content[:100]}")


# ============================================================
# 6. 输入验证与批量处理工具
# ============================================================
def validate_batch_input(data: list[dict]) -> list[str]:
    """
    批量请求前验证每条数据是否合法。
    返回错误信息列表（空列表 = 全部合法）。
    """
    errors = []
    for i, item in enumerate(data):
        if "custom_id" not in item:
            errors.append(f"第 {i + 1} 条: 缺少 custom_id")
        if "url" not in item:
            errors.append(f"第 {i + 1} 条: 缺少 url")
        if "body" not in item:
            errors.append(f"第 {i + 1} 条: 缺少 body")
        if "messages" not in item.get("body", {}):
            errors.append(f"第 {i + 1} 条: body 中缺少 messages")
    return errors


# ============================================================
# 主流程
# ============================================================
if __name__ == "__main__":
    # 1. 准备文件
    filename = prepare_jsonl_file()

    # 2. 上传
    input_file_id = upload_input_file(filename)

    # 3. 创建任务
    batch_id = create_batch_job(input_file_id)

    # 4. 监控
    status, output_file_id, error_file_id = monitor_batch(batch_id)

    # 5. 下载结果
    if output_file_id:
        download_and_parse_results(output_file_id)

    print("\n" + "=" * 60)
    print("Batch API 演示完成！")
    print("=" * 60)

    # 清理临时文件
    # os.remove(filename)