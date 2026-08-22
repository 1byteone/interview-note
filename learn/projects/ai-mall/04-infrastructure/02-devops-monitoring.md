# 02 · CI/CD 与监控告警

> 本文展示 AI 智能商城的 DevOps 实践：从代码提交到自动部署的 CI/CD 流水线，以及基于 Prometheus + Grafana 的监控告警体系。这是基础设施技术栈的集中实践。

---

## 一、CI/CD 流水线

### 1.1 流水线架构

```
开发者提交代码 (Git)
    │
    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  GitLab CI/CD 流水线                                                      │
│                                                                          │
│  Stage 1: 代码检查 (Code Quality)                                         │
│  ├── Lint (ESLint/Pylint/Checkstyle)                                     │
│  ├── 单元测试 (JUnit/Pytest)                                             │
│  └── 代码覆盖率 (JaCoCo/Pytest-cov) > 80%                                │
│                                                                          │
│  Stage 2: 构建 (Build)                                                   │
│  ├── Maven 编译 (Java 服务)                                              │
│  ├── pip 安装 (Python 服务)                                              │
│  └── Docker 镜像构建 (所有服务)                                           │
│                                                                          │
│  Stage 3: 集成测试 (Integration Test)                                     │
│  ├── Docker Compose 启动全部服务                                          │
│  ├── API 契约测试 (Spring Cloud Contract)                                │
│  └── E2E 测试 (Selenium/Playwright)                                      │
│                                                                          │
│  Stage 4: 部署 (Deploy)                                                  │
│  ├── staging 环境部署 (自动)                                              │
│  ├── 验收测试 (Smoke Test)                                               │
│  └── production 环境部署 (手动审批)                                      │
└──────────────────────────────────────────────────────────────────────────┘
```

### 1.2 GitLab CI 配置

```yaml
# .gitlab-ci.yml
stages:
  - test
  - build
  - integration
  - deploy

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
  DOCKER_REGISTRY: registry.example.com/ai-mall

# ========== Stage 1: 测试 ==========
unit-test:
  stage: test
  image: maven:3.9-eclipse-temurin-17
  script:
    - mvn test -pl mall-user-service,mall-order-service,mall-product-service
    - mvn jacoco:report
  artifacts:
    reports:
      junit: "**/target/surefire-reports/*.xml"
      coverage_report:
        path: "**/target/site/jacoco/"
  coverage: '/Total.*?([0-9]{1,3})%/'

python-test:
  stage: test
  image: python:3.12
  script:
    - cd ai-backend
    - pip install -e ".[dev]"
    - pytest --cov=src --cov-report=term --cov-report=xml
  artifacts:
    reports:
      junit: "**/test-results/*.xml"
      cobertura: "**/coverage.xml"

# ========== Stage 2: 构建 ==========
build-java:
  stage: build
  image: maven:3.9-eclipse-temurin-17
  script:
    - mvn package -DskipTests -B
    - for service in mall-gateway mall-user-service mall-order-service; do
        docker build -t $DOCKER_REGISTRY/$service:$CI_COMMIT_SHORT_SHA ./$service;
        docker push $DOCKER_REGISTRY/$service:$CI_COMMIT_SHORT_SHA;
      done

build-python:
  stage: build
  image: docker:24
  services:
    - docker:dind
  script:
    - docker build -t $DOCKER_REGISTRY/ai-search-gateway:$CI_COMMIT_SHORT_SHA ./ai-backend/mall-micro-ai-search
    - docker push $DOCKER_REGISTRY/ai-search-gateway:$CI_COMMIT_SHORT_SHA

# ========== Stage 3: 集成测试 ==========
integration-test:
  stage: integration
  image: docker/compose:latest
  services:
    - docker:dind
  script:
    - docker-compose -f docker-compose.test.yml up -d
    - sleep 30  # 等待服务启动
    - ./scripts/test-api.sh  # API 测试脚本
    - docker-compose -f docker-compose.test.yml down

# ========== Stage 4: 部署 ==========
deploy-staging:
  stage: deploy
  image: alpine:latest
  script:
    - apk add --no-cache openssh-client
    - ssh deploy@staging-server "docker stack deploy -c docker-compose.staging.yml ai-mall"
  only:
    - develop

deploy-production:
  stage: deploy
  image: alpine:latest
  script:
    - apk add --no-cache openssh-client
    - ssh deploy@prod-server "docker stack deploy -c docker-compose.prod.yml ai-mall"
  only:
    - master
  when: manual  # 手动触发，需要审批
  environment:
    name: production
    url: https://mall.example.com
```

---

## 二、监控告警体系

### 2.1 监控架构

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        Prometheus 监控体系                                │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Java 服务     │  │ Python 服务   │  │ 中间件       │  │ 基础设施     │  │
│  │ (Micrometer) │  │ (prometheus- │  │ (Exporter)   │  │ (Node Ex-)  │  │
│  │              │  │  client)     │  │              │  │  porter)    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                 │                 │          │
│         └─────────────────┼─────────────────┼─────────────────┘          │
│                           │                 │                            │
│                           ▼                 ▼                            │
│                    ┌─────────────────────────────────┐                   │
│                    │         Prometheus 服务          │                   │
│                    │  拉取指标 · 存储 · 告警规则       │                   │
│                    └───────────────┬─────────────────┘                   │
│                                    │                                    │
│                    ┌───────────────┴─────────────────┐                   │
│                    │                                  │                   │
│                    ▼                                  ▼                   │
│           ┌──────────────────┐             ┌──────────────────┐          │
│           │  Grafana 可视化   │             │  AlertManager    │          │
│           │  仪表盘 · 监控大屏 │             │  告警通知        │          │
│           └──────────────────┘             └────────┬─────────┘          │
│                                                     │                    │
│                                   ┌─────────────────┼──────────┐        │
│                                   ▼                 ▼          ▼        │
│                              ┌────────┐      ┌────────┐   ┌────────┐   │
│                              │ 邮件    │      │ 钉钉    │   │ 短信    │   │
│                              └────────┘      └────────┘   └────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
```

### 2.2 关键监控指标

| 指标类别 | 指标 | 告警阈值 | 说明 |
|---------|------|---------|------|
| **业务指标** | 订单创建 QPS | > 1000 | 限流触发 |
| | AI 搜索延迟 P95 | > 5s | 用户体验下降 |
| | 支付成功率 | < 99% | 支付链路异常 |
| | 商品搜索平均响应时间 | > 500ms | 搜索服务异常 |
| **系统指标** | CPU 使用率 | > 80% | 资源瓶颈 |
| | 内存使用率 | > 85% | OOM 风险 |
| | 磁盘使用率 | > 90% | 扩容或清理 |
| | GC 暂停时间 | > 1s | JVM 参数优化 |
| **中间件** | MySQL 连接数 | > 200 | 连接池不够 |
| | Redis 内存使用率 | > 80% | 内存淘汰风险 |
| | RocketMQ 堆积 | > 10000 | 消费能力不足 |
| **AI 服务** | LLM 调用失败率 | > 5% | 供应商问题 |
| | Embedding 调用延迟 | > 2s | 网络/模型问题 |
| | Token 消耗/小时 | 预算上限 | 成本控制 |

### 2.3 Prometheus 告警规则

```yaml
# prometheus/alerts.yml
groups:
  - name: ai-mall-alerts
    rules:
      # 服务可用性告警
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        annotations:
          summary: "{{ $labels.instance }} 服务不可用"
          description: "{{ $labels.job }} 已经宕机超过 1 分钟"

      # AI 搜索延迟告警
      - alert: AiSearchHighLatency
        expr: histogram_quantile(0.95, rate(ai_search_duration_seconds_bucket[5m])) > 5
        for: 5m
        annotations:
          summary: "AI 搜索 P95 延迟超过 5 秒"
          description: "当前 P95 延迟：{{ $value }}s"

      # LLM 调用失败率告警
      - alert: LlmFailureRateHigh
        expr: rate(llm_call_failed_total[5m]) / rate(llm_call_total[5m]) > 0.05
        for: 3m
        annotations:
          summary: "LLM 调用失败率超过 5%"
          description: "当前失败率：{{ $value | humanizePercentage }}"

      # 订单积压告警
      - alert: RocketMQBacklog
        expr: rocketmq_messages_behind > 10000
        for: 5m
        annotations:
          summary: "RocketMQ 消息堆积超过 10000"
          description: "当前堆积数量：{{ $value }}"

      # GC 暂停告警
      - alert: JvmGcPauseTime
        expr: jvm_gc_pause_seconds_max > 1
        for: 1m
        annotations:
          summary: "JVM GC 暂停超过 1 秒"
          description: "{{ $labels.instance }} GC 暂停时间：{{ $value }}s"
```

### 2.4 Grafana 仪表盘

```
仪表盘 1: 业务监控大屏
├── 订单实时交易额 (折线图)
├── 用户活跃数 (实时计数)
├── AI 搜索调用量 (柱状图)
├── 支付成功率 (仪表盘)
└── Top 10 搜索关键词 (表格)

仪表盘 2: 系统资源监控
├── CPU 使用率 (按服务分组)
├── 内存使用率 (按服务分组)
├── 网络 IO (折线图)
├── JVM 堆内存 (GC 区域)
└── 磁盘使用率 (饼图)

仪表盘 3: AI 服务监控
├── LLM 调用延迟 (P50/P95/P99)
├── Token 消耗趋势 (面积图)
├── Embedding 调用量 (柱状图)
├── Agent 执行步骤分布 (饼图)
└── 供应商切换事件 (日志)
```

---

## 三、日志收集 (ELK)

### 3.1 日志架构

```
┌──────────────────────────────────────────────────────────────────────────┐
│  ELK 日志收集体系                                                        │
│                                                                          │
│  每个服务容器 → stdout → Docker 日志驱动 → Filebeat → Logstash → ES      │
│                                                                          │
│  或：                                                                     │
│  每个服务 → 日志文件 → Filebeat → Logstash → ES → Kibana 查询             │
└──────────────────────────────────────────────────────────────────────────┘
```

### 3.2 日志规范

```java
// Java 服务日志格式（Logback）
{
  "timestamp": "2026-08-22T10:30:00.123+08:00",
  "level": "INFO",
  "service": "mall-order-service",
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userId": 10086,
  "requestId": "req-001",
  "message": "订单创建成功",
  "orderId": "2026082210001",
  "duration": 245,
  "extra": { "skuId": 1001, "quantity": 2 }
}
```

### 3.3 关键日志查询场景

```
Kibana 查询示例:

# 查询某个用户的全部请求链路
traceId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

# 查询订单服务最近的错误日志
service: "mall-order-service" AND level: "ERROR"

# 查询 AI 搜索延迟超过 5 秒的请求
service: "ai-search-gateway" AND duration: > 5000

# 查询 LLM 调用失败的请求
message: "LLM call failed" OR message: "ChatOpenAI error"
```

---

## 四、总结

| 环节 | 工具 | 配置位置 | 关键实践 |
|------|------|---------|---------|
| **CI/CD** | GitLab CI | `.gitlab-ci.yml` | 多阶段流水线，自动测试+构建+部署 |
| **镜像构建** | Docker | `Dockerfile` | 多阶段构建，减小镜像体积 |
| **服务编排** | Docker Compose | `docker-compose.yml` | 一键启动全部服务 |
| **指标收集** | Prometheus | `prometheus.yml` | 拉取 + 告警规则 |
| **可视化** | Grafana | 仪表盘配置 | 业务/系统/AI 三层监控 |
| **日志收集** | ELK | Filebeat + Logstash | 结构化日志，traceId 串联 |
| **告警通知** | AlertManager | 告警规则 | 钉钉/邮件/短信分级通知 |

---

> **下一篇：** [../05-stack-mapping/01-16-stack-map.md](../05-stack-mapping/01-16-stack-map.md) — 16 技术栈完整映射表