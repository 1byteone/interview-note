package com.example.basics;

import java.util.ArrayList;
import java.util.List;

/**
 * OopDemo —— 面向对象编程核心概念示例
 *
 * 演示内容：
 * 1. 抽象类（Abstract Class）与抽象方法
 * 2. 接口（Interface）与默认方法（default method）
 * 3. 继承（Inheritance）与实现（Implements）
 * 4. 多态（Polymorphism）
 * 5. 泛型（Generics）
 * 6. 记录类（Record，Java 16+）
 * 7. 密封类（Sealed Class，Java 17+）
 */
public class OopDemo {

    public static void main(String[] args) {
        // ========== 1. 继承与多态 ==========

        // 向上转型：子类对象被当作父类引用使用（多态的核心）
        Animal dog = new Dog("旺财", "金毛");
        Animal cat = new Cat("咪咪", "橘猫");

        // 多态：同一方法调用在不同子类上表现出不同行为
        List<Animal> animals = new ArrayList<>();
        animals.add(dog);
        animals.add(cat);

        System.out.println("=== 多态演示 ===");
        for (Animal animal : animals) {
            // 动态绑定：运行时根据实际对象类型调用对应的方法
            animal.makeSound();   // 具体子类实现
            animal.sleep();       // 父类已实现的方法，子类可重写也可不重写
            System.out.println(animal.describe());  // 调用父类非抽象方法
            System.out.println("---");
        }

        // 向下转型：需要 instanceof 检查，否则可能 ClassCastException
        if (dog instanceof Dog d) {
            // Java 16+ instanceof 模式匹配，无需手动强转
            d.fetch();
        }

        // ========== 2. 接口与默认方法 ==========

        Swimmable swimmer = new Dog("水手", "拉布拉多");
        swimmer.swim();       // 接口抽象方法的实现
        swimmer.dive();       // 接口默认方法，子类可直接使用或重写

        // ========== 3. 泛型 ==========

        System.out.println("\n=== 泛型演示 ===");
        Box<String> stringBox = new Box<>("Hello Generics");
        Box<Integer> intBox = new Box<>(42);

        System.out.println("String Box: " + stringBox.getContent());
        System.out.println("Integer Box: " + intBox.getContent());

        // 泛型方法：交换两个元素
        String[] names = {"小明", "小红", "小刚"};
        swap(names, 0, 1);
        System.out.println("交换后: " + String.join(", ", names));

        // ========== 4. 记录类 ==========

        System.out.println("\n=== Record 记录类演示 ===");
        Product phone = new Product("P001", "智能手机", 2999.99);
        // 记录类自动生成构造器、getter、equals、hashCode、toString
        System.out.println(phone);
        System.out.println("商品名称: " + phone.name() + ", 价格: " + phone.price());

        // 记录类的紧凑构造器验证
        // Product invalid = new Product("P002", "", 100.0);  // 会抛出 IllegalArgumentException

        // ========== 5. 密封类 ==========

        System.out.println("\n=== 密封类演示 ===");
        Shape circle = new Circle(5.0);
        Shape rectangle = new Rectangle(3.0, 4.0);
        System.out.println("圆形面积: " + circle.area());
        System.out.println("矩形面积: " + rectangle.area());

        // 密封类在 switch 中可被穷举（Java 17+ 预览，Java 21 正式）
        // 编译器可以检查是否覆盖了所有子类型
        printArea(circle);
        printArea(rectangle);
    }

    // 泛型方法：交换数组中两个位置的值
    public static <T> void swap(T[] array, int i, int j) {
        T temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    // 密封类 + 模式匹配（Java 16+ 的 instanceof 模式匹配）
    public static void printArea(Shape shape) {
        // 使用 instanceof 模式匹配（Java 16 正式引入）
        if (shape instanceof Circle c) {
            System.out.println("圆形, 半径=" + c.radius() + ", 面积=" + c.area());
        } else if (shape instanceof Rectangle r) {
            System.out.println("矩形, 长=" + r.length() + ", 宽=" + r.width() + ", 面积=" + r.area());
        }
    }
}

// ============================================================
// 1. 抽象类：不能被实例化，可以包含抽象方法和具体方法
// ============================================================
abstract class Animal {
    protected String name;  // protected：子类可访问

    // 构造器：抽象类可以有构造器，用于初始化子类公共字段
    public Animal(String name) {
        this.name = name;
    }

    // 抽象方法：只有声明，没有实现，子类必须实现
    public abstract void makeSound();

    // 具体方法：子类可以继承或重写
    public void sleep() {
        System.out.println(name + " 正在睡觉... zzz");
    }

    // 具体方法：提供公共行为
    public String describe() {
        return "这是一只动物，名字叫: " + name;
    }
}

// ============================================================
// 2. 接口：定义行为契约，支持多实现
// ============================================================
interface Swimmable {
    // 抽象方法：实现类必须实现
    void swim();

    // 默认方法（Java 8+）：有方法体，实现类可选择性地重写
    default void dive() {
        System.out.println("正在潜水...（默认行为）");
    }
}

// ============================================================
// 3. 继承与实现：Dog 继承 Animal，同时实现 Swimmable 接口
// ============================================================
class Dog extends Animal implements Swimmable {
    private String breed;  // 品种

    public Dog(String name, String breed) {
        super(name);  // 调用父类构造器
        this.breed = breed;
    }

    // 实现抽象方法
    @Override
    public void makeSound() {
        System.out.println(name + "（" + breed + "）: 汪汪汪！");
    }

    // 重写父类方法
    @Override
    public void sleep() {
        System.out.println(name + " 蜷缩着睡觉...");
    }

    // 实现接口方法
    @Override
    public void swim() {
        System.out.println(name + " 正在狗刨式游泳 🏊");
    }

    // 重写接口默认方法
    @Override
    public void dive() {
        System.out.println(name + " 一头扎进水里！");
    }

    // 子类特有方法
    public void fetch() {
        System.out.println(name + " 正在接飞盘 🥏");
    }
}

class Cat extends Animal {
    private String color;  // 毛色

    public Cat(String name, String color) {
        super(name);
        this.color = color;
    }

    @Override
    public void makeSound() {
        System.out.println(name + "（" + color + "）: 喵喵喵～");
    }

    @Override
    public void sleep() {
        System.out.println(name + " 在窗台上晒太阳睡觉...");
    }
}

// ============================================================
// 4. 泛型类：Box<T> 可以存放任意类型
// ============================================================
class Box<T> {
    private T content;

    public Box(T content) {
        this.content = content;
    }

    public T getContent() {
        return content;
    }

    public void setContent(T content) {
        this.content = content;
    }

    // 泛型方法也可以定义在非泛型类中
    public <U> boolean isSameType(U other) {
        return content.getClass() == other.getClass();
    }
}

// ============================================================
// 5. Record 记录类（Java 16 正式引入）
// 不可变数据载体：自动生成构造器、访问器、equals、hashCode、toString
// ============================================================
record Product(String id, String name, double price) {
    // 紧凑构造器（Compact Constructor）：可以添加参数校验
    public Product {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (price < 0) {
            throw new IllegalArgumentException("价格不能为负数");
        }
    }

    // 记录类中也可以定义额外方法
    public String toShortString() {
        return id + " - " + name;
    }
}

// ============================================================
// 6. 密封类（Sealed Class, Java 17 正式引入）
// 限制哪些类可以继承或实现，提供更精确的继承控制
// ============================================================
abstract sealed class Shape permits Circle, Rectangle {
    // sealed 类必须声明 permits 子类列表；由于包含抽象方法，必须声明为 abstract
    public abstract double area();
}

// 允许继承的子类必须用 final / sealed / non-sealed 修饰
final class Circle extends Shape {
    private final double radius;

    public Circle(double radius) {
        this.radius = radius;
    }

    public double radius() {
        return radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
}

// non-sealed 表示允许被进一步继承
non-sealed class Rectangle extends Shape {
    private final double length;
    private final double width;

    public Rectangle(double length, double width) {
        this.length = length;
        this.width = width;
    }

    public double length() {
        return length;
    }

    public double width() {
        return width;
    }

    @Override
    public double area() {
        return length * width;
    }
}