#!/bin/bash
# ============================================
# 网络故障排查工具集
# 功能：Ping / Traceroute / 端口检查 / DNS / curl / tcpdump
# 用法：bash 03_network_check.sh <target_host> [port]
# 示例：bash 03_network_check.sh google.com 443
# ============================================

set -euo pipefail

TARGET="${1:-google.com}"
PORT="${2:-80}"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

echo "============================================"
echo "  网络诊断报告"
echo "  目标: $TARGET:$PORT"
echo "  时间: $TIMESTAMP"
echo "============================================"

# ---------- 1. DNS 解析 ----------
dns_check() {
    echo ""
    echo "### 1. DNS 解析"
    echo "-----------------------------"
    # 使用 host 或 nslookup 或 dig 解析域名
    if command -v host &>/dev/null; then
        host "$TARGET" 2>&1 | head -5
    elif command -v nslookup &>/dev/null; then
        nslookup "$TARGET" 2>&1 | head -10
    elif command -v dig &>/dev/null; then
        dig +short "$TARGET" 2>&1
    else
        # 使用 getent（glibc 内置）
        getent hosts "$TARGET" 2>&1 || echo "DNS 解析失败"
    fi
}

# ---------- 2. Ping 测试 ----------
ping_check() {
    echo ""
    echo "### 2. Ping 连通性测试"
    echo "-----------------------------"
    if command -v ping &>/dev/null; then
        ping -c 4 -W 3 "$TARGET" 2>&1 || echo "Ping 失败（可能被防火墙阻止）"
    else
        echo "ping 命令不可用"
    fi
}

# ---------- 3. Traceroute 路由追踪 ----------
traceroute_check() {
    echo ""
    echo "### 3. 路由追踪"
    echo "-----------------------------"
    if command -v traceroute &>/dev/null; then
        traceroute -m 15 -n "$TARGET" 2>&1 | head -10
    elif command -v tracert &>/dev/null; then
        tracert "$TARGET" 2>&1 | head -10
    else
        echo "traceroute 命令不可用，使用 mtr 替代..."
        mtr -r -c 3 -n "$TARGET" 2>&1 | head -10 || echo "路由追踪不可用"
    fi
}

# ---------- 4. 端口检查 ----------
port_check() {
    echo ""
    echo "### 4. 端口检查 :$PORT"
    echo "-----------------------------"
    # 优先使用 nc，其次 ss
    if command -v nc &>/dev/null; then
        if nc -zv -w 5 "$TARGET" "$PORT" 2>&1; then
            echo "端口 $PORT: OPEN ✓"
        else
            echo "端口 $PORT: CLOSED ✗"
        fi
    else
        # 使用 /dev/tcp 内置（bash 特性）
        timeout 5 bash -c "echo >/dev/tcp/$TARGET/$PORT" 2>/dev/null \
            && echo "端口 $PORT: OPEN ✓" \
            || echo "端口 $PORT: CLOSED ✗"
    fi

    # 使用 ss 查看本地监听端口
    echo ""
    echo "本地监听端口（常见服务）:"
    ss -tlnp 2>/dev/null | head -10 || netstat -tlnp 2>/dev/null | head -10 || echo "ss/netstat 不可用"
}

# ---------- 5. HTTP 端点测试 ----------
curl_check() {
    echo ""
    echo "### 5. HTTP 端点测试"
    echo "-----------------------------"
    if command -v curl &>/dev/null; then
        # 基本连接测试
        echo "HTTP 响应头:"
        curl -sI --connect-timeout 5 -o /dev/null -w "\
  HTTP 状态码: %{http_code}\n\
  总耗时: %{time_total}s\n\
  DNS 解析: %{time_namelookup}s\n\
  TCP 连接: %{time_connect}s\n\
  TLS 握手: %{time_appconnect}s\n\
  首字节: %{time_starttransfer}s\n" "http://${TARGET}:${PORT}/" 2>&1 || echo "HTTP 请求失败"
    else
        echo "curl 不可用，使用 wget 替代..."
        timeout 5 wget -q -O /dev/null "http://${TARGET}:${PORT}/" \
            && echo "HTTP 连接成功" || echo "HTTP 请求失败"
    fi
}

# ---------- 6. tcpdump 抓包示例（需要 root） ----------
tcpdump_example() {
    echo ""
    echo "### 6. tcpdump 抓包示例（需 root 权限）"
    echo "-----------------------------"
    if command -v tcpdump &>/dev/null; then
        echo "使用方法（需 sudo）："
        echo "  # 抓取目标 IP 的流量，保存到文件"
        echo "  sudo tcpdump -i eth0 host $(getent hosts "$TARGET" 2>/dev/null | awk '{print $1; exit}') -w capture.pcap"
        echo ""
        echo "  # 实时查看 HTTP 请求"
        echo "  sudo tcpdump -i any -A port 80 | grep -E 'GET|POST|Host:'"
        echo ""
        echo "  # 分析已保存的 pcap"
        echo "  tcpdump -r capture.pcap -X"
        echo ""
        echo "  # 统计各协议流量"
        echo "  sudo tcpdump -i any -nn -c 1000 2>/dev/null | awk -F' ' '{print \$3}' | sort | uniq -c | sort -rn"
    else
        echo "tcpdump 未安装"
    fi
}

# ---------- 执行全部检查 ----------
dns_check
ping_check
traceroute_check
port_check
curl_check
tcpdump_example

echo ""
echo "============================================"
echo "  网络诊断完成"
echo "============================================"