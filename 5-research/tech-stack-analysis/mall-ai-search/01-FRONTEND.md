# 01 · 前端技术栈：Vue3 + Axios + AI/ES 双搜索模式

> 从用户点击"搜索"按钮出发，看前端如何管理两种搜索模式，如何用 `Promise.allSettled` 实现 AI 接口的容错并行调用，以及 AI 推荐结果的渲染策略。
>
> **对应项目：** `frontend/ai_search.html`

---

## 一、基础概念

### 1.1 技术栈三件套

| 技术 | 版本 | 角色 | Java 生态对照 |
|------|------|------|--------------|
| **Vue3** | CDN (createApp) | 响应式 UI 框架，管理搜索状态、商品列表、AI 推荐结果 | Thymeleaf + Alpine.js / htmx |
| **Axios** | CDN | HTTP 客户端，封装请求/响应拦截 | RestTemplate / WebClient |
| **request.js** | 项目封装 | Axios 实例，统一 baseURL、超时、拦截器 | — |

**关键选择：CDN 方式引入。** 项目没有使用 Vite/webpack 构建工具，而是直接在 HTML 中通过 `<script>` 标签引入 Vue3 和 Axios。这意味着：
- 零构建步骤，改 HTML 即可生效
- 但无法使用 SFC（单文件组件）、TypeScript、ES Module 等现代化特性
- 适合演示/原型阶段，生产环境建议迁移到 Vite + Vue3 SFC

### 1.2 搜索模式设计

项目支持两种搜索模式，通过 radio 按钮切换：

```html
<label><input type="radio" v-model="searchMode" value="es">传统搜索</label>
<label><input type="radio" v-model="searchMode" value="ai">AI自然语言搜索</label>
```

| 模式 | 触发接口 | 数据处理 | 适用场景 |
|------|---------|---------|---------|
| `es`（传统） | `GET /search/product/page` | ES 关键词分页搜索 | 精确关键词查询 |
| `ai`（智能） | `GET /v1/search/recommend` + `GET /v1/search/extract` | LLM 语义理解 + 向量召回 | 模糊/自然语言查询 |

---

## 二、进阶机制

### 2.1 Promise.allSettled —— 容错并行调用

这是项目中最值得关注的前端设计模式。AI 搜索需要同时调用两个接口：

1. **`/v1/search/recommend`** — 获取 AI 推荐结果（核心功能，可能耗时较长）
2. **`/v1/search/extract`** — 提取结构化查询条件（辅助功能，用于后续 ES 分页）

```javascript
const [resRecommend, resExtract] = await Promise.allSettled([
  request.get("/v1/search/recommend", { params: { query, threadId } }),
  request.get("/v1/search/extract", { params: { query } })
]);
```

**为什么用 `allSettled` 而不是 `all`？**

| 特性 | `Promise.all` | `Promise.allSettled` |
|------|--------------|---------------------|
| 任一失败 | 整体 reject | 其他仍成功 |
| 结果 | 按序数组 / 抛异常 | `{status:'fulfilled', value}` 或 `{status:'rejected', reason}` |
| 适用场景 | 全部必须成功 | 各自独立，可部分失败 |

**在本项目中的实际价值：** `/extract` 接口的失败不影响 `/recommend` 的展示，反之亦然。用户可能只看到 AI 推荐但没有 ES 分页商品，或者只看到分页商品但没有 AI 推荐——体验降级而非完全不可用。

### 2.2 响应式状态管理

Vue3 的 `createApp` API 管理了以下状态分组：

```javascript
data() {
  return {
    // 搜索相关
    searchMode: "es",           // 搜索模式
    loading: false,             // 加载状态
    threadId: Math.random()*100000, // AI 会话 ID

    // AI 推荐相关
    aiRecommendList: [],        // 推荐商品列表
    aiSummary: "",              // 总结导语
    aiReasonList: [],           // 推荐理由数组

    // 传统搜索相关
    product: { productName, brandId, minPrice, maxPrice, ... },
    goodsList: {},              // 商品列表
    pagination: { pageNum, pages }, // 分页信息

    // 分类导航相关
    categoryList: {},
    twoCategoryList: [],
    threeCategoryList: [],
    brandList: [],
  }
}
```

状态按职责分组，但没有使用 Vuex/Pinia 等状态管理库，因为组件层级简单（单页面应用）。

---

## 三、项目现场

### 3.1 `aiSearch()` 方法全流程解析

这是项目的核心方法，整合了 AI 搜索的完整前端逻辑：

```javascript
async aiSearch() {
  // 1. 前置校验
  if (!this.product.productName) { alert("请输入查询内容"); return; }

  // 2. 清空旧数据
  this.loading = true;
  this.aiRecommendList = [];
  this.aiSummary = "";
  this.aiReasonList = [];
  this.goodsList = [];
  this.pagination = {};

  try {
    const queryText = this.product.productName;

    // 3. 并行调用 AI 接口（容错设计）
    const [resRecommend, resExtract] = await Promise.allSettled([
      request.get("/v1/search/recommend", { params: { query: queryText, threadId } }),
      request.get("/v1/search/extract", { params: { query: queryText } })
    ]);

    // 4. 处理推荐结果（fulfilled 才渲染）
    if (resRecommend.status === 'fulfilled' && resRecommend.value.code === 200) {
      const data = resRecommend.value.data;
      this.aiRecommendList = data.productList?.slice(0,5) || [];
      this.aiSummary = data.summary || "";
      this.aiReasonList = data.reason || [];
    }

    // 5. 处理提取结果 → 拼接 ES 分页查询参数
    let canQueryPage = false;
    if (resExtract.status === 'fulfilled' && resExtract.value.code === 200) {
      const extractData = resExtract.value.data;
      this.product.productName = extractData.keyword;
      this.product.minPrice = extractData.minPrice;
      this.product.maxPrice = extractData.maxPrice;
      canQueryPage = true;
    }

    // 6. 条件满足时，用提取的关键词调 ES 分页
    if (canQueryPage) {
      const respPage = await request.get('/search/product/page', { params: this.product });
      if (respPage.code === 200) {
        this.goodsList = respPage.data.records;
        this.pagination.pageNum = respPage.data.pageNum;
        this.pagination.pages = respPage.data.pages;
      }
    }
  } catch (e) {
    console.error("AI搜索异常", e);
    alert("AI搜索服务异常，请切换传统搜索");
  } finally {
    this.loading = false;
  }
}
```

**设计亮点：**

1. **先清空再渲染** — 避免旧数据闪烁
2. **`allSettled` 容错** — 两个接口独立成败
3. **`slice(0,5)` 限制展示** — 后端可能返回 10 条，前端只展示 5 条
4. **降级体验** — 推荐失败不影响 ES 分页，反之亦然
5. **`finally` 保证 loading 关闭** — 无论成功失败，都结束加载状态

### 3.2 搜索模式路由

```javascript
search() {
  if (this.loading) return;          // 防止重复提交
  this.product.pageNum = 1;
  if (this.searchMode === "ai") {
    this.aiSearch();                 // AI 模式
  } else {
    // 传统模式：清空 AI 数据
    this.aiRecommendList = [];
    this.aiSummary = "";
    this.aiReasonList = [];
    this.getData();                  // ES 分页查询
  }
}
```

### 3.3 AI 推荐结果的 UI 渲染

```html
<!-- 加载动画 -->
<div v-if="loading" class="search-loading">
  <div class="spinner-circle"></div>
</div>

<!-- 传统商品列表 -->
<div v-else class="goods-list">
  <ul class="yui3-g">
    <li v-for="item in goodsList">
      <div class="list-wrap">
        <div class="p-img">
          <img :src="item.skuDefaultImg" />
        </div>
        <div class="price"><strong><em>¥</em><i>{{item.price}}</i></strong></div>
        <div class="attr" v-html="item.skuName"></div>
      </div>
    </li>
  </ul>
</div>

<!-- AI 推荐栏目（仅 AI 模式有数据时展示） -->
<div v-if="!loading && aiRecommendList.length > 0" class="recommend-block">
  <div class="recommend-title">为你推荐</div>

  <!-- AI 总结 -->
  <div v-if="aiSummary" class="ai-summary-card">{{ aiSummary }}</div>

  <!-- 推荐理由列表 -->
  <div v-if="aiReasonList.length > 0" class="ai-reason-card">
    <div class="ai-reason-title">推荐理由：</div>
    <ul class="ai-reason-list">
      <li v-for="(r, idx) in aiReasonList">{{ r }}</li>
    </ul>
  </div>

  <!-- 推荐商品卡片 -->
  <div class="recommend-goods">
    <div class="recommend-item" v-for="item in aiRecommendList">
      <img :src="item.skuDefaultImg" />
      <div class="recommend-price">¥{{ item.price }}</div>
      <div>{{ item.skuName }}</div>
    </div>
  </div>
</div>
```

**渲染策略：**
- `v-if="loading"` + `v-else` 切换加载态和内容态
- AI 推荐区域使用 `v-if="!loading && aiRecommendList.length > 0"` 条件渲染，仅在 AI 模式且有数据时展示
- `v-if="aiSummary"` 和 `v-if="aiReasonList.length > 0"` 分别控制每个子区域的可见性，兼容后端部分字段为空的情况

---

## 四、Java 对照

### 4.1 Vue3 数据响应 ↔ Spring Boot 控制层

| 前端 (Vue3) | 后端 (Spring Boot) |
|------------|-------------------|
| `data()` 中定义响应式属性 | `@ModelAttribute` 绑定请求参数 |
| `v-model` 双向绑定 | `@RequestParam` 接收参数 |
| `v-for` 渲染列表 | `th:each` 或 JSON 返回 |

### 4.2 Axios 请求 ↔ RestTemplate/WebClient

```java
// Java 对照：WebClient 并行调用（类似 Promise.allSettled）
WebClient webClient = WebClient.builder()
    .baseUrl("http://ai-search-service")
    .build();

Mono<Result<ProductRecommendResponse>> recommendMono =
    webClient.get()
        .uri("/api/v1/recommend?query={query}&threadId={threadId}", query, threadId)
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<>() {});

Mono<Result<SearchCondition>> extractMono =
    webClient.get()
        .uri("/api/v1/extract?query={query}", query)
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<>() {});

// Zip 组合（类似 Promise.all）
Mono<Tuple2<Result<ProductRecommendResponse>, Result<SearchCondition>>> combined =
    Mono.zip(recommendMono, extractMono);

// 或者用 onErrorResume 实现容错（类似 allSettled）
recommendMono.onErrorResume(e -> Mono.just(new Result<>(500, "推荐失败")));
```

### 4.3 区别要点

| 维度 | 前端 (Vue3 + Axios) | Spring Boot 后端 |
|------|-------------------|-----------------|
| 渲染方式 | 客户端渲染 (CSR) | 服务端渲染 (SSR) / JSON 返回 |
| 状态管理 | Vue3 reactive state | Session / Redis 缓存 |
| 并行请求 | `Promise.allSettled` | `Mono.zip` / `CompletableFuture` |
| 容错 | 每个 Promise 独立处理 | `onErrorResume` / `@ExceptionHandler` |

---

## 五、最小可复现示例

### 5.1 纯前端：AI 搜索模式的 Promise.allSettled 调用

```html
<!DOCTYPE html>
<html>
<body>
  <h2>AI 搜索演示</h2>
  <input id="queryInput" placeholder="输入查询，如 5000元以下华为手机" />
  <button id="searchBtn">AI 搜索</button>
  <div id="loading" style="display:none">⏳ 搜索中...</div>
  <div id="result"></div>

  <script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>
  <script src="https://unpkg.com/axios/dist/axios.min.js"></script>
  <script>
    const { createApp } = Vue;
    createApp({
      data() {
        return {
          query: '',
          loading: false,
          recommendResult: null,
          extractResult: null,
          error: null
        }
      },
      methods: {
        async aiSearch() {
          if (!this.query) return;
          this.loading = true;
          this.error = null;
          this.recommendResult = null;
          this.extractResult = null;

          try {
            // 关键模式：Promise.allSettled 容错并行
            const [recommend, extract] = await Promise.allSettled([
              axios.get('/api/v1/recommend', { params: { query: this.query, threadId: 1 } }),
              axios.get('/api/v1/extract', { params: { query: this.query } })
            ]);

            if (recommend.status === 'fulfilled') {
              this.recommendResult = recommend.value.data;
            }

            if (extract.status === 'fulfilled') {
              this.extractResult = extract.value.data;
            }

            if (recommend.status === 'rejected' && extract.status === 'rejected') {
              this.error = 'AI 服务暂时不可用，请稍后重试';
            }
          } catch (e) {
            this.error = '系统异常：' + e.message;
          } finally {
            this.loading = false;
          }
        }
      }
    }).mount('#app');
  </script>
</body>
</html>
```

### 5.2 Java 对照：WebClient 容错并行调用

```java
@Component
public class AiSearchClient {
    private final WebClient webClient;

    public AiSearchClient(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("http://ai-search-service").build();
    }

    /**
     * 模拟 Promise.allSettled 的容错语义
     * 每个请求独立处理，失败时返回默认值而非抛出异常
     */
    public AiSearchResult search(String query, String threadId) {
        // 1. 推荐请求
        CompletableFuture<ProductRecommendResponse> recommendFuture =
            webClient.get()
                .uri("/api/v1/recommend?query={q}&threadId={t}", query, threadId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<ProductRecommendResponse>>() {})
                .map(Result::getData)
                .onErrorResume(e -> Mono.empty())  // 失败返回空
                .toFuture();

        // 2. 提取请求
        CompletableFuture<SearchCondition> extractFuture =
            webClient.get()
                .uri("/api/v1/extract?query={q}", query)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<SearchCondition>>() {})
                .map(Result::getData)
                .onErrorResume(e -> Mono.empty())  // 失败返回空
                .toFuture();

        // 3. 组合结果（CompletableFuture.allOf 类似 Promise.allSettled）
        return CompletableFuture.allOf(recommendFuture, extractFuture)
            .thenApply(v -> new AiSearchResult(
                recommendFuture.join(),  // 可能为 null
                extractFuture.join()     // 可能为 null
            ))
            .exceptionally(e -> new AiSearchResult(null, null))
            .join();
    }
}
```

---

## 六、面试要点

### Q1: 为什么用 Promise.allSettled 而不是 Promise.all？

**回答思路：** `/recommend` 和 `/extract` 是**两个独立的职责**，任一失败不影响另一方。`allSettled` 让每个请求独立决定自己的成败，用户体验降级而非崩溃。`all` 要求全部成功，任一失败整个链路断裂，不符合容错设计。

### Q2: 传统搜索和 AI 搜索前端如何切换？

**回答思路：** 通过 `searchMode` 变量控制路由。ES 模式直接调分页接口；AI 模式先并行调两个 AI 接口，提取关键词后复用 ES 分页接口。两种模式的数据在各自的状态树中独立管理，切换时清空对方数据。

### Q3: 前端如何防止重复提交？

**回答思路：** `search()` 方法入口检查 `if (this.loading) return;`，利用 Vue3 的响应式 `loading` 状态和按钮 `:disabled` 属性双重保障。

### Q4: AI 推荐结果中字段为空时如何保证页面不报错？

**回答思路：** 三层防御：
1. 后端保证 `product_list` / `summary` / `reason` 是空集合而非 null
2. 前端 `data.productList?.slice(0,5) \|\| []` 可选链 + 默认值
3. 模板端 `v-if="aiSummary"` 条件渲染，空数据不显示

### Q5: 这个前端架构如果要上生产，你会怎么改进？

**回答思路：**
1. 迁移到 Vite + Vue3 SFC + TypeScript，代码模块化
2. 引入 Pinia 管理状态，替代 `data()` 中扁平的状态
3. 用 Axios 拦截器统一处理 401/500 等错误码
4. 添加请求防抖（debounce），避免频繁搜索
5. 添加 AI 推荐的缓存策略（相同的 query 不重复请求）

---

> **下一篇：** [02-API-GATEWAY.md —— FastAPI 网关层：Pydantic + 路由 + 异常处理](./02-API-GATEWAY.md)
>
> 请求到达后端，看 FastAPI 如何组织路由、统一响应格式、处理全局异常，以及它与 Spring Boot 的对应关系。