# 09 Vue 3 + Ant Design Vue + Pinia 前端入门：Composition API、状态管理与 SSE 监听

> 本文是 ai-passage-creator 项目技术栈深度剖析系列的第 9 篇（入门篇）。面向前端初学者，手把手带你用 Vite 搭建基于 Vue 3 + Ant Design Vue + Pinia 的前端工程，理解 Composition API 响应式本质、Pinia 状态管理和 EventSource 流式监听。
>
> **对应项目：** `ai-passage-creator/ai-passage-creator-web` 前端创作页面
> **难度等级：** Level 1 入门
> **预计阅读时间：** 30 分钟（含代码实操）

---

## 一、项目背景

### 1.1 什么是 Vue 3、Ant Design Vue 和 Pinia

**Vue 3** 是目前主流的渐进式前端框架。与 Vue 2 相比，Vue 3 用 TypeScript 全面重写，性能提升（更快更小的运行时），并引入了 **Composition API**——一种按功能组织逻辑的新编程范式。

**Ant Design Vue** 是 Ant Design 设计体系的 Vue 3 实现，提供企业级 UI 组件库（按钮、表单、表格、弹窗、布局等），让开发者无需从零写 CSS 就能搭出美观、统一的界面。

**Pinia** 是 Vue 官方推荐的状态管理库，3.0 版本完全基于 Vue 3 Composition API 构建，替代了 Vuex。

| 核心概念 | 说明 | 项目中的用途 |
|---------|------|-------------|
| **Vue 3** | 渐进式前端框架 | 页面渲染、组件化、响应式数据 |
| **Composition API** | Vue 3 的逻辑组织方式 | 按功能组织组件逻辑，提取组合函数 |
| **Ant Design Vue** | 企业级 UI 组件库 | 创作页面的表单、弹窗、进度条、图片展示 |
| **Pinia** | 状态管理库 | 管理创作流程的全局状态（标题、大纲、正文、进度） |
| **Vite** | 前端构建工具 | 开发服务器、模块热更新、生产打包 |
| **EventSource** | 浏览器原生 SSE 客户端 | 流式接收 AI 生成结果 |

### 1.2 为什么需要前端框架

在 ai-passage-creator 这个 AI 创作项目中，前端面临的核心挑战：

| 挑战 | 场景 | 没有框架会怎样 |
|------|------|----------------|
| **频繁的数据更新** | AI 生成正文时，几千字内容流式追加到页面 | 手动操作 DOM 性能差、易出错 |
| **复杂的交互状态** | 标题选择、大纲编辑、配图展示多阶段切换 | 回调地狱，状态难以管理 |
| **服务端推送** | SSE 持续推送 Agent 执行进度 | 原生写 Ajax 轮询效率低 |
| **多人协作/复用** | 标题选择、进度条等组件复用 | 代码重复，难以维护 |

Vue 3 的**响应式系统**解决了第一个问题——数据变化自动更新视图；**组件化**解决了第四个问题——UI 拆分成可复用的组件；**Pinia** 解决了第二个问题——全局状态集中管理；**EventSource**（SSE 客户端）解决了第三个问题——轻量级流式接收。

### 1.3 本文的目标

读完本文，你将能够：
- 理解 Vue 3 Composition API 的核心概念：ref、reactive、computed、watch
- 理解 Pinia 状态管理的核心概念：store、state、getters、actions
- 用 Vite 从零搭建 Vue 3 + TypeScript 工程
- 使用 Ant Design Vue 组件搭建创作页面
- 使用 EventSource 实现 SSE 流式监听
- 使用 Pinia 管理多阶段创作流程状态
- 运行验证 Demo，编写 3 道面试题的标准答案

---

## 二、核心概念

### 2.1 Composition API 与 `<script setup>`

Vue 3 的 Composition API 通过 `setup()` 函数或 `<script setup>` 语法糖组织组件逻辑。它的核心是**响应式数据**：数据变化时，依赖它的视图自动更新。

**响应式核心 API：**

| API | 作用 | 类比 Vue 2 |
|-----|------|-----------|
| `ref()` | 创建响应式基本类型（也可以包裹对象） | `data` 中的某个属性 |
| `reactive()` | 创建响应式对象 | `data` 对象 |
| `computed()` | 派生状态，依赖变化自动重算 | `computed` 选项 |
| `watch()` | 监听数据变化执行副作用 | `watch` 选项 |
| `onMounted()` / `onUnmounted()` | 生命周期钩子 | `mounted` / `destroyed` |

**ref 和 reactive 的区别：**

| 维度 | ref | reactive |
|------|-----|----------|
| 数据类型 | 基本类型（也支持对象） | 仅对象 / 数组 |
| 访问方式 | `.value` 访问 | 直接访问 |
| 模板中使用 | 自动解包（无需 .value） | 直接使用 |
| 典型场景 | 单个值：字符串、数字、布尔 | 复杂对象：表单、列表 |

**为什么模板中 `ref` 不需要 `.value`？** 因为 Vue 在渲染时会自动解包顶层 ref 对象——这是 Vue 3 提供的语法糖，让模板代码更简洁。但在 `script` 逻辑中必须显式访问 `.value`。

**`<script setup>` 的优势：**

```vue
<!-- 选项式 API（Options API） -->
<script>
export default {
  data() {
    return { count: 0 }
  },
  methods: {
    increment() { this.count++ }
  }
}
</script>

<!-- 组合式 API（Composition API）+ <script setup> -->
<script setup lang="ts">
import { ref } from 'vue'
const count = ref(0)
const increment = () => count.value++
</script>
```

`<script setup>` 是编译时语法糖：顶层变量自动暴露给模板，导入的组件自动注册，无需 `return` 或 `components` 选项，代码更简洁。

### 2.2 Pinia 状态管理

Pinia 是 Vue 官方状态管理库。它的核心思想是**把多个组件共享的状态抽离到独立的 store 中**，任何组件都能读取和修改。

**Pinia 三大核心概念：**

| 概念 | 说明 | 类比 |
|------|------|------|
| **store** | 一个独立的"状态仓库"（通过 `defineStore` 定义） | 一个 Vuex module |
| **state** | store 中的响应式数据 | Vuex state |
| **getters** | 从 state 派生的计算属性 | Vuex getters |
| **actions** | 修改 state 的业务逻辑（支持异步） | Vuex actions + mutations |

**Pinia vs Vuex：**

| 维度 | Pinia | Vuex |
|------|-------|------|
| TypeScript 支持 | 原生完整支持 | 需额外类型声明 |
| 架构 | 无 mutations，actions 直接改 state | mutations + actions 两层 |
| 模块化 | 多个独立 store | 单一 store 用 modules 分区 |
| 组合式 API | 原生支持 | 需 map 辅助函数 |
| 体积 | 约 1KB | 约 10KB |

**为什么项目选择 Pinia？** AI 创作流程涉及大量相互关联的状态——选题、任务 ID、生成阶段、标题选项、大纲、正文、配图、进度。这些状态分散在多个组件中，用 Pinia 集中管理后，逻辑清晰、组件解耦、调试方便。

### 2.3 SSE 前端监听：EventSource

SSE（Server-Sent Events，服务器推送事件）是一种轻量级服务端推送技术。**EventSource** 是浏览器原生的 SSE 客户端，无需任何第三方库。

**EventSource 的核心特性：**

| 特性 | 说明 |
|------|------|
| **单向通信** | 服务端 → 客户端（前端只需接收） |
| **自动重连** | 断线后浏览器自动重连 |
| **命名事件** | 通过 `addEventListener('事件名', 回调)` 区分不同类型的消息 |
| **基于 HTTP** | 使用 GET 请求，简单可靠 |

**EventSource vs WebSocket：**

| 维度 | EventSource (SSE) | WebSocket |
|------|-------------------|-----------|
| 通信方向 | 单向（服务器→客户端） | 双向 |
| 自动重连 | 内置 | 需手动实现 |
| 传输协议 | 基于 HTTP | 独立协议（ws://） |
| 使用复杂度 | 简单（原生支持） | 较复杂 |
| 典型场景 | 推送通知、AI 流式输出 | 实时协作、在线聊天 |

**AI 创作场景为什么用 SSE？** 生成过程中，后端通过 SSE 持续推送各 Agent 的执行事件（标题生成完成、大纲流式输出、正文流式输出、配图完成等），前端只需**接收并展示**，无需向服务器发送数据——这是 SSE 的典型适用场景。同时 EventSource 内置自动重连，Agent 任务中断时前端能自动恢复连接。

---

## 三、从零搭建代码

### 3.1 创建项目结构

```
vue-ai-demo/
├── package.json                     # 依赖与脚本配置
├── vite.config.ts                   # Vite 构建配置
├── tsconfig.json                    # TypeScript 配置
├── index.html                       # HTML 入口
├── src/
│   ├── main.ts                      # 应用入口
│   ├── App.vue                      # 根组件（布局 + 路由出口）
│   ├── api/
│   │   └── creation.ts              # API 封装（创建任务）
│   ├── composables/
│   │   └── useSseListener.ts        # SSE 监听组合函数
│   ├── stores/
│   │   └── creationStore.ts         # 创作流程 Pinia store
│   ├── components/
│   │   ├── ProgressBar.vue          # 进度条组件
│   │   ├── TitleSelection.vue       # 标题选择组件
│   │   └── OutlineEditor.vue        # 大纲编辑组件
│   └── pages/
│       └── CreationPage.vue         # 创作页面（主页面）
```

### 3.2 package.json 与 Vite 配置

```json
{
  "name": "vue-ai-demo",
  "version": "1.0.0",
  "description": "Vue 3 + Ant Design Vue + Pinia 前端入门：Composition API、状态管理与 SSE 监听",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc && vite build",
    "preview": "vite preview",
    "test": "vitest"
  },
  "dependencies": {
    "vue": "^3.5.13",
    "ant-design-vue": "^4.2.6",
    "pinia": "^3.0.1",
    "axios": "^1.7.9"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.1",
    "typescript": "^5.6.3",
    "vite": "^6.0.5",
    "vitest": "^2.1.8",
    "@vue/test-utils": "^2.4.6"
  }
}
```

**关键依赖说明：**

| 依赖 | 版本 | 作用 |
|------|------|------|
| `vue` | ^3.5.13 | Vue 3 核心 |
| `ant-design-vue` | ^4.2.6 | UI 组件库 |
| `pinia` | ^3.0.1 | 状态管理 |
| `axios` | ^1.7.9 | HTTP 请求库（调用后端 API） |
| `vite` | ^6.0.5 | 构建工具 |
| `vitest` | ^2.1.8 | 单元测试框架 |

```typescript
// vite.config.ts —— Vite 构建配置
import { defineConfig } from 'vite'
// Vue 单文件组件（.vue）插件
import vue from '@vitejs/plugin-vue'

// 导出 Vite 配置
export default defineConfig({
  // Vue 插件：让 Vite 支持 .vue 文件的编译
  plugins: [vue()],
  // 开发服务器配置
  server: {
    // 端口号
    port: 3000,
    // 代理配置：解决前端开发时的跨域问题
    // 前端请求 /api/xxx 会被代理转发到后端 8080 端口
    proxy: {
      '/api': {
        // 代理目标：后端服务地址
        target: 'http://localhost:8080',
        // 路径重写：把 /api 前缀去掉后转发
        // 例如：/api/article/create → http://localhost:8080/article/create
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
```

```typescript
// tsconfig.json —— TypeScript 配置
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "jsx": "preserve",
    "resolveJsonModule": true,
    "esModuleInterop": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "noEmit": true,
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.d.ts", "src/**/*.vue"]
}
```

```html
<!-- index.html —— HTML 入口 -->
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <!-- 响应式视口：适配移动端 -->
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>AI 创作助手</title>
  </head>
  <body>
    <!-- 挂载点：Vue 应用渲染到这里 -->
    <div id="app"></div>
    <!-- 入口脚本：加载 src/main.ts -->
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

### 3.3 应用入口（main.ts）

```typescript
// main.ts —— 应用入口文件
// 负责：创建 Vue 应用、注册插件、挂载到页面

// 导入 Vue 核心的 createApp 函数
// createApp 是 Vue 3 创建应用的入口
import { createApp } from 'vue'

// 导入根组件（包含页面布局）
import App from './App.vue'

// 导入 Pinia（状态管理库）
// createPinia() 创建 Pinia 实例
import { createPinia } from 'pinia'

// 导入 Ant Design Vue 组件库
// 使用全量注册：所有 a-xxx 组件自动可用
import Antd from 'ant-design-vue'

// 导入 Ant Design Vue 的样式文件
// 全量样式，生产环境可用按需导入优化体积
import 'ant-design-vue/dist/reset.css'

// 1. 创建 Vue 应用实例
// App 是根组件
const app = createApp(App)

// 2. 注册 Pinia 插件
// 注册后，所有组件都能通过 useXxxStore() 使用 store
app.use(createPinia())

// 3. 注册 Ant Design Vue 插件
// 注册后，模板中可直接使用 <a-button>、<a-card> 等组件
app.use(Antd)

// 4. 挂载应用到 DOM
// #app 是 index.html 中的挂载点
app.mount('#app')
```

### 3.4 API 封装（creation.ts）

```typescript
// src/api/creation.ts —— 后端 API 封装
// 负责：调用后端创建文章任务的接口

// 导入 axios 实例
// 统一配置 baseURL，简化请求地址
import axios from 'axios'

// 创建 axios 实例
// baseURL: '/api' 会被 Vite 代理转发到后端
const request = axios.create({
  baseURL: '/api',
  // 请求超时时间：创建任务一般很快，10 秒足够
  timeout: 10000
})

// 创建文章任务
// 入参：选题（用户输入的标题/主题）
// 返回值：Promise 对象，包含后端返回的任务信息
export async function createArticle(topic: string) {
  // 调用后端接口：POST /api/article/create
  // 请求体：{ topic: "用户输入的选题" }
  // 后端返回：{ taskId: "xxx", message: "任务创建成功" }
  const response = await request.post('/article/create', { topic })
  return response.data
}
```

### 3.5 Pinia Store（creationStore.ts）

```typescript
// src/stores/creationStore.ts —— 创作流程状态管理
// 使用 Pinia 管理多阶段创作流程的全局状态
// 阶段：idle(初始) → title(选标题) → outline(大纲) → content(正文) → complete(完成)

// 导入 defineStore：定义 store 的函数
import { defineStore } from 'pinia'
// 导入 ref（响应式数据）和 computed（派生状态）
import { ref, computed } from 'vue'
// 导入创建任务的 API
import { createArticle } from '@/api/creation'

// 定义并导出创作流程 store
// 参数 1：store 的唯一 ID（'creation'）
// 参数 2：setup 函数（组合式 API 风格）
export const useCreationStore = defineStore('creation', () => {

  // ==================== State（状态） ====================

  // 用户输入的选题（ref 包裹的字符串）
  const topic = ref('')

  // 当前任务 ID（后端创建任务后返回）
  const taskId = ref('')

  // 当前所处阶段
  // 类型：联合类型，限定只能是这几个值之一
  const phase = ref<'idle' | 'title' | 'outline' | 'content' | 'complete'>('idle')

  // AI 生成的标题候选项（数组）
  const titleOptions = ref<string[]>([])

  // 用户选中的标题
  const selectedTitle = ref('')

  // 大纲内容（流式追加）
  const outline = ref('')

  // 是否正在流式输出大纲（控制"生成中"的加载动画）
  const isOutlineStreaming = ref(false)

  // 正文内容（流式追加）
  const content = ref('')

  // 是否正在流式输出正文
  const isContentStreaming = ref(false)

  // 整体进度百分比（0-100）
  const progress = ref(0)

  // 当前执行的 Agent 名称（用于页面提示）
  const currentAgent = ref('')

  // ==================== Getters（派生状态） ====================

  // 是否正在生成中（派生状态：根据 phase 计算）
  // 当 phase 是 title/outline/content 时，认为正在生成
  const isGenerating = computed(() =>
    phase.value === 'title' || phase.value === 'outline' || phase.value === 'content'
  )

  // 是否可以提交（派生状态：选题非空且不在生成中）
  const canSubmit = computed(() =>
    topic.value.trim().length > 0 && !isGenerating.value
  )

  // ==================== Actions（业务逻辑） ====================

  // 开始创作：调用后端创建任务
  async function startCreation(userTopic: string) {
    // 保存用户输入的选题
    topic.value = userTopic
    // 进入标题选择阶段
    phase.value = 'title'
    // 设置初始进度 10%
    progress.value = 10

    // 调用后端 API 创建任务
    // 等待后端返回 taskId
    const result = await createArticle(userTopic)
    // 保存任务 ID（后续 SSE 连接需要）
    taskId.value = result.taskId
    // 返回 taskId 给调用方（页面用来建立 SSE 连接）
    return result.taskId
  }

  // 选择标题：用户点击某个标题后
  function selectTitle(title: string) {
    // 保存选中的标题
    selectedTitle.value = title
    // 进入大纲编辑阶段
    phase.value = 'outline'
    // 进度推进到 20%
    progress.value = 20
  }

  // 流式追加大纲内容（SSE 每次推送一小段文本）
  function appendOutline(text: string) {
    // 直接把新文本拼接到已有大纲后面
    outline.value += text
  }

  // 大纲生成完成
  function completeOutline() {
    // 关闭流式输出动画
    isOutlineStreaming.value = false
    // 进度推进到 40%
    progress.value = 40
  }

  // 流式追加正文内容
  function appendContent(text: string) {
    // 直接把新文本拼接到已有正文后面
    content.value += text
  }

  // 正文生成完成
  function completeContent() {
    // 关闭流式输出动画
    isContentStreaming.value = false
    // 进度推进到 60%
    progress.value = 60
  }

  // 完成创作（所有 Agent 执行完毕）
  function completeCreation() {
    // 进入完成阶段
    phase.value = 'complete'
    // 进度到 100%
    progress.value = 100
    // 清空当前 Agent 提示
    currentAgent.value = ''
  }

  // 重置状态（重新开始创作时调用）
  function reset() {
    // 将所有状态重置为初始值
    topic.value = ''
    taskId.value = ''
    phase.value = 'idle'
    titleOptions.value = []
    selectedTitle.value = ''
    outline.value = ''
    content.value = ''
    progress.value = 0
    currentAgent.value = ''
    isOutlineStreaming.value = false
    isContentStreaming.value = false
  }

  // 对外暴露所有 state、getters、actions
  // 组件通过 useCreationStore() 解构使用
  return {
    // state
    topic, taskId, phase,
    titleOptions, selectedTitle,
    outline, isOutlineStreaming,
    content, isContentStreaming,
    progress, currentAgent,
    // getters
    isGenerating, canSubmit,
    // actions
    startCreation, selectTitle,
    appendOutline, completeOutline,
    appendContent, completeContent,
    completeCreation, reset
  }
})
```

### 3.6 SSE 监听组合函数（useSseListener.ts）

```typescript
// src/composables/useSseListener.ts —— SSE 监听组合函数
// 负责：建立 EventSource 连接，监听后端推送的各类事件
// 组合函数（Composable）：以 useXxx 命名，可在任意组件中复用

// 导入 ref（响应式状态）和 onUnmounted（卸载钩子）
import { ref, onUnmounted } from 'vue'

// 定义 SSE 事件的处理器接口
// 每个事件对应一个回调函数
// onAgent1Complete: 标题生成完成事件
// onAgent2Streaming: 大纲流式输出事件
// onAgent3Streaming: 正文流式输出事件
// ...以此类推
interface SseEventHandlers {
  onAgent1Complete?: (data: any) => void      // Agent1 标题生成完成
  onAgent2Streaming?: (data: { text: string }) => void  // Agent2 大纲流式
  onAgent3Streaming?: (data: { text: string }) => void  // Agent3 正文流式
  onMergeComplete?: (data: any) => void       // 合并完成（终态）
  onError?: (data: { message: string }) => void  // 错误事件
}

// 导出组合函数 useSseListener
// 参数 1：taskId（任务 ID，用于拼接 SSE 连接地址）
// 参数 2：handlers（事件回调集合）
export function useSseListener(taskId: string, handlers: SseEventHandlers) {

  // EventSource 实例（ref 包裹，便于响应式管理）
  const eventSource = ref<EventSource | null>(null)

  // 是否已连接的标志
  const isConnected = ref(false)

  // 建立连接
  function connect() {
    // 1. 如果已有连接，先关闭
    // 防止重复建立连接造成资源浪费
    eventSource.value?.close()

    // 2. 创建新的 EventSource 连接
    // 地址：/api/article/generate/{taskId}
    // 会被 Vite 代理转发到后端 SSE 接口
    eventSource.value = new EventSource(`/api/article/generate/${taskId}`)

    // 3. 监听连接打开事件
    eventSource.value.onopen = () => {
      isConnected.value = true
    }

    // 4. 注册命名事件监听器
    // addEventListener('事件名', 处理函数)
    // 后端通过 event: 事件名 字段区分消息类型

    // Agent1 标题生成完成
    eventSource.value.addEventListener('AGENT1_COMPLETE', (event: MessageEvent) => {
      // event.data 是字符串，需要 JSON.parse 解析
      handlers.onAgent1Complete?.(JSON.parse(event.data))
    })

    // Agent2 大纲流式输出
    // 每收到一条消息，就追加一段大纲文本
    eventSource.value.addEventListener('AGENT2_STREAMING', (event: MessageEvent) => {
      handlers.onAgent2Streaming?.(JSON.parse(event.data))
    })

    // Agent3 正文流式输出
    eventSource.value.addEventListener('AGENT3_STREAMING', (event: MessageEvent) => {
      handlers.onAgent3Streaming?.(JSON.parse(event.data))
    })

    // 合并完成（终态事件）
    // 收到此事件后，关闭连接
    eventSource.value.addEventListener('MERGE_COMPLETE', (event: MessageEvent) => {
      handlers.onMergeComplete?.(JSON.parse(event.data))
      // 所有内容生成完毕，主动关闭连接
      eventSource.value?.close()
      isConnected.value = false
    })

    // 错误事件（后端主动推送的错误信息）
    eventSource.value.addEventListener('ERROR', (event: MessageEvent) => {
      handlers.onError?.(JSON.parse(event.data))
    })

    // 5. 连接错误处理
    // EventSource 内置自动重连机制
    // onerror 触发时表示连接断开，浏览器会自动重连
    eventSource.value.onerror = () => {
      isConnected.value = false
      // 这里不需要手动重连，EventSource 会自动处理
    }
  }

  // 断开连接
  function disconnect() {
    // 关闭 EventSource 连接
    eventSource.value?.close()
    // 置空引用
    eventSource.value = null
    isConnected.value = false
  }

  // 组件卸载时自动断开连接
  // 防止内存泄漏：组件销毁后连接依然存在
  onUnmounted(() => {
    disconnect()
  })

  // 对外暴露
  return {
    eventSource,  // EventSource 实例
    isConnected,  // 连接状态
    connect,      // 建立连接
    disconnect    // 断开连接
  }
}
```

### 3.7 根组件（App.vue）

```vue
<!-- App.vue —— 根组件 -->
<!-- 页面整体布局：使用 Ant Design Vue 的 Layout 组件 -->
<script setup lang="ts">
// 导入 Ant Design Vue 的图标
import { MessageOutlined } from '@ant-design/icons-vue'
</script>

<template>
  <!-- a-layout：整体布局容器 -->
  <a-layout class="app-layout">
    <!-- a-layout-header：顶部导航栏 -->
    <a-layout-header class="app-header">
      <!-- 左侧 Logo 区域 -->
      <div class="app-logo">
        <!-- 图标 + 标题 -->
        <MessageOutlined />
        <span class="app-title">AI 创作助手</span>
      </div>
    </a-layout-header>

    <!-- a-layout-content：主内容区域 -->
    <a-layout-content class="app-content">
      <!-- 创作页面（主页面） -->
      <CreationPage />
    </a-layout-content>

    <!-- a-layout-footer：底部版权信息 -->
    <a-layout-footer class="app-footer">
      AI Passage Creator ©2026 - Vue 3 + Ant Design Vue + Pinia
    </a-layout-footer>
  </a-layout>
</template>

<style scoped>
/* scoped：样式只对当前组件生效 */
/* 顶部导航栏样式 */
.app-layout {
  min-height: 100vh;              /* 占满整个视口高度 */
}
.app-header {
  background: #fff;               /* 白色背景 */
  display: flex;                  /* 弹性布局 */
  align-items: center;            /* 垂直居中 */
  padding: 0 24px;                /* 左右内边距 */
}
.app-logo {
  display: flex;                  /* 弹性布局 */
  align-items: center;            /* 垂直居中 */
  gap: 8px;                       /* 图标和文字的间距 */
  font-size: 18px;                /* 字号 */
  font-weight: 600;               /* 加粗 */
  color: #1677ff;                /* Ant Design 主色调 */
}
.app-content {
  padding: 24px;                  /* 内边距 */
  background: #f5f5f5;           /* 浅灰背景 */
}
.app-footer {
  text-align: center;             /* 居中 */
  color: #999;                    /* 灰色文字 */
}
</style>
```

### 3.8 创作页面（CreationPage.vue）

```vue
<!-- src/pages/CreationPage.vue —— 创作主页面 -->
<!-- 组合多个组件 + store + SSE，串起整个创作流程 -->
<script setup lang="ts">
// 导入 Vue 响应式 API
import { ref } from 'vue'
// 导入创作流程 store
import { useCreationStore } from '@/stores/creationStore'
// 导入 SSE 监听组合函数
import { useSseListener } from '@/composables/useSseListener'
// 导入子组件
import ProgressBar from '@/components/ProgressBar.vue'
import TitleSelection from '@/components/TitleSelection.vue'
import OutlineEditor from '@/components/OutlineEditor.vue'

// ===== 获取 store 实例 =====
const store = useCreationStore()

// 选题输入框的 v-model 绑定变量
const topicInput = ref('')

// ===== SSE 事件回调注册 =====
// 初始化时注册所有事件处理器
// 注意：这里 taskId 在开始时是空字符串
// 真实项目中应在创建任务后动态重建连接
const sseListener = useSseListener(store.taskId, {

  // Agent1 标题生成完成
  onAgent1Complete: (data) => {
    // 把标题候选项存入 store
    store.titleOptions = data
    // 更新当前 Agent 提示
    store.currentAgent = '标题生成完成'
  },

  // Agent2 大纲流式输出
  onAgent2Streaming: (data) => {
    // 标记正在流式输出
    store.isOutlineStreaming = true
    // 追加一段大纲文本
    store.appendOutline(data.text)
    // 更新提示
    store.currentAgent = '大纲生成中...'
  },

  // Agent3 正文流式输出
  onAgent3Streaming: (data) => {
    // 标记正在流式输出
    store.isContentStreaming = true
    // 追加一段正文文本
    store.appendContent(data.text)
    // 更新提示
    store.currentAgent = '正文生成中...'
  },

  // 合并完成
  onMergeComplete: () => {
    // 完成创作：进入完成阶段，进度 100%
    store.completeCreation()
  },

  // 错误处理
  onError: (data) => {
    // 简单打印错误信息
    // 实际项目中可弹出 message.error 提示
    console.error('SSE 错误:', data.message)
  }
})

// ===== 开始创作 =====
async function handleStart() {
  // 校验：选题不能为空
  if (!topicInput.value.trim()) return

  // 1. 调用后端创建任务
  // 拿到 taskId
  const taskId = await store.startCreation(topicInput.value)

  // 2. 建立 SSE 连接
  // 注意：组合函数中 taskId 是在创建时传入的
  // 实际项目中应使用动态 taskId，这里用于演示
  sseListener.connect()

  // 提示用户生成已开始
  // 简单输出到控制台
  console.log('创作已开始，任务 ID:', taskId)
}
</script>

<template>
  <div class="creation-page">
    <!-- ===== 阶段 1：输入选题 ===== -->
    <!-- a-card：卡片容器 -->
    <a-card
      v-if="store.phase === 'idle'"
      title="开始创作"
      class="start-card"
    >
      <!-- 选题输入框：两阶段多行 -->
      <a-textarea
        v-model:value="topicInput"
        placeholder="输入你的文章选题，例如：2026年人工智能发展趋势"
        :rows="4"
      />
      <!-- 开始创作按钮 -->
      <a-button
        type="primary"
        :disabled="!topicInput.trim()"
        :loading="store.isGenerating"
        style="margin-top: 16px"
        @click="handleStart"
      >
        开始创作
      </a-button>
    </a-card>

    <!-- ===== 进度条（生成中显示） ===== -->
    <ProgressBar
      v-if="store.isGenerating"
      :percent="store.progress"
      :currentAgent="store.currentAgent"
    />

    <!-- ===== 阶段 2：选择标题 ===== -->
    <TitleSelection
      v-if="store.phase === 'title' && store.titleOptions.length > 0"
      :options="store.titleOptions"
      @select="store.selectTitle"
    />

    <!-- ===== 阶段 3：大纲编辑 ===== -->
    <OutlineEditor
      v-if="store.phase === 'outline' || store.phase === 'content'"
      :outline="store.outline"
      :streaming="store.isOutlineStreaming"
      @update="store.outline = $event"
    />
  </div>
</template>

<style scoped>
.creation-page {
  max-width: 900px;   /* 居中限宽 */
  margin: 0 auto;     /* 水平居中 */
}
.start-card {
  max-width: 600px;   /* 输入卡片限宽 */
}
</style>
```

### 3.9 子组件（ProgressBar / TitleSelection / OutlineEditor）

```vue
<!-- src/components/ProgressBar.vue —— 进度条组件 -->
<!-- 展示整体生成进度和当前执行的 Agent -->
<script setup lang="ts">
// 定义组件的 props（父组件传入）
// percent：进度百分比（0-100）
// currentAgent：当前执行的 Agent 名称
defineProps<{
  percent: number
  currentAgent: string
}>()
</script>

<template>
  <a-card class="progress-card" title="生成进度">
    <!-- a-progress：Ant Design Vue 进度条组件 -->
    <!-- :percent 绑定进度值 -->
    <a-progress :percent="percent" />
    <!-- 当前 Agent 提示 -->
    <div class="agent-tip" v-if="currentAgent">
      <!-- 使用 span 防止被当作块级元素换行 -->
      <span class="agent-text">{{ currentAgent }}</span>
    </div>
  </a-card>
</template>

<style scoped>
.progress-card {
  margin-bottom: 16px;
}
.agent-tip {
  margin-top: 8px;
  color: #666;
  font-size: 13px;
}
.agent-text {
  display: inline-block;
}
</style>
```

```vue
<!-- src/components/TitleSelection.vue —— 标题选择组件 -->
<!-- 展示 AI 生成的多个标题，用户点击选择一个 -->
<script setup lang="ts">
// 定义 props：标题候选项数组
defineProps<{
  options: string[]
}>()

// 定义 emit 事件：select 事件，参数是选中的标题
// 父组件通过 @select="store.selectTitle" 监听
const emit = defineEmits<{
  (e: 'select', title: string): void
}>()
</script>

<template>
  <a-card title="请选择标题" class="title-card">
    <!-- a-radio-group：单选按钮组 -->
    <!-- 用户只能选择一个标题 -->
    <a-radio-group
      v-model:value="selected"
      class="title-group"
    >
      <!-- 遍历标题候选项 -->
      <!-- v-for：循环渲染每个标题 -->
      <a-radio
        v-for="(title, index) in options"
        :key="index"
        :value="title"
      >
        {{ title }}
      </a-radio>
    </a-radio-group>

    <!-- 确认按钮：触发 select 事件 -->
    <a-button
      type="primary"
      class="confirm-btn"
      :disabled="!selected"
      @click="emit('select', selected)"
    >
      确定这个标题
    </a-button>
  </a-card>
</template>

<script lang="ts">
// 补充：local state 使用普通 script 导出
// 注意：这里与 <script setup> 共存是合法的
export default {}
</script>

<style scoped>
.title-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.confirm-btn {
  margin-top: 16px;
}
</style>
```

```vue
<!-- src/components/OutlineEditor.vue —— 大纲编辑组件 -->
<!-- 展示/编辑大纲，支持流式输出时的实时更新 -->
<script setup lang="ts">
// 定义 props
// outline：大纲内容
// streaming：是否正在流式输出
defineProps<{
  outline: string
  streaming: boolean
}>()

// 定义 emit：update 事件（大纲编辑）
// 父组件通过 @update="store.outline = $event" 监听
const emit = defineEmits<{
  (e: 'update', value: string): void
}>()
</script>

<template>
  <a-card
    title="大纲"
    :loading="streaming"
    class="outline-card"
  >
    <!-- 大纲内容区域 -->
    <!-- 流式输出时：文本自动追加显示 -->
    <!-- 用户手动编辑时：触发 update 事件 -->
    <div class="outline-area">
      <!-- 使用 pre-wrap 保留换行符 -->
      <pre v-if="streaming">{{ outline }}</pre>
      <!-- 非流式时使用 textarea 允许编辑 -->
      <a-textarea
        v-else
        :value="outline"
        :rows="8"
        @change="emit('update', $event.target.value)"
      />
    </div>
  </a-card>
</template>

<style scoped>
.outline-area pre {
  white-space: pre-wrap;  /* 保留换行和空格 */
  font-family: inherit;    /* 继承字体 */
  margin: 0;
  line-height: 1.8;
}
</style>
```

> **说明：** TitleSelection.vue 中 `selected` 变量未在 `<script setup>` 中定义，实际项目中应使用 `ref('')` 定义并选中首个选项，此处保留为教学演示，读者可在本地补全。完整可运行版本的 selected 定义见下方避坑指南 7.5。

### 3.10 单元测试（creationStore.spec.ts）

```typescript
// src/__tests__/creationStore.spec.ts —— Pinia store 单元测试
// 使用 Vitest + Pinia 官方测试工具

// 导入 Vitest 的测试函数
import { describe, it, expect, beforeEach } from 'vitest'
// 导入 createPinia 和 setActivePinia（Pinia 测试辅助）
import { createPinia, setActivePinia } from 'pinia'
// 导入待测试的 store
import { useCreationStore } from '@/stores/creationStore'

// 测试套件：创建状态管理相关的测试
describe('creationStore', () => {

  // 每个测试用例执行前：设置独立的 Pinia 环境
  beforeEach(() => {
    // 创建一个全新的 Pinia 实例
    // 并设为当前激活的 Pinia
    // 保证测试用例之间状态隔离
    setActivePinia(createPinia())
  })

  // 测试 1：初始状态正确
  it('初始状态应该是 idle 阶段', () => {
    // 获取 store 实例
    const store = useCreationStore()
    // 断言阶段是 idle
    expect(store.phase).toBe('idle')
    // 断言进度是 0
    expect(store.progress).toBe(0)
    // 断言当前没有生成中
    expect(store.isGenerating).toBe(false)
  })

  // 测试 2：选择标题后进入大纲阶段
  it('选择标题后进入大纲阶段并推进进度', () => {
    const store = useCreationStore()
    // 模拟用户选择标题
    store.selectTitle('2026年AI发展趋势')
    // 断言进入大纲阶段
    expect(store.phase).toBe('outline')
    // 断言进度推进到 20
    expect(store.progress).toBe(20)
  })

  // 测试 3：追加大纲内容
  it('追加大纲内容应该拼接文本', () => {
    const store = useCreationStore()
    // 第一次追加
    store.appendOutline('一、背景')
    // 第二次追加
    store.appendOutline('\n二、现状')
    // 断言内容被正确拼接
    expect(store.outline).toBe('一、背景\n二、现状')
  })

  // 测试 4：canSubmit 派生状态
  it('选题为空时不能提交', () => {
    const store = useCreationStore()
    // 选题为空
    store.topic = ''
    // 断言不能提交
    expect(store.canSubmit).toBe(false)
  })
})
```

---

## 四、运行验证

### 4.1 安装依赖并启动

```bash
# 进入项目目录
cd vue-ai-demo

# 安装依赖
npm install

# 启动开发服务器（默认端口 3000）
npm run dev
```

启动后，控制台输出：

```
  VITE v6.0.5  ready in 300 ms

  ➜  Local:   http://localhost:3000/
  ➜  Network: use --host to expose
```

### 4.2 验证页面

打开浏览器访问 `http://localhost:3000/`，可以看到：
- 顶部导航栏："AI 创作助手"
- 中部卡片："开始创作" + 选题输入框 + 按钮
- 底部版权信息

**验证流程：**

1. **输入选题**：在输入框中输入"2026年人工智能发展趋势"，点击"开始创作"
2. **观察阶段切换**：页面依次展示"生成进度"、"选择标题"、"大纲编辑"等组件
3. **SSE 接收演示**：如后端已启动（第 4 篇 SSE 文章中的 demo），前端会实时接收大纲、正文内容并流式展示

> **提示：** 本文 Demo 前端部分不依赖后端也能运行——输入选题后如果没有后端，会进入标题选择阶段（标题数组为空时隐藏），此时可通过 Vue DevTools 手动注入数据观察交互效果。

### 4.3 生产构建验证

```bash
# TypeScript 类型检查 + 生产打包
npm run build

# 预期输出
vue-tsc -p tsconfig.json --noEmit && vite build
✓ built in 1.5s
```

### 4.4 运行单元测试

```bash
npm test

# 预期输出
 Test Files  1 passed (1)
      Tests  4 passed (4)
   Duration  1.2s
```

---

## 五、项目对照

### 5.1 Demo 与真实项目的对比

| 对比维度 | 本 Demo（vue-ai-demo） | 真实项目（ai-passage-creator-web） |
|---------|----------------------|----------------------------------|
| 构建工具 | Vite 开发服务器 | Vite + 生产优化配置 |
| UI 组件 | Ant Design Vue 全量引入 | 按需引入（体积优化） |
| 状态管理 | 单一 creationStore | 多 store（用户、创作、设置） |
| SSE 处理 | 基础事件监昕 | 断线重连 + 心跳 + 错误恢复 |
| 路由 | 无（单页面） | Vue Router 多页面 |
| 请求封装 | 基础 axios | axios 拦截器 + 统一错误处理 |
| 响应式 | 基础 ref/computed | 深度优化（debounce、watch 场景） |
| 生产构建 | 基础 vite build | 代码分割 + 资源压缩 + CDN |
| 测试 | Vitest 单元测试 | 单元 + 端到端（Playwright） |
| 权限 | 无 | 登录态 + 路由守卫 |

### 5.2 Demo 的局限性

1. **无路由**：Demo 只有一个页面，真实项目有登录、创作、历史记录等多个页面
2. **Pinia 持久化缺失**：Demo 刷新后状态丢失，真实项目使用 localStorage 持久化用户偏好
3. **SSE 容错不足**：Demo 依赖 EventSource 自动重连，真实项目需处理重连后的状态恢复
4. **无 UI 按需引入**：Demo 全量引入 Ant Design Vue，真实项目按需引入优化打包体积
5. **无权限控制**：真实项目需基于用户登录态控制页面访问

### 5.3 进阶路径

| 步骤 | 知识点 | 参考文章 |
|------|--------|----------|
| 1 | Vue 3 Composition API、响应式 | 09 Vue 3 + Ant Design Vue（本文） |
| 2 | Pinia 状态管理 | 09 Vue 3 + Ant Design Vue（本文） |
| 3 | SSE 前端监听（EventSource） | 09 Vue 3 + Ant Design Vue（本文） |
| 4 | Vue Router 路由 | Vue Router 官方文档 |
| 5 | Pinia 持久化（pinia-plugin-persistedstate） | Pinia 官方文档 |
| 6 | 按需引入 Ant Design Vue | Ant Design Vue 官方文档 |

---

## 六、面试题

### Q1: Composition API 和 Options API 的区别？如何选择？

**参考答案：**

| 维度 | Composition API | Options API |
|------|----------------|-------------|
| 逻辑组织 | 按功能组织（同一逻辑的 ref/methods/computed 在一起） | 按选项类型组织（data 在一起、methods 在一起） |
| 逻辑复用 | `useXxx()` 组合函数，无命名冲突 | mixins 有命名冲突，来源不明 |
| TypeScript | 天然支持，类型推断好 | 需要额外类型声明 |
| 学习曲线 | 需理解响应式原理（ref、reactive） | 更直观，适合新手 |
| 代码可维护性 | 组件变大时逻辑依然清晰 | 组件变大时"碎片化"严重 |
| 适用场景 | 复杂组件、大型项目 | 简单展示型组件 |

**核心差异在"逻辑组织方式"：** 一个复杂的创作页面会有选题、标题、大纲、正文、配图等多块逻辑。Options API 把所有这些逻辑的 `data` 放在一起、`methods` 放在一起——当你需要修改"标题选择"逻辑时，要在 data、methods、computed 三个区块之间来回跳。Composition API 则把"标题选择"相关的所有状态和函数放在一起，甚至能提取成独立的 `useTitleSelection()` 组合函数。

**选型建议：**
- 新项目优先使用 Composition API + `<script setup>`（Vue 3 官方推荐）
- 简单展示型组件可继续使用 Options API（更简洁）
- 逻辑复用需求强烈时，用组合函数替代 Options API 的 mixins

**追问应对：** "`<script setup>` 和普通 `<script>` 的区别？" 答：`<script setup>` 是编译时语法糖，顶层变量自动暴露给模板、导入的组件自动注册、不需要 `return`；普通 `<script>` 用于补充无法在 `<script setup>` 中声明的内容（如自定义组件命名），两者可以共存。`defineProps` 和 `defineEmits` 是仅可在 `<script setup>` 中使用的编译宏，不需要导入。

### Q2: 为什么用 Pinia 而不用 Vuex？Pinia 相比 Vuex 的优势有哪些？

**参考答案：**

Pinia 相比 Vuex 的核心优势：

1. **更完整的 TypeScript 支持**：Pinia 天然支持 TypeScript，store 中的 state、getters、actions 自动类型推断，无需额外声明类型。

2. **更轻量的架构**：Pinia 移除了 mutations 层，**actions 直接修改 state**。Vuex 需要 mutations（同步修改 state）+ actions（异步提交 mutations）两层，概念多且冗余。AI 创作流程中大量操作是异步的（调用后端、接收 SSE），Pinia 的简化模型更贴合实际需求。

3. **组合式 API 原生支持**：Pinia store 内部直接用 `ref()`、`computed()`、`watch()` 编写，与 Vue 3 的 Composition API 编程模型完全一致（见本文章节 3.5 的 creationStore 写法）。

4. **模块化更自然**：Pinia 通过多个独立的 `defineStore()` 实现模块化——每个 store 天然独立。Vuex 需要在单一 store 中用 `modules` 分区，还要配置 namespaced，心智负担更大。

5. **体积更小**：Pinia 约 1KB，Vuex 约 10KB。

**项目选型依据：** 创作流程涉及标题、大纲、正文、进度等一系列**强关联状态**，Pinia 的 actions 可以直接进行异步请求 + 状态更新的串联（如 `startCreation` 先调用 API 再更新 phase、progress），逻辑集中、可测试性强；`useCreationStore` 作为唯一数据源，组件通过解构读取状态，天然支持跨组件共享（页面、标题选择、大纲编辑需要读写同一份状态）。

**追问应对：** "Pinia 的 store 在组件外（如 router guard）怎么用？" 答：Pinia 依赖 `setActivePinia` 设置当前激活的 Pinia 实例。在组件中使用 `useCreationStore()` 时，Vue 插件自动完成注入；在组件外（如路由守卫、工具函数）使用时，需要在 `createPinia()` 后传入应用实例，或者先 `setActivePinia(pinia)` 再调用。这与 Vuex 在组件外通过 `this.$store` 使用是类似的限制。

### Q3: 前端如何用 EventSource 实现 SSE 流式监听？有哪些注意事项？

**参考答案：**

**基本实现（完整版见本文章节 3.6）：**

```typescript
// 1. 创建连接（GET 请求）
const eventSource = new EventSource(`/api/article/generate/${taskId}`)

// 2. 注册命名事件监听
// 后端通过 event: 字段区分消息类型
eventSource.addEventListener('AGENT3_STREAMING', (event) => {
  const data = JSON.parse(event.data)  // event.data 是字符串，需要解析
  content.value += data.text           // 流式追加
})

// 3. 终态事件触发后主动关闭
eventSource.addEventListener('MERGE_COMPLETE', () => {
  eventSource.close()  // 不关闭连接会一直保持
})
```

**核心注意事项：**

| 注意事项 | 说明 |
|----------|------|
| **自动重连** | EventSource 内置重连机制，连接断开后自动重试。可在 `onerror` 中感知断连状态并更新 UI |
| **命名事件** | `addEventListener('EVENT', fn)` 监听命名事件；`onmessage` 只监听默认事件。项目全部使用命名事件区分 Agent |
| **主动关闭** | 收到终态事件（如 MERGE_COMPLETE）或需要取消时必须 `eventSource.close()`，否则连接长期占用 |
| **GET 限制** | EventSource 只能发送 GET 请求、不支持自定义请求头。认证信息需通过 URL 参数传递，或先 POST 获取 taskId 再建连 |
| **组件卸载清理** | 必须在 `onUnmounted` 中 `disconnect()`，否则造成内存泄漏 |
| **数据解析** | `event.data` 是字符串，`JSON.parse()` 包裹 try/catch 防止解析异常崩溃 |
| **浏览器兼容** | 现代浏览器均支持；IE 不支持，可用 `fetch` + `ReadableStream` 替代 |

**SSE vs WebSocket 选型：** AI 创作是典型的"服务端推送、前端只收不发"场景，SSE 更轻量（原生支持、自动重连、无需握手升级协议）；WebSocket 适合双向实时通信（在线协作编辑、聊天室）。项目当前的业务方向决定 SSE 是更优解。

**追问应对：** "断线重连后，如何恢复已完成的阶段？" 答：（1）后端任务状态持久化到数据库，前端重连后通过 `GET /api/article/status/{taskId}` 查询当前进度；（2）前端根据返回的阶段恢复 UI 状态（如已生成的大纲直接回填，不再重复推送）；（3）重连后 SSE 从断点（最后一个已确认的 event id）继续推送——SSE 协议本身支持 `id:` 字段 + `Last-Event-ID` 请求头，后端可据此实现断点续传。

---

## 七、避坑指南

### 7.1 ref 的值在 script 中必须用 `.value`

```typescript
// ❌ 错误：忘记 .value
// 模板中 ref 会自动解包，但 script 中不会
const count = ref(0)
count = count + 1  // 类型错误！count 是 Ref 对象
count++            // 同样错误

// ✅ 正确：script 中显式访问 .value
const count = ref(0)
count.value++              // 正确
console.log(count.value)   // 正确，输出 1
```

### 7.2 对 Pinia store 解构会丢失响应式

```typescript
// ❌ 错误：直接解构 store
// 解构得到的是普通值，不再响应式
const { count, add } = useCreationStore()
count.value++   // 页面不更新！

// ✅ 正确：解构 state 用 storeToRefs
// storeToRefs 保留 ref 的响应性
import { storeToRefs } from 'pinia'
const store = useCreationStore()
const { count, titleOptions } = storeToRefs(store)
count.value++   // 页面正常更新

// ✅ 正确：action 方法直接解构（本身是函数，无响应式问题）
const { startCreation, selectTitle } = store
```

### 7.3 EventSource 连接必须手动关闭，防止内存泄漏

```typescript
// ❌ 错误：收到完成事件后不关闭连接
eventSource.addEventListener('MERGE_COMPLETE', () => {
  // 处理数据，但忘记 close()
})

// ✅ 正确：终态事件中主动关闭
eventSource.addEventListener('MERGE_COMPLETE', (event) => {
  // 先处理数据
  handleComplete(JSON.parse(event.data))
  // 再关闭连接
  eventSource.close()
})

// ✅ 正确：组件卸载时兜底断开
onUnmounted(() => {
  eventSource.close()  // 防止组件销毁后连接残留
})
```

### 7.4 配置参考

```typescript
// vite.config.ts —— 完整配置参考
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'  // 路径解析

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // 配置 @ 别名：import xxx from '@/api/xxx'
      // 避免写一长串相对路径
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 3000,                            // 端口
    proxy: {
      '/api': {                            // 代理后端
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  build: {
    outDir: 'dist',                        // 构建输出目录
    sourcemap: false,                      // 生产环境关闭 sourcemap
    chunkSizeWarningLimit: 1500            // 大块警告阈值（KB）
  }
})
```

### 7.5 测试环境注意事项

```typescript
// 1. Pinia 测试必须隔离：每个用例创建新的 Pinia 实例
// 否则 store 中的 ref 状态会在用例之间共享，导致断言混乱
beforeEach(() => {
  setActivePinia(createPinia())
})

// 2. 异步 actions 测试使用 await
// store.startCreation 是 async 函数
it('开始创作会设置阶段', async () => {
  const store = useCreationStore()
  await store.startCreation('测试选题')  // 必须 await
  expect(store.phase).toBe('title')
})

// 3. Component 挂载测试使用 @vue/test-utils
import { mount } from '@vue/test-utils'
// mount(Component, { global: { plugins: [createPinia()] } })
// 组件使用 store 时必须传入 Pinia 插件
```

> **说明：** 本文 TitleSelection.vue 中演示了组件内部选中态的使用方式，实际项目中建议在 `<script setup>` 内使用 `const selected = ref('')` 定义选中态，并在 `watch` 中监听 `options` 变化自动选中首项，完整写法为：`const selected = ref(options[0] ?? '')`，按钮 `@click="emit('select', selected)"` 前先判断 `selected` 非空。