# 09 · 商品数据向量化同步链路：MySQL → 切片 → Embedding → RedisVL

> 所有在线搜索的前提是离线数据已准备好。这一篇追踪数据从 MySQL 出发，经过**文本拼接、切片、向量化、批量写入**的全流程，最终形成可供检索的向量索引。
>
> **对应项目：** `src/smart_search/core/vector_sync_service.py`

---

## 一、基础概念

### 1.1 数据同步链路总览

```
管理员调用 /api/v1/sync
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Step 1: SQLAlchemy 从 MySQL 流式读取 SKU 商品数据                    │
│  SQL: SELECT id, sku_name, sku_attribute, price, ... FROM sku_info   │
│  使用 lazy_load() 避免一次性加载全部数据到内存                          │
└──────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Step 2: 字段拼接为 page_content                                     │
│  "华为Pura 70。6.7英寸OLED屏。华为。手机。4999"                        │
└──────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Step 3: RecursiveCharacterTextSplitter 文本切片                     │
│  256 tokens/chunk, overlap=25, 中文分隔符优先                         │
└──────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Step 4: BGE-M3 Embedding 向量化 + 批量写入 RedisVL                  │
│  每批 100 条，幂等 ID 防重复，写入 Redis Stack HNSW 索引               │
└──────────────────────────────────────────────────────────────────────┘
    │
    ▼
在线搜索阶段：RedisVectorStore.similarity_search(query, k=10)
```

### 1.2 为什么需要数据同步

| 问题 | 说明 |
|------|------|
| **MySQL 不能直接做语义搜索** | SQL 只能做关键词精确匹配，无法理解语义 |
| **Embedding 不能在查询时实时计算** | Embedding 调用耗时~100ms，无法实时处理 |
| **向量检索需要索引结构** | HNSW 索引构建是 CPU 密集型，需离线完成 |
| **数据一致性** | 商品数据更新后，向量索引也需要同步更新 |

**解决思路：** 离线一次性将 MySQL 数据向量化写入 Redis，在线只做查询。

---

## 二、进阶机制

### 2.1 SQLAlchemy + SQLDatabaseLoader —— 流式读取

```python
from langchain_community.utilities import SQLDatabase
from langchain_community.document_loaders import SQLDatabaseLoader

db = SQLDatabase(self.sql_engine)

sql_query = """
    SELECT id, spu_id, price, sku_name, sku_attribute,
           brand_name, category_name, sku_default_img
    FROM sku_info WHERE deleted=0
"""

loader = SQLDatabaseLoader(
    query=sql_query,
    db=db,
    page_content_mapper=self.custom_page_content_mapper,
    metadata_mapper=self.custom_metadata_mapper,
)

# 流式加载，避免一次性加载全部到内存
doc_iterator = loader.lazy_load()
```

**`lazy_load()` 做了什么？**

返回一个**生成器（generator）**，每次从数据库游标读取一条记录，处理完再读下一条。内存占用始终是 O(1)，而不是 O(N)。

**对比 Java：** 相当于 `ResultSet` 的 `next()` 逐行遍历，而非 `List<Entity> findAll()` 一次性加载全部。

### 2.2 字段拼接 —— 决定搜索质量的关键

```python
@staticmethod
def custom_page_content_mapper(row) -> str:
    """将商品字段拼接为文档 page_content"""
    fields = ["sku_name", "sku_attribute", "brand_name", "category_name", "price"]
    content_parts = [str(getattr(row, field, "")) for field in fields]
    return "。".join(content_parts)
```

**字段拼接策略为什么重要？**

```
不好的拼接：                       好的拼接（本项目）：
"华为Pura 70"                     "华为Pura 70。6.7英寸OLED屏幕。华为。手机。4999"
  ↑ 只匹配"华为"或"Pura"            ↑ 品牌、品类、属性、价格全匹配
```

**成本的拼接策略决定了搜索质量的上限。** 如果只拼接 `sku_name`，用户搜索"华为"能匹配，但搜索"大屏手机"就匹配不到了。拼接了 `sku_attribute`（"6.7英寸OLED屏幕"）后，"大屏"也能匹配。

### 2.3 文本切片 —— 为什么需要切

```python
splitter = RecursiveCharacterTextSplitter(
    chunk_size=CHUNK_SIZE,      # 256 tokens
    chunk_overlap=CHUNK_OVERLAP,# 25 tokens
    separators=["\n\n", "\n", "。", "，", " "],  # 中文优先分隔符
    length_function=tiktoken_len,  # 用 tiktoken 计算 token 长度
    strip_whitespace=True
)
```

**为什么需要切片？**

| 问题 | 说明 |
|------|------|
| **Embedding 模型有输入长度限制** | BGE-M3 支持 8192 token，但过长文本语义会模糊 |
| **检索精度随长度下降** | 整篇商品描述嵌入后，向量体现的是"平均语义"，细节被稀释 |
| **LLM 上下文有限** | Agent 检索结果太长会超出 LLM 上下文窗口 |

**切片策略：**

```
"华为Pura 70。6.7英寸OLED屏幕。华为。手机。4999。..."
    ↑ chunk 1 (256 tokens)                  ↑ chunk 2 (256 tokens, overlap 25)
    ├── "华为Pura 70。6.7英寸OLED屏幕..."    ├── "...OLED屏幕。华为。手机..."
    └── 语义完整，包含商品名+属性             └── 与 chunk 1 有 25 tokens重叠
```

**重叠（overlap）的作用：** 避免切在语义边界上导致信息丢失。重叠部分会被两个 chunk 共享，保证边界处的语义完整性。

### 2.4 tiktoken —— 精确计算 token 长度

```python
import tiktoken

TIKTOKEN_ENCODING = "cl100k_base"

def tiktoken_len(text: str) -> int:
    """tiktoken计算token长度"""
    tokenizer = tiktoken.get_encoding(TIKTOKEN_ENCODING)
    return len(tokenizer.encode(text))
```

**为什么不用 `len(text)` 按字符数切？**

```python
# 中英文 token 密度差异巨大
len("华为Pura 70")  # 10 个字符 → 3 tokens (cl100k_base)
len("iPhone 16 Pro Max")  # 17 个字符 → 5 tokens

# 用字符数切会导致：
# 中文 chunk 256 字符 ≠ 256 tokens（实际可能只有 80 tokens）
# 英文 chunk 256 字符 ≠ 256 tokens（实际可能 100+ tokens）
```

**tiktoken 是 OpenAI 开源的 tokenizer，`cl100k_base` 编码用于 ChatGPT 和 Embedding 模型。** 用 token 而非字符数作为切片单位，保证每个 chunk 的语义密度一致。

### 2.5 批量写入 + 幂等 ID

```python
BATCH_SIZE = 100

batch_docs: list[Document] = []
total_chunk = 0

for doc in doc_iterator:
    chunk_texts = splitter.split_text(doc.page_content)
    for chunk in chunk_texts:
        chunk_doc = Document(page_content=chunk, metadata=doc.metadata.copy())
        batch_docs.append(chunk_doc)
        total_chunk += 1

        # 达到批次阈值，批量写入
        if len(batch_docs) >= BATCH_SIZE:
            doc_ids = [self._generate_doc_id(d) for d in batch_docs]
            self.vector_store.add_documents(documents=batch_docs, ids=doc_ids)
            batch_docs.clear()

# 收尾不足一批
if batch_docs:
    doc_ids = [self._generate_doc_id(d) for d in batch_docs]
    self.vector_store.add_documents(documents=batch_docs, ids=doc_ids)
```

**批量写入：** 每 100 条一次网络请求，比逐条写入快 100 倍。

**幂等 ID：**

```python
@staticmethod
def _generate_doc_id(doc: Document) -> str:
    """根据sku_id+chunk内容生成md5唯一文档id"""
    raw = f"{doc.metadata['id']}_{doc.page_content.strip()}".encode("utf‑8")
    return hashlib.md5(raw).hexdigest()
```

**幂等（Idempotent）** 意味着：同一商品同一切片内容，重复同步不会产生重复文档。产品更新后重新同步，只有变更的切片会更新。

---

## 三、项目现场

### 3.1 同步触发方式

管理员通过 HTTP 请求触发同步：

```
GET /api/v1/sync
```

返回：

```json
{"code": 200, "msg": "操作成功", "data": "MySQL数据向量化入库完成！总分片数量：847"}
```

### 3.2 全量同步的局限

| 局限 | 说明 | 改进方向 |
|------|------|---------|
| **全量非增量** | 每次同步全部数据，数据量大时效率低 | 增加增量同步（基于 `updated_at`） |
| **手动触发** | 管理员手动调用，无法自动响应 MySQL 变更 | 添加 CDC 监听（Debezium / Canal） |
| **无进度反馈** | 只有开始和结束通知 | 添加 WebSocket 实时进度推送 |

### 3.3 生产级改进方案

```
当前方案：                              改进方案：
                                          MySQL
管理员 GET /sync → 全量同步                 │
                                           ├── Canal (MySQL binlog 监听)
                                           │      │
                                           │      ▼
                                           ├── 实时增量 → 单条 Embedding → RedisVL
                                           │
                                           └── 定时全量 → 兜底数据一致性校验
```

---

## 四、Java 对照

### 4.1 Spring Boot ETL 对照

```java
// 1. JPA 实体
@Entity
@Table(name = "sku_info")
public class SkuInfo {
    @Id private Long id;
    private Long spuId;
    private BigDecimal price;
    private String skuName;
    private String skuAttribute;
    private String brandName;
    private String categoryName;
    @Column(name = "deleted") private Integer deleted;
}

// 2. 数据同步服务
@Service
public class ProductVectorSyncService {

    private final JdbcTemplate jdbcTemplate;
    private final RedisVectorStore vectorStore;

    @Transactional(readOnly = true)
    public String loadSkuFromMysql() {
        // 流式读取（类似 lazy_load）
        int batchSize = 100;
        int totalChunks = 0;

        jdbcTemplate.query(
            "SELECT * FROM sku_info WHERE deleted=0",
            rs -> {
                // 逐行处理
                String content = buildContent(rs);
                List<String> chunks = splitText(content, 256, 25);
                for (String chunk : chunks) {
                    Document doc = new Document(chunk, extractMetadata(rs));
                    // 收集到批次
                    batchDocs.add(doc);

                    if (batchDocs.size() >= batchSize) {
                        vectorStore.addDocuments(batchDocs);
                        totalChunks += batchDocs.size();
                        batchDocs.clear();
                    }
                }
            }
        );

        // 收尾
        if (!batchDocs.isEmpty()) {
            vectorStore.addDocuments(batchDocs);
            totalChunks += batchDocs.size();
        }

        return "同步完成，总分片：" + totalChunks;
    }
}
```

### 4.2 对照总结

| 维度 | Python | Java |
|------|--------|------|
| 数据库连接 | `SQLAlchemy` + `create_engine` | `JdbcTemplate` / `JPA` |
| 流式读取 | `lazy_load()` 生成器 | `ResultSet` 逐行遍历 |
| 文本切片 | `RecursiveCharacterTextSplitter` | 需自行实现或使用 `LangChain4j` |
| Token 计算 | `tiktoken` | 需自行封装或使用 `GPT-2 Tokenizer` |
| 向量写入 | `RedisVectorStore.add_documents` | `Redis OM` 或自定义 |

---

## 五、最小可复现示例

### 5.1 完整数据同步流程

```python
# sync_demo.py
# 需要: pip install langchain-community langchain-redis langchain-openai tiktoken
import hashlib
import tiktoken
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_core.documents import Document

def demo_sync_pipeline():
    """演示数据同步的核心流程：拼接 → 切片 → 向量化 → 写入"""

    # 1. 模拟 MySQL 数据
    sku_records = [
        {"id": 1, "sku_name": "华为Pura 70 Ultra", "sku_attribute": "6.7英寸OLED 5000mAh",
         "brand_name": "华为", "category_name": "手机", "price": 6999},
        {"id": 2, "sku_name": "苹果iPhone 16 Pro", "sku_attribute": "6.3英寸OLED 4500mAh",
         "brand_name": "苹果", "category_name": "手机", "price": 9999},
    ]

    # 2. 字段拼接
    def build_page_content(row: dict) -> str:
        fields = ["sku_name", "sku_attribute", "brand_name", "category_name", "price"]
        return "。".join(str(row.get(f, "")) for f in fields)

    # 3. 文本切片
    tokenizer = tiktoken.get_encoding("cl100k_base")
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=256,
        chunk_overlap=25,
        separators=["\n\n", "\n", "。", "，", " "],
        length_function=lambda x: len(tokenizer.encode(x)),
        strip_whitespace=True,
    )

    # 4. 处理并写入（模拟）
    for record in sku_records:
        content = build_page_content(record)
        chunks = splitter.split_text(content)
        for chunk in chunks:
            doc = Document(
                page_content=chunk,
                metadata={k: v for k, v in record.items()}
            )
            # 生成幂等 ID
            doc_id = hashlib.md5(
                f"{record['id']}_{chunk.strip()}".encode("utf-8")
            ).hexdigest()
            print(f"  [{doc_id[:8]}] {chunk[:50]}...")

            # 真实场景：vector_store.add_documents([doc], ids=[doc_id])
```

### 5.2 验证切片策略

```python
def test_text_splitter():
    """验证切片策略对中文的支持"""

    text = "华为Pura 70 Ultra。6.7英寸OLED屏幕。5000mAh大电池。华为。手机。6999元"
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=50,  # 小 chunk 尺寸便于验证
        chunk_overlap=10,
        separators=["。", "，", " "],
        length_function=lambda x: len(x),  # 用字符数方便演示
    )

    chunks = splitter.split_text(text)
    print(f"原始文本: {text}")
    print(f"切片数量: {len(chunks)}")
    for i, chunk in enumerate(chunks):
        print(f"  chunk {i}: {chunk}")

    # 验证：中文字符正确分割
    assert len(chunks) > 1, "应被切分为多个 chunk"
    assert all(c.strip() for c in chunks), "每个 chunk 不应为空"

    # 验证：重叠部分存在
    if len(chunks) > 1:
        assert chunks[0][-10:] in chunks[1], "应有重叠部分"
```

---

## 六、面试要点

### Q1: 为什么数据同步要在离线阶段完成，而不是在线查询时实时 Embedding？

**回答思路：** Embedding 调用耗时~100ms，如果每次查询都实时 Embedding，加上 Redis 检索和 LLM 调用，总延迟会很高。离线预计算将 Embedding 前置到数据写入阶段，查询时只需计算一次查询文本的 Embedding，大幅降低在线延迟。

### Q2: 为什么用 tiktoken 而不是字符数来切分文本？

**回答思路：** 中英文 token 密度差异大——中文 1 个 token 约 1.5 字符，英文 1 个 token 约 4 字符。用字符数切分会导致不同语言 chunk 的语义密度不一致。tiktoken 使用 OpenAI 的 `cl100k_base` 编码，精确计算 token 数，保证语义密度一致。

### Q3: 分批写入和幂等 ID 的设计意图是什么？

**回答思路：** 分批写入（batch=100）减少网络往返次数，提高吞吐量。幂等 ID（md5(sku_id + chunk_content)）保证重复同步不会产生重复文档，产品更新后只有变更的切片会更新。

### Q4: 字段拼接策略如何影响搜索质量？

**回答思路：** 拼接策略决定了搜索质量的上限。只拼接 `sku_name` 只能匹配商品名，拼接了 `sku_attribute`、`brand_name`、`category_name`、`price` 后，用户可以按属性、品牌、品类、价格范围进行语义匹配。**好的拼接 = 从多个维度描述商品，让向量能捕捉更多语义特征。**

### Q5: 如果 MySQL 数据量很大（百万级 SKU），这个同步方案有什么瓶颈？

**回答思路：** 三个瓶颈：1) 全量同步中逐条 Embedding 调用外部 API，速率受限，需引入并发控制；2) 切片数量大，Redis 内存占用高（每条向量 1024 维约 4KB，百万级需数 GB）；3) 全量同步耗时过长，需增量同步补充。改进方向：添加 Canal 监听 binlog 做增量同步、引入消息队列削峰填谷、Redis 做集群分片。

---

> **下一篇：** [10-ARCHITECTURE.md —— 架构复盘与面试题集](./10-ARCHITECTURE.md)
>
> 全链路复盘，技术栈横向对比总表，以及 20+ 面试高频题与回答思路。