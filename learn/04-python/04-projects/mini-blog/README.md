# 迷你博客 — 异步 API 服务

> 🎯 独立小项目 | 预计开发时间：2 小时

用 Python 标准库 + asyncio 实现一个极简的博客 API 服务。**不依赖任何第三方框架**（FastAPI、Flask、aiohttp 都不用），只为理解 HTTP 协议与异步 I/O 的本质。

---

## 项目目标

实现一个单文件博客 API 服务，支持：

- `GET /posts` — 获取文章列表
- `GET /posts/{id}` — 获取单篇文章
- `POST /posts` — 创建文章
- `PUT /posts/{id}` — 更新文章
- `DELETE /posts/{id}` — 删除文章

数据存储在内存中（一个简单的 dict），持久化不是重点，**理解异步网络编程**才是。

---

## 实现

```python
# mini_blog.py
import asyncio
import json
from datetime import datetime
from typing import Optional

# 内存存储
posts = {}
next_id = 1

class BlogAPI:
    """极简的 HTTP 请求处理器"""

    async def handle(self, reader: asyncio.StreamReader,
                     writer: asyncio.StreamWriter):
        """处理单个 HTTP 连接"""
        try:
            request = await self._parse_request(reader)
            response = await self._route(request)
            writer.write(response.encode("utf-8"))
        except Exception as e:
            error_response = self._json_response(
                {"error": str(e)}, status=500
            )
            writer.write(error_response.encode("utf-8"))
        finally:
            await writer.drain()
            writer.close()

    async def _parse_request(self, reader: asyncio.StreamReader) -> dict:
        """解析 HTTP 请求"""
        data = await reader.readuntil(b"\r\n\r\n")
        raw = data.decode("utf-8")
        lines = raw.split("\r\n")

        # 解析请求行
        method, path, _ = lines[0].split(" ")

        # 解析 Content-Length
        headers = {}
        body = None
        for line in lines[1:]:
            if ": " in line:
                key, value = line.split(": ", 1)
                headers[key] = value

        # 读取请求体
        content_length = int(headers.get("Content-Length", 0))
        if content_length > 0:
            body = await reader.readexactly(content_length)
            body = json.loads(body.decode("utf-8"))

        return {"method": method, "path": path, "headers": headers, "body": body}

    async def _route(self, request: dict) -> str:
        """路由分发"""
        method = request["method"]
        path = request["path"]

        # GET /posts
        if method == "GET" and path == "/posts":
            return self._list_posts()

        # GET /posts/{id}
        if method == "GET" and path.startswith("/posts/"):
            post_id = int(path.split("/")[-1])
            return self._get_post(post_id)

        # POST /posts
        if method == "POST" and path == "/posts":
            return self._create_post(request["body"])

        # PUT /posts/{id}
        if method == "PUT" and path.startswith("/posts/"):
            post_id = int(path.split("/")[-1])
            return self._update_post(post_id, request["body"])

        # DELETE /posts/{id}
        if method == "DELETE" and path.startswith("/posts/"):
            post_id = int(path.split("/")[-1])
            return self._delete_post(post_id)

        return self._json_response({"error": "Not Found"}, status=404)

    def _list_posts(self) -> str:
        return self._json_response({
            "posts": list(posts.values()),
            "total": len(posts),
        })

    def _get_post(self, post_id: int) -> str:
        post = posts.get(post_id)
        if not post:
            return self._json_response({"error": "Not Found"}, status=404)
        return self._json_response(post)

    def _create_post(self, body: dict) -> str:
        global next_id
        if not body or "title" not in body or "content" not in body:
            return self._json_response(
                {"error": "title and content are required"}, status=400
            )
        post = {
            "id": next_id,
            "title": body["title"],
            "content": body["content"],
            "created_at": datetime.now().isoformat(),
            "updated_at": datetime.now().isoformat(),
        }
        posts[next_id] = post
        next_id += 1
        return self._json_response(post, status=201)

    def _update_post(self, post_id: int, body: dict) -> str:
        if post_id not in posts:
            return self._json_response({"error": "Not Found"}, status=404)
        post = posts[post_id]
        if "title" in body:
            post["title"] = body["title"]
        if "content" in body:
            post["content"] = body["content"]
        post["updated_at"] = datetime.now().isoformat()
        return self._json_response(post)

    def _delete_post(self, post_id: int) -> str:
        if post_id not in posts:
            return self._json_response({"error": "Not Found"}, status=404)
        del posts[post_id]
        return self._json_response({"message": "Deleted"})

    def _json_response(self, data: dict, status: int = 200) -> str:
        body = json.dumps(data, ensure_ascii=False)
        status_text = {200: "OK", 201: "Created", 400: "Bad Request",
                       404: "Not Found", 500: "Internal Server Error"}
        return (
            f"HTTP/1.1 {status} {status_text[status]}\r\n"
            f"Content-Type: application/json; charset=utf-8\r\n"
            f"Content-Length: {len(body.encode('utf-8'))}\r\n"
            f"Connection: close\r\n"
            f"\r\n"
            f"{body}"
        )

async def main():
    api = BlogAPI()
    server = await asyncio.start_server(api.handle, "127.0.0.1", 8080)
    addr = server.sockets[0].getsockname()
    print(f"博客 API 服务启动于 http://{addr[0]}:{addr[1]}")

    async with server:
        await server.serve_forever()

if __name__ == "__main__":
    asyncio.run(main())
```

---

## 运行与测试

```bash
# 启动服务
python mini_blog.py

# 另一个终端，测试 API
# 创建文章
curl -X POST http://localhost:8080/posts \
  -H "Content-Type: application/json" \
  -d '{"title": "异步编程入门", "content": "asyncio 是 Python 异步编程的核心..."}'

# 创建第二篇
curl -X POST http://localhost:8080/posts \
  -H "Content-Type: application/json" \
  -d '{"title": "GIL 原理", "content": "Global Interpreter Lock..."}'

# 获取文章列表
curl http://localhost:8080/posts

# 获取单篇文章
curl http://localhost:8080/posts/1

# 更新文章
curl -X PUT http://localhost:8080/posts/1 \
  -H "Content-Type: application/json" \
  -d '{"title": "异步编程完全指南"}'

# 删除文章
curl -X DELETE http://localhost:8080/posts/1
```

---

## 你可以从这个项目学到什么

1. **HTTP 协议的本质**：请求行、请求头、请求体、响应格式——脱离框架理解协议
2. **asyncio 的 Stream I/O**：`StreamReader` / `StreamWriter` 处理网络字节流
3. **异步网络服务器**：`asyncio.start_server` 如何并发处理多个连接
4. **JSON 序列化**：`json.dumps` / `json.loads` 处理数据序列化
5. **RESTful 设计**：资源路径、HTTP 方法、状态码的语义

## 扩展挑战

完成基础版本后，尝试以下扩展：

1. **支持分页**：`GET /posts?page=1&size=10`
2. **添加搜索**：`GET /posts?q=异步`
3. **持久化存储**：使用 `aiofiles` 异步写入 JSON 文件
4. **添加中间件**：请求日志、认证 Token 校验
5. **压力测试**：用 `wrk` 或 `hey` 测试并发处理能力（对比同步实现的性能差异）

---

## 总结

| 知识点 | 在本项目中实践 |
|---|---|
| asyncio 事件循环 | `asyncio.start_server` + `serve_forever` |
| 协程并发 | 每个连接独立处理，自动并发 |
| HTTP 协议 | 手动解析请求行与头，构造响应 |
| 内存数据 | dict 模拟数据库，无外部依赖 |
| 测试 | curl 手动测试 API 各端点 |

下一步：进入 [05-interview/quick-revision.md](../../05-interview/quick-revision.md) 开始面试冲刺。