package com.example.advanced.starter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * CustomStarterDemo —— 演示 Spring Boot 条件装配
 *
 * @ConditionalOnProperty：根据配置文件中的属性值决定是否创建 Bean
 * 这是 Spring Boot 自动配置的核心机制之一
 *
 * 其他常用条件注解：
 * - @ConditionalOnClass：当类路径存在某个类时
 * - @ConditionalOnMissingBean：当容器中不存在某个 Bean 时
 * - @ConditionalOnExpression：当 SpEL 表达式为 true 时
 * - @ConditionalOnWebApplication：当应用是 Web 应用时
 */
@Component
@ConditionalOnProperty(
        name = "app.features.custom-starter.enabled", // 配置属性名
        havingValue = "true",                          // 属性值等于 true 时启用
        matchIfMissing = false                         // 未配置时默认不启用
)
public class CustomStarterDemo {

    private static final Logger log = LoggerFactory.getLogger(CustomStarterDemo.class);

    @PostConstruct
    public void init() {
        log.info("==============================================");
        log.info("  自定义 Starter 已激活（custom-starter-demo）");
        log.info("  配置: app.features.custom-starter.enabled=true");
        log.info("  此 Bean 由 @ConditionalOnProperty 控制");
        log.info("==============================================");
        System.out.println(">>> [CustomStarterDemo] 自定义 Starter 已启动！");
        System.out.println(">>> 可通过设置 app.features.custom-starter.enabled=false 关闭此功能");
    }

    /**
     * 模拟自定义 Starter 提供的功能
     */
    public void doSomething() {
        System.out.println(">>> [CustomStarterDemo] 执行自定义功能...");
    }
}