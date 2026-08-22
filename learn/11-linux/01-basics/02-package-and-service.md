# 包管理与服务管理

> 掌握 Linux 上的软件安装、服务管理、进程监控、日志查看和定时任务，是 Java 后端开发者的必备技能。

---

## 1. 包管理

### apt (Debian/Ubuntu)

```bash
# 更新包索引
sudo apt update

# 升级所有包
sudo apt upgrade -y

# 安装软件
sudo apt install openjdk-17-jdk -y

# 删除软件
sudo apt remove nginx
sudo apt purge nginx           # 删除配置
sudo apt autoremove            # 清理依赖

# 搜索软件
apt search openjdk
apt show openjdk-17-jdk        # 查看详细信息

# 清理缓存
sudo apt clean
sudo apt autoclean
```

### yum/dnf (CentOS/RHEL/Rocky)

```bash
# yum（CentOS 7）
sudo yum install -y java-17-openjdk
sudo yum remove nginx
sudo yum list installed | grep java

# dnf（CentOS 8+/Rocky/Alma）
sudo dnf install -y java-17-openjdk
sudo dnf remove nginx
sudo dnf list installed
sudo dnf groupinstall "Development Tools"
```

### 配置国内镜像源（Ubuntu 22.04）

```bash
# 备份原始源
sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak

# 替换为阿里云源
sudo sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list
sudo sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list

sudo apt update
```

---

## 2. systemd 服务管理

### 服务单元文件

一个典型的 Java 应用服务单元文件 `/etc/systemd/system/app.service`：

```ini
[Unit]
Description=My Java Application
After=network.target mysql.service

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/app
ExecStart=/usr/bin/java -jar -Xms512m -Xmx2g app.jar
ExecStop=/bin/kill -SIGTERM $MAINPID
Restart=on-failure
RestartSec=10
Environment=SPRING_PROFILES_ACTIVE=prod

[Install]
WantedBy=multi-user.target
```

### 常用命令

```bash
# 启动/停止/重启
sudo systemctl start app
sudo systemctl stop app
sudo systemctl restart app
sudo systemctl reload app        # 重新加载配置（不中断服务）

# 查看状态
sudo systemctl status app
sudo systemctl is-active app

# 开机自启
sudo systemctl enable app
sudo systemctl disable app
sudo systemctl is-enabled app

# 重新加载 systemd 配置（修改单元文件后必须执行）
sudo systemctl daemon-reload

# 查看服务日志
sudo journalctl -u app -f        # 实时追踪
sudo journalctl -u app --since "10 min ago"
sudo journalctl -u app -n 100    # 最近100行
```

---

## 3. 进程管理

### 查看进程

```bash
# ps — 静态快照
ps aux                           # 所有进程
ps aux | grep java               # 筛选 Java 进程
ps -ef --forest                  # 树形显示进程关系

# top — 动态监控
top                              # 按 CPU 排序
top -o %MEM                      # 按内存排序
top -p 12345                     # 监控特定 PID
htop                             # 更友好的 top（需安装）

# 查看进程详细信息
ls -l /proc/12345/               # 进程信息目录
cat /proc/12345/environ          # 环境变量
cat /proc/12345/cmdline          # 启动命令
```

### 进程管理

```bash
# 终止进程
kill 12345                       # 优雅终止（SIGTERM）
kill -9 12345                    # 强制终止（SIGKILL，慎用）
killall java                     # 终止所有 Java 进程
pkill -f app.jar                 # 按名称匹配

# 后台运行
nohup java -jar app.jar > app.log 2>&1 &

# 查看后台作业
jobs
fg %1                           # 调到前台
bg %1                           # 继续后台运行

# 更可靠的守护进程方式（推荐）
# 使用 systemd 或 supervisor
```

---

## 4. 日志查看

### 系统日志

```bash
# 系统日志
journalctl -xe                   # 查看最近的系统日志和错误
journalctl -k                    # 内核日志
journalctl -p err                # 只看错误级别

# 传统日志文件
/var/log/syslog                  # Ubuntu 系统日志
/var/log/messages                # CentOS 系统日志
/var/log/auth.log                # 认证日志
/var/log/kern.log                # 内核日志
```

### 应用日志查看技巧

```bash
# 实时追踪
tail -f /var/log/app/application.log

# 查看最后 N 行
tail -n 200 app.log

# 按时间范围
sed -n '/2024-08-20 10:00/,/2024-08-20 11:00/p' app.log

# 关键词搜索
grep -n "ERROR" app.log
grep -c "NullPointerException" app.log    # 统计次数

# 日志分析
grep "ERROR" app.log | cut -d' ' -f1-3 | sort | uniq -c | sort -nr
```

---

## 5. 定时任务（crontab）

### 格式

```
分钟 小时 日 月 星期  命令
 0    8    *  *  *    /usr/bin/backup.sh
```

| 字段 | 范围 | 说明 |
|------|------|------|
| 分钟 | 0-59 | |
| 小时 | 0-23 | |
| 日 | 1-31 | |
| 月 | 1-12 | |
| 星期 | 0-7 | 0 和 7 都表示周日 |

### 常用示例

```bash
# 编辑定时任务
crontab -e

# 每分钟执行
* * * * * /script/check.sh

# 每天凌晨2点执行
0 2 * * * /script/daily-backup.sh

# 每小时执行
0 * * * * /script/hourly-task.sh

# 每周一凌晨3点
0 3 * * 1 /script/weekly-report.sh

# 每月1号凌晨4点
0 4 1 * * /script/monthly-cleanup.sh

# 每5分钟
*/5 * * * * /script/health-check.sh

# 查看定时任务
crontab -l

# 日志重定向（推荐写法）
0 2 * * * /script/backup.sh >> /var/log/backup.log 2>&1
```

### 定时备份 Java 应用日志

```bash
# 每天凌晨归档前一天的日志
0 0 * * * cd /var/log/app && tar -czf app-$(date -d yesterday +\%Y\%m\%d).tar.gz app.log && > app.log && find . -name "*.tar.gz" -mtime +30 -delete
```

---

## 总结

本章你学会了：

- 使用 apt/yum 安装、更新、删除软件包
- 配置国内镜像源加速下载
- 编写 systemd 服务单元文件管理 Java 应用
- 使用 ps/top 查看进程，用 kill/nohup 管理进程
- 使用 tail/grep/journalctl 查看日志
- 使用 crontab 设置定时任务

下一步：进入 [Shell 脚本编程](../02-core/01-shell-scripting.md) 学习自动化。