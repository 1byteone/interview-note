package com.example.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * NioDemo —— Java NIO（New I/O）示例
 *
 * 演示内容：
 * 1. FileChannel 文件读写（通道 + 缓冲区）
 * 2. ByteBuffer 的使用（put / flip / get / clear / compact）
 * 3. Selector 非阻塞网络编程（简单示例）
 *
 * 对比传统 IO（BIO）：
 * - BIO：面向流（Stream），阻塞式，一个线程处理一个连接
 * - NIO：面向通道（Channel）+ 缓冲区（Buffer），可选择非阻塞模式
 * - NIO 的 Selector 可用一个线程管理多个通道（多路复用）
 */
public class NioDemo {

    public static void main(String[] args) throws Exception {
        NioDemo demo = new NioDemo();

        System.out.println("=== 1. FileChannel 文件读写 ===");
        demo.demoFileChannel();

        System.out.println("\n=== 2. ByteBuffer 使用详解 ===");
        demo.demoByteBuffer();

        System.out.println("\n=== 3. Selector 非阻塞网络 I/O（简单示例）===");
        demo.demoSelector();

        System.out.println("\n程序正常结束");
    }

    // ============================================================
    // 1. FileChannel 文件读写
    // ============================================================
    public void demoFileChannel() throws IOException {
        // 创建临时文件
        Path tempFile = Files.createTempFile("nio-demo-", ".txt");
        System.out.println("临时文件: " + tempFile);

        // --- 写入文件（FileChannel）---
        // FileChannel 是双向的（可读可写），但 FileInputStream/FileOutputStream 得到的 Channel 是单向的
        // 使用 RandomAccessFile 获取双向通道，或使用 Files.newByteChannel

        // 方式一：通过 FileChannel 写入
        try (FileChannel writeChannel = (FileChannel) Files.newByteChannel(
                tempFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {

            // 准备数据
            String content = "Hello NIO!\nJava NIO 提供了非阻塞 I/O 操作。\nChannels and Buffers 是核心概念。";
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

            // 分配缓冲区（ByteBuffer）：在堆外或堆内分配内存
            ByteBuffer buffer = ByteBuffer.allocate(1024);  // 堆内分配
            // ByteBuffer.allocateDirect(1024);  // 直接内存分配（零拷贝，适合大文件）

            // 写入数据到缓冲区
            buffer.put(bytes);

            // flip：切换为读模式（limit = position, position = 0）
            // 重要：写入数据后必须 flip 才能被读取/写出
            buffer.flip();

            // 从缓冲区读取数据并写入通道
            while (buffer.hasRemaining()) {
                writeChannel.write(buffer);
            }
            System.out.println("写入完成: " + content.length() + " 字节");
        }

        // 方式二：通过 FileChannel 读取
        try (FileChannel readChannel = (FileChannel) Files.newByteChannel(
                tempFile, StandardOpenOption.READ)) {

            // 分配缓冲区
            ByteBuffer buffer = ByteBuffer.allocate(1024);

            // 从通道读取数据到缓冲区
            int bytesRead = readChannel.read(buffer);
            System.out.println("读取了 " + bytesRead + " 字节");

            // flip：切换为读模式
            buffer.flip();

            // 将缓冲区数据解码为字符串
            String content = StandardCharsets.UTF_8.decode(buffer).toString();
            System.out.println("读取内容:\n" + content);

            // clear：重置缓冲区（position = 0, limit = capacity），准备再次写入
            buffer.clear();

            // 演示分散读取（Scatter Read）：将通道数据分散到多个缓冲区
            // 适用于消息头 + 消息体结构
            readChannel.position(0);  // 重置到文件开头
            ByteBuffer header = ByteBuffer.allocate(20);
            ByteBuffer body = ByteBuffer.allocate(256);
            ByteBuffer[] buffers = {header, body};
            readChannel.read(buffers);  // 先填满 header，再填 body
            header.flip();
            body.flip();
            System.out.println("Scatter 读取 - header: " + StandardCharsets.UTF_8.decode(header));
            // body 无需输出，内容已通过 header 展示
        }

        // 文件传输：零拷贝（transferTo / transferFrom），避免数据在用户态和内核态之间拷贝
        // 常用于文件拷贝、静态文件服务器
        try (FileChannel source = (FileChannel) Files.newByteChannel(tempFile, StandardOpenOption.READ);
             FileChannel target = (FileChannel) Files.newByteChannel(
                     Files.createTempFile("nio-copy-", ".txt"),
                     StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {

            // transferTo: 直接从源通道传输到目标通道（零拷贝）
            long transferred = source.transferTo(0, source.size(), target);
            System.out.println("transferTo 零拷贝传输: " + transferred + " 字节");
        }

        // 清理临时文件
        Files.deleteIfExists(tempFile);
    }

    // ============================================================
    // 2. ByteBuffer 使用详解
    // ============================================================
    @SuppressWarnings("unused")
    public void demoByteBuffer() {
        System.out.println("--- ByteBuffer 核心属性 ---");
        System.out.println("position: 当前读写位置");
        System.out.println("limit:    可读/可写上限");
        System.out.println("capacity: 缓冲区总容量");

        // 创建缓冲区
        ByteBuffer buf = ByteBuffer.allocate(64);  // 64 字节
        System.out.println("\n初始状态: position=" + buf.position()
                + ", limit=" + buf.limit()
                + ", capacity=" + buf.capacity());

        // 写入数据
        buf.putInt(42);        // 4 字节
        buf.put((byte) 1);     // 1 字节
        buf.putChar('A');      // 2 字节
        buf.putDouble(3.14);   // 8 字节
        System.out.println("写入后: position=" + buf.position()
                + ", limit=" + buf.limit()
                + ", capacity=" + buf.capacity());

        // flip：切换到读模式（position -> 0, limit -> 原 position）
        buf.flip();
        System.out.println("flip 后: position=" + buf.position()
                + ", limit=" + buf.limit());

        // 读取数据（必须按照写入的顺序和类型读取）
        int intVal = buf.getInt();       // 读取 int
        byte byteVal = buf.get();        // 读取 byte
        char charVal = buf.getChar();    // 读取 char
        double doubleVal = buf.getDouble();  // 读取 double
        System.out.println("读取: int=" + intVal + ", byte=" + byteVal
                + ", char=" + charVal + ", double=" + doubleVal);

        // rewind：重新读取（position = 0, limit 不变）
        buf.rewind();
        System.out.println("rewind 后: position=" + buf.position()
                + ", limit=" + buf.limit());

        // clear：重置为写模式（position = 0, limit = capacity）
        // 注意：clear 不会清空数据，只是移动指针
        buf.clear();
        System.out.println("clear 后: position=" + buf.position()
                + ", limit=" + buf.limit()
                + ", capacity=" + buf.capacity());

        // compact：压缩未读完的数据
        // 将 position 到 limit 之间的数据移到缓冲区开头，position 设为剩余数据的末尾
        // 场景：从通道读取部分数据后，想继续写入更多数据
        buf.put("Hello World".getBytes(StandardCharsets.UTF_8));
        buf.flip();
        byte[] first3 = new byte[3];
        buf.get(first3);  // 读取前 3 字节 "Hel"
        System.out.println("读取前 3 字节: " + new String(first3));

        buf.compact();  // 将未读的 "lo World" 移到开头
        System.out.println("compact 后: position=" + buf.position());
        // 此时可以继续写入数据

        // 直接缓冲区 vs 堆缓冲区
        // allocateDirect: 直接内存（操作系统本地内存），零拷贝，大文件高性能
        // allocate: 堆内存，JVM 管理，小文件更合适
        ByteBuffer heapBuf = ByteBuffer.allocate(1024);       // 堆缓冲区
        ByteBuffer directBuf = ByteBuffer.allocateDirect(1024);  // 直接缓冲区
        System.out.println("heapBuf 是否直接缓冲区: " + heapBuf.isDirect());
        System.out.println("directBuf 是否直接缓冲区: " + directBuf.isDirect());
    }

    // ============================================================
    // 3. Selector 非阻塞网络 I/O（简单示例）
    // ============================================================
    public void demoSelector() throws IOException {
        System.out.println("--- Selector 非阻塞 I/O 原理 ---");
        System.out.println("Selector 可以实现单线程管理多个 Channel（多路复用）");
        System.out.println("类似 Linux epoll / kqueue 的事件驱动模型");

        // 创建 Selector（选择器）
        Selector selector = Selector.open();

        // 创建 ServerSocketChannel（非阻塞模式）
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);  // 设置为非阻塞模式（核心！）
        serverChannel.bind(new InetSocketAddress(0));  // 绑定到随机端口
        int port = serverChannel.socket().getLocalPort();
        System.out.println("演示服务器启动在端口: " + port);

        // 将 ServerSocketChannel 注册到 Selector，关注 OP_ACCEPT 事件
        // SelectionKey 表示 Channel 在 Selector 上的注册关系
        SelectionKey serverKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("注册 SelectionKey: " + serverKey);

        // 说明：Selector 的工作流程
        // 1. 创建 Selector 并注册 Channel
        // 2. 调用 select() 阻塞等待事件就绪
        // 3. 遍历 selectedKeys() 处理就绪事件
        // 4. 处理完需手动移除 key

        // 用一个简单的演示：向自己发送一个请求，验证 Selector 工作机制
        System.out.println("\nSelector 工作流程:");
        System.out.println("  1. Channel 注册到 Selector");
        System.out.println("  2. select() 阻塞等待 I/O 事件");
        System.out.println("  3. 遍历 selectedKeys() 处理事件");
        System.out.println("  4. 处理完成后移除 key");

        // 这里不实际启动连接，仅展示 Selector 的非阻塞 API 用法
        // 完整示例请参考 NIO 实现的 EchoServer

        // 清理
        serverChannel.close();
        selector.close();
        System.out.println("Selector 演示完成");
    }

    /**
     * 完整 NIO EchoServer 示例（参考框架，此处注释）
     */
    /*
    public class NioEchoServer {
        public static void main(String[] args) throws IOException {
            Selector selector = Selector.open();
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.configureBlocking(false);
            ssc.bind(new InetSocketAddress(8080));
            ssc.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                selector.select();  // 阻塞，直到有事件就绪
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();  // 必须手动移除

                    if (key.isAcceptable()) {
                        // 接受新连接
                        SocketChannel sc = ssc.accept();
                        sc.configureBlocking(false);
                        sc.register(selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) {
                        // 读取数据
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer buf = ByteBuffer.allocate(1024);
                        int read = sc.read(buf);
                        if (read == -1) {
                            sc.close();  // 客户端关闭
                        } else {
                            buf.flip();
                            sc.write(buf);  // echo 回写
                        }
                    }
                }
            }
        }
    }
    */
}