# GraalVM Native Image — 原生编译 · AOT · 毫秒级启动

> 等级：🎯 面试进阶
> 目标：理解 Spring Boot 3 + GraalVM Native Image 的 AOT 编译原理，以及它带来的性能收益和适用场景。

---

## 一、什么是 GraalVM Native Image

### 1.1 传统 JVM 启动 vs Native Image

```
传统 JVM 启动：
源码 → javac → .class 字节码 → JIT 编译 → 机器码
                                   ↓
                          运行慢（需预热，解释执行 + JIT 编译）
                          内存占用大（JVM 本身 + 字节码 + JIT 缓存）

Native Image 启动：
源码 → javac → .class 字节码 → AOT 编译 → 原生可执行文件
                                   ↓
                          启动即顶峰（已编译为机器码）
                          内存占用小（无 JVM 堆外开销）
```

| 对比项 | 传统 JVM | Native Image |
|--------|----------|-------------|
| 启动时间 | 3-10 秒 | 10-100 毫秒 |
| 内存占用 | 200-500MB | 30-80MB |
| 峰值性能 | 需预热（JIT） | 启动即峰值 |
| 打包大小 | 20-50MB | 50-150MB（含静态链接库） |

### 1.2 核心原理

AOT（Ahead-of-Time）编译：**在编译期（构建时）** 将字节码编译为机器码，而不是运行时通过 JIT 编译。

```java
// 传统流程：JIT 在运行时将热点代码编译为机器码
// AOT 流程：构建时将所有代码编译为机器码，运行时直接执行机器码
```

闭世界分析（Closed World Analysis）：GraalVM 在 AOT 编译时假设**所有代码在编译时已知**，这要求：

- 反射、动态代理、资源加载必须提前注册
- 无动态类加载（无法加载 classpath 不存在的类）
- 无 JNI 动态链接

---

## 二、Spring Boot 3 的 AOT 引擎

### 2.1 AOT 处理流程

```
Spring Boot 应用 → AOT 引擎 → 生成 AOT 处理过的源码
                                     ↓
                        编译为 Native Image → 原生可执行文件
```

Spring Boot 3 的 AOT 引擎在构建时做以下事情：

1. **分析 Bean 定义**：确定哪些 Bean 会被创建
2. **分析条件注解**：提前计算 @Conditional 的结果
3. **生成反射配置**：自动发现哪些类需要反射（如 @RestController、@Service）
4. **生成代理配置**：自动发现哪些类需要动态代理
5. **生成资源配置**：自动发现 classpath 下的资源文件
6. **生成序列化配置**：自动发现需要序列化的类

### 2.2 构建配置

```xml
<!-- pom.xml -->
<build>
    <plugins>
        <plugin>
            <groupId>org.graalvm.buildtools</groupId>
            <artifactId>native-maven-plugin</artifactId>
            <version>0.10.2</version>
        </plugin>
    </plugins>
</build>
```

```yaml
# 引入 GraalVM 原生支持
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.2</version>
</dependency>
```

构建命令：

```bash
# 安装 GraalVM 并设置 JAVA_HOME
# 使用 GraalVM 的 native-image 工具
mvn -Pnative native:compile

# 直接运行原生可执行文件（无需 JDK）
./target/mall-order-service
```

### 2.3 运行时配置

```java
// 当某些类需要反射但无法被自动发现时，手动注册
@RegisterReflectionForBinding({OrderRequest.class, OrderResponse.class})
@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

---

## 三、性能对比实战

### 3.1 启动时间对比

```bash
# 传统 JVM 启动
time java -jar mall-order-service.jar
# 启动时间: 5.2s

# Native Image 启动
time ./target/mall-order-service
# 启动时间: 0.08s (80ms)
```

### 3.2 内存占用对比

```bash
# JVM 启动后
ps aux | grep mall-order-service
# RSS: 350MB

# Native Image 启动后
# RSS: 45MB
```

### 3.3 吞吐量对比

```bash
# 压测：wrk -t4 -c100 -d30s http://localhost:8080/api/products
# JVM（预热后）：12,000 req/s
# Native Image：10,500 req/s（略低，约 10-15%）
```

> **关键发现**：Native Image 的峰值性能略低于 JIT 优化后的 JVM（约 10-15%），但启动时间快 50-100 倍，内存占用低 5-8 倍。

---

## 四、适用场景与限制

### 4.1 适用场景

| 场景 | 推荐 | 原因 |
|------|------|------|
| Serverless 函数 | 强烈推荐 | 毫秒级启动，冷启动无压力 |
| 边缘设备/IoT | 强烈推荐 | 低内存占用，无 JVM 依赖 |
| 微服务容器 | 推荐 | 快速启动、缩容，适合 K8s |
| 批处理作业 | 推荐 | 用完即弃，无需预热 |
| 长时间运行服务 | 不推荐 | 传统 JVM 长期运行性能更优 |
| 高并发 API | 可选 | 需权衡启动速度和峰值性能 |

### 4.2 已知限制

| 限制 | 说明 | 解决方案 |
|------|------|---------|
| 反射需提前注册 | 运行时反射调用需提前告知 | @RegisterReflectionForBinding |
| 动态代理需提前注册 | 不能动态创建代理类 | AOT 引擎自动处理大部分 |
| 序列化需提前注册 | 需提前知道序列化类型 | @RegisterForReflection |
| 无动态类加载 | 不能加载 classpath 外类 | 编译时确定所有依赖 |
| 启动慢的构建 | 构建需要 3-5 分钟 | 增量构建（实验性） |
| 三方库兼容性 | 某些库不支持 Native | 检查 [Spring 官方兼容列表](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0.0-RC2-Release-Notes) |

---

## 五、容器化部署

```dockerfile
# Dockerfile — 多阶段构建
# 第一阶段：构建原生镜像
FROM ghcr.io/graalvm/native-image:17 AS builder
WORKDIR /build
COPY . .
RUN mvn -Pnative native:compile -DskipTests

# 第二阶段：最小运行时镜像
FROM alpine:3.19
RUN apk add --no-cache libc6-compat
COPY --from=builder /build/target/mall-order-service /app/service
EXPOSE 8080
CMD ["/app/service"]
```

生成的 Docker 镜像大小约 80MB（传统 JVM 镜像约 300MB）。

---

## 六、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| AOT 和 JIT 区别？ | AOT 编译时编译，启动快但优化少；JIT 运行时编译热点代码，长期运行性能更好 |
| Spring Boot 3 的 AOT 引擎做什么？ | 构建时分析 Bean 定义、条件注解、反射/代理/资源，生成注册配置 |
| Native Image 的"闭世界分析"指什么？ | 假设所有代码在编译时已知，不能动态加载类 |
| Native Image 最适合什么场景？ | Serverless 函数、边缘设备、K8s 微服务（冷启动敏感） |
| Native Image 的性能劣势？ | 峰值性能比 JIT 慢 10-15%，构建时间长（3-5 分钟），三方库兼容性有限 |
| 反射在 Native Image 中怎么处理？ | @RegisterReflectionForBinding 或 AOT 引擎自动生成配置 |

> 了解了新编译技术，下一节：Spring Boot 3.x 的新特性全景。