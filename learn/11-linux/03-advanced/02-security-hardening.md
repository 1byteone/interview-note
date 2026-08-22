# 安全加固

> 服务器安全是 Java 后端开发者的必修课。本章从 SSH 配置、防火墙、SELinux、入侵防御到安全基线，覆盖生产环境安全加固的核心要点。

---

## 1. SSH 安全配置

### 禁止 root 直接登录

```bash
# 1. 创建普通用户
useradd deploy
passwd deploy
usermod -aG sudo deploy

# 2. 配置 SSH 密钥登录
ssh-keygen -t ed25519 -C "deploy@server"
ssh-copy-id deploy@192.168.1.100

# 3. 修改 SSH 配置
sudo vim /etc/ssh/sshd_config

# 关键配置项
Port 2222                              # 修改默认端口，避开扫描
PermitRootLogin no                     # 禁止 root 直接登录
PasswordAuthentication no              # 禁止密码登录
PubkeyAuthentication yes               # 启用密钥登录
AllowUsers deploy                      # 仅允许特定用户
MaxAuthTries 3                         # 最大认证尝试次数
ClientAliveInterval 300                # 客户端无操作 300 秒后断开
ClientAliveCountMax 0                  # 立即断开

# 4. 重启 SSH 服务
sudo systemctl restart sshd
```

### SSH 安全最佳实践

```bash
# 使用 ed25519 密钥（比 RSA 更安全、更快）
ssh-keygen -t ed25519 -a 100

# 配置 fail2ban 保护 SSH
# 见第 4 节

# 使用 SSH 代理转发（避免私钥留在服务器）
ssh -A deploy@192.168.1.100

# 配置 ~/.ssh/config
# Host *.example.com
#     User deploy
#     Port 2222
#     IdentityFile ~/.ssh/id_ed25519
#     ServerAliveInterval 60
```

---

## 2. 防火墙

### iptables — 传统防火墙

```bash
# 查看当前规则
sudo iptables -L -n -v

# 基本规则示例
# 允许已建立的连接
sudo iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# 允许 SSH
sudo iptables -A INPUT -p tcp --dport 2222 -j ACCEPT

# 允许 HTTP/HTTPS
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT

# 允许应用端口
sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT

# 允许内网通信
sudo iptables -A INPUT -s 10.0.0.0/8 -j ACCEPT
sudo iptables -A INPUT -s 172.16.0.0/12 -j ACCEPT
sudo iptables -A INPUT -s 192.168.0.0/16 -j ACCEPT

# 禁止其他所有入站
sudo iptables -P INPUT DROP

# 保存规则
sudo iptables-save > /etc/iptables/rules.v4
```

### nftables — 新一代防火墙

```bash
# nftables 是 iptables 的替代方案（Ubuntu 22.04 默认）
# 配置文件: /etc/nftables.conf

# 查看规则
sudo nft list ruleset

# 基础规则示例
sudo nft add table inet filter
sudo nft add chain inet filter input { type filter hook input priority 0 \; }
sudo nft add rule inet filter input ct state established,related accept
sudo nft add rule inet filter input tcp dport 2222 accept
sudo nft add rule inet filter input tcp dport {80,443} accept
sudo nft add rule inet filter input iif lo accept
sudo nft add rule inet filter input drop
```

### ufw — 简化防火墙（Ubuntu 推荐）

```bash
# 启用
sudo ufw enable

# 配置规则
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 2222/tcp comment 'SSH'
sudo ufw allow 80/tcp comment 'HTTP'
sudo ufw allow 443/tcp comment 'HTTPS'
sudo ufw allow from 10.0.0.0/8 to any port 8080 comment '内网应用'

# 查看状态
sudo ufw status verbose

# 删除规则
sudo ufw delete allow 80/tcp
```

---

## 3. SELinux / AppArmor

### SELinux（CentOS/RHEL）

```bash
# 查看状态
getenforce
# Enforcing  — 强制模式
# Permissive — 只记录不阻止
# Disabled   — 关闭

# 临时切换
sudo setenforce 0      # 切换到 Permissive
sudo setenforce 1      # 切换到 Enforcing

# 永久修改（修改后重启）
sudo vim /etc/selinux/config
# SELINUX=enforcing

# 查看 SELinux 上下文
ls -Z
ps auxZ

# 修改文件上下文
sudo chcon -t httpd_sys_content_t /var/www/html/index.html
sudo restorecon -v /var/www/html/index.html

# 管理布尔值
sudo getsebool -a | grep httpd
sudo setsebool httpd_can_network_connect on
```

### AppArmor（Ubuntu）

```bash
# 查看状态
sudo aa-status

# 查看应用配置文件
cat /etc/apparmor.d/usr.sbin.nginx

# 临时禁用/启用
sudo aa-disable /usr/sbin/nginx
sudo aa-enforce /usr/sbin/nginx

# 查看日志
sudo journalctl | grep apparmor
```

---

## 4. Fail2ban 防暴力破解

### 安装与配置

```bash
# 安装
sudo apt install fail2ban -y

# 创建本地配置
sudo cp /etc/fail2ban/jail.conf /etc/fail2ban/jail.local

# 配置 SSH 保护
sudo vim /etc/fail2ban/jail.local

[sshd]
enabled = true
port = 2222                       # 你的 SSH 端口
filter = sshd
logpath = /var/log/auth.log
maxretry = 3                      # 3 次失败
bantime = 3600                    # 封禁 1 小时
findtime = 600                    # 10 分钟内

# 配置 Nginx 保护
[nginx-http-auth]
enabled = true
port = http,https
filter = nginx-http-auth
logpath = /var/log/nginx/error.log
maxretry = 5
bantime = 600

# 启动
sudo systemctl start fail2ban
sudo systemctl enable fail2ban

# 查看状态
sudo fail2ban-client status
sudo fail2ban-client status sshd

# 解封 IP
sudo fail2ban-client set sshd unbanip 192.168.1.2
```

---

## 5. 安全基线检查

### 系统安全基线

```bash
#!/bin/bash
# 安全基线检查脚本

echo "===== 安全基线检查 ====="

# 1. 检查 SSH 配置
echo "--- SSH 配置 ---"
grep -E "^(PermitRootLogin|PasswordAuthentication|Port)" /etc/ssh/sshd_config

# 2. 检查未授权的 SUID 文件
echo "--- SUID 文件 ---"
find / -perm -4000 -type f 2>/dev/null

# 3. 检查开放端口
echo "--- 开放端口 ---"
ss -tlnp

# 4. 检查最近登录记录
echo "--- 最近登录 ---"
last -n 10

# 5. 检查失败的登录尝试
echo "--- 失败登录 ---"
lastb -n 10

# 6. 检查系统用户
echo "--- 可登录用户 ---"
awk -F: '($7 ~ /bash|sh/) {print $1}' /etc/passwd

# 7. 检查防火墙状态
echo "--- 防火墙状态 ---"
sudo ufw status verbose 2>/dev/null || sudo iptables -L -n -v
```

### 日常安全操作

```bash
# 系统更新
sudo apt update && sudo apt upgrade -y

# 自动安全更新
sudo apt install unattended-upgrades -y
sudo dpkg-reconfigure -plow unattended-upgrades

# 审计日志
sudo auditctl -w /etc/passwd -p wa -k passwd_changes
sudo auditctl -w /etc/shadow -p wa -k shadow_changes
sudo ausearch -k passwd_changes

# 文件完整性检查
sudo apt install aide -y
sudo aideinit
sudo aide --check
```

---

## 安全加固清单

```
□ SSH 端口改为非默认
□ 禁止 root 直接登录
□ 仅允许密钥登录
□ 限制 SSH 登录用户
□ 启用防火墙（ufw/iptables）
□ 仅开放必要端口
□ 限制内网访问
□ 安装 fail2ban
□ 启用自动安全更新
□ 定期检查系统日志
□ 配置审计规则
□ 定期备份重要数据
```

---

## 总结

本章你学会了：

- SSH 安全配置（改端口、禁 root、密钥登录）
- 三种防火墙工具（iptables/nftables/ufw）
- SELinux 和 AppArmor 的基本使用
- Fail2ban 防暴力破解配置
- 安全基线检查脚本

下一步：进入 [项目实战](../04-projects/mall-integration.md) 将 Linux 技能应用到 AI 商城部署。