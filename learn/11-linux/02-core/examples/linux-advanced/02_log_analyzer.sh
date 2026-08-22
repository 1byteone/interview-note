#!/bin/bash
# ============================================
# Nginx 访问日志分析器
# 功能：状态码统计 / 慢请求分析 / Top IP / 汇总报告
# 用法：bash 02_log_analyzer.sh /var/log/nginx/access.log
# ============================================

set -euo pipefail

LOG_FILE="${1:-/var/log/nginx/access.log}"
REPORT_DIR="/tmp/nginx-report"
REPORT_FILE="${REPORT_DIR}/report-$(date '+%Y%m%d%H%M%S').txt"

# ---------- 前置检查 ----------
if [ ! -f "$LOG_FILE" ]; then
    echo "错误: 日志文件不存在: $LOG_FILE"
    echo "用法: $0 <access_log_path>"
    exit 1
fi

# 如果日志文件不存在，生成一份示例日志用于演示
if [ "$LOG_FILE" = "/var/log/nginx/access.log" ] && [ ! -f "$LOG_FILE" ]; then
    echo "未找到实际日志，生成演示数据..."
    LOG_FILE="/tmp/demo-access.log"
    cat > "$LOG_FILE" << 'EOF'
192.168.1.1 - - [22/Aug/2026:10:15:30 +0800] "GET /api/users HTTP/1.1" 200 1234 "-" "Mozilla/5.0" 0.032
192.168.1.2 - - [22/Aug/2026:10:15:31 +0800] "POST /api/orders HTTP/1.1" 201 567 "-" "curl/7.79" 0.124
192.168.1.1 - - [22/Aug/2026:10:15:32 +0800] "GET /api/products HTTP/1.1" 200 8901 "-" "Mozilla/5.0" 0.045
10.0.0.1 - - [22/Aug/2026:10:15:33 +0800] "GET /health HTTP/1.1" 200 2 "-" "kube-probe" 0.001
192.168.1.3 - - [22/Aug/2026:10:15:34 +0800] "GET /api/users HTTP/1.1" 500 0 "-" "python-requests" 2.345
192.168.1.1 - - [22/Aug/2026:10:15:35 +0800] "POST /api/orders HTTP/1.1" 400 123 "-" "Mozilla/5.0" 0.067
10.0.0.2 - - [22/Aug/2026:10:15:36 +0800] "GET /api/products HTTP/1.1" 200 8901 "-" "Java/17" 0.512
192.168.1.4 - - [22/Aug/2026:10:15:37 +0800] "DELETE /api/orders/123 HTTP/1.1" 204 0 "-" "curl/7.79" 0.023
192.168.1.2 - - [22/Aug/2026:10:15:38 +0800] "GET /api/users HTTP/1.1" 200 1234 "-" "Mozilla/5.0" 0.031
10.0.0.1 - - [22/Aug/2026:10:15:39 +0800] "POST /api/orders HTTP/1.1" 503 45 "-" "python-requests" 3.001
192.168.1.5 - - [22/Aug/2026:10:15:40 +0800] "GET /static/js/app.js HTTP/1.1" 304 0 "-" "Mozilla/5.0" 0.002
192.168.1.1 - - [22/Aug/2026:10:15:41 +0800] "GET /api/users?page=2 HTTP/1.1" 200 5678 "-" "Mozilla/5.0" 0.078
EOF
fi

mkdir -p "$REPORT_DIR"

echo "============================================"
echo "   Nginx 访问日志分析报告"
echo "   日志文件: $LOG_FILE"
echo "   生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================"

TOTAL_LINES=$(wc -l < "$LOG_FILE")
echo ""
echo "### 1. 总览"
echo "总请求数: $TOTAL_LINES"

# ---------- 状态码分布 ----------
echo ""
echo "### 2. HTTP 状态码分布"
echo "-----------------------------"
# 常见 Nginx 日志格式: $remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent
# 状态码在第 9 列（awk 从 1 开始）
awk '{print $9}' "$LOG_FILE" | sort | uniq -c | sort -rn | awk '{
    printf "  %s : %d 次", $2, $1
    if ($2 ~ /^2/) printf " ✓"
    else if ($2 ~ /^3/) printf " ↪"
    else if ($2 ~ /^4/) printf " ⚠"
    else if ($2 ~ /^5/) printf " ✗"
    printf "\n"
}'

# 计算成功率
SUCCESS=$(awk '$9 ~ /^2[0-9][0-9]$/' "$LOG_FILE" | wc -l)
CLIENT_ERR=$(awk '$9 ~ /^4[0-9][0-9]$/' "$LOG_FILE" | wc -l)
SERVER_ERR=$(awk '$9 ~ /^5[0-9][0-9]$/' "$LOG_FILE" | wc -l)
echo "  ---"
echo "  2xx 成功: $SUCCESS ($(echo "scale=2; $SUCCESS*100/$TOTAL_LINES" | bc)%)"
echo "  4xx 客户端错误: $CLIENT_ERR"
echo "  5xx 服务端错误: $SERVER_ERR"

# ---------- 慢请求分析（响应时间 > 1s）----------
echo ""
echo "### 3. 慢请求分析（响应时间 > 1s）"
echo "-----------------------------"
# 最后一个字段是响应时间（秒）
awk '$NF > 1.0 {printf "  %6.3fs  %s %s\n", $NF, $6, $7}' "$LOG_FILE" | sort -rn | head -10

SLOW_COUNT=$(awk '$NF > 1.0' "$LOG_FILE" | wc -l)
echo "  慢请求总数: $SLOW_COUNT"

# ---------- Top 10 IP ----------
echo ""
echo "### 4. Top 10 请求来源 IP"
echo "-----------------------------"
awk '{print $1}' "$LOG_FILE" | sort | uniq -c | sort -rn | head -10 | awk '{
    printf "  %s : %d 次\n", $2, $1
}'

# ---------- 请求方法统计 ----------
echo ""
echo "### 5. HTTP 方法分布"
echo "-----------------------------"
# 方法在第 6 列，去掉引号
awk '{gsub(/"/, "", $6); print $6}' "$LOG_FILE" | sort | uniq -c | sort -rn | awk '{
    printf "  %s : %d 次\n", $2, $1
}'

# ---------- 端点访问统计 ----------
echo ""
echo "### 6. Top 5 请求端点"
echo "-----------------------------"
awk '{gsub(/"/, "", $7); print $7}' "$LOG_FILE" | sort | uniq -c | sort -rn | head -5 | awk '{
    printf "  %s : %d 次\n", $2, $1
}'

# ---------- 保存报告 ----------
{
    echo "Nginx 访问日志分析报告"
    echo "======================="
    echo "日志: $LOG_FILE"
    echo "时间: $(date)"
    echo "总请求: $TOTAL_LINES"
    echo "成功率: $SUCCESS/$TOTAL_LINES ($(echo "scale=2; $SUCCESS*100/$TOTAL_LINES" | bc)%)"
    echo "慢请求: $SLOW_COUNT"
    echo "5xx错误: $SERVER_ERR"
} > "$REPORT_FILE"

echo ""
echo "============================================"
echo "报告已保存: $REPORT_FILE"
echo "============================================"