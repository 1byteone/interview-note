# IO / NIO — BIO /* NIO /* AIO · Netty 入门

> 等级：👶 新手入门 → 🎯 面试进阶
> 目标：理解三种 IO 模型，掌握 NIO 三大核心组件，入门 Netty 框架。

---

## 一、BIO / NIO / AIO 对比

### 1.1 概念解释

| 模型 | 全称 | 特点 | 典型代表 |
|------|------|------|---------|
| **BIO** | Blocking IO | 阻塞 IO，一个线程处理一个连接 | `ServerSocket` |
| **NIO** | Non-blocking IO | 同步非阻塞 IO，多路复用 | `Selector + Channel + Buffer` |
| **AIO** | Asynchronous IO | 异步非阻塞 IO，回调通知 | `AsynchronousServerSocketChannel` |

### 1.2 核心区别

```
BIO： 线程 → 阻塞等待 read → 数据就绪 → 读取
NIO： 线程 → Selector.select() → 就绪 Channel → 非阻塞 read
AIO： 线程 → 发起 read → 立即返回 → 数据就绪时回调
```

### 1.3 适用场景

| 模型 | 并发数 | 延迟 | 编码复杂度 |
|------|--------|------|-----------|
| BIO | 低（< 1000） | 高 | 低 |
| NIO | 高（数万） | 中 | 高 |
| AIO | 极高 | 低 | 高 |

> 实际应用中，NIO（多路复用）是主流，AIO 在 Windows 上表现好（IOCP），Linux 上 AIO 内核实现不成熟，因此 Netty 基于 NIO 而非 AIO。

---

## 二、NIO 三大核心

### 2.1 Channel（通道）

- 双向的，可读可写
- 常见实现：`FileChannel`、`SocketChannel`、`ServerSocketChannel`、`DatagramChannel`

```java
// 读取文件
try (FileChannel channel = FileChannel.open(Paths.get("data.txt"), StandardOpenOption.READ)) {
    ByteBuffer buf = ByteBuffer.allocate(1024);
    channel.read(buf);          // 读入 Buffer
    buf.flip();                 // 切换读模式
    while (buf.hasRemaining()) {
        System.out.print((char) buf.get());
    }
}
```

### 2.2 Buffer（缓冲区）

- NIO 所有数据读写都通过 Buffer 进行
- 核心属性：`capacity`（容量）、`position`（当前位置）、`limit`（界限）、`mark`（标记）

```
写模式： position → limit = capacity
         [写入数据...]
         position 随写入增长

flip() 切换读模式：
         position = 0, limit = 旧 position
         [读取数据...]
         position 随读取增长

clear() 切换写模式：
         position = 0, limit = capacity
```

### 2.3 Selector（选择器）

- 单线程管理多个 Channel 的事件（连接、读、写）
- 基于操作系统的**多路复用**（epoll / kqueue / IOCP）

```java
// 服务端 NIO 多路复用示例
Selector selector = Selector.open();
ServerSocketChannel ssc = ServerSocketChannel.open();
ssc.configureBlocking(false);                         // 非阻塞模式
ssc.bind(new InetSocketAddress(8080));
ssc.register(selector, SelectionKey.OP_ACCEPT);       // 注册 accept 事件

while (true) {
    selector.select();                                 // 阻塞，直到有事件就绪
    Set<SelectionKey> keys = selector.selectedKeys();  // 获取就绪事件
    Iterator<SelectionKey> it = keys.iterator();
    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove();
        if (key.isAcceptable()) {     // 新连接
            SocketChannel sc = ssc.accept();
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_READ);
        } else if (key.isReadable()) { // 可读
            // 读取数据...
        }
    }
}
```

### 2.4 零拷贝（Zero-Copy）

传统 IO 在用户态和内核态之间多次拷贝数据，零拷贝通过 `transferTo` / `sendfile` 减少拷贝次数：

```java
// 传统 IO：4 次拷贝 + 4 次上下文切换
// 零拷贝：2 次拷贝 + 2 次上下文切换
FileChannel channel = FileChannel.open(Paths.get("bigfile.zip"), StandardOpenOption.READ);
channel.transferTo(0, channel.size(), socketChannel);  // 直接到网卡
```

---

## 三、Netty 入门

### 3.1 为什么用 Netty？

- JDK NIO 的 API 过于底层，编码复杂，易出 bug（拆包粘包、空轮询 bug）
- Netty 封装了 NIO，提供：
  - **Reactor 线程模型**
  - **零拷贝**（CompositeByteBuf 避免内存拷贝）
  - **编解码器**（编解码 HTTP、Protobuf 等）
  - **内存池**（PooledByteBufAllocator）

### 3.2 Netty 线程模型

```
┌──────────────────────────────────────────────┐
│  Boss Group (1 个线程，处理 accept)           │
│  └── Acceptor → Channel → Worker Group       │
│                                               │
│  Worker Group (N 个线程，处理 IO 读写)         │
│  ├── Worker-1 → Channel → ChannelPipeline    │
│  ├── Worker-2 → Channel → ChannelPipeline    │
│  └── Worker-N → Channel → ChannelPipeline    │
└──────────────────────────────────────────────┘
```

### 3.3 快速入门

```java
// 服务端
EventLoopGroup boss = new NioEventLoopGroup(1);
EventLoopGroup worker = new NioEventLoopGroup();

try {
    ServerBootstrap b = new ServerBootstrap();
    b.group(boss, worker)
     .channel(NioServerSocketChannel.class)
     .childHandler(new ChannelInitializer<SocketChannel>() {
         @Override
         protected void initChannel(SocketChannel ch) {
             ch.pipeline().addLast(new StringDecoder());
             ch.pipeline().addLast(new StringEncoder());
             ch.pipeline().addLast(new ServerHandler());
         }
     })
     .option(ChannelOption.SO_BACKLOG, 128)
     .childOption(ChannelOption.SO_KEEPALIVE, true);

    ChannelFuture f = b.bind(8080).sync();
    f.channel().closeFuture().sync();
} finally {
    worker.shutdownGracefully();
    boss.shutdownGracefully();
}
```

### 3.4 核心组件

| 组件 | 含义 |
|------|------|
| `EventLoopGroup` | Reactor 线程组，管理 EventLoop |
| `EventLoop` | 单线程事件循环，处理注册到它的所有 Channel |
| `Channel` | 通信通道的抽象 |
| `ChannelPipeline` | 责任链，处理入站/出站事件 |
| `ChannelHandler` | 处理器，业务逻辑所在 |
| `ByteBuf` | Netty 的缓冲区，支持零拷贝、池化、引用计数 |

---

## 四、面试高频题

| 题目 | 核心要点 |
|------|---------|
| BIO、NIO、AIO 区别？ | 阻塞 vs 非阻塞、同步 vs 异步、多路复用 |
| NIO 为什么比 BIO 好？ | 单线程可管理大量连接，减少线程上下文切换 |
| select、poll、epoll 区别？ | select 有 1024 限制，poll 无限制但线性扫描，epoll 事件驱动 O(1) |
| 什么是零拷贝？ | 减少用户态/内核态数据拷贝，`transferTo` 直接到网卡/磁盘 |
| Netty 为什么快？ | Reactor 模型、零拷贝、内存池、无锁串行化 |
| 什么是拆包粘包？ | TCP 流式协议，消息边界不固定；通过定长/分隔符/LengthField 解决 |

> 进阶篇：Java 8-21 新特性，Record、Sealed Class、Virtual Threads。