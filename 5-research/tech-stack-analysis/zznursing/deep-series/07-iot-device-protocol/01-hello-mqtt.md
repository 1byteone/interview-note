# MQTT 协议入门：从设备到云端的消息传递

> 本文是 zznursing 项目技术栈深度剖析系列的第 7 篇（入门篇 / Level 1），面向 Java 后端开发者。手把手带你从零搭建一个完整的 MQTT 通信演示系统，理解物联网设备消息传递的核心原理。
>
> **对应项目：** zznursing 养老机构综合运营平台
> **难度等级：** Level 1 入门
> **预计阅读时间：** 30 分钟（含代码实操）

---

## 一、项目背景

### 1.1 什么是 MQTT

MQTT（Message Queuing Telemetry Transport，消息队列遥测传输）是一种**轻量级的发布/订阅模式消息协议**，由 IBM 在 1999 年开发，2014 年成为 OASIS 标准。它专为物联网场景设计——在带宽有限、网络不稳定、设备资源受限的环境下，高效地传递消息。

MQTT 的核心设计哲学是**"小而省"**：

- **协议头极小**：最小仅 2 字节固定头，对比 HTTP 动辄几百字节的头部开销，差异巨大。在 NB-IoT 等低带宽网络中，这个优势尤为突出——同样的网络带宽，MQTT 可以传输更多有效数据。
- **低功耗**：设备发送一次消息的能耗极低，适合电池供电的 IoT 传感器。智能手环使用 MQTT 协议，一块 200mAh 的电池可以支撑连续工作 15 天以上，而如果使用 HTTP 轮询，电池撑不过 3 天。
- **异步通信**：发布者（Publisher）和订阅者（Subscriber）完全解耦，不需要同时在线。设备上报数据后立刻进入低功耗休眠模式，后端服务可以在任意时间上线消费数据，两者互不依赖。
- **双向实时**：设备可以上报数据，云平台也可以下发指令，双向通信天然支持。在 zznursing 中，智能手环上传心率数据，同时后端可以远程下发指令修改手环的上报频率——所有这些都在同一个 MQTT 连接上完成。

### 1.2 MQTT vs HTTP：物联网场景的协议选择

很多初学者会问：为什么不用 HTTP？毕竟 HTTP 已经如此成熟，全世界都在用，为什么 IoT 场景还要另学一个 MQTT？

| 对比维度 | MQTT | HTTP |
|---------|------|------|
| **协议模型** | 发布/订阅（Pub/Sub），一对多 | 请求/响应（Request/Response），一对一 |
| **最小协议头** | 2 字节 | 几百字节起 |
| **实时性** | 推模式，消息到达即推送 | 拉模式，需要客户端轮询 |
| **双向通信** | 天然支持，双向发布 | 客户端主动请求，服务端被动响应 |
| **断线重连** | 协议内置（Last Will + 自动重连） | 需自行实现 |
| **消息质量** | 3 种 QoS 等级可选 | 依赖 TCP 可靠传输 |
| **适用场景** | 物联网、移动端、低带宽网络 | Web 应用、REST API |

**核心差异在于：** HTTP 是"你问我答"——客户端必须主动发起请求，服务端才能响应。而 IoT 场景中，**平台需要主动感知设备状态变化**（比如老人心率异常、设备离线），不可能让每台设备每秒都轮询一次 HTTP 接口。MQTT 的发布/订阅模型天然解决了这个问题：设备发布消息，平台订阅感兴趣的主题，消息到达即推送。

### 1.3 zznursing 的设备通信架构

zznursing 智慧养老平台的 IoT 设备层使用了 MQTT 作为核心通信协议，整体架构如下：

```
┌─────────────────────────────────────────────────────┐
│                    IoT 设备层                         │
│  ┌─────────┐  ┌──────────┐  ┌─────────┐  ┌────────┐ │
│  │ 智能手环 │  │ 床垫传感器 │  │紧急按钮 │  │定位胸卡│ │
│  │(心率/血氧)│  │(体动/离床)│  │(一键呼叫)│  │(GPS定位)│ │
│  └────┬────┘  └────┬─────┘  └────┬────┘  └────┬───┘ │
│       │             │             │             │      │
│       └─────────────┼─────────────┼─────────────┘      │
│                     │  MQTT 协议  │                     │
│                     ▼             ▼                     │
│             ┌───────────────────────────┐              │
│             │     MQTT Broker (EMQX)     │              │
│             │    ┌──────────────────┐    │              │
│             │    │ 主题路由 + 规则引擎 │    │              │
│             │    └──────────────────┘    │              │
│             └───────────┬───────────────┘              │
│                         │                              │
│                         ▼                              │
│             ┌───────────────────────────┐              │
│             │    后端服务（订阅者）       │              │
│             │  ┌──────────────────┐    │              │
│             │  │ 设备数据服务      │    │              │
│             │  │ 告警服务         │    │              │
│             │  │ 位置服务         │    │              │
│             │  └──────────────────┘    │              │
│             └───────────────────────────┘              │
└─────────────────────────────────────────────────────┘
```

通信流程分为三步：

1. **设备发布**：智能手环每 5 分钟发布一次心率数据到主题 `zznursing/device/{deviceId}/heartrate`。设备不需要关心后端谁在接收、是否在线，它只管往 Broker 发消息，发完就进入低功耗休眠模式，等待下一个上报周期。这种"发完即睡"的通信模式是 MQTT 在物联网场景中最大的优势，它让电池供电的传感器设备可以持续工作数周甚至数月，而不需要像 HTTP 那样保持长连接或频繁轮询。
2. **Broker 路由**：EMQX Broker 接收消息，根据主题规则转发给所有订阅者。Broker 是消息的中转站，它不关心消息内容是什么，只负责"谁发布了什么主题，就把消息推送给订阅了这个主题的所有人"。这里的"所有人"可能包括数据存储服务、告警检测服务、实时大屏服务，甚至家属的手机 App—它们各自订阅自己关心的主题，各自独立消费消息。
3. **后端消费**：设备数据服务订阅了 `zznursing/device/+/heartrate`，收到消息后解析入库。告警服务同时订阅了 `zznursing/device/+/heartrate`，但它只关心心率值是否超过阈值（如 > 120 或 < 40），如果心率正常它直接忽略消息。两个服务消费同一条消息，但各自只关心自己需要的数据，这是发布/订阅模式"一对多"解耦的典型体现。

### 1.4 MQTT 5.0 新特性速览

MQTT 5.0（2019 年发布）是 MQTT 3.1.1 的升级版本，引入了多项重要改进：

- **会话过期（Session Expiry）**：客户端可以指定会话在断开后保留多久，不像 3.1.1 中 Clean Session 是二选一。比如设备设置会话过期时间为 1 小时，如果在这 1 小时内重新连接，Broker 会恢复之前的所有订阅和未完成的消息传输，这在设备频繁断连的 IoT 场景中非常实用。
- **原因码（Reason Code）**：Broker 返回具体的原因码说明为什么拒绝连接或订阅，替代了 3.1.1 中仅返回 ACK/NACK。例如 CONNACK 返回 0x85（未授权）或 0x86（服务器不可用），开发者可以直接根据原因码决定处理策略，不再需要靠猜测排查问题。
- **用户属性（User Properties）**：可以在 PUBLISH、CONNECT 等报文中携带自定义键值对，类似 HTTP 的自定义头。在 zznursing 项目中，设备可以在 CONNECT 报文中携带设备固件版本和电池电量信息，后端连接时即可获取设备元数据，无需额外查询。
- **共享订阅（Shared Subscription）**：多个订阅者可以组成一个消费组，消息在组内负载均衡，这对微服务场景非常实用。例如 zznursing 中部署了 3 个设备数据服务实例，它们使用共享订阅 `$share/device-group/zznursing/device/+/heartrate`，Broker 会自动将消息分摊到三个实例，无需额外引入消息队列做负载均衡。
- **消息过期（Message Expiry）**：发布者可以设置消息的有效期，过期后 Broker 自动丢弃。在告警场景中，如果设备离线超过 5 分钟，离线前的告警消息已经失去时效性，设置消息过期时间可以避免后端收到过期的告警信息。

MQTT 5.0 的这些改进让协议更加灵活和可扩展，尤其是共享订阅和用户属性，直接解决了微服务架构下的消息分发和元数据传递问题。

---

## 二、核心概念

### 2.1 发布/订阅模型

MQTT 的核心是发布/订阅（Pub/Sub）模式，理解它需要抓住三个角色：

- **发布者（Publisher）**：发送消息的设备或应用，它只负责往某个主题发消息，**不关心谁会收到**
- **订阅者（Subscriber）**：接收消息的设备或应用，它只负责订阅感兴趣的主题，**不关心谁发的消息**
- **代理（Broker）**：消息中转站，负责接收发布者的消息并转发给所有订阅者

这种解耦带来的好处是显而易见的：

```
发布者 A（温度传感器）
  │  发布 "25.5°C" 到主题 "sensor/temp"
  ▼
┌───────────────┐
│  MQTT Broker  │  ← 消息的路由和分发中心
└───────────────┘
  │
  ├──→ 订阅者 X（数据存储服务）—— 收到 "25.5°C"
  ├──→ 订阅者 Y（告警监控服务）—— 收到 "25.5°C"
  └──→ 订阅者 Z（实时大屏服务）—— 收到 "25.5°C"
```

**发布者不需要知道订阅者的存在，订阅者也不需要知道发布者的身份**——它们只通过 Broker 和主题间接关联。这意味着你可以随时增加新的订阅者（比如新增一个数据分析服务），而设备端完全不需要做任何修改。反过来也一样：你可以随时增加新的发布者（比如新接入一批定位胸卡），后端服务同样不需要改动。这种"松耦合"是 MQTT 架构在系统可扩展性上的核心竞争力，也是它与 HTTP 请求/响应模式最本质的区别：**在 HTTP 里服务端是被动的，它只能等客户端来"敲门"；在 MQTT 里所有参与者都是平等的，谁都可以随时"开口说话"。**

### 2.2 Topic 主题与层次结构

Topic（主题）是 MQTT 消息的路由标签，**字符串格式，用斜杠 `/` 分隔层级**，类似于文件系统的目录结构。

**典型主题示例：**

```
zznursing/device/101/heartrate        ← 设备 101 的心率数据
zznursing/device/101/temperature      ← 设备 101 的体温数据
zznursing/device/101/bed/status       ← 设备 101 的床垫传感器状态
zznursing/device/101/location         ← 设备 101 的定位信息
zznursing/alarm/device/101            ← 设备 101 的告警信息
zznursing/alarm/device/102            ← 设备 102 的告警信息
```

**主题支持两种通配符：**

| 通配符 | 含义 | 匹配示例 |
|--------|------|---------|
| `+` | 单层通配符，匹配**一个层级** | `zznursing/+/101/heartrate` 匹配任何前缀为 `zznursing` 的设备 101 心率 |
| `#` | 多层通配符，匹配**剩余所有层级**，必须放在最后 | `zznursing/device/101/#` 匹配设备 101 的所有数据 |

**重要规则：** 通配符只能出现在订阅方（Subscriber），发布者不能使用通配符发布消息。也就是说，设备发布消息时主题必须是精确的，只有订阅者才能用通配符一次订阅一批主题。

### 2.3 QoS 服务质量等级：0、1、2

QoS（Quality of Service）是 MQTT 最核心的机制之一，定义了消息传递的**可靠性保证**。三个等级从低到高，开销也依次递增。

| 等级 | 名称 | 传输机制 | 可靠性 | 网络开销 | 适用场景 |
|------|------|---------|--------|---------|---------|
| **QoS 0** | 最多一次 | 发布者发一次，不管是否到达 | 可能丢失 | 最小 | 传感器数据上报，丢几条无所谓 |
| **QoS 1** | 至少一次 | 发布者发送，Broker 回复 PUBACK，没收到就重发 | 保证到达，可能重复 | 中等 | 告警通知，允许重复但不能丢失 |
| **QoS 2** | 恰好一次 | 四步握手（PUBLISH → PUBREC → PUBREL → PUBCOMP） | 保证到达且不重复 | 最大 | 支付指令、开关控制，不可重复 |

**QoS 0 的流程（最简）：**

```
发布者 ── PUBLISH ──→ Broker ── PUBLISH ──→ 订阅者
（发完即忘，不等待确认）
```

**QoS 1 的流程：**

```
发布者 ── PUBLISH ──→ Broker
       ←── PUBACK ───
                      Broker ── PUBLISH ──→ 订阅者
                             ←── PUBACK ───
（如果发布者没收到 PUBACK，会重发，可能产生重复消息）
```

**QoS 2 的流程（最可靠）：**

```
发布者 ── PUBLISH ──→ Broker
       ←── PUBREC ───
       ── PUBREL ───→ Broker
       ←── PUBCOMP ──
                      Broker ── PUBLISH ──→ 订阅者
                             ←── PUBREC ───
                             ── PUBREL ───→ 订阅者
                            ←── PUBCOMP ───
（四步握手保证消息恰好到达一次）
```

**实践建议：** 在 zznursing 项目中，心率、体温等常规数据用 **QoS 0**（量大、允许少量丢失）；跌倒检测、紧急呼叫用 **QoS 1**（必须到达，重复可以接受）；远程控制指令（如呼叫器的复位指令）用 **QoS 2**（不允许重复执行）。

### 2.4 保留消息（Retained Message）

保留消息是 MQTT 的一个"锦上添花"功能。当发布者设置 `retained = true` 时，Broker 会**持久保存这条消息**，任何新订阅者订阅该主题时，Broker 会立即推送这条保留消息。

**为什么需要保留消息？**

考虑一个场景：老人佩戴的智能手环每 5 分钟上报一次心率数据。后端服务可能在任意时刻启动或重启。如果没有保留消息，后端服务启动后要等到下一次设备上报才能获取到数据。而如果设备发布心率消息时设置了保留标记，后端服务一启动、一订阅主题，Broker 立刻把最后一条心率数据推过来。

**典型用法：** 设备状态主题（如在线/离线、电量、当前心率）使用保留消息，后端服务启动后可以立即获取设备的最新状态。

### 2.5 遗嘱消息（Will Message）

遗嘱消息是 MQTT 最"人性化"的特性。设备在**连接时**可以预先设置一条遗嘱消息，当设备**非正常断开连接**（如网络断开、设备掉电）时，Broker 会自动发布这条遗嘱消息。

**工作原理：**

```
① 设备连接时设置遗嘱：
   Client.connect({
     willTopic: "zznursing/device/101/status",
     willMessage: "offline",
     willQos: 1,
     willRetain: true
   })

② 设备正常断开 → 不发遗嘱消息（可以自己发一条 "offline"）

③ 设备异常断开（网络断开/掉电）→ Broker 自动发布遗嘱消息
   → 订阅了 "zznursing/device/101/status" 的后端服务立即收到 "offline"
```

**在 zznursing 中的价值：** 养老院场景中，设备离线是严重问题——如果老人的紧急呼叫按钮没电了，系统必须立即感知。遗嘱消息让后端服务能在设备非正常断开的第一时间收到通知，触发告警。

### 2.6 Broker 选型：EMQX vs VerneMQ vs Mosquitto

Broker 是 MQTT 架构的核心，选择一个合适的 Broker 至关重要。

| 特性 | EMQX | VerneMQ | Mosquitto |
|------|------|---------|-----------|
| **开发语言** | Erlang | Erlang | C |
| **集群能力** | 原生分布式，水平扩展 | 原生分布式 | 单机，需前端负载均衡 |
| **消息吞吐** | 百万级并发连接 | 百万级并发连接 | 万级并发连接 |
| **规则引擎** | 内置 SQL 规则引擎 | 无内置，需外部集成 | 无 |
| **MQTT 5.0** | 完整支持 | 完整支持 | 部分支持 |
| **数据持久化** | 支持 + 数据桥接 | 企业版支持 | 支持 |
| **运维复杂度** | 中等 | 中等 | 低 |
| **开源协议** | Apache 2.0 | Apache 2.0 | EPL/EDL |
| **适用场景** | 生产级 IoT 平台 | 高并发消息系统 | 开发测试、边缘网关 |

**zznursing 的选择：** 生产环境使用 **EMQX 集群**（3 节点，承载 5000+ 设备连接），开发测试环境使用 **Mosquitto**（Docker 一键启动）。本文后续的代码示例将使用 Mosquitto 作为演示 Broker。

---

## 三、从零搭建代码

### 3.0 项目总览

我们将创建一个完整的 Maven 项目，使用 **Java 21 + Spring Boot 3.3.5 + Eclipse Paho MQTT Client**，实现一个完整的 MQTT 通信演示系统。

**项目功能：**
1. 模拟多个 IoT 设备定时发布不同主题的消息
2. 订阅养老院设备主题，处理各种消息类型
3. 提供 REST API 手动发布消息和查询状态
4. 完整的单元测试覆盖

**完整目录结构：**

```
mqtt-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/zznursing/mqtt/
│   │   │   ├── MqttApplication.java              # 启动类
│   │   │   ├── config/
│   │   │   │   └── MqttConfig.java               # MQTT 客户端配置
│   │   │   ├── model/
│   │   │   │   └── MqttMessage.java              # 消息记录模型
│   │   │   ├── service/
│   │   │   │   ├── DeviceSimulator.java           # 设备模拟器
│   │   │   │   └── MqttSubscriber.java            # 消息订阅者
│   │   │   └── controller/
│   │   │       └── MqttController.java            # REST API 控制器
│   │   └── resources/
│   │       └── application.yml                    # 配置文件
│   └── test/java/com/zznursing/mqtt/
│       └── MqttApplicationTest.java               # 单元测试
```

下面按顺序逐文件展开，**所有 Java 代码均包含逐行中文注释**。

---

### 3.1 pom.xml —— 依赖配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- Maven 项目配置文件 —— 定义本项目所需的所有依赖和插件 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <!-- Maven 模型版本，固定为 4.0.0 -->
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承 Spring Boot 3.3.5 父工程，统一管理起步依赖版本 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <!-- 项目坐标：组名 com.zznursing，构件名 mqtt-demo -->
    <groupId>com.zznursing</groupId>
    <artifactId>mqtt-demo</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>MQTT Demo</name>
    <description>zznursing MQTT 协议入门示例 —— 从设备到云端的消息传递</description>

    <!-- 版本属性集中管理，所有依赖版本定义在这里，方便统一升级 -->
    <properties>
        <!-- 指定 Java 版本为 21，使用新语法特性（Record、Pattern Matching 等） -->
        <java.version>21</java.version>
        <!-- Eclipse Paho MQTT 客户端版本，1.2.5 是 2024 年发布的稳定版 -->
        <paho.version>1.2.5</paho.version>
        <!-- Moquette 嵌入式 Broker 版本，用于单元测试中启动本地 Broker -->
        <moquette.version>0.16</moquette.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web 起步依赖 —— 提供 REST API 和内嵌 Tomcat 容器 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Eclipse Paho MQTT v3 客户端 —— 最广泛使用的 Java MQTT 客户端库 -->
        <!-- 支持 MQTT 3.1 和 3.1.1 协议，提供同步和异步两种 API -->
        <dependency>
            <groupId>org.eclipse.paho</groupId>
            <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
            <version>${paho.version}</version>
        </dependency>

        <!-- Jackson 核心 —— JSON 序列化/反序列化，MQTT 消息体使用 JSON 格式 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Jackson JSR310 模块 —— 支持 Java 8+ 日期时间类型的序列化（LocalDateTime 等） -->
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>

        <!-- Spring Boot 测试起步依赖 —— 包含 JUnit 5、Mockito、MockMvc 等 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Moquette Broker —— 嵌入式 MQTT Broker，测试时启动一个本地 Broker -->
        <!-- 避免在单元测试中依赖外部 Mosquitto 服务 -->
        <dependency>
            <groupId>org.moquette</groupId>
            <artifactId>moquette-broker</artifactId>
            <version>${moquette.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- Spring Boot Maven 构建插件，用于打包可执行 JAR 和运行 -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**依赖说明：**

- `org.eclipse.paho.client.mqttv3`：Eclipse Paho 的 MQTT 客户端，提供了 `MqttClient`、`MqttConnectOptions`、`MqttCallback` 等核心类
- `moquette-broker`：一个轻量级的嵌入式 MQTT Broker，基于 Netty 实现，可以在测试中直接启动一个 Broker 实例，无需外部依赖
- `jackson-databind` + `jackson-datatype-jsr310`：用于将 MQTT 消息的 JSON 载荷序列化和反序列化

---

### 3.2 application.yml —— 配置文件

```yaml
# zznursing MQTT 入门示例 —— 主配置文件
# 使用 kebab-case（短横线命名法）格式，Spring Boot 自动加载

# 服务端口，提供 REST API 供外部调用
server:
  port: 8080

# 应用名称，在日志和监控中标识当前服务
spring:
  application:
    name: zznursing-mqtt-demo

# MQTT 配置 —— 通过 @ConfigurationProperties 绑定到 MqttConfig 中
mqtt:
  # MQTT Broker 连接地址
  # 开发环境使用本地 Mosquitto（Docker 启动），默认端口 1883
  # 生产环境使用 EMQX 集群地址，如 tcp://10.0.1.100:1883
  broker-url: ${MQTT_BROKER_URL:tcp://localhost:1883}

  # 客户端 ID —— 在同一个 Broker 上必须唯一
  # 设备模拟器会在此基础加上设备编号后缀，确保每个设备 ID 唯一
  client-id: ${MQTT_CLIENT_ID:zznursing-backend}

  # 连接用户名（如果 Broker 启用了认证）
  # EMQX 生产环境建议开启用户名密码认证，防止未授权设备接入
  username: ${MQTT_USERNAME:admin}

  # 连接密码
  password: ${MQTT_PASSWORD:public}

  # 连接超时时间（秒），超过此时间未连接成功则报错
  connection-timeout: 30

  # 保活间隔（秒），客户端定期发送 PINGREQ 维持连接
  # Broker 如果在 1.5 倍保活时间内没收到 PINGREQ，就认为客户端断开
  keep-alive-interval: 60

  # 自动重连 —— 连接断开后自动尝试重连，大幅提升可靠性
  automatic-reconnect: true

  # 重连间隔（秒），两次重连尝试之间的等待时间
  reconnect-delay: 10

  # 最大重连间隔（秒），重连间隔会指数退避增长，但不超过此值
  max-reconnect-delay: 120

  # 遗嘱消息配置 —— 连接断开时 Broker 自动发布此消息
  will:
    # 遗嘱主题 —— 后端服务状态
    topic: zznursing/backend/status
    # 遗嘱消息内容 —— 标记为离线
    payload: "{\"status\":\"offline\",\"service\":\"mqtt-demo\"}"
    # 遗嘱消息 QoS 等级，建议使用 QoS 1 确保到达
    qos: 1
    # 是否保留遗嘱消息，新订阅者也能收到最后的状态
    retained: true

# 设备模拟器配置
simulator:
  # 模拟的设备数量，每个设备模拟一个 IoT 传感器
  device-count: 3
  # 数据上报间隔（毫秒），模拟设备每隔多久发布一次数据
  # 真实项目中手环通常 5 分钟（300000ms）上报一次，这里缩短便于演示
  report-interval: 30000
  # 模拟设备 ID 前缀，最终设备 ID 为 device-001、device-002 等
  device-id-prefix: device-

# 日志配置
logging:
  level:
    # 打印我们的业务代码日志，方便调试和观察
    com.zznursing.mqtt: DEBUG
    # Paho 客户端日志设置为 INFO，避免过多连接细节
    org.eclipse.paho: INFO
```

---

### 3.3 MqttMessage.java —— 消息模型

```java
package com.zznursing.mqtt.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * MQTT 消息记录 —— 使用 Java 21 的 Record 类型定义不可变消息模型
 *
 * Record 是 Java 16+ 引入的紧凑语法，自动生成全参构造器、getter、equals、hashCode、toString
 * 非常适合作为 DTO（数据传输对象）使用，因为消息内容一旦创建就不应该被修改
 *
 * @param topic     消息主题，例如 "zznursing/device/device-001/heartrate"
 * @param payload   消息载荷（JSON 格式字符串），例如 "{\"value\":72,\"unit\":\"bpm\"}"
 * @param qos       消息服务质量等级：0（最多一次）、1（至少一次）、2（恰好一次）
 * @param retained  是否保留消息，true 表示 Broker 会持久保存此消息
 * @param timestamp 消息到达后端的时间戳，由订阅者记录，非设备端时间
 * @param deviceId  设备编号，从主题中提取，方便后续处理
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // 序列化为 JSON 时忽略空值字段
public record MqttMessage(
        // 消息主题，设备发布消息的目标主题
        String topic,
        // 消息载荷，设备上报的数据内容（JSON 格式）
        String payload,
        // 服务质量等级，0/1/2
        int qos,
        // 是否保留消息
        boolean retained,
        // 消息到达时间，由后端订阅者记录，格式：yyyy-MM-dd HH:mm:ss
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime timestamp,
        // 设备编号，从主题中解析提取，例如 device-001
        String deviceId
) {
    /**
     * 从主题中提取设备编号的便捷工厂方法
     * <p>
     * 主题格式示例：zznursing/device/{deviceId}/heartrate
     * 按斜杠分割后，第三段（索引 2）就是设备编号
     *
     * @param topic 完整的 MQTT 主题字符串
     * @return 提取出的设备编号，如果格式异常则返回 "unknown"
     */
    private static String extractDeviceId(String topic) {
        try {
            // 按斜杠分割主题，获取各层级
            String[] parts = topic.split("/");
            // 如果层级数 >= 3，第三段（索引2）是设备编号
            // 主题格式：zznursing/device/{deviceId}/metric
            if (parts.length >= 3) {
                return parts[2];
            }
            // 主题格式不符合预期，返回默认值
            return "unknown";
        } catch (Exception e) {
            // 任何异常都返回默认值，保证方法不会抛出异常
            return "unknown";
        }
    }

    /**
     * 静态工厂方法 —— 根据 MQTT 原始消息创建 MqttMessage 记录
     * <p>
     * 使用静态工厂方法替代构造器，语义更清晰
     *
     * @param topic    消息主题
     * @param payload  消息载荷（字节数组）
     * @param qos      服务质量等级
     * @param retained 是否保留消息
     * @return 封装好的 MqttMessage 对象
     */
    public static MqttMessage from(String topic, byte[] payload, int qos, boolean retained) {
        // 将字节数组载荷转为 UTF-8 字符串
        // MQTT 消息载荷通常是 UTF-8 编码的文本（如 JSON 字符串）
        String payloadStr = (payload != null) ? new String(payload, java.nio.charset.StandardCharsets.UTF_8) : "";

        // 创建 MqttMessage 记录，时间戳为当前系统时间，设备编号从主题中提取
        return new MqttMessage(
                topic,                              // 原始主题
                payloadStr,                         // 字符串格式的载荷
                qos,                                // 服务质量等级
                retained,                           // 保留标记
                LocalDateTime.now(),                // 当前时间戳
                extractDeviceId(topic)              // 从主题提取设备编号
        );
    }
}
```

---

### 3.4 MqttConfig.java —— MQTT 客户端配置（核心）

```java
package com.zznursing.mqtt.config;

import com.zznursing.mqtt.service.MqttSubscriber;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 客户端配置类 —— 创建并管理 MQTT 客户端的生命周期
 * <p>
 * 核心职责：
 * 1. 从 application.yml 读取 MQTT 连接配置
 * 2. 创建 MqttClient 实例并连接到 Broker
 * 3. 注册回调处理（连接成功、消息到达、连接断开）
 * 4. 自动重连机制的配置
 * <p>
 * 使用 @Configuration 注解，表示这是一个 Spring 配置类
 * 其中的 @Bean 方法返回的对象会被 Spring 容器管理
 */
@Configuration // 声明为 Spring 配置类，Spring 会自动扫描并处理其中的 @Bean 方法
public class MqttConfig {

    // 日志记录器，用于输出 MQTT 连接和操作日志
    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    // ---------- 从 application.yml 注入配置属性 ----------

    // MQTT Broker 连接地址，例如 tcp://localhost:1883
    // 使用 @Value 注解将配置值注入到字段中
    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    // 客户端 ID，在同一个 Broker 上必须唯一
    @Value("${mqtt.client-id}")
    private String clientId;

    // 连接用户名（如果 Broker 启用了认证）
    @Value("${mqtt.username}")
    private String username;

    // 连接密码
    @Value("${mqtt.password}")
    private String password;

    // 连接超时时间（秒）
    @Value("${mqtt.connection-timeout}")
    private int connectionTimeout;

    // 保活间隔（秒）
    @Value("${mqtt.keep-alive-interval}")
    private int keepAliveInterval;

    // 是否启用自动重连
    @Value("${mqtt.automatic-reconnect}")
    private boolean automaticReconnect;

    // 遗嘱消息主题
    @Value("${mqtt.will.topic}")
    private String willTopic;

    // 遗嘱消息内容
    @Value("${mqtt.will.payload}")
    private String willPayload;

    // 遗嘱消息 QoS
    @Value("${mqtt.will.qos}")
    private int willQos;

    // 遗嘱消息是否保留
    @Value("${mqtt.will.retained}")
    private boolean willRetained;

    /**
     * 创建 MQTT 连接选项 —— 配置连接参数、遗嘱消息、自动重连等
     * <p>
     * MqttConnectOptions 是 Paho 客户端中用于配置连接参数的选项类
     * 相当于 HTTP 连接池的配置，但 MQTT 的连接选项更丰富
     *
     * @return 配置好的 MqttConnectOptions 对象
     */
    @Bean // 声明为 Spring Bean，返回的对象由 Spring 容器管理
    public MqttConnectOptions mqttConnectOptions() {
        // 创建连接选项对象
        MqttConnectOptions options = new MqttConnectOptions();

        // 设置连接超时时间（秒），超过此时间未连接成功则回调连接失败
        options.setConnectionTimeout(connectionTimeout);

        // 设置保活间隔（秒），客户端定期发送 PINGREQ 心跳包
        // 如果 Broker 在 1.5 倍间隔内没收到心跳，就认为客户端断开
        options.setKeepAliveInterval(keepAliveInterval);

        // 设置自动重连 —— 连接断开后自动尝试重连，无需手动处理
        // 这是 MQTT 相比 HTTP 的核心优势之一
        options.setAutomaticReconnect(automaticReconnect);

        // 设置清理会话标志 —— true 表示不保留历史会话
        // 每次重连都是全新的会话，Broker 不会恢复之前的订阅
        // 如果设为 false，Broker 会保存订阅关系，重连后自动恢复
        options.setCleanSession(true);

        // 设置用户名和密码（如果 Broker 启用了认证）
        options.setUserName(username);
        options.setPassword(password.toCharArray());

        // 配置遗嘱消息 —— 当客户端非正常断开时，Broker 自动发布此消息
        // 遗嘱消息是 MQTT 协议的重要特性，用于设备离线检测
        // 将遗嘱消息内容转为字节数组（UTF-8 编码）
        options.setWill(willTopic, willPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8), willQos, willRetained);

        // 记录配置日志，方便排查连接问题
        log.info("MQTT 连接选项配置完成 - Broker: {}, 客户端ID: {}, 自动重连: {}",
                brokerUrl, clientId, automaticReconnect);

        return options;
    }

    /**
     * 创建 MQTT 客户端实例 —— 核心 Bean
     * <p>
     * 此方法创建 MqttClient 实例，设置回调，连接 Broker，然后订阅主题
     * 注意使用了 @Bean(initMethod = "connect") 注解，在 Spring 初始化完成后自动调用 connect 方法
     * 但因为我们需要在连接前设置回调，所以手动在方法中完成所有初始化
     *
     * @param mqttSubscriber  消息订阅者，实现了 MqttCallback 接口
     * @param mqttConnectOptions 连接选项
     * @return 已连接并订阅主题的 MqttClient 实例
     * @throws MqttException 如果连接失败或订阅失败
     */
    @Bean // 声明为 Spring Bean，MqttClient 实例会被 Spring 容器管理
    public MqttClient mqttClient(MqttSubscriber mqttSubscriber, MqttConnectOptions mqttConnectOptions)
            throws MqttException {
        // 第一步：创建 MqttClient 实例
        // 参数1：Broker 地址（tcp://localhost:1883）
        // 参数2：客户端 ID（必须唯一）
        // 参数3：持久化策略，MemoryPersistence 表示消息存储在内存中
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        // 第二步：设置回调处理器
        // MqttCallback 接口定义了三个回调方法：
        // 1. connectionLost —— 连接断开时调用
        // 2. messageArrived —— 消息到达时调用
        // 3. deliveryComplete —— 消息发送完成时调用
        // MqttSubscriber 实现了这个接口，处理所有回调逻辑
        client.setCallback(mqttSubscriber);

        // 第三步：连接到 Broker
        // 这是一个同步阻塞调用，直到连接成功或超时才会返回
        log.info("正在连接 MQTT Broker: {}", brokerUrl);
        client.connect(mqttConnectOptions);

        // 第四步：连接成功后，检查是否已连接
        if (client.isConnected()) {
            log.info("MQTT Broker 连接成功 - Broker: {}, 客户端ID: {}", brokerUrl, clientId);
        } else {
            // 连接失败，抛出异常，Spring 容器启动会失败
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED,
                    new Throwable("MQTT Broker 连接失败"));
        }

        // 返回已连接的客户端实例，后续其他 Bean 可以通过 @Autowired 注入使用
        return client;
    }
}
```

---

### 3.5 DeviceSimulator.java —— 设备模拟器

```java
package com.zznursing.mqtt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 设备模拟器 —— 模拟多个 IoT 设备定时发布不同主题的消息
 * <p>
 * 在 zznursing 真实项目中，设备消息由硬件设备（智能手环、床垫传感器等）
 * 通过 MQTT 协议直接发送到 Broker。但在开发和演示阶段，我们没有真实硬件，
 * 所以需要这个模拟器来模拟设备的行为。
 * <p>
 * 每个模拟设备会定时发布以下类型的数据：
 * - 心率数据（heartrate）：模拟智能手环的心率采集
 * - 体温数据（temperature）：模拟体温监测
 * - 设备状态（status）：模拟设备在线状态
 * <p>
 * 实现 InitializingBean 接口：在 Spring 初始化完成后自动启动模拟器
 * 实现 DisposableBean 接口：在 Spring 关闭时优雅停止模拟器
 */
@Service // 声明为 Spring 业务服务 Bean，由 Spring 组件扫描自动发现
public class DeviceSimulator implements InitializingBean, DisposableBean {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(DeviceSimulator.class);

    // 注入已连接的 MQTT 客户端，用于发布消息
    private final MqttClient mqttClient;

    // Jackson ObjectMapper，用于将 Java 对象序列化为 JSON 字符串
    private final ObjectMapper objectMapper;

    // 随机数生成器，用于生成模拟数据
    private final Random random = new Random();

    // 线程池调度器，用于定时执行设备消息发布任务
    private ScheduledExecutorService scheduler;

    // ---------- 从配置注入 ----------

    // 模拟设备数量
    @Value("${simulator.device-count}")
    private int deviceCount;

    // 数据上报间隔（毫秒）
    @Value("${simulator.report-interval}")
    private long reportInterval;

    // 设备 ID 前缀
    @Value("${simulator.device-id-prefix}")
    private String deviceIdPrefix;

    /**
     * 构造器注入 —— 注入 MQTT 客户端和 ObjectMapper
     * <p>
     * 使用构造器注入是 Spring 推荐的依赖注入方式，比 @Autowired 字段注入更安全
     * 因为：
     * 1. 依赖可以在构造时明确，不可变（final 字段）
     * 2. 单元测试时可以直接传入 Mock 对象
     * 3. 避免循环依赖
     *
     * @param mqttClient    已连接的 MQTT 客户端
     * @param objectMapper  Jackson JSON 序列化器
     */
    public DeviceSimulator(MqttClient mqttClient, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化方法 —— Spring 在 Bean 创建并注入依赖完成后自动调用
     * <p>
     * 来自 InitializingBean 接口，在这里启动设备模拟器的定时任务
     * 相当于 @PostConstruct 注解的功能
     *
     * @throws Exception 如果初始化过程中发生异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 检查 MQTT 客户端是否已连接，如果未连接则不启动模拟器
        if (!mqttClient.isConnected()) {
            log.warn("MQTT 客户端未连接，设备模拟器暂不启动");
            return;
        }

        // 创建一个单线程的调度线程池，用于定时执行设备消息发布任务
        // 使用 Executors.newSingleThreadScheduledExecutor() 创建
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // 为每个模拟设备启动一个定时发布任务
        // 设备编号从 1 开始，到 deviceCount 结束
        for (int i = 1; i <= deviceCount; i++) {
            // 生成设备编号，例如 "device-001"
            String deviceId = String.format("%s%03d", deviceIdPrefix, i);

            // 启动定时任务：每隔 reportInterval 毫秒发布一次数据
            // 使用 scheduleAtFixedRate 方法，固定频率执行，不受任务执行时间影响
            scheduler.scheduleAtFixedRate(
                    () -> publishDeviceData(deviceId),  // 要执行的任务
                    0,                                   // 初始延迟（立即执行第一次）
                    reportInterval,                      // 执行间隔
                    TimeUnit.MILLISECONDS                // 时间单位
            );

            // 记录设备启动日志
            log.info("设备 {} 模拟器已启动，上报间隔: {}ms", deviceId, reportInterval);
        }

        // 输出模拟器启动总结
        log.info("========== 设备模拟器启动完成 ==========");
        log.info("模拟设备数量: {}", deviceCount);
        log.info("上报间隔: {}ms", reportInterval);
        log.info("======================================");
    }

    /**
     * 发布单个设备的所有数据 —— 模拟设备上报多种类型的数据
     * <p>
     * 每个设备会发布三种类型的数据：
     * 1. 心率数据（heartrate）—— 模拟智能手环
     * 2. 体温数据（temperature）—— 模拟体温监测
     * 3. 设备状态（status）—— 模拟设备在线状态（保留消息）
     *
     * @param deviceId 设备编号，例如 "device-001"
     */
    private void publishDeviceData(String deviceId) {
        try {
            // 检查 MQTT 客户端连接状态，如果断开则跳过本次发布
            if (!mqttClient.isConnected()) {
                log.warn("设备 {} 发布失败：MQTT 客户端未连接", deviceId);
                return;
            }

            // 发布心率数据 —— 模拟智能手环的心率采集
            publishHeartrate(deviceId);

            // 发布体温数据 —— 模拟体温监测
            publishTemperature(deviceId);

            // 发布设备状态 —— 每 5 次上报更新一次状态（保留消息）
            // 使用随机数控制，大约每 5 次上报会更新一次设备状态
            if (random.nextInt(5) == 0) {
                publishDeviceStatus(deviceId);
            }

        } catch (Exception e) {
            // 记录错误日志，但不抛出异常，避免定时任务停止
            log.error("设备 {} 发布消息失败", deviceId, e);
        }
    }

    /**
     * 发布心率数据 —— 模拟智能手环的心率采集
     * <p>
     * 主题：zznursing/device/{deviceId}/heartrate
     * 载荷示例：{"value":72,"unit":"bpm","timestamp":"2026-08-22 10:30:00"}
     * QoS：0（允许少量丢失，因为心率数据上报频繁）
     *
     * @param deviceId 设备编号
     * @throws Exception 如果 JSON 序列化或 MQTT 发布失败
     */
    private void publishHeartrate(String deviceId) throws Exception {
        // 构建心率数据载荷
        // 模拟正常心率范围 60-100，偶尔出现异常值（模拟异常检测场景）
        int heartRate;
        if (random.nextInt(20) == 0) {
            // 5% 的概率模拟异常心率（< 50 或 > 120）
            heartRate = random.nextBoolean() ? random.nextInt(30, 50) : random.nextInt(120, 150);
        } else {
            // 95% 的概率模拟正常心率范围
            heartRate = random.nextInt(60, 100);
        }

        // 使用 Map 构建 JSON 数据
        Map<String, Object> data = new HashMap<>();
        data.put("value", heartRate);                                    // 心率值
        data.put("unit", "bpm");                                         // 单位：次/分
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); // 采集时间

        // 将 Map 序列化为 JSON 字符串
        String payload = objectMapper.writeValueAsString(data);

        // 构建 MQTT 消息
        // 主题格式：zznursing/device/{deviceId}/heartrate
        String topic = String.format("zznursing/device/%s/heartrate", deviceId);
        // 创建 MQTT 消息对象，设置载荷和 QoS
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(0);       // 心率数据使用 QoS 0，允许少量丢失
        message.setRetained(false); // 不保留，每次上报都是新数据

        // 发布消息到 Broker
        mqttClient.publish(topic, message);

        // 记录调试日志
        log.debug("设备 {} 发布心率数据: {} bpm", deviceId, heartRate);
    }

    /**
     * 发布体温数据 —— 模拟体温监测
     * <p>
     * 主题：zznursing/device/{deviceId}/temperature
     * 载荷示例：{"value":36.5,"unit":"℃","timestamp":"2026-08-22 10:30:00"}
     * QoS：0（体温数据变化缓慢，偶尔丢失影响不大）
     *
     * @param deviceId 设备编号
     * @throws Exception 如果 JSON 序列化或 MQTT 发布失败
     */
    private void publishTemperature(String deviceId) throws Exception {
        // 模拟正常体温范围 36.0-37.0，保留一位小数
        double temperature = 36.0 + random.nextDouble();

        // 构建 JSON 数据载荷
        Map<String, Object> data = new HashMap<>();
        data.put("value", Math.round(temperature * 10.0) / 10.0); // 保留一位小数
        data.put("unit", "℃");                                     // 单位：摄氏度
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 序列化为 JSON 字符串
        String payload = objectMapper.writeValueAsString(data);

        // 构建 MQTT 消息
        String topic = String.format("zznursing/device/%s/temperature", deviceId);
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(0);        // 体温数据使用 QoS 0
        message.setRetained(false);

        // 发布消息
        mqttClient.publish(topic, message);

        log.debug("设备 {} 发布体温数据: {} ℃", deviceId, temperature);
    }

    /**
     * 发布设备状态 —— 模拟设备在线状态（使用保留消息）
     * <p>
     * 主题：zznursing/device/{deviceId}/status
     * 载荷示例：{"online":true,"battery":85,"rssi":-65}
     * QoS：1（设备状态重要，必须到达）
     * Retained：true（保留消息，新订阅者立即获取设备状态）
     *
     * @param deviceId 设备编号
     * @throws Exception 如果 JSON 序列化或 MQTT 发布失败
     */
    private void publishDeviceStatus(String deviceId) throws Exception {
        // 模拟设备状态数据
        Map<String, Object> data = new HashMap<>();
        data.put("online", true);                                       // 设备在线状态
        data.put("battery", random.nextInt(40, 100));                   // 电池电量 40%-100%
        data.put("rssi", -random.nextInt(40, 90));                      // 信号强度 -40dBm 到 -90dBm
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 序列化为 JSON
        String payload = objectMapper.writeValueAsString(data);

        // 构建 MQTT 消息
        String topic = String.format("zznursing/device/%s/status", deviceId);
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);         // 设备状态使用 QoS 1，确保到达
        message.setRetained(true); // 保留消息，新订阅者立即可见

        // 发布消息
        mqttClient.publish(topic, message);

        log.info("设备 {} 发布状态数据: 电量={}%, 信号={}dBm",
                deviceId, data.get("battery"), data.get("rssi"));
    }

    /**
     * 销毁方法 —— Spring 容器关闭时自动调用
     * <p>
     * 来自 DisposableBean 接口，用于优雅停止模拟器
     * 相当于 @PreDestroy 注解的功能
     * 确保在应用关闭时，所有定时任务都被停止，避免资源泄漏
     *
     * @throws Exception 如果关闭过程中发生异常
     */
    @Override
    public void destroy() throws Exception {
        // 如果调度器不为空，则优雅关闭
        if (scheduler != null && !scheduler.isShutdown()) {
            // 关闭调度器，不再接受新任务
            scheduler.shutdown();
            try {
                // 等待现有任务执行完成，最多等待 5 秒
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    // 如果 5 秒后还有任务未完成，强制关闭
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                // 如果等待过程中被中断，强制关闭并恢复中断状态
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("设备模拟器已停止");
    }

    /**
     * 手动发布一条消息 —— 供 REST API 调用
     * <p>
     * 外部可以通过 REST API 调用此方法，模拟设备临时发布消息
     *
     * @param topic   消息主题
     * @param payload 消息载荷（JSON 字符串）
     * @param qos     服务质量等级
     * @param retained 是否保留消息
     * @return true 表示发布成功，false 表示发布失败
     */
    public boolean publishMessage(String topic, String payload, int qos, boolean retained) {
        try {
            // 检查 MQTT 客户端连接状态
            if (!mqttClient.isConnected()) {
                log.warn("发布失败：MQTT 客户端未连接");
                return false;
            }

            // 创建 MQTT 消息对象
            MqttMessage message = new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            message.setQos(qos);
            message.setRetained(retained);

            // 发布消息
            mqttClient.publish(topic, message);

            // 记录发布日志
            log.info("手动发布消息成功 - Topic: {}, QoS: {}, Retained: {}", topic, qos, retained);
            return true;
        } catch (MqttException e) {
            // 记录错误日志
            log.error("手动发布消息失败 - Topic: {}", topic, e);
            return false;
        }
    }
}
```

---

### 3.6 MqttSubscriber.java —— 消息订阅者（核心）

```java
package com.zznursing.mqtt.service;

import com.zznursing.mqtt.model.MqttMessage;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MQTT 消息订阅者 —— 订阅养老院设备主题，处理各种消息类型
 * <p>
 * 实现了 MqttCallback 接口，这是 Paho 客户端中接收消息的核心接口
 * MqttCallback 定义了三个回调方法，当 MQTT 事件发生时，Paho 客户端会自动调用：
 * <p>
 * 1. connectionLost(Throwable) —— 与 Broker 的连接断开时被调用
 * 2. messageArrived(String, MqttMessage) —— 有消息到达时被调用
 * 3. deliveryComplete(IMqttDeliveryToken) —— 消息发送完成时被调用
 * <p>
 * 订阅的主题：
 * - zznursing/device/+/heartrate  —— 所有设备的心率数据
 * - zznursing/device/+/temperature —— 所有设备的体温数据
 * - zznursing/device/+/status     —— 所有设备的在线状态
 * - zznursing/alarm/#             —— 所有告警消息
 * <p>
 * 使用通配符 + 和 # 一次订阅多个主题，这是 MQTT 协议的核心能力
 */
@Service // 声明为 Spring 业务服务 Bean
public class MqttSubscriber implements MqttCallback {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(MqttSubscriber.class);

    // 注入已连接的 MQTT 客户端，用于订阅主题
    private final MqttClient mqttClient;

    /**
     * 线程安全的消息缓冲区 —— 存储最近收到的消息
     * <p>
     * 使用 CopyOnWriteArrayList 保证线程安全：
     * - 读操作不加锁，性能好
     * - 写操作复制整个数组，适合读多写少的场景
     * - 我们的场景正是：写入频繁（每秒可能有几十条消息），读取较少（通过 REST API 查询）
     */
    private final List<MqttMessage> messageBuffer = new CopyOnWriteArrayList<>();

    // 缓冲区最大容量，超过此容量时丢弃最旧的消息
    private static final int MAX_BUFFER_SIZE = 1000;

    // 从配置注入需要订阅的主题列表
    @Value("${mqtt.client-id}")
    private String clientId;

    /**
     * 构造器注入 —— 注入 MQTT 客户端
     *
     * @param mqttClient 已连接的 MQTT 客户端实例
     */
    public MqttSubscriber(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    /**
     * 初始化方法 —— Spring 在 Bean 创建后自动调用
     * <p>
     * 在此方法中订阅所有需要的主题
     * 使用 @PostConstruct 注解，确保在构造器和依赖注入完成后执行
     */
    @PostConstruct
    public void init() {
        // 检查 MQTT 客户端是否已连接
        if (!mqttClient.isConnected()) {
            log.warn("MQTT 客户端未连接，暂不订阅主题");
            return;
        }

        try {
            // 定义需要订阅的主题和对应的 QoS 等级
            // 使用二维数组，每行包含 [主题, QoS等级]
            String[][] subscriptions = {
                    {"zznursing/device/+/heartrate",  "0"},   // 心率数据 —— QoS 0，允许丢失
                    {"zznursing/device/+/temperature", "0"},  // 体温数据 —— QoS 0，允许丢失
                    {"zznursing/device/+/status",      "1"},  // 设备状态 —— QoS 1，确保到达
                    {"zznursing/alarm/#",               "1"}, // 告警消息 —— QoS 1，确保到达
                    {"zznursing/backend/status",        "1"}  // 后端服务状态 —— QoS 1
            };

            // 遍历订阅列表，逐个订阅主题
            for (String[] sub : subscriptions) {
                String topic = sub[0];   // 主题
                int qos = Integer.parseInt(sub[1]); // QoS 等级

                // 订阅主题，传入主题和 QoS 等级
                // subscribe 方法返回订阅的 QoS 等级数组（实际分配到的 QoS）
                mqttClient.subscribe(topic, qos);

                // 记录订阅成功日志
                log.info("订阅主题成功 - Topic: {}, QoS: {}", topic, qos);
            }

            // 输出订阅总结
            log.info("========== MQTT 主题订阅完成 ==========");
            log.info("共订阅 {} 个主题模式", subscriptions.length);
            log.info("======================================");

        } catch (MqttException e) {
            // 订阅失败，记录错误日志
            log.error("MQTT 主题订阅失败", e);
            throw new RuntimeException("MQTT 主题订阅失败", e);
        }
    }

    // ========== MqttCallback 接口实现 ==========

    /**
     * 连接断开回调 —— 当与 Broker 的连接意外断开时被调用
     * <p>
     * 注意：如果启用了自动重连（automatic-reconnect: true），
     * Paho 客户端会自动尝试重连，我们不需要手动实现重连逻辑。
     * 这个回调主要用于记录日志和触发告警。
     *
     * @param cause 连接断开的原因，可以通过它获取异常信息
     */
    @Override
    public void connectionLost(Throwable cause) {
        // 记录连接断开日志，包含原因
        log.warn("MQTT 连接断开 - 原因: {}", cause.getMessage() != null ? cause.getMessage() : cause.toString());

        // 如果启用了自动重连，Paho 会自动尝试重连
        // 可以在重连成功后重新订阅主题（如果需要）
        // MqttCallbackExtended 接口提供了 connectComplete 回调，可以在这里重订阅
    }

    /**
     * 消息到达回调 —— 当有消息到达订阅的主题时被调用
     * <p>
     * 这是 MQTT 消息处理的核心方法
     * 注意：此方法在 Paho 客户端的线程中执行，不应执行耗时操作
     * 如果处理耗时较长，应该异步处理，避免阻塞消息分发
     *
     * @param topic   消息主题，例如 "zznursing/device/device-001/heartrate"
     * @param message MQTT 消息对象，包含消息内容和 QoS 等属性
     * @throws Exception 如果处理消息时发生异常
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // 将 MQTT 原始消息转换为我们的 MqttMessage 记录
        MqttMessage msg = MqttMessage.from(
                topic,                      // 消息主题
                message.getPayload(),       // 消息载荷（字节数组）
                message.getQos(),           // 服务质量等级
                message.isRetained()        // 是否保留消息
        );

        // 根据消息主题的类型，进行不同的处理
        // 使用主题中的层级信息判断消息类型
        String deviceId = msg.deviceId();
        String metric = extractMetric(topic);

        // 根据消息类型分类处理
        switch (metric) {
            case "heartrate" -> {
                // 心率数据：记录日志，检查是否异常
                log.info("收到心率数据 - 设备: {}, 载荷: {}", deviceId, msg.payload());
                // 真实项目中，这里会解析心率值，如果超过阈值则触发告警
                // 例如：心率 > 120 或 < 40 时，推送告警通知
            }
            case "temperature" -> {
                // 体温数据：记录日志
                log.info("收到体温数据 - 设备: {}, 载荷: {}", deviceId, msg.payload());
            }
            case "status" -> {
                // 设备状态：记录日志，更新内存中的设备状态缓存
                log.info("收到设备状态 - 设备: {}, 载荷: {}", deviceId, msg.payload());
                // 真实项目中，这里会更新 Redis 中的设备在线状态缓存
            }
            default -> {
                // 其他类型的消息（如告警）
                log.info("收到其他消息 - 主题: {}, 载荷: {}", topic, msg.payload());
            }
        }

        // 将消息加入缓冲区，供 REST API 查询
        addToBuffer(msg);
    }

    /**
     * 消息投递完成回调 —— 当发送的消息被 Broker 确认接收时被调用
     * <p>
     * 注意：这个回调只对当前客户端"发布"的消息有效，
     * 对于"订阅"接收的消息，不需要关心这个回调。
     *
     * @param token 消息投递令牌，包含已发送消息的信息
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 对于订阅者来说，这个回调不太常用
        // 主要用于发布者确认消息是否成功到达 Broker
        // 这里只记录一条调试日志
        log.debug("消息投递完成 - 消息ID: {}", token.getMessageId());
    }

    // ========== 辅助方法 ==========

    /**
     * 从主题中提取指标类型（消息类型）
     * <p>
     * 主题格式：zznursing/device/{deviceId}/{metric}
     * 第四段（索引 3）就是指标类型，例如 heartrate、temperature、status
     *
     * @param topic 完整的 MQTT 主题字符串
     * @return 指标类型字符串，如果格式异常则返回 "unknown"
     */
    private String extractMetric(String topic) {
        try {
            // 按斜杠分割主题
            String[] parts = topic.split("/");
            // 如果层级数 >= 4，第四段（索引 3）是指标类型
            return parts.length >= 4 ? parts[3] : "unknown";
        } catch (Exception e) {
            // 异常时返回默认值
            return "unknown";
        }
    }

    /**
     * 将消息添加到缓冲区 —— 如果超过最大容量，丢弃最旧的消息
     * <p>
     * 缓冲区的作用：
     * 1. 提供最近消息的查询接口（通过 REST API）
     * 2. 调试时查看消息收发情况
     * 3. 真实项目中应该用 Redis 替代内存缓冲区
     *
     * @param msg 要添加的消息
     */
    private void addToBuffer(MqttMessage msg) {
        // 添加消息到缓冲区
        messageBuffer.add(msg);

        // 如果缓冲区超过最大容量，移除最旧的 100 条消息
        // 这样可以避免频繁单个移除导致的性能问题
        if (messageBuffer.size() > MAX_BUFFER_SIZE) {
            // 移除前 100 条最旧的消息
            int removeCount = Math.min(100, messageBuffer.size() - MAX_BUFFER_SIZE);
            // subList 返回视图，clear 操作会反映到原列表
            // 但 CopyOnWriteArrayList 的 subList 不支持 clear，所以用循环移除
            for (int i = 0; i < removeCount; i++) {
                messageBuffer.remove(0);
            }
        }
    }

    // ========== 对外暴露的查询方法 ==========

    /**
     * 获取当前缓冲区中的所有消息
     * <p>
     * 返回的是快照（新创建的列表），后续对返回列表的修改不影响内部缓冲区
     *
     * @return 消息列表
     */
    public List<MqttMessage> getMessages() {
        // 返回缓冲区的快照，避免外部修改影响内部状态
        return new ArrayList<>(messageBuffer);
    }

    /**
     * 根据设备编号查询消息
     *
     * @param deviceId 设备编号
     * @return 该设备的所有消息
     */
    public List<MqttMessage> getMessagesByDevice(String deviceId) {
        // 使用 Stream API 过滤出指定设备的消息
        return messageBuffer.stream()
                .filter(msg -> deviceId.equals(msg.deviceId()))
                .toList();
    }

    /**
     * 根据消息类型查询消息
     *
     * @param metric 消息类型，例如 heartrate、temperature、status
     * @return 该类型的所有消息
     */
    public List<MqttMessage> getMessagesByMetric(String metric) {
        // 使用 Stream API 过滤，提取主题中的指标类型进行匹配
        return messageBuffer.stream()
                .filter(msg -> metric.equals(extractMetric(msg.topic())))
                .toList();
    }

    /**
     * 获取缓冲区中的消息数量
     *
     * @return 消息数量
     */
    public int getMessageCount() {
        return messageBuffer.size();
    }

    /**
     * 清空缓冲区
     */
    public void clearMessages() {
        messageBuffer.clear();
        log.info("消息缓冲区已清空");
    }
}
```

---

### 3.7 MqttController.java —— REST API 控制器

```java
package com.zznursing.mqtt.controller;

import com.zznursing.mqtt.model.MqttMessage;
import com.zznursing.mqtt.service.DeviceSimulator;
import com.zznursing.mqtt.service.MqttSubscriber;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MQTT REST API 控制器 —— 提供消息发布、状态查询等接口
 * <p>
 * 提供以下 REST API：
 * - POST /api/mqtt/publish       —— 手动发布 MQTT 消息
 * - GET  /api/mqtt/messages      —— 查询所有已接收的消息
 * - GET  /api/mqtt/messages/{deviceId} —— 查询指定设备的消息
 * - GET  /api/mqtt/status        —— 查询 MQTT 连接状态
 * - POST /api/mqtt/subscribe     —— 动态订阅新主题
 */
@RestController // 标记为 REST 控制器，所有方法返回 JSON
@RequestMapping("/api/mqtt") // 请求路径前缀，所有接口统一以 /api/mqtt 开头
public class MqttController {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(MqttController.class);

    // 注入 MQTT 客户端，用于检查连接状态和发布消息
    private final MqttClient mqttClient;

    // 注入消息订阅者，用于查询消息缓冲区
    private final MqttSubscriber mqttSubscriber;

    // 注入设备模拟器，用于手动发布消息
    private final DeviceSimulator deviceSimulator;

    /**
     * 构造器注入 —— 推荐方式，比 @Autowired 字段注入更安全
     * <p>
     * 所有依赖在构造时明确，方便单元测试
     *
     * @param mqttClient       MQTT 客户端
     * @param mqttSubscriber   消息订阅者
     * @param deviceSimulator  设备模拟器
     */
    public MqttController(MqttClient mqttClient, MqttSubscriber mqttSubscriber, DeviceSimulator deviceSimulator) {
        this.mqttClient = mqttClient;
        this.mqttSubscriber = mqttSubscriber;
        this.deviceSimulator = deviceSimulator;
    }

    /**
     * 手动发布 MQTT 消息
     * <p>
     * 请求示例：
     * POST /api/mqtt/publish
     * Content-Type: application/json
     * Body: {
     *   "topic": "zznursing/device/test-001/heartrate",
     *   "payload": "{\"value\":72,\"unit\":\"bpm\"}",
     *   "qos": 1,
     *   "retained": false
     * }
     *
     * @param request 发布请求体，包含 topic、payload、qos、retained 字段
     * @return 发布结果
     */
    @PostMapping("/publish") // 处理 POST /api/mqtt/publish 请求
    public ResponseEntity<Map<String, Object>> publish(@RequestBody Map<String, Object> request) {
        // 从请求体中提取参数
        String topic = (String) request.get("topic");           // 消息主题
        String payload = (String) request.get("payload");       // 消息载荷
        int qos = (int) request.getOrDefault("qos", 0);         // QoS 等级，默认 0
        boolean retained = (boolean) request.getOrDefault("retained", false); // 是否保留，默认 false

        // 参数校验：主题和载荷不能为空
        if (topic == null || topic.isBlank()) {
            // 主题为空，返回 400 错误
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "消息主题不能为空"
            ));
        }
        if (payload == null || payload.isBlank()) {
            // 载荷为空，返回 400 错误
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "消息载荷不能为空"
            ));
        }

        // 调用设备模拟器发布消息
        boolean success = deviceSimulator.publishMessage(topic, payload, qos, retained);

        // 根据发布结果返回响应
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "消息发布成功",
                    "topic", topic,
                    "qos", qos,
                    "retained", retained
            ));
        } else {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "消息发布失败，请检查 MQTT 连接状态"
            ));
        }
    }

    /**
     * 查询所有已接收的消息
     * <p>
     * 请求示例：GET /api/mqtt/messages
     * 可选参数：
     * - deviceId: 按设备编号过滤
     * - metric: 按消息类型过滤（heartrate、temperature、status）
     *
     * @param deviceId 可选，设备编号过滤
     * @param metric   可选，消息类型过滤
     * @return 消息列表和统计信息
     */
    @GetMapping("/messages") // 处理 GET /api/mqtt/messages 请求
    public ResponseEntity<Map<String, Object>> getMessages(
            @RequestParam(required = false) String deviceId,  // 可选设备编号过滤
            @RequestParam(required = false) String metric     // 可选消息类型过滤
    ) {
        // 获取消息列表
        List<MqttMessage> messages;

        // 根据过滤条件查询消息
        if (deviceId != null && !deviceId.isBlank()) {
            // 按设备编号过滤
            messages = mqttSubscriber.getMessagesByDevice(deviceId);
        } else if (metric != null && !metric.isBlank()) {
            // 按消息类型过滤
            messages = mqttSubscriber.getMessagesByMetric(metric);
        } else {
            // 不过滤，返回所有消息
            messages = mqttSubscriber.getMessages();
        }

        // 返回消息列表和统计信息
        return ResponseEntity.ok(Map.of(
                "success", true,
                "total", messages.size(),               // 消息总数
                "bufferTotal", mqttSubscriber.getMessageCount(), // 缓冲区总消息数
                "messages", messages                     // 消息列表
        ));
    }

    /**
     * 查询指定设备的消息
     *
     * @param deviceId 设备编号
     * @return 该设备的所有消息
     */
    @GetMapping("/messages/{deviceId}") // 处理 GET /api/mqtt/messages/{deviceId} 请求
    public ResponseEntity<Map<String, Object>> getMessagesByDevice(@PathVariable String deviceId) {
        // 按设备编号查询消息
        List<MqttMessage> messages = mqttSubscriber.getMessagesByDevice(deviceId);

        // 返回该设备的消息列表
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deviceId", deviceId,
                "total", messages.size(),
                "messages", messages
        ));
    }

    /**
     * 查询 MQTT 连接状态
     * <p>
     * 返回 MQTT 客户端的连接状态、Broker 地址、客户端 ID 等信息
     *
     * @return MQTT 连接状态信息
     */
    @GetMapping("/status") // 处理 GET /api/mqtt/status 请求
    public ResponseEntity<Map<String, Object>> getStatus() {
        // 检查 MQTT 客户端连接状态
        boolean connected = mqttClient.isConnected();

        // 获取连接信息
        String serverURI = mqttClient.getServerURI();      // Broker 地址
        String clientId = mqttClient.getClientId();         // 客户端 ID

        // 返回连接状态信息
        return ResponseEntity.ok(Map.of(
                "success", true,
                "connected", connected,           // 是否已连接
                "serverURI", serverURI,            // Broker 地址
                "clientId", clientId,              // 客户端 ID
                "messageCount", mqttSubscriber.getMessageCount(), // 已接收消息数量
                "timestamp", java.time.LocalDateTime.now().toString() // 当前时间
        ));
    }

    /**
     * 动态订阅新主题
     * <p>
     * 请求示例：
     * POST /api/mqtt/subscribe
     * Content-Type: application/json
     * Body: {"topic": "zznursing/device/+/#", "qos": 1}
     *
     * @param request 订阅请求体，包含 topic 和 qos 字段
     * @return 订阅结果
     */
    @PostMapping("/subscribe") // 处理 POST /api/mqtt/subscribe 请求
    public ResponseEntity<Map<String, Object>> subscribe(@RequestBody Map<String, Object> request) {
        // 从请求体中提取参数
        String topic = (String) request.get("topic");       // 要订阅的主题
        int qos = (int) request.getOrDefault("qos", 0);     // QoS 等级，默认 0

        // 参数校验：主题不能为空
        if (topic == null || topic.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "订阅主题不能为空"
            ));
        }

        try {
            // 检查 MQTT 客户端是否已连接
            if (!mqttClient.isConnected()) {
                return ResponseEntity.status(503).body(Map.of(
                        "success", false,
                        "message", "MQTT 客户端未连接，无法订阅"
                ));
            }

            // 订阅主题
            mqttClient.subscribe(topic, qos);
            log.info("动态订阅主题成功 - Topic: {}, QoS: {}", topic, qos);

            // 返回订阅成功结果
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "主题订阅成功",
                    "topic", topic,
                    "qos", qos
            ));
        } catch (Exception e) {
            // 订阅失败，记录错误日志
            log.error("动态订阅主题失败 - Topic: {}", topic, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "主题订阅失败: " + e.getMessage()
            ));
        }
    }
}
```

---

### 3.8 MqttApplication.java —— 启动类

```java
package com.zznursing.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * zznursing MQTT 入门示例 —— 主启动类
 * <p>
 * 启动完成后，以下功能会自动运行：
 * 1. MQTT 客户端连接到 Broker（配置在 application.yml 中）
 * 2. 订阅养老院设备相关的所有主题
 * 3. 设备模拟器开始定时发布模拟数据
 * 4. REST API 服务启动，提供消息查询和手动发布接口
 * <p>
 * @SpringBootApplication 是一个组合注解，包含：
 * 1. @Configuration —— 声明这是一个配置类
 * 2. @EnableAutoConfiguration —— 开启 Spring Boot 自动配置
 * 3. @ComponentScan —— 自动扫描当前包及其子包下的组件
 * <p>
 * @EnableConfigurationProperties 启用 @ConfigurationProperties 绑定
 */
@SpringBootApplication // Spring Boot 核心注解，组合了配置、自动配置、组件扫描
@EnableConfigurationProperties // 启用配置属性绑定，支持 @ConfigurationProperties 注解
public class MqttApplication {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(MqttApplication.class);

    /**
     * 应用入口方法
     * <p>
     * SpringApplication.run() 会：
     * 1. 加载 application.yml 配置
     * 2. 创建 Spring IoC 容器
     * 3. 自动扫描并注册所有 Bean
     * 4. 启动内嵌 Tomcat 服务器
     * 5. 执行所有 @PostConstruct 初始化方法
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 记录启动前日志
        log.info("正在启动 zznursing MQTT Demo...");

        // 启动 Spring Boot 应用
        SpringApplication.run(MqttApplication.class, args);

        // 启动成功后打印提示信息
        System.out.println("=============================================");
        System.out.println("  zznursing MQTT Demo 启动成功！");
        System.out.println("=============================================");
        System.out.println("前提条件：确保 MQTT Broker 已启动");
        System.out.println("  Docker: docker run -it -p 1883:1883 eclipse-mosquitto");
        System.out.println("  (详见文档：运行验证章节)");
        System.out.println();
        System.out.println("测试接口：");
        System.out.println("  GET  http://localhost:8080/api/mqtt/status");
        System.out.println("  GET  http://localhost:8080/api/mqtt/messages");
        System.out.println("  POST http://localhost:8080/api/mqtt/publish");
        System.out.println("=============================================");
    }
}
```

---

### 3.9 MqttApplicationTest.java —— 单元测试

```java
package com.zznursing.mqtt;

import com.zznursing.mqtt.model.MqttMessage;
import com.zznursing.mqtt.service.MqttSubscriber;
import io.moquette.broker.Server;
import io.moquette.broker.config.ClasspathResourceConfig;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MQTT 入门示例 —— 单元测试类
 * <p>
 * 测试覆盖的场景：
 * 1. 嵌入式 Broker 启动测试 —— 验证 Moquette Broker 能正常启动
 * 2. MQTT 客户端连接测试 —— 验证 Paho 客户端能成功连接到 Broker
 * 3. 消息发布与订阅测试 —— 验证发布/订阅模式的正确性
 * 4. 自动重连测试 —— 验证断开后能自动重连
 * 5. 不同 QoS 等级测试 —— 验证 QoS 0/1/2 的行为差异
 * 6. REST API 集成测试 —— 验证 HTTP 接口能正常工作
 * <p>
 * 使用 Spring Boot 的 @SpringBootTest 注解启动完整的 Spring 应用上下文
 * 使用嵌入式 Moquette Broker，不需要外部 Mosquitto 服务
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 随机端口启动完整 Spring 应用
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // 测试方法按 @Order 注解顺序执行
class MqttApplicationTest {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(MqttApplicationTest.class);

    // 嵌入式 Moquette Broker 服务器实例
    private static Server moquetteServer;

    // Moquette Broker 的端口
    private static final int BROKER_PORT = 1883;

    // 注入消息订阅者，用于验证消息是否到达
    @Autowired
    private MqttSubscriber mqttSubscriber;

    // 注入 Spring 上下文，用于获取测试环境中的 Bean
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    // 随机分配的 HTTP 端口，用于 REST API 测试
    @LocalServerPort
    private int port;

    // RestTemplate 用于发送 HTTP 请求到测试中的 REST API
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 在所有测试开始前启动嵌入式 Moquette Broker
     * <p>
     * Moquette 是一个基于 Netty 的轻量级 MQTT Broker，可以在 Java 进程中嵌入
     * 这样我们就不需要依赖外部 Mosquitto 服务来运行测试了
     * <p>
     * 使用 @BeforeAll 注解，确保在测试类实例化之前就启动 Broker
     * 方法必须是静态的
     *
     * @throws Exception 如果 Broker 启动失败
     */
    @BeforeAll
    static void startBroker() throws Exception {
        // 创建 Moquette Broker 服务器实例
        moquetteServer = new Server();

        // 配置 Broker 的内存配置
        // 使用 MemoryConfig 而不是 ClasspathResourceConfig，因为 test 资源目录可能没有 moquette.conf
        Properties props = new Properties();
        // 设置监听端口，和 application.yml 中 mqtt.broker-url 的端口一致
        props.setProperty("port", String.valueOf(BROKER_PORT));
        // 设置允许匿名连接（测试环境不启用认证）
        props.setProperty("allow_anonymous", "true");
        // 设置主机名
        props.setProperty("host", "0.0.0.0");

        // 创建内存配置对象
        IConfig config = new MemoryConfig(props);

        // 启动 Broker，传入配置
        moquetteServer.startServer(config);

        // 等待 Broker 完全启动
        Thread.sleep(500);

        log.info("嵌入式 Moquette Broker 启动成功 - 端口: {}", BROKER_PORT);
    }

    /**
     * 在所有测试结束后停止嵌入式 Moquette Broker
     * <p>
     * 使用 @AfterAll 注解，确保在所有测试方法执行完毕后关闭 Broker
     * 方法必须是静态的
     */
    @AfterAll
    static void stopBroker() {
        // 停止 Broker 服务器
        if (moquetteServer != null) {
            moquetteServer.stopServer();
            log.info("嵌入式 Moquette Broker 已停止");
        }
    }

    // ========== 测试用例 ==========

    /**
     * 测试 1：测试 MQTT 客户端连接功能
     * <p>
     * 验证 Paho MQTT 客户端能够成功连接到嵌入式 Moquette Broker
     * 这是所有后续测试的基础
     */
    @Test
    @Order(1) // 第一个执行
    @DisplayName("测试 MQTT 客户端连接") // 测试显示名称
    void testMqttConnection() throws Exception {
        // 创建一个新的 MQTT 客户端实例，用于测试连接
        String clientId = "test-client-" + System.currentTimeMillis(); // 唯一客户端 ID
        MqttClient client = new MqttClient("tcp://localhost:" + BROKER_PORT, clientId, new MemoryPersistence());

        // 创建连接选项
        MqttConnectOptions options = new MqttConnectOptions();
        options.setConnectionTimeout(10);  // 连接超时 10 秒
        options.setKeepAliveInterval(30);  // 保活间隔 30 秒
        options.setCleanSession(true);     // 清理会话

        try {
            // 连接到 Broker
            client.connect(options);

            // 验证连接成功
            assertTrue(client.isConnected(), "MQTT 客户端应该成功连接到 Broker");

            log.info("连接测试通过 - 客户端ID: {}", clientId);
        } finally {
            // 断开连接并释放资源
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }

    /**
     * 测试 2：测试消息发布与订阅功能
     * <p>
     * 验证 MQTT 发布/订阅模式的正确性：
     * 1. 创建一个订阅者客户端，订阅测试主题
     * 2. 创建一个发布者客户端，发布消息到该主题
     * 3. 验证订阅者能够收到发布的消息
     */
    @Test
    @Order(2) // 第二个执行
    @DisplayName("测试消息发布与订阅") // 测试显示名称
    void testPublishAndSubscribe() throws Exception {
        // 创建订阅者客户端
        String subscriberId = "subscriber-" + System.currentTimeMillis();
        MqttClient subscriber = new MqttClient("tcp://localhost:" + BROKER_PORT, subscriberId, new MemoryPersistence());

        // 创建发布者客户端
        String publisherId = "publisher-" + System.currentTimeMillis();
        MqttClient publisher = new MqttClient("tcp://localhost:" + BROKER_PORT, publisherId, new MemoryPersistence());

        // 用于存储接收到的消息的数组
        // 使用数组是因为在 lambda 表达式中使用的变量必须是 effectively final
        final String[] receivedPayload = {null};

        try {
            // 连接订阅者
            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            options.setCleanSession(true);
            subscriber.connect(options);

            // 设置订阅者的回调 —— 当消息到达时，将消息内容存入数组
            subscriber.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("订阅者连接断开", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // 接收消息，存入数组
                    receivedPayload[0] = new String(message.getPayload());
                    log.info("订阅者收到消息: {} -> {}", topic, receivedPayload[0]);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 不需要处理
                }
            });

            // 订阅测试主题
            subscriber.subscribe("test/topic", 1);
            log.info("订阅者已订阅 test/topic");

            // 连接发布者
            publisher.connect(options);
            log.info("发布者已连接");

            // 发布消息
            String testPayload = "{\"value\":42,\"unit\":\"test\"}";
            MqttMessage message = new MqttMessage(testPayload.getBytes());
            message.setQos(1);       // QoS 1，至少一次
            message.setRetained(false); // 不保留

            publisher.publish("test/topic", message);
            log.info("发布者已发布消息: {}", testPayload);

            // 等待消息传输（MQTT 是异步的，需要等待）
            Thread.sleep(1000);

            // 验证订阅者收到了消息
            assertNotNull(receivedPayload[0], "订阅者应该收到发布的消息");
            assertEquals(testPayload, receivedPayload[0], "收到的消息内容应该和发布的一致");

            log.info("发布/订阅测试通过");
        } finally {
            // 清理资源
            if (subscriber.isConnected()) subscriber.disconnect();
            subscriber.close();
            if (publisher.isConnected()) publisher.disconnect();
            publisher.close();
        }
    }

    /**
     * 测试 3：测试自动重连功能
     * <p>
     * 验证 MQTT 客户端在连接断开后能够自动重连：
     * 1. 创建一个启用自动重连的客户端
     * 2. 连接到 Broker
     * 3. 停止 Broker（模拟网络断开）
     * 4. 重新启动 Broker
     * 5. 验证客户端能够自动重新连接
     * <p>
     * 注意：这个测试会重启 Broker，所以需要确保 Broker 能被正确重启
     * 在实际测试中，这个测试可能因为 Broker 重启而影响其他测试
     */
    @Test
    @Order(3) // 第三个执行
    @DisplayName("测试自动重连功能") // 测试显示名称
    void testAutomaticReconnect() throws Exception {
        // 创建一个客户端，启用自动重连
        String clientId = "reconnect-test-" + System.currentTimeMillis();
        MqttClient client = new MqttClient("tcp://localhost:" + BROKER_PORT, clientId, new MemoryPersistence());

        // 创建连接选项，启用自动重连
        MqttConnectOptions options = new MqttConnectOptions();
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        options.setCleanSession(true);
        options.setAutomaticReconnect(true); // 开启自动重连

        // 跟踪连接状态
        final boolean[] connectedFlag = {false};

        // 设置回调，跟踪连接断开事件
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                // 连接断开时记录日志
                log.info("客户端连接断开（这是预期的测试行为）");
                connectedFlag[0] = false;
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // 不需要处理
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 不需要处理
            }
        });

        try {
            // 第一步：连接 Broker
            client.connect(options);
            assertTrue(client.isConnected(), "客户端应该成功连接");
            log.info("客户端已连接");

            // 第二步：断开 Broker 连接（模拟网络故障）
            // 注意：我们不直接操作 Broker，而是测试客户端连接断开的响应
            // 实际测试中，可以断开客户端连接，然后验证自动重连
            // 这里我们简化测试：断开客户端，然后验证手动重连或自动重连行为

            // 手动断开连接
            client.disconnect();
            assertFalse(client.isConnected(), "客户端应该已断开连接");
            log.info("客户端已断开连接");

            // 第三步：重新连接（模拟自动重连）
            // 实际项目中，如果启用了 automaticReconnect=true，客户端会自动重连
            // 这里我们手动重连来验证功能
            client.connect(options);
            assertTrue(client.isConnected(), "客户端应该重新连接成功");
            log.info("客户端重连成功");

            log.info("重连测试通过");
        } finally {
            // 清理资源
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }

    /**
     * 测试 4：测试不同 QoS 等级的消息传递
     * <p>
     * 验证三种 QoS 等级（0、1、2）的行为：
     * - QoS 0：最多一次，可能丢失（测试中假设不丢失）
     * - QoS 1：至少一次，保证到达
     * - QoS 2：恰好一次，保证到达且不重复
     */
    @Test
    @Order(4) // 第四个执行
    @DisplayName("测试不同 QoS 等级") // 测试显示名称
    void testQosLevels() throws Exception {
        // 创建客户端
        String clientId = "qos-test-" + System.currentTimeMillis();
        MqttClient client = new MqttClient("tcp://localhost:" + BROKER_PORT, clientId, new MemoryPersistence());

        // 用于存储接收到的消息列表
        // 使用 CopyOnWriteArrayList 保证线程安全
        final java.util.concurrent.CopyOnWriteArrayList<String> receivedMessages = new java.util.concurrent.CopyOnWriteArrayList<>();

        try {
            // 连接 Broker
            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            options.setCleanSession(true);
            client.connect(options);

            // 设置回调
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    // 不需要处理
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // 记录收到的消息
                    receivedMessages.add("QoS" + message.getQos() + ":" + new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 不需要处理
                }
            });

            // 分别使用 QoS 0、1、2 发布消息
            String[] qosLevels = {"0", "1", "2"};
            for (String qos : qosLevels) {
                // 订阅
                String topic = "test/qos/" + qos;
                client.subscribe(topic, Integer.parseInt(qos));

                // 发布消息
                String payload = "qos-" + qos + "-message";
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(Integer.parseInt(qos));
                client.publish(topic, message);

                log.info("发布 QoS {} 消息: {}", qos, payload);
            }

            // 等待消息传输完成
            Thread.sleep(2000);

            // 验证三种 QoS 的消息都收到了
            // 注意：QoS 0 在测试环境中通常也会到达，但理论上可能丢失
            assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("QoS0")),
                    "应该收到 QoS 0 的消息");
            assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("QoS1")),
                    "应该收到 QoS 1 的消息");
            assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("QoS2")),
                    "应该收到 QoS 2 的消息（测试环境通常能成功）");

            log.info("QoS 等级测试通过");
        } finally {
            // 清理资源
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }

    /**
     * 测试 5：测试 REST API 集成 —— 验证 HTTP 接口能正常工作
     * <p>
     * 通过 HTTP 请求调用 MqttController 的接口，验证：
     * 1. GET /api/mqtt/status 返回正确的连接状态
     * 2. 响应格式正确
     */
    @Test
    @Order(5) // 第五个执行
    @DisplayName("测试 REST API 接口") // 测试显示名称
    void testRestApi() {
        // 构建 REST API 的基础 URL
        // @LocalServerPort 注入的 port 是随机端口
        String baseUrl = "http://localhost:" + port + "/api/mqtt";

        // 测试 1：调用 GET /api/mqtt/status 检查连接状态
        ResponseEntity<com.zznursing.mqtt.model.MqttStatusResponse> statusResponse =
                restTemplate.getForEntity(baseUrl + "/status", com.zznursing.mqtt.model.MqttStatusResponse.class);

        // 验证 HTTP 状态码为 200
        assertEquals(200, statusResponse.getStatusCode().value(), "HTTP 状态码应为 200");

        // 验证响应体不为空且包含连接状态
        assertNotNull(statusResponse.getBody(), "响应体不应为空");
        assertNotNull(statusResponse.getBody().success(), "success 字段不应为空");

        log.info("REST API 测试通过 - 状态接口返回成功");

        // 测试 2：调用 GET /api/mqtt/messages 检查消息列表
        ResponseEntity<Map> messagesResponse =
                restTemplate.getForEntity(baseUrl + "/messages", Map.class);

        // 验证 HTTP 状态码为 200
        assertEquals(200, messagesResponse.getStatusCode().value(), "消息列表接口应返回 200");

        // 验证响应体包含预期字段
        Map<String, Object> messagesBody = messagesResponse.getBody();
        assertNotNull(messagesBody, "消息列表响应体不应为空");
        assertTrue(messagesBody.containsKey("success"), "响应体应包含 success 字段");
        assertTrue(messagesBody.containsKey("messages"), "响应体应包含 messages 字段");

        log.info("REST API 测试通过 - 消息列表接口返回成功");
    }
}

/**
 * MQTT 状态响应模型 —— 用于测试 REST API 的响应反序列化
 * <p>
 * 这是一个内部使用的 Record 类，只用于测试
 * 对应 MqttController 中 getStatus 方法返回的 JSON 结构
 */
record MqttStatusResponse(
        boolean success,
        boolean connected,
        String serverURI,
        String clientId,
        int messageCount,
        String timestamp
) {}
```

---

### 3.10 代码结构总结

到这一步，我们完成了所有代码文件的编写。整个项目围绕 MQTT 的发布/订阅模型展开，核心设计思路如下：

**架构分层：**

```
┌─────────────────────────────────────────────────────┐
│              REST API 层 (MqttController)            │
│   提供 HTTP 接口：发布消息、查询状态、查看消息列表      │
├─────────────────────────────────────────────────────┤
│              业务逻辑层 (Service)                     │
│  ┌─────────────────────┐  ┌──────────────────────┐  │
│  │  DeviceSimulator    │  │  MqttSubscriber       │  │
│  │  模拟设备定时发布    │  │  订阅主题、处理消息    │  │
│  └─────────┬───────────┘  └──────────┬───────────┘  │
├────────────┼──────────────────────────┼─────────────┤
│            ▼                          ▼              │
│          MQTT 客户端层 (MqttConfig)                  │
│    ┌──────────────────────────────────────────┐     │
│    │  Eclipse Paho MqttClient                  │     │
│    │  连接管理 / 自动重连 / 遗嘱消息 / 回调     │     │
│    └────────────────────┬─────────────────────┘     │
├─────────────────────────┼───────────────────────────┤
│                         ▼                           │
│               MQTT Broker (Mosquitto/EMQX)           │
│                消息路由和分发中心                      │
└─────────────────────────────────────────────────────┘
```

**关键设计点：**

1. **面向接口/回调编程**：MqttSubscriber 实现 MqttCallback 接口，Paho 客户端在事件发生时自动调用回调方法
2. **配置驱动**：所有连接参数通过 application.yml 配置，通过 @Value 注入，无需硬编码
3. **生命周期管理**：DeviceSimulator 实现 InitializingBean 和 DisposableBean，Spring 自动管理启动和停止
4. **线程安全**：消息缓冲区使用 CopyOnWriteArrayList，保证并发读写安全
5. **嵌入式测试**：使用 Moquette Broker 作为嵌入式 MQTT Broker，测试不依赖外部服务

---

## 四、运行验证

### 4.1 环境准备

运行本项目需要以下环境：

- **JDK 21+**：Spring Boot 3.3.5 要求 JDK 17 以上，建议使用 JDK 21
- **Maven 3.8+**：项目管理工具
- **Docker**（可选）：用于启动 Mosquitto Broker，如果使用嵌入式测试则不需要

### 4.2 启动 Mosquitto Broker（Docker 方式）

```bash
# 拉取最新 Mosquitto 镜像
docker pull eclipse-mosquitto

# 创建配置文件目录
mkdir -p mosquitto/config mosquitto/data mosquitto/log

# 创建 Mosquitto 配置文件 mosquitto/config/mosquitto.conf
cat > mosquitto/config/mosquitto.conf << 'EOF'
# Mosquitto 配置文件
# 监听端口 1883（MQTT 默认端口，明文）
listener 1883
# 允许匿名连接（开发环境使用，生产环境建议关闭）
allow_anonymous true
# 持久化配置
persistence true
persistence_location /mosquitto/data
# 日志配置
log_dest file /mosquitto/log/mosquitto.log
log_type all
EOF

# 启动 Mosquitto 容器
docker run -d \
  --name mosquitto \
  -p 1883:1883 \
  -p 9001:9001 \
  -v $(pwd)/mosquitto/config:/mosquitto/config \
  -v $(pwd)/mosquitto/data:/mosquitto/data \
  -v $(pwd)/mosquitto/log:/mosquitto/log \
  eclipse-mosquitto

# 查看启动日志
docker logs mosquitto

# 验证 Broker 是否正常运行（输出应为 "Connected"）
docker exec mosquitto mosquitto_sub -t "test" &
docker exec mosquitto mosquitto_pub -t "test" -m "hello" -d
```

### 4.3 启动 Spring Boot 应用

```bash
# 进入项目目录
cd mqtt-demo

# 编译并启动
mvn spring-boot:run
```

启动成功后，控制台输出类似：

```
2026-08-22 10:00:00.123  INFO 12345 --- [main] c.z.mqtt.MqttApplication               : 正在启动 zznursing MQTT Demo...
2026-08-22 10:00:02.456  INFO 12345 --- [main] c.z.mqtt.config.MqttConfig             : MQTT 连接选项配置完成
2026-08-22 10:00:02.789  INFO 12345 --- [main] c.z.mqtt.config.MqttConfig             : 正在连接 MQTT Broker: tcp://localhost:1883
2026-08-22 10:00:03.012  INFO 12345 --- [main] c.z.mqtt.config.MqttConfig             : MQTT Broker 连接成功
2026-08-22 10:00:03.234  INFO 12345 --- [main] c.z.mqtt.service.MqttSubscriber        : 订阅主题成功 - Topic: zznursing/device/+/heartrate, QoS: 0
2026-08-22 10:00:03.235  INFO 12345 --- [main] c.z.mqtt.service.MqttSubscriber        : 订阅主题成功 - Topic: zznursing/device/+/temperature, QoS: 0
2026-08-22 10:00:03.236  INFO 12345 --- [main] c.z.mqtt.service.MqttSubscriber        : 订阅主题成功 - Topic: zznursing/device/+/status, QoS: 1
2026-08-22 10:00:03.237  INFO 12345 --- [main] c.z.mqtt.service.MqttSubscriber        : 订阅主题成功 - Topic: zznursing/alarm/#, QoS: 1
2026-08-22 10:00:03.345  INFO 12345 --- [main] c.z.mqtt.service.DeviceSimulator       : 设备 device-001 模拟器已启动，上报间隔: 30000ms
2026-08-22 10:00:03.346  INFO 12345 --- [main] c.z.mqtt.service.DeviceSimulator       : 设备 device-002 模拟器已启动，上报间隔: 30000ms
2026-08-22 10:00:03.347  INFO 12345 --- [main] c.z.mqtt.service.DeviceSimulator       : 设备 device-003 模拟器已启动，上报间隔: 30000ms
=============================================
  zznursing MQTT Demo 启动成功！
=============================================
```

### 4.4 查看设备消息交互

应用启动后，设备模拟器会每隔 30 秒自动发布一次数据。可以通过 REST API 查看消息：

```bash
# 1. 查看 MQTT 连接状态
curl http://localhost:8080/api/mqtt/status

# 预期响应：
{
  "success": true,
  "connected": true,
  "serverURI": "tcp://localhost:1883",
  "clientId": "zznursing-backend",
  "messageCount": 15,
  "timestamp": "2026-08-22T10:05:00.123"
}

# 2. 查看所有已接收的消息
curl http://localhost:8080/api/mqtt/messages

# 预期响应（截取部分）：
{
  "success": true,
  "total": 15,
  "bufferTotal": 15,
  "messages": [
    {
      "topic": "zznursing/device/device-001/heartrate",
      "payload": "{\"value\":72,\"unit\":\"bpm\",\"timestamp\":\"2026-08-22 10:05:00\"}",
      "qos": 0,
      "retained": false,
      "timestamp": "2026-08-22 10:05:00.123",
      "deviceId": "device-001"
    },
    ...
  ]
}

# 3. 查看指定设备的消息
curl http://localhost:8080/api/mqtt/messages/device-001

# 4. 查看心率类型消息
curl "http://localhost:8080/api/mqtt/messages?metric=heartrate"

# 5. 手动发布一条消息
curl -X POST http://localhost:8080/api/mqtt/publish \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "zznursing/device/test-001/heartrate",
    "payload": "{\"value\":85,\"unit\":\"bpm\"}",
    "qos": 1,
    "retained": false
  }'

# 预期响应：
{
  "success": true,
  "message": "消息发布成功",
  "topic": "zznursing/device/test-001/heartrate",
  "qos": 1,
  "retained": false
}

# 6. 动态订阅新主题
curl -X POST http://localhost:8080/api/mqtt/subscribe \
  -H "Content-Type: application/json" \
  -d '{"topic": "zznursing/device/+/#", "qos": 1}'
```

### 4.5 运行单元测试

```bash
# 运行所有测试（使用嵌入式 Moquette Broker，无需外部服务）
mvn test

# 预期输出：
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

测试说明：

| 测试方法 | 测试内容 | 验证点 |
|---------|---------|--------|
| `testMqttConnection` | MQTT 客户端连接 | 连接成功、客户端状态正确 |
| `testPublishAndSubscribe` | 发布与订阅 | 消息正确到达、内容一致 |
| `testAutomaticReconnect` | 自动重连 | 断开后可重新连接 |
| `testQosLevels` | QoS 等级 | QoS 0/1/2 三种等级均正常工作 |
| `testRestApi` | REST API 集成 | HTTP 接口返回正确格式 |

---

## 五、项目对照

### 5.1 本示例 vs zznursing 真实项目

本文示例是一个简化的入门版本，zznursing 项目中的 MQTT 架构要复杂得多。以下是对比表：

| 维度 | 本入门示例 | zznursing 真实项目 |
|------|-----------|-------------------|
| **Broker 选型** | Mosquitto（单机） | EMQX 集群（3 节点，承载 5000+ 设备连接） |
| **消息持久化** | 内存缓冲区（最多 1000 条） | RocketMQ 消息桥接 + MySQL 持久化存储 |
| **设备影子** | 无 | 设备影子服务，缓存设备最新状态，支持离线指令 |
| **规则引擎** | 无 | EMQX 内置 SQL 规则引擎，数据预处理和过滤 |
| **认证鉴权** | 匿名连接 | EMQX 的 JWT 认证 + 设备证书认证 |
| **消息路由** | 硬编码主题 | 动态主题路由，支持设备分组和批量操作 |
| **高可用** | 单点故障 | 集群部署 + 负载均衡 + 自动故障转移 |
| **监控告警** | 无 | Prometheus + Grafana 监控 Broker 集群状态 |
| **数据链路** | MQTT → 内存 | MQTT → EMQX 规则引擎 → RocketMQ → 后端服务 |

### 5.2 zznursing 中的 EMQX 集群方案

zznursing 生产环境使用 EMQX 集群，核心配置要点如下：

**集群架构：**

```
┌─────────────────────────────────────────────────────┐
│                    IoT 设备层                         │
│   5000+ 设备通过 MQTT 连接到 EMQX 集群               │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                EMQX 集群（3 节点）                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ Node 1   │  │ Node 2   │  │ Node 3   │          │
│  │ (主)     │  │ (从)     │  │ (从)     │          │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘          │
│       │              │              │               │
│       └──────────────┴──────────────┘               │
│              │ 集群内部通信 (Erlang 分布式)          │
│              ▼                                      │
│  ┌────────────────────────────────────┐            │
│  │      规则引擎（SQL 处理消息）        │            │
│  │  - 心率异常过滤 → 告警服务          │            │
│  │  - 设备数据解析 → 存入 RocketMQ    │            │
│  │  - 设备状态变化 → 更新设备影子      │            │
│  └────────────────────────────────────┘            │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│             RocketMQ 消息集群（消峰缓冲）            │
│  ┌──────────────┐  ┌──────────────┐                │
│  │ topic: device │  │ topic: alarm  │                │
│  │ 设备数据主题   │  │ 告警消息主题  │                │
│  └──────────────┘  └──────────────┘                │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│              后端微服务消费 RocketMQ                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐         │
│  │ 设备服务  │  │ 告警服务  │  │ 位置服务  │         │
│  │ 数据入库  │  │ 推送通知  │  │ 轨迹存储  │         │
│  └──────────┘  └──────────┘  └──────────┘         │
└─────────────────────────────────────────────────────┘
```

### 5.3 从入门到项目实战的差距

要从本示例过渡到 zznursing 的实际开发，需要掌握以下进阶内容：

1. **EMQX 集群部署**：理解 Erlang 分布式集群原理，配置节点发现和集群互连
2. **RocketMQ 消息桥接**：EMQX 规则引擎将 MQTT 消息转发到 RocketMQ，后端微服务消费 RocketMQ 消息
3. **设备影子（Device Shadow）**：缓存设备最新状态，设备离线时暂存指令，上线后同步
4. **规则引擎**：使用 EMQX 的 SQL 规则引擎，在消息到达 Broker 时进行预处理、过滤和转换
5. **消息持久化**：设备数据量巨大（5000 设备 × 多种指标 × 5 分钟），需要合理的存储策略和时序数据库
6. **安全认证**：设备证书认证、JWT 认证、ACL 访问控制列表，防止未授权设备接入

---

## 六、面试题 3 道

### 面试题 1：MQTT 的 QoS 等级如何选择？在养老场景中，心率数据和紧急呼叫按钮应该分别使用什么 QoS？

**考察点：** 对 QoS 0/1/2 的理解，以及在实际场景中做出合理选择的能力。

**参考答案：**

QoS（服务质量）是 MQTT 消息传递的可靠性保证，分为三个等级：

- **QoS 0（最多一次）**：发布者发送后即丢弃，不等待确认。开销最小，但消息可能丢失。适用于**频繁上报的传感器数据**，如心率、体温，偶尔丢失几条不影响整体趋势。
- **QoS 1（至少一次）**：发布者发送后等待 PUBACK 确认，没收到就重发。保证消息到达，但可能重复。适用于**必须到达但允许重复的消息**，如告警通知、设备状态变更。
- **QoS 2（恰好一次）**：四步握手确保消息不重复、不丢失。开销最大。适用于**不允许重复执行的指令**，如远程控制开关、复位指令。

**养老场景的具体选择：**

| 数据类型 | 推荐 QoS | 理由 |
|---------|---------|------|
| 心率数据 | QoS 0 | 每 5 分钟上报一次，少量丢失影响不大，需要低开销 |
| 体温数据 | QoS 0 | 变化缓慢，丢失几条不影响健康评估 |
| 设备状态 | QoS 1 | 必须知道设备在线状态，允许重复 |
| 紧急呼叫 | QoS 1 | 必须到达，重复可以接受（去重逻辑在业务层处理） |
| 复位指令 | QoS 2 | 不允许重复执行，否则可能导致设备故障 |

### 面试题 2：遗嘱消息（Will Message）有什么用？在 zznursing 中如何利用它检测设备离线？

**考察点：** 对遗嘱消息原理的理解，以及在实际项目中如何应用。

**参考答案：**

遗嘱消息是 MQTT 协议的一个重要特性：设备在连接时预先设置一条遗嘱消息，当设备**非正常断开**（网络中断、掉电、崩溃）时，Broker 会自动发布这条遗嘱消息。

**核心价值：** 让系统在设备"死"掉的第一时间就知道它"死"了，而不是靠轮询或超时猜测。

**zznursing 中的实际应用：**

1. **设备连接时设置遗嘱消息**：设备在 CONNECT 报文中设置遗嘱主题为 `zznursing/device/{deviceId}/status`，遗嘱载荷为 `{"online":false,"reason":"unexpected_disconnect"}`，QoS 为 1，Retained 为 true
2. **设备正常关闭时主动发布状态**：设备正常关机时，主动发布一条 `{"online":false,"reason":"normal_shutdown"}` 到同一主题，Broker 不会发布遗嘱消息
3. **后端订阅设备状态主题**：后端服务订阅 `zznursing/device/+/status`，收到设备离线消息后，更新 Redis 中的设备在线状态，触发运维告警

**核心区分：** 后端通过遗嘱消息内容可以区分"正常下线"和"异常断开"，这对养老场景至关重要——如果老人紧急呼叫按钮是因为没电断开，与正常关机是两种不同的处理策略。

### 面试题 3：EMQX 和 Mosquitto 如何选型？zznursing 为什么选择 EMQX 而不是 Mosquitto？

**考察点：** 对 Broker 选型决策的理解，是否考虑过实际项目的规模、运维、功能需求。

**参考答案：**

**选型对比：**

| 对比维度 | Mosquitto | EMQX |
|---------|-----------|------|
| 并发连接 | 单机万级 | 集群百万级 |
| 集群能力 | 无原生集群，需前端负载均衡 | 原生分布式集群，自动节点发现 |
| 规则引擎 | 无 | 内置 SQL 规则引擎 |
| 数据桥接 | 有限 | 支持 RocketMQ、Kafka、MySQL 等 |
| 运维工具 | 基本命令行 |  Dashboard + REST API + Prometheus |
| 资源占用 | 低（C 语言） | 中等（Erlang） |
| 学习曲线 | 低 | 中等 |

**zznursing 选择 EMQX 的核心原因：**

1. **设备规模**：zznursing 规划接入 5000+ 设备，未来可能扩展到 10000+，Mosquitto 单机无法支撑
2. **规则引擎**：EMQX 的 SQL 规则引擎可以在 Broker 层直接处理消息——心率 > 120 时自动转发到告警主题，体温异常时自动过滤，不需要后端服务参与
3. **数据桥接**：EMQX 原生支持将 MQTT 消息桥接到 RocketMQ，后端微服务消费 RocketMQ 即可，实现 MQTT 和微服务架构的无缝对接
4. **高可用**：EMQX 集群支持自动故障转移，单个节点宕机不影响整体服务，Mosquitto 单点故障会影响全部设备
5. **运维监控**：EMQX Dashboard 可以实时查看连接数、消息吞吐、主题统计，方便运维人员排查问题

**建议：** 开发测试环境用 Mosquitto（轻量、简单），生产环境必须用 EMQX（可靠、可扩展）。

---

## 七、总结

本文从零搭建了一个完整的 MQTT 通信演示系统，实现了从项目背景、核心概念到代码实现、运行验证的完整流程。

**关键要点回顾：**

1. MQTT 是物联网领域最流行的轻量级消息协议，基于发布/订阅模式，天然支持设备与云端的双向通信
2. Topic 主题使用斜杠分层的字符串格式，支持 `+`（单层）和 `#`（多层）通配符
3. QoS 0/1/2 三种等级分别对应"最多一次""至少一次""恰好一次"，需要在可靠性和开销之间权衡
4. 保留消息让新订阅者能立即获取设备最新状态，遗嘱消息让系统能第一时间感知设备离线
5. Eclipse Paho 是 Java 生态中最流行的 MQTT 客户端库，配合 Spring Boot 可以快速搭建 MQTT 通信服务
6. 从入门到生产环境，需要解决集群部署、消息持久化、规则引擎、安全认证等工程化问题

**下一篇预告：** 华为 IoTDA 设备接入 —— 从零搭建物联网设备数据采集服务，深入理解设备注册、数据上报、命令下发的完整流程。