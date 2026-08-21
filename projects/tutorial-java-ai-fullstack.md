# 从零到一：Java 微服务 + AI RAG 融合开发实战教程

> 作者：杨劲松 | 技术栈：Spring Cloud Alibaba + LangGraph + Hybrid RAG
> 配套项目：mall-micro-cloud（微云商城）、CropWise（农业知识问答）
> 本教程基于真实项目代码，非 Demo 玩具

---

## 📖 教程概述

本教程分两篇，覆盖**你的个人技术栈官网的全部核心技术**：

| 篇 | 项目 | 技术栈 | 核心知识点 |
|----|------|--------|-----------|
| **上篇** | mall-micro-cloud | Spring Cloud Alibaba 微服务 | 微服务拆分、Nacos、Gateway、Seata、RocketMQ、Sentinel |
| **下篇** | CropWise 农业知识问答 | LangGraph Agent + Hybrid RAG + GraphRAG | 检索增强、Agent 编排、知识图谱、幻觉控制 |

---

# 上篇：Spring Cloud Alibaba 微服务实战

> 对应项目：`mall-micro-cloud`（11 个微服务，Spring Boot 3.3.2 + Spring Cloud 2023.0.1 + Alibaba 2023.0.1.0）

---

## 第一章：微服务架构设计

### 1.1 服务拆分原则

微服务拆分不是拍脑袋，而是有章可循的。我们按**业务边界**将商城拆分为 11 个微服务：

```
┌─────────────────────────────────────────────────────────────┐
│                     API Gateway (mall-gateway)                │
│               Spring Cloud Gateway + Nacos 路由               │
│              统一入口：鉴权 / 限流 / 路由转发                    │
└──────────┬──────────┬──────────┬──────────┬──────────────────┘
           │          │          │          │
     ┌─────▼──┐ ┌────▼───┐ ┌───▼────┐ ┌───▼──────┐
     │用户服务 │ │商品服务 │ │订单服务 │ │秒杀服务   │
     │user    │ │product │ │order   │ │seckill   │
     └────────┘ └────────┘ └────────┘ └──────────┘
     ┌─────▼──┐ ┌────▼───┐ ┌───▼────┐ ┌───▼──────┐
     │购物车   │ │搜索服务 │ │支付服务 │ │定时任务   │
     │cart    │ │es      │ │pay     │ │scheduler │
     └────────┘ └────────┘ └────────┘ └──────────┘
           │          │
     ┌─────▼──────────▼──────┐
     │  mall-common (公共模块) │
     │  JWT / Redis / 统一异常  │
     └────────────────────────┘
```

**拆分原则**：
1. **高内聚低耦合**：每个服务只负责一个业务域（订单服务不管商品库存）
2. **数据独立**：每个服务有自己的数据库（订单库、商品库不混用）
3. **接口契约**：通过 OpenFeign 定义接口，不直接访问对方数据库

### 1.2 父工程依赖管理

```xml
<!-- pom.xml 父工程统一管理版本 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.2</version>
</parent>

<properties>
    <spring-cloud.version>2023.0.1</spring-cloud.version>
    <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**为什么这么设计？**
- 统一版本管理，避免依赖冲突
- 子模块不需要声明版本号，父工程统一控制
- 升级组件只需改父工程一处

---

## 第二章：Nacos 服务注册与配置中心

### 2.1 服务注册

```yaml
# application.yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 192.168.150.101:8848
        namespace: ${spring.profiles.active:public}
```

核心机制：服务启动时向 Nacos 注册 IP:Port，消费者通过服务名调用。

### 2.2 配置中心动态刷新

```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: 192.168.150.101:8848
        namespace: ${spring.profiles.active:public}
        file-extension: yml
```

**配置变更实时生效原理**（长轮询）：
```
客户端发起长轮询请求 → 服务端挂起（最长30s）
    ↓ 配置变更
服务端立即响应 → 客户端拉取最新配置
    ↓
@RefreshScope 注解的 Bean 重新注入
```

---

## 第三章：API 网关 (Gateway)

### 3.1 路由配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: mall-seckill
          uri: lb://mall-seckill-service
          predicates:
            - Path=/api/seckill/**
          filters:
            - StripPrefix=1
```

**关键点**：
- `lb://` 前缀：通过 Nacos 负载均衡
- `Path` 断言：按路径匹配路由
- `StripPrefix`：去除路径前缀再转发

### 3.2 自定义全局过滤器

```java
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String token = request.getHeaders().getFirst("Authorization");

        // 校验 JWT Token
        if (StringUtils.isEmpty(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 放行
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级
    }
}
```

---

## 第四章：OpenFeign 远程调用

### 4.1 接口定义（公共模块）

```java
@FeignClient(
    name = "mall-order-service",
    fallbackFactory = OrderFallbackFactory.class
)
public interface OrderClient {
    @GetMapping("/order/{id}")
    Result<OrderDTO> getOrder(@PathVariable("id") Long id);
}
```

### 4.2 服务降级

```java
@Component
public class OrderFallbackFactory implements FallbackFactory<OrderClient> {
    @Override
    public OrderClient create(Throwable cause) {
        return id -> {
            log.error("调用订单服务失败: {}", cause.getMessage());
            return Result.error(500, "订单服务暂时不可用");
        };
    }
}
```

**面试考点**：Feign 底层通过 JDK 动态代理生成接口实现类，调用时通过 Nacos 获取服务实例列表，用 LoadBalancer 做负载均衡。

---

## 第五章：Sentinel 流量控制与熔断降级

### 5.1 限流规则

```java
@GetMapping("/product/{id}")
@SentinelResource(
    value = "getProduct",
    blockHandler = "handleBlock"
)
public Product getProduct(@PathVariable Long id) {
    return productService.getProductById(id);
}

public Product handleBlock(Long id, BlockException e) {
    return Product.defaultProduct(); // 降级返回默认值
}
```

### 5.2 熔断状态机

```
CLOSED（关闭）→ 达到阈值 → OPEN（开启）
    ↑                           ↓
    └──────── 半开检测 ←─────────┘
          成功 → CLOSED
          失败 → OPEN
```

---

## 第六章：Seata 分布式事务

### 6.1 AT 模式原理

Seata AT 模式是**自动补偿**方案，对业务代码零侵入：

```
【一阶段】
业务 SQL 执行前 → 生成前置镜像（beforeImage）
执行业务 SQL → 生成后置镜像（afterImage）
写入 undo_log 表 → 本地提交

【二阶段-全局提交】
删除 undo_log（实际数据已提交）

【二阶段-全局回滚】
读取 undo_log → 生成反向 SQL → 校验后置镜像 → 执行回滚
```

### 6.2 代码配置

```yaml
# 引入 seata 依赖即可，业务代码无需改动
# @GlobalTransactional 注解标记分布式事务入口
@GlobalTransactional(rollbackFor = Exception.class)
public void createOrder(OrderDTO order) {
    orderService.create(order);       // 本地事务
    productService.deductStock(order); // 远程事务（Seata 协调）
}
```

---

## 第七章：RocketMQ 消息队列与秒杀设计

### 7.1 秒杀库存扣减架构

```
用户请求
    ↓
① Redis 预扣库存（原子 DECR）—— 扛高并发
    ↓ 成功
② 发送 RocketMQ 消息（异步）
    ↓
③ StockDeductConsumer 消费
    ↓ 幂等校验（Redis SETNX 30s）
    ↓
④ 乐观锁更新 DB 库存（WHERE beforeStock）
    ↓
⑤ 写入流水表
```

### 7.2 幂等性实现

```java
// 幂等 key：que:lock:stock:{transactionId}
String lockKey = "que:lock:stock:" + dto.getTransactionId();
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
if (!Boolean.TRUE.equals(acquired)) {
    log.debug("重复消息跳过: {}", dto.getTransactionId());
    return;
}
```

### 7.3 乐观锁兜底

```java
@Transactional(rollbackFor = Exception.class)
public void recordStockFlow(StockDeductMessageDTO dto) {
    // 1. 写入流水表
    baseMapper.insert(flow);
    // 2. 乐观锁更新库存（WHERE beforeStock = 旧值）
    boolean updated = seckillGoodsService.update(null,
        new LambdaUpdateWrapper<SeckillGoods>()
            .eq(SeckillGoods::getActivityId, dto.getActivityId())
            .eq(SeckillGoods::getStoreCount, dto.getBeforeStock())
            .set(SeckillGoods::getStoreCount, dto.getAfterStock()));
    // 3. 更新流水状态
    flow.setStatus(updated ? 1 : 2);
    baseMapper.updateById(flow);
}
```

**三层设计的目的**：
| 层 | 作用 | 性能 |
|----|------|------|
| Redis 预扣 | 扛 10w QPS 瞬时并发 | 微秒级 |
| RocketMQ 异步 | 削峰填谷，保护 MySQL | 毫秒级 |
| 乐观锁兜底 | 保证最终一致性 | 行级锁 |

---

# 下篇：LangGraph RAG + Agent 智能问答实战

> 对应项目：`CropWise`（FastAPI + LangGraph + Hybrid RAG + Neo4j 知识图谱）

---

## 第八章：Hybrid RAG 检索增强架构

### 8.1 整体架构

```
用户问题
    ↓
Domain Guard（领域守卫）—— 非农业问题直接拒绝
    ↓
QueryTransformer（查询转换）
    └── 实体提取 + 意图检测 + Multi-Query 分解
    ↓
并行检索（3 路）
    ├── Vector Branch（BGE-M3 / ChromaDB）
    ├── BM25 Branch（中文农业分词）
    └── Graph Branch（Neo4j 知识图谱）
    ↓
RRF Fusion（k=60，分支加权融合）
    ↓
BGE-Reranker（交叉编码器精排 top-30 → top-5）
    ↓
证据门控（Evidence Gate）—— 低分证据过滤
    ↓
LLM 生成 + 决策卡输出
    ↓
SSE 流式响应
```

### 8.2 向量检索 + BM25 混合检索

```python
# retrieval/rrf_fusion.py — RRF 融合算法
def rrf_fusion(
    vector_results: List[Document],
    bm25_results: List[Document],
    k: int = 60
) -> List[Document]:
    """Reciprocal Rank Fusion 融合多路检索结果"""
    scores = {}
    for rank, doc in enumerate(vector_results):
        doc_id = doc.metadata.get("id", doc.page_content[:50])
        scores[doc_id] = scores.get(doc_id, 0) + 1 / (k + rank + 1)
    
    for rank, doc in enumerate(bm25_results):
        doc_id = doc.metadata.get("id", doc.page_content[:50])
        scores[doc_id] = scores.get(doc_id, 0) + 1 / (k + rank + 1)
    
    # 按分数降序排序
    sorted_docs = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return sorted_docs[:top_k]
```

**为什么用 RRF 而不是加权平均？**
- RRF 对排名敏感，对分数不敏感
- 不同检索器的分数尺度不同（余弦相似度 0-1，BM25 可能 0-100），直接加权没有意义
- RRF 天然鲁棒，不需要调权重

### 8.3 BGE-Reranker 精排

```python
# retrieval/reranker.py — 交叉编码器重排序
class BGEReranker:
    def rerank(self, query: str, documents: List[Document], top_k: int = 5) -> List[Document]:
        pairs = [[query, doc.page_content] for doc in documents]
        # 调用 BGE-Reranker API 计算相关性分数
        scores = self._call_reranker_api(pairs)
        
        # 按分数降序排序，取 top_k
        scored = list(zip(documents, scores))
        scored.sort(key=lambda x: x[1], reverse=True)
        return [doc for doc, _ in scored[:top_k]]
```

**Reranker 为什么比向量检索更准？**
- 向量检索：双编码器（query 和 doc 分别编码，余弦相似度）—— 丢失了 query 和 doc 之间的交互信息
- Reranker：交叉编码器（query 和 doc 拼接后一起输入）—— 能看到 query 和 doc 的完整交互

---

## 第九章：LangGraph Agent 编排

### 9.1 Agent 架构

```python
# agent.py — 农业智能问答 Agent
class AgricultureAgent:
    """基于 LangGraph 的农业问答案例"""
    
    def __init__(self):
        self.llm = ChatOpenAI(
            model=settings.agnes_chat_model,
            api_key=settings.agnes_api_key,
            base_url=settings.agnes_base_url,
        )
        self.tools = get_all_tools()  # 6 个工具
    
    async def stream(self, message: str, session_id: str) -> AsyncIterator[Dict]:
        """流式处理用户消息"""
        # 1. 领域守卫
        decision = classify_query(message)
        if not decision["allowed"]:
            yield {"type": "rejection", "reason": decision["reason"]}
            return
        
        # 2. 检索增强
        context = await agriir_pipeline.run(message)
        
        # 3. Agent 执行（ReAct 循环）
        async for event in self._react_loop(message, context, session_id):
            yield event
```

### 9.2 6 个工具定义

```python
# tools.py — 6 个农业工具
@tool
def query_crop_knowledge(crop_name: str, topic: str) -> str:
    """查询作物种植知识（crop_name + topic）"""
    return knowledge_base.similarity_search(f"{crop_name} {topic}")

@tool
def get_current_datetime() -> str:
    """获取当前日期时间"""
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")

@tool
def calculate_growing_period(crop: str, region: str, sowing_date: str) -> str:
    """计算作物生育期与农时"""
    return farming_calendar.calculate(crop, region, sowing_date)

@tool
def get_agri_weather(location: str, date: str) -> str:
    """获取农业气象预报"""
    return weather_service.query(location, date)

@tool
def fetch_web_content(url: str) -> str:
    """抓取网页内容"""
    response = requests.get(url, timeout=10)
    return response.text

@tool
def search_agri_resources(query: str) -> List[Dict]:
    """搜索农业图片和官方资料入口"""
    return wikimedia_search(query)
```

### 9.3 Domain Guard 领域守卫

```python
# domain_guard.py — 领域守卫（防止 AI 越界）
AGRICULTURE_TERMS = ("水稻", "小麦", "玉米", "病虫害", "施肥", "灌溉", ...)
NON_AGRICULTURE_TERMS = ("java", "python", "编程", "代码", "算法", ...)

def classify_query(message: str) -> DomainDecision:
    """判断是否属于农业知识范围"""
    text = message.lower()
    
    agri_hits = [t for t in AGRICULTURE_TERMS if t in text]
    non_agri_hits = [t for t in NON_AGRICULTURE_TERMS if t in text]
    
    # 代码生成直接拒绝（即使提到作物名）
    if non_agri_hits and any(t in text for t in ("代码", "编程", "java")):
        return {"allowed": False, "category": "unsupported_action", ...}
    
    if agri_hits:
        return {"allowed": True, "category": "agriculture", ...}
    
    return {"allowed": False, "category": "ambiguous", ...}
```

---

## 第十章：知识图谱（Neo4j + GraphRAG）

### 10.1 Docker 部署 Neo4j

```yaml
# docker-compose.neo4j.yml
services:
  neo4j:
    image: neo4j:5-community
    ports:
      - "7474:7474"   # HTTP 浏览器
      - "7687:7687"   # Bolt 驱动
    environment:
      NEO4J_AUTH: neo4j/cropwise2026
      NEO4J_PLUGINS: '["apoc"]'
    volumes:
      - neo4j_data:/data
      - ./backend/kg/init.cypher:/init.cypher
```

### 10.2 知识图谱 Schema

```cypher
// 12 类实体 + 16 类关系
CREATE CONSTRAINT crop_name IF NOT EXISTS FOR (c:Crop) REQUIRE c.name IS UNIQUE;
CREATE CONSTRAINT disease_name IF NOT EXISTS FOR (d:Disease) REQUIRE d.name IS UNIQUE;

// 创建实体
CREATE (c:Crop {name: '水稻', scientific_name: 'Oryza sativa'})
CREATE (d:Disease {name: '稻瘟病', pathogen: 'Magnaporthe oryzae'})
CREATE (p:Pest {name: '稻飞虱', scientific_name: 'Nilaparvata lugens'})

// 创建关系
CREATE (d)-[:AFFECTS]->(c)
CREATE (p)-[:DAMAGES]->(c)
```

### 10.3 Python 驱动连接

```python
# kg/connection.py
from neo4j import GraphDatabase

class Neo4jConnection:
    def __init__(self):
        self.driver = GraphDatabase.driver(
            settings.neo4j_uri,
            auth=(settings.neo4j_user, settings.neo4j_password)
        )
    
    def query_knowledge_graph(self, query: str, entity: str) -> List[Dict]:
        """查询知识图谱"""
        with self.driver.session() as session:
            result = session.run(
                f"MATCH (n)-[r]->(m) WHERE n.name CONTAINS $entity RETURN n, r, m",
                entity=entity
            )
            return [record.data() for record in result]
```

---

## 第十一章：SSE 流式响应与前端对接

### 11.1 后端 SSE 实现

```python
# main.py — SSE 流式端点
@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    async def event_generator():
        async for event in agri_agent.stream(request.message, request.session_id):
            yield f"event: {event['type']}\ndata: {json.dumps(event)}\n\n"
    
    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        }
    )
```

### 11.2 前端 Next.js 消费 SSE

```tsx
// frontend/app/chat/page.tsx
const response = await fetch('/api/chat/stream', {
    method: 'POST',
    body: JSON.stringify({ message, session_id }),
});

const reader = response.body!.getReader();
const decoder = new TextDecoder();

while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    
    const text = decoder.decode(value);
    // 解析 SSE 事件
    for (const event of parseSSE(text)) {
        if (event.type === 'token') {
            setAnswer(prev => prev + event.data);
        } else if (event.type === 'decision_card') {
            setDecisionCard(event.data);
        }
    }
}
```

---

## 第十二章：项目部署与运维

### 12.1 Docker 多阶段构建（Java 项目）

```dockerfile
# 第一阶段：构建
FROM maven:3.8-openjdk-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:resolve
COPY src ./src
RUN mvn package -DskipTests

# 第二阶段：运行
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 12.2 Python 项目一键启动

```bash
# 后端
cd backend && pip install -r requirements.txt && python main.py

# 前端
cd frontend && npm install && npm run dev

# Neo4j 知识图谱
docker compose -f docker-compose.neo4j.yml up -d
```

---

## 总结

本教程从**真实项目代码**出发，覆盖了从 Java 微服务到 AI RAG 的完整技术栈：

| 技术方向 | 核心能力 | 项目落地 |
|----------|---------|----------|
| Spring Cloud 微服务 | 服务拆分、Nacos、Gateway、Seata、RocketMQ、Sentinel | mall-micro-cloud 11 个微服务 |
| Hybrid RAG | Vector + BM25 + RRF + Reranker | CropWise 多路召回 |
| LangGraph Agent | ReAct 循环、6 工具编排、Domain Guard | 农业智能问答 |
| 知识图谱 | Neo4j 12 实体 + 16 关系 | GraphRAG 增强检索 |
| 流式响应 | SSE 协议、前端消费 | 毫秒级首字上屏 |

> 💡 **核心理念**：Java 后端 + AI 应用不是两条平行线，而是可以深度融合的。微服务为 AI 提供数据底座，AI 为业务系统提供智能能力——这正是 1byteone 技术栈官网传递的核心价值。