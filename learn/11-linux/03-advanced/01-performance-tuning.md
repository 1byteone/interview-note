# 性能调优

> 当 Java 应用出现 CPU 飙高、内存溢出、磁盘写满、网络延迟时，你需要快速定位瓶颈。本章教你系统化的性能排查方法。

---

## 1. CPU 排查

### top — 实时监控

```bash
# 启动 top
top

# 在 top 界面中的快捷键
P            # 按 CPU 使用率排序
M            # 按内存使用率排序
1            # 显示每个 CPU 核心
c            # 显示完整命令行
q            # 退出

# 输出解读
%Cpu(s): 85.0 us, 10.0 sy, 0.0 ni, 5.0 id, 0.0 wa
          ↑        ↑                  ↑         ↑
          |        |                  |         └── IO 等待
          |        |                  └── 空闲
          |        └── 系统态（内核）
          └── 用户态（应用）
```

### vmstat — 系统整体状况

```bash
# 每2秒输出一次，共5次
vmstat 2 5

# 输出解读
procs -----------memory---------- ---swap-- -----io---- -system-- ------cpu-----
 r  b   swpd   free   buff  cache   si   so    bi    bo   in   cs us sy id wa st
 3  0      0 1024M   200M   2.0G    0    0    10    20  500  800 85 10  5  0  0

 r（运行队列） — 超过 CPU 核心数*2 说明 CPU 不足
 b（阻塞进程） — 值高说明 IO 密集
 us（用户态）  — 应用占 CPU 比例
 wa（IO 等待） — 值高说明磁盘是瓶颈
```

### perf — 性能分析器

```bash
# 安装
sudo apt install linux-tools-common -y

# 采样 CPU 调用栈
sudo perf top -p <PID>

# 记录性能数据
sudo perf record -p <PID> -g --sleep 30
sudo perf report
```

### 实战：Java 应用 CPU 100% 排查

```bash
# 1. 找到 CPU 最高的进程
top -c

# 2. 找到进程内 CPU 最高的线程
top -H -p <PID>

# 3. 将线程 ID 转为十六进制
printf "%x\n" <THREAD_ID>
# 输出: 3a8f

# 4. 获取线程堆栈
jstack <PID> | grep -A 50 "0x3a8f"

# 5. 或者使用 jstack 找到所有 CPU 高的线程
jstack <PID> > /tmp/threaddump.txt
# 在 dump 中搜索 nid=0x3a8f

# 6. 或者使用一条命令
top -H -p <PID> -b -n 1 | awk '{print $1}' | tail -n +8 | while read tid; do
    printf "Thread: %s (0x%x)\n" "$tid" "$tid"
    jstack <PID> | grep -A 30 "nid=0x$(printf '%x' $tid)"
done
```

**常见 CPU 100% 原因：**

| 原因 | 堆栈特征 | 解决 |
|------|----------|------|
| 死循环 | while(true) / for(;;) | 检查循环退出条件 |
| 频繁 GC | GC 线程占用 > 50% | 调整堆大小、GC 策略 |
| 正则回溯 | Pattern.matcher 调用 | 优化正则表达式 |
| 线程自旋 | Unsafe.park | 检查锁竞争 |
| 计算密集 | 正常业务逻辑 | 考虑扩容或优化算法 |

---

## 2. 内存排查

### free — 内存使用概览

```bash
free -h
#               total        used        free      shared  buff/cache   available
# Mem:           7.6G        3.2G        1.5G        200M        2.9G        3.8G
# Swap:          2.0G        0.0K        2.0G

# available 是真正可用的内存（包括可回收的缓存）
# 当 available 接近 0 时，系统可能触发 OOM Killer
```

### sar — 历史趋势分析

```bash
# 安装
sudo apt install sysstat -y

# 查看内存使用趋势
sar -r -s 09:00:00 -e 18:00:00

# 查看 swap 使用
sar -S -s 09:00:00 -e 18:00:00

# 内存页错误
sar -B 1 5
```

### slabtop — 内核 slab 缓存

```bash
# 查看内核对象缓存占用
slabtop -s c

# 如果 dentry（目录缓存）或 inode_cache 占用过高
# 可能是文件句柄泄漏或大量小文件
```

### 实战：Java 应用 OOM 排查

```bash
# 1. 启动时添加 JVM 参数
JAVA_OPTS="-Xms2g -Xmx2g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/dump.hprof"

# 2. OOM 时自动生成 heap dump

# 3. 分析 dump 文件
# 使用 Eclipse MAT 或 jhat
jhat /tmp/dump.hprof

# 4. 查看进程内存映射
pmap -x <PID>

# 5. 查看堆内存使用
jmap -heap <PID>
jmap -histo <PID> | head -30

# 6. 实时监控 GC
jstat -gcutil <PID> 1000
#  S0    S1    E     O     M    YGC   YGCT   FGC  FGCT
# 0.00  0.00  45.2  78.5  92.3  1250  8.250   5   3.450
```

**常见内存问题：**

| 问题 | 现象 | 排查方法 |
|------|------|----------|
| 堆内存泄漏 | OOM、GC 频率高 | jmap -histo, MAT 分析 |
| 堆外内存泄漏 | top 显示 RSS 高但堆小 | pmap, NMT( Native Memory Tracking) |
| 元空间泄漏 | 频繁 Full GC | 检查类加载器泄漏 |
| 直接内存 | Netty 应用内存飙高 | -XX:MaxDirectMemorySize |

---

## 3. 磁盘排查

### df — 磁盘空间

```bash
df -h
# Filesystem      Size  Used Avail Use% Mounted on
# /dev/sda1       100G   85G   15G  85% /
# /dev/sdb1       500G  200G  300G  40% /data

# 定位大目录
du -sh /* 2>/dev/null | sort -rh | head -10
du -sh /var/log/* 2>/dev/null | sort -rh | head -10
```

### iostat — 磁盘 IO 性能

```bash
# 每2秒输出
iostat -x 2 5

# 输出解读
Device  r/s   w/s  rkB/s  wkB/s  await  svctm  %util
sda     100   200   5000   10000   5.2    0.5    15.0

 r/s/w/s    — 每秒读写次数
 rkB/s/wkB/s— 每秒读写数据量
 await      — IO 平均等待时间（ms），> 10ms 说明磁盘慢
 %util      — 磁盘利用率，> 80% 说明磁盘饱和
```

### iotop — 查看进程 IO

```bash
# 安装
sudo apt install iotop -y

# 查看哪些进程在大量读写磁盘
sudo iotop -oP
```

### 磁盘写满排查

```bash
# 1. 找到大文件
find / -type f -size +1G -exec ls -lh {} \; 2>/dev/null

# 2. 找到已删除但仍在占用的文件（进程未释放）
lsof | grep deleted

# 3. 清理日志
# 清空日志（不删除文件）
> /var/log/app/application.log

# 日志轮转配置
cat /etc/logrotate.d/app
# /var/log/app/*.log {
#     daily
#     rotate 7
#     compress
#     delaycompress
#     missingok
#     notifempty
#     copytruncate
# }
```

---

## 4. 网络排查（性能视角）

### 查看网络延迟

```bash
# 使用 ping 测延迟
ping -c 10 192.168.1.100

# 使用 ss 查看连接状态统计
ss -s
# Total: 1234 (kernel 0)
# TCP:   567 (estab 456, closed 89, orphaned 12, synrecv 0, timewait 22)
# 注意 timewait 过多说明短连接频繁创建

# 查看网络接口统计
ip -s link show eth0
# 注意 errors、dropped、overruns 字段
```

### 关键性能指标

| 指标 | 正常范围 | 告警阈值 | 排查方法 |
|------|----------|----------|----------|
| CPU us | < 70% | > 90% | top, perf |
| CPU wa | < 5% | > 20% | iostat, iotop |
| 内存 available | > 20% | < 10% | free, sar |
| 磁盘 %util | < 60% | > 80% | iostat -x |
| 磁盘 await | < 5ms | > 10ms | iostat -x |
| 网络延迟 | < 1ms(内网) | > 10ms | ping |
| 网络丢包 | 0% | > 0.1% | ping, ip -s |
| TCP timewait | < 10000 | > 30000 | ss -s |

---

## 总结

本章你学会了：

- 使用 top/vmstat/perf 排查 CPU 瓶颈
- Java 应用 CPU 100% 的排查流程（top → top -H → jstack）
- 使用 free/sar/jmap 排查内存问题
- OOM 时自动 dump 和分析方法
- 使用 df/iostat/iotop 排查磁盘问题
- 网络性能指标和监控方法

下一步：学习 [安全加固](02-security-hardening.md) 保护你的服务器。