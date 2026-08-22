package com.example.basics;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * HelloWorld —— 入门示例
 * 演示：包声明（package）、导入（import）、类定义、main 方法入口
 *
 * 运行方式：java com.example.basics.HelloWorld（需先编译）
 */
public class HelloWorld {

    // 程序入口：JVM 启动后调用 main 方法
    public static void main(String[] args) {
        // 1. 控制台输出
        System.out.println("Hello, Java 17!");

        // 2. 变量与数据类型
        int age = 18;                 // 基本类型 int
        double price = 19.99;         // 基本类型 double
        boolean passed = true;        // 基本类型 boolean
        String name = "张三";          // 引用类型 String（注意：String 是引用类型）

        System.out.println("姓名: " + name + ", 年龄: " + age
                + ", 价格: " + price + ", 是否通过: " + passed);

        // 3. 字符串模板拼接（JDK 8+ 方式，简单直观）
        String message = String.format("我叫 %s，今年 %d 岁。", name, age);
        System.out.println(message);

        // 4. 条件语句与循环
        for (int i = 1; i <= 5; i++) {
            if (i % 2 == 0) {
                System.out.println("i = " + i + " 是偶数");
            } else {
                System.out.println("i = " + i + " 是奇数");
            }
        }

        // 5. 使用 JDK 时间 API
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println("当前时间: " + now.format(formatter));

        // 6. 方法调用
        System.out.println("3 + 4 = " + add(3, 4));

        // 7. 命令行参数（程序启动时可通过 java HelloWorld arg1 arg2 传入）
        if (args.length > 0) {
            System.out.println("命令行参数[0] = " + args[0]);
        }
    }

    /**
     * 静态方法：两数相加
     * static 表示属于类本身，不需要实例即可调用
     */
    public static int add(int a, int b) {
        return a + b;
    }
}