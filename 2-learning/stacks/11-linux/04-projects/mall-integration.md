# AI 商城部署集成

> 将 Linux 技能应用到 AI 智能商城的实际部署中，涵盖多服务部署架构、健康检查、监控和日志收集方案。

---

## 1. AI 商城部署架构

### 服务拓扑

```
                        ┌──────────────┐
                        │  Nginx 负载   │
                        │  均衡器      │
                        └──────┬───────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
     ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
     │  Spring Cloud  │ │  Spring Cloud  │ │  Spring Cloud  │
     │  Gateway 网关  │ │  用户服务      │ │  商品服务      │
     │  :8080         │ │  :8081         │ │  :8082         │
     └────────────────┘ └────────────────┘ └────────────────┘
              │                │                │
              └────────────────┼────────────────┘
                               │
                               ▼
                     ┌────────────────┐
                     │  AI 推荐服务   │
                     │  Python FastAPI│
                     │  :8083         │
                     └────────────────┘
```

### 服务器规划

| 服务器 | 配置 | 部署服务 |
|--------|------|----------|
| app-node-1 | 4C 8G 100G SSD | 网关 + 用户服务 + 商品服务 |
| app-node-2 | 4C 8G 100G SSD | AI 推荐 + Elasticsearch |
| app-node-3 | 4C 16G 200G SSD | MySQL + Redis + RocketMQ |
| app-node-4 | 2C 4G 50G SSD | Nginx 负载均衡 + 监控 |

---

## 2. 多服务部署脚本

### 部署目录结构

```bash
/opt/mall/
├── bin/
│   ├── deploy.sh              # 部署主脚本
│   ├── deploy-all.sh          # 一键部署所有服务
│   └── rollback.sh            # 回滚脚本
├── services/
│   ├── gateway/
│   │   └── mall-gateway.jar
│   ├── user-service/
│   │   └── mall-user.jar
│   ├── product-service/
│   │   └── mall-product.jar
│   └── ai-recommend/
│       └── main.py
├── config/
│   ├── application-prod.yml
│   └── nginx/
│       └── mall.conf
├── logs/
│   ├── gateway/
│   ├── user-service/
│   ├── product-service/
│   └── ai-recommend/
└── backup/
    └── releases/
```

### 服务部署脚本

```bash
#!/bin/bash
# 文件名: /opt/mall/bin/deploy.sh
# 用途: 部署单个微服务

set -euo pipefail

SERVICE_NAME=$1
VERSION=$2
REMOTE_HOST="deploy@${DEPLOY_HOST:-localhost}"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"; }

# 检查参数
if [ $# -lt 2 ]; then
    echo "用法: $0 <service-name> <version>"
    echo "示例: $0 gateway v1.2.3"
    exit 1
fi

# JVM 配置（按服务不同）
case "${SERVICE_NAME}" in
    gateway)      JAR="mall-gateway.jar";     JVM="-Xms512m -Xmx512m"; PORT=8080 ;;
    user-service) JAR="mall-user.jar";        JVM="-Xms1g -Xmx1g";     PORT=8081 ;;
    product-service) JAR="mall-product.jar";  JVM="-Xms1g -Xmx1g";     PORT=8082 ;;
    *)
        echo "未知服务: ${SERVICE_NAME}"
        exit 1
        ;;
esac

# 1. 构建
log "构建 ${SERVICE_NAME}:${VERSION}"
cd "/opt/mall/${SERVICE_NAME}"
mvn clean package -DskipTests -q
cp "target/${JAR}" "/opt/mall/builds/${SERVICE_NAME}-${VERSION}.jar"

# 2. 备份
log "备份旧版本"
BACKUP_DIR="/opt/mall/backup/releases/${SERVICE_NAME}"
mkdir -p "${BACKUP_DIR}"
cp "/opt/mall/services/${SERVICE_NAME}/${JAR}" "${BACKUP_DIR}/${JAR}.$(date +%Y%m%d_%H%M%S)" 2>/dev/null || true

# 3. 部署新版本
cp "/opt/mall/builds/${SERVICE_NAME}-${VERSION}.jar" "/opt/mall/services/${SERVICE_NAME}/${JAR}"

# 4. 重启服务
log "重启 ${SERVICE_NAME}"
sudo systemctl restart "mall-${SERVICE_NAME}"

# 5. 健康检查
log "健康检查..."
for i in {1..30}; do
    sleep 2
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${PORT}/actuator/health" 2>/dev/null || echo "000")
    if [ "${STATUS}" = "200" ]; then
        log "${SERVICE_NAME} 部署成功"
        exit 0
    fi
    log "等待中... (${i}/30)"
done

log "部署失败，触发回滚"
/opt/mall/bin/rollback.sh "${SERVICE_NAME}"
exit 1
```

### 一键部署所有服务

```bash
#!/bin/bash
# 文件名: /opt/mall/bin/deploy-all.sh

set -euo pipefail

VERSION=${1:-latest}
NODES=("app-node-1" "app-node-2")

for node in "${NODES[@]}"; do
    echo "===== 部署到 ${node} ====="
    
    # 同步代码
    rsync -avz --delete /opt/mall/ "deploy@${node}:/opt/mall/"
    
    # 部署服务
    ssh "deploy@${node}" "cd /opt/mall && \
        ./bin/deploy.sh gateway ${VERSION} && \
        ./bin/deploy.sh user-service ${VERSION} && \
        ./bin/deploy.sh product-service ${VERSION}"
done

echo "===== 全部部署完成 ====="
```

---

## 3. 健康检查与监控

### 健康检查端点

```bash
#!/bin/bash
# 文件名: /opt/mall/bin/health-check.sh

SERVICES=(
    "gateway:8080:Spring Cloud Gateway"
    "user-service:8081:用户服务"
    "product-service:8082:商品服务"
    "ai-recommend:8083:AI 推荐"
    "mysql:3306:MySQL"
    "redis:6379:Redis"
    "rocketmq:9876:RocketMQ"
)

echo "===== 健康检查报告 ====="
echo "时间: $(date)"
echo ""

for entry in "${SERVICES[@]}"; do
    IFS=':' read -r name port desc <<< "${entry}"
    
    # TCP 端口检查
    timeout 3 bash -c "echo >/dev/tcp/localhost/${port}" 2>/dev/null
    TCP_STATUS=$?
    
    # HTTP 健康检查（如果适用）
    if [ "${port}" -ge 8080 ] && [ "${port}" -le 8083 ]; then
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/actuator/health" 2>/dev/null || echo "000")
        echo "${desc} (${name}:${port}) — TCP: $([ ${TCP_STATUS} -eq 0 ] && echo 'OK' || echo 'DOWN') | HTTP: ${HTTP_CODE}"
    else
        echo "${desc} (${name}:${port}) — TCP: $([ ${TCP_STATUS} -eq 0 ] && echo 'OK' || echo 'DOWN')"
    fi
done
```

### systemd 服务单元文件

```ini
# /etc/systemd/system/mall-gateway.service
[Unit]
Description=Mall Gateway Service
After=network.target

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/mall/services/gateway
ExecStart=/usr/bin/java ${JAVA_OPTS} -jar mall-gateway.jar
ExecStop=/bin/kill -SIGTERM $MAINPID
Restart=always
RestartSec=10
StartLimitInterval=300
StartLimitBurst=5
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=JAVA_OPTS=-Xms512m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError

[Install]
WantedBy=multi-user.target
```

---

## 4. 日志收集方案

### 日志目录结构

```bash
/var/log/mall/
├── gateway/
│   ├── gateway.log
│   ├── gateway-error.log
│   └── access.log
├── user-service/
│   ├── user.log
│   └── user-error.log
├── product-service/
│   ├── product.log
│   └── product-error.log
└── ai-recommend/
    ├── recommend.log
    └── access.log
```

### logrotate 配置

```bash
# /etc/logrotate.d/mall
/var/log/mall/*/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    copytruncate
    dateext
    dateformat -%Y%m%d
    postrotate
        # 通知应用重新打开日志文件
        find /opt/mall/services -name "*.jar" | while read jar; do
            PID=$(ps aux | grep "${jar}" | grep -v grep | awk '{print $2}')
            [ -n "${PID}" ] && kill -USR1 "${PID}" 2>/dev/null || true
        done
    endscript
}
```

### 集中式日志收集

```bash
# 方案一：使用 rsyslog 集中收集
# /etc/rsyslog.d/mall.conf
$template RemoteLogs,"/var/log/mall/%HOSTNAME%/%programname%/%$year%%$month%%$day%.log"
*.* ?RemoteLogs
& ~

# 方案二：使用 Filebeat 发送到 Elasticsearch
# /etc/filebeat/filebeat.yml
filebeat.inputs:
- type: log
  enabled: true
  paths:
    - /var/log/mall/*/*.log
  fields:
    app: mall
  fields_under_root: true

output.elasticsearch:
  hosts: ["localhost:9200"]
  index: "mall-logs-%{+yyyy.MM.dd}"
```

---

## 总结

本章你学会了：

- AI 商城的服务拓扑和服务器规划
- 多服务部署脚本（构建→备份→部署→健康检查→回滚）
- 一键部署所有服务到多台机器
- 健康检查脚本和 systemd 服务配置
- 日志收集方案（logrotate + 集中式收集）

下一步：完成 [迷你部署系统小项目](mini-blog/README.md) 巩固所学知识。