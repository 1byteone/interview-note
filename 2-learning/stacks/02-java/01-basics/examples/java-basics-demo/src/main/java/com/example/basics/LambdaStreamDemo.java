package com.example.basics;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * LambdaStreamDemo —— Lambda 表达式与 Stream API 示例
 *
 * 演示内容：
 * 1. Lambda 表达式的基本语法与使用
 * 2. Stream API：filter / map / reduce / collect / groupingBy
 * 3. 方法引用（Method Reference）
 * 4. Optional 的创建与使用
 */
public class LambdaStreamDemo {

    public static void main(String[] args) {
        // ========== 准备数据 ==========
        List<Student> students = Arrays.asList(
                new Student("张三", "计算机", 85),
                new Student("李四", "数学", 92),
                new Student("王五", "计算机", 78),
                new Student("赵六", "物理", 95),
                new Student("钱七", "数学", 88),
                new Student("孙八", "计算机", 70),
                new Student("周九", "物理", 60)
        );

        // ========== 1. Lambda 表达式基础 ==========

        System.out.println("=== 1. Lambda 表达式入门 ===");

        // Lambda 语法： (参数) -> { 方法体 }
        // 传统方式：匿名内部类
        Comparator<Student> byScoreOld = new Comparator<>() {
            @Override
            public int compare(Student a, Student b) {
                return Integer.compare(a.getScore(), b.getScore());
            }
        };

        // Lambda 方式：更简洁
        Comparator<Student> byScoreLambda = (a, b) -> Integer.compare(a.getScore(), b.getScore());

        // 方法引用方式：最简洁
        Comparator<Student> byScoreRef = Comparator.comparingInt(Student::getScore);

        // 使用 Lambda 排序
        List<Student> sorted = new ArrayList<>(students);
        sorted.sort(byScoreLambda);
        System.out.println("按分数升序:");
        sorted.forEach(s -> System.out.println("  " + s));  // forEach 接收 Consumer 函数式接口

        // ========== 2. Stream API 操作 ==========

        System.out.println("\n=== 2. Stream API 链式操作 ===");

        // filter: 过滤出计算机专业的学生
        Stream<Student> csStream = students.stream()
                .filter(s -> "计算机".equals(s.getMajor()));

        // map: 提取姓名
        List<String> csNames = students.stream()
                .filter(s -> "计算机".equals(s.getMajor()))
                .map(Student::getName)          // 方法引用：s -> s.getName()
                .collect(Collectors.toList());  // 收集为 List

        System.out.println("计算机专业学生: " + csNames);

        // ========== 3. reduce 归约 ==========

        System.out.println("\n=== 3. reduce 归约操作 ===");

        // 计算所有学生的总分
        int totalScore = students.stream()
                .mapToInt(Student::getScore)
                .sum();
        System.out.println("总分: " + totalScore);

        // 使用 reduce 方法：累加分数
        Optional<Integer> sum = students.stream()
                .map(Student::getScore)
                .reduce(Integer::sum);  // 等价于 (a, b) -> a + b
        sum.ifPresent(s -> System.out.println("reduce 计算总分: " + s));

        // reduce 带初始值
        int sumWithInit = students.stream()
                .map(Student::getScore)
                .reduce(0, Integer::sum);
        System.out.println("reduce 带初始值总分: " + sumWithInit);

        // ========== 4. collect 与 groupingBy 分组 ==========

        System.out.println("\n=== 4. 分组操作 groupingBy ===");

        // 按专业分组
        Map<String, List<Student>> byMajor = students.stream()
                .collect(Collectors.groupingBy(Student::getMajor));

        byMajor.forEach((major, group) -> {
            System.out.println(major + ": " + group.size() + "人");
            group.forEach(s -> System.out.println("  " + s));
        });

        // 按专业统计平均分
        Map<String, Double> avgScoreByMajor = students.stream()
                .collect(Collectors.groupingBy(
                        Student::getMajor,                  // 分组键
                        Collectors.averagingInt(Student::getScore)  // 下游收集器：计算平均值
                ));
        System.out.println("\n各专业平均分:");
        avgScoreByMajor.forEach((major, avg) ->
                System.out.printf("  %s: %.1f分%n", major, avg));

        // 按分数段分组：partitioningBy 分为及格/不及格
        Map<Boolean, List<Student>> passFail = students.stream()
                .collect(Collectors.partitioningBy(s -> s.getScore() >= 60));
        System.out.println("\n及格人数: " + passFail.get(true).size()
                + ", 不及格人数: " + passFail.get(false).size());

        // ========== 5. 方法引用 ==========

        System.out.println("\n=== 5. 方法引用 ===");

        // 静态方法引用：ClassName::staticMethod
        List<String> names = students.stream()
                .map(Student::getName)
                .collect(Collectors.toList());
        names.forEach(System.out::println);  // 实例方法引用：object::instanceMethod

        // 构造器引用：ClassName::new
        List<String> nameList = Arrays.asList("甲", "乙", "丙");
        List<Student> fromNames = nameList.stream()
                .map(Student::new)  // 调用 Student(String name) 构造器
                .collect(Collectors.toList());
        System.out.println("构造器引用创建: " + fromNames);

        // ========== 6. Optional 使用 ==========

        System.out.println("\n=== 6. Optional 处理空值 ===");

        // 查找第一个计算机专业的学生
        Optional<Student> firstCS = students.stream()
                .filter(s -> "计算机".equals(s.getMajor()))
                .findFirst();

        // 安全地处理 Optional 结果
        firstCS.ifPresent(s -> System.out.println("第一个计算机学生: " + s));

        // orElse 提供默认值
        Student found = firstCS.orElse(new Student("默认", "未知", 0));
        System.out.println("找到或默认: " + found);

        // orElseThrow 不存在时抛出异常
        Student mustExist = firstCS.orElseThrow(() -> new NoSuchElementException("没有找到计算机学生"));

        // Optional 链式操作
        String result = students.stream()
                .filter(s -> s.getScore() > 90)
                .findFirst()
                .map(Student::getName)
                .orElse("没有90分以上的学生");
        System.out.println("第一名高分学生: " + result);

        // 空 Optional 的处理
        Optional<String> empty = Optional.empty();
        String value = empty
                .map(String::toUpperCase)
                .filter(v -> v.length() > 3)
                .orElse("默认值");
        System.out.println("空 Optional 处理结果: " + value);
    }
}

/**
 * 学生实体类，用于 Stream 操作演示
 */
class Student {
    private String name;
    private String major;
    private int score;

    // 构造器引用演示用
    public Student(String name) {
        this(name, "未知", 0);
    }

    public Student(String name, String major, int score) {
        this.name = name;
        this.major = major;
        this.score = score;
    }

    // getter / setter
    public String getName() { return name; }
    public String getMajor() { return major; }
    public int getScore() { return score; }
    public void setName(String name) { this.name = name; }
    public void setMajor(String major) { this.major = major; }
    public void setScore(int score) { this.score = score; }

    @Override
    public String toString() {
        return String.format("%s(%s, %d分)", name, major, score);
    }
}