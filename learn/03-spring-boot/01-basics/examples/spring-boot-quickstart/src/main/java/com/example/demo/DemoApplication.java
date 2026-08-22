package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DemoApplication —— Spring Boot 应用入口
 *
 * @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
 * 自动扫描当前包及其子包下的所有 Spring 组件
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        // SpringApplication.run() 启动内嵌 Web 容器（Tomcat）
        // 返回 ConfigurableApplicationContext（Spring IoC 容器）
        SpringApplication.run(DemoApplication.class, args);
    }
}