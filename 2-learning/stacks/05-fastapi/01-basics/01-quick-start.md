# 01 快速开始 — 第一个 FastAPI 应用

> 适用：👶 入门
> 目标：10 分钟从零搭建一个商品搜索 API

---

## 1. 安装

```bash
pip install fastapi uvicorn
```

建议使用虚拟环境：

```bash
python -m venv venv
source venv/bin/activate  # Linux/Mac
venv\Scripts\activate     # Windows
pip install fastapi uvicorn
```

---

## 2. 第一个 API

创建一个 `main.py` 文件：

```python
from fastapi import FastAPI

app = FastAPI(title="商品搜索 API", version="1.0.0")


@app.get("/")
def root():
    return {"message": "Hello FastAPI!"}
```

启动服务：

```bash
uvicorn main:app --reload --port 8000
```

访问 `http://localhost:8000` 返回 `{"message": "Hello FastAPI!"}`。  
访问 `http://localhost:8000/docs` 查看自动生成的 OpenAPI 文档。  
访问 `http://localhost:8000/redoc` 查看 ReDoc 文档。

> Java 对比：@SpringBootApplication + @RestController → 一个 app 对象 + @app.get() 装饰器。FastAPI 自动生成 OpenAPI 文档，Spring Boot 需要 springdoc-openapi。

---

## 3. 路径参数与查询参数

```python
from fastapi import FastAPI

app = FastAPI()

# 商品数据（模拟）
products = {
    1: {"name": "iPhone 15", "price": 6999, "category": "手机"},
    2: {"name": "MacBook Pro", "price": 14999, "category": "电脑"},
    3: {"name": "AirPods", "price": 1299, "category": "耳机"},
}


@app.get("/products/{product_id}")
def get_product(product_id: int):
    """路径参数：根据商品 ID 查询"""
    if product_id not in products:
        return {"error": "商品不存在"}
    return products[product_id]


@app.get("/products/")
def search_products(keyword: str = "", min_price: float = 0):
    """查询参数：搜索商品"""
    result = []
    for p in products.values():
        if keyword and keyword not in p["name"]:
            continue
        if p["price"] < min_price:
            continue
        result.append(p)
    return {"results": result, "total": len(result)}
```

> Java 对比：`@PathVariable` → 路径参数，`@RequestParam` → 查询参数。FastAPI 用类型注解自动解析，无需额外注解。

路径参数和查询参数的区别：

| 特性 | 路径参数 | 查询参数 |
|------|----------|----------|
| URL 示例 | `/products/1` | `/products/?keyword=iPhone&min_price=5000` |
| 用途 | 标识资源 ID | 过滤、排序、分页 |
| 必需性 | 通常是必需的 | 通常是可选的 |
| 类型转换 | 自动 | 自动 |

---

## 4. 请求体 — Pydantic BaseModel

```python
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()


# 定义请求体模型
class ProductCreate(BaseModel):
    name: str
    price: float
    category: str
    description: str = ""


# 定义响应体模型
class ProductResponse(BaseModel):
    id: int
    name: str
    price: float
    category: str
    description: str


products_db = {}
next_id = 1


@app.post("/products/", response_model=ProductResponse)
def create_product(product: ProductCreate):
    """请求体：创建商品"""
    global next_id
    product_id = next_id
    next_id += 1
    products_db[product_id] = product.model_dump()
    return ProductResponse(id=product_id, **products_db[product_id])
```

> Java 对比：`@RequestBody` 对应 Pydantic 模型参数。FastAPI 自动完成 JSON 解析、类型校验和文档生成，比 Spring Boot 的 `@Valid` 更简洁。

---

## 5. 最小案例：商品搜索 API

完整商品搜索服务：

```python
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional
from enum import Enum

app = FastAPI(title="AI 商城搜索服务")


class Category(str, Enum):
    PHONE = "手机"
    COMPUTER = "电脑"
    EARPHONE = "耳机"
    OTHER = "其他"


class ProductCreate(BaseModel):
    name: str
    price: float
    category: Category
    description: str = ""


class ProductResponse(BaseModel):
    id: int
    name: str
    price: float
    category: Category
    description: str


# 内存数据库
products_db = {}
next_id = 1


@app.post("/products/", response_model=ProductResponse, status_code=201)
def create_product(product: ProductCreate):
    global next_id
    product_id = next_id
    next_id += 1
    products_db[product_id] = product.model_dump()
    return ProductResponse(id=product_id, **products_db[product_id])


@app.get("/products/search")
def search_products(
    keyword: Optional[str] = None,
    category: Optional[Category] = None,
    min_price: float = 0,
    max_price: float = 999999,
    page: int = 1,
    size: int = 10,
):
    """商品搜索——支持关键词、分类、价格区间、分页"""
    results = []
    for pid, p in products_db.items():
        if keyword and keyword not in p["name"]:
            continue
        if category and p["category"] != category:
            continue
        if p["price"] < min_price or p["price"] > max_price:
            continue
        results.append(ProductResponse(id=pid, **p))

    start = (page - 1) * size
    end = start + size
    return {
        "results": results[start:end],
        "total": len(results),
        "page": page,
        "size": size,
    }


@app.get("/products/{product_id}", response_model=ProductResponse)
def get_product(product_id: int):
    if product_id not in products_db:
        raise HTTPException(status_code=404, detail="商品不存在")
    return ProductResponse(id=product_id, **products_db[product_id])
```

---

## 6. 自动 API 文档

FastAPI 最强大的特性之一：基于 Pydantic 模型和类型注解自动生成 OpenAPI 文档。

- Swagger UI：`/docs` — 交互式测试页面
- ReDoc：`/redoc` — 更清晰的文档展示
- OpenAPI JSON：`/openapi.json` — 可被其他工具消费

> 小贴士：对于 Java 开发者，这相当于 Spring Boot + springdoc-openapi + swagger-ui 的整合，但零配置即可使用。

---

## 本章小结

| 概念 | FastAPI 实现 | Spring Boot 对应 |
|------|-------------|-----------------|
| 应用入口 | `FastAPI()` | `@SpringBootApplication` |
| GET 路由 | `@app.get()` | `@GetMapping` |
| POST 路由 | `@app.post()` | `@PostMapping` |
| 路径参数 | 函数参数 + 类型注解 | `@PathVariable` |
| 查询参数 | 函数参数（可选） | `@RequestParam` |
| 请求体验证 | Pydantic BaseModel | `@RequestBody` + `@Valid` |
| 自动文档 | 内置 /docs | springdoc-openapi |
| 类型安全 | Python 类型注解 | Java 编译时类型检查 |

---

## 练习

1. 添加一个 `PUT /products/{product_id}` 接口用于更新商品
2. 添加一个 `DELETE /products/{product_id}` 接口用于删除商品
3. 为搜索接口添加排序参数（按价格升序/降序）
4. 尝试修改 `uvicorn` 启动参数，将端口改为 8080