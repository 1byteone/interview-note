package com.example.basics;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ExceptionDemo —— Java 异常处理机制示例
 *
 * 演示内容：
 * 1. try-catch-finally 基本异常处理
 * 2. try-with-resources 自动资源管理（Java 7+）
 * 3. 自定义异常
 * 4. 多 catch 块
 */
public class ExceptionDemo {

    public static void main(String[] args) {
        ExceptionDemo demo = new ExceptionDemo();

        System.out.println("=== 1. try-catch-finally 基本异常处理 ===");
        demo.basicTryCatch();

        System.out.println("\n=== 2. 多 catch 块 ===");
        demo.multiCatch();

        System.out.println("\n=== 3. try-with-resources 自动资源管理 ===");
        demo.tryWithResources();

        System.out.println("\n=== 4. 自定义异常 ===");
        try {
            demo.validateAge(-5);
        } catch (BusinessException e) {
            System.err.println("捕获自定义异常: " + e.getMessage());
            System.err.println("错误码: " + e.getErrorCode());
            e.printStackTrace();
        }

        System.out.println("\n=== 5. finally 一定会执行 ===");
        demo.finallyAlwaysRuns();

        System.out.println("\n程序正常结束！");
    }

    // ============================================================
    // 1. try-catch-finally 基本结构
    // ============================================================
    public void basicTryCatch() {
        try {
            // 可能抛出异常的代码
            int result = 10 / 2;  // 正常，不会抛出 ArithmeticException
            System.out.println("计算结果: " + result);

            // 模拟异常：访问数组越界
            int[] arr = {1, 2, 3};
            System.out.println(arr[5]);  // ArrayIndexOutOfBoundsException
        } catch (ArrayIndexOutOfBoundsException e) {
            // 捕获特定类型的异常
            System.err.println("数组越界异常: " + e.getMessage());
        } finally {
            // finally 块：无论是否抛出异常，都会执行
            // 通常用于释放资源：关闭文件、数据库连接等
            System.out.println("finally 块执行（清理资源）");
        }
    }

    // ============================================================
    // 2. 多 catch 块（Java 7+ 支持 multi-catch）
    // ============================================================
    public void multiCatch() {
        try {
            String input = "abc";
            // NumberFormatException
            int num = Integer.parseInt(input);
            System.out.println("解析数字: " + num);

        } catch (NumberFormatException e) {
            // 捕获数字格式异常
            System.err.println("数字格式错误: " + e.getMessage());

        } catch (NullPointerException e) {
            // 捕获空指针异常
            System.err.println("空指针异常: " + e.getMessage());

        } catch (IllegalArgumentException e) {
            System.err.println("非法参数: " + e.getMessage());

        } catch (Exception e) {
            // 通用异常处理（兜底）
            System.err.println("其他异常: " + e.getMessage());

        } finally {
            System.out.println("multiCatch 方法 finally 块");
        }

        // Java 7+ 多异常捕获（multi-catch）：用 | 分隔多个异常类型
        try {
            // 根据条件抛出不同异常
            boolean condition = true;
            if (condition) {
                throw new IOException("IO 错误");
            } else {
                throw new IllegalArgumentException("参数错误");
            }
        } catch (IOException | IllegalArgumentException e) {
            // 统一处理多个异常类型
            // 注意：e 在此处是 final 类型（不能重新赋值）
            System.err.println("multi-catch 捕获: " + e.getClass().getSimpleName()
                    + " - " + e.getMessage());
        }
    }

    // ============================================================
    // 3. try-with-resources (Java 7+，Java 9 支持增强)
    // 自动调用资源的 close() 方法，无需显式 finally
    // ============================================================
    public void tryWithResources() {
        // 方式一：在 try 中声明资源（Java 7+）
        // 资源必须实现 AutoCloseable 接口
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("demo-", ".txt");
            System.out.println("临时文件: " + tempFile);
        } catch (IOException e) {
            System.err.println("创建临时文件失败: " + e.getMessage());
        }

        // 使用 try-with-resources 写入文件
        if (tempFile != null) {
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
                // try 块结束后，writer 会自动关闭（调用 close() 方法）
                writer.write("Hello, try-with-resources!");
                writer.newLine();
                writer.write("无需手动调用 close()");
                System.out.println("文件写入成功");
            } catch (IOException e) {
                System.err.println("文件写入失败: " + e.getMessage());
            }

            // 读取刚写入的内容
            try (BufferedReader reader = Files.newBufferedReader(tempFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("读取内容: " + line);
                }
            } catch (IOException e) {
                System.err.println("文件读取失败: " + e.getMessage());
            }
        }

        // 方式二：Java 9 增强，可以在 try 外部声明资源
        // 只要资源是 final 或 effectively final 即可
        /*
        BufferedReader reader = Files.newBufferedReader(tempFile);
        try (reader) {  // 无需重新声明
            // 使用 reader
        }
        */
    }

    // ============================================================
    // 4. 自定义异常
    // ============================================================
    public void validateAge(int age) throws BusinessException {
        if (age < 0) {
            // 抛出自定义异常，包含错误码和详细信息
            throw new BusinessException("AGE_NEGATIVE", "年龄不能为负数: " + age);
        }
        if (age > 150) {
            throw new BusinessException("AGE_TOO_LARGE", "年龄超过合理范围: " + age);
        }
        System.out.println("年龄验证通过: " + age);
    }

    // ============================================================
    // 5. finally 一定会执行（特殊情况验证）
    // ============================================================
    @SuppressWarnings("finally")
    public void finallyAlwaysRuns() {
        try {
            System.out.println("try 块开始");
            // 即使有 return 语句，finally 仍会在 return 之前执行
            // return;  // 取消注释测试
            // System.exit(0);  // 只有 System.exit() 才会阻止 finally 执行
        } catch (Exception e) {
            System.err.println("catch 块");
        } finally {
            System.out.println("finally 块：无论 try 中是否有 return，我都会执行");
        }
    }
}

/**
 * 自定义业务异常
 * 继承 RuntimeException 表示非检查型异常（Unchecked Exception）
 * 继承 Exception 表示检查型异常（Checked Exception），需要 throws 声明
 */
class BusinessException extends RuntimeException {

    private final String errorCode;
    private final String detailMessage;

    /**
     * @param errorCode 错误码，用于定位问题
     * @param message   人类可读的错误描述
     */
    public BusinessException(String errorCode, String message) {
        super(message);  // 调用父类构造器存储消息
        this.errorCode = errorCode;
        this.detailMessage = message;
    }

    /**
     * 支持链式异常（cause）：保留原始异常信息
     */
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.detailMessage = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDetailMessage() {
        return detailMessage;
    }

    @Override
    public String toString() {
        return String.format("BusinessException[%s]: %s", errorCode, detailMessage);
    }
}