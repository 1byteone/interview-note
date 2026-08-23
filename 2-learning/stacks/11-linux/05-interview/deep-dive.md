# Linux 深挖题

> 面试中可能遇到的 Linux 底层原理问题，涉及启动流程、文件系统、内核参数等。

---

## 1. Linux 启动流程

### 完整启动过程

```
BIOS/UEFI
    │ 上电自检，加载引导设备
    ▼
Boot Loader (GRUB2)
    │ 读取 /boot/grub/grub.cfg
    │ 选择内核和 initramfs
    ▼
Kernel
    │ 解压内核，初始化硬件驱动
    │ 挂载 initramfs（临时根文件系统）
    ▼
initramfs
    │ 加载必要的驱动模块
    │ 挂载真正的根文件系统
    ▼
init (PID 1)
    │ systemd → 读取 /etc/systemd/system/default.target
    │ 并行启动系统服务
    ▼
Login
    │ getty → 显示登录提示
    │ 用户登录 → shell
```

### 关键点

- **GRUB2**：现代 Linux 默认引导加载器，配置文件 `/boot/grub/grub.cfg`
- **initramfs**：临时根文件系统，包含内核启动所需的驱动和脚本
- **systemd**：PID 1，所有进程的父进程，并行化启动服务
- **runlevel**：传统 SysV init 的运行级别（0 关机, 1 单用户, 3 多用户, 5 图形界面, 6 重启）
- **systemd target**：替代 runlevel，如 `multi-user.target` 对应 runlevel 3

### 常见面试题

**Q: 如何排查系统无法启动？**

A: 按以下顺序排查：
1. GRUB 菜单选择恢复模式（recovery mode）
2. 进入单用户模式（在 GRUB 内核参数行添加 `single` 或 `init=/bin/bash`）
3. 检查 `/var/log/boot.log` 和 `dmesg`
4. 检查 `/etc/fstab` 是否有挂载错误
5. 检查关键 systemd 服务状态

---

## 2. 文件系统

### 虚拟文件系统 (VFS)

Linux 通过 VFS 抽象层统一管理各种文件系统：

```
用户进程
    │
    ▼
虚拟文件系统 (VFS)
    │
    ├── ext4         ── 传统磁盘文件系统
    ├── xfs          ── 高性能文件系统（RHEL 默认）
    ├── btrfs        ── 写时复制，支持快照
    ├── tmpfs        ── 内存文件系统（/tmp, /dev/shm）
    ├── procfs       ── 进程信息（/proc）
    ├── sysfs        ── 内核对象（/sys）
    ├── devtmpfs     ── 设备文件（/dev）
    └── overlay2     ── Docker 联合文件系统
```

### inode 和目录项

```bash
# inode 包含文件的元数据（权限、大小、时间戳等），不包含文件名
# 目录项（dentry）将文件名映射到 inode

# 查看 inode
stat /etc/passwd
ls -li /etc/passwd

# inode 耗尽（磁盘有空间但无法创建文件）
df -i
# 解决方案：删除大量小文件
```

### 硬链接 vs 软链接

| 特性 | 硬链接 | 软链接 |
|------|--------|--------|
| inode | 相同 | 不同 |
| 跨文件系统 | 不支持 | 支持 |
| 目录链接 | 不支持 | 支持 |
| 原文件删除后 | 仍可访问 | 失效 |
| 命令 | `ln src dst` | `ln -s src dst` |

---

## 3. 内核参数

### 查看与修改

```bash
# 查看所有内核参数
sysctl -a

# 查看单个参数
sysctl net.ipv4.tcp_tw_reuse
cat /proc/sys/net/ipv4/tcp_tw_reuse

# 临时修改
sysctl -w net.ipv4.tcp_tw_reuse=1

# 永久修改
echo "net.ipv4.tcp_tw_reuse = 1" >> /etc/sysctl.conf
sysctl -p
```

### Java 应用相关的内核参数

```bash
# 内核参数优化建议（/etc/sysctl.d/99-java.conf）

# 网络优化
net.ipv4.tcp_tw_reuse = 1              # 复用 TIME_WAIT 连接
net.ipv4.tcp_fin_timeout = 15          # FIN 等待时间
net.ipv4.tcp_keepalive_time = 120      # TCP 保活时间
net.ipv4.ip_local_port_range = 1024 65000  # 本地端口范围
net.core.somaxconn = 1024              # 最大连接队列
net.ipv4.tcp_max_syn_backlog = 1024    # SYN 队列长度

# 内存优化
vm.swappiness = 10                     # 减少 swap 使用
vm.overcommit_memory = 1               # 允许内存过量分配
vm.max_map_count = 262144              # 最大内存映射数（Elasticsearch 需要）

# 文件句柄
fs.file-max = 6815744                  # 系统级文件句柄限制
fs.nr_open = 1048576                   # 进程级文件句柄限制
```

### 文件句柄限制

```bash
# 查看当前限制
ulimit -n
cat /proc/self/limits

# 修改用户级限制（/etc/security/limits.conf）
# 格式: <domain> <type> <item> <value>
deploy    soft    nofile    65536
deploy    hard    nofile    65536
deploy    soft    nproc     65536
deploy    hard    nproc     65536

# 修改 systemd 服务限制
# 在服务单元文件中添加
[Service]
LimitNOFILE=65536
LimitNPROC=65536
```

---

## 4. 进程与线程

### 进程状态

```
R (Running)        — 正在运行或可运行
S (Sleeping)       — 可中断睡眠（等待事件）
D (Uninterruptible)— 不可中断睡眠（磁盘 IO）
T (Stopped)        — 停止（SIGSTOP/SIGTSTP）
Z (Zombie)         — 僵尸进程（已终止但父进程未回收）
X (Dead)           — 已死亡
```

### 僵尸进程处理

```bash
# 僵尸进程的特征：ps 显示 Z 状态，但不占用资源
# 僵尸进程无法被 kill，只能通过父进程回收

# 查找僵尸进程
ps aux | grep -w Z

# 找到父进程
ps -o ppid= -p <ZOMBIE_PID>

# 结束父进程（父进程结束，僵尸进程被 init 回收）
kill -9 <PARENT_PID>

# 如果父进程是 init，无法结束，重启系统
```

### 孤儿进程

- 父进程先于子进程退出，子进程被 init (PID 1) 收养
- 不会造成资源泄漏，init 会定期回收

---

## 5. 内存管理

### 虚拟内存与物理内存

```
进程虚拟地址空间 (32位: 4GB, 64位: 2^48)
┌──────────────────────┐ 0xFFFFFFFF
│     内核空间         │
├──────────────────────┤ 0xC0000000 (32位)
│                      │
│     用户空间         │
│    (堆、栈、数据)    │
│                      │
├──────────────────────┤ 0x00000000
│   代码段 (text)      │
└──────────────────────┘

虚拟地址 → MMU → 物理地址
```

### OOM Killer

```bash
# 当系统内存不足时，OOM Killer 选择进程杀掉

# 查看 OOM 评分
cat /proc/<PID>/oom_score
cat /proc/<PID>/oom_score_adj

# 调整 OOM 优先级（避免重要进程被杀死）
echo -1000 > /proc/<PID>/oom_score_adj  # 降低被杀的几率
echo 1000 > /proc/<PID>/oom_score_adj   # 提高被杀的几率

# 查看 OOM 日志
dmesg | grep -i "killed process"
journalctl -k | grep -i oom
```

---

## 6. I/O 模型

### 五种 I/O 模型

| 模型 | 特点 | 主要应用 |
|------|------|----------|
| 阻塞 I/O | 进程等待直到数据就绪 | 传统文件读写 |
| 非阻塞 I/O | 立即返回，轮询检查 | |
| I/O 多路复用 | select/poll/epoll 单线程管理多连接 | Nginx, Redis, Netty |
| 信号驱动 I/O | 数据就绪时发信号通知 | |
| 异步 I/O | 内核完成操作后通知 | Java AIO |

### epoll 的优势

- **select**：FD_SET 限制 1024，每次需遍历所有 FD
- **poll**：无 1024 限制，但每次仍需遍历所有 FD
- **epoll**：事件驱动，只返回就绪的 FD，O(1) 复杂度

---

## 7. 常见面试题

**Q: 进程和线程的区别？**

A: 进程是资源分配的基本单位，线程是 CPU 调度的基本单位。进程有独立的地址空间，线程共享进程的地址空间。切换线程比切换进程开销小。

**Q: Linux 中如何查看系统瓶颈？**

A: 使用 `dstat` 查看 CPU、磁盘、网络、内存的综合状况。CPU 高用 `top`，磁盘 IO 高用 `iostat`，网络高用 `ss -s`，内存高用 `free -h` 和 `vmstat`。

**Q: 软中断和硬中断的区别？**

A: 硬中断由硬件触发，中断处理程序执行时间短。软中断由内核线程处理，处理下半部的工作。`/proc/softirqs` 查看软中断统计。

**Q: 什么是 page cache？**

A: 内核将磁盘内容缓存到内存中，提高读写性能。`free` 命令中的 `buff/cache` 就是 page cache。可以通过 `echo 3 > /proc/sys/vm/drop_caches` 手动清理。