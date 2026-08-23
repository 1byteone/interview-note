# Vue 3.5 + Ant Design Vue 4.2 + Pinia 3.0 -- 前端架构

> 覆盖：Vue 3.5 Composition API、Ant Design Vue 4.2、Pinia 3.0 状态管理、SSE 前端监听、创作流程页面、组件通信

---

## 一、三个核心概念

### 1.1 Vue 3.5 Composition API

Vue 3.5 是 Vue 3 的最新稳定版本，Composition API 是 Vue 3 引入的声明式逻辑组织方式，通过 `setup()` 函数或 `<script setup>` 语法糖将组件逻辑按功能（而非选项）组织。

**核心 API：**

| API | 作用 | 类比 |
|-----|------|------|
| `ref()` | 创建响应式的基本类型值 | 类似 `this.count` |
| `reactive()` | 创建响应式对象 | 类似 `this.data` |
| `computed()` | 派生状态，依赖变化时自动重新计算 | 类似 Vue 2 的 `computed` 选项 |
| `watch()` | 监听响应式数据变化并执行副作用 | 类似 Vue 2 的 `watch` 选项 |
| `onMounted()` / `onUnmounted()` | 生命周期钩子 | 类似 `mounted` / `destroyed` |
| `provide()` / `inject()` | 跨层级组件通信 | 类似 React Context |
| `defineProps()` / `defineEmits()` | 组件 props 和事件声明（`<script setup>` 中） | 类似 Options API 的 `props` / `emits` |

**`<script setup>` 语法糖** 是 Vue 3.5 推荐写法，相比 Options API：

```vue
<!-- Options API -->
<script>
export default {
  data() { return { count: 0 } },
  methods: { increment() { this.count++ } }
}
</script>

<!-- Composition API + <script setup> -->
<script setup lang="ts">
import { ref } from 'vue'
const count = ref(0)
const increment = () => count.value++
</script>
```

**优势对比**：Composition API 将同一逻辑的 data/methods/computed/watch 汇聚在一起，而不是分散在四个选项中。当组件逻辑复杂时，可以提取为 `useXxx()` 组合函数，实现真正的逻辑复用（Options API 的 mixins 存在命名冲突和来源不明的问题）。

### 1.2 Ant Design Vue 4.2

Ant Design Vue 4.2 是 Ant Design 的 Vue 3 实现，提供企业级 UI 组件库。4.x 版本完全基于 Vue 3 Composition API 重写。

**项目中使用的核心组件：**

| 组件 | 用途 | 关键属性 |
|------|------|----------|
| `a-layout` / `a-sider` / `a-content` | 页面布局骨架 | `collapsed` 控制侧边栏折叠 |
| `a-menu` | 导航菜单 | `v-model:selectedKeys` 双向绑定选中项 |
| `a-form` / `a-form-item` | 表单（创作输入） | `model` 绑定表单数据，`rules` 校验规则 |
| `a-input` / `a-textarea` | 文本输入（标题、大纲编辑） | `v-model` 双向绑定 |
| `a-button` | 操作按钮 | `type="primary"` 主操作，`loading` 加载态 |
| `a-modal` | 弹窗（标题选择、确认） | `v-model:visible` 控制显隐 |
| `a-radio-group` | 标题选择 | `v-model:value` 绑定选中值 |
| `a-progress` | SSE 进度展示 | `percent` 百分比，`status` 状态 |
| `a-spin` | 加载中状态 | `spinning` 控制显隐 |
| `a-collapse` | 可折叠面板（配图区域） | `v-model:activeKey` |
| `a-image` | 图片展示（配图预览） | `preview` 开启预览 |
| `a-tag` | 标签（配图方式标记） | `color` 自定义颜色 |
| `a-message` | 全局提示 | `message.success()` / `message.error()` |
| `a-notification` | 通知提醒 | `notification.open()` |

### 1.3 Pinia 3.0 状态管理

Pinia 是 Vue 的官方状态管理库，3.0 版本完全基于 Vue 3 Composition API 构建，替代 Vuex。

**核心概念：**

| 概念 | 说明 | 类比 Vuex |
|------|------|-----------|
| `defineStore()` | 定义 store | `new Vuex.Store()` |
| `state` | 响应式数据 | `state` |
| `getters` | 派生状态，支持 this 访问 | `getters` |
| `actions` | 业务逻辑（支持异步） | `actions` + `mutations` |
| `useStore()` | 在组件中使用 store | `mapState` / `mapActions` |

**Pinia vs Vuex 核心差异：**

| 维度 | Pinia | Vuex |
|------|-------|------|
| TypeScript 支持 | 原生完整支持，无需额外类型声明 | 需要额外类型声明 |
| 架构 | 无 mutations，state/actions 直接修改 | 需要 mutations、actions 两层 |
| 模块化 | 多个独立 store，模块化自然 | 单一 store 通过 modules 分区 |
| 体积 | 更轻量（约 1KB） | 更大 |
| 开发工具 | DevTools 支持 | DevTools 支持 |
| 组合式 API | 原生支持 | 需额外 map 辅助函数 |
| 服务器端渲染 | 原生支持 | 需额外配置 |

---

## 二、实战：SSE 前端监听

### 2.1 EventSource 连接管理

```typescript
// src/composables/useSseListener.ts
import { ref, onUnmounted } from 'vue'
import type { Ref } from 'vue'

interface SseEventHandlers {
  onAgent1Complete?: (data: TitleOption[]) => void
  onAgent2Streaming?: (data: { text: string }) => void
  onAgent2Complete?: (data: { outline: string }) => void
  onAgent3Streaming?: (data: { text: string }) => void
  onAgent3Complete?: (data: { content: string }) => void
  onAgent4Complete?: (data: { imageRequirements: ImageRequirement[] }) => void
  onImageComplete?: (data: { url: string }) => void
  onAgent5Complete?: (data: { images: string[] }) => void
  onMergeComplete?: (data: { article: Article }) => void
  onError?: (data: { message: string }) => void
}

export function useSseListener(taskId: string, handlers: SseEventHandlers) {
  const eventSource: Ref<EventSource | null> = ref(null)
  const isConnected = ref(false)

  const connect = () => {
    // 关闭已有连接
    eventSource.value?.close()

    // 创建新连接
    eventSource.value = new EventSource(`/api/article/generate/${taskId}`)

    eventSource.value.onopen = () => {
      isConnected.value = true
    }

    // 注册事件监听
    eventSource.value.addEventListener('AGENT1_COMPLETE', (event) => {
      handlers.onAgent1Complete?.(JSON.parse(event.data))
    })

    eventSource.value.addEventListener('AGENT2_STREAMING', (event) => {
      handlers.onAgent2Streaming?.(JSON.parse(event.data))
    })

    eventSource.value.addEventListener('AGENT2_COMPLETE', (event) => {
      handlers.onAgent2Complete?.(JSON.parse(event.data))
    })

    eventSource.value.addEventListener('AGENT3_STREAMING', (event) => {
      handlers.onAgent3Streaming?.(JSON.parse(event.data))
    })

    eventSource.value.addEventListener('AGENT3_COMPLETE', (event) => {
      handlers.onAgent3Complete?.(JSON.parse(event.data))
    })

    eventSource.value.addEventListener('AGENT4_COMPLETE', (event) => {
      handlers.onAgent4Complete?.(JSON.parse(event.data))
    })

    eventSource.value.addEventListener('IMAGE_COMPLETE', (event) => {
      handlers.onImageComplete?.(JSON.parse(event.data))
    })

    eventSource.value.addEventListener('AGENT5_COMPLETE', (event) => {
      handlers.onAgent5Complete?.(JSON.parse(event.data))
    })

    eventSource.value.addEventListener('MERGE_COMPLETE', (event) => {
      handlers.onMergeComplete?.(JSON.parse(event.data))
      eventSource.value?.close()
      isConnected.value = false
    })

    eventSource.value.addEventListener('ERROR', (event) => {
      handlers.onError?.(JSON.parse(event.data))
    })

    // 连接错误自动重连（EventSource 内置机制）
    eventSource.value.onerror = () => {
      isConnected.value = false
      // EventSource 会自动尝试重连
    }
  }

  const disconnect = () => {
    eventSource.value?.close()
    eventSource.value = null
    isConnected.value = false
  }

  // 组件卸载时自动断开
  onUnmounted(() => {
    disconnect()
  })

  return {
    eventSource,
    isConnected,
    connect,
    disconnect
  }
}
```

### 2.2 Pinia Store 管理创作状态

```typescript
// src/stores/creationStore.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { createArticle, startGeneration } from '@/api/creation'

export const useCreationStore = defineStore('creation', () => {
  // ===== State =====
  const topic = ref('')                    // 用户输入的选题
  const taskId = ref('')                   // 当前任务 ID
  const phase = ref<'idle' | 'title' | 'outline' | 'content' | 'complete'>('idle')

  // 标题相关
  const titleOptions = ref<string[]>([])   // AI 生成的标题选项
  const selectedTitle = ref('')            // 用户选择的标题

  // 大纲相关
  const outline = ref('')                  // 大纲内容
  const isOutlineStreaming = ref(false)    // 是否正在流式输出大纲

  // 正文相关
  const content = ref('')                  // 正文内容
  const isContentStreaming = ref(false)    // 是否正在流式输出正文

  // 配图相关
  const images = ref<ImageItem[]>([])      // 配图列表
  const imageRequirements = ref<ImageRequirement[]>([]) // 配图需求

  // 进度
  const progress = ref(0)                  // 整体进度百分比
  const currentAgent = ref('')             // 当前执行 Agent 名称

  // ===== Getters =====
  const isGenerating = computed(() =>
    phase.value === 'title' || phase.value === 'outline' || phase.value === 'content'
  )

  const canSubmit = computed(() =>
    topic.value.trim().length > 0 && !isGenerating.value
  )

  const formattedContent = computed(() => {
    // 将配图嵌入到正文中
    let result = content.value
    images.value.forEach(img => {
      result = result.replace(
        `![${img.placeholder}]`,
        `\n\n![${img.alt}](${img.url})\n\n`
      )
    })
    return result
  })

  // ===== Actions =====
  // 开始创作
  async function startCreation(userTopic: string) {
    topic.value = userTopic
    phase.value = 'title'
    progress.value = 10

    const result = await createArticle({ topic: userTopic })
    taskId.value = result.taskId
    return result.taskId
  }

  // 开始生成（传入 taskId 启动 SSE）
  function startGeneration(taskId: string) {
    return startGeneration(taskId)
  }

  // 选择标题
  function selectTitle(title: string) {
    selectedTitle.value = title
    phase.value = 'outline'
    progress.value = 20
  }

  // 更新大纲（流式追加）
  function appendOutline(text: string) {
    outline.value += text
  }

  // 完成大纲
  function completeOutline() {
    isOutlineStreaming.value = false
    progress.value = 40
  }

  // 更新正文（流式追加）
  function appendContent(text: string) {
    content.value += text
  }

  // 完成正文生成
  function completeContent() {
    isContentStreaming.value = false
    progress.value = 60
  }

  // 添加配图
  function addImage(image: ImageItem) {
    images.value.push(image)
  }

  // 设置配图需求
  function setImageRequirements(requirements: ImageRequirement[]) {
    imageRequirements.value = requirements
  }

  // 完成创作
  function completeCreation() {
    phase.value = 'complete'
    progress.value = 100
    currentAgent.value = ''
  }

  // 重置状态
  function reset() {
    topic.value = ''
    taskId.value = ''
    phase.value = 'idle'
    titleOptions.value = []
    selectedTitle.value = ''
    outline.value = ''
    content.value = ''
    images.value = []
    imageRequirements.value = []
    progress.value = 0
    currentAgent.value = ''
    isOutlineStreaming.value = false
    isContentStreaming.value = false
  }

  // 设置进度
  function setProgress(value: number) {
    progress.value = value
  }

  // 设置当前 Agent
  function setCurrentAgent(agent: string) {
    currentAgent.value = agent
  }

  return {
    // state
    topic, taskId, phase,
    titleOptions, selectedTitle,
    outline, isOutlineStreaming,
    content, isContentStreaming,
    images, imageRequirements,
    progress, currentAgent,
    // getters
    isGenerating, canSubmit, formattedContent,
    // actions
    startCreation, startGeneration,
    selectTitle,
    appendOutline, completeOutline,
    appendContent, completeContent,
    addImage, setImageRequirements,
    completeCreation, reset,
    setProgress, setCurrentAgent
  }
})
```

### 2.3 创作页面组件

```vue
<!-- src/pages/CreationPage.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useCreationStore } from '@/stores/creationStore'
import { useSseListener } from '@/composables/useSseListener'
import TitleSelection from '@/components/TitleSelection.vue'
import OutlineEditor from '@/components/OutlineEditor.vue'
import ContentViewer from '@/components/ContentViewer.vue'
import ProgressBar from '@/components/ProgressBar.vue'

const store = useCreationStore()
const topicInput = ref('')

// 初始化 SSE 监听器
const sseListener = useSseListener(store.taskId, {
  onAgent1Complete: (data) => {
    store.titleOptions = data
    store.setCurrentAgent('标题生成完成')
  },
  onAgent2Streaming: (data) => {
    store.isOutlineStreaming = true
    store.appendOutline(data.text)
    store.setCurrentAgent('大纲生成中...')
  },
  onAgent2Complete: () => {
    store.completeOutline()
    store.setCurrentAgent('大纲生成完成')
  },
  onAgent3Streaming: (data) => {
    store.isContentStreaming = true
    store.appendContent(data.text)
    store.setCurrentAgent('正文生成中...')
  },
  onAgent3Complete: () => {
    store.completeContent()
    store.setCurrentAgent('正文生成完成')
  },
  onAgent4Complete: (data) => {
    store.setImageRequirements(data.imageRequirements)
    store.setCurrentAgent('配图分析完成')
    store.setProgress(70)
  },
  onImageComplete: (data) => {
    store.addImage({ url: data.url, alt: '配图', placeholder: '' })
    store.setProgress(store.progress + 5)
  },
  onAgent5Complete: () => {
    store.setCurrentAgent('配图准备就绪')
    store.setProgress(90)
  },
  onMergeComplete: (data) => {
    store.completeCreation()
  },
  onError: (data) => {
    console.error('SSE Error:', data.message)
  }
})

// 开始创作
async function handleStart() {
  if (!topicInput.value.trim()) return
  const taskId = await store.startCreation(topicInput.value)
  sseListener.connect()
}
</script>

<template>
  <div class="creation-page">
    <!-- 阶段 1: 输入选题 -->
    <a-card v-if="store.phase === 'idle'" title="开始创作">
      <a-textarea
        v-model:value="topicInput"
        placeholder="输入你的文章选题，例如：2025年人工智能发展趋势"
        :rows="4"
      />
      <a-button
        type="primary"
        :disabled="!topicInput.trim()"
        :loading="store.isGenerating"
        @click="handleStart"
        style="margin-top: 16px"
      >
        开始创作
      </a-button>
    </a-card>

    <!-- 进度条 -->
    <ProgressBar
      v-if="store.isGenerating"
      :percent="store.progress"
      :currentAgent="store.currentAgent"
    />

    <!-- 阶段 2: 标题选择 -->
    <TitleSelection
      v-if="store.phase === 'title' && store.titleOptions.length > 0"
      :options="store.titleOptions"
      @select="store.selectTitle"
    />

    <!-- 阶段 3: 大纲编辑 -->
    <OutlineEditor
      v-if="store.phase === 'outline' || store.phase === 'content'"
      :outline="store.outline"
      :streaming="store.isOutlineStreaming"
      @update="store.outline = $event"
    />

    <!-- 阶段 4: 正文展示 -->
    <ContentViewer
      v-if="store.phase === 'content' || store.phase === 'complete'"
      :content="store.formattedContent"
      :images="store.images"
      :streaming="store.isContentStreaming"
    />
  </div>
</template>
```

---

## 三、组件通信模式

### 3.1 父子组件通信

| 模式 | 实现方式 | 适用场景 |
|------|----------|----------|
| Props 向下传 | `defineProps()` | 父组件传递数据给子组件 |
| Emits 向上传 | `defineEmits()` | 子组件事件通知父组件 |
| v-model 双向绑定 | `v-model:xxx` | 表单类组件双向绑定 |
| 插槽 (Slots) | `<slot>` | 布局和模板定制 |

### 3.2 跨层级组件通信

| 模式 | 实现方式 | 适用场景 |
|------|----------|----------|
| Provide / Inject | `provide()` + `inject()` | 祖先组件向后代组件传递数据 |
| Pinia Store | `useCreationStore()` | 全局状态管理（创作流程） |
| Vue Router | `useRoute()` / `useRouter()` | 页面间传递参数 |
| 事件总线 | mitt 库 | 非父子组件通信（不推荐，优先使用 Pinia） |

### 3.3 创作流程中的通信设计

```
CreationPage (页面容器)
  │
  ├─ provide:  taskId, phase
  │
  ├─ TitleSelection.vue ────── emit: select(title) ──→ CreationPage → store.selectTitle()
  │     props: options[]
  │
  ├─ OutlineEditor.vue ─────── v-model:outline ──────→ CreationPage
  │     props: outline, streaming
  │
  ├─ ContentViewer.vue ─────── props: content, images, streaming
  │
  └─ ProgressBar.vue ───────── props: percent, currentAgent
```

---

## 四、面试题

### Q1: Composition API vs Options API，如何选择？

**参考答案：**

| 维度 | Composition API | Options API |
|------|----------------|-------------|
| 逻辑组织 | 按功能组织（同一逻辑的 data/methods 在一起） | 按选项类型组织（data 在一起，methods 在一起） |
| 逻辑复用 | `useXxx()` 组合函数，无命名冲突 | mixins 有命名冲突，来源不明 |
| TypeScript | 天然支持，类型推断好 | 需要额外类型声明 |
| 学习曲线 | 需要理解响应式原理 | 更直观，适合新手 |
| 适用场景 | 复杂组件、大型项目 | 简单组件、小型项目 |

**选择建议**：
- 新项目优先使用 Composition API + `<script setup>`
- 简单展示型组件可继续使用 Options API
- 逻辑复用需求强烈时使用 Composition API 提取组合函数

---

### Q2: Pinia 相比 Vuex 的优势是什么？为什么选择 Pinia？

**参考答案：**

Pinia 相比 Vuex 的核心优势：

1. **更完整的 TypeScript 支持**：Pinia 天然支持 TypeScript，无需额外类型声明。store 中的 state、getters、actions 自动推断类型，IDE 补全体验好。

2. **更轻量的架构**：Pinia 移除了 Vuex 的 mutations，actions 直接修改 state。Vuex 需要 mutations（同步修改 state）和 actions（异步提交 mutations）两层，概念更复杂。

3. **组合式 API 原生支持**：Pinia store 可以使用 `ref()`、`computed()`、`watch()` 等 Composition API，与 Vue 3 的编程模型一致。

4. **模块化更自然**：Pinia 通过多个独立的 `defineStore()` 实现模块化，每个 store 是独立的模块。Vuex 需要使用 `modules` 选项在一个大 store 中分区，命名空间需要额外配置。

5. **体积更小**：Pinia 约 1KB，Vuex 约 10KB。

**项目中的选择**：ai-passage-creator 选择 Pinia 是因为创作流程涉及多个相关联的状态（标题、大纲、正文、配图、进度），Pinia 的组合式 API 让这些状态的管理和逻辑复用更加自然。`useCreationStore` 将所有创作相关状态和操作聚合在一起，组件通过解构获取所需状态。

---

### Q3: 前端如何实现 SSE 流式监听？EventSource 的使用要点和注意事项？

**参考答案：**

**基本实现**：
```typescript
// 1. 创建连接
const eventSource = new EventSource(`/api/article/generate/${taskId}`)

// 2. 注册命名事件
eventSource.addEventListener('AGENT3_STREAMING', (event) => {
  const data = JSON.parse(event.data)
  // 增量处理
})

// 3. 完成时关闭
eventSource.addEventListener('MERGE_COMPLETE', () => {
  eventSource.close()
})
```

**使用要点和注意事项**：

1. **自动重连**：EventSource 内置断线重连机制，连接断开后自动尝试重连。可通过 `eventSource.onerror` 监听重连事件。

2. **命名事件 vs 默认事件**：`addEventListener('EVENT_NAME', handler)` 监听命名事件，`eventSource.onmessage` 监听默认事件。项目中全部使用命名事件，便于区分不同 Agent 的事件。

3. **连接关闭**：服务端完成推送后，前端必须在 `MERGE_COMPLETE` 或其他终止事件中主动 `eventSource.close()`，否则连接会一直保持。

4. **HTTP 限制**：EventSource 只能发送 GET 请求，不支持自定义请求头。如果需要传递 Token 等认证信息，可以通过 URL 参数传递（`EventSource('/api/stream?token=xxx')`），或先通过 POST 获取 taskId 再建立 SSE 连接。

5. **浏览器兼容性**：EventSource 在主流浏览器中支持良好，但不支持 IE。如果需要兼容旧浏览器，可使用 `fetch` + `ReadableStream` 自行实现。

6. **内存管理**：组件卸载时必须关闭 EventSource 连接，否则会造成内存泄漏。建议在 `onUnmounted` 或 `beforeUnmount` 生命周期中调用 `eventSource.close()`。

7. **数据解析**：`event.data` 是字符串，需使用 `JSON.parse()` 解析为对象。注意处理解析异常（catch 包裹）。

**SSE vs WebSocket 选择**：项目选择 SSE 而非 WebSocket，因为 AI 创作流程是典型的"服务端推送"场景（前端只需接收事件，无需发送数据），SSE 的 EventSource API 更轻量，内置自动重连，无需额外库。如果后续需要双向通信（如实时编辑协作），可以考虑迁移到 WebSocket。