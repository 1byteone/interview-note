# ai-passage-creator-demo 面试题汇总

> 覆盖全部技术栈：Spring AI Alibaba 1.1.0 StateGraph 多 Agent 编排、5 Agent 协作流程、策略模式配图（6 种配图方式 + 降级）、SSE 流式输出、人机协作三阶段创作、MyBatis-Flex 1.11.1、Stripe 31.2.0 支付集成、Redis + Redisson 缓存与分布式锁、Vue 3.5 + Ant Design Vue 4.2 + Pinia 3.0 前端架构。

---

## 一、选择题（10 道）

### 第 1 题
ai-passage-creator-demo 中，Spring AI Alibaba 的 StateGraph 通过什么机制控制多个节点向同一个状态 key 写入数据时的合并行为？

A. 使用 `@Merge` 注解标注在状态字段上  
B. 使用 `KeyStrategy` 接口定义合并策略，支持覆盖、追加等模式  
C. 使用 `StateMerger` 配置类在全局统一配置  
D. 默认使用后写入覆盖前写入，无法自定义

**答案：B**

**解析：** StateGraph 的 `KeyStrategy` 控制状态合并行为。常见实现包括 `OverrideStrategy`（覆盖策略，后写入的值覆盖前值）和 `AppendStrategy`（追加策略，将值追加到已有列表末尾）。在项目中，`keyStrategyFactory` 作为参数传入 `StateGraph` 构造器。KeyStrategy 是 StateGraph 状态管理的关键概念，类比 LangGraph 的 `Channel.Reducer`。

---

### 第 2 题
在项目的 5 Agent 协作流程中，哪两个 Agent 使用并行执行而非顺序执行？

A. TitleGeneratorAgent 和 OutlineGeneratorAgent  
B. ContentGeneratorAgent 和 ImageAnalyzerAgent  
C. ImageAnalyzerAgent 和 ParallelImageGenerator  
D. ContentGeneratorAgent 和 ContentMergerAgent

**答案：B**

**解析：** 在 StateGraph 编排中，`content_generator` 和 `image_analyzer` 使用 `node_async()` 异步节点执行。ContentGeneratorAgent 生成正文的同时，ImageAnalyzerAgent 开始分析正文确定配图需求。ParallelImageGenerator 内部也实现了多张配图的并行生成。这种流水线 + 并行混合架构提高了整体吞吐量。

---

### 第 3 题
项目中配图系统采用策略模式，以下关于 `ImageSearchService` 接口设计的描述，正确的是？

A. 接口定义 `search(String keyword, int count)` 和 `supports(ImageMethodEnum method)` 两个方法，所有配图策略实现此接口  
B. 接口定义 `search(String keyword)` 一个方法，所有策略共用同一方法签名  
C. 接口定义 `search(String keyword, int count, ImageMethodEnum method)` 一个方法，参数包含策略类型  
D. 接口定义 `search(String keyword, ImageMethodEnum method)` 方法，通过 `@Service` 注解自动注册

**答案：A**

**解析：** `ImageSearchService` 接口定义了 `search(String keyword, int count)` 和 `supports(ImageMethodEnum method)` 两个方法。`search` 负责执行搜索，`supports` 用于判断该服务是否支持某种配图方式。`ImageServiceStrategy` 通过 `Map<String, ImageSearchService>` 管理所有策略实例，运行时根据 `ImageMethodEnum` 选择对应的策略。

---

### 第 4 题
项目中的 SSE 事件定义，以下哪个事件不是在配图阶段触发的？

A. `AGENT4_COMPLETE`  
B. `IMAGE_COMPLETE`  
C. `AGENT2_STREAMING`  
D. `MERGE_COMPLETE`

**答案：C**

**解析：** `AGENT2_STREAMING` 是在大纲生成阶段（Agent 2：OutlineGeneratorAgent）流式输出大纲时触发的事件，属于三阶段创作流程的"大纲阶段"而非配图阶段。配图阶段（阶段 3 内部）的事件包括：`AGENT4_COMPLETE`（配图分析完成）、`IMAGE_COMPLETE`（单张配图完成）、`AGENT5_COMPLETE`（全部配图就绪）、`MERGE_COMPLETE`（图文合并完成）。

---

### 第 5 题
项目采用三阶段人机协作创作流程，以下关于 `ArticlePhase` 枚举定义的描述，正确的是？

A. `TITLE_SELECTION`、`OUTLINE_EDITING`、`CONTENT_GENERATION`、`COMPLETED` 四个阶段  
B. `TITLE_INPUT`、`OUTLINE_EDITING`、`CONTENT_GENERATION`、`IMAGE_GENERATION`、`COMPLETED` 五个阶段  
C. `TITLE_SELECTION`、`OUTLINE_EDITING`、`CONTENT_GENERATION`、`IMAGE_GENERATION`、`COMPLETED` 五个阶段  
D. `TITLE_INPUT`、`OUTLINE_INPUT`、`CONTENT_INPUT`、`COMPLETED` 四个阶段

**答案：A**

**解析：** `ArticlePhase` 枚举定义了四个阶段：`TITLE_SELECTION`（选题选择中）、`OUTLINE_EDITING`（大纲编辑中）、`CONTENT_GENERATION`（正文生成中）、`COMPLETED`（完成）。注意正文生成和配图生成是合并为 `CONTENT_GENERATION` 一个阶段的，因为 StateGraph 自动编排了正文 + 配图的流程。每个阶段用户都可以接受、编辑、重新生成或要求 AI 优化。

---

### 第 6 题
项目的配图降级链的正确顺序是？

A. Picsum 随机图片 → 主配图策略 → 跳过配图  
B. 主配图策略 → 跳过配图 → Picsum 随机图片  
C. 主配图策略 → Picsum 随机图片 → 跳过配图  
D. 跳过配图 → 主配图策略 → Picsum 随机图片

**答案：C**

**解析：** 降级链为：`主配图策略 → Picsum 随机图片 → 跳过配图`。ImageAnalyzerAgent 根据正文内容选择最合适的配图方式，调用对应 `ImageSearchService` 获取图片。如果主策略失败（网络超时、API 限额、无结果），自动降级到 Picsum 随机图片。如果 Picsum 也失败，跳过该段落的配图，继续处理下一段。这种设计保证文章生成不中断。

---

### 第 7 题
MyBatis-Flex 相比 MyBatis-Plus 的核心优势在于？

A. 有更丰富的社区生态和文档  
B. 运行时通过拦截器 + SQL 解析器动态生成 SQL，性能更好  
C. 通过 APT 在编译期生成静态元数据，无运行时反射和 SQL 解析开销  
D. 支持更多数据库类型

**答案：C**

**解析：** MyBatis-Flex 通过 APT（Annotation Processing Tool）在编译期扫描 `@Table` 和 `@Column` 注解，生成对应的静态元数据类（如 `ACCOUNT` 表字段常量），运行时直接使用这些常量构建查询，无需任何 SQL 解析和反射。MyBatis-Plus 在运行时通过拦截器 + SQL 解析器动态生成 SQL，存在解析开销。这是 MyBatis-Flex 性能更高的根本原因。

---

### 第 8 题
Stripe 31.x Java SDK 中，创建一个支付意图（PaymentIntent）的正确方式是什么？

A. `Stripe.apiKey = "sk_test_xxx"; PaymentIntent.create(params);`  
B. `StripeClient client = new StripeClient("sk_test_xxx"); client.v1().paymentIntents().create(params);`  
C. `new PaymentIntent().create(params);`  
D. `Stripe.createPaymentIntent(params);`

**答案：B**

**解析：** Stripe Java SDK 31.x 采用新的 `StripeClient` 客户端 API，替代了旧版本（30.x 之前）的静态方法 API。新 API 通过 `StripeClient` 实例的 `v1().paymentIntents().create(params)` 创建支付意图。选项 A 是旧版本 API，选项 C 和 D 是错误用法。项目中使用 Checkout Session 托管结账页面，VIP 会员体系基于 Stripe 支付实现。

---

### 第 9 题
Redisson 分布式锁的 WatchDog 机制，其默认的锁过期时间和续期间隔分别是？

A. 过期 30 秒，每 10 秒续期一次  
B. 过期 60 秒，每 30 秒续期一次  
C. 过期 10 秒，每 5 秒续期一次  
D. 过期 30 秒，每 30 秒续期一次

**答案：A**

**解析：** Redisson WatchDog 的默认行为：锁过期时间 30 秒，看门狗后台线程每 10 秒检查一次锁是否还在持有，如果持有则续期 30 秒。这种机制解决了原生 Redis `SETNX + EXPIRE` 最大的痛点——"业务执行时间 > 锁过期时间"导致锁提前释放。如果持有锁的线程崩溃，看门狗随 JVM 守护线程销毁，锁正常超时释放。

---

### 第 10 题
Vue 3.5 Composition API 中，`<script setup>` 语法糖下，子组件向父组件发送事件使用哪个 API？

A. `this.$emit()`  
B. `defineEmits()`  
C. `emit()`  
D. `useEmit()`

**答案：B**

**解析：** 在 `<script setup>` 语法糖中，使用 `defineEmits()` 声明组件可以触发的事件，返回值是一个 emit 函数，调用它来触发事件。例如：`const emit = defineEmits<{ select: [title: string] }>(); emit('select', title)`。选项 A `this.$emit()` 是 Options API 的用法，在 `<script setup>` 中不可用（没有 `this`）。选项 C 和 D 不是 Vue 的 API。

---

## 二、判断题（5 道）

### 第 1 题
Spring AI Alibaba 的 StateGraph 中，`node_async()` 创建的异步节点可以并行执行，但无法保证节点间的执行顺序。

**答案：错误**

**解析：** `node_async()` 创建的异步节点虽然不阻塞主线程，但节点间的执行顺序仍然由 `addEdge()` 定义的边决定。在项目中，`content_generator` 和 `image_analyzer` 使用 `node_async()` 异步执行，但边定义 `addEdge("content_generator", "image_analyzer")` 确保 `image_analyzer` 必须在 `content_generator` 完成后才能开始。`node_async` 控制的是"是否阻塞主线程"，而非"是否保证顺序"。

---

### 第 2 题
项目的配图策略中，Picsum 随机图片是所有用户均可使用的配图方式。

**答案：错误**

**解析：** Picsum 随机图片在所有配图策略中质量最低（"低"），它的定位是"降级兜底"策略——当主配图策略失败时自动回退到 Picsum。它不是一种主动选择的配图方式，而是降级链中的中间环节。项目的配图方式分为"全部用户"和"VIP 用户"两类，Picsum 属于降级策略，不在正常配图方式之列。

---

### 第 3 题
项目的三阶段创作流程中，用户在每个阶段都可以选择"接受"、"编辑"、"重新生成"或"要求 AI 优化"。

**答案：正确**

**解析：** 项目核心设计理念是"AI 生成 + 人工把关"，三阶段（选题、大纲、正文+配图）的每个阶段，用户都可以：接受（AI 结果满意，直接进入下一阶段）、编辑（手动修改 AI 生成的内容）、重新生成（要求 AI 基于反馈重新生成）、优化（对 AI 生成内容提出修改意见）。这是 Human-in-the-loop 设计模式的具体体现。

---

### 第 4 题
项目的 SSE 实现中，`SseEmitter` 的超时时间设置为 0 表示永不超时，连接将一直保持直到客户端主动关闭。

**答案：正确**

**解析：** `new SseEmitter(0L)` 中的参数是超时时间（毫秒），0 表示永不超时。在 AI 创作场景中，正文生成可能需要较长时间（30 秒以上），设置永不超时可以避免连接在生成过程中断开。连接通过 `onCompletion` 回调（客户端断开）或 `MERGE_COMPLETE` 事件（创作完成）来关闭，不会因为超时而中断。

---

### 第 5 题
Pinia 3.0 中，定义 store 时必须使用 Options API 风格（state、getters、actions 对象），不支持 Composition API 风格。

**答案：错误**

**解析：** Pinia 3.0 完全基于 Vue 3 Composition API 构建，支持两种定义 store 的方式：1）Options API 风格：`defineStore('id', { state: () => ({}), getters: {}, actions: {} })`；2）Composition API 风格（推荐）：`defineStore('id', () => { const count = ref(0); const increment = () => count.value++; return { count, increment } })`。项目中 `useCreationStore` 使用 Composition API 风格，因为创作流程的状态逻辑复杂，组合式 API 让状态管理更灵活。

---

## 三、简答题（10 道）

### 第 1 题：Spring AI Alibaba StateGraph 的核心概念有哪些？项目中如何利用 StateGraph 编排多 Agent 流程？

**参考答案：**

StateGraph 的核心概念包括：`StateGraph`（有向图工作流）、`Node`（处理步骤，封装 Agent 逻辑）、`Edge`（节点间的流转关系）、`ConditionalEdge`（条件路由）、`State`（节点间共享的状态数据）、`KeyStrategy`（状态合并策略）、`CompiledGraph`（编译后的可执行图）、`ParallelNode`（并行执行子节点）。

项目中利用 StateGraph 编排正文+配图阶段的 4 个 Agent：

```java
StateGraph graph = new StateGraph(keyStrategyFactory)
    .addNode("content_generator", node_async(contentGeneratorAgent))
    .addNode("image_analyzer", node_async(imageAnalyzerAgent))
    .addNode("parallel_image_generator", node_async(parallelImageGenerator))
    .addNode("content_merger", node_async(contentMergerAgent))
    .addEdge(START, "content_generator")
    .addEdge("content_generator", "image_analyzer")
    .addEdge("image_analyzer", "parallel_image_generator")
    .addEdge("parallel_image_generator", "content_merger")
    .addEdge("content_merger", END);
```

关键设计点：使用 `node_async()` 实现异步执行，`parallel_image_generator` 内部多线程并发生成多张配图，`KeyStrategy` 控制状态的合并策略。

---

### 第 2 题：5 Agent 协作流程中，每个 Agent 的职责是什么？为什么需要 5 个 Agent 而不是一个？

**参考答案：**

5 个 Agent 的职责：

| Agent | 职责 | 输入 | 输出 |
|-------|------|------|------|
| TitleGeneratorAgent | 根据选题生成 3-5 个标题方案 | 用户选题 | 标题列表 |
| OutlineGeneratorAgent | 根据选定标题生成文章大纲 | 标题 | 结构化大纲（流式） |
| ContentGeneratorAgent | 根据大纲生成 Markdown 正文 | 大纲 | Markdown 正文（流式） |
| ImageAnalyzerAgent | 分析正文确定配图需求 | 正文 | 配图需求列表 |
| ParallelImageGenerator | 并行获取配图，上传 COS | 配图需求 | 图片 URL 列表 |
| ContentMergerAgent | 将配图嵌入正文对应位置 | 正文 + 图片URL | 完整图文文章 |

**为什么需要 5 个 Agent 而不是一个？**
单一职责原则在 AI Agent 中的应用：每个 Agent 聚焦单一任务，降低 Prompt 复杂度，避免指令冲突，便于独立测试和优化。同时，分工后可以并行执行（如配图生成），提高整体效率。此外，在三阶段人机协作中，每个阶段需要用户介入确认，分 Agent 设计让用户可以在每个阶段独立编辑和优化。

---

### 第 3 题：项目的配图系统如何实现策略模式？6 种配图方式分别是什么？如何扩展新的配图方式？

**参考答案：**

**策略模式设计**：通过 `ImageSearchService` 接口和 `ImageMethodEnum` 枚举实现。`ImageSearchService` 定义 `search(String keyword, int count)` 和 `supports(ImageMethodEnum method)` 两个方法，每种配图方式实现此接口并通过 `@Service("xxx")` 注册。`ImageServiceStrategy` 通过 `Map<String, ImageSearchService>` 管理所有策略，运行时根据 `ImageMethodEnum` 选择对应策略。

**6 种配图方式**：

| 方式 | 实现 | 权限 | 质量 |
|------|------|------|------|
| Pexels | Pexels API 关键词搜索 | 全部用户 | 高 |
| Mermaid | AI 生成 Mermaid → 渲染 | 全部用户 | 中高 |
| Iconify | Iconify 图标库搜索 | 全部用户 | 中 |
| 表情包 | Bing 图片搜索 | 全部用户 | 中 |
| Nano Banana | Gemini AI 生图 | VIP | 高 |
| SVG Diagram | AI 生成 SVG 代码 | VIP | 高 |

**扩展新方式只需三步**：1）在 `ImageMethodEnum` 中新增枚举值；2）实现 `ImageSearchService` 接口，使用 `@Service("xxx")` 注册；3）在配置中启用新方式。完全符合开闭原则，零修改现有代码。

---

### 第 4 题：项目的三阶段人机协作创作流程是如何设计的？数据库如何支持断点续作？

**参考答案：**

**三阶段设计**：

| 阶段 | 生成内容 | 用户参与方式 |
|------|----------|-------------|
| 选题 | 3-5 个标题 | 选择标题 or 要求重新生成 |
| 大纲 | 结构化大纲 | 直接编辑 or AI 优化 |
| 正文+配图 | 完整图文文章 | 实时观察进度，最终确认 |

每个阶段用户都可以：接受、编辑、重新生成、要求 AI 优化。

**断点续作支持**：
- `article` 表的 `phase` 字段（`TITLE_SELECTION` / `OUTLINE_EDITING` / `CONTENT_GENERATION` / `COMPLETED`）追踪当前阶段
- 中间结果（标题选项、大纲、正文）实时保存到数据库
- 用户可随时回到之前的阶段重新处理
- 前端通过 `taskId` 恢复上下文，重新建立 SSE 连接

---

### 第 5 题：MyBatis-Flex 的 APT 机制是什么？它如何提升性能？

**参考答案：**

APT（Annotation Processing Tool）是 Java 编译期的注解处理器机制。MyBatis-Flex 利用 APT 在编译期扫描 `@Table` 和 `@Column` 注解，生成对应的静态元数据类（如 `ACCOUNT` 表字段常量）。

**性能提升原因**：
1. **无运行时反射**：生成的字段常量（如 `ACCOUNT.ID`、`ACCOUNT.USER_NAME`）是编译期确定的静态变量，运行时直接引用，无需反射获取字段信息
2. **无 SQL 解析**：MyBatis-Plus 在运行时通过拦截器 + SQL 解析器动态解析 SQL 语句，MyBatis-Flex 的 QueryWrapper 直接使用编译期生成的字段常量构建查询，无解析开销
3. **编译期错误检查**：如果字段名拼写错误，编译期即可发现，不会等到运行时才报错

**示例**：`@Table("tb_account")` 注解的 `Account` 实体，APT 在编译期生成 `ACCOUNT` 类，包含 `ID`、`USER_NAME` 等静态字段常量。QueryWrapper 中直接使用 `ACCOUNT.ID.eq(1)` 构建查询条件。

---

### 第 6 题：项目中 Stripe 支付集成是如何实现的？Webhook 如何处理？

**参考答案：**

**支付流程**：
1. 用户选择 VIP 套餐，前端请求后端创建 Checkout Session
2. 后端使用 `StripeClient` 创建 `checkout.Session`，返回 session URL
3. 前端跳转到 Stripe 托管结账页面
4. 用户完成支付，Stripe 回调 Webhook

**Webhook 处理**：
```java
@PostMapping("/stripe/webhook")
public ResponseEntity<String> handleWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String sigHeader) {
    
    Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    
    switch (event.getType()) {
        case "checkout.session.completed":
            handlePaymentSuccess(session);
            break;
        case "customer.subscription.deleted":
            handleSubscriptionCancelled(event);
            break;
    }
    return ResponseEntity.ok();
}
```

**关键设计点**：
- 签名验证：`Webhook.constructEvent()` 使用 HMAC-SHA256 验证签名，确保回调来自 Stripe
- 幂等性：使用 `stripeSessionId` 唯一索引防止重复处理
- VIP 过期：本地记录 VIP 过期时间，每次请求时校验

---

### 第 7 题：项目中的 SSE 事件体系是如何设计的？有哪些事件类型？

**参考答案：**

SSE 事件体系基于 Spring 的 `SseEmitter` 实现，通过 `SseEmitterManager` 管理所有连接：

```java
public class SseEmitterManager {
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    
    public void sendEvent(String taskId, String eventName, Object data) {
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        }
    }
}
```

**10 种事件类型**：

| 事件名 | 触发时机 | 数据 |
|--------|----------|------|
| `AGENT1_COMPLETE` | 标题生成完成 | 标题列表 |
| `AGENT2_STREAMING` | 大纲流式输出中 | 当前片段 |
| `AGENT2_COMPLETE` | 大纲生成完成 | 完整大纲 |
| `AGENT3_STREAMING` | 正文流式输出中 | 当前片段 |
| `AGENT3_COMPLETE` | 正文生成完成 | 完整正文 |
| `AGENT4_COMPLETE` | 配图分析完成 | 配图需求列表 |
| `IMAGE_COMPLETE` | 单张配图完成 | 图片 URL |
| `AGENT5_COMPLETE` | 全部配图就绪 | 图片 URL 列表 |
| `MERGE_COMPLETE` | 图文合并完成 | 最终文章 |
| `ERROR` | 发生错误 | 错误信息 |

---

### 第 8 题：项目中 Redisson 分布式锁的 WatchDog 机制的原理是什么？如何防止锁提前释放？

**参考答案：**

**WatchDog 原理**：
1. 调用 `lock.lock()` 时，默认锁过期时间为 30 秒
2. Redisson 启动一个后台守护线程（WatchDog），每 10 秒检查一次
3. 如果业务线程仍在持有锁，WatchDog 自动续期 30 秒
4. 如果业务线程崩溃（JVM 进程退出），WatchDog 随守护线程销毁，锁正常超时释放

**解决的问题**：原生 Redis `SETNX + EXPIRE` 方案最大的痛点是"业务执行时间 > 锁过期时间"导致锁提前释放，其他实例趁虚而入。WatchDog 通过动态续期保证锁在整个业务执行期间有效。

**使用注意事项**：
- `tryLock(5, 30, TimeUnit.SECONDS)`：等待 5 秒获取锁，锁过期 30 秒（此时 WatchDog 不工作，因为指定了 leaseTime）
- `lock.lock()`：不指定过期时间，WatchDog 自动续期
- 释放锁时必须在 finally 块中检查 `isHeldByCurrentThread()`

---

### 第 9 题：项目的配图降级策略如何设计？降级触发条件有哪些？

**参考答案：**

**降级链**：`主配图策略 → Picsum 随机图片 → 跳过配图`

**执行流程**：
1. ImageAnalyzerAgent 分析正文内容，判断每段最适合的配图方式
2. 调用对应 `ImageSearchService` 获取图片
3. 主策略失败 → 自动降级到 Picsum 随机图片
4. Picsum 也失败 → 跳过该段落的配图，继续处理下一段

**降级触发条件**：
- 网络超时或异常
- API 调用次数超限
- 搜索结果为空
- 图片下载失败
- 内容审核不通过

**设计优点**：保证文章生成不中断；对不同优先级用户差异化对待（VIP 用户更多高质量方式）；通过配置中心可动态调整降级阈值。

---

### 第 10 题：Vue 3 前端如何实现 SSE 监听？EventSource 的使用要点和注意事项？

**参考答案：**

**基本实现**：
```typescript
const eventSource = new EventSource(`/api/article/generate/${taskId}`)

eventSource.addEventListener('AGENT3_STREAMING', (event) => {
    const data = JSON.parse(event.data)
    content.value += data.text  // 增量追加
})

eventSource.addEventListener('MERGE_COMPLETE', () => {
    eventSource.close()  // 完成关闭
})
```

**注意事项**：
1. **自动重连**：EventSource 内置断线重连机制，连接断开后自动重连
2. **命名事件**：使用 `addEventListener('EVENT_NAME', handler)` 监听命名事件，而非 `onmessage`
3. **连接关闭**：完成或错误时主动 `eventSource.close()`，防止内存泄漏
4. **HTTP 限制**：EventSource 只能发送 GET 请求，不支持自定义请求头。Token 可通过 URL 参数传递
5. **内存管理**：组件卸载时（`onUnmounted`）必须关闭连接
6. **数据解析**：`event.data` 是字符串，需 `JSON.parse()` 解析

---

## 四、场景题（5 道）

### 第 1 题：创作流程中断恢复场景
**场景：** 用户在创作过程中浏览器意外刷新，此时正文已经生成到一半（大纲阶段完成，正文生成中）。请描述用户刷新后，前端和后端如何协作恢复创作流程，而不是重新开始。

**参考答案：**

恢复流程分为三步：

**1. 前端恢复上下文**：
- 用户刷新后，页面重新加载，从 URL 参数或 localStorage 中读取 `taskId`
- 前端调用 `GET /api/article/{taskId}` 获取文章当前状态
- 后端返回 `phase` 字段（当前阶段）、`outline`（已生成的大纲）、`content`（已生成的正文片段）、`titleOptions` 等

**2. 恢复 SSE 连接**：
- 根据 `phase` 字段，判断当前处于哪个阶段
- 重新建立 SSE 连接：`new EventSource('/api/article/generate/${taskId}')`
- 后端检查 `phase` 状态，如果 `CONTENT_GENERATION` 阶段但正文未完成，继续执行正文生成；如果已完成，直接返回已有结果

**3. 状态恢复**：
- Pinia Store 使用 `$patch()` 一次性恢复所有状态
- 进度条根据已有进度重新计算百分比
- 如果正文已生成完毕但配图未完成，仅恢复配图阶段的 SSE 监听

**关键设计**：数据库的 `phase` 字段和中间结果的持久化是断点续作的基础，`taskId` 是恢复上下文的关键标识。

---

### 第 2 题：配图策略智能选择场景
**场景：** 用户创作一篇关于"微服务架构设计"的技术文章，正文包含微服务拆分原则、服务间通信、API 网关、分布式事务等章节。请描述 ImageAnalyzerAgent 如何自动为不同段落选择最合适的配图方式。

**参考答案：**

`ImageAnalyzerAgent` 通过 LLM 分析正文内容，为每段选择最合适的配图方式：

**1. 内容分析**：LLM 分析每段内容，判断其类型（技术概念、架构描述、对比分析、幽默总结等）

**2. 智能匹配**：
- "微服务拆分原则"段落 → **Mermaid 图表**：绘制拆分前后的架构对比图
- "服务间通信（REST/gRPC/消息队列）"段落 → **SVG 图解**：绘制通信模式对比图
- "API 网关"段落 → **Mermaid 图表**：绘制网关路由示意图
- "分布式事务（Seata AT/TCC/Saga）"段落 → **SVG 图解**：绘制事务流程对比图
- 总结段落 → **Iconify 图标**：点缀相关概念图标

**3. 降级处理**：如果某个段落的最优策略不可用（如 Nano Banana 需要 VIP），自动降级到次优策略

**4. 配图需求输出**：每段输出一个配图需求对象，包含段落位置、配图方式、关键词、图片描述

---

### 第 3 题：高并发配图生成场景
**场景：** 某篇技术文章需要 5 张配图，使用 Pexels 配图方式。但 Pexels API 有频率限制（每秒 10 次请求）。请描述 ParallelImageGenerator 如何管理并发请求、处理限流和失败降级。

**参考答案：**

**1. 并发控制**：`ParallelImageGenerator` 内部使用线程池管理并发：
```java
// 线程池配置
ExecutorService executor = Executors.newFixedThreadPool(5);
// 每个配图需求提交一个任务
List<CompletableFuture<ImageResult>> futures = requirements.stream()
    .map(req -> CompletableFuture.supplyAsync(() -> 
        imageServiceStrategy.getService(req.getMethod()).search(req.getKeyword(), 1), executor))
    .collect(Collectors.toList());
// 等待所有完成
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

**2. 限流策略**：使用令牌桶或信号量控制 Pexels API 的请求频率：
```java
RateLimiter rateLimiter = RateLimiter.create(10); // 每秒 10 个请求
// 每次调用前获取令牌
rateLimiter.acquire();
```

**3. 失败降级**：每张配图独立捕获异常，单张失败不影响其他配图：
```java
futures.forEach(future -> {
    try {
        ImageResult result = future.get(10, TimeUnit.SECONDS);
        // 发送 IMAGE_COMPLETE 事件
        sseEmitterManager.sendEvent(taskId, "IMAGE_COMPLETE", result);
    } catch (Exception e) {
        // 降级到 Picsum
        ImageResult fallback = picsumService.search(req.getKeyword(), 1);
        sseEmitterManager.sendEvent(taskId, "IMAGE_COMPLETE", fallback);
    }
});
```

**4. 结果收集**：每完成一张配图就通过 SSE 推送给前端，实现实时展示。

---

### 第 4 题：VIP 会员权益控制场景
**场景：** 系统需要根据用户 VIP 状态控制配图方式的使用权限。免费用户只能使用 Pexels、Mermaid、Iconify 和表情包，VIP 用户还可使用 Nano Banana AI 生图和 SVG 图解。请描述如何实现这种权限控制。

**参考答案：**

**1. 权限模型设计**：`ImageMethodEnum` 中定义 `AccessLevel` 枚举：
```java
public enum ImageMethodEnum {
    // 免费用户可用
    PEXELS("pexels", "Pexels 图库", AccessLevel.FREE),
    MERMAID("mermaid", "Mermaid 图表", AccessLevel.FREE),
    ICONIFY("iconify", "Iconify 图标", AccessLevel.FREE),
    MEME("meme", "表情包", AccessLevel.FREE),
    // VIP 用户可用
    NANO_BANANA("nanoBanana", "Nano Banana AI 生图", AccessLevel.VIP),
    SVG_DIAGRAM("svgDiagram", "SVG 图解", AccessLevel.VIP);
}
```

**2. 权限校验**：`ImageAnalyzerAgent` 在选择配图方式时，调用权限校验方法：
```java
public boolean isAccessible(ImageMethodEnum method, User user) {
    if (method.getAccessLevel() == AccessLevel.FREE) {
        return true;  // 免费方式所有用户可用
    }
    return user.getIsVip() && 
           user.getVipExpireTime().isAfter(LocalDateTime.now());  // VIP 且未过期
}
```

**3. 降级处理**：如果 VIP 用户选择的配图方式不可用（如 API 限额用尽），自动降级到免费方式：
```java
ImageMethodEnum actualMethod = isAccessible(method, user) ? method : 
    findFreeFallback(method);  // 降级到免费方式
```

**4. 配额管理**：VIP 用户的每日配额也通过 Redis 管理，`RedissonClient` 的 `RRateLimiter` 控制每日调用次数，扣减配额时使用 Redisson 分布式锁保证原子性。

---

### 第 5 题：SSE 连接异常场景
**场景：** 用户在创作过程中，SSE 连接因网络波动中断。此时正文生成已进行到 60%，配图已生成 2 张。请描述前端和后端如何处理连接中断后的恢复，确保用户不会丢失已生成的内容。

**参考答案：**

**前端处理**：

1. **自动重连**：EventSource 内置自动重连机制，网络恢复后自动重新建立连接。前端通过 `onerror` 事件监听断连状态，显示网络提示。

2. **增量恢复**：连接恢复后，后端会从断点处继续推送未推送的事件。前端已有的内容（已追加的正文、已展示的配图）不做清空，仅处理新接收的事件。

3. **状态持久化**：Pinia Store 中的状态在 SSR 场景下可以做 sessionStorage 备份，刷新后恢复：
```typescript
// 组件卸载前保存状态
onBeforeUnmount(() => {
    sessionStorage.setItem('creation-state', JSON.stringify(store.$state))
})
```

**后端处理**：

1. **SseEmitter 重新创建**：客户端重连时，后端检测到新的 SSE 连接请求，创建新的 `SseEmitter`，替换 `emitterMap` 中旧的实例。

2. **断点续推**：后端根据 `taskId` 获取当前创作状态，判断哪些事件已经推送过，哪些尚未推送。未推送的事件继续通过新的 emitter 推送。

3. **幂等处理**：`IMAGE_COMPLETE` 等事件包含图片 URL，前端收到重复事件时去重（通过 URL 或图片 ID）。

**关键设计**：数据库的持久化状态 + SSE 的事件驱动架构 + Pinia 的客户端状态管理，三层配合确保连接中断不影响创作过程。

---

## 五、深挖题（5 道）

### 第 1 题：StateGraph 的 `node_async()` 和普通 `node()` 在底层实现上有何本质区别？异步节点内部如何保证线程安全的状态访问？

**参考答案：**

**本质区别**：

| 维度 | `node()` | `node_async()` |
|------|----------|----------------|
| 执行线程 | 图引擎的主线程 | 独立线程池 |
| 阻塞行为 | 同步执行，完成前不返回 | 异步执行，提交后立即返回 |
| 线程模型 | 单线程顺序执行 | 多线程并发执行 |
| 适用场景 | 轻量级节点，依赖前置节点 | 耗时操作（LLM 调用、网络请求） |

**线程安全保证**：

1. **状态隔离**：每个异步节点执行时，从 `State` 中读取当前状态的快照（不可变引用），执行结束后将增量结果合并回全局状态。读写分离避免并发修改。

2. **KeyStrategy 合并**：`KeyStrategy` 定义了多个异步节点同时写入同一个 key 时的合并策略。`OverrideStrategy` 使用 `ConcurrentHashMap` 的原子操作保证覆盖的正确性；`AppendStrategy` 使用 `CopyOnWriteArrayList` 或加锁追加。

3. **CompletableFuture 编排**：`node_async()` 内部使用 `CompletableFuture` 编排异步任务，`allOf()` 等待所有并行节点完成后再进入下一阶段。

4. **线程池隔离**：异步节点使用独立的线程池（与图引擎线程池隔离），避免影响主流程的执行。线程池大小通过配置控制，防止资源耗尽。

---

### 第 2 题：策略模式配图系统中，`ImageServiceStrategy` 的 Spring Map 注入（`Map<String, ImageSearchService>`）和`ImageMethodEnum` 枚举之间如何建立映射关系？为什么选择枚举 + Map 的组合而非直接使用 `@Qualifier` 注解注入？

**参考答案：**

**映射关系建立**：

1. `ImageMethodEnum` 中每个枚举值包含 `beanName` 字段，对应 `@Service("xxx")` 的 Bean 名称
2. `ImageServiceStrategy` 构造器注入 `Map<String, ImageSearchService>`，Spring 自动将 Bean 名称作为 key
3. 运行时通过 `getService(ImageMethodEnum method)` 方法：`serviceMap.get(method.getBeanName())` 获取对应策略

```java
public enum ImageMethodEnum {
    PEXELS("pexelsImageSearchService", "Pexels 图库", AccessLevel.FREE),
    MERMAID("mermaidImageSearchService", "Mermaid 图表", AccessLevel.FREE),
    // ...
}

public class ImageServiceStrategy {
    private final Map<String, ImageSearchService> serviceMap;
    
    public ImageSearchService getService(ImageMethodEnum method) {
        return serviceMap.get(method.getBeanName());
    }
}
```

**为什么选择枚举 + Map 而非 `@Qualifier`？**

1. **运行时动态选择**：`@Qualifier` 在编译期确定注入哪个 Bean，但配图方式的选择是运行时由 `ImageAnalyzerAgent` 通过 LLM 分析正文后动态决定的。枚举 + Map 可以在运行时根据枚举值动态查表获取。

2. **降级支持**：当主策略失败时，需要动态切换到降级策略。枚举 + Map 可以轻松实现 `findFallback(method)` 逻辑，`@Qualifier` 无法动态切换。

3. **扩展性**：新增配图方式只需添加枚举值和实现类，`ImageServiceStrategy` 零修改。如果使用 `@Qualifier` 注入，需要在策略类中显式注入每个新策略。

4. **可遍历性**：Map 结构可以遍历所有策略，实现批量操作（如健康检查、状态统计）。`@Qualifier` 注入的 Bean 无法直接遍历。

---

### 第 3 题：项目中 SSE 的 `SseEmitter` 使用 `new SseEmitter(0L)` 设置为永不超时，但生产环境中可能存在大量未关闭的连接。请分析潜在风险，并给出优化的资源管理方案。

**参考答案：**

**潜在风险**：

1. **连接泄漏**：用户关闭浏览器但服务端未及时感知，`SseEmitter` 残留在 `emitterMap` 中，导致连接数持续增长
2. **内存泄漏**：每个 `SseEmitter` 持有输出流和回调对象，未关闭的连接占用堆内存
3. **线程资源**：虽然 `SseEmitter` 释放了 Tomcat 请求线程，但业务线程池中的线程可能因挂起的 emitter 而无法释放
4. **Tomcat 连接数**：Tomcat 的 NIO 连接器有最大连接数限制（默认 10000），连接泄漏最终会耗尽连接池

**优化方案**：

1. **心跳检测**：定时发送心跳事件（如每 30 秒发送 `HEARTBEAT` 事件），如果多次发送失败，主动关闭 emitter：
```java
// 心跳任务
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(() -> {
    emitterMap.forEach((taskId, emitter) -> {
        try {
            emitter.send(SseEmitter.event().name("HEARTBEAT").data("{}"));
        } catch (IOException e) {
            emitter.completeWithError(e);
            emitterMap.remove(taskId);
        }
    });
}, 30, 30, TimeUnit.SECONDS);
```

2. **合理超时**：不为 0，设置合理的超时时间（如 30 分钟），配合心跳机制：
```java
SseEmitter emitter = new SseEmitter(1800000L); // 30 分钟超时
```

3. **`onCompletion` 回调清理**：确保 `onCompletion` 回调中从 `emitterMap` 移除：
```java
emitter.onCompletion(() -> emitterMap.remove(taskId));
emitter.onTimeout(() -> emitterMap.remove(taskId));
emitter.onError(e -> emitterMap.remove(taskId));
```

4. **连接数限制**：配置 Tomcat 连接数和业务线程池大小，结合限流防止资源耗尽：
```yaml
server:
  tomcat:
    max-connections: 500
    max-threads: 200
```

5. **监控告警**：`emitterMap.size()` 作为指标监控，超过阈值告警，自动清理超时连接。

---

### 第 4 题：MyBatis-Flex 的 APT 编译期代码生成和 MyBatis-Plus 的运行时 SQL 解析，两者在架构上的本质差异是什么？这种差异在实际项目中带来了哪些具体影响？

**参考答案：**

**架构本质差异**：

| 维度 | MyBatis-Flex（APT） | MyBatis-Plus（运行时解析） |
|------|---------------------|--------------------------|
| 代码生成时机 | 编译期（javac 阶段） | 运行时（首次加载时） |
| 元数据来源 | 编译期生成的静态类 | 运行时反射获取字段信息 |
| SQL 构建方式 | 直接拼接字符串常量 | 解析 SQL 语句树，动态修改 |
| 错误发现 | 编译期即可发现字段错误 | 运行时才暴露字段错误 |
| 启动速度 | 快（无运行时解析） | 稍慢（首次加载需解析） |

**具体影响**：

1. **性能影响**：MyBatis-Plus 的 `LambdaQueryWrapper` 每次查询都需要解析 Lambda 表达式，提取 `SerializedLambda` 信息，再反射获取字段名。MyBatis-Flex 直接使用编译期生成的 `ACCOUNT.USER_NAME` 常量，零解析开销。在高并发场景下，这种差异会被放大。

2. **开发体验**：MyBatis-Flex 的 IDE 自动补全更好（字段常量是静态变量，IDE 直接提示），MyBatis-Plus 的 Lambda 表达式虽然也支持补全，但需要额外的 `LambdaMeta` 解析。

3. **启动时间**：MyBatis-Plus 在启动时需要扫描所有 Mapper 接口，解析 SQL 语句，生成代理对象。MyBatis-Flex 的 APT 生成在编译期完成，启动时只需加载编译后的 class 文件。

4. **调试便捷性**：MyBatis-Flex 的 SQL 语句在编译期已确定，调试时直接看到完整 SQL；MyBatis-Plus 的 SQL 在运行时动态生成，需要通过日志或拦截器查看最终 SQL。

5. **框架兼容性**：MyBatis-Plus 的拦截器机制可能与其他 MyBatis 插件冲突（如分页插件、数据脱敏插件）。MyBatis-Flex 无拦截器，架构更干净，兼容性更好。

---

### 第 5 题：项目中三阶段人机协作流程的状态机设计，如果从 `TITLE_SELECTION` 阶段用户要求"重新生成"，需要回退到哪个阶段？这种状态回退在数据库层面如何实现？请结合状态机模式分析设计中的权衡。

**参考答案：**

**状态回退分析**：

```
正常流程：TITLE_SELECTION → OUTLINE_EDITING → CONTENT_GENERATION → COMPLETED
回退路径：
  - 在 OUTLINE_EDITING 要求重新生成 → 回到 OUTLINE_EDITING 头部（重新生成大纲）
  - 在 CONTENT_GENERATION 要求重新生成 → 回到 CONTENT_GENERATION 头部（重新生成正文）
  - 在 TITLE_SELECTION 要求重新生成 → 回到 TITLE_SELECTION（重新生成标题）
  - 在 OUTLINE_EDITING 要求重新选题 → 回到 TITLE_SELECTION（重新开始）
```

注意：从 `TITLE_SELECTION` 阶段用户要求"重新生成"，实际上是在同一阶段内重新生成标题，而非回退到更早的阶段（因为 `TITLE_SELECTION` 已经是首个阶段）。

**数据库层面的实现**：

```java
// 更新 phase 字段回退
public void regenerateTitle(Long articleId) {
    Article article = articleMapper.selectOneById(articleId);
    // 清除后续阶段的数据
    article.setTitleOptions(generateNewTitles());  // 重新生成标题
    article.setPhase(ArticlePhase.TITLE_SELECTION);
    article.setOutline(null);    // 清除已有大纲
    article.setContent(null);    // 清除已有正文
    articleMapper.update(article);
}
```

**状态机设计权衡**：

| 设计选择 | 优点 | 缺点 |
|----------|------|------|
| 回退时清除后续数据 | 数据一致性保证，状态明确 | 丢失已生成内容，用户可能不满意 |
| 回退时保留后续数据 | 用户可参考之前的内容 | 数据冗余，状态管理复杂 |
| 允许任意阶段回退 | 灵活性高，用户体验好 | 状态转换逻辑复杂，容易出错 |
| 只允许回退到上一阶段 | 状态机简单，易于维护 | 灵活性受限，用户可能不满意 |

**项目中的权衡**：采用"回退时清除后续数据"策略，但保留回退前的版本作为"历史记录"，用户可随时查看和恢复。这种设计在数据一致性和用户体验之间取得了平衡——状态机保持简单，同时通过历史记录保留了用户的选择权。