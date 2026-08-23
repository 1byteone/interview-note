package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * DemoApplicationTests —— 验证 Spring Context 能否正常加载
 *
 * @SpringBootTest：自动扫描配置，启动内嵌 Web 服务器
 * 如果 Context 加载失败（如 Bean 注入错误），测试将以红色失败
 */
@SpringBootTest
class DemoApplicationTests {

    @Test
    void contextLoads() {
        // 验证 Spring IoC 容器能正常启动
        // 所有 Bean 都能被正确注入
    }
}