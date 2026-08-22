# 补充篇 · AI 搜索桥接服务：Java → Python 的 Feign 桥接

> mall-aisearch-service 是 Java 微服务与 Python AI 搜索服务之间的桥梁。看它如何通过 Feign 调用 Python AI 接口，将 JSON 响应转为 Java DTO。
>
> **对应项目：** `mall-services/mall-aisearch-service` + `mall-api/aisearch/`

---

## 一、架构定位

### 1.1 桥接模式

```
┌─────────────────────────────────────────────────────────────────┐
│                      mall-micro-cloud (Java)                    │
│                                                                 │
│  前端 Vue3                                                       │
│    │                                                             │
│    ▼                                                             │
│  Gateway 网关                                                     │
│    │ 路由 /api/v1/**  →  lb://mall-aisearch-service              │
│    ▼                                                             │
│  mall-aisearch-service (Java 桥接服务)                            │
│    │                                                             │
│    │ OpenFeign 远程调用                                           │
│    ▼                                                             │
│  AiPythonFeignClient                                             │
│    │  http://127.0.0.1:9010/api/v1/{recommend,extract}           │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                   mall-ai-search (Python FastAPI)                │
│                                                                 │
│  GET /api/v1/recommend?query=...&thread_id=...                  │
│  GET /api/v1/extract?query=...                                   │
│  → LangChain Agent → RedisVL → LLM → 返回 JSON                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、进阶机制

### 2.1 Feign 客户端定义

```java
@FeignClient(contextId = "ai-python-feign", name = "python-ai-server",
             url = "http://127.0.0.1:9010")  // 直连 Python 服务
public interface AiPythonFeignClient {

    @GetMapping("/api/v1/recommend")
    Result recommend(@RequestParam("query") String query,
                     @RequestParam("thread_id") String threadId);

    @GetMapping("/api/v1/extract")
    Result extract(@RequestParam("query") String query);
}
```

**设计要点：**

| 要素 | 说明 |
|------|------|
| `url = "http://127.0.0.1:9010"` | 直连 Python 服务，不走 Nacos 注册中心 |
| `contextId = "ai-python-feign"` | 避免与其它 FeignClient bean name 冲突 |
| 参数名 `thread_id` | 与 Python 服务参数名保持一致 |

### 2.2 服务实现 —— JSON 解析

```java
@Service
public class AiSearchSeriveImpl implements IAiSearchService {

    @Autowired
    private AiPythonFeignClient aiPythonFeignClient;

    @Override
    public ProductRecommendDTO recommend(String query, String threadId) {
        // 1. Feign 调用 Python AI 服务
        Result resp = aiPythonFeignClient.recommend(query, threadId);

        // 2. 业务状态码校验
        if (resp.getCode() != 200) {
            throw new BusinessException(80001, "AI服务返回异常:" + resp.getMsg());
        }

        // 3. 将 JSON 转为 Java 对象（手动解析嵌套字段）
        JSONObject recommendJson = JSONObject.parseObject(JSON.toJSONString(resp.getData()));
        JSONArray productListJson = JSONArray.parseArray(
            JSON.toJSONString(recommendJson.get("product_list")));

        List<ProductDTO> productList = new ArrayList<>();
        for (Object object : productListJson) {
            JSONObject productJson = JSONObject.parseObject(JSON.toJSONString(object));
            ProductDTO productDTO = CamelCastUtils.jsonToObject(productJson, ProductDTO.class);
            productList.add(productDTO);
        }

        ProductRecommendDTO productRecommendDTO = CamelCastUtils.jsonToObject(
            recommendJson, ProductRecommendDTO.class);
        productRecommendDTO.setProductList(productList);
        return productRecommendDTO;
    }
}
```

---

## 三、面试要点

### Q1: 为什么 Java 不直接调 Python AI 服务，而要加一个桥接服务？

**回答思路：** 三个原因：1) **统一 Gateway 路由**——所有请求都经过 Gateway，前端不需要知道 Python 服务的地址；2) **类型转换层**——Python 返回的 JSON 字段名（`product_list`）与 Java 的字段名（`productList`）不同，需要转换；3) **故障隔离**——Python 服务异常时，Java 桥接服务可以返回兜底数据，而不是让前端直接报错。

### Q2: 为什么不用 Nacos 注册 Python 服务，而是硬编码 URL？

**回答思路：** Python 服务不是 Spring Cloud 应用，无法注册到 Nacos。硬编码 URL 是当前阶段的简化方案。改进方向：1) 将 URL 放入 Nacos 配置中心动态管理；2) 用单独的 Nacos HTTP 注册；3) 通过 API Gateway 统一路由。

---

> **下一篇：** [更新现有文档 —— 补充 00-OVERVIEW、06-SECKILL、10-ARCHITECTURE 中遗漏的内容](./UPDATE-EXISTING.md)
>
> 更新全景导读、秒杀篇、架构复盘篇，补全 ElasticJob、布隆过滤器、MQ 幂等消费、AI 桥接服务等内容。