# Java 核心基础 (Core) 面试题大全

## 📚 知识体系

```
Java 基础
├── 数据类型
│   ├── 基本类型（int/long/double/boolean...）
│   ├── 引用类型
│   ├── 自动装箱/拆箱
│   └── 缓存池（IntegerCache）
├── 面向对象
│   ├── 封装/继承/多态
│   ├── 抽象类 vs 接口
│   ├── equals/hashCode
│   └── 深拷贝/浅拷贝
├── 语法基础
│   ├── final/finally/finalize
│   ├── static
│   ├── String（不可变）
│   ├── StringBuilder/StringBuffer
│   ├── 异常体系
│   └── 泛型
├── 新特性
│   ├── Lambda / Stream
│   ├── Optional
│   ├── 方法引用
│   ├── 新日期 API (LocalDate)
│   └── 记录类 (Record) / instanceof 模式匹配
```

---

## 🎯 Level 1：基础题

### 1. == 和 equals 的区别？
**答案**：
- `==`：比较**基本类型值** 或 **引用类型地址**
- `equals`：Object 默认比较地址（==），子类可重写。**String、包装类**已重写为比较内容

```java
String a = new String("abc");
String b = new String("abc");
a == b        // false（地址不同）
a.equals(b)   // true（内容相同）
```

### 2. String 为什么不可变？有什么好处？
**答案**：
**不可变原因**：
1. `final` 修饰 char[] 数组 + `final` 类 + 无修改方法
2. 方法返回的都是新字符串

**好处**：
1. **线程安全**（不可变对象天然安全）
2. **字符串常量池**（缓存复用，性能优化）
3. **Hash 缓存**（hashCode 只计算一次，HashMap 键）
4. **安全性**（作为参数不会被子类篡改）

### 3. 抽象类和接口的区别？
**答案**：

| 特性 | 抽象类 | 接口 |
|------|--------|------|
| 关键词 | abstract class | interface |
| 方法 | 可含实现方法 | 默认方法/静态方法/抽象方法 |
| 字段 | 任意访问修饰符 | public static final |
| 构造器 | 有 | 无 |
| 单继承 | 单继承 | 多实现 |
| 设计思想 | "is-a"（是什么） | "can-do"（能做什么） |
| JDK 8+ | - | 默认方法（解决方法冲突） |

---

## 🎯 Level 2：进阶题

### 4. equals 和 hashCode 的关系？
**答案**：
- **约定**：`equals` 相等的对象 `hashCode` 必须相等
- 不重写 hashCode：`hashCode` 默认按地址计算，导致 HashMap 中将逻辑相等的对象放在不同桶

**需同时重写**：重写 equals 时必须重写 hashCode（否则 HashMap/HashSet 出 bug）

```java
public class Person {
    private String name;
    private int age;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return age == person.age && Objects.equals(name, person.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age);
    }
}
```

### 5. 深拷贝和浅拷贝的区别？
**答案**：
- **浅拷贝**：只复制基本类型和引用类型地址，引用对象**共享**
- **深拷贝**：引用对象也**独立复制**

**实现方式**：
```java
// 浅拷贝（需要实现 Cloneable）
@Override
protected Object clone() throws CloneNotSupportedException {
    return super.clone();  // 引用类型共享
}

// 深拷贝
@Override
protected Object clone() throws CloneNotSupportedException {
    User clone = (User) super.clone();
    clone.setAddress(this.address.clone());  // 引用类型也克隆
    return clone;
}

// 深拷贝（序列化方式）
ObjectOutputStream / ObjectInputStream 实现
```

---

## 🎯 Level 3：高级题

### 6. String、StringBuilder、StringBuffer 的区别？
**答案**：

| 特性 | String | StringBuilder | StringBuffer |
|------|--------|---------------|--------------|
| 可变性 | 不可变 | 可变 | 可变 |
| 线程安全 | 安全（不可变） | 不安全 | 安全（synchronized） |
| 性能 | 最差（拼接创建新对象） | 最快 | 中等 |
| 适用 | 常量/少量拼接 | 单线程拼接 | 多线程拼接 |

**使用建议**：`"+"` 拼接在编译期会优化为 `StringBuilder.append()`（单条语句内）。

### 7. 异常体系？
**答案**：

```
Throwable
 ├── Error（不可处理）
 │   ├── OutOfMemoryError
 │   ├── StackOverflowError
 │   └── NoClassDefFoundError
 └── Exception
     ├── RuntimeException（非受检异常）
     │   ├── NullPointerException
     │   ├── ClassCastException
     │   ├── IllegalArgumentException
     │   └── IndexOutOfBoundsException
     └── 受检异常（Checked）
         ├── IOException
         ├── SQLException
         └── ClassNotFoundException
```

**避免异常误用**：
- try-with-resources 自动关闭资源
- 不要捕获 Exception 后空处理
- 用自定义异常（业务异常）

### 8. 泛型中的类型擦除？
**答案**：
- 泛型信息在**编译期**擦除（运行时没有泛型类型）
- `List<String>` 和 `List<Integer>` 运行时都是 `List`
- 桥接方法解决多态问题

```java
// 编译期
List<String> list = new ArrayList<>();
// 运行时
List list = new ArrayList();  // 类型擦除

// 局限：不能 new T()、不能 instanceof T、不能创建泛型数组
```

---

## 📖 学习资源

### 推荐项目
- [JavaGuide 基础部分](https://javaguide.cn/java/basis/)
- [Java 官方教程](https://docs.oracle.com/javase/tutorial/)

### 最佳实践
1. 重写 equals 必须重写 hashCode
2. 字符串拼接优先 StringBuilder（大量拼接）
3. 捕获异常要具体，不捕获 Throwable
4. 处理完资源必须关闭（try-with-resources）