# Linux 代码题

> Shell 脚本编程面试题，涵盖字符串处理、文件操作、日志分析、系统管理等场景。

---

## 第 1 题：统计日志中错误类型

### 题目

有一个 Java 应用日志文件 `app.log`，格式如下：

```
2024-08-20 10:00:01 [ERROR] java.lang.NullPointerException: null
2024-08-20 10:00:02 [INFO] User login success
2024-08-20 10:00:03 [ERROR] com.mysql.jdbc.exceptions.jdbc4.CommunicationsException: Communications link failure
2024-08-20 10:00:04 [ERROR] java.lang.NullPointerException: null
2024-08-20 10:00:05 [WARN] Connection pool exhausted
```

统计每种异常类型出现的次数，按次数降序输出。

### 解答

```bash
#!/bin/bash

# 方案一：使用 grep + awk
echo "=== 异常类型统计 ==="
grep -oE "[A-Za-z]+Exception" app.log | sort | uniq -c | sort -nr

# 方案二：纯 awk
awk '{
    for(i=1; i<=NF; i++) {
        if($i ~ /Exception/) {
            exceptions[$i]++
        }
    }
} END {
    for(ex in exceptions) {
        print exceptions[ex], ex
    }
}' app.log | sort -rn

# 方案三：提取异常类名（含包名）
echo "=== 完整异常类名统计 ==="
grep -oE "[a-zA-Z]+(\.[a-zA-Z]+)*Exception" app.log | sort | uniq -c | sort -nr
```

---

## 第 2 题：查找大文件并清理

### 题目

编写一个脚本，扫描 `/var/log` 目录，找出所有大于 100MB 的文件，按大小降序排列，并提示用户是否删除。

### 解答

```bash
#!/bin/bash

# 查找大文件
echo "正在扫描大文件..."
LARGE_FILES=$(find /var/log -type f -size +100M -exec ls -lh {} \; 2>/dev/null | sort -k5 -hr)

if [ -z "${LARGE_FILES}" ]; then
    echo "未找到大于 100MB 的文件"
    exit 0
fi

echo "找到以下大文件："
echo "${LARGE_FILES}"
echo ""

# 逐文件询问是否删除
echo "${LARGE_FILES}" | while read -r line; do
    FILENAME=$(echo "${line}" | awk '{print $NF}')
    SIZE=$(echo "${line}" | awk '{print $5}')
    
    read -p "删除 ${FILENAME} (${SIZE})? [y/N] " -r REPLY
    if [[ "${REPLY}" =~ ^[Yy]$ ]]; then
        # 清空文件而非删除（保留文件句柄）
        > "${FILENAME}"
        echo "已清空: ${FILENAME}"
    fi
done

# 安全版本：只清空不删除，避免影响正在写入的进程
# 使用 truncate 替代 rm
```

---

## 第 3 题：进程监控脚本

### 题目

编写一个脚本，监控指定 Java 进程，如果进程不存在或 CPU 使用率超过 90%，则重启该进程并发送告警。

### 解答

```bash
#!/bin/bash

APP_NAME="mall-gateway"
JAR_PATH="/opt/app/${APP_NAME}.jar"
LOG_FILE="/var/log/monitor/monitor.log"
ALERT_EMAIL="admin@example.com"

# 确保日志目录存在
mkdir -p "$(dirname "${LOG_FILE}")"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "${LOG_FILE}"
}

send_alert() {
    local msg=$1
    echo "${msg}" | mail -s "[ALERT] ${APP_NAME} 异常" "${ALERT_EMAIL}"
    log "告警已发送: ${msg}"
}

# 获取进程 PID
get_pid() {
    pgrep -f "${JAR_PATH}" 2>/dev/null || echo ""
}

# 重启服务
restart_app() {
    log "重启 ${APP_NAME}..."
    nohup java -jar "${JAR_PATH}" > /dev/null 2>&1 &
    local new_pid=$!
    log "新 PID: ${new_pid}"
    echo "${new_pid}"
}

# 主循环
while true; do
    PID=$(get_pid)
    
    if [ -z "${PID}" ]; then
        log "进程不存在"
        send_alert "${APP_NAME} 进程不存在，正在重启"
        PID=$(restart_app)
    fi
    
    # 检查 CPU 使用率
    CPU_USAGE=$(ps -p "${PID}" -o %cpu --no-headers 2>/dev/null || echo "0")
    CPU_USAGE_INT=${CPU_USAGE%.*}
    
    if [ "${CPU_USAGE_INT}" -gt 90 ]; then
        log "CPU 使用率过高: ${CPU_USAGE}%"
        send_alert "${APP_NAME} CPU 使用率 ${CPU_USAGE}%，重启中..."
        
        kill "${PID}" 2>/dev/null
        sleep 3
        PID=$(restart_app)
    fi
    
    sleep 60
done
```

---

## 第 4 题：字符串处理

### 题目

给定一个文件 `data.txt`，每行格式为 `IP:PORT:STATUS`，如：

```
192.168.1.1:8080:UP
192.168.1.2:8080:DOWN
192.168.1.3:8080:UP
192.168.1.1:8081:UP
192.168.1.2:8081:DOWN
```

统计每个 IP 上 UP 和 DOWN 的服务数量。

### 解答

```bash
#!/bin/bash

# 方案一：awk 统计
echo "=== IP 服务状态统计 ==="
awk -F: '
{
    ip = $1
    status = $3
    if(status == "UP") {
        up[ip]++
    } else if(status == "DOWN") {
        down[ip]++
    }
}
END {
    printf "%-20s %-10s %-10s %-10s\n", "IP", "UP", "DOWN", "TOTAL"
    for(ip in up) {
        total = up[ip] + (down[ip] ? down[ip] : 0)
        printf "%-20s %-10d %-10d %-10d\n", ip, up[ip], down[ip] ? down[ip] : 0, total
    }
    for(ip in down) {
        if(!(ip in up)) {
            printf "%-20s %-10d %-10d %-10d\n", ip, 0, down[ip], down[ip]
        }
    }
}' data.txt

# 方案二：使用关联数组和管道
echo ""
echo "=== 精简版 ==="
awk -F: '{cnt[$1","$3]++} END {for(k in cnt) print k, cnt[k]}' data.txt | sort
```

---

## 第 5 题：批量重命名

### 题目

将当前目录下所有 `.log` 文件重命名为 `YYYYMMDD_HHMMSS.log`（以文件的最后修改时间为准），并归档到 `archive/` 目录。

### 解答

```bash
#!/bin/bash

ARCHIVE_DIR="archive"
mkdir -p "${ARCHIVE_DIR}"

for file in *.log; do
    [ -f "${file}" ] || continue
    
    # 获取文件最后修改时间
    TIMESTAMP=$(stat -c "%Y" "${file}" 2>/dev/null || date -r "${file}" "+%s")
    
    # 格式化为 YYYYMMDD_HHMMSS
    NEW_NAME=$(date -d "@${TIMESTAMP}" "+%Y%m%d_%H%M%S" 2>/dev/null || date -r "${file}" "+%Y%m%d_%H%M%S").log
    
    # 如果有重名，添加序号
    if [ -f "${ARCHIVE_DIR}/${NEW_NAME}" ]; then
        COUNT=1
        while [ -f "${ARCHIVE_DIR}/${NEW_NAME%.*}_${COUNT}.log" ]; do
            ((COUNT++))
        done
        NEW_NAME="${NEW_NAME%.*}_${COUNT}.log"
    fi
    
    # 移动文件
    mv "${file}" "${ARCHIVE_DIR}/${NEW_NAME}"
    echo "已归档: ${file} → ${ARCHIVE_DIR}/${NEW_NAME}"
done

echo "归档完成"
```

---

## 第 6 题：检查服务健康状态

### 题目

编写一个脚本，检查配置文件 `services.conf` 中列出的所有 HTTP 服务是否健康，输出每行的状态码和响应时间，并在最后给出汇总。

### 解答

```bash
#!/bin/bash

# services.conf 格式：
# 服务名,URL
# 网关,http://localhost:8080/actuator/health
# 用户服务,http://localhost:8081/actuator/health

CONFIG_FILE="services.conf"
PASS=0
FAIL=0
TIMEOUT=5

echo "=========================================="
echo "服务健康检查报告"
echo "检查时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="

while IFS=',' read -r name url; do
    # 跳过空行和注释
    [ -z "${name}" ] && continue
    [[ "${name}" =~ ^# ]] && continue
    
    # 发起 HTTP 请求，记录响应时间和状态码
    START_TIME=$(date +%s%N)
    
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        --connect-timeout "${TIMEOUT}" \
        --max-time "${TIMEOUT}" \
        "${url}" 2>/dev/null || echo "000")
    
    END_TIME=$(date +%s%N)
    DURATION=$(( (END_TIME - START_TIME) / 1000000 ))
    
    # 判断健康状态
    if [ "${HTTP_CODE}" = "200" ]; then
        echo "[PASS] ${name} — HTTP ${HTTP_CODE} — ${DURATION}ms"
        ((PASS++))
    else
        echo "[FAIL] ${name} — HTTP ${HTTP_CODE} — ${DURATION}ms"
        ((FAIL++))
    fi
done < "${CONFIG_FILE}"

echo "=========================================="
echo "汇总: 通过 ${PASS} / 失败 ${FAIL} / 总计 $((PASS + FAIL))"
echo "=========================================="

# 如果有失败，退出码非零
[ "${FAIL}" -eq 0 ] && exit 0 || exit 1
```

---

## 第 7 题：日志切割与归档

### 题目

编写一个脚本，将当前日志文件按天切割，压缩后保留 30 天，并删除超过 30 天的旧归档。

### 解答

```bash
#!/bin/bash

LOG_DIR="/var/log/app"
LOG_FILE="${LOG_DIR}/application.log"
ARCHIVE_DIR="${LOG_DIR}/archive"
RETENTION_DAYS=30

# 确保目录存在
mkdir -p "${ARCHIVE_DIR}"

# 获取昨天的日期
YESTERDAY=$(date -d "yesterday" "+%Y%m%d")

# 如果日志文件存在且非空，进行切割
if [ -s "${LOG_FILE}" ]; then
    # 复制当前日志到归档
    cp "${LOG_FILE}" "${ARCHIVE_DIR}/application-${YESTERDAY}.log"
    
    # 压缩
    gzip -f "${ARCHIVE_DIR}/application-${YESTERDAY}.log"
    
    # 清空当前日志（保留文件句柄，不影响正在写入的进程）
    > "${LOG_FILE}"
    
    echo "[$(date)] 日志已切割: application-${YESTERDAY}.log.gz"
fi

# 删除超过保留天数的旧归档
find "${ARCHIVE_DIR}" -name "application-*.log.gz" -mtime +${RETENTION_DAYS} -delete
echo "[$(date)] 已清理超过 ${RETENTION_DAYS} 天的旧日志"

# 统计归档大小
TOTAL_SIZE=$(du -sh "${ARCHIVE_DIR}" | cut -f1)
echo "[$(date)] 归档目录大小: ${TOTAL_SIZE}"
```

---

## 第 8 题：解析 Nginx 访问日志

### 题目

Nginx 访问日志格式如下：

```
192.168.1.1 - - [20/Aug/2024:10:00:01 +0800] "GET /api/order HTTP/1.1" 200 1234 "https://example.com" "Mozilla/5.0"
```

统计：
1. 每个 IP 的访问次数
2. 每个接口的请求次数
3. 状态码分布
4. 响应时间超过 3 秒的请求（假设日志中有 `$request_time` 字段）

### 解答

```bash
#!/bin/bash

LOG_FILE="access.log"

echo "=== IP 访问次数 TOP 10 ==="
awk '{ips[$1]++} END {for(ip in ips) print ips[ip], ip}' "${LOG_FILE}" | sort -rn | head -10

echo ""
echo "=== 接口请求次数 TOP 10 ==="
# 提取 URL 路径（第7列，去掉查询参数）
awk '{
    url = $7
    gsub(/\?.*/, "", url)    # 去掉查询参数
    urls[url]++
} END {
    for(url in urls) print urls[url], url
}' "${LOG_FILE}" | sort -rn | head -10

echo ""
echo "=== 状态码分布 ==="
awk '{status[$9]++} END {for(s in status) print status[s], s}' "${LOG_FILE}" | sort -rn

echo ""
echo "=== 慢请求（> 3秒） ==="
# 假设日志最后一列是请求时间
awk '$NF > 3 {print $1, $7, $NF"秒"}' "${LOG_FILE}" | sort -k3 -rn | head -10
```

---

## 面试技巧

- **set -euo pipefail**：生产脚本必备，防止错误蔓延
- **使用 local 变量**：函数内变量用 local 声明，避免污染全局
- **错误处理优先**：先处理异常情况，再处理正常流程
- **日志输出**：每次操作都记录日志，便于排查
- **幂等性**：脚本可重复执行，不会造成副作用