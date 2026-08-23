# 迷你项目：带网关+限流+监控的微服务演示

## 项目描述

搭建一个基于 Spring Cloud Alibaba 的微型博客系统，包含网关、限流、监控等核心基础设施组件，演示微服务架构中服务注册发现、统一网关、流量控制和可观测性的完整集成。

**技术栈：** Spring Boot 3.x + Spring Cloud Alibaba 2023.x + Nacos + Sentinel + Gateway + Prometheus + Grafana

## 架构图

```text
                            ┌──────────────────────────────┐
                            │         Nginx (80)            │
                            │     反向代理 / 负载均衡        │
                            └──────────┬───────────────────┘
                                       │
                                       ▼
                            ┌──────────────────────────────┐
                            │    Spring Cloud Gateway       │
                            │  (路由转发 / 鉴权 / 限流)      │
                            └──┬───────────────┬───────────┘
                               │               │
                     ┌─────────▼──────┐  ┌─────▼────────────┐
                     │  user-service  │  │ article-service  │
                     │   (用户服务)    │  │   (文章服务)      │
                     │  端口: 8081    │  │   端口: 8082     │
                     └────────┬───────┘  └────────┬─────────┘
                              │                   │
                              └────────┬──────────┘
                                       │
                                       ▼
                            ┌──────────────────────────────┐
                            │           Nacos               │
                            │  注册中心 + 配置中心 (8848)    │
                            └──────────────────────────────┘

    ┌───────────────────────────────────────────────────────────────┐
    │                        监控体系                                │
    │  ┌────────────┐    ┌────────────┐    ┌─────────────────┐     │
    │  │ Prometheus  │───▶│  Grafana   │    │  Alertmanager   │     │
    │  │  端口:9090  │    │  端口:3000 │    │   端口:9093     │     │
    │  └────────────┘    └────────────┘    └─────────────────┘     │
    └───────────────────────────────────────────────────────────────┘
```

## 服务列表

| 服务名 | 端口 | 描述 | 关键依赖 |
|--------|------|------|----------|
| `gateway` | 8080 | API 网关，路由转发、鉴权、限流 | Nacos, Sentinel |
| `user-service` | 8081 | 用户服务，注册登录、用户信息 CRUD | Nacos, MySQL |
| `article-service` | 8082 | 文章服务，文章发布、列表查询 | Nacos, MySQL |
| `nacos` | 8848 | 注册中心 + 配置中心 | - |
| `prometheus` | 9090 | 指标采集 | 各服务 /actuator/prometheus |
| `grafana` | 3000 | 监控面板 | Prometheus |
| `sentinel-dashboard` | 8858 | Sentinel 控制台 | Nacos |

## 启动步骤

### 方式一：Docker Compose 一键启动

```yaml
# docker-compose.yml
version: '3.8'
services:
  nacos:
    image: nacos/nacos-server:v2.3.2
    ports:
      - "8848:8848"
    environment:
      MODE: standalone

  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root123
    volumes:
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql

  prometheus:
    image: prom/prometheus:v2.52.0
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:10.4.3
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin

  sentinel-dashboard:
    image: bladex/sentinel-dashboard:1.8.8
    ports:
      - "8858:8858"

  gateway:
    build: ./gateway
    ports:
      - "8080:8080"
    depends_on:
      - nacos

  user-service:
    build: ./user-service
    ports:
      - "8081:8081"
    depends_on:
      - nacos
      - mysql

  article-service:
    build: ./article-service
    ports:
      - "8082:8082"
    depends_on:
      - nacos
      - mysql
```

**启动命令：**
```bash
# 一键启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f gateway

# 停止所有服务
docker-compose down
```

### 方式二：本地 IDE 启动

```bash
# 1. 启动基础设施（Docker）
docker run -d --name nacos -p 8848:8848 -e MODE=standalone nacos/nacos-server:v2.3.2
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root123 mysql:8.0

# 2. 启动监控
docker run -d --name prometheus -p 9090:9090 -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus

# 3. 启动应用（IDE 中依次启动）
#    gateway → user-service → article-service
```

## 关键配置示例

### Gateway 路由配置（application.yml）

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/user/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userKeyResolver}"
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20

        - id: article-service
          uri: lb://article-service
          predicates:
            - Path=/api/article/**
          filters:
            - StripPrefix=1

      default-filters:
        - name: Hystrix
          args:
            name: default
            fallbackUri: forward:/fallback
```

### Sentinel 限流配置（sentinel-rule.json）

```json
[
  {
    "resource": "GET:/api/article/list",
    "controlBehavior": 0,
    "count": 100,
    "grade": 1,
    "limitApp": "default",
    "strategy": 0
  },
  {
    "resource": "createArticle",
    "count": 50,
    "grade": 1,
    "limitApp": "default",
    "strategy": 0
  }
]
```

### Prometheus 采集配置（prometheus.yml）

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['gateway:8080']

  - job_name: 'user-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['user-service:8081']

  - job_name: 'article-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['article-service:8082']
```

## 验证方法

### 1. 验证服务注册

```bash
# 查看 Nacos 注册的服务列表
curl http://localhost:8848/nacos/v1/ns/service/list

# 预期输出包含 gateway, user-service, article-service
```

### 2. 验证网关路由

```bash
# 通过网关调用用户服务
curl http://localhost:8080/api/user/health

# 通过网关调用文章服务
curl http://localhost:8080/api/article/health
```

### 3. 验证 Sentinel 限流

```bash
# 快速请求触发限流（使用 ab 压测）
ab -n 200 -c 20 http://localhost:8080/api/article/list

# 预期：部分请求返回 429 Too Many Requests
curl -v http://localhost:8080/api/article/list
# 响应头中应包含 X-RateLimit-Limit 等限流信息
```

### 4. 查看 Prometheus 指标

```bash
# 查看 Prometheus 是否正常采集
curl http://localhost:9090/api/v1/targets

# 查询 JVM 内存指标
curl 'http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes'

# 查询请求计数
curl 'http://localhost:9090/api/v1/query?query=http_server_requests_seconds_count'
```

### 5. 查看 Grafana 面板

```bash
# 浏览器访问 Grafana
open http://localhost:3000
# 默认账号密码：admin / admin

# 添加 Prometheus 数据源（URL: http://prometheus:9090）
# 导入 JVM 面板（推荐 ID: 4701，Spring Boot 面板 ID: 12900）
```

### 6. 验证 Sentinel 控制台

```bash
# 浏览器访问 Sentinel 控制台
open http://localhost:8858
# 默认账号密码：sentinel / sentinel
# 可查看实时监控、配置限流规则
```