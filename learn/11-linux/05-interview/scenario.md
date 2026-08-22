# Linux 场景题

> 面向 Java 后端开发者的 Linux 实战场景，考察故障排查和问题解决能力。

---

## 场景 1：CPU 飙高

### 现象

线上 Java 应用突然响应缓慢，`top` 显示 CPU 使用率 100%。

### 排查步骤

```bash
# 1. 找到 CPU 最高的进程
top -c

# 2. 找到进程内 CPU 最高的线程
top -H -p <PID>

# 3. 将线程 ID 转十六进制
printf "%x\n" <TID>

# 4. 获取线程堆栈
jstack <PID> | grep -A 30 "0x<TID_HEX>"

# 5. 多次采样对比
for i in {1..5}; do
    top -H -p <PID> -b -n 1 | tail -n +8 | awk '{print $1, $9}' >> /tmp/cpu.log
    sleep 3
done
```

### 常见原因及解决方案

| 原因 | 堆栈特征 | 解决 |
|------|----------|------|
| 死循环 | `while(true)` 或 `for(;;)` | 检查循环退出条件，添加超时机制 |
| 频繁 Full GC | GC 线程占用 CPU | `jstat -gcutil` 查看，调整堆大小 |
| 正则回溯 | `java.util.regex.Pattern` | 优化正则，使用 `Pattern.compile` 预编译 |
| 线程自旋 | `sun.misc.Unsafe.park` | 检查锁竞争，优化同步代码 |
| 反序列化 | `ObjectInputStream.readObject` | 限制反序列化大小，使用白名单 |

### 预防措施

- 配置 JVM 参数 `-XX:+ThreadDumpOnOutOfMemoryError`
- 使用 APM 工具（如 SkyWalking、Arthas）监控线程状态
- 设置 CPU 使用率告警阈值

---

## 场景 2：OOM（内存溢出）

### 现象

Java 应用报错 `java.lang.OutOfMemoryError: Java heap space`，或进程被 OOM Killer 杀死。

### 排查步骤

```bash
# 1. 确认 OOM 类型
# Java heap space — 堆内存不足
# Metaspace — 元空间不足
# Direct buffer memory — 直接内存不足
# unable to create new native thread — 线程数超限

# 2. 查看堆内存使用
jmap -heap <PID>
jmap -histo <PID> | head -30

# 3. 生成 heap dump（如果启动时未配置自动 dump）
jmap -dump:format=b,file=/tmp/dump.hprof <PID>

# 4. 查看 GC 情况
jstat -gcutil <PID> 1000

# 5. 查看系统内存
free -h
cat /proc/meminfo

# 6. 查看进程内存映射
pmap -x <PID> | tail -20
```

### 常见原因及解决方案

| 原因 | 特征 | 解决 |
|------|------|------|
| 堆内存泄漏 | 老年代持续增长，Full GC 无法回收 | 使用 MAT 分析 dump，定位泄漏对象 |
| 大对象 | 直接进入老年代的大数组/集合 | 检查是否一次性加载过多数据 |
| 元空间泄漏 | 频繁类加载/卸载 | 检查热部署、动态代理、反射 |
| 直接内存泄漏 | Netty 等框架使用 DirectBuffer | 使用 `-XX:MaxDirectMemorySize` 限制 |
| 线程溢出 | 创建了过多线程 | 检查线程池配置，使用信号量限制 |

### 预防措施

```bash
# JVM 参数（启动时配置）
JAVA_OPTS="
    -Xms2g -Xmx2g
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/var/log/app/dump.hprof
    -XX:MetaspaceSize=256m
    -XX:MaxMetaspaceSize=512m
    -XX:+PrintGCDetails
    -XX:+PrintGCDateStamps
    -Xloggc:/var/log/app/gc.log
"
```

---

## 场景 3：磁盘写满

### 现象

应用报错 `No space left on device`，或数据库写入失败。

### 排查步骤

```bash
# 1. 确认磁盘空间
df -h

# 2. 找到大目录
du -sh /* | sort -rh | head -10
du -sh /var/log/* | sort -rh | head -10

# 3. 找到大文件
find / -type f -size +500M -exec ls -lh {} \; 2>/dev/null

# 4. 检查 inode 是否耗尽
df -i

# 5. 查找已删除但仍在占用的文件
lsof | grep deleted

# 6. 查看日志文件大小
ls -lh /var/log/app/*.log
```

### 常见原因及解决方案

| 原因 | 排查方法 | 解决 |
|------|----------|------|
| 日志未轮转 | `ls -lh /var/log/` | 配置 logrotate |
| 大日志文件 | `tail -f` 未清理 | 清空日志 `> app.log` |
| 已删除但未释放 | `lsof \| grep deleted` | 重启进程 |
| 数据库 binlog | `ls -lh /var/lib/mysql/` | 配置过期清理 |
| 临时文件 | `du -sh /tmp/*` | 清理临时目录 |
| Docker 日志 | `ls -lh /var/lib/docker/containers/` | 限制日志大小 |

### 预防措施

```bash
# logrotate 示例配置
# /etc/logrotate.d/app
/var/log/app/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    copytruncate
    maxsize 500M
}

# Docker 容器日志限制
# docker-compose.yml
logging:
  driver: "json-file"
  options:
    max-size: "100m"
    max-file: "3"
```

---

## 场景 4：网络超时

### 现象

服务间调用频繁超时，`FeignClient` 或 `RestTemplate` 报 `Read timed out`。

### 排查步骤

```bash
# 1. 确认目标服务是否存活
systemctl status mall-user-service
ps aux | grep mall-user

# 2. 检查端口监听
ss -tlnp | grep 8081

# 3. 测试连通性
telnet 192.168.1.100 8081

# 4. 测试 HTTP 健康检查
curl -v --connect-timeout 5 --max-time 10 http://192.168.1.100:8081/actuator/health

# 5. 检查网络延迟
ping -c 10 192.168.1.100

# 6. 检查路由
traceroute 192.168.1.100

# 7. 抓包分析
sudo tcpdump -i any port 8081 -n

# 8. 检查连接数
ss -s | grep estab
ss -t state time-wait | wc -l
```

### 常见原因及解决方案

| 原因 | 特征 | 解决 |
|------|------|------|
| 服务未启动 | 端口未监听 | 启动服务 |
| 防火墙拦截 | telnet 超时 | 放行端口 |
| 连接池耗尽 | 大量 TIME_WAIT | 优化连接池配置，启用连接复用 |
| DNS 解析失败 | nslookup 超时 | 检查 DNS 配置，使用 hosts |
| 带宽饱和 | 网络延迟高 | 扩容带宽，优化传输 |
| 服务过载 | 响应慢，队列堆积 | 扩容实例，限流 |

### 预防措施

```bash
# 内核参数优化
# /etc/sysctl.d/99-network.conf
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15
net.ipv4.tcp_keepalive_time = 120
net.ipv4.ip_local_port_range = 1024 65000
net.core.somaxconn = 1024

# 应用层优化
# RestTemplate 配置连接池
# 启用 HTTP 连接复用（Keep-Alive）
# 设置合理的超时时间
```

---

## 场景 5：服务启动失败

### 现象

Java 应用 `systemctl start` 后立即退出，或启动后无法访问。

### 排查步骤

```bash
# 1. 查看服务状态
systemctl status mall-gateway
journalctl -u mall-gateway -n 100 --no-pager

# 2. 直接启动看标准输出
sudo -u appuser java -jar /opt/app/mall-gateway.jar

# 3. 检查端口是否被占用
ss -tlnp | grep 8080

# 4. 检查配置文件
# 检查 application.yml 中的数据库连接等配置
# 检查是否有语法错误

# 5. 检查 JVM 参数
# 是否设置了 -Xmx 超过可用内存

# 6. 检查依赖服务
# MySQL、Redis 等是否可用
telnet localhost 3306
```

### 常见原因及解决方案

| 原因 | 特征 | 解决 |
|------|------|------|
| 端口被占用 | BindException | 检查端口占用，修改端口 |
| 配置错误 | 启动日志中有异常 | 检查配置文件 |
| 数据库无法连接 | 启动时连接失败 | 检查数据库状态和连接串 |
| 内存不足 | JVM 无法分配内存 | 降低 -Xmx 或增加内存 |
| 依赖服务未就绪 | 启动脚本中未等待依赖 | 使用 systemd After 依赖 |

---

## 场景 6：Shell 脚本调试

### 现象

Shell 脚本执行结果不符合预期。

### 调试技巧

```bash
# 1. 启用调试模式
bash -x script.sh          # 打印每条命令的执行过程
#!/bin/bash -x             # 脚本中启用
set -x                     # 局部启用
set +x                     # 关闭调试

# 2. 检查语法
bash -n script.sh          # 语法检查，不执行

# 3. 捕获错误
set -e                     # 任何命令失败退出
set -u                     # 使用未定义变量报错
set -o pipefail            # 管道中任何命令失败退出

# 4. 使用 trap 调试
trap 'echo "Line $LINENO: $BASH_COMMAND"' DEBUG
trap 'echo "Error at line $LINENO"' ERR

# 5. 检查变量
echo "DEBUG: VAR=${VAR}"
```

---

## 场景总结速查

| 场景 | 核心命令 | 关键指标 |
|------|----------|----------|
| CPU 飙高 | top, jstack, perf | CPU us > 90% |
| OOM | jmap, jstat, free | 老年代持续增长 |
| 磁盘写满 | df, du, lsof | Use% > 90% |
| 网络超时 | ping, telnet, tcpdump | 延迟 > 10ms, 丢包 |
| 端口冲突 | ss, netstat | BindException |
| 连接数过多 | ss -s, ss state | TIME_WAIT > 10000 |