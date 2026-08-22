# Linux 快速入门

> 面向 Java 后端开发者的 Linux 入门指南，目标是让你能上手操作服务器、部署应用。

---

## 1. Linux 发行版选择

生产环境中主流选择：

| 发行版 | 包管理器 | 适用场景 | 特点 |
|--------|----------|----------|------|
| **CentOS 7/8** | yum | 传统企业级 | 稳定，但 CentOS 8 已于 2021 年停止维护 |
| **Ubuntu 20.04/22.04 LTS** | apt | 通用/云原生 | 社区活跃，文档丰富，云服务器首选 |
| **Debian 11/12** | apt | 追求稳定 | 比 Ubuntu 更保守，安全性好 |
| **Rocky Linux / AlmaLinux** | dnf/yum | CentOS 替代 | 与 RHEL 二进制兼容 |
| **Alibaba Cloud Linux 3** | yum/dnf | 阿里云 ECS | 阿里云原生优化 |

**推荐：** 个人学习用 **Ubuntu 22.04 LTS**，云服务器用 **Ubuntu 22.04** 或 **Alibaba Cloud Linux 3**。

---

## 2. 文件系统结构

```
/                 根目录
├── bin/          -> /usr/bin       # 用户二进制命令
├── sbin/         -> /usr/sbin      # 系统管理命令
├── etc/                            # 配置文件
├── var/                            # 可变数据（日志、缓存）
│   └── log/                        # 日志文件
├── usr/                            # 用户程序与数据
│   ├── local/                      # 手工编译安装的软件
│   └── share/                      # 共享数据
├── opt/                            # 可选软件包
├── home/                           # 用户家目录
├── root/                           # root 用户家目录
├── tmp/                            # 临时文件
├── dev/                            # 设备文件
├── proc/                           # 进程与内核信息（虚拟文件系统）
├── sys/                            # 内核与设备信息
└── mnt/ /media/                    # 挂载点
```

**Java 开发者关注的重点目录：**

| 目录 | 用途 |
|------|------|
| `/etc/nginx/` | Nginx 配置 |
| `/etc/ssh/` | SSH 配置 |
| `/var/log/` | 应用日志 |
| `/usr/local/` | JDK、Tomcat 等安装目录 |
| `/opt/` | 部分商业软件安装目录 |
| `/proc/` | 查看进程信息、内核参数 |

---

## 3. 基本命令

### 文件与目录操作

```bash
# 查看目录
ls -la                   # 列出所有文件（含隐藏文件）
ls -lh                   # 以人类可读大小显示
pwd                      # 显示当前目录

# 切换目录
cd /etc/nginx            # 进入目录
cd ~                     # 进入家目录
cd -                     # 回到上一个目录

# 创建与删除
mkdir -p app/logs        # 递归创建目录
touch app.log            # 创建空文件
rm -rf temp/             # 递归强制删除（慎用！）
cp -r src/ dest/         # 递归复制
mv old new               # 移动/重命名

# 查看文件
cat app.log              # 显示全部内容
less app.log             # 分页查看（按 q 退出）
head -n 20 app.log       # 查看前20行
tail -n 100 -f app.log   # 查看最后100行并实时追踪
```

### 搜索与查找

```bash
# 查找文件
find /var/log -name "*.log" -mtime -7    # 7天内修改的 .log 文件
find . -type f -size +100M               # 大于100MB的文件

# 搜索文件内容
grep "ERROR" app.log                     # 搜索关键字
grep -r "NullPointerException" /app/     # 递归搜索目录
grep -A 5 -B 5 "OOM" app.log             # 显示匹配行前后5行
```

---

## 4. 文件权限

### 权限表示

```
-rwxr-xr-x  1 root root  12345 Aug 20 10:00 app.jar
  ^^^^^^^
  ||||||||
  ||||||└── 其他用户权限 (r-x)
  |||||└─── 组用户权限 (r-x)
  ||||└──── 所有者权限 (rwx)
  |||└───── 文件类型（- 普通文件，d 目录）
```

### 权限修改

```bash
# 数字法
chmod 755 app.jar        # rwxr-xr-x
chmod 644 config.yml     # rw-r--r--

# 字母法
chmod u+x app.jar        # 给所有者添加执行权限
chmod g-w app.jar        # 移除组的写权限
chmod o+r config.yml     # 给其他人添加读权限

# 更改所有者
chown admin:admin app.jar    # 修改所有者和组
chown -R admin:admin /app/   # 递归修改目录
```

**常见权限场景：**

| 场景 | 权限 |
|------|------|
| Shell 脚本执行 | 755 |
| 配置文件 | 644 |
| SSH 私钥 | 600 |
| 日志文件 | 640 |
| 可执行程序 | 755 |

---

## 5. vim 基础操作

### 三种模式

```
┌──────────┐    i / a    ┌──────────┐    Esc    ┌──────────┐
│  Normal   │ ────────→  │  Insert   │ ──────→  │  Normal   │
│  (默认)   │ ←────────  │  (编辑)   │          │  (默认)   │
└──────────┘    Esc     └──────────┘          └──────────┘
      │                                               │
      :                                               :
      ↓                                               ↓
  ┌──────────┐                                        │
  │ Command  │ ←──────────────────────────────────────┘
  │  (命令)  │
  └──────────┘
```

### 常用快捷键

```bash
# Normal 模式
gg           # 跳到文件开头
G            # 跳到文件末尾
/error       # 搜索 "error"
:nohl        # 取消高亮
dd           # 删除当前行
yy           # 复制当前行
p            # 粘贴
u            # 撤销
Ctrl + r     # 重做

# Command 模式（按 : 进入）
:w           # 保存
:q           # 退出
:wq          # 保存并退出
:q!          # 强制退出不保存
:set number  # 显示行号
```

---

## 6. 最小案例：搭建 LNMP 环境

在 Ubuntu 服务器上快速搭建 Nginx + MySQL + PHP 环境：

```bash
# 更新包列表
sudo apt update && sudo apt upgrade -y

# 安装 Nginx
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx

# 安装 MySQL 8.0
sudo apt install mysql-server -y
sudo mysql_secure_installation

# 安装 PHP 8.1 + FPM
sudo apt install php8.1-fpm php8.1-mysql -y

# 配置 Nginx 站点
sudo tee /etc/nginx/sites-available/default > /dev/null << 'EOF'
server {
    listen 80;
    root /var/www/html;
    index index.php index.html;
    
    location ~ \.php$ {
        include snippets/fastcgi-php.conf;
        fastcgi_pass unix:/run/php/php8.1-fpm.sock;
    }
}
EOF

# 重启 Nginx
sudo systemctl restart nginx

# 验证
curl -I http://localhost
```

---

## 总结

本章你学会了：

- 如何选择 Linux 发行版
- 文件系统目录结构及各目录用途
- 常用文件操作命令
- 文件权限管理
- vim 基本操作
- 搭建 LNMP 环境

下一步：学习 [包管理与服务管理](02-package-and-service.md)。