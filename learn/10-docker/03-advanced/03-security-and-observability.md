# 容器安全与可观测性 — 安全加固 · 资源限制 · 日志 · 监控

> 等级：🎯 面试进阶
> 目标：掌握容器安全最佳实践、资源限制、日志收集体系与监控方案。

---

## 一、容器安全

### 1.1 核心安全原则

```
镜像安全 → 运行安全 → 网络安全 → 供应链安全
  ├─ 使用正式镜像    ├─ 非 root 运行   ├─ 最小网络暴露  ├─ 依赖漏洞扫描
  ├─ 镜像签名验证    ├─ 只读文件系统    ├─ 网络隔离      ├─ 镜像源可信
  └─ 最小化基础镜像  ├─ 禁用特权模式   └─ 敏感信息加密  └─ CVE 持续跟踪
```

### 1.2 非 root 运行

```dockerfile
# ❌ 默认 root 运行（危险）
FROM alpine
RUN apk add --no-cache curl
CMD ["/usr/bin/curl", "..."]
```

```dockerfile
# ✅ 创建非 root 用户运行
FROM alpine
RUN apk add --no-cache curl && \
    addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
CMD ["/usr/bin/curl", "..."]
```

```yaml
# Compose 中同样配置
services:
  app:
    build: .
    user: "1001:1001"        # 指定 UID:GID
    security_opt:
      - no-new-privileges:true   # 禁止提权
    read_only: true               # 根文件系统只读
    cap_drop:
      - ALL                        # 移除所有 Linux 能力
```

### 1.3 镜像扫描

```bash
# Docker Scout（内置）
docker scout cves mall-order:1.0

# Trivy（开源，最常用）
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy image mall-order:1.0

# 结果分级：CRITICAL / HIGH / MEDIUM / LOW
# CI 中阻断：存在 CRITICAL 漏洞则构建失败
trivy image --severity CRITICAL,HIGH --exit-code 1 mall-order:1.0
```

### 1.4 安全最佳实践清单

| 实践 | 说明 |
|------|------|
| 基础镜像最小化 | alpine / -slim 变体 |
| 非 root 运行 | USER 指令 + Compose user |
| 只读文件系统 | 需要写入的目录单独挂载 |
| 移除特权能力 | cap_drop: ALL，按需添加 |
| 密钥不入镜像 | 构建参数 ARG 不用存密钥，运行时注入 |
| 网络最小化 | 只暴露必要端口，容器间按需连通 |
| 定期扫描 | CI 中集成 Trivy，阻断高危漏洞 |
| 固定镜像版本 | 不用 latest，用不可变 tag |

---

## 二、资源限制

### 2.1 Docker 命令行限制

```bash
# CPU 限制：最多使用 1.5 核
docker run -d --cpus=1.5 my-app

# 内存限制：最多 512MB
docker run -d --memory=512m my-app

# 内存 + 交换分区
docker run -d --memory=512m --memory-swap=1g my-app

# 查看限制是否生效
docker inspect --format='{{.HostConfig.Memory}}' my-app
docker stats
```

### 2.2 Compose 资源限制

```yaml
services:
  order-service:
    build: .
    resources:
      limits:                    # 上限（超出则 OOM 杀死）
        cpus: "1.0"
        memory: 1G
      reservations:              # 预留（保证可用量）
        cpus: "0.5"
        memory: 512M
```

### 2.3 K8s 资源限制

```yaml
resources:
  requests:      # 调度依据，保证下限
    cpu: 250m    # 0.25 核
    memory: 512Mi
  limits:        # 使用上限
    cpu: "1"
    memory: 1Gi
```

**重要概念**：
- `requests` 影响**调度**（Node 必须满足 requests 总和）
- `limits` 触发**驱逐**（超过 limits 容器被杀）
- `CPU 是可压缩资源`（超限只是被节流）
- `内存是不可压缩资源`（超限直接 OOM Kill）

### 2.4 为什么必须限制内存

```
无限制 → 容器吃满宿主机内存 → 宿主机 Swap 疯狂 → 整机响应缓慢 → 故障蔓延
有限制 → 单容器 OOM → Docker 重启该容器 → 其他服务不受影响
```

---

## 三、日志收集

### 3.1 docker logs 的局限

```bash
docker logs <container>          # 只能看 stdout/stderr
docker logs -f --tail 100        # 跟随 + 最近 100 行
```

局限：容器删除日志就没了、无法集中检索、无法跨容器分析。

### 3.2 日志驱动

```yaml
# Compose 配置日志驱动
services:
  order-service:
    build: .
    logging:
      driver: json-file          # 默认
      options:
        max-size: "10m"          # 单文件最大 10MB
        max-file: "3"            # 保留 3 个轮转文件
```

### 3.3 EFK 日志体系

```
容器 stdout -> Fluentd 收集 -> Elasticsearch 存储 -> Kibana 可视化
                    (filter/route)      (索引/检索)
```

```yaml
# docker-compose 中的 EFK
services:
  fluentd:
    image: fluent/fluentd:v1.16
    volumes:
      - ./fluentd.conf:/fluentd/etc/fluent.conf
    ports:
      - "24224:24224"

  elasticsearch:
    image: elasticsearch:8.11.0
    environment:
      - discovery.type=single-node
    ports: ["9200:9200"]

  kibana:
    image: kibana:8.11.0
    ports: ["5601:5601"]
    depends_on: [elasticsearch]

  order-service:
    build: .
    logging:
      driver: fluentd             # 日志发送到 Fluentd
      options:
        fluentd-address: "fluentd:24224"
        tag: "mall.order"
```

```ruby
# fluentd.conf
<source>
  @type forward
  port 24224
</source>

<filter **>
  @type parser
  key_name log
  <parse>
    @type json
  </parse>
</filter>

<match **>
  @type elasticsearch
  host elasticsearch
  port 9200
  index_name mall-log
</match>
```

**使用场景**：某订单报错 → Kibana 搜 `service:order AND level:ERROR` → 秒级定位所有相关日志。

---

## 四、监控（cAdvisor + Prometheus）

### 4.1 架构

```
                   ┌─────────────────────────────────┐
Docker 容器        │  Prometheus                     │
┌──────────┐       │  ┌─────────────────────────┐   │     Grafana
│ cAdvisor │──────►│  │ 自定义指标 (业务监控)     │───┼───► 可视化
│ (容器指标)│ scrape │  └─────────────────────────┘   │   告警
│ Node       │       │  ┌─────────────────────────┐   │
│ Exporter  │──────►│  │ 基础设施指标 (CPU/内存)   │   │
└──────────┘       │  └─────────────────────────┘   │
                   └─────────────────────────────────┘
```

| 组件 | 作用 |
|------|------|
| **cAdvisor** | Google 开源，收集容器 CPU/内存/网络/磁盘指标 |
| **node-exporter** | 收集宿主机系统指标 |
| **Prometheus** | 时序数据库 + 抓取 + 告警规则 |
| **Grafana** | 可视化仪表盘 + 告警通知 |

### 4.2 完整 docker-compose

```yaml
services:
  cadvisor:
    image: gcr.io/cadvisor/cadvisor:v0.49.1
    volumes:
      - /:/rootfs:ro
      - /var/run:/var/run:ro
      - /sys:/sys:ro
      - /var/lib/docker/:/var/lib/docker:ro
      - /dev/disk/:/dev/disk:ro
    ports: ["8080:8080"]

  node-exporter:
    image: prom/node-exporter:v1.8.2
    volumes:
      - /proc:/host/proc:ro
      - /sys:/host/sys:ro
      - /:/rootfs:ro
    command:
      - '--path.procfs=/host/proc'
      - '--path.sysfs=/host/sys'
      - '--rootfs=/rootfs'
    ports: ["9100:9100"]

  prometheus:
    image: prom/prometheus:v2.53.0
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prom_data:/prometheus
    ports: ["9090:9090"]

  grafana:
    image: grafana/grafana:11.1.0
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin123
    volumes:
      - grafana_data:/var/lib/grafana
    ports: ["3000:3000"]

volumes:
  prom_data:
  grafana_data:
```

### 4.3 prometheus.yml

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'cadvisor'
    static_configs:
      - targets: ['cadvisor:8080']

  - job_name: 'node'
    static_configs:
      - targets: ['node-exporter:9100']

  - job_name: 'spring-boot'          # 微服务指标
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'gateway:8080'
          - 'order-service:8082'
          - 'search-service:8085'
```

### 4.4 核心监控指标

| 分类 | PromQL 示例 | 关注点 |
|------|------------|--------|
| 容器 CPU | `rate(container_cpu_usage_seconds_total[1m])` | CPU 使用率 |
| 容器内存 | `container_memory_usage_bytes` | 内存使用 |
| 容器网络 | `rate(container_network_receive_bytes_total[1m])` | 网络流量 |
| 容器重启 | `container_restart_count` | 崩溃次数 |
| Java 堆 | `jvm_memory_used_bytes{area="heap"}` | JVM 堆使用 |
| 接口延迟 | `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))` | P99 延迟 |

### 4.5 告警规则

```yaml
# prometheus 告警规则
groups:
  - name: container-alerts
    rules:
      - alert: ContainerHighCPU
        expr: rate(container_cpu_usage_seconds_total{container!=""}[5m]) > 1.5
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "容器 CPU 使用率过高 {{ $labels.container }}"

      - alert: ContainerOOM
        expr: increase(container_oom_events_total[5m]) > 0
        for: 1m
        labels: { severity: critical }
        annotations:
          summary: "容器发生 OOM {{ $labels.name }}"

      - alert: ContainerRestart
        expr: increase(container_restart_count[10m]) > 3
        labels: { severity: critical }
        annotations:
          summary: "容器频繁重启 {{ $labels.name }}"
```

---

## 五、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| 容器安全最关键的措施？ | 非 root 运行 + 最小权限 + 镜像扫描 |
| 为什么容器要用非 root？ | root 容器内提权可突破到宿主机，隔离防线崩溃 |
| CPU 和内存超限的区别？ | CPU 可压缩只节流，内存不可压缩直接 OOM Kill |
| 日志方案怎么选？ | 单机 docker logs，集群 EFK/ELK 集中收集 |
| cAdvisor 和 Prometheus 关系？ | cAdvisor 暴露容器指标，Prometheus 定期抓取存储 |

> 容器安全与监控完备后，进入项目实战：AI 商城全栈容器化。