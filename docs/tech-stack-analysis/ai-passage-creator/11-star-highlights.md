# ai-passage-creator STAR 亮点

> 本文档精选 ai-passage-creator 项目中 3 个最具代表性的技术亮点，每个亮点按照 Situation（背景）、Task（任务）、Action（行动）、Result（成果）四部分展开，并附带技术深挖方向，适合面试准备、项目复盘和技术分享。

---

## 亮点一：Multi-Agent StateGraph 编排 —— 5 个 Agent 协同创作

### Situation

ai-passage-creator 的核心功能是根据用户输入的一个选题，自动生成一篇完整的文章（含标题、大纲、正文、配图）。传统方案使用单一 LLM 调用，一次性生成全部内容，存在三个问题：1）单次生成内容质量不稳定，长文本容易失去逻辑连贯性；2）标题、大纲、正文、配图需要不同维度的专业能力，单一 LLM 难以兼顾；3）无法支持流式输出和阶段性人工干预，用户无法在生成过程中调整方向。

### Task

设计一套多 Agent 协同工作流，将文章创作拆解为多个专业化阶段，每个阶段由独立的 Agent 负责，通过有向无环图（DAG）编排执行顺序。要求：1）Agent 之间通过共享状态通信，每个 Agent 只关注自己的职责；2）支持并行执行（配图生成可并发）；3）支持流式输出，前端实时展示每个 Agent 的执行进度；4）支持人工介入（标题选择、大纲编辑）。

### Action

基于 Spring AI Alibaba 1.1.0 的 StateGraph 引擎，构建 5 个 Agent 的 DAG 工作流：

**1. StateGraph 定义**：使用 `KeyStrategy` 定义共享状态，通过 `addNode()` 注册节点，`addEdge()` 和 `addConditionalEdge()` 定义执行顺序，`node_async()` 包装 Agent 实现异步执行。

```java
StateGraph graph = new StateGraph(keyStrategyFactory)
    .addNode("title_generator", node_async(titleGeneratorAgent))
    .addNode("outline_generator", node_async(outlineGeneratorAgent))
    .addNode("content_generator", node_async(contentGeneratorAgent))
    .addNode("image_analyzer", node_async(imageAnalyzerAgent))
    .addNode("parallel_image_generator", node_async(parallelImageGenerator))
    .addNode("content_merger", node_async(contentMergerAgent))
    .addEdge(START, "title_generator")
    .addEdge("title_generator", "outline_generator")
    .addEdge("outline_generator", "content_generator")
    .addEdge("content_generator", "image_analyzer")
    .addEdge("image_analyzer", "parallel_image_generator")
    .addEdge("parallel_image_generator", "content_merger")
    .addEdge("content_merger", END);
```

**2. 5 个 Agent 职责分工**：

| Agent | 职责 | 输入 | 输出 |
|-------|------|------|------|
| TitleGeneratorAgent | 根据选题生成 3-5 个标题选项 | 用户选题 | 标题选项列表 |
| OutlineGeneratorAgent | 根据选中标题生成文章大纲 | 标题 | 大纲（流式输出） |
| ContentGeneratorAgent | 根据大纲逐段生成正文 | 大纲 | 正文（流式输出） |
| ImageAnalyzerAgent | 分析正文内容，提取配图需求 | 正文 | 配图需求列表 |
| ParallelImageGenerator | 并发执行多张配图生成 | 配图需求列表 | 配图 URL 列表 |
| ContentMergerAgent | 将配图嵌入正文，合并最终文章 | 正文 + 配图 | 最终文章 |

**3. 并行执行**：`ParallelImageGenerator` 内部使用 `CompletableFuture` 并发执行多个图片生成任务，每张图片的生成策略（Pexels/Mermaid/Iconify 等）由 `ImageSearchService` 策略模式决定。`node_async()` 确保每个 Agent 异步执行，不阻塞主流程。

**4. 状态共享**：`KeyStrategy` 定义了 Agent 间共享状态的 key 和合并策略（覆盖或追加）。每个 Agent 执行后，其输出通过 `Channel.Reducer` 合并到全局状态，后续 Agent 从全局状态中读取所需数据。

**5. 流式事件**：每个 Agent 执行完毕后，通过 `SseEmitter` 推送命名事件（`AGENT1_COMPLETE`、`AGENT2_COMPLETE` 等），前端实时展示执行进度。`ContentGeneratorAgent` 和 `OutlineGeneratorAgent` 还支持流式输出（`AGENT2_STREAMING`、`AGENT3_STREAMING`），逐 Token 推送正文内容。

### Result

- 5 个 Agent 各司其职，文章质量显著提升——标题专业、大纲结构清晰、正文逻辑连贯、配图与内容匹配
- 并行配图生成将整体耗时从 15+ 秒降至 8-10 秒（6 张配图并发）
- 流式输出让用户 2-3 秒内即可看到首段内容，体验接近实时
- 状态共享机制确保 Agent 间数据传递零拷贝，无冗余序列化开销
- 整套流程可扩展——新增 Agent 只需添加节点和边，无需修改已有 Agent

### 技术深挖方向

- **StateGraph 执行模型 vs 传统工作流引擎**：StateGraph 的事件驱动循环与 Activiti/Camunda 的 BPMN 引擎有本质区别——StateGraph 每执行一个节点触发一个事件，状态引擎根据 `Channel.Reducer` 合并增量；BPMN 引擎基于令牌（Token）在流程图中流转。StateGraph 更适合 AI 场景的动态决策，BPMN 更适合结构化审批流程

- **`node_async()` 实现原理**：`node_async()` 将 Agent 包装为 `AsyncNode`，内部使用 `CompletableFuture.supplyAsync()` 提交到线程池执行。`StateGraph.stream()` 流式执行的内部机制是：每执行完一个节点，检查后续边的条件，将符合条件的下一个节点提交到线程池。`ParallelNode` 则将多个节点同时提交到线程池，等待所有节点完成后聚合结果

- **KeyStrategy 状态合并策略**：`KeyStrategy` 定义了每个状态 key 的合并行为——`overwrite`（覆盖，适用于标题、大纲等单值字段）、`appender`（追加，适用于配图 URL 列表等多值字段）、`custom`（自定义合并函数）。合理选择合并策略可以避免状态膨胀（覆盖策略自动丢弃旧值）和竞态条件（自定义合并函数保证线程安全）

- **CompletableFuture 并发控制**：`ParallelImageGenerator` 使用 `CompletableFuture.allOf()` 等待所有图片生成完成，但需要控制并发度防止打爆外部 API。使用 `Executors.newFixedThreadPool(3)` 限制并发数为 3，配合 `Semaphore` 实现更细粒度的限流

---

## 亮点二：六种配图策略 + 降级链 —— 弹性配图生成

### Situation

文章配图是 AI 创作的关键环节，但配图场景极其多样：有的文章需要真实照片（风景、人物），有的需要示意图（架构图、流程图），有的需要图标或表情包点缀。单一配图源无法满足所有场景——Pexels 提供高质量照片但无图标，Mermaid 擅长技术图表但无法生成照片，Iconify 提供图标集但无法生成复杂图片。此外，第三方 API 随时可能不可用（限流、超时、服务故障），需要优雅降级而非直接报错。

### Task

设计一套多策略配图方案，支持 6 种配图方式，每种方式对应一个策略实现。要求：1）根据文章内容自动选择最合适的配图策略；2）当主策略失败时自动降级到备用策略，最终降级到跳过配图；3）支持 VIP 用户使用高级策略（Nano Banana、SVG Diagram）；4）运行时动态扩展，新增策略无需修改调用方代码。

### Action

采用策略模式（Strategy Pattern）实现六种配图策略，通过 `ImageSearchService` 接口统一抽象，`ImageMethodEnum` 枚举标识策略类型，`ImageSearchStrategyRegistry` 管理策略注册和路由。

**1. 策略接口与实现**：

```java
public interface ImageSearchService {
    ImageResult search(ImageRequirement requirement);
    ImageMethodEnum getMethod();
}

// 六种策略实现
@Component
public class PexelsImageSearchService implements ImageSearchService { /* ... */ }

@Component
public class MermaidImageSearchService implements ImageSearchService { /* ... */ }

@Component
public class IconifyImageSearchService implements ImageSearchService { /* ... */ }

@Component
public class MemeImageSearchService implements ImageSearchService { /* ... */ }

@VipOnly
@Component
public class NanoBananaImageSearchService implements ImageSearchService { /* ... */ }

@VipOnly
@Component
public class SvgDiagramImageSearchService implements ImageSearchService { /* ... */ }
```

**2. 六种策略详解**：

| 策略 | 来源 | 适用场景 | 特点 | VIP 限制 |
|------|------|----------|------|----------|
| Pexels | Pexels API（免费图库） | 风景、人物、实物照片 | 高质量真实照片，API 免费 | 否 |
| Mermaid | Mermaid.js 渲染 | 架构图、流程图、时序图 | 文本描述转图表，适合技术文章 | 否 |
| Iconify | Iconify 图标集 | 图标点缀、装饰元素 | 200,000+ 图标，CDN 加速 | 否 |
| 表情包 | 表情包库 | 轻松话题、趣味点缀 | 增加文章趣味性，适合科普文 | 否 |
| Nano Banana | Nano Banana API（VIP） | 高质量 AI 生成图片 | AI 生成图片，质量高但需付费 | 是 |
| SVG Diagram | SVG 渲染引擎（VIP） | 专业 SVG 图表 | 可定制样式，矢量图无损缩放 | 是 |

**3. 降级链设计**：

```
主策略 → Picsum（随机占位图）→ 跳过配图
```

降级逻辑在 `ImageSearchService` 的 `search()` 方法中实现：

```java
public ImageResult search(ImageRequirement requirement) {
    try {
        // 尝试主策略
        return primaryStrategy.search(requirement);
    } catch (Exception e) {
        log.warn("Primary strategy failed, degrading to Picsum: {}", e.getMessage());
        try {
            // 降级到 Picsum 随机占位图
            return picsumStrategy.search(requirement);
        } catch (Exception e2) {
            log.error("Picsum also failed, skipping image: {}", e2.getMessage());
            // 最终降级：跳过配图
            return ImageResult.skipped();
        }
    }
}
```

**4. 策略选择逻辑**：`ImageAnalyzerAgent` 分析正文内容后，为每个配图位置生成 `ImageRequirement`（含场景描述、推荐策略类型）。`ImageSearchStrategyRegistry` 根据推荐策略类型和用户 VIP 等级，选择合适的策略实现。如果推荐策略不可用（VIP 策略对非 VIP 用户），自动降级到免费策略。

**5. 策略注册机制**：`ImageSearchStrategyRegistry` 通过构造器注入 `List<ImageSearchService>`，Spring 自动收集所有 `@Component` 实现，构建 `Map<ImageMethodEnum, ImageSearchService>`。新增策略只需：实现 `ImageSearchService` 接口 → 标注 `@Component` → 添加到 `ImageMethodEnum`，零修改已有代码。

### Result

- 6 种配图策略覆盖文章配图的全部场景（照片/图表/图标/表情包/AI 生成/SVG）
- 降级链确保 99.9% 的配图请求都能成功返回（主策略失败 → Picsum → 跳过，不会抛异常给用户）
- VIP 策略对非 VIP 用户自动降级，无需额外处理逻辑
- 新增策略只需实现接口 + 注册枚举，符合开闭原则
- 并发配图场景下，6 张图片同时生成，整体耗时约 3-5 秒

### 技术深挖方向

- **策略模式 vs 工厂模式的选择**：`ImageSearchStrategyRegistry` 本质上是策略模式 + 注册表模式，而非工厂模式。策略模式强调"算法可互换"，调用方通过注册表获取策略后调用；工厂模式强调"对象创建"，调用方不关心具体实现类。本场景中，调用方（`ImageAnalyzerAgent`）需要根据文章内容选择策略，策略之间可降级替换，策略模式更合适

- **降级链的容错设计**：降级链本质上是"责任链模式"的变体——每个策略尝试执行，失败则传递给下一个策略。但与传统责任链不同，降级链不是"范围检查"而是"容错保护"。关键设计点：1）降级链的终止条件必须是"确定成功"或"全部失败"；2）降级策略（Picsum）必须是 100% 可靠（无需外部 API，使用本地缓存图片库）；3）降级日志需要区分"策略不适用"（正常）和"策略执行异常"（需要告警）

- **VIP 策略控制与 AOP**：`@VipOnly` 注解配合 AOP 切面实现 VIP 策略保护——`@Around("execution(* com.xx.ImageSearchService.search(..)) && @annotation(vipOnly)")` 在方法执行前检查当前用户是否为 VIP。比在策略实现类中硬编码 `if (!isVip) ...` 更优雅，因为：1）VIP 检查与业务逻辑解耦；2）新增 VIP 策略只需标注注解，无需修改已有检查逻辑；3）AOP 切面可以统一处理降级逻辑（非 VIP 用户自动降级到免费策略）

- **Picsum 占位图的定位**：Picsum 是降级链的"保险丝"，不是"主要方案"。它的职责是"确保不出现空白图片位"，而不是"提供好看配图"。因此 Picsum 策略的实现应该最简单——从本地预置的占位图列表中随机选取，不依赖任何外部 API，确保 100% 可用

---

## 亮点三：三阶段人机协作 —— 标题选择 + 大纲编辑 + 内容生成

### Situation

纯 AI 生成的文章往往存在"一锤子买卖"的问题：用户输入选题，AI 输出文章，用户不满意只能重新生成。这种方式浪费 Token、耗时，且无法利用用户的领域知识。实际创作场景中，用户希望在关键节点介入——选择最合适的标题、调整大纲结构、修改正文内容，AI 在用户确认的基础上继续生成。

### Task

设计一套人机协作工作流，将创作过程分为三个阶段，每个阶段用户确认后 AI 继续下一步。要求：1）支持断点续传——用户刷新页面后能恢复当前阶段；2）SSE 实时推送每个阶段的执行状态；3）数据库记录每个阶段的完成状态，支持回溯；4）用户可以在阶段内编辑 AI 生成的内容。

### Action

使用 `ArticlePhase` 枚举定义四个阶段状态，通过 SSE 事件系统实现前端实时交互，后端 StateGraph 根据阶段状态控制执行流程。

**1. 阶段定义**：

```java
public enum ArticlePhase {
    TITLE_SELECTION,   // 阶段一：AI 生成标题，用户选择
    OUTLINE_EDITING,   // 阶段二：AI 生成大纲，用户编辑确认
    CONTENT_GENERATION, // 阶段三：AI 生成正文，用户预览
    COMPLETED          // 完成
}
```

**2. 三阶段工作流**：

```
用户输入选题
    │
    ▼
[TITLE_SELECTION]  AI 生成 3-5 个标题选项
    │                    ↓
    │              用户选择标题 → 进入下一阶段
    │
    ▼
[OUTLINE_EDITING]  AI 生成文章大纲
    │                    ↓
    │              用户编辑大纲 → 确认后进入下一阶段
    │
    ▼
[CONTENT_GENERATION]  AI 逐段生成正文
    │                        ↓
    │                   AI 分析配图需求
    │                        ↓
    │                   AI 并发生成配图
    │                        ↓
    │                   AI 合并配图到正文
    │                        ↓
    │                  用户预览最终文章
    │
    ▼
[COMPLETED]  文章创作完成
```

**3. 数据库持久化**：`article` 表记录每个阶段的状态：

```sql
CREATE TABLE article (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    topic       VARCHAR(500)  NOT NULL COMMENT '选题',
    phase       VARCHAR(50)   NOT NULL DEFAULT 'TITLE_SELECTION' COMMENT '当前阶段',
    title       VARCHAR(200)  NULL COMMENT '选中标题',
    outline     TEXT          NULL COMMENT '大纲内容',
    content     TEXT          NULL COMMENT '正文内容',
    images      JSON          NULL COMMENT '配图信息',
    created_at  DATETIME      NOT NULL,
    updated_at  DATETIME      NOT NULL
);
```

**4. SSE 事件系统**：10 个命名事件覆盖全部阶段和 Agent：

| 事件名称 | 触发时机 | 数据内容 |
|----------|----------|----------|
| AGENT1_COMPLETE | 标题生成完成 | 标题选项列表 |
| AGENT2_STREAMING | 大纲流式输出 | 文本片段 |
| AGENT2_COMPLETE | 大纲生成完成 | 完整大纲 |
| AGENT3_STREAMING | 正文流式输出 | 文本片段 |
| AGENT3_COMPLETE | 正文生成完成 | 完整正文 |
| AGENT4_COMPLETE | 配图需求分析完成 | 配图需求列表 |
| IMAGE_COMPLETE | 单张配图完成 | 图片 URL |
| AGENT5_COMPLETE | 所有配图完成 | 配图 URL 列表 |
| MERGE_COMPLETE | 文章合并完成 | 最终文章 |
| ERROR | 异常发生 | 错误信息 |

**5. SseEmitter 管理**：`SseEmitterManager` 使用 `ConcurrentHashMap` 管理多个连接，支持断点恢复：

```java
public class SseEmitterManager {
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String taskId) {
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        emitterMap.put(taskId, emitter);
        return emitter;
    }

    public void sendEvent(String taskId, String eventName, Object data) {
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        }
    }
}
```

**6. 断点续传**：用户刷新页面后，前端通过 `taskId` 重新连接 SSE。后端查询 `article.phase` 获取当前阶段，从断点处恢复——如果 `phase = OUTLINE_EDITING`，直接返回已生成的大纲，用户继续编辑，不会重新调用 AI。

### Result

- 三阶段人机协作让用户在每个关键节点都有控制权，满意度提升
- 断点续传机制确保用户刷新页面不会丢失进度，体验接近桌面应用
- 10 个 SSE 命名事件，前端精确控制每个阶段的 UI 展示（标题选择弹窗、大纲编辑框、正文预览区）
- 数据库阶段记录支持回溯——用户可以查看历史创作记录，重新进入某个阶段
- `SseEmitter` 不超时配置（`0L`）确保长连接不因空闲断开，适合 AI 创作这种耗时不确定的场景

### 技术深挖方向

- **SseEmitter 资源管理**：`new SseEmitter(0L)` 设置不超时，但需要配套的清理机制——`emitter.onCompletion()` 和 `emitter.onTimeout()` 回调中从 `ConcurrentHashMap` 移除连接。如果缺少清理，长时间运行的 SSE 连接会积累大量未关闭的 `SseEmitter` 实例，导致内存泄漏。生产环境建议配合心跳机制（每 30 秒发送 `:heartbeat` 注释行）检测死连接

- **断点恢复的状态一致性**：断点恢复的挑战在于"状态一致性"——用户可能在上次会话中完成了大纲编辑但数据库未持久化。解决方案：每次用户操作（选择标题、编辑大纲）都同步写入数据库，确保 `article.phase` 和 `article.outline` 等字段与用户操作一致。恢复时查询数据库重建状态，无需依赖内存中的 `SseEmitter` 连接

- **SSE vs WebSocket 在 AI 创作场景的选择**：SSE 的优势在于：1）EventSource API 内置自动重连，无需额外代码；2）服务端单向推送，符合 AI 创作"服务端推送事件"的模型；3）基于 HTTP 协议，穿透防火墙和代理更容易。局限在于：1）只能发送 GET 请求，无法携带自定义请求头；2）浏览器并发连接数限制（HTTP/1.1 下每个域名 6 个）。项目选择 SSE 是合理的，因为 AI 创作场景是"纯推送"，不需要双向通信

- **ArticlePhase 状态机设计**：`ArticlePhase` 枚举的流转方向是单向的（TITLE_SELECTION → OUTLINE_EDITING → CONTENT_GENERATION → COMPLETED），不允许回退。这种设计简化了状态一致性——不需要考虑"回退到上一阶段时，已生成的数据如何处理"。如果未来需要支持"回到大纲重新编辑"，需要引入版本号机制（每次编辑生成新版本，保留历史版本）

- **SSE 事件命名规范**：项目中 10 个事件采用 `AGENT{N}_{ACTION}` 格式（`AGENT1_COMPLETE`、`AGENT2_STREAMING`），命名与 StateGraph 的节点名称一一对应。这种命名方式的优势：前端可以通过事件名称直接推断出当前的 Agent 和执行状态，无需额外解析。前端代码中事件监听器和 Store 的 action 一一对应，降低了前后端对接的心智负担