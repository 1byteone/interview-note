# Vue 3 管理后台

> zznursing 项目管理后台使用 Vue 3 + Element Plus + Vite 构建，面向养老机构运营人员，提供设备管理、健康监测、告警处理、人员管理等核心功能。

---

## 一、Vue 3 Composition API 架构

### 1.1 项目结构

```
zznursing-admin/
├── src/
│   ├── api/                    # API 接口封装
│   │   ├── device.ts           # 设备管理接口
│   │   ├── health.ts           # 健康数据接口
│   │   ├── alert.ts            # 告警管理接口
│   │   ├── elderly.ts          # 老人管理接口
│   │   └── system.ts           # 系统管理接口
│   ├── components/             # 公共组件
│   │   ├── DeviceStatusTag.vue  # 设备状态标签
│   │   ├── HealthChart.vue      # 健康数据图表
│   │   ├── AlertNotify.vue      # 告警通知弹窗
│   │   └── ElderlySelector.vue  # 老人选择器
│   ├── composables/            # 组合式函数
│   │   ├── useDeviceData.ts    # 设备数据逻辑
│   │   ├── useWebSocket.ts     # WebSocket 连接
│   │   └── useAuth.ts          # 登录鉴权
│   ├── router/                 # 路由配置
│   │   └── index.ts
│   ├── stores/                 # Pinia 状态管理
│   │   ├── device.ts           # 设备状态
│   │   ├── alert.ts            # 告警状态
│   │   └── user.ts             # 用户状态
│   ├── views/                  # 页面视图
│   │   ├── dashboard/          # 仪表盘
│   │   ├── device/             # 设备管理
│   │   ├── health/             # 健康监测
│   │   ├── alert/              # 告警中心
│   │   ├── elderly/            # 老人管理
│   │   └── system/             # 系统设置
│   ├── utils/                  # 工具函数
│   │   ├── request.ts          # Axios 封装
│   │   └── format.ts           # 格式化工具
│   ├── App.vue
│   └── main.ts
├── public/
├── index.html
├── vite.config.ts
├── package.json
└── tsconfig.json
```

### 1.2 项目配置

```json
// package.json —— 核心依赖
{
  "name": "zznursing-admin",
  "version": "1.0.0",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.2.0",
    "pinia": "^2.1.0",
    "element-plus": "^2.5.0",
    "@element-plus/icons-vue": "^2.3.0",
    "axios": "^1.6.0",
    "echarts": "^5.4.0",
    "vue-echarts": "^6.6.0",
    "dayjs": "^1.11.0",
    "lodash-es": "^4.17.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "vite": "^5.0.0",
    "vue-tsc": "^1.8.0",
    "typescript": "^5.3.0",
    "sass": "^1.69.0"
  }
}
```

```typescript
// vite.config.ts —— Vite 构建配置
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  // 路径别名
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  // 开发服务器配置
  server: {
    port: 3000,
    // 代理后端 API 请求，解决跨域问题
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  },
  // 构建配置
  build: {
    outDir: 'dist',
    // 生产环境移除 console
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    }
  }
})
```

---

## 二、关键页面组件

### 2.1 仪表盘页面

```vue
<!-- DashboardView.vue —— 运营仪表盘首页 -->
<template>
  <div class="dashboard">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="20">
      <el-col :span="6" v-for="card in statCards" :key="card.title">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__content">
            <div class="stat-card__value">{{ card.value }}</div>
            <div class="stat-card__title">{{ card.title }}</div>
          </div>
          <el-icon :size="48" class="stat-card__icon" :color="card.color">
            <component :is="card.icon" />
          </el-icon>
        </el-card>
      </el-col>
    </el-row>

    <!-- 实时告警列表 -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="section-header">
          <span>实时告警</span>
          <el-tag type="danger" v-if="recentAlerts.length">
            未处理 {{ recentAlerts.length }}
          </el-tag>
        </div>
      </template>
      <el-table :data="recentAlerts" stripe style="width: 100%">
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column prop="elderlyName" label="老人" width="100" />
        <el-table-column prop="alertType" label="告警类型" width="120">
          <template #default="{ row }">
            <el-tag :type="alertTypeTag(row.alertType)">
              {{ row.alertType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'pending' ? 'danger' : 'success'">
              {{ row.status === 'pending' ? '待处理' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleAlert(row)">
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 健康趋势图表 -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="section-header">
          <span>今日健康趋势</span>
          <el-radio-group v-model="healthChartType" size="small">
            <el-radio-button value="heartRate">心率</el-radio-button>
            <el-radio-button value="bloodPressure">血压</el-radio-button>
            <el-radio-button value="temperature">体温</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <v-chart :option="healthChartOption" style="height: 300px" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardStats, getRecentAlerts } from '@/api/dashboard'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// 注册 ECharts 组件
use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const healthChartType = ref('heartRate')
const recentAlerts = ref([])

// 统计卡片数据
const statCards = ref([
  { title: '在线设备', value: 128, icon: 'Monitor', color: '#67C23A' },
  { title: '入住老人', value: 86, icon: 'User', color: '#409EFF' },
  { title: '今日告警', value: 3, icon: 'Warning', color: '#F56C6C' },
  { title: '待处理', value: 2, icon: 'Clock', color: '#E6A23C' }
])

// 健康趋势图表配置
const healthChartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: {
    type: 'category',
    data: ['06:00', '08:00', '10:00', '12:00', '14:00', '16:00', '18:00', '20:00']
  },
  yAxis: { type: 'value' },
  series: [{
    data: [72, 75, 78, 74, 80, 76, 72, 70],
    type: 'line',
    smooth: true,
    areaStyle: { opacity: 0.3 }
  }]
}))

// 告警类型标签映射
const alertTypeTag = (type: string) => {
  const map: Record<string, string> = {
    '心率异常': 'danger',
    '跌倒检测': 'danger',
    '设备离线': 'warning',
    '电量不足': 'info'
  }
  return map[type] || 'info'
}

// 处理告警
const handleAlert = (row: any) => {
  router.push('/alert')
}

// 页面加载时获取数据
onMounted(async () => {
  try {
    const stats = await getDashboardStats()
    statCards.value = stats.cardData
    recentAlerts.value = await getRecentAlerts()
  } catch (error) {
    console.error('加载仪表盘数据失败', error)
  }
})
</script>

<style lang="scss" scoped>
.dashboard {
  padding: 20px;

  .stat-card {
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    justify-content: space-between;

    &__content {
      display: flex;
      flex-direction: column;
    }

    &__value {
      font-size: 32px;
      font-weight: bold;
      color: #303133;
    }

    &__title {
      font-size: 14px;
      color: #909399;
      margin-top: 4px;
    }

    &__icon {
      position: absolute;
      right: 20px;
      top: 50%;
      transform: translateY(-50%);
    }
  }

  .section-card {
    margin-bottom: 20px;
  }

  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}
</style>
```

### 2.2 设备管理页面

```vue
<!-- DeviceManagement.vue —— 设备管理页面 -->
<template>
  <div class="device-management">
    <!-- 搜索与操作栏 -->
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="设备ID">
          <el-input v-model="searchForm.deviceId" placeholder="请输入设备ID" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部">
            <el-option label="全部" value="" />
            <el-option label="在线" value="online" />
            <el-option label="离线" value="offline" />
            <el-option label="故障" value="fault" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="searchForm.type" placeholder="全部">
            <el-option label="全部" value="" />
            <el-option label="心率手环" value="heart_band" />
            <el-option label="跌倒检测器" value="fall_detector" />
            <el-option label="定位胸牌" value="locator" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 设备列表 -->
    <el-card shadow="never" class="device-table">
      <template #header>
        <div class="table-header">
          <span>设备列表</span>
          <el-button type="primary" @click="handleRegister">注册新设备</el-button>
        </div>
      </template>
      <el-table :data="deviceList" stripe v-loading="loading">
        <el-table-column prop="deviceId" label="设备ID" min-width="180" />
        <el-table-column prop="deviceName" label="名称" width="150" />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <DeviceStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="battery" label="电量" width="100">
          <template #default="{ row }">
            <el-progress
              :percentage="row.battery"
              :status="row.battery < 20 ? 'exception' : ''"
              :stroke-width="12"
            />
          </template>
        </el-table-column>
        <el-table-column prop="lastOnlineTime" label="最后在线" width="170" />
        <el-table-column prop="elderlyName" label="绑定老人" width="120" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDetail(row)">详情</el-button>
            <el-button type="warning" link @click="showCommand(row)">命令</el-button>
            <el-button type="danger" link @click="handleUnbind(row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, prev, pager, next, jumper"
        @change="loadDeviceList"
        class="pagination"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getDeviceList, registerDevice } from '@/api/device'
import DeviceStatusTag from '@/components/DeviceStatusTag.vue'

const loading = ref(false)
const deviceList = ref([])

// 搜索表单
const searchForm = reactive({
  deviceId: '',
  status: '',
  type: ''
})

// 分页
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// 加载设备列表
const loadDeviceList = async () => {
  loading.value = true
  try {
    const result = await getDeviceList({
      ...searchForm,
      page: pagination.page,
      size: pagination.size
    })
    deviceList.value = result.records
    pagination.total = result.total
  } catch (error) {
    console.error('加载设备列表失败', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadDeviceList()
}

const handleReset = () => {
  searchForm.deviceId = ''
  searchForm.status = ''
  searchForm.type = ''
  handleSearch()
}

const handleRegister = () => {
  // 打开注册新设备对话框
}

const showDetail = (row: any) => {
  // 跳转设备详情页
}

const showCommand = (row: any) => {
  // 打开命令下发对话框
}

const handleUnbind = (row: any) => {
  // 确认解绑操作
}

onMounted(() => {
  loadDeviceList()
})
</script>

<style lang="scss" scoped>
.device-management {
  padding: 20px;

  .search-bar {
    margin-bottom: 20px;
  }

  .table-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
```

---

## 三、API 封装

### 3.1 Axios 请求封装

```typescript
// src/utils/request.ts —— Axios 封装
import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 创建 Axios 实例
const service: AxiosInstance = axios.create({
  // 基础 URL，通过 Vite 环境变量配置
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  // 请求超时时间：15 秒
  timeout: 15000,
  // 请求头
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器：在每个请求中注入 Token
service.interceptors.request.use(
  (config: AxiosRequestConfig) => {
    // 从 localStorage 获取 Token
    const token = localStorage.getItem('token')
    if (token) {
      // 在请求头中添加 Authorization
      config.headers = config.headers || {}
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一处理响应和错误
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data

    // 业务逻辑错误处理
    if (res.code && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res
  },
  (error) => {
    // HTTP 错误处理
    if (error.response) {
      const status = error.response.status

      switch (status) {
        case 401:
          // Token 过期或未登录，跳转到登录页
          localStorage.removeItem('token')
          router.push('/login')
          ElMessage.error('登录已过期，请重新登录')
          break
        case 403:
          ElMessage.error('无权限访问')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
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

// 导出封装的请求方法
export default service
```

### 3.2 设备管理 API

```typescript
// src/api/device.ts —— 设备管理 API 封装
import request from '@/utils/request'

// 设备管理接口基础路径
const BASE_URL = '/v1/device'

// 设备数据类型定义
export interface DeviceInfo {
  deviceId: string      // 设备ID
  deviceName: string    // 设备名称
  type: string          // 设备类型
  status: string        // 设备状态
  battery: number       // 电量百分比
  lastOnlineTime: string // 最后在线时间
  elderlyName: string   // 绑定老人姓名
}

export interface DeviceListParams {
  deviceId?: string     // 设备ID（模糊搜索）
  status?: string       // 状态筛选
  type?: string         // 类型筛选
  page: number          // 页码
  size: number          // 每页条数
}

export interface DeviceListResult {
  records: DeviceInfo[] // 设备列表
  total: number         // 总记录数
  page: number          // 当前页码
  size: number          // 每页条数
}

/**
 * 获取设备列表（分页）
 */
export function getDeviceList(params: DeviceListParams): Promise<DeviceListResult> {
  return request.get(`${BASE_URL}/list`, { params })
}

/**
 * 获取设备详情
 */
export function getDeviceDetail(deviceId: string): Promise<DeviceInfo> {
  return request.get(`${BASE_URL}/${deviceId}`)
}

/**
 * 注册新设备到 IoTDA 平台
 */
export function registerDevice(data: {
  deviceName: string
  type: string
  productId: string
}): Promise<{ deviceId: string; secret: string }> {
  return request.post(`${BASE_URL}/register`, data)
}

/**
 * 解绑设备
 */
export function unbindDevice(deviceId: string): Promise<void> {
  return request.delete(`${BASE_URL}/${deviceId}/bind`)
}

/**
 * 下发设备命令
 */
export function sendCommand(deviceId: string, command: {
  commandName: string
  params: Record<string, any>
}): Promise<any> {
  return request.post(`${BASE_URL}/${deviceId}/command`, command)
}
```

---

## 四、路由权限管理

```typescript
// src/router/index.ts —— 路由配置
import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

// 路由表
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', noAuth: true }  // 不需要登录
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '仪表盘', roles: ['admin', 'operator'] }
      },
      {
        path: 'device',
        name: 'Device',
        component: () => import('@/views/device/DeviceManagement.vue'),
        meta: { title: '设备管理', roles: ['admin', 'operator'] }
      },
      {
        path: 'health',
        name: 'Health',
        component: () => import('@/views/health/HealthMonitor.vue'),
        meta: { title: '健康监测', roles: ['admin', 'operator', 'nurse'] }
      },
      {
        path: 'alert',
        name: 'Alert',
        component: () => import('@/views/alert/AlertCenter.vue'),
        meta: { title: '告警中心', roles: ['admin', 'operator', 'nurse'] }
      },
      {
        path: 'elderly',
        name: 'Elderly',
        component: () => import('@/views/elderly/ElderlyManagement.vue'),
        meta: { title: '老人管理', roles: ['admin'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：权限校验
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = `${to.meta.title} - 智能养老管理平台`

  // 检查是否需要登录
  if (to.meta.noAuth) {
    next()
    return
  }

  // 检查 Token
  const token = localStorage.getItem('token')
  if (!token) {
    next('/login')
    return
  }

  // 检查角色权限
  const userRole = localStorage.getItem('userRole')
  const requiredRoles = to.meta.roles as string[]
  if (requiredRoles && !requiredRoles.includes(userRole || '')) {
    next('/dashboard')
    return
  }

  next()
})

export default router
```

---

## 五、面试题

### 问题 1：Vue 3 vs Vue 2 核心区别

**主要区别：**

| 维度 | Vue 2 | Vue 3 |
|------|-------|-------|
| **响应式原理** | Object.defineProperty（无法监听数组索引和属性新增） | Proxy（可监听任意属性变化，性能更好） |
| **API 风格** | Options API（data/methods/computed 分散） | Composition API（按逻辑聚合，更好的复用性） |
| **TypeScript** | 支持有限，需要额外装饰器 | 原生 TypeScript 支持，类型推导更完善 |
| **性能** | 虚拟 DOM 全量比较 | 静态标记 + Patch Flag，更新性能提升 1.3-2 倍 |
| **Tree Shaking** | 不支持按需引入 | 支持，未使用的 API 不打包 |

### 问题 2：Pinia 状态管理方案

**Pinia 核心特性：**

1. **组合式 API**：支持 Composition API 风格的 Store 定义，代码更简洁
2. **TypeScript 友好**：完整的类型推导，无需额外类型声明
3. **模块化**：每个 Store 独立定义，无需 modules 嵌套
4. **DevTools 支持**：内置 Vue DevTools 集成，支持时间旅行调试
5. **轻量级**：相比 Vuex 体积更小，API 更简洁

### 问题 3：路由权限控制方案

**实现方案：**

1. **路由元信息**：在路由 `meta.roles` 中定义可访问的角色
2. **路由守卫**：`router.beforeEach` 中检查 Token 和角色权限
3. **动态路由**：根据用户角色动态添加路由（`router.addRoute`）
4. **菜单权限**：根据角色生成可访问的菜单列表，未授权菜单不渲染
5. **按钮级权限**：使用自定义指令 `v-permission` 控制按钮级别的显示隐藏