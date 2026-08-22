# Shell 脚本编程

> Shell 脚本是 Java 后端开发者自动化部署、日志分析、定时任务的利器。本章从基础语法到实战，带你写出生产级的 Shell 脚本。

---

## 1. Shell 基础

### 脚本头（Shebang）

```bash
#!/bin/bash
# 第一行指定解释器，建议用 /bin/bash 而非 /bin/sh
```

### 变量

```bash
# 定义变量（等号两边不能有空格）
APP_NAME="mall-gateway"
JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
PORT=8080

# 使用变量（推荐用花括号包裹）
echo "Starting ${APP_NAME} on port ${PORT}"

# 命令替换
CURRENT_DIR=$(pwd)
DATE=$(date +%Y%m%d)

# 环境变量
echo "JAVA_HOME: ${JAVA_HOME}"
export PATH="${JAVA_HOME}/bin:${PATH}"

# 特殊变量
echo "脚本名: $0"
echo "参数个数: $#"
echo "所有参数: $@"
echo "第一个参数: $1"
echo "上一个命令退出码: $?"
echo "当前 PID: $$"
```

### 条件判断

```bash
# if 语句
if [ -f "app.jar" ]; then
    echo "app.jar 存在"
elif [ -d "target/" ]; then
    echo "target 目录存在"
else
    echo "两者都不存在"
fi

# 常用判断条件
[ -f "file" ]     # 是否为文件
[ -d "dir" ]      # 是否为目录
[ -z "$var" ]     # 字符串是否为空
[ -n "$var" ]     # 字符串是否非空
[ "$a" = "$b" ]   # 字符串相等
[ "$a" != "$b" ]  # 字符串不等
[ $a -eq $b ]     # 数值相等
[ $a -gt $b ]     # 数值大于
[ $a -lt $b ]     # 数值小于
[ -x "file" ]     # 是否可执行
[ -e "path" ]     # 是否存在

# 逻辑组合
[ -f "app.jar" ] && [ -x "app.jar" ]
[ -z "$1" ] || echo "参数: $1"
```

### 循环

```bash
# for 循环 — 遍历文件
for jar in /opt/app/*.jar; do
    echo "发现 JAR: ${jar}"
done

# for 循环 — 数字范围
for i in {1..5}; do
    echo "第 ${i} 次尝试"
done

# for 循环 — 类 C 风格
for ((i=0; i<10; i++)); do
    echo "count: ${i}"
done

# while 循环 — 读取文件
while IFS= read -r line; do
    echo "行: ${line}"
done < /var/log/app.log

# until 循环
count=0
until [ $count -ge 5 ]; do
    echo "retry ${count}"
    ((count++))
done
```

### 函数

```bash
# 定义函数
log_info() {
    local msg=$1
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] ${msg}"
}

log_error() {
    local msg=$1
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] ${msg}" >&2
}

# 使用函数
log_info "开始部署应用"
log_error "连接数据库失败"

# 带返回值的函数
get_status() {
    local service=$1
    systemctl is-active "$service" 2>/dev/null
    return $?
}

if get_status "nginx"; then
    echo "Nginx 运行中"
else
    echo "Nginx 未运行"
fi
```

---

## 2. 正则表达式

### grep — 文本搜索

```bash
# 基本用法
grep "ERROR" app.log
grep -v "DEBUG" app.log           # 排除
grep -i "error" app.log           # 忽略大小写
grep -E "ERROR|FATAL" app.log     # 扩展正则（| 表示或）
grep -o "Exception.*" app.log     # 只输出匹配部分

# 正则示例
grep -E "^[0-9]{4}-[0-9]{2}-[0-9]{2}" app.log     # 匹配日期开头
grep -E "([0-9]{1,3}\.){3}[0-9]{1,3}" app.log     # 匹配 IP 地址
grep -P "[\x{4e00}-\x{9fa5}]" app.log              # 匹配中文字符
```

### sed — 流编辑器

```bash
# 替换
sed 's/ERROR/ERROR/g' app.log                      # 替换所有
sed -i 's/127.0.0.1/192.168.1.100/g' config.yml    # 原地替换
sed 's/^/PREFIX: /' app.log                        # 行首添加

# 行操作
sed -n '10,20p' app.log                            # 打印 10-20 行
sed '1,5d' app.log                                 # 删除 1-5 行
sed -n '/ERROR/p' app.log                          # 只打印 ERROR 行

# 多重编辑
sed -e 's/DEBUG/INFO/g' -e 's/WARN/WARNING/g' app.log
```

### awk — 文本处理

```bash
# 基本格式：awk 'pattern {action}' file

# 列处理
awk '{print $1, $NF}' app.log                       # 打印第一列和最后一列
awk -F '|' '{print $1, $3}' data.csv                # 自定义分隔符

# 条件过滤
awk '$3 > 5000 {print $1, $3}' access.log           # 响应时间 > 5000ms
awk '/ERROR/ {count++} END {print count}' app.log   # 统计 ERROR 次数

# 内置变量
awk '{print NR, NF, $0}' app.log                    # 行号、列数、整行

# 实战：统计各 IP 访问次数
awk '{ips[$1]++} END {for(ip in ips) print ip, ips[ip]}' access.log | sort -k2 -nr | head -10
```

---

## 3. 实战：自动化部署脚本

```bash
#!/bin/bash
# 文件名: deploy.sh
# 用途: 自动化部署 Java 应用

set -e                           # 任何命令失败立即退出
set -u                           # 使用未定义变量时报错

# 配置
APP_NAME="mall-gateway"
JAR_NAME="${APP_NAME}.jar"
REMOTE_HOST="192.168.1.100"
REMOTE_DIR="/opt/app/${APP_NAME}"
BACKUP_DIR="${REMOTE_DIR}/backup"
JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# 1. 构建
log "开始构建 ${APP_NAME}"
mvn clean package -DskipTests -q
log "构建完成"

# 2. 备份
log "备份旧版本"
ssh -t "${REMOTE_HOST}" "mkdir -p ${BACKUP_DIR} && \
    cp ${REMOTE_DIR}/${JAR_NAME} ${BACKUP_DIR}/${JAR_NAME}.$(date +%Y%m%d_%H%M%S) 2>/dev/null; true"

# 3. 传输
log "上传新版本"
scp "target/${JAR_NAME}" "${REMOTE_HOST}:${REMOTE_DIR}/"

# 4. 重启
log "重启服务"
ssh -t "${REMOTE_HOST}" "sudo systemctl restart ${APP_NAME}"

# 5. 健康检查
log "等待服务启动..."
for i in {1..30}; do
    sleep 2
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://${REMOTE_HOST}:8080/actuator/health 2>/dev/null || echo "000")
    if [ "${STATUS}" = "200" ]; then
        log "服务启动成功，状态码: ${STATUS}"
        exit 0
    fi
    log "等待中... (${i}/30)"
done

log "启动超时，触发回滚"
ssh -t "${REMOTE_HOST}" "cp ${BACKUP_DIR}/${JAR_NAME}.$(date +%Y%m%d_%H%M%S) ${REMOTE_DIR}/${JAR_NAME} && sudo systemctl restart ${APP_NAME}"
exit 1
```

---

## 4. 实战：日志分析脚本

```bash
#!/bin/bash
# 文件名: log-analyzer.sh
# 用途: 分析 Java 应用日志，生成统计报告

LOG_FILE=${1:-/var/log/app/application.log}
REPORT_FILE="log-report-$(date +%Y%m%d).txt"

if [ ! -f "${LOG_FILE}" ]; then
    echo "错误: 日志文件 ${LOG_FILE} 不存在"
    exit 1
fi

echo "========== 日志分析报告 ==========" > "${REPORT_FILE}"
echo "分析时间: $(date)" >> "${REPORT_FILE}"
echo "日志文件: ${LOG_FILE}" >> "${REPORT_FILE}"
echo "" >> "${REPORT_FILE}"

# 1. 总体统计
TOTAL_LINES=$(wc -l < "${LOG_FILE}")
echo "总行数: ${TOTAL_LINES}" >> "${REPORT_FILE}"

# 2. 日志级别统计
echo "" >> "${REPORT_FILE}"
echo "--- 日志级别分布 ---" >> "${REPORT_FILE}"
for level in ERROR WARN INFO DEBUG; do
    count=$(grep -c "${level}" "${LOG_FILE}" 2>/dev/null || echo 0)
    echo "${level}: ${count}" >> "${REPORT_FILE}"
done

# 3. 异常堆栈提取
echo "" >> "${REPORT_FILE}"
echo "--- 异常类型统计 ---" >> "${REPORT_FILE}"
grep -oE "[A-Za-z]+Exception" "${LOG_FILE}" 2>/dev/null | sort | uniq -c | sort -nr >> "${REPORT_FILE}"

# 4. 按小时统计请求量（假设日志格式含时间戳）
echo "" >> "${REPORT_FILE}"
echo "--- 每小时请求量 ---" >> "${REPORT_FILE}"
grep -oE "2024-[0-9]{2}-[0-9]{2} [0-9]{2}" "${LOG_FILE}" 2>/dev/null | sort | uniq -c | sort -k2 >> "${REPORT_FILE}"

# 5. 耗时超过阈值的请求
echo "" >> "${REPORT_FILE}"
echo "--- 慢请求（>3000ms）---" >> "${REPORT_FILE}"
grep -E "耗时" "${LOG_FILE}" 2>/dev/null | awk -F'耗时' '{print $2}' | grep -oE '[0-9]+' | awk '$1 > 3000 {print}' | wc -l >> "${REPORT_FILE}"

echo "" >> "${REPORT_FILE}"
echo "报告已生成: ${REPORT_FILE}"
```

---

## 总结

本章你学会了：

- Shell 脚本的变量、条件判断、循环和函数
- 正则表达式的 grep/sed/awk 三剑客
- 编写自动化部署脚本（构建→备份→传输→重启→健康检查→回滚）
- 编写日志分析脚本（统计级别、异常、耗时）

下一步：学习 [网络排查](02-network-troubleshooting.md) 解决服务间连通性问题。