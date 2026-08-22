# AI 商城基础设施全景

## 基础设施架构图

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                            AI 商城基础设施架构                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────────────┐     │
│  │  Nginx   │───▶│ Gateway  │───▶│ Sentinel │───▶│   Service A      │     │
│  │ (反向代理)│    │ (路由网关)│    │ (限流熔断)│    │  (订单服务)       │     │
│  └──────────┘    └──────────┘    └──────────┘    └───────┬──────────┘     │
│        │               │               │                  │               │
│        │               │               │                  │ OpenFeign     │
│        │               │               │                  ▼               │
│        │               │               │    ┌──────────────────┐          │
│        │               │               │    │   Service B      │          │
│        │               │               │    │  (库存服务)       │          │
│        │               │               │    └───────┬──────────┘          │
│        │               │               │            │                     │
│        │               │               │            ▼                     │
│        │               │               │    ┌──────────────────┐          │
│        │               │               │    │     Seata        │          │
│        │               │               │    │  (分布式事务)     │          │
│        │               │               │    └──────────────────┘          │
│        │               │               │                                  │
│        ▼               ▼               ▼                                  │
│  ┌─────────────────────────────────────────────────────┐                  │
│  │                     Nacos                             │                 │
│  │          (注册中心 + 配置中心)                         │                 │
│  └─────────────────────────────────────────────────────┘                  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────┐                  │
│  │              监控告警体系                              │                 │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────┐       │                 │
│  │  │Prometheus│─▶│ Grafana  │  │ Alertmanager  │       │                 │
│  │  │  (采集)   │  │  (面板)   │  │   (告警)      │       │                 │
│  │  └──────────┘  └──────────┘  └──────────────┘       │                 │
│  └─────────────────────────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 服务注册链路

AI 商城的每个微服务在启动时都会自动注册到 Nacos，Gateway 通过 Nacos 实现服务发现，整体流程如下：

**服务注册流程：**
1. `order-service` 启动，携带 `spring.cloud.nacos.discovery.server-addr` 配置
2. Nacos Client 发送注册请求，包含服务名 `order-service`、IP、端口、元数据
3. Nacos Server 接收请求，将服务实例信息写入注册表（基于 Distro 协议在集群内同步）
4. 注册成功后，Nacos 返回心跳间隔（默认 5 秒），服务定期发送心跳维持健康状态
5. 服务关闭时发送 `deregister` 请求，Nacos 从注册表中移除该实例

**服务发现流程：**
1. `gateway` 启动时也注册到 Nacos，同时通过 `nacos.discovery` 拉取所有服务列表
2. 当路由规则匹配到 `lb://order-service` 时，Gateway 向 Nacos 查询健康的 `order-service` 实例
3. Nacos 返回实例列表，Gateway 使用负载均衡策略（默认轮询）选择一个实例
4. 选中的实例 IP:Port 替换路由中的服务名，完成真实请求转发

**心跳机制：**
- 每个服务每 5 秒发送一次心跳到 Nacos
- Nacos 若 15 秒未收到心跳，标记为"不健康"但不立即剔除
- 30 秒内仍未恢复，则剔除该实例

## 请求全链路追踪

一次完整的用户下单请求，从客户端到数据库，经过多个组件，追踪过程如下：

```
客户端 ──▶ Nginx ──▶ Gateway ──▶ Sentinel ──▶ order-service ──▶ Seata TC
  │                   │            │                │                │
  │                   │            │                │ OpenFeign      │
  │                   │            │                ▼                │
  │                   │            │         stock-service ──────────┘
  │                   │            │                │                │
  │                   │            │                ▼                │
  │                   │            │              MySQL              │
  ▼                   ▼            ▼                ▼                ▼
┌──────┐         ┌────────┐   ┌────────┐     ┌──────────┐     ┌──────────┐
│TraceID│═══════▶│ Span1  │══▶│ Span2  │════▶│  Span3   │════▶│  Span4   │
│生成点 │         │ Nginx  │   │Gateway │     │order-svc │     │stock-svc │
└──────┘         └────────┘   └────────┘     └──────────┘     └──────────┘
```

**全链路追踪步骤：**
1. **Nginx** 生成 TraceID（通过 `proxy_set_header` 注入），或由 Gateway 接收后生成
2. **Gateway** 创建第一个 Span，记录路由匹配耗时、请求 URI、响应状态码
3. **Sentinel** 拦截并创建流量控制 Span，记录 QPS、拒绝数、限流规则匹配结果
4. **order-service** 创建业务 Span，记录方法执行耗时、参数、异常信息
5. **OpenFeign** 调用 `stock-service` 时，通过 `RequestInterceptor` 传递 TraceID
6. **stock-service** 创建子 Span，关联到父 TraceID，记录库存扣减操作
7. **Seata TC** 创建全局事务 Span，记录 XID、分支事务状态、二阶段提交耗时
8. **MySQL** 通过 SkyWalking 或 JDBC 拦截器记录 SQL 执行 Span

**数据采集：** 推荐使用 SkyWalking Agent 或 Micrometer Tracing，通过 gRPC 上报到 OAP Server。

## 配置中心统一管理

AI 商城所有微服务的配置集中在 Nacos Config，实现统一管理：

**Nacos 配置结构：**
```
nacos-config/
├── order-service/
│   ├── order-service-dev.yaml      # 开发环境
│   ├── order-service-test.yaml     # 测试环境
│   └── order-service-prod.yaml     # 生产环境
├── stock-service/
│   ├── stock-service-dev.yaml
│   └── stock-service-prod.yaml
├── gateway/
│   └── gateway-routes.yaml         # 路由规则
└── shared/
    ├── datasource.yaml             # 数据库连接配置
    ├── redis.yaml                  # Redis 配置
    └── seata.yaml                  # Seata 配置
```

**配置变更推送流程：**
1. 运维人员在 Nacos 控制台修改配置并发布
2. Nacos Server 通知所有订阅该 DataID 的客户端（通过长轮询）
3. 客户端收到变更通知，拉取最新配置
4. Spring Cloud 自动刷新 `@RefreshScope` 标注的 Bean
5. 应用"热更新"配置，无需重启

**配置优先级：** 本地 `bootstrap.yaml` > Nacos 共享配置 > Nacos 扩展配置 > Nacos 应用配置

## 监控告警覆盖

AI 商城采用 Prometheus + Grafana + Alertmanager 构建完整的监控告警体系：

**指标采集链路：**
```
服务 JVM 指标 ──▶ Micrometer ──▶ /actuator/prometheus ──▶ Prometheus（拉取）
Nginx 指标    ──▶ nginx-exporter                          ▶ Prometheus
Gateway 指标  ──▶ 自定义 Metric                            ▶ Prometheus
Sentinel 指标 ──▶ Sentinel Metric 暴露                      ▶ Prometheus
MySQL 指标    ──▶ mysqld-exporter                          ▶ Prometheus
```

**Grafana 核心面板：**
- **JVM 面板**：堆内存、GC 次数、线程数、类加载数
- **请求面板**：QPS、P99/P95/P50 延迟、错误率
- **资源面板**：CPU 使用率、内存使用率、磁盘 IO
- **服务依赖面板**：调用拓扑图、服务间调用延迟
- **Sentinel 面板**：限流拒绝数、熔断次数、热点限流命中数

**告警规则（Alertmanager）：**
- **高延迟告警**：P99 延迟 > 500ms 持续 1 分钟，触发告警
- **错误率告警**：5xx 错误率 > 1% 持续 2 分钟，触发告警
- **服务宕机告警**：Prometheus 无法拉取 /metrics 超过 30 秒，触发告警
- **限流告警**：Sentinel 拒绝率 > 10%，触发告警
- **数据库连接告警**：连接池使用率 > 80%，触发告警

**告警通知渠道：** 企业微信机器人、钉钉机器人、邮件、PagerDuty