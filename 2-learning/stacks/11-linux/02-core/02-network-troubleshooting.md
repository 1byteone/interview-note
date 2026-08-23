# 网络排查

> 微服务架构中，服务间网络通信是基石。本章教你从 TCP/IP 基础到实战排查，快速定位网络问题。

---

## 1. 网络基础

### TCP/IP 协议栈

```
应用层    HTTP/HTTPS/SSH/DNS    ← 你的 Java 应用在这里
传输层    TCP/UDP               ← 端口号
网络层    IP                    ← IP 地址
链路层    以太网/WiFi           ← MAC 地址
```

### OSI 七层模型（面试常考）

| 层 | 名称 | 协议 | 排查工具 |
|----|------|------|----------|
| 7 | 应用层 | HTTP, DNS, SSH | curl, nc |
| 6 | 表示层 | SSL/TLS | openssl |
| 5 | 会话层 | Sockets | ss, netstat |
| 4 | 传输层 | TCP, UDP | ss, telnet |
| 3 | 网络层 | IP, ICMP | ping, traceroute |
| 2 | 数据链路层 | 以太网 | tcpdump |
| 1 | 物理层 | 网线/光纤 | 硬件检查 |

### 三次握手与四次挥手

```
三次握手（建立连接）：
  Client                    Server
    |  SYN=1, seq=x          |
    |──────────────────────→ |
    |  SYN=1, ACK=1, seq=y  |
    |←────────────────────── |
    |  ACK=1, seq=x+1       |
    |──────────────────────→ |

四次挥手（关闭连接）：
  Client                    Server
    |  FIN=1                 |
    |──────────────────────→ |
    |  ACK=1                 |
    |←────────────────────── |
    |  FIN=1                 |
    |←────────────────────── |
    |  ACK=1                 |
    |──────────────────────→ |
```

---

## 2. 网络排查命令

### ping — 连通性测试

```bash
# 基本测试
ping -c 5 192.168.1.1         # 发送 5 个包
ping -c 5 baidu.com            # 测试 DNS 解析+连通性

# 结果解读
64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=0.3ms
                              ↑       ↑           ↑
                              |       |           └── 往返时间
                              |       └── 生存时间（每经过一个路由器减1）
                              └── 序号（丢包时会有缺失）
```

### telnet — 端口连通性测试

```bash
# 测试端口是否开放
telnet 192.168.1.100 8080

# 如果连接成功，会显示 Trying...Connected...
# 如果连接失败，会显示 Connection refused 或 timeout
```

### curl — HTTP 接口测试

```bash
# 基本用法
curl http://localhost:8080/actuator/health
curl -I http://localhost:8080/api/order      # 只显示响应头
curl -v http://localhost:8080/api/order      # 显示详细交互

# 常见场景
curl -X POST http://localhost:8080/api/order \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "amount": 99.9}'

# 超时控制
curl --connect-timeout 5 --max-time 10 http://localhost:8080

# 跟踪重定向
curl -L http://localhost:8080

# 状态码检查
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080
```

### traceroute — 路由追踪

```bash
# 追踪到目标的路由路径
traceroute 192.168.1.100

# 输出示例
 1  192.168.1.1 (192.168.1.1)  0.5ms  0.3ms  0.4ms
 2  10.0.0.1 (10.0.0.1)      2.1ms  1.9ms  2.0ms
 3  172.16.0.1 (172.16.0.1)  8.5ms  8.2ms  8.3ms
 4  * * *                      # 节点不响应 ICMP
```

### ss / netstat — 套接字查看

```bash
# 查看监听端口（推荐 ss）
ss -tlnp                        # TCP 监听端口
ss -ulnp                        # UDP 监听端口
ss -tunap                       # 所有连接
ss -tlnp | grep 8080            # 查看特定端口

# netstat（需要安装）
netstat -tlnp
netstat -an | grep ESTABLISHED  # 查看已建立的连接
netstat -s                      # 网络统计
```

**输出解读：**

```
State      Recv-Q Send-Q  Local Address:Port   Peer Address:Port
LISTEN     0      50      0.0.0.0:8080         0.0.0.0:*         ← 监听所有网卡
LISTEN     0      50      127.0.0.1:8080       0.0.0.0:*         ← 仅监听本地
ESTAB      0      0       192.168.1.2:8080     10.0.0.2:56432    ← 已建立连接
TIME_WAIT  0      0       192.168.1.2:8080     10.0.0.2:56433    ← 主动关闭后的等待
```

---

## 3. tcpdump 抓包分析

### 基本用法

```bash
# 安装
sudo apt install tcpdump -y

# 抓取所有包
sudo tcpdump -i eth0

# 抓取特定端口
sudo tcpdump -i eth0 port 8080

# 抓取特定主机
sudo tcpdump -i eth0 host 192.168.1.100

# 保存到文件
sudo tcpdump -i eth0 -w capture.pcap port 8080

# 读取文件
tcpdump -r capture.pcap -n
```

### 实战：排查连接超时

```bash
# 抓取三次握手过程
sudo tcpdump -i eth0 -n "host 192.168.1.100 and port 8080"

# 输出示例
# 21:00:00.123456 IP 192.168.1.2.56432 > 192.168.1.100.8080: Flags [S], seq 1000
# 21:00:00.123789 IP 192.168.1.100.8080 > 192.168.1.2.56432: Flags [S.], seq 2000, ack 1001
# 21:00:00.123890 IP 192.168.1.2.56432 > 192.168.1.100.8080: Flags [.], ack 2001

# 如果看到 SYN 重传（Flags [S] 重复出现），说明目标不可达
# 21:00:00.123456 IP 192.168.1.2.56432 > 192.168.1.100.8080: Flags [S], seq 1000
# 21:00:03.123456 IP 192.168.1.2.56432 > 192.168.1.100.8080: Flags [S], seq 1000  ← 重传
# 21:00:09.123456 IP 192.168.1.2.56432 > 192.168.1.100.8080: Flags [S], seq 1000  ← 再次重传
```

---

## 4. DNS 排查

### 常用命令

```bash
# nslookup
nslookup mall-api.example.com
# Server:         192.168.1.1
# Address:        192.168.1.1#53
# Name:           mall-api.example.com
# Address:        10.0.0.100

# dig（更详细）
dig mall-api.example.com
dig +short mall-api.example.com          # 精简输出
dig @8.8.8.8 mall-api.example.com        # 指定 DNS 服务器

# hosts 文件
cat /etc/hosts
# 格式：IP 地址    域名
# 127.0.0.1  localhost
# 10.0.0.100 mall-api.example.com

# 查看 DNS 配置
cat /etc/resolv.conf
# nameserver 192.168.1.1
# nameserver 8.8.8.8
```

### 域名解析问题排查

```bash
# 1. 先 ping IP 确认网络连通
ping -c 3 10.0.0.100

# 2. 再 ping 域名确认 DNS 解析
ping -c 3 mall-api.example.com

# 3. 如果域名能 ping 但服务连不上，检查 DNS 缓存
# 查看 DNS 缓存
sudo systemd-resolve --statistics

# 清除 DNS 缓存
sudo systemd-resolve --flush-caches

# 4. 使用 hosts 文件临时绕过 DNS
echo "10.0.0.100 mall-api.example.com" >> /etc/hosts
```

---

## 5. 实战：排查连接超时问题

### 场景

Java 应用通过 `http://user-service:8081` 调用用户服务，出现连接超时。

### 排查步骤

```bash
# Step 1: 检查服务是否在监听
ss -tlnp | grep 8081
# 如果没输出，说明服务没启动

# Step 2: 检查 DNS 解析
nslookup user-service
# 如果解析失败，检查 /etc/hosts 或 DNS 配置

# Step 3: 检查连通性
telnet user-service 8081
# 如果卡住，说明防火墙或网络不通

# Step 4: 检查防火墙
sudo iptables -L -n | grep 8081
# 如果有 DROP 规则，需要放行

# Step 5: 抓包分析
sudo tcpdump -i any port 8081 -n
# 观察是否有 SYN 包发出，是否有 SYN+ACK 回复

# Step 6: 检查 Java 应用连接池
# 查看应用日志，是否有 connection pool exhausted 错误
```

### 常见问题速查

| 现象 | 可能原因 | 排查命令 |
|------|----------|----------|
| ping 不通 | 网络不通、防火墙拦截 | ping, traceroute |
| telnet 端口不通 | 服务未启动、防火墙拦截 | telnet, ss |
| 连接超时 | 防火墙丢包、路由问题、服务过载 | tcpdump, ss |
| DNS 解析失败 | DNS 配置错误、域名不存在 | nslookup, dig |
| 连接被拒绝 | 服务未监听、端口错误 | ss, curl |
| 连接重置 | 服务崩溃、连接池满、超时 | tcpdump, 应用日志 |

---

## 总结

本章你学会了：

- TCP/IP 协议栈和 OSI 七层模型
- 使用 ping/telnet/curl/traceroute 排查网络问题
- 使用 ss/netstat 查看端口和连接状态
- 使用 tcpdump 抓包分析网络交互
- DNS 排查和 hosts 配置
- 连接超时问题的系统化排查流程

下一步：学习 [性能调优](../03-advanced/01-performance-tuning.md) 排查 Java 应用性能问题。