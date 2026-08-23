package com.example.advanced.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AppConfig —— Spring @Configuration 配置类
 *
 * @Configuration：标记为配置类，等同于 XML 配置中的 <beans>
 * @Bean：在方法上声明，返回的对象将注册为 Spring IoC 容器中的 Bean
 *
 * 对比 @Component 和 @Bean：
 * - @Component：用于自定义类，通过类路径扫描自动注册
 * - @Bean：用于第三方库的类，或需要手动创建复杂配置的对象
 */
@Configuration
public class AppConfig {

    /**
     * 配置异步任务执行器
     * 为 @EnableAsync + @Async 提供线程池
     *
     * 如果不自定义此 Bean，Spring 会使用 SimpleAsyncTaskExecutor（每次创建新线程，不推荐）
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        // ThreadPoolTaskExecutor 是 Spring 对 ThreadPoolExecutor 的封装
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：池中始终保留的线程数
        executor.setCorePoolSize(2);

        // 最大线程数：队列满时最多可创建的线程数
        executor.setMaxPoolSize(5);

        // 任务队列容量：超出核心线程数的任务先放入队列
        executor.setQueueCapacity(10);

        // 线程名前缀：方便排查问题
        executor.setThreadNamePrefix("async-demo-");

        // 拒绝策略：队列和最大线程都满时的处理方式
        // CallerRunsPolicy：让调用者线程直接执行任务（降低任务提交速度）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务完成再关闭（优雅关闭）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

    /**
     * 演示：通过 @Bean 创建第三方库对象
     * 假设这里创建了一个第三方 SDK 的客户端
     */
    @Bean
    public String appVersion() {
        // 实际项目中可能返回一个配置好的第三方客户端
        return "1.0.0";
    }
}