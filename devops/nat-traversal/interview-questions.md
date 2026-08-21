# 内网穿透 (NAT Traversal) 面试题大全

## 📚 知识体系

```
内网穿透原理
├── NAT 类型
│   ├── Full Cone
│   ├── Restricted Cone
│   ├── Port Restricted Cone
│   └── Symmetric
├── NAT 映射机制
├── 打洞 (Hole Punching)
├── 中继 (Relay)
└── 隧道 (Tunnel)

常见工具
├── FRP (Fast Reverse Proxy)
├── ngrok
├── nps
├── Cloudflare Tunnel
├── Tailscale
├── ZeroTier
├── 花生壳
└── cpolar

应用场景
├── 远程办公
├── 本地服务公网访问
├── Webhook 调试
├── 微信/支付宝回调对接
└── 家庭 NAS、摄像头
```

---

## 🎯 Level 1：基础题

### 1. 什么是内网穿透？为什么需要？
**答案**：
内网穿透（NAT Traversal）是让**处于内网（NAT 之后）的设备能够被公网访问**的技术。

**为什么需要**：
1. 内网设备没有公网 IP
2. NAT 会阻止外部主动建立连接
3. 将本地部署的服务（如 Spring Boot、Docker、NAS）暴露到公网
4. 对接第三方回调（微信支付回调、Webhook）

### 2. NAT 四种类型是什么？
**答案**：

| NAT 类型 | 特点 | 打洞成功率 |
|----------|------|-----------|
| **Full Cone** | 任何外部主机可访问 | 高 |
| **Restricted Cone** | 仅发过包的 IP 可访问 | 中 |
| **Port Restricted Cone** | 仅发过包的 IP+端口可访问 | 中 |
| **Symmetric** | 每次连接不同端口，最难 | 低 |

**对称型 NAT 最难穿透**，通常需要**中继（Relay）**方案。

---

## 🎯 Level 2：进阶题

### 3. 内网穿透的三种实现方式？
**答案**：

**方式一：端口映射（静态 NAT）**
```
路由器配置：
公网 IP:8080 → 内网 192.168.1.10:8080
```
- 优点：简单
- 缺点：需要路由器管理权限，公网 IP 暴露

**方式二：隧道/反向代理（FRP、ngrok）**
```text
公网服务器（有公网 IP）
    ↓ 反向代理隧道
内网设备（无公网 IP）→ FRP Client → FRP Server → 公网用户
```

**方式三：P2P 打洞（Tailscale、ZeroTier）**
```text
设备A ←→ 协调服务器（打洞） ←→ 设备B
                ↓ 打洞成功后
设备A ←—————— 直连 ——————→ 设备B
```

### 4. FRP 的工作原理是什么？
**答案**：

**FRP（Fast Reverse Proxy）组成**：
- **FRPS（服务端）**：部署在有公网 IP 的服务器
- **FRPC（客户端）**：部署在内网机器上

**工作流程**：
```text
┌─────────────┐   主动连接     ┌──────────────┐
│  公网服务器   │ ←—————————— │  内网客户端    │
│   FRPS      │    建立隧道    │    FRPC      │
└──────┬──────┘               └──────┬───────┘
       ↑                             ↑
       │ 用户访问                     │ 内网服务
       │ http://公网IP:8080          │ localhost:8080
┌──────┴──────┐               ┌──────┴───────┐
│   公网用户    │               │  Spring Boot │
└─────────────┘               │  (内网:8080)  │
                              └──────────────┘
```

**配置示例**：
```ini
# frps.ini（服务端）
[common]
bind_port = 7000
vhost_http_port = 8080

# frpc.ini（客户端）
[common]
server_addr = 公网服务器IP
server_port = 7000

[web]
type = http
local_port = 8080
custom_domains = example.com
```

---

## 🎯 Level 3：高级题

### 5. P2P 打洞原理是什么？
**答案**：

**UDP 打洞（Hole Punching）**：
```text
① 设备A → 协调服务器：告知 A 的公网地址
② 设备B → 协调服务器：告知 B 的公网地址
③ 协调服务器交换 A、B 的公网地址
④ A 向 B 的公网地址发包（此时可能被 B 的 NAT 丢弃）
⑤ B 向 A 的公网地址发包（与 A 的已建立映射匹配）
⑥ 双方 NAT 都建立映射 → 打洞成功 → 直连
```

**关键点**：
- 双方必须同时打洞（Simultaneous Open）
- UDP 打洞成功率高于 TCP
- 对称型 NAT 打洞失败 → 回退到中继方案

---

## 🎯 Level 4：专家题

### 6. 如何选择内网穿透方案？
**答案**：

**选择维度**：

| 方案 | 成本 | 速度 | 安全 | 适用场景 |
|------|------|------|------|----------|
| **FRP** | 需公网服务器 | 快（直连） | 高（加密） | 自建服务、生产对接 |
| **ngrok** | 免费/付费 | 中 | 中 | 临时调试 |
| **Cloudflare Tunnel** | 免费 | 中 | 高 | 零信任访问 |
| **Tailscale** | 免费(基础) | 快（P2P） | 高（WireGuard） | 远程办公、设备组网 |
| **ZeroTier** | 免费(基础) | 快（P2P） | 高 | 组建虚拟局域网 |
| **花生壳** | 免费/付费 | 中 | 中 | 个人使用 |

### 7. Spring Boot 项目如何用 FRP 暴露公网？
**答案**：

**部署架构**：
```
微信回调 → 公网服务器(FRPS:443) → 隧道 → 内网 Spring Boot (:8080)
```

**步骤**：
1. 公网服务器部署 FRPS（Docker）：
```yaml
# docker-compose.yml (frps)
services:
  frps:
    image: snowdreamtech/frps
    container_name: frps
    restart: always
    volumes:
      - ./frps.ini:/etc/frp/frps.ini
    ports:
      - "7000:7000"      # 控制端口
      - "7001:7001"      # 业务端口
      - "8080:8080"      # HTTP 端口
```

2. 内网机器部署 FRPC：
```ini
# frpc.ini
[common]
server_addr = 8.8.8.8
server_port = 7000

[spring-boot-app]
type = tcp
local_ip = 127.0.0.1
local_port = 8080
remote_port = 7001
```

3. 访问：`http://8.8.8.8:7001` 即可访问内网 Spring Boot 服务

---

## 📖 学习资源

### 推荐项目
- [FRP 官方文档](https://github.com/fatedier/frp) - 高性能内网穿透
- [ngrok 官方文档](https://ngrok.com/docs/)
- [Tailscale](https://tailscale.com/)
- [ZeroTier](https://www.zerotier.com/)
- [lanproxy](https://github.com/ffay/lanproxy) - 中文内网穿透项目

### 最佳实践
1. 生产对接（微信回调）优先使用 FRP + 公网服务器
2. 长期内网服务使用 Tailscale/ZeroTier 组网
3. 临时调试用 ngrok/cpolar
4. 务必开启 TLS 加密（FRP 支持 TLS）
5. 限制访问来源与端口，配合防火墙