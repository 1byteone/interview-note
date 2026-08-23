# 快速入门 — 环境搭建 · 第一个 API · 配置管理

> 等级：👶 新手通道
> 目标：从零搭建 Spring Boot 项目，编写第一个 REST API，掌握配置文件管理。

---

## 一、环境搭建

### 1.1 前置条件

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | Spring Boot 3.x 要求 JDK 17，推荐 JDK 21 |
| Maven | 3.6+ | 项目构建工具，也可用 Gradle |
| IDE | IntelliJ IDEA | 推荐 Ultimate 版（社区版也够用） |

### 1.2 创建项目方式

**方式一：Spring Initializr（推荐）**

访问 https://start.spring.io/，选择：

- Project: Maven
- Language: Java
- Spring Boot: 3.3.x
- Dependencies: Spring Web, Spring Boot DevTools, Lombok

点击 Generate 下载 zip，解压后用 IDEA 打开。

**方式二：IDEA 内置**

File → New → Project → Spring Initializr，选择依赖后直接创建。

**方式三：Maven 手动创建**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.2</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>quick-start</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
    </dependencies>

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

### 1.3 项目结构

```
quick-start/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/quickstart/
    │   │   ├── QuickStartApplication.java      ← 启动类
    │   │   ├── controller/
    │   │   │   └── HelloController.java
    │   │   └── model/
    │   │       └── Product.java
    │   └── resources/
    │       ├── application.yml                  ← 主配置
    │       ├── application-dev.yml              ← 开发环境
    │       └── application-prod.yml             ← 生产环境
    └── test/
        └── java/com/example/quickstart/
            └── QuickStartApplicationTests.java
```

---

## 二、第一个 REST API

### 2.1 启动类

```java
package com.example.quickstart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QuickStartApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuickStartApplication.class, args);
    }
}
```

### 2.2 Hello Controller

```java
package com.example.quickstart.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello, Spring Boot!";
    }
}
```

### 2.3 启动与验证

```bash
# 方式一：IDE 中直接运行 main 方法
# 方式二：Maven 命令行
mvn spring-boot:run

# 方式三：打包后运行
mvn clean package -DskipTests
java -jar target/quick-start-1.0.0.jar
```

启动后访问：`http://localhost:8080/api/hello`，返回 `Hello, Spring Boot!`

---

## 三、配置文件管理

### 3.1 application.yml 基础

```yaml
server:
  port: 8080

spring:
  application:
    name: quick-start

# 自定义配置
app:
  name: 快速入门示例
  version: 1.0.0
```

### 3.2 多环境配置

```yaml
# application-dev.yml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:h2:mem:testdb
    username: sa
    password:

logging:
  level:
    com.example: DEBUG
```

```yaml
# application-prod.yml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://prod-db:3306/mall?useSSL=false
    username: prod_user
    password: ${DB_PASSWORD}

logging:
  level:
    com.example: WARN
```

### 3.3 激活环境

```yaml
# application.yml
spring:
  profiles:
    active: dev  # 激活开发环境
```

也可以通过命令行指定：

```bash
java -jar quick-start.jar --spring.profiles.active=prod
```

### 3.4 配置绑定 @ConfigurationProperties

```java
package com.example.quickstart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String name;
    private String version;

    // getter / setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
```

### 3.5 配置优先级

Spring Boot 配置加载优先级（从高到低）：

1. 命令行参数（`--server.port=9090`）
2. JNDI 属性
3. Java 系统属性（`-D` 参数）
4. 操作系统环境变量
5. `application-{profile}.yml`（特定环境）
6. `application.yml`（通用配置）
7. `@PropertySource` 注解

---

## 四、热部署（DevTools）

### 4.1 引入依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

### 4.2 触发方式

- **代码修改**：保存后自动重启（Restart 类加载器重新加载）
- **模板文件**：修改后立即生效（LiveReload）
- 不会重启静态资源和视图模板

### 4.3 原理

DevTools 使用**双类加载器机制**：

- `Base ClassLoader`：加载第三方 jar（不常变）
- `Restart ClassLoader`：加载项目代码（频繁变）

修改代码时只重启 Restart ClassLoader，速度远快于冷启动。

---

## 五、最小案例：商品 CRUD API

### 5.1 数据模型

```java
package com.example.quickstart.model;

public class Product {
    private Long id;
    private String name;
    private Double price;
    private Integer stock;

    // 构造函数、getter/setter
    public Product() {}
    public Product(Long id, String name, Double price, Integer stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}
```

### 5.2 Controller

```java
package com.example.quickstart.controller;

import com.example.quickstart.model.Product;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    // 创建商品
    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product) {
        Long id = idGenerator.getAndIncrement();
        product.setId(id);
        products.put(id, product);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    // 查询所有商品
    @GetMapping
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(new ArrayList<>(products.values()));
    }

    // 查询单个商品
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        Product product = products.get(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }

    // 更新商品
    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        Product old = products.replace(id, product);
        if (old == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }

    // 删除商品
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Product removed = products.remove(id);
        if (removed == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
```

### 5.3 测试 API

```bash
# 创建商品
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Spring Boot 实战","price":59.9,"stock":100}'

# 查询所有
curl http://localhost:8080/api/products

# 查询单个
curl http://localhost:8080/api/products/1

# 更新
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Spring Boot 实战（第2版）","price":69.9,"stock":80}'

# 删除
curl -X DELETE http://localhost:8080/api/products/1
```

---

## 六、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| @SpringBootApplication 是什么？ | 组合注解：@Configuration + @EnableAutoConfiguration + @ComponentScan |
| spring-boot-starter-parent 有什么用？ | 统一管理依赖版本，避免版本冲突 |
| application.yml 和 application.properties 区别？ | YAML 结构化更强，支持多文档块；properties 扁平 |
| 如何切换开发/生产环境？ | spring.profiles.active=dev 或命令行 --spring.profiles.active=prod |
| DevTools 重启和冷启动有什么区别？ | DevTools 用双类加载器，只重启项目代码，快 5-10 倍 |
| 配置优先级是怎样的？ | 命令行 > 环境变量 > profile 配置 > 通用配置 > 默认值 |

> 掌握了快速入门，下一节深入理解 Spring IoC 容器和 DI 依赖注入的核心机制。