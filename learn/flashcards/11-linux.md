# Linux — 面试抽认卡

> 来源：`learn/11-linux/05-interview/`

---

### Card 1: 文件权限管理
**维度**: 📝速记 | **难度**: ⭐

> **Q: Linux 文件权限 `rwxr-xr--` 如何解读？chmod 755 和 644 分别代表什么？**

**A:** 10 位字符：第 1 位文件类型（`-` 文件，`d` 目录，`l` 软链接），后 9 位每 3 位一组（所有者/组/其他）。`rwx`=4+2+1=7（读+写+执行），`r-x`=4+0+1=5（读+执行），`r--`=4+0+0=4（只读）。755：所有者 rwx，组 r-x，其他 r-x（可执行文件/目录）。644：所有者 rw-，组 r--，其他 r--（普通文件）。`chmod u+x file` 给所有者加执行权限，`chmod -R 755 dir` 递归修改。

---

### Card 2: 进程管理命令
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Linux 进程管理有哪些常用命令？如何定位进程问题？**

**A:** `ps aux`（查看所有进程，CPU/内存占用）、`top`（实时进程监控，按 P 按 CPU 排序，按 M 按内存排序）、`htop`（增强版 top，交互式）、`kill -9 <pid>`（强制杀死）、`kill -15 <pid>`（优雅终止）。`ps aux --sort=-%cpu`（按 CPU 降序）、`ps aux --sort=-%mem`（按内存降序）。`strace -p <pid>`（跟踪系统调用，定位阻塞原因）、`lsof -p <pid>`（查看进程打开的文件描述符）。`pgrep -f "java"` 按名称查找进程。

---

### Card 3: 网络排查命令
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: 网络故障排查的常用命令和步骤是什么？**

**A:** 步骤：① `ping <host>` 检查网络连通性；② `telnet <host> <port>` 或 `nc -zv <host> <port>` 检查端口是否开放；③ `curl -v http://host:port` 检查 HTTP 服务；④ `traceroute <host>` 路由追踪，定位网络瓶颈；⑤ `netstat -tlnp` 或 `ss -tlnp` 查看监听端口和进程；⑥ `tcpdump -i eth0 port 80` 抓包分析；⑦ `nslookup <domain>` 或 `dig <domain>` DNS 解析排查；⑧ `iptables -L -n` 检查防火墙规则。

---

### Card 4: Shell 脚本基础
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: Shell 脚本中 `$?`、`$0`、`$@`、`$#` 分别代表什么？**

**A:** `$?` 上一条命令退出码（0 成功，非 0 失败）。`$0` 脚本名称。`$@` 所有参数列表（以空格分隔）。`$#` 参数个数。`$1`...`$9` 第 1-9 个参数。`set -e` 脚本遇到错误时退出（防止错误继续执行）。`set -x` 调试模式（打印每条命令）。`${var:-default}` 变量默认值。`&&` 和 `||` 短路运算。`trap 'cleanup' EXIT` 脚本退出时执行清理函数。

---

### Card 5: awk/sed 命令
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: awk 和 sed 的典型用法是什么？**

**A:** awk：文本格式化处理，列操作。`awk '{print $1, $3}' file` 打印第 1 和第 3 列。`awk -F: '{print $1}' /etc/passwd` 指定分隔符提取用户名。`awk '$3 > 100 {print $1}'` 条件过滤。`awk '{sum+=$3} END {print sum}'` 求和。sed：流式编辑，行操作。`sed -i 's/old/new/g' file` 全局替换。`sed -n '10,20p' file` 打印 10-20 行。`sed '/^#/d' file` 删除注释行。`sed '5a\new line' file` 在第 5 行后插入。

---

### Card 6: 系统启动流程
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: Linux 系统从开机到登录的完整启动流程是什么？**

**A:** ① BIOS/UEFI 自检，加载启动设备；② Bootloader（GRUB2）加载内核到内存；③ 内核解压，初始化硬件驱动，挂载根文件系统（`initramfs`）；④ 执行 `/sbin/init`（PID=1，systemd 进程）；⑤ systemd 并行启动服务（读取 `.service` 单元文件）；⑥ 执行 `getty` 启动终端，显示登录界面；⑦ 用户登录后启动 Shell 或桌面环境。关键：`systemd-analyze blame` 查看各服务启动耗时，`systemctl list-units --type=service` 查看服务状态。

---

### Card 7: 虚拟内存管理
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: Linux 虚拟内存是如何工作的？什么是 Swap 交换分区？**

**A:** 虚拟内存通过 MMU（内存管理单元）将进程虚拟地址映射到物理内存。每个进程有独立的 4GB 虚拟地址空间（32 位），实际物理内存按页（4KB）分配。Swap 是磁盘上的一块区域，当物理内存不足时，将闲置的内存页换出到 Swap，释放物理内存给活跃进程。`swappiness`（0-100，默认 60）控制换出倾向：值越小越倾向不换出，值越大越积极换出。`free -h` 查看内存使用，`vmstat 1` 监控 swap 换入换出。频繁 swap 表示内存不足，增加物理内存比依赖 swap 更有效。

---

### Card 8: OOM Killer
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: Linux 内存不足时 OOM Killer 如何选择要杀死的进程？**

**A:** OOM Killer 根据 `oom_score` 评分（0-1000）选择分数最高的进程杀死。`oom_score` 基于：进程内存占用（越大分越高）、CPU 占用、进程运行时间（时间越短分越高）、`oom_score_adj` 调整值（-1000 禁 OOM，+1000 优先杀死）。`/proc/<pid>/oom_score` 查看具体分数。关键进程（如 `sshd`）通过 `echo -1000 > /proc/<pid>/oom_score_adj` 保护。`dmesg | grep -i "killed process"` 查看 OOM 日志。

---

### Card 9: CPU 排查
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 服务器 CPU 负载过高时如何排查？**

**A:** ① `top` 或 `htop` 查看 CPU 占用高的进程（按 P 排序）；② `ps aux --sort=-%cpu | head -5` 定位最耗 CPU 的前 5 个进程；③ `top -H -p <pid>` 查看进程内哪个线程占用 CPU 高；④ `printf "%x\n" <tid>` 将线程 ID 转十六进制；⑤ `jstack <pid> | grep <hex_tid>`（Java 应用）定位代码行；⑥ `perf top -p <pid>` 查看热点函数；⑦ `vmstat 1` 查看 `us/sy/id/wa` 指标（us 用户态，sy 内核态，wa IO 等待）。CPU 负载高不一定是坏事，关键在于是否影响业务延迟。

---

### Card 10: 内存排查
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 服务器内存不足时如何排查？**

**A:** ① `free -h` 查看总内存/已用/可用/Swap；② `top` 按 M 排序，查看各进程内存占用；③ `ps aux --sort=-%mem | head -10` 定位前 10 个内存大户；④ `smem` 查看 PSS（实际物理内存占用，去除共享库重复计算）；⑤ `cat /proc/meminfo` 查看详细内存分配（`MemAvailable` 是真实可用内存）；⑥ `pmap -x <pid>` 查看进程内存映射详情；⑦ `dmesg | tail -20` 查看 OOM 日志。Java 应用：`jmap -heap <pid>` 查看堆使用，`jstat -gcutil <pid> 1s` 查看 GC 情况。

---

### Card 11: 磁盘排查
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 磁盘 IO 过高或磁盘空间不足时如何排查？**

**A:** 磁盘空间：`df -h` 查看分区使用率，`du -sh /* | sort -rh | head -10` 定位大目录，`find / -type f -size +100M -exec ls -lh {} \;` 查找大文件。`lsof | grep deleted` 查看已删除但仍被进程占用的文件（释放文件句柄后空间才释放）。磁盘 IO：`iostat -x 1` 查看 `%util`（设备利用率，>80% 应关注）、`await`（IO 等待时间，>10ms 慢）、`r/s`、`w/s`（读写次数）。`iotop` 按进程查看 IO 使用。`dstat` 综合查看系统资源。

---

### Card 12: iptables 防火墙
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: iptables 的规则链和执行顺序是什么？**

**A:** 表（Tables）：filter（过滤，默认）、nat（NAT 转换）、mangle（包修改）。链（Chains）：INPUT（入站）、OUTPUT（出站）、FORWARD（转发）。规则执行顺序：从上到下匹配，匹配后停止。`iptables -A INPUT -p tcp --dport 22 -s 192.168.1.0/24 -j ACCEPT`（允许内网 SSH）。`iptables -A INPUT -p tcp --dport 80 -j ACCEPT`（允许 HTTP）。`iptables -P INPUT DROP`（默认拒绝入站）。`iptables -L -n -v` 查看规则及匹配计数。`iptables-save` 持久化规则。nftables 是 iptables 的下一代替代品。

---

### Card 13: SSH 安全配置
**维度**: 🎯场景 | **难度**: ⭐⭐

> **Q: SSH 服务器安全加固有哪些措施？**

**A:** ① 禁用 root 登录：`PermitRootLogin no`；② 密钥认证替代密码：`PasswordAuthentication no`，`PubkeyAuthentication yes`；③ 修改默认端口：`Port 2222`（减少扫描攻击）；④ 限制登录用户：`AllowUsers alice bob`；⑤ 白名单 IP：`iptables -A INPUT -p tcp --dport 22 -s trusted_ip -j ACCEPT`；⑥ 登录失败限制：`fail2ban` 自动封禁密码暴力破解；⑦ 使用 SSH 协议版本 2（更安全）；⑧ 禁用空密码：`PermitEmptyPasswords no`。

---

### Card 14: crontab 定时任务
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: crontab 的 5 个时间字段如何配置？常见示例有哪些？**

**A:** `分 时 日 月 周`（cron 表达式）。`* * * * *` 每分钟执行。`0 2 * * *` 每天凌晨 2 点。`*/5 * * * *` 每 5 分钟。`0 9-18 * * 1-5` 工作日 9 点到 18 点每小时。`0 0 1 * *` 每月 1 号零点。`crontab -e` 编辑，`crontab -l` 查看。注意事项：脚本需写绝对路径（`/usr/bin/python /home/script.py`），输出重定向（`>> /var/log/cron.log 2>&1`），环境变量有限（设置 `PATH` 和 `SHELL`）。`systemd-cron` 或 `systemd.timer` 是 systemd 替代方案。

---

### Card 15: systemd 服务管理
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: systemd 如何管理服务？如何编写一个 .service 单元文件？**

**A:** `systemctl start/stop/status/restart/enable/disable <service>`。`systemctl list-units --type=service` 查看所有服务。`.service` 文件示例：`[Unit]; Description=My App; After=network.target; [Service]; Type=simple; ExecStart=/usr/bin/java -jar /opt/app.jar; User=appuser; Restart=always; RestartSec=5; [Install]; WantedBy=multi-user.target`。`journalctl -u myapp.service -f` 查看实时日志。`systemctl daemon-reload` 重载配置。`systemd-analyze` 分析启动耗时。