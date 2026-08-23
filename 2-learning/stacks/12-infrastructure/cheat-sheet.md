# 基础设施速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| Nginx 反向代理 | 接收客户端请求，转发到后端服务器，隐藏后端细节 | 代理配置别忘了 proxy_set_header Host/Real-IP，否则后端拿不到真实 IP |
| 负载均衡算法 | 轮询(默认)、weight(权重)、ip_hash(会话保持)、least_conn(最少连接)、fair(响应时间) | ip_hash 在 nginx 作为上游代理时可能失效，需注意 |
| 网关 (Gateway) | 统一入口：路由、鉴权、限流、日志、熔断 | 网关层不要做业务逻辑，只做横切关注点 |
| 网关过滤器链 | Pre 过滤器(前置处理) → 路由转发 → Post 过滤器(响应后) | 过滤器顺序决定执行顺序，@Order / setOrder 控制 |
| Nacos | 注册中心 + 配置中心，支持 AP+CP 混合模式 | 临时实例(心跳保活) vs 持久实例(Nacos 管理) 要分清 |
| Nacos 一致性 | 临时实例用 Distro(AP 最终一致)，持久实例用 Raft(CP 强一致) | 默认注册是临时实例，AP 模式，选型要看场景容忍度 |
| Sentinel | 流量控制、熔断降级、系统保护 | 规则持久化到 Nacos 推拉模式不同，注意 push 模式配置 |
| Sentinel 滑动窗口 | 时间窗口分多个小格子，统计滑动窗口内的请求 QPS | 窗口精度=窗口长度/格子数，格子数越大精度越高但内存占用大 |
| Seata AT | 自动补偿事务模式，解析 SQL 生成前后镜像，两阶段提交 | 自动生成 undo log，但多表操作/复杂 SQL 可能解析失败，需手动确认 |
| Seata TCC | Try-Confirm-Cancel 三阶段，业务方自己实现补偿逻辑 | AT 解决不了嵌套事务/跨服务复杂场景时用 TCC，但代码侵入大 |
| Prometheus | 指标采集 + 时序数据库 + 告警 | 拉取模式，目标服务需暴露 /metrics 端点 |
| 蓝绿/金丝雀 | 蓝绿两套环境切流量；金丝雀逐步放量，监控决策 | 蓝绿成本高(双倍资源)；金丝雀需要灰度路由能力 |
| CI/CD | 持续集成(自动构建测试) + 持续交付/部署 | CI 仅到制品，CD 才到生产环境，要区分清楚 |
| 监控体系 | 指标(Metrics) + 日志(Logging) + 链路追踪(Tracing) 三支柱 | 三者缺一不可，仅指标无法定位慢调用根因 |

## 🔧 常用命令/API

```nginx
# Nginx 负载均衡 + 反向代理配置模板
upstream backend {
    server 10.0.0.1:8080 weight=3 max_fails=3 fail_timeout=30s;
    server 10.0.0.2:8080 weight=1;
    keepalive 32;                              # 保持长连接
}

server {
    listen 80;
    server_name api.example.com;

    location / {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
        proxy_send_timeout 10s;

        # 限流：每 IP 10r/s，突发 20
        limit_req zone=per_ip burst=20 nodelay;
    }

    location /health {
        proxy_pass http://backend/actuator/health;
        access_log off;
    }

    # 静态资源缓存
    location /static/ {
        expires 7d;
        add_header Cache-Control "public, immutable";
    }
}

# 限流区域定义
limit_req_zone $binary_remote_addr zone=per_ip:10m rate=10r/s;
```

```yaml
# Spring Cloud Gateway 路由配置
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userKeyResolver}"
                redis-rate-limiter.replenishRate: 100    # 令牌桶速率
                redis-rate-limiter.burstCapacity: 200    # 突发容量
            - name: CircuitBreaker
              args:
                name: orderCircuitBreaker
                fallbackUri: forward:/fallback/order
```

```bash
# Nginx 常用运维命令
nginx -t                                        # 检查配置
nginx -s reload                                 # 重载配置
nginx -s stop                                   # 停止
tail -f /var/log/nginx/access.log               # 实时日志
tail -f /var/log/nginx/error.log                # 错误日志
```

```json
// Sentinel 限流规则（JSON 推送到 Nacos）
[
  {
    "resource": "GET:/api/orders",
    "limitApp": "default",
    "grade": 1,              // 0=线程数, 1=QPS
    "count": 100,
    "strategy": 0,           // 0=直接, 1=关联, 2=链路
    "controlBehavior": 0,    // 0=快速失败, 1=Warm Up, 2=排队等待
    "clusterMode": false
  }
]
```

## 🎯 面试高频 TOP10

1. **Q: Nginx 负载均衡算法有哪些？** **A:** 轮询、weight(权重)、ip_hash(会话保持)、least_conn(最少连接)、fair(后端响应时间)；upstream 配置 server 参数。
2. **Q: 网关过滤器链执行顺序？** **A:** Pre 过滤器按 order 升序 → 路由转发 → Post 过滤器按 order 降序；GlobalFilter 对所有路由生效，GatewayFilter 只对指定路由。
3. **Q: Nacos 一致性协议？** **A:** 临时实例(心跳)用 Distro 协议(AP，最终一致)；持久实例用 Raft 协议(CP，强一致)；Nacos 默认临时实例，追求 AP 高可用优先。
4. **Q: Seata AT 模式原理？** **A:** 解析 SQL 生成 before/after 镜像 → 一阶段本地事务提交并记录 undo log → 二阶段全局提交直接返回 / 全局回滚则用 undo log 逆向恢复数据。
5. **Q: Sentinel 滑动窗口如何实现？** **A:** 时间窗口分割成 N 个小格子(如1秒/500ms×2)，每个格子独立计数；请求落在当前时间格子，累加当前窗口所有格子计数；精度=窗口长度/格子数。
6. **Q: 蓝绿部署 vs 金丝雀部署？** **A:** 蓝绿两套环境全量切流量(成本高)；金丝雀逐步放量(5%→50%→100%)，配合监控决定是否全量或回滚，更精细。
7. **Q: Prometheus 拉取 vs 推送模式？** **A:** Pull 模式(主动拉取，状态检测简单，可横向扩展)；Push 适合短生命周期任务(批处理/定时任务)，用 PushGateway 桥接。
8. **Q: 限流策略有哪些？** **A:** 固定窗口、滑动窗口(更精确)、令牌桶(允许突发)、漏桶(平滑速率)；Sentinel 实现滑动窗口 + 令牌桶/漏桶，Nginx limit_req 控制 IP。
9. **Q: 熔断 vs 降级 vs 限流？** **A:** 熔断(下游故障，快速失败，防止级联)；降级(主动降低服务能力，保核心)；限流(控制入口流量，保护系统)；三者配合使用。
10. **Q: 全链路追踪怎么实现？** **A:** 每个请求生成 traceId，贯穿所有服务；span 记录每个服务处理时间/状态；Spring Cloud 用 Micrometer Tracing + OpenTelemetry + Zipkin/SkyWalking。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| Nginx 后端没配健康检查 | 主动健康检查(nginx_upstream_check_module) 或被动(fail_timeout/max_fails) |
| Gateway 过滤器里写业务逻辑 | 网关只做横切(鉴权/限流/日志)，业务逻辑在服务层 |
| Sentinel 规则只内存不持久化 | 规则持久化到 Nacos/Apollo，推模式(避免拉模式延迟) |
| Nacos 集群用 AP 但要求强一致 | 持久实例选 CP 模式，清楚业务一致性需求 |
| 限流阈值拍脑袋设 | 压测确定基线，留余量 70-80% 设置限流值 |
| 单点 Nginx 部署 | Nginx + Keepalived 双机热备 / F5 硬件 / 云负载均衡 |
| 监控只看 CPU 内存 | 全链路：延迟/P99/QPS/错误率/慢查询/GC/连接数 |
| CI/CD 没加回滚步骤 | 部署脚本必须包含回滚能力(保留历史版本) |

## 📐 架构设计要点

- **入口层**：DNS 负载 → CDN → Nginx(LB/SSL/限流) → 网关(路由/鉴权/限流) → 业务服务。
- **注册中心**：Nacos(推荐) / Eureka / Consul；健康检查 + 心跳保活，配置中心统一管理。
- **服务网格**：基础设施层下沉至 Sidecar(istio/Linkerd)，业务无感知。
- **可观测性**：Metric(Prometheus) + Log(Loki/ELK) + Trace(SkyWalking) 三位一体，告警推送到钉钉/企微。
- **容灾**：多机房部署 / 异地多活 / 数据备份 + 演练预案。

## 🔗 关联技术

- **Docker/K8s**：基础设施容器化，部署编排和自动扩缩容。
- **Spring Cloud**：微服务治理框架，Gateway/Nacos/Sentinel/Seata 是核心组件。
- **Linux**：Nginx/Sentinel/Seata 都部署在 Linux 上，内核参数影响性能。
- **Redis**：Sentinel 的限流计数器、Gateway 的令牌桶都依赖 Redis。
- **MySQL**：Seata 事务日志、Nacos 存储持久化数据(MySQL 模式)。