# Linux 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| 文件权限 | rwxrwxrwx (所有者/组/其他人)，数字 755=rwxr-xr-x | chmod 777 是最不安全的做法，生产环境禁 |
| 进程状态 | R(运行)、S(可中断睡眠)、D(不可中断，IO 等待)、Z(僵尸)、T(停止) | D 进程太多的原因是 IO 慢；Z 进程父进程没 wait 回收 |
| 文件描述符 | 0 标准输入 / 1 标准输出 / 2 标准错误 | 2>&1 重定向标准错误到标准输出，> 覆盖 >> 追加 |
| 管道 | 前一个命令的输出作为后一个命令的输入 | 管道有缓冲，且只在子 shell 中运行 |
| 硬链接 vs 软链接 | 硬链接共享 inode(不能跨文件系统)，软链接是路径引用 | 硬链接不能对目录，软链接可以；软链接路径移动即失效 |
| Swap 分区 | 磁盘空间当内存用，系统内存不足时触发 | 使用 swap 时系统性能急剧下降，应监控 swap 使用率 |
| OOM Killer | 内存不足时内核选择进程杀掉(打分机制) | /var/log/messages 或 dmesg 查看 OOM 日志 |
| 文件系统 | ext4(默认)、xfs(大文件)、btrfs(快照) | df -h 看空间，df -i 看 inode，inode 满也会"磁盘满" |
| 系统调用 | 用户态→内核态的接口，open/read/write/close/fork | 大量系统调用是性能瓶颈，减少上下文切换 |
| 中断 | 硬件/软件中断，处理高优先级事件 | 软中断过多(如网络中断)会消耗大量 CPU，观察 /proc/softirqs |

## 🔧 常用命令/API

```bash
# ----- CPU 排查 -----
top -H                                      # 看线程级 CPU
htop                                        # 更友好的 top
mpstat -P ALL 1                             # 每个 CPU 核使用率
pidstat -p <pid> 1                          # 进程级 CPU 统计
uptime                                      # 1/5/15 分钟负载

# 示例：CPU 飙高 → 找进程 → 找线程 → 看栈
# top → shift+P 按 CPU 排序 → 记下 PID
# top -H -p <PID> → 找到 CPU 高的 TID
# printf "%x\n" <TID> → 转十六进制
# jstack <PID> | grep -A 30 <十六进制TID>  # 定位 Java 线程代码行
```

```bash
# ----- 内存排查 -----
free -h                                     # 内存概况
vmstat 1 5                                  # 内存/swap/IO/CPU
cat /proc/meminfo                           # 详细内存信息
ps aux --sort=-%mem | head -10              # 内存 TOP10
smem -s rss -r                              # 更准确的内存统计

# 示例：OOM 排查
dmesg | grep -i "killed process"            # 看 OOM 杀了谁
cat /var/log/messages | grep -i oom         # 系统日志找 OOM
```

```bash
# ----- 磁盘排查 -----
df -h                                       # 磁盘空间
df -i                                       # inode 使用
du -sh /* | sort -rh | head -10            # 根目录 TOP10 大目录
iostat -x 1 5                               # 磁盘 IO 详细（await/r/s/w/s/%util）
iotop                                       # 进程级 IO 监控

# 示例：磁盘写满 → 找大文件
find / -type f -size +100M -exec ls -lh {} \; | sort -k5 -rh | head -20
lsof | grep deleted                         # 找到已删除但未释放的文件
```

```bash
# ----- 网络排查 -----
netstat -tlnp                               # 监听端口
ss -tlnp                                    # 更快的 netstat 替代
tcpdump -i eth0 port 80 -w capture.pcap     # 抓包分析
curl -w "@curl-format" -o /dev/null -s URL  # 请求耗时分解
ping -c 5 target                             # 连通性
mtr target                                  # 路由追踪+丢包率

# 并发连接数
ss -s                                       # 统计汇总
netstat -n | awk '/^tcp/ {++S[$NF]} END {for(a in S) print a, S[a]}'
# 查看 TIME_WAIT/CLOSE_WAIT 数量
```

```bash
# ----- Shell 脚本常用 -----
# 检查端口监听
if ss -tlnp | grep -q ':8080'; then
    echo "Port 8080 is in use"
fi

# 简单的部署脚本模板
#!/bin/bash
set -euo pipefail                            # 出错即停、未定义变量报错、管道失败检测
APP_DIR="/opt/app"
BACKUP_DIR="/data/backup"
JAR="app.jar"

echo "=== Deploy Started ==="
cp "$APP_DIR/$JAR" "$BACKUP_DIR/$(date +%Y%m%d_%H%M%S)_$JAR"
systemctl stop app
cp /tmp/$JAR "$APP_DIR/"
systemctl start app
sleep 5
if systemctl is-active --quiet app; then
    echo "=== Deploy OK ==="
else
    echo "=== Deploy FAILED ==="
    systemctl status app
fi
```

```bash
# awk/sed 常用一行命令
awk '{print $1, $5}' access.log | sort | uniq -c | sort -rn | head -10   # 访问 IP TOP10
sed -i 's/old_host/new_host/g' config.properties                          # 批量替换
awk 'NR>1 && $9 ~ /^5../' access.log                                      # 5xx 状态码
```

## 🎯 面试高频 TOP10

1. **Q: CPU 飙高怎么排查？** **A:** top 找高 CPU 进程 PID → top -H -p PID 找线程 → 线程 ID 转十六进制 → jstack 定位代码行 → 看是业务繁忙还是死循环。
2. **Q: OOM 怎么排查？** **A:** dmesg | grep -i killed 看谁被杀 → free -m 看内存 → jmap -heap 看堆 → 分析堆转储(jmap -dump:live,format=b) → 或者用 MAT/JProfiler 分析。
3. **Q: 磁盘满了但 du 找不到大文件？** **A:** lsof | grep deleted 查已删除但未释放文件的进程，重启该进程即可释放空间(cat /dev/null > 也可以)。
4. **Q: 网络超时/延迟怎么排查？** **A:** ping 看基础连通性 → mtr 追踪路由看哪一跳延迟 → tcpdump 抓包分析重传 → curl -w 分解请求耗时 → 检查 DNS/防火墙/带宽限制。
5. **Q: Linux 启动流程？** **A:** BIOS/UEFI → 引导加载器(GRUB) → 加载内核 → 执行 init(PID 1) → systemd 并行启动目标单元 → 登录提示。
6. **Q: 软链接和硬链接区别？** **A:** 硬链接共享 inode(不可跨文件系统、不可对目录)，删除原文件链接还在；软链接是路径引用(可跨文件系统、可对目录)，原文件删除则失效。
7. **Q: TIME_WAIT 过多怎么办？** **A:** net.ipv4.tcp_tw_reuse=1(客户端重用) + tcp_tw_recycle(2.6 废弃，内核 4.10+ 移除) + 调短 tcp_fin_timeout；更优方案是改用长连接。
8. **Q: 进程状态 D(不可中断)过多怎么办？** **A:** D 是内核态 IO 等待，查看 IO 设备(磁盘/NFS 挂载) 是否卡顿，排查 IO 瓶颈(IOPS/延迟/硬盘故障)。
9. **Q: 僵尸进程怎么处理？** **A:** 僵尸进程是已结束但父进程未 wait 回收；kill 父进程(让 init 回收) 或修复父进程代码(正确 wait/waitpid)。
10. **Q: 如何查看系统瓶颈？** **A:** 使用 USE 法：所有资源(CPU/内存/磁盘/网络) 看利用率、饱和度、错误；配合 top/vmstat/iostat/sar/ss 逐一排查。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 直接 rm -rf 重要文件 | 先备份/移动，确认再删除 |
| 生产环境 chmod 777 | 最小权限原则：755 目录，644 文件，敏感文件 600 |
| 忘记设置 ulimit | 文件描述符/进程数限制，在高并发系统必须调大(如 65535) |
| 用 root 运行所有服务 | 创建专用用户，最小权限，防越权 |
| 忽略系统日志 | 定期检查 /var/log/ 下 messages/secure/syslog 等 |
| 不限制日志文件大小 | 配置 logrotate 轮转，防止磁盘被日志打满 |
| 编辑配置文件不加备份 | 修改前复制 .bak；修改后 diff 验证 |

## 📐 架构设计要点

- **运维标准化**：统一 OS 版本(如 CentOS 7/Ubuntu 22.04)、统一初始化脚本、配置管理(Ansible/SaltStack)。
- **监控告警全覆盖**：CPU/内存/磁盘/网络/进程/端口，指标 + 日志 + 告警三件套。
- **安全基线**：SSH 密钥登录、防火墙 iptables/firewalld、SELinux/AppArmor、审计(auditd)。
- **备份策略**：系统盘 + 数据盘 + 配置文件定期备份，异地容灾；3-2-1 原则(3 副本 2 介质 1 异地)。
- **内核参数优化**：网络(并发/缓冲区)、文件描述符、内存(swappiness)、IO 调度器。

## 🔗 关联技术

- **Docker**：容器基于 Linux 的 Namespace 和 Cgroup，docker 宿主机就是 Linux 系统。
- **Nginx**：反向代理 / 负载均衡 / 静态资源服务，部署在 Linux 上，需要调优。
- **Shell 脚本**：自动化部署、定时任务、日志清理、运维巡检。
- **Prometheus + Node Exporter**：采集 Linux 主机指标，可视化监控。
- **K8s**：Kubernetes 节点就是 Linux 机器，节点资源管理依赖 Linux 内核特性。