package com.example.advanced;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AdvancedApplication —— Spring Boot 进阶示例主类
 *
 * @EnableAsync：开启异步方法支持（@Async 生效的前提）
 * 需要先注册一个 TaskExecutor 的 Bean（见 AppConfig）
 */
@SpringBootApplication
@EnableAsync
public class AdvancedApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdvancedApplication.class, args);
    }
}