package com.example.advanced.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * GreetingService —— 演示 @Async 异步方法
 *
 * 异步方法的调用规则：
 * 1. 调用方必须通过 Spring Bean 调用（代理生效），类内部调用无效
 * 2. @Async 方法不能是 private
 * 3. 异步线程来自 AppConfig 中配置的 taskExecutor 线程池
 * 4. 返回 void 时无法感知执行结果，建议返回 CompletableFuture<T>
 */
@Service
public class GreetingService {

    private static final Logger log = LoggerFactory.getLogger(GreetingService.class);

    /**
     * 同步方法：调用方线程直接执行
     */
    public String greetSync(String name) {
        sleep(1000);  // 模拟耗时操作 1 秒
        return "Hello, " + name + "! (同步, 耗时1s)";
    }

    /**
     * 异步方法：由线程池异步执行，方法立即返回
     * 通过线程名可以观察到执行线程与调用线程不同
     */
    @Async("taskExecutor") // 指定使用 AppConfig 中配置的线程池
    public CompletableFuture<String> greetAsync(String name) {
        log.info("【异步任务执行】线程: {}, 处理: {}", Thread.currentThread().getName(), name);
        sleep(1000);  // 模拟耗时操作 1 秒
        return CompletableFuture.completedFuture("Hello, " + name + "! (异步, 耗时1s)");
    }

    /**
     * 模拟耗时的工具方法
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}