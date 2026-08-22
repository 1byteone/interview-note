# Vue 3 管理后台入门：从零搭建养老平台管理端

> 本文是 zznursing 项目技术栈深度剖析系列的第 5 篇（入门篇）。面向 Java 后端开发者，手把手带你从零搭建一个 Vue 3 + Vite + Element Plus 的管理后台，实现养老平台的设备管理、住户管理和健康看板功能。
>
> **对应项目：** zznursing 养老机构综合运营平台 —— 管理后台（Vue 3 前端）
> **难度等级：** Level 1 入门
> **预计阅读时间：** 30 分钟（含代码实操）

---

## 一、项目背景

### 1.1 什么是 Vue 3 + Vite + Element Plus

Vue 3 是当前前端领域最主流的前端框架之一，由尤雨溪团队于 2020 年正式发布。相比 Vue 2，Vue 3 在响应式原理、TypeScript 支持、性能优化等方面做了全面升级。Vite 是 Vue 团队开发的下一代前端构建工具，基于浏览器原生 ES Module 实现了秒级的热更新启动，彻底告别了 Webpack 时代漫长的冷启动等待。Element Plus 是 Element UI 的 Vue 3 版本，提供了 Table（表格）、Form（表单）、Dialog（弹窗）、Card（卡片）等 60 多个企业级 UI 组件，是 Vue 3 生态中最成熟的中后台组件库。

三者的关系可以这样理解：Vue 3 是"骨架"，负责页面的组件化组织和响应式数据绑定；Vite 是"脚手架和引擎"，负责代码的编译、打包和热更新；Element Plus 是"家具和装修"，提供现成的 UI 组件让开发者快速搭建页面。三者结合，构成了当前中后台开发最主流的技术方案。

### 1.2 为什么 zznursing 选择这个技术栈

zznursing 是一个养老机构综合运营平台，管理后台面向养老院运营人员（院长、护工、管理员），提供设备管理、住户管理、健康看板、告警处理、报表统计等核心功能。选择 Vue 3 + Vite + Element Plus 主要基于以下考虑：

**第一，Vue 3 的 Composition API 非常适合复杂业务逻辑的复用。** 养老平台中多个页面都需要设备状态查询、健康数据展示、告警处理等逻辑。使用 Composition API 可以将这些逻辑提取为独立的 `useXxx` 组合函数，在多个组件间共享，避免代码重复。例如，`useDeviceStatus` 函数可以在设备管理页面和健康看板中同时使用。

**第二，Vite 的开发体验对前端团队非常重要。** 养老平台的管理后台页面较多（仪表盘、设备管理、住户管理、告警中心、报表等），在 Webpack 时代每次冷启动需要 30 秒以上，修改代码后的热更新也要 2-3 秒。Vite 基于 ES Module 的按需编译机制，将冷启动时间缩短到 1 秒以内，热更新达到毫秒级。这对于需要频繁调试的前端开发来说，体验提升是革命性的。

**第三，Element Plus 的组件完整度适合企业内部系统。** 养老平台涉及大量数据表格（设备列表、住户列表、告警列表）、表单（添加住户、注册设备）、弹窗（确认操作、查看详情）。Element Plus 提供了现成的 `el-table`、`el-form`、`el-dialog` 等组件，开箱即用，无需从零开发。而且 Element Plus 支持国际化，可以方便地适配中文界面。

### 1.3 管理后台的核心功能

zznursing 的管理后台主要包含以下功能模块：

- **设备管理**：管理养老院中所有 IoT 设备（心率手环、跌倒检测器、定位胸牌等），包括设备注册、状态查看、绑定/解绑住户、OTA 升级等
- **住户管理**：管理入住老人的基本信息、健康档案、家属联系方式等
- **健康看板**：以卡片和图表形式展示全院老人的健康数据概览，包括在线设备数量、告警数量、健康趋势等
- **告警中心**：展示跌倒检测、心率异常、电子围栏越界等告警事件，支持处理确认
- **报表统计**：展示入住率、服务满意度、营收分析等运营数据

### 1.4 与传统 jQuery / SPA 方案的对比

在 Vue 3 出现之前，中后台开发主要有两种方案：传统 jQuery + 后端模板引擎（如 JSP、FreeMarker），以及基于 Vue 2 / React 的 SPA（单页应用）。

传统 jQuery 方案的特点是：每个页面由后端渲染，前端通过 jQuery 操作 DOM 实现交互。这种方案的优点是上手简单，适合后端开发者；缺点是页面切换需要完整刷新，交互体验差，前端代码难以维护（大量 `$()` 操作和回调嵌套）。在养老平台中，如果使用 jQuery 实现设备管理表格的搜索/排序/分页，需要手写大量 DOM 操作代码，且页面切换时数据状态会丢失。

Vue 2 SPA 方案相比 jQuery 有了质的飞跃：组件化开发、响应式数据绑定、路由切换无需刷新页面。但 Vue 2 使用 Options API（`data`、`methods`、`computed` 分散在四个选项中），当组件逻辑复杂时，同一功能的代码会被拆散到不同位置，阅读和维护困难。此外，Vue 2 的响应式系统基于 `Object.defineProperty`，无法监听数组索引的变化和对象属性的新增，需要调用 `Vue.set()` 或 `this.$set()` 来手动处理。

Vue 3 的 Composition API 完美解决了这些问题：通过 `setup()` 函数将同一逻辑的代码聚合在一起，可以提取为 `useXxx` 函数供多个组件复用；响应式系统基于 `Proxy`，可以监听任意属性的新增和删除，无需手动处理。

---

## 二、核心概念

### 2.1 Vue 3 Composition API（setup、ref、reactive、computed、watch）

Composition API 是 Vue 3 最核心的变革。它引入了一个新的组件选项 `setup()`，所有逻辑都在这个函数中组织。

**ref 和 reactive** 是 Vue 3 中两种创建响应式数据的方式：

- `ref()` 用于包装基本类型值（字符串、数字、布尔值），在 `<script>` 中需要通过 `.value` 访问，但在模板中会自动解包，无需 `.value`
- `reactive()` 用于包装对象，返回一个响应式代理，可以直接访问属性，无需 `.value`

```javascript
// ref 和 reactive 的基本用法
import { ref, reactive } from 'vue'

const count = ref(0)           // 响应式基本类型
const user = reactive({        // 响应式对象
  name: '张三',
  age: 80
})

console.log(count.value)       // 在 JS 中需要用 .value 访问
console.log(user.name)         // reactive 对象直接访问属性
```

**computed** 用于创建派生状态，当依赖的数据变化时自动重新计算。这和 Java 中的 `getter` 方法类似，但会自动缓存结果，只有依赖变化时才重新计算。

**watch** 用于监听响应式数据的变化并执行副作用操作，比如在数据变化时调用 API 或更新本地存储。这和 Java 中的监听器模式类似。

### 2.2 Vite 构建工具

Vite 是 Vue 3 生态的标配构建工具，它的核心优势在于：

- **开发模式基于原生 ES Module**：浏览器直接加载 `.vue` 和 `.js` 文件，无需打包，启动速度极快
- **按需编译**：只有当前页面用到的模块才会被编译，修改代码后只重新编译变化的模块
- **HMR 毫秒级热更新**：修改代码后，页面在不刷新的情况下实时更新，且保留当前状态
- **生产模式基于 Rollup**：打包时使用 Rollup 进行代码压缩和 Tree Shaking，生成优化的生产包

用一句话总结：Vite 让开发体验从"改代码等 3 秒刷新"变成了"改代码即时生效"。

### 2.3 Element Plus 组件库

Element Plus 提供了中后台开发最常用的组件，本文主要用到以下几个：

| 组件 | 用途 | 关键属性 |
|------|------|----------|
| `el-container` / `el-aside` / `el-header` / `el-main` | 页面布局骨架 | 无特殊属性 |
| `el-menu` / `el-menu-item` | 侧边栏导航菜单 | `router` 开启路由模式 |
| `el-table` / `el-table-column` | 数据表格展示 | `data` 绑定数据源，`prop` 绑定列字段 |
| `el-form` / `el-form-item` | 表单输入 | `model` 绑定表单数据，`rules` 校验规则 |
| `el-input` | 文本输入框 | `v-model` 双向绑定 |
| `el-dialog` | 弹窗 | `v-model` 控制显隐 |
| `el-button` | 按钮 | `type` 控制样式，`@click` 绑定点击事件 |
| `el-card` | 卡片容器 | `shadow` 控制阴影效果 |
| `el-tag` | 标签（显示状态） | `type` 控制颜色 |
| `el-pagination` | 分页组件 | `total`、`current-page`、`page-size` |

### 2.4 Vue Router 路由

Vue Router 是 Vue 官方的路由管理器，用于实现 SPA 中的页面切换。核心概念包括：

- **路由配置**：定义 URL 路径和组件的对应关系
- **路由视图**：`<router-view>` 组件，用于渲染当前路径对应的组件
- **路由导航**：`<router-link>` 组件或 `router.push()` 方法，实现页面跳转
- **路由守卫**：`router.beforeEach()` 钩子，在路由切换前执行权限检查

在 zznursing 管理后台中，路由配置了登录页、仪表盘、设备管理、住户管理、报表等页面，同时通过路由守卫实现登录鉴权，未登录的用户自动跳转到登录页。

### 2.5 Pinia 状态管理

Pinia 是 Vue 的官方状态管理库，替代了 Vuex。它的核心概念是 Store（状态仓库），每个 Store 包含 state（数据）、getters（派生数据）和 actions（操作）三个部分。

与 Vuex 相比，Pinia 的优势在于：

- **去掉了 mutations**：直接修改 state，无需区分同步和异步
- **完整的 TypeScript 支持**：无需额外的类型声明
- **模块化**：每个 Store 独立定义，无需 modules 嵌套
- **轻量级**：API 更简洁，体积更小

在 zznursing 管理后台中，我们使用 Pinia 管理用户状态（登录信息、token）和设备数据（设备列表、搜索条件）。

### 2.6 Axios HTTP 请求

Axios 是一个基于 Promise 的 HTTP 客户端，用于浏览器和 Node.js 环境。在 Vue 项目中，Axios 负责与后端 API 通信，发送 GET/POST 请求并处理响应。

Axios 的核心功能包括：

- **请求拦截器**：在每个请求发送前注入 token 等公共信息
- **响应拦截器**：统一处理响应错误，如 401 未登录自动跳转
- **请求取消**：支持取消正在进行的请求
- **超时设置**：设置请求超时时间

在本文的示例中，我们使用 Axios 请求本地的 Mock API，模拟真实的后端接口调用。

---

## 三、从零搭建：完整代码

本节将搭建一个完整的养老平台管理后台，包含登录页、仪表盘、设备管理、住户管理四个页面，使用 Mock API 模拟后端数据，无需真实后端即可运行。

### 3.1 项目结构

```
hello-admin/
├── package.json                    # 项目依赖配置
├── vite.config.js                  # Vite 构建配置
├── index.html                      # 入口 HTML 文件
├── src/
│   ├── main.js                     # Vue 应用入口
│   ├── App.vue                     # 根组件（布局）
│   ├── router/
│   │   └── index.js                # 路由配置
│   ├── views/
│   │   ├── Login.vue               # 登录页
│   │   ├── Dashboard.vue           # 仪表盘
│   │   ├── DeviceList.vue          # 设备管理
│   │   └── ResidentList.vue        # 住户管理
│   ├── stores/
│   │   ├── user.js                 # 用户状态
│   │   └── device.js               # 设备状态
│   └── api/
│       ├── request.js              # Axios 实例
│       └── mock.js                 # Mock API 服务
```

### 3.2 package.json —— 项目依赖配置

```json
{
  "name": "hello-admin",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.3.0",
    "pinia": "^2.1.0",
    "element-plus": "^2.5.0",
    "@element-plus/icons-vue": "^2.3.0",
    "axios": "^1.6.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "vite": "^5.0.0"
  }
}
```

**代码说明：**

- `vue`：Vue 3 核心库，版本 3.4.0 及以上
- `vue-router`：Vue 官方路由库，用于实现 SPA 页面切换
- `pinia`：Vue 官方状态管理库，替代 Vuex
- `element-plus`：Element Plus 组件库，提供中后台 UI 组件
- `@element-plus/icons-vue`：Element Plus 配套的图标库
- `axios`：HTTP 请求库，用于调用后端 API
- `@vitejs/plugin-vue`：Vite 的 Vue 3 插件，用于编译 `.vue` 单文件组件
- `vite`：前端构建工具，提供开发服务器和生产打包

### 3.3 vite.config.js —— Vite 构建配置

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// 使用 defineConfig 定义 Vite 配置，可以获得 TypeScript 类型提示
export default defineConfig({
  plugins: [vue()],  // 启用 Vue 3 插件，支持 .vue 文件编译
  resolve: {
    alias: {
      // 设置 @ 别名指向 src 目录，方便引入模块
      // 例如：import App from '@/App.vue' 等价于 import App from 'src/App.vue'
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,  // 开发服务器端口号
    open: true   // 启动后自动打开浏览器
  }
})
```

**代码说明：**

- `@vitejs/plugin-vue` 插件负责将 `.vue` 单文件组件编译为 JavaScript，这是 Vite 支持 Vue 3 的核心依赖
- `@` 别名让导入路径更简洁，避免 `../../../../` 这种深层嵌套的路径
- `server.port` 设置开发服务器端口为 3000，避免与后端 Spring Boot 的 8080 端口冲突

### 3.4 index.html —— 入口 HTML 文件

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <!-- 设置移动端视口，确保在移动设备上显示正常 -->
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>智慧养老管理平台</title>
</head>
<body>
  <!-- Vue 3 应用挂载点，所有组件都会渲染到这个 div 内部 -->
  <div id="app"></div>
  <!-- 入口 JavaScript 文件，Vite 会自动处理它 -->
  <!-- type="module" 表示使用 ES Module 方式加载 -->
  <script type="module" src="/src/main.js"></script>
</body>
</html>
```

**代码说明：**

- `div#app` 是 Vue 应用的根挂载点，Vue 会接管这个元素内部的所有内容
- `script type="module"` 是 ES Module 的标准加载方式，Vite 在开发模式下利用这个特性实现按需编译
- 注意 `src` 路径以 `/` 开头，这是 Vite 的约定，表示相对于项目根目录

### 3.5 src/main.js —— Vue 应用入口

```javascript
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'

// 创建 Vue 应用实例
const app = createApp(App)

// 注册 Element Plus 组件库，之后所有组件中都可以使用 el- 开头的组件
app.use(ElementPlus)

// 注册所有 Element Plus 图标组件，可以在模板中直接使用图标名
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册路由管理器，启用 SPA 页面切换功能
app.use(router)

// 注册 Pinia 状态管理器，用于跨组件共享状态
app.use(createPinia())

// 将应用挂载到 index.html 中的 #app 元素上
app.mount('#app')
```

**代码说明：**

- `createApp(App)` 创建 Vue 3 应用实例，这是 Vue 3 的入口 API
- `app.use()` 方法用于注册插件，Element Plus、Router、Pinia 都是通过插件机制集成
- 注册 Element Plus 图标组件后，可以在模板中直接使用 `<el-icon><Monitor /></el-icon>` 这样的写法
- 挂载顺序有讲究：先注册插件，再挂载应用，确保插件在组件渲染前已经初始化完毕

### 3.6 src/App.vue —— 根组件（布局骨架）

```vue
<template>
  <!-- 如果用户已登录，显示完整布局；否则显示登录页 -->
  <!-- v-if 是条件渲染指令，userStore.isLoggedIn 为 true 时渲染布局 -->
  <div class="app-container" v-if="userStore.isLoggedIn">
    <!-- 整体布局容器，Element Plus 提供的布局组件 -->
    <el-container style="height: 100vh">
      <!-- 左侧导航栏 -->
      <el-aside width="220px" class="app-aside">
        <!-- 系统标题区域 -->
        <div class="app-logo">
          <h2>智慧养老平台</h2>
        </div>
        <!-- 导航菜单，router 属性开启路由模式，点击菜单项自动跳转 -->
        <el-menu
          :default-active="route.path"
          router
          class="app-menu"
        >
          <!-- 每个 el-menu-item 对应一个路由，index 属性指定跳转路径 -->
          <el-menu-item index="/dashboard">
            <el-icon><Monitor /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-menu-item index="/devices">
            <el-icon><Setting /></el-icon>
            <span>设备管理</span>
          </el-menu-item>
          <el-menu-item index="/residents">
            <el-icon><User /></el-icon>
            <span>住户管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <!-- 右侧主内容区域 -->
      <el-container>
        <!-- 顶部导航栏 -->
        <el-header class="app-header">
          <!-- 右侧显示当前登录用户和退出按钮 -->
          <div class="header-right">
            <span class="header-user">{{ userStore.username }}</span>
            <!-- 退出登录按钮，点击后调用 userStore 的 logout 方法 -->
            <el-button type="danger" size="small" @click="handleLogout">
              退出登录
            </el-button>
          </div>
        </el-header>
        <!-- 主内容区域，router-view 渲染当前路由对应的组件 -->
        <!-- 点击菜单切换路由时，这里显示的内容会随之变化 -->
        <el-main class="app-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
  <!-- 未登录时，只显示 router-view（登录页） -->
  <router-view v-else />
</template>

<script setup>
// 导入 Vue 3 的响应式 API
import { ref } from 'vue'
// 导入路由相关 API，useRoute 获取当前路由信息，useRouter 进行路由跳转
import { useRoute, useRouter } from 'vue-router'
// 导入用户状态管理 Store
import { useUserStore } from '@/stores/user'

// 获取当前路由信息实例，用于高亮当前菜单项
const route = useRoute()
// 获取路由管理器实例，用于编程式导航
const router = useRouter()
// 获取用户状态 Store，包含登录状态和用户信息
const userStore = useUserStore()

// 退出登录的处理函数
// 清空用户状态并跳转到登录页
const handleLogout = () => {
  userStore.logout()  // 清除 Pinia 中的用户状态和 localStorage 中的 token
  router.push('/login')  // 跳转到登录页
}
</script>

<style>
/* 全局样式，重置默认边距 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

/* 应用容器，占满整个视口 */
.app-container {
  width: 100%;
  height: 100vh;
}

/* 左侧导航栏样式 */
.app-aside {
  background-color: #304156;
  color: white;
}

/* 系统标题区域样式 */
.app-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.app-logo h2 {
  color: white;
  font-size: 18px;
  font-weight: 500;
}

/* 导航菜单样式，覆盖 Element Plus 默认主题色 */
.app-menu {
  border-right: none;
  background-color: transparent;
}

.app-menu .el-menu-item {
  color: rgba(255, 255, 255, 0.7);
}

.app-menu .el-menu-item.is-active {
  color: #409EFF;
  background-color: rgba(64, 158, 255, 0.1);
}

/* 顶部导航栏样式 */
.app-header {
  background-color: white;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 20px;
}

/* 头部右侧区域样式 */
.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
}

/* 用户名显示样式 */
.header-user {
  font-size: 14px;
  color: #606266;
}

/* 主内容区域样式 */
.app-main {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
```

**代码说明：**

- `App.vue` 是整个应用的根组件，负责管理整体的布局结构
- 使用 `v-if="userStore.isLoggedIn"` 控制显示布局还是登录页，这是 Vue 3 条件渲染的标准写法
- `el-menu` 的 `router` 属性开启路由模式，点击菜单项时自动通过 Vue Router 跳转到对应的 URL
- `:default-active="route.path"` 根据当前路由路径高亮对应的菜单项
- `<router-view />` 是路由出口，当前路由对应的组件会渲染在这个位置
- 退出登录调用 `userStore.logout()` 清除状态，然后通过 `router.push` 跳转到登录页
- 样式使用 scoped 样式（`<style scoped>`），样式只作用于当前组件，不会污染全局

### 3.7 src/router/index.js —— 路由配置

```javascript
import { createRouter, createWebHistory } from 'vue-router'

// 定义路由表，每个路由对象包含 path（路径）、name（名称）、component（对应的组件）
const routes = [
  {
    path: '/login',  // 登录页路径
    name: 'Login',
    // 使用动态导入实现懒加载，只有访问该路径时才加载对应的组件
    // 这样可以减少首屏加载的代码体积
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/dashboard',  // 仪表盘路径
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue')
  },
  {
    path: '/devices',  // 设备管理路径
    name: 'Devices',
    component: () => import('@/views/DeviceList.vue')
  },
  {
    path: '/residents',  // 住户管理路径
    name: 'Residents',
    component: () => import('@/views/ResidentList.vue')
  },
  {
    // 默认路径重定向到仪表盘
    path: '/',
    redirect: '/dashboard'
  }
]

// 创建路由实例
// createWebHistory 使用 HTML5 History 模式，URL 中不带 # 号
// 相比 Hash 模式（URL 带 #），History 模式的 URL 更美观
const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：在每次路由切换前执行
// to: 要跳转到的路由对象  from: 当前路由对象  next: 放行函数
router.beforeEach((to, from, next) => {
  // 从 localStorage 获取 token，判断用户是否登录
  const token = localStorage.getItem('token')
  // 如果访问登录页，直接放行
  if (to.path === '/login') {
    next()
  } else {
    // 访问其他页面时，检查是否有 token
    if (token) {
      next()  // 已登录，放行
    } else {
      next('/login')  // 未登录，跳转到登录页
    }
  }
})

export default router
```

**代码说明：**

- `createWebHistory()` 创建 HTML5 History 模式的路由，URL 类似 `http://localhost:3000/dashboard`，没有 `#` 号
- 组件使用动态导入 `() => import()` 实现懒加载，只有访问对应路径时才加载组件代码，减少首屏加载时间
- 路由守卫 `beforeEach` 是 Vue Router 提供的导航守卫，在每次路由切换前执行，适合做登录鉴权
- `next()` 是放行函数，`next('/login')` 表示跳转到登录页

### 3.8 src/views/Login.vue —— 登录页面

```vue
<template>
  <!-- 登录页容器，居中显示 -->
  <div class="login-container">
    <!-- 登录卡片，宽度 400px，带阴影效果 -->
    <el-card class="login-card" shadow="always">
      <!-- 卡片标题 -->
      <h2 class="login-title">智慧养老管理平台</h2>
      <p class="login-subtitle">请登录您的账号</p>

      <!-- 登录表单，model 绑定表单数据对象，rules 绑定校验规则 -->
      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="80px"
        size="large"
      >
        <!-- 用户名输入项 -->
        <el-form-item label="用户名" prop="username">
          <!-- v-model 双向绑定到 loginForm.username -->
          <el-input v-model="loginForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <!-- 密码输入项 -->
        <el-form-item label="密码" prop="password">
          <!-- type="password" 使输入内容以密文显示 -->
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <!-- 登录按钮 -->
        <el-form-item>
          <!-- loading 属性在提交时显示加载状态，防止重复点击 -->
          <!-- @click 绑定 handleLogin 方法 -->
          <el-button
            type="primary"
            :loading="loading"
            @click="handleLogin"
            style="width: 100%"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 提示信息 -->
      <p class="login-tip">提示：输入任意用户名和密码即可登录（演示模式）</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

// 获取路由管理器，用于登录成功后跳转
const router = useRouter()
// 获取用户状态 Store，用于保存登录信息
const userStore = useUserStore()

// 获取表单组件引用，用于调用表单的校验方法
const formRef = ref(null)
// 加载状态，控制登录按钮的 loading 效果
const loading = ref(false)

// 登录表单数据对象，使用 reactive 创建响应式对象
const loginForm = reactive({
  username: 'admin',  // 默认用户名
  password: '123456'  // 默认密码
})

// 表单校验规则
const loginRules = {
  // 用户名校验：必填，至少 2 个字符，最多 20 个字符
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  // 密码校验：必填，至少 6 个字符
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ]
}

// 登录处理函数
const handleLogin = async () => {
  // 1. 先校验表单，通过后才执行登录逻辑
  // validate() 返回 Promise，校验失败时抛出异常
  try {
    await formRef.value.validate()
  } catch {
    // 校验不通过，直接返回，不执行登录
    return
  }

  // 2. 设置加载状态，禁用按钮防止重复提交
  loading.value = true

  try {
    // 3. 调用 userStore 的 login 方法执行登录
    // 这里会调用 Mock API 验证用户名和密码
    await userStore.login(loginForm.username, loginForm.password)
    // 4. 登录成功，跳转到仪表盘
    router.push('/dashboard')
  } catch (error) {
    // 登录失败，错误信息已在 userStore 中通过 ElMessage 提示
    console.error('登录失败:', error)
  } finally {
    // 5. 无论成功还是失败，都取消加载状态
    loading.value = false
  }
}
</script>

<style scoped>
/* 登录页容器：全屏居中显示 */
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

/* 登录卡片样式 */
.login-card {
  width: 400px;
  padding: 20px;
}

/* 登录标题样式 */
.login-title {
  text-align: center;
  color: #303133;
  margin-bottom: 8px;
}

/* 登录副标题样式 */
.login-subtitle {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin-bottom: 30px;
}

/* 提示信息样式 */
.login-tip {
  text-align: center;
  color: #909399;
  font-size: 12px;
  margin-top: 10px;
}
</style>
```

**代码说明：**

- `el-form` 的 `ref="formRef"` 获取表单组件的引用，通过 `formRef.value.validate()` 手动触发校验
- `:rules="loginRules"` 绑定校验规则，每个字段可以有多个校验规则（必填、最小长度、最大长度等）
- `trigger: 'blur'` 表示在输入框失去焦点时触发校验
- `show-password` 属性在密码框右侧显示一个眼睛图标，点击可切换密码的显示/隐藏
- `loading` 状态在登录过程中禁用按钮，防止用户重复提交
- 使用 `try/catch/finally` 确保无论登录成功还是失败，都取消加载状态，避免按钮一直禁用

### 3.9 src/views/Dashboard.vue —— 仪表盘页面

```vue
<template>
  <div class="dashboard">
    <!-- 页面标题 -->
    <h2 class="page-title">仪表盘</h2>

    <!-- 统计卡片区域：一行四个卡片，使用 el-row 和 el-col 布局 -->
    <!-- :gutter="20" 设置卡片之间的间距为 20px -->
    <el-row :gutter="20" class="stat-row">
      <!-- 循环渲染统计卡片，v-for 遍历 statCards 数组 -->
      <!-- :span="6" 表示每个卡片占 6 列宽度（总共 24 列，一行显示 4 个） -->
      <el-col :span="6" v-for="card in statCards" :key="card.title">
        <!-- shadow="hover" 鼠标悬停时显示阴影效果 -->
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card-content">
            <!-- 统计数值 -->
            <div class="stat-value">{{ card.value }}</div>
            <!-- 统计标题 -->
            <div class="stat-label">{{ card.title }}</div>
          </div>
          <!-- 统计图标，使用 Element Plus 图标 -->
          <el-icon :size="40" :color="card.color">
            <component :is="card.icon" />
          </el-icon>
        </el-card>
      </el-col>
    </el-row>

    <!-- 设备状态表格区域 -->
    <el-card class="section-card" shadow="never">
      <!-- 卡片头部插槽，显示标题和操作按钮 -->
      <template #header>
        <div class="section-header">
          <span>设备状态概览</span>
        </div>
      </template>
      <!-- 设备状态表格，:data 绑定数据源，stripe 开启斑马纹 -->
      <el-table :data="deviceStatusList" stripe style="width: 100%">
        <!-- 设备名称列 -->
        <el-table-column prop="name" label="设备名称" min-width="150" />
        <!-- 设备状态列，使用 el-tag 显示状态标签 -->
        <el-table-column prop="status" label="状态" width="120">
          <!-- 使用插槽自定义渲染，row 是当前行的数据对象 -->
          <template #default="{ row }">
            <!-- 根据状态值显示不同颜色的标签 -->
            <el-tag :type="row.status === '在线' ? 'success' : 'danger'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 绑定住户列 -->
        <el-table-column prop="resident" label="绑定住户" width="150" />
        <!-- 电量列，使用 el-progress 进度条显示 -->
        <el-table-column prop="battery" label="电量" width="150">
          <template #default="{ row }">
            <!-- :percentage 绑定电量百分比 -->
            <!-- :status="'exception'" 在电量低于 20% 时显示红色 -->
            <el-progress
              :percentage="row.battery"
              :status="row.battery < 20 ? 'exception' : ''"
              :stroke-width="15"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 健康数据概览区域 -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="section-header">
          <span>住户健康概览</span>
        </div>
      </template>
      <!-- 健康数据表格 -->
      <el-table :data="healthDataList" stripe style="width: 100%">
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="heartRate" label="心率(次/分)" width="120" />
        <el-table-column prop="bloodPressure" label="血压(mmHg)" width="150" />
        <el-table-column prop="temperature" label="体温(°C)" width="120" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === '正常' ? 'success' : 'danger'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
// 引入 Mock API 中的获取仪表盘数据函数
import { getDashboardStats, getDeviceStatusList, getHealthDataList } from '@/api/mock'

// 统计卡片数据，包含标题、数值、图标和颜色
const statCards = ref([])
// 设备状态列表
const deviceStatusList = ref([])
// 健康数据列表
const healthDataList = ref([])

// 页面加载时获取数据
onMounted(async () => {
  try {
    // 并行请求三个接口，提高加载速度
    const [stats, devices, health] = await Promise.all([
      getDashboardStats(),
      getDeviceStatusList(),
      getHealthDataList()
    ])
    // 将返回的数据赋值给响应式变量
    statCards.value = stats
    deviceStatusList.value = devices
    healthDataList.value = health
  } catch (error) {
    console.error('加载仪表盘数据失败:', error)
  }
})
</script>

<style scoped>
/* 页面标题样式 */
.page-title {
  font-size: 22px;
  color: #303133;
  margin-bottom: 20px;
}

/* 统计卡片行样式 */
.stat-row {
  margin-bottom: 20px;
}

/* 统计卡片样式 */
.stat-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px;
}

/* 统计卡片内容区域 */
.stat-card-content {
  display: flex;
  flex-direction: column;
}

/* 统计数值样式 */
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

/* 统计标签样式 */
.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

/* 区域卡片样式 */
.section-card {
  margin-bottom: 20px;
}

/* 区域头部样式 */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: bold;
  font-size: 16px;
}
</style>
```

**代码说明：**

- `Promise.all([...])` 并行发起三个 API 请求，比串行请求快三倍，这是前端性能优化的常用技巧
- `v-for="card in statCards"` 循环渲染统计卡片，数据驱动视图，新增卡片只需修改数据源
- `el-table-column` 的 `prop` 属性绑定数据对象的字段名，`label` 属性是列标题
- 插槽 `#default="{ row }"` 自定义列渲染，`row` 是当前行的数据对象，可以访问 `row.name`、`row.status` 等属性
- `el-progress` 进度条组件，`:status="'exception'"` 在电量低于 20% 时显示红色警告

### 3.10 src/views/DeviceList.vue —— 设备管理页面

```vue
<template>
  <div class="device-list">
    <!-- 页面标题 -->
    <h2 class="page-title">设备管理</h2>

    <!-- 搜索区域卡片 -->
    <el-card class="search-card" shadow="never">
      <!-- 内联表单，搜索条件在一行显示 -->
      <el-form :inline="true" :model="searchForm" size="default">
        <!-- 设备名称搜索 -->
        <el-form-item label="设备名称">
          <el-input
            v-model="searchForm.name"
            placeholder="请输入设备名称"
            clearable
          />
        </el-form-item>
        <!-- 设备状态筛选 -->
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable>
            <el-option label="全部" value="" />
            <el-option label="在线" value="在线" />
            <el-option label="离线" value="离线" />
            <el-option label="故障" value="故障" />
          </el-select>
        </el-form-item>
        <!-- 搜索和重置按钮 -->
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 设备列表卡片 -->
    <el-card class="table-card" shadow="never">
      <!-- 卡片头部，包含标题和新增按钮 -->
      <template #header>
        <div class="table-header">
          <span>设备列表</span>
          <el-button type="primary" @click="showAddDialog = true">
            新增设备
          </el-button>
        </div>
      </template>

      <!-- 设备表格，v-loading 绑定加载状态 -->
      <el-table :data="deviceList" stripe v-loading="loading" style="width: 100%">
        <!-- 设备编号列 -->
        <el-table-column prop="id" label="设备编号" width="180" />
        <!-- 设备名称列 -->
        <el-table-column prop="name" label="设备名称" min-width="150" />
        <!-- 设备类型列 -->
        <el-table-column prop="type" label="类型" width="120" />
        <!-- 状态列，使用标签显示 -->
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <!-- 绑定住户列 -->
        <el-table-column prop="resident" label="绑定住户" width="120" />
        <!-- 电量列 -->
        <el-table-column prop="battery" label="电量(%)" width="100">
          <template #default="{ row }">
            <el-progress
              :percentage="row.battery"
              :status="row.battery < 20 ? 'exception' : ''"
            />
          </template>
        </el-table-column>
        <!-- 操作列 -->
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, prev, pager, next, jumper"
        @current-change="loadDeviceList"
        class="pagination"
      />
    </el-card>

    <!-- 新增/编辑设备对话框 -->
    <el-dialog
      v-model="showAddDialog"
      title="新增设备"
      width="500px"
    >
      <!-- 对话框中的表单 -->
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="设备名称">
          <el-input v-model="addForm.name" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="addForm.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="心率手环" value="心率手环" />
            <el-option label="跌倒检测器" value="跌倒检测器" />
            <el-option label="定位胸牌" value="定位胸牌" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定住户">
          <el-input v-model="addForm.resident" placeholder="请输入绑定住户姓名" />
        </el-form-item>
      </el-form>
      <!-- 对话框底部按钮 -->
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">确认添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
// 引入 Mock API 中的设备相关函数
import { getDeviceList, addDevice, deleteDevice } from '@/api/mock'

// 设备列表数据
const deviceList = ref([])
// 加载状态
const loading = ref(false)
// 新增对话框的显示/隐藏状态
const showAddDialog = ref(false)

// 搜索表单数据
const searchForm = reactive({
  name: '',    // 设备名称搜索关键字
  status: ''   // 设备状态筛选
})

// 分页数据
const pagination = reactive({
  page: 1,       // 当前页码
  pageSize: 10,  // 每页条数
  total: 0       // 总记录数
})

// 新增设备表单数据
const addForm = reactive({
  name: '',      // 设备名称
  type: '',      // 设备类型
  resident: ''   // 绑定住户
})

// 根据状态返回对应的标签类型
const statusType = (status) => {
  const map = {
    '在线': 'success',
    '离线': 'info',
    '故障': 'danger'
  }
  return map[status] || 'info'
}

// 加载设备列表
const loadDeviceList = async () => {
  loading.value = true
  try {
    // 调用 Mock API 获取设备列表
    const result = await getDeviceList({
      name: searchForm.name,
      status: searchForm.status,
      page: pagination.page,
      pageSize: pagination.pageSize
    })
    // 更新设备列表和分页数据
    deviceList.value = result.list
    pagination.total = result.total
  } catch (error) {
    console.error('加载设备列表失败:', error)
    ElMessage.error('加载设备列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索按钮点击处理
const handleSearch = () => {
  pagination.page = 1  // 搜索时重置到第一页
  loadDeviceList()
}

// 重置按钮点击处理
const handleReset = () => {
  searchForm.name = ''
  searchForm.status = ''
  handleSearch()
}

// 编辑设备按钮点击处理
const handleEdit = (row) => {
  // 将当前行数据填充到表单中
  addForm.name = row.name
  addForm.type = row.type
  addForm.resident = row.resident
  // 打开对话框
  showAddDialog.value = true
}

// 确认添加设备
const handleAdd = async () => {
  try {
    // 调用 Mock API 添加设备
    await addDevice({
      name: addForm.name,
      type: addForm.type,
      resident: addForm.resident
    })
    // 关闭对话框
    showAddDialog.value = false
    // 重置表单
    addForm.name = ''
    addForm.type = ''
    addForm.resident = ''
    // 重新加载列表
    loadDeviceList()
    // 提示成功信息
    ElMessage.success('添加设备成功')
  } catch (error) {
    ElMessage.error('添加设备失败')
  }
}

// 删除设备
const handleDelete = (row) => {
  // 使用 Element Plus 的确认对话框，确认后才执行删除
  ElMessageBox.confirm(
    `确定要删除设备 "${row.name}" 吗？`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      // 调用 Mock API 删除设备
      await deleteDevice(row.id)
      // 重新加载列表
      loadDeviceList()
      ElMessage.success('删除成功')
    } catch (error) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {
    // 用户点击取消，不做任何操作
  })
}

// 页面加载时获取设备列表
onMounted(() => {
  loadDeviceList()
})
</script>

<style scoped>
/* 页面标题样式 */
.page-title {
  font-size: 22px;
  color: #303133;
  margin-bottom: 20px;
}

/* 搜索卡片样式 */
.search-card {
  margin-bottom: 20px;
}

/* 表格卡片样式 */
.table-card {
  margin-bottom: 20px;
}

/* 表格头部样式 */
.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 分页组件样式 */
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
```

**代码说明：**

- `el-form :inline="true"` 使表单元素在一行内水平排列，适合搜索条件
- `el-select` 下拉选择框，`v-model` 绑定选中的值，`clearable` 属性显示清除按钮
- `el-dialog` 弹窗组件，`v-model` 控制显示/隐藏，`title` 设置标题
- `ElMessageBox.confirm()` 返回一个 Promise，用户点击确定时 resolve，点击取消时 reject
- `fixed="right"` 固定操作列在表格右侧，方便在水平滚动时始终可见
- 新增和编辑共用同一个对话框，通过 `showAddDialog` 控制显隐
- 分页组件的 `@current-change` 事件监听页码变化，触发重新加载数据

### 3.11 src/views/ResidentList.vue —— 住户管理页面

```vue
<template>
  <div class="resident-list">
    <!-- 页面标题 -->
    <h2 class="page-title">住户管理</h2>

    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" size="default">
        <!-- 姓名搜索 -->
        <el-form-item label="姓名">
          <el-input
            v-model="searchForm.name"
            placeholder="请输入姓名"
            clearable
          />
        </el-form-item>
        <!-- 健康状态筛选 -->
        <el-form-item label="健康状态">
          <el-select v-model="searchForm.healthStatus" placeholder="全部" clearable>
            <el-option label="全部" value="" />
            <el-option label="正常" value="正常" />
            <el-option label="关注" value="关注" />
            <el-option label="异常" value="异常" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 住户列表 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="table-header">
          <span>住户列表</span>
        </div>
      </template>

      <el-table :data="residentList" stripe v-loading="loading" style="width: 100%">
        <!-- 姓名列 -->
        <el-table-column prop="name" label="姓名" width="120" />
        <!-- 年龄列 -->
        <el-table-column prop="age" label="年龄" width="80" />
        <!-- 性别列 -->
        <el-table-column prop="gender" label="性别" width="80" />
        <!-- 房间号列 -->
        <el-table-column prop="room" label="房间号" width="120" />
        <!-- 健康状态列 -->
        <el-table-column prop="healthStatus" label="健康状态" width="120">
          <template #default="{ row }">
            <el-tag :type="healthTagType(row.healthStatus)">
              {{ row.healthStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 心率列 -->
        <el-table-column prop="heartRate" label="心率(次/分)" width="120" />
        <!-- 血压列 -->
        <el-table-column prop="bloodPressure" label="血压(mmHg)" width="140" />
        <!-- 紧急联系人和电话列 -->
        <el-table-column prop="contact" label="紧急联系人" width="120" />
        <el-table-column prop="phone" label="联系电话" min-width="150" />
        <!-- 操作列 -->
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">
              查看
            </el-button>
            <el-button type="success" link size="small" @click="handleEdit(row)">
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, prev, pager, next, jumper"
        @current-change="loadResidentList"
        class="pagination"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
// 引入 Mock API 中的获取住户列表函数
import { getResidentList } from '@/api/mock'

// 住户列表数据
const residentList = ref([])
// 加载状态
const loading = ref(false)

// 搜索表单
const searchForm = reactive({
  name: '',           // 姓名搜索关键字
  healthStatus: ''    // 健康状态筛选
})

// 分页数据
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

// 根据健康状态返回对应的标签颜色类型
const healthTagType = (status) => {
  const map = {
    '正常': 'success',
    '关注': 'warning',
    '异常': 'danger'
  }
  return map[status] || 'info'
}

// 加载住户列表
const loadResidentList = async () => {
  loading.value = true
  try {
    const result = await getResidentList({
      name: searchForm.name,
      healthStatus: searchForm.healthStatus,
      page: pagination.page,
      pageSize: pagination.pageSize
    })
    residentList.value = result.list
    pagination.total = result.total
  } catch (error) {
    console.error('加载住户列表失败:', error)
    ElMessage.error('加载住户列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.page = 1
  loadResidentList()
}

// 重置
const handleReset = () => {
  searchForm.name = ''
  searchForm.healthStatus = ''
  handleSearch()
}

// 查看住户详情
const handleView = (row) => {
  ElMessage.info(`查看住户：${row.name}`)
}

// 编辑住户信息
const handleEdit = (row) => {
  ElMessage.info(`编辑住户：${row.name}`)
}

// 页面加载时获取数据
onMounted(() => {
  loadResidentList()
})
</script>

<style scoped>
/* 与 DeviceList.vue 样式保持一致，保证页面风格统一 */
.page-title {
  font-size: 22px;
  color: #303133;
  margin-bottom: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
```

**代码说明：**

- 与 DeviceList 类似的结构，但展示的是住户信息而不是设备信息
- `healthTagType` 函数根据健康状态返回对应的标签颜色：正常（绿色）、关注（橙色）、异常（红色）
- `handleView` 和 `handleEdit` 目前只是简单的提示，实际项目中会跳转到详情页或打开编辑对话框

### 3.12 src/stores/user.js —— 用户状态管理（Pinia Store）

```javascript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
// 引入 Mock API 中的登录函数
import { login as loginApi } from '@/api/mock'

// defineStore 定义 Pinia Store
// 第一个参数 'user' 是 Store 的唯一标识，在 Vue DevTools 中会显示这个名称
// 第二个参数是 Setup 函数，返回 state、getters 和 actions
export const useUserStore = defineStore('user', () => {
  // ========== state（状态） ==========
  // 使用 ref 定义响应式状态，类似组件的 setup() 中的 ref
  // 用户名，从 localStorage 中读取初始值，刷新页面后状态保持
  const username = ref(localStorage.getItem('username') || '')
  // 登录令牌，从 localStorage 中读取初始值
  const token = ref(localStorage.getItem('token') || '')

  // ========== getters（计算属性） ==========
  // 使用 computed 定义派生状态
  // isLoggedIn 根据 token 是否存在判断是否已登录
  const isLoggedIn = computed(() => !!token.value)

  // ========== actions（操作方法） ==========
  // 登录方法
  const login = async (user, pwd) => {
    // 调用 Mock API 的 login 接口
    // 模拟后端验证，任意用户名和密码都返回成功
    const result = await loginApi(user, pwd)
    // 将返回的 token 和用户名保存到 state
    token.value = result.token
    username.value = result.username
    // 持久化到 localStorage，刷新页面后状态不丢失
    localStorage.setItem('token', result.token)
    localStorage.setItem('username', result.username)
  }

  // 退出登录方法
  const logout = () => {
    // 清空 state
    username.value = ''
    token.value = ''
    // 清除 localStorage
    localStorage.removeItem('token')
    localStorage.removeItem('username')
  }

  // 返回所有 state、getters 和 actions，供组件使用
  return { username, token, isLoggedIn, login, logout }
})
```

**代码说明：**

- `defineStore('user', () => { ... })` 使用 Setup 语法定义 Store，这是 Pinia 推荐的 Composition API 风格
- `ref()` 定义响应式状态，`computed()` 定义派生状态，与 Vue 组件的 setup 函数完全一致
- `localStorage` 用于持久化登录状态，即使刷新页面 token 也不会丢失
- `login` 和 `logout` 是 actions，负责修改 state 并同步到 localStorage
- 组件中通过 `useUserStore()` 获取 Store 实例，直接访问 `store.username`、`store.isLoggedIn` 等属性

### 3.13 src/stores/device.js —— 设备状态管理（Pinia Store）

```javascript
import { defineStore } from 'pinia'
import { ref } from 'vue'

// 设备管理相关的 Store，用于缓存设备列表和搜索条件
export const useDeviceStore = defineStore('device', () => {
  // ========== state ==========
  // 缓存的设备列表，避免重复请求 API
  const deviceList = ref([])
  // 当前设备总数
  const totalCount = ref(0)

  // ========== actions ==========
  // 设置设备列表缓存
  const setDeviceList = (list, total) => {
    deviceList.value = list
    totalCount.value = total
  }

  // 清空设备缓存
  const clearDeviceList = () => {
    deviceList.value = []
    totalCount.value = 0
  }

  return { deviceList, totalCount, setDeviceList, clearDeviceList }
})
```

**代码说明：**

- 这个 Store 用于缓存设备列表数据，避免在页面切换时重复请求 API
- 通常在搜索或翻页时，先更新本地缓存再更新 UI，提升用户体验
- 实际项目中，还可以在这里维护搜索条件、排序状态等

### 3.14 src/api/mock.js —— Mock API 服务

```javascript
/**
 * Mock API 服务
 *
 * 本文件模拟后端 API 接口，返回假数据。
 * 在真实项目中，这些接口会调用 Spring Boot 后端的 REST API。
 * 使用 Mock 服务的目的是让前端开发不依赖后端，可以独立开发和调试。
 */

// ========== 模拟延迟函数 ==========
// 模拟网络延迟，让操作有"真实感"
// 同时也能测试前端加载状态的显示效果
const delay = (ms = 500) => new Promise(resolve => setTimeout(resolve, ms))

// ========== 模拟数据存储 ==========
// 模拟数据库中的设备数据
let mockDevices = [
  { id: 'DEV001', name: '心率手环-A01', type: '心率手环', status: '在线', resident: '张三', battery: 85 },
  { id: 'DEV002', name: '跌倒检测器-B01', type: '跌倒检测器', status: '在线', resident: '李四', battery: 72 },
  { id: 'DEV003', name: '定位胸牌-C01', type: '定位胸牌', status: '离线', resident: '王五', battery: 15 },
  { id: 'DEV004', name: '心率手环-A02', type: '心率手环', status: '在线', resident: '赵六', battery: 90 },
  { id: 'DEV005', name: '跌倒检测器-B02', type: '跌倒检测器', status: '故障', resident: '孙七', battery: 45 },
  { id: 'DEV006', name: '心率手环-A03', type: '心率手环', status: '在线', resident: '周八', battery: 60 },
  { id: 'DEV007', name: '定位胸牌-C02', type: '定位胸牌', status: '在线', resident: '吴九', battery: 88 },
  { id: 'DEV008', name: '心率手环-A04', type: '心率手环', status: '离线', resident: '郑十', battery: 8 },
  { id: 'DEV009', name: '跌倒检测器-B03', type: '跌倒检测器', status: '在线', resident: '陈一', battery: 55 },
  { id: 'DEV010', name: '定位胸牌-C03', type: '定位胸牌', status: '在线', resident: '林二', battery: 95 },
  { id: 'DEV011', name: '心率手环-A05', type: '心率手环', status: '在线', resident: '黄三', battery: 78 },
  { id: 'DEV012', name: '跌倒检测器-B04', type: '跌倒检测器', status: '在线', resident: '刘四', battery: 82 }
]

// 模拟数据库中的住户数据
let mockResidents = [
  { id: 1, name: '张三', age: 78, gender: '男', room: '101', healthStatus: '正常', heartRate: 72, bloodPressure: '128/85', contact: '张小明', phone: '13800138001' },
  { id: 2, name: '李四', age: 82, gender: '女', room: '102', healthStatus: '关注', heartRate: 88, bloodPressure: '145/92', contact: '李小红', phone: '13800138002' },
  { id: 3, name: '王五', age: 75, gender: '男', room: '103', healthStatus: '异常', heartRate: 95, bloodPressure: '160/100', contact: '王小刚', phone: '13800138003' },
  { id: 4, name: '赵六', age: 80, gender: '女', room: '104', healthStatus: '正常', heartRate: 70, bloodPressure: '120/80', contact: '赵大伟', phone: '13800138004' },
  { id: 5, name: '孙七', age: 85, gender: '男', room: '201', healthStatus: '正常', heartRate: 68, bloodPressure: '130/85', contact: '孙丽华', phone: '13800138005' },
  { id: 6, name: '周八', age: 79, gender: '女', room: '202', healthStatus: '关注', heartRate: 82, bloodPressure: '140/88', contact: '周建国', phone: '13800138006' },
  { id: 7, name: '吴九', age: 73, gender: '男', room: '203', healthStatus: '正常', heartRate: 75, bloodPressure: '125/82', contact: '吴美玲', phone: '13800138007' },
  { id: 8, name: '郑十', age: 88, gender: '女', room: '301', healthStatus: '异常', heartRate: 92, bloodPressure: '155/95', contact: '郑志强', phone: '13800138008' }
]

// ========== API 接口 ==========

/**
 * 登录接口
 * 模拟后端登录验证，任意用户名和密码都返回成功
 * @param {string} username - 用户名
 * @param {string} password - 密码
 * @returns {Promise<{token: string, username: string}>}
 */
export const login = async (username, password) => {
  await delay(800)  // 模拟 800ms 网络延迟
  // 模拟返回 token 和用户名
  return {
    token: 'mock-token-' + Date.now(),  // 生成一个伪 token
    username: username                    // 返回传入的用户名
  }
}

/**
 * 获取仪表盘统计数据
 * 返回四个统计卡片的数值和图标配置
 * @returns {Promise<Array>} 统计卡片数组
 */
export const getDashboardStats = async () => {
  await delay(300)
  return [
    { title: '在线设备', value: 12, icon: 'Monitor', color: '#67C23A' },
    { title: '入住老人', value: 8, icon: 'User', color: '#409EFF' },
    { title: '今日告警', value: 2, icon: 'Warning', color: '#F56C6C' },
    { title: '待处理', value: 1, icon: 'Clock', color: '#E6A23C' }
  ]
}

/**
 * 获取设备状态概览列表（仪表盘使用）
 * 返回所有设备的状态信息，用于仪表盘中的设备状态表格
 * @returns {Promise<Array>} 设备状态列表
 */
export const getDeviceStatusList = async () => {
  await delay(300)
  // 从 mockDevices 中选取前 5 条作为概览数据
  return mockDevices.slice(0, 5).map(d => ({
    name: d.name,
    status: d.status,
    resident: d.resident,
    battery: d.battery
  }))
}

/**
 * 获取健康数据概览列表（仪表盘使用）
 * 返回所有住户的健康数据，用于仪表盘中的健康概览表格
 * @returns {Promise<Array>} 健康数据列表
 */
export const getHealthDataList = async () => {
  await delay(300)
  return mockResidents.map(r => ({
    name: r.name,
    heartRate: r.heartRate,
    bloodPressure: r.bloodPressure,
    temperature: (36 + Math.random() * 1.5).toFixed(1),  // 随机生成体温 36.0-37.5
    status: r.healthStatus
  }))
}

/**
 * 获取设备列表（分页）
 * 支持按名称搜索和按状态筛选
 * @param {Object} params - 查询参数
 * @param {string} params.name - 设备名称（模糊搜索）
 * @param {string} params.status - 设备状态筛选
 * @param {number} params.page - 当前页码
 * @param {number} params.pageSize - 每页条数
 * @returns {Promise<{list: Array, total: number}>} 设备列表和总数
 */
export const getDeviceList = async (params) => {
  await delay(500)
  // 根据搜索条件过滤数据
  let filtered = [...mockDevices]
  if (params.name) {
    // 按名称模糊搜索，indexOf 判断是否包含关键字
    filtered = filtered.filter(d => d.name.includes(params.name))
  }
  if (params.status) {
    // 按状态精确筛选
    filtered = filtered.filter(d => d.status === params.status)
  }
  // 计算总数
  const total = filtered.length
  // 分页处理：根据页码和每页条数截取数据
  const start = (params.page - 1) * params.pageSize
  const end = start + params.pageSize
  const list = filtered.slice(start, end)
  // 返回分页后的数据
  return { list, total }
}

/**
 * 添加设备
 * 将新设备添加到模拟数据数组中
 * @param {Object} data - 设备数据
 * @param {string} data.name - 设备名称
 * @param {string} data.type - 设备类型
 * @param {string} data.resident - 绑定住户
 * @returns {Promise<{success: boolean}>}
 */
export const addDevice = async (data) => {
  await delay(500)
  // 生成新设备对象，ID 自动递增
  const newDevice = {
    id: 'DEV' + String(mockDevices.length + 1).padStart(3, '0'),  // 生成 DEV013、DEV014...
    name: data.name,
    type: data.type,
    status: '在线',  // 新设备默认在线
    resident: data.resident,
    battery: 100     // 新设备默认满电
  }
  // 将新设备添加到数组最前面
  mockDevices.unshift(newDevice)
  return { success: true }
}

/**
 * 删除设备
 * 根据设备 ID 从模拟数据中移除
 * @param {string} id - 设备编号
 * @returns {Promise<{success: boolean}>}
 */
export const deleteDevice = async (id) => {
  await delay(300)
  // 根据 ID 过滤掉要删除的设备
  mockDevices = mockDevices.filter(d => d.id !== id)
  return { success: true }
}

/**
 * 获取住户列表（分页）
 * 支持按姓名搜索和按健康状态筛选
 * @param {Object} params - 查询参数
 * @param {string} params.name - 姓名（模糊搜索）
 * @param {string} params.healthStatus - 健康状态筛选
 * @param {number} params.page - 当前页码
 * @param {number} params.pageSize - 每页条数
 * @returns {Promise<{list: Array, total: number}>} 住户列表和总数
 */
export const getResidentList = async (params) => {
  await delay(500)
  // 根据搜索条件过滤
  let filtered = [...mockResidents]
  if (params.name) {
    filtered = filtered.filter(r => r.name.includes(params.name))
  }
  if (params.healthStatus) {
    filtered = filtered.filter(r => r.healthStatus === params.healthStatus)
  }
  // 计算总数
  const total = filtered.length
  // 分页处理
  const start = (params.page - 1) * params.pageSize
  const end = start + params.pageSize
  const list = filtered.slice(start, end)
  return { list, total }
}
```

**代码说明：**

- `delay()` 函数模拟网络延迟，让操作有真实的加载感，同时测试前端的 loading 状态
- `mockDevices` 和 `mockResidents` 是模拟数据库，数据存储在内存中，刷新页面后重置
- 所有 API 函数都返回 Promise，与真实 Axios 调用的返回格式一致
- 分页逻辑在 Mock 中实现：根据 `page` 和 `pageSize` 截取数组片段
- 添加和删除操作直接修改 `mockDevices` 数组，模拟后端的增删改查

### 3.15 src/api/request.js —— Axios 实例封装

```javascript
/**
 * Axios 请求封装
 *
 * 在真实项目中，这个文件会配置 Axios 的 baseURL、请求拦截器（注入 token）、
 * 响应拦截器（统一处理错误）等。
 * 在本文的演示项目中，我们直接使用 Mock API，所以这个文件作为一个备用。
 * 当后端接口开发完成后，可以将 Mock API 替换为真实的 Axios 调用。
 */

import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 创建 Axios 实例
const request = axios.create({
  // 基础 URL，通过环境变量配置
  // 开发环境使用代理到本地的 Spring Boot 后端
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  // 请求超时时间：15 秒
  timeout: 15000,
  // 请求头
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器：在每次请求前自动注入 token
request.interceptors.request.use(
  (config) => {
    // 从 localStorage 获取 token
    const token = localStorage.getItem('token')
    if (token) {
      // 在请求头中添加 Authorization 字段
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一处理响应和错误
request.interceptors.response.use(
  (response) => {
    // 直接返回响应数据，调用方通过 .then(data => ...) 获取
    return response.data
  },
  (error) => {
    // HTTP 错误处理
    if (error.response) {
      const status = error.response.status
      switch (status) {
        case 401:
          // Token 过期或未登录，清除 token 并跳转到登录页
          localStorage.removeItem('token')
          router.push('/login')
          ElMessage.error('登录已过期，请重新登录')
          break
        case 403:
          ElMessage.error('无权限访问')
          break
        case 500:
          ElMessage.error('服务器内部错误')
          break
        default:
          ElMessage.error(`请求失败: ${error.message}`)
      }
    } else {
      ElMessage.error('网络连接失败，请检查网络')
    }
    return Promise.reject(error)
  }
)

export default request
```

**代码说明：**

- `axios.create()` 创建 Axios 实例，配置 baseURL、timeout 等公共参数
- 请求拦截器在每个请求发出前自动注入 token，开发者无需在每个请求中手动添加
- 响应拦截器统一处理 HTTP 错误，401 自动跳转登录页，避免每个页面都写一遍错误处理逻辑
- 在演示项目中，这个文件不会被直接使用（因为使用的是 Mock API），但提供了真实项目中的标准封装模式

---

## 四、运行验证

### 4.1 初始化项目

首先确保你的电脑上安装了 Node.js 16 或以上版本。打开终端，进入项目目录，执行以下命令安装依赖：

```bash
# 进入项目目录
cd hello-admin

# 安装所有依赖
npm install
```

`npm install` 会根据 `package.json` 中的配置下载所有依赖包到 `node_modules` 目录。如果网络较慢，可以使用国内的 npm 镜像源：

```bash
npm install --registry=https://registry.npmmirror.com
```

### 4.2 启动开发服务器

```bash
# 启动 Vite 开发服务器
npm run dev
```

看到以下输出表示启动成功：

```
  VITE v5.0.0  ready in 520ms

  Local:   http://localhost:3000/
  Network: http://192.168.x.x:3000/
```

浏览器会自动打开 `http://localhost:3000/`（如果未自动打开，请手动访问）。

### 4.3 体验功能

**第一步：登录系统**

打开页面后，会看到登录界面。输入任意用户名和密码（演示模式下无需验证），点击"登录"按钮。系统会模拟 800ms 的网络延迟，然后跳转到仪表盘页面。

**第二步：查看仪表盘**

登录成功后进入仪表盘，可以看到：
- 顶部四个统计卡片：在线设备数、入住老人数、今日告警数、待处理数
- 设备状态概览表格：显示设备名称、状态、绑定住户和电量
- 住户健康概览表格：显示住户的心率、血压、体温和健康状态

**第三步：管理设备**

点击左侧菜单的"设备管理"，进入设备列表页面：
- 可以使用搜索框按设备名称搜索，或按下拉菜单按状态筛选
- 表格支持分页，每页显示 10 条记录
- 点击"新增设备"按钮，填写设备信息后添加新设备
- 点击每行末尾的"删除"按钮，确认后删除设备

**第四步：查看住户**

点击左侧菜单的"住户管理"，查看住户信息列表，支持按姓名搜索和按健康状态筛选。

### 4.4 验证 Mock API 的独立性

为了验证整个应用不依赖后端，可以尝试：
1. 断开网络连接
2. 刷新页面，重新登录
3. 所有功能仍然正常工作

这是因为所有数据都由 `src/api/mock.js` 提供，数据存储在浏览器内存中，不依赖任何后端服务。

---

## 五、项目对照：与 zznursing 真实管理后台的对比

本文搭建的示例管理后台是 zznursing 项目真实管理后台的"最小可行版本"。两者在功能完整度、技术复杂度上存在较大差距，以下是对比分析：

### 5.1 数据表格的复杂度差异

示例中的表格使用了基础的分页和搜索功能。在 zznursing 的真实管理后台中，`el-table` 的使用更加复杂：

- **多条件排序**：设备列表支持按设备名称、状态、最后在线时间等多字段排序，排序状态通过 URL 参数持久化，刷新页面不丢失
- **列自定义**：用户可以通过拖拽调整列的顺序和宽度，可以选择显示/隐藏某些列，偏好设置保存在 localStorage 中
- **批量操作**：支持批量选中设备，进行批量删除、批量绑定住户、批量 OTA 升级等操作
- **行内编辑**：部分字段可以直接在表格中点击编辑，无需打开弹窗，类似于 Excel 的编辑体验

### 5.2 图表可视化的差异

示例中健康数据以表格形式展示。zznursing 的真实管理后台集成了 ECharts 图表库，提供更丰富的可视化能力：

- **健康趋势折线图**：展示单个住户 24 小时的心率变化趋势，X 轴为时间，Y 轴为心率值，异常点用红色标记
- **设备分布饼图**：展示各类设备（心率手环、跌倒检测器、定位胸牌）的数量占比
- **告警热力图**：展示各楼层/区域的告警频次分布，帮助管理者快速定位问题高发区域
- **运营数据大屏**：在大屏显示器上展示全院运营数据概览，包含实时设备状态、入住率、告警统计等

### 5.3 权限控制的差异

示例中只有登录/未登录两种状态。zznursing 的真实管理后台实现了完整的 RBAC（基于角色的访问控制）权限模型：

- **角色体系**：管理员（admin）、护工（nurse）、院长（director）等角色，每个角色拥有不同的菜单和操作权限
- **按钮级权限**：使用自定义指令 `v-permission` 控制按钮级别的显示隐藏，例如普通护工看不到"删除设备"按钮
- **数据权限**：不同角色看到的数据范围不同，如护工只能看到自己负责的楼层住户数据，院长可以看到全院数据
- **动态路由**：根据用户角色动态生成可访问的路由表，未授权的路由不会注册到 Vue Router 中

### 5.4 后端集成的差异

示例使用 Mock API 模拟数据。zznursing 的真实管理后台与 Spring Boot 后端进行真实 API 集成：

- **RESTful API**：前端通过 Axios 调用后端的 REST API，请求格式遵循 RESTful 规范
- **Token 认证**：使用 JWT Token 进行身份认证，Token 过期后自动刷新
- **WebSocket 实时推送**：告警通知、设备状态变化等通过 WebSocket 实时推送到前端，无需手动刷新
- **文件上传**：住户头像、体检报告等文件通过 MinIO 对象存储上传和管理

### 5.5 表单验证和用户体验的差异

- **表单验证**：真实项目中使用 Element Plus 的表单验证规则 + 后端校验双重保障，确保数据质量
- **加载骨架屏**：页面加载时显示骨架屏（Skeleton），而不是简单的 loading 动画，提升用户体验
- **错误边界**：全局错误处理，API 调用失败时显示友好的错误页面，而不是白屏或控制台报错
- **操作确认**：删除、批量操作等敏感操作使用二次确认弹窗，防止误操作

---

## 六、面试实战：3 道面试题 + 回答框架

### 面试题 1：Vue 3 的 Composition API 相比 Options API 有哪些优势？在实际项目中如何选择？

**考察点：** 面试官想考察候选人对 Vue 3 核心设计理念的理解，以及在实际项目中合理选型的能力。

**回答框架：**

- **背景**：Vue 3 引入了 Composition API（组合式 API），在原有 Options API（选项式 API）之外提供了一种新的逻辑组织方式。两者不是替代关系，而是互补关系。

- **方案**：Composition API 的核心优势有三点：
  1. **逻辑聚合**：Options API 将同一功能的代码分散到 `data`、`methods`、`computed`、`watch` 四个选项中，当一个组件有多个功能时，阅读和维护困难。Composition API 通过 `setup()` 函数将同一功能的代码聚合在一起。
  2. **逻辑复用**：Options API 的 mixins 存在命名冲突和来源不明的问题。Composition API 可以将逻辑提取为 `useXxx()` 组合函数，在多个组件中复用，且来源清晰。
  3. **更好的 TypeScript 支持**：Composition API 天然支持类型推导，无需额外的类型声明。

- **深度（选型策略）**：
  - 简单组件（如展示型按钮、标签）：Options API 完全够用，代码更简洁
  - 复杂组件（如设备管理表格、多步骤表单）：Composition API 更适合，逻辑清晰
  - 需要复用逻辑的场景：必须使用 Composition API，提取为组合函数
  - 团队有 Vue 2 迁移需求：建议全面使用 Composition API，减少迁移成本

- **扩展**：在实际项目中，我们通常混合使用两种 API。Element Plus 组件本身使用 Options API 编写，而业务组件使用 Composition API。这种混合使用在 Vue 3 中是完全支持的。

### 面试题 2：Element Plus 的 el-table 组件如何实现复杂的表格功能？请举例说明。

**考察点：** 面试官想考察候选人对 Element Plus 组件库的掌握程度，以及处理复杂表格场景的经验。

**回答框架：**

- **背景**：在中后台管理系统中，表格是最核心的 UI 组件。Element Plus 的 `el-table` 提供了丰富的功能，可以满足大部分企业级表格需求。

- **方案**：常见的复杂表格功能及实现方式：
  1. **多列排序**：设置 `el-table-column` 的 `sortable` 属性，或使用 `sort-change` 事件自定义排序逻辑
  2. **列筛选**：使用 `filters` 和 `filter-method` 实现列级别的数据筛选
  3. **自定义列模板**：通过插槽 `#default="{ row }"` 自定义列的渲染内容，如将状态值渲染为标签
  4. **固定列**：设置 `fixed` 属性固定列（如操作列固定在右侧），方便在水平滚动时操作
  5. **展开行**：使用 `type="expand"` 实现行的展开，显示更多详情信息
  6. **多选行**：设置 `type="selection"` 实现复选框多选，配合批量操作

- **深度（性能优化）**：当表格数据量较大时（超过 1000 行），`el-table` 的虚拟滚动可以帮助优化性能。通过设置 `el-table` 的 `height` 属性和 `virtual-scroll` 属性，表格只渲染可视区域内的行，大幅减少 DOM 节点数量。

- **扩展**：在 zznursing 项目中，设备管理表格使用了行内编辑（点击单元格直接编辑），结合 `el-input` 和 `el-select` 的插槽渲染，实现了类似 Excel 的编辑体验，减少了弹窗的使用频率。

### 面试题 3：Pinia 相比 Vuex 有哪些改进？请结合项目经验说明。

**考察点：** 面试官想考察候选人对状态管理方案的理解，以及从 Vuex 迁移到 Pinia 的实际经验。

**回答框架：**

- **背景**：Pinia 是 Vue 的官方状态管理库，在 Vue 3 生态中完全替代了 Vuex。Pinia 的核心理念是"更简单、更类型安全"，它在保留 Vuex 核心概念的基础上做了大量简化。

- **方案**：Pinia 相比 Vuex 的主要改进：
  1. **去掉了 mutations**：Vuex 需要区分同步（mutations）和异步（actions），导致代码冗余。Pinia 统一用 actions，直接修改 state，大幅减少样板代码。
  2. **完整的 TypeScript 支持**：Vuex 需要手动声明类型，Pinia 自动推导类型，无需额外类型定义。
  3. **模块化更简单**：Vuex 使用 modules 嵌套，命名空间需要手动配置。Pinia 每个 Store 独立定义，天然支持模块化。
  4. **体积更小**：Pinia 的 API 更简洁，体积约 1KB，比 Vuex 小很多。
  5. **DevTools 支持更好**：Pinia 内置 Vue DevTools 集成，支持时间旅行调试和状态快照。

- **深度（代码对比）**：以登录状态管理为例，Vuex 需要定义 state、mutations、actions 三个文件或同一个文件中的三个部分；而 Pinia 只需要一个 `defineStore` 调用，所有逻辑在 setup 函数中组织，代码量减少约 40%。

- **扩展**：在实际项目中，建议按业务模块划分 Store，每个模块一个独立的 Store 文件。例如：`user.js` 管理用户登录状态，`device.js` 管理设备数据，`alert.js` 管理告警信息。这种组织方式比 Vuex 的 modules 嵌套更清晰，且每个 Store 都可以独立测试。

---

## 总结

本文从零搭建了一个 Vue 3 + Vite + Element Plus 的养老平台管理后台，涵盖了以下知识点：

1. **Vue 3 Composition API**：使用 `ref`、`reactive`、`computed`、`onMounted` 等 API 组织组件逻辑
2. **Vite 构建工具**：配置开发服务器、路径别名，体验毫秒级热更新
3. **Element Plus 组件库**：使用 `el-table`、`el-form`、`el-dialog`、`el-card` 等组件搭建页面
4. **Vue Router**：配置路由表、懒加载、路由守卫实现登录鉴权
5. **Pinia 状态管理**：定义 Store 管理用户状态和设备数据
6. **Mock API 服务**：不依赖后端即可完成前端开发和测试

对照 zznursing 的真实管理后台，本文示例在功能完整度上还有较大差距，但覆盖了管理后台最核心的页面骨架和交互模式。读者可以在此基础上继续扩展，逐步实现权限控制、图表可视化、文件上传等高级功能。

在下一篇文章中，我们将深入分析 zznursing 项目的 MySQL + Redis 数据存储设计，学习如何设计 IoT 场景下的时序数据模型和缓存策略。

---

## 参考资料

- [Vue 3 官方文档](https://vuejs.org/) — Composition API、响应式系统、组件开发
- [Vite 官方文档](https://vitejs.dev/) — 构建配置、开发服务器、生产打包
- [Element Plus 官方文档](https://element-plus.org/) — 所有组件的使用指南和 API 参考
- [Pinia 官方文档](https://pinia.vuejs.org/) — Store 定义、状态管理、插件开发
- [Vue Router 官方文档](https://router.vuejs.org/) — 路由配置、导航守卫、动态路由
- [Axios 官方文档](https://axios-http.com/) — 请求配置、拦截器、错误处理
- [zznursing 项目源码](https://github.com/1byteone/zznursing) — 完整的管理后台源码