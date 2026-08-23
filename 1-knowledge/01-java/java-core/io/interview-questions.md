# Java IO / NIO 面试题大全

## 📚 知识体系

```
Java IO 体系
├── 字节流
│   ├── InputStream / OutputStream
│   ├── FileInputStream / FileOutputStream
│   ├── BufferedInputStream / BufferedOutputStream
│   └── DataInputStream / DataOutputStream
├── 字符流
│   ├── Reader / Writer
│   ├── FileReader / FileWriter
│   ├── BufferedReader / BufferedWriter
│   └── InputStreamReader / OutputStreamWriter
├── 序列化
│   ├── Serializable / Externalizable
│   ├── transient 关键字
│   └── serialVersionUID
└── 装饰器模式

Java NIO
├── Buffer（缓冲区）
├── Channel（通道）
├── Selector（选择器）
├── 非阻塞 IO
├── 零拷贝（FileChannel.transferTo）
├── Path / Files
├── 内存映射文件（MappedByteBuffer）
└── AIO（异步 IO）

网络编程
├── BIO（阻塞 IO）
├── NIO（非阻塞 IO）
├── AIO（异步 IO）
├── Netty 框架
└── Reactor 模式
```

---

## 🎯 Level 1：基础题

### 1. 字节流和字符流的区别？
**答案**：

| 对比 | 字节流 | 字符流 |
|------|--------|--------|
| 单位 | byte（8位） | char（16位） |
| 处理 | 所有文件（图片/视频/文本） | 文本文件 |
| 基类 | InputStream / OutputStream | Reader / Writer |
| 缓冲区 | 无（不处理编码） | 有（编码/解码） |
| 适用 | 二进制文件 | 文本文件 |

**转换**：`InputStreamReader / OutputStreamWriter` 是字节流到字符流的桥梁。

### 2. BIO、NIO、AIO 的区别？
**答案**：

| 特性 | BIO | NIO | AIO |
|------|-----|-----|-----|
| 模型 | 同步阻塞 | 同步非阻塞 | 异步非阻塞 |
| 线程 | 1 请求 1 线程 | 1 线程多请求（Selector） | 回调通知 |
| 并发 | 低（线程多） | 高（复用） | 极高 |
| 复杂度 | 简单 | 中等 | 复杂 |
| 适用 | 低并发 | 高并发 | 极高并发 |

---

## 🎯 Level 2：进阶题

### 3. NIO 三大核心组件是什么？
**答案**：

**Buffer（缓冲区）**：
- 读写数据的容器（ByteBuffer / CharBuffer / IntBuffer 等）
- 核心属性：capacity（容量）、position（位置）、limit（上限）、mark（标记）

**Channel（通道）**：
- 双向传输（读/写共用一个通道）
- 实现：FileChannel / SocketChannel / ServerSocketChannel / DatagramChannel

**Selector（选择器）**：
- 单线程监听多个 Channel 的事件
- 事件类型：OP_ACCEPT / OP_CONNECT / OP_READ / OP_WRITE

**NIO 工作流程**：
```text
Selector.select()  // 阻塞，直到有事件就绪
    ↓
Set<SelectionKey>  // 获取就绪事件集合
    ↓
遍历 SelectionKey
    ├── OP_ACCEPT → 建立连接
    ├── OP_READ   → 读取数据
    └── OP_WRITE  → 写入数据
```

### 4. 什么是零拷贝？Java 如何实现？
**答案**：
零拷贝是指数据在**用户态和内核态之间不经过多次拷贝**，直接从内核态传输到目标位置。

**传统 IO 四次拷贝**：
```
磁盘 → 内核缓冲区 → 用户缓冲区 → Socket 缓冲区 → 网卡
```

**零拷贝（FileChannel.transferTo）**：
```
磁盘 → 内核缓冲区 → 网卡（2 次拷贝，DMA 传输）
```

**Java 实现**：
```java
FileChannel in = new FileInputStream("source.txt").getChannel();
FileChannel out = new FileOutputStream("dest.txt").getChannel();
// 零拷贝传输
in.transferTo(0, in.size(), out);
```

---

## 🎯 Level 3：高级题

### 5. Netty 的核心组件？
**答案**：

| 组件 | 作用 |
|------|------|
| **Channel** | 网络连接通道 |
| **EventLoop** | 处理 I/O 事件的循环（1 线程绑定多个 Channel） |
| **EventLoopGroup** | EventLoop 线程池（Boss + Worker） |
| **ChannelPipeline** | 责任链，处理入站/出站事件 |
| **ChannelHandler** | 业务处理器（编解码/业务逻辑） |
| **ByteBuf** | 字节缓冲区（比 ByteBuffer 更灵活） |
| **Bootstrap / ServerBootstrap** | 客户端/服务端启动器 |

**Reactor 模式**：
```text
Boss Group（1 线程）
    ↓ accept
Channel 注册到 Worker
    ↓
Worker Group（N 线程）
    ↓ read/write
ChannelPipeline → Handler 链
```

---

## 📖 学习资源

### 推荐项目
- [Netty 官方文档](https://netty.io/)
- [Netty in Action](https://book.douban.com/subject/24786334/)

### 最佳实践
1. 高并发网络编程优先使用 Netty（非裸 NIO）
2. 文件传输使用零拷贝（transferTo/transferFrom）
3. 选择合适缓冲区大小（1024~8192）
4. 避免在 IO 线程做耗时业务操作