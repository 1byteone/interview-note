package com.example.advanced.controller;

import com.example.advanced.service.GreetingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ActuatorDemoController —— 演示自定义端点 + Actuator 集成
 *
 * 包含：
 * 1. 一个触发自定义 HealthIndicator 状态的端点
 * 2. 同步/异步调用对比（配合 Micrometer 指标）
 * 3. 暴露运行时信息（配合 Actuator）
 */
@RestController
@RequestMapping("/api/demo")
public class ActuatorDemoController {

    private final GreetingService greetingService;

    /**
     * 从 application.yml 读取自定义配置
     * @Value 注入配置值（${key:默认值}）
     */
    @Value("${app.title:Advanced Demo}")
    private String appTitle;

    @Value("${app.version:0.0.1}")
    private String appVersion;

    public ActuatorDemoController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    /**
     * 应用信息：展示从配置读取的值（可配合 Actuator /info 端点）
     */
    @GetMapping("/info")
    public Map<String, String> info() {
        Map<String, String> info = new HashMap<>();
        info.put("title", appTitle);
        info.put("version", appVersion);
        info.put("thread", Thread.currentThread().getName());
        return info;
    }

    /**
     * 同步调用演示：调用线程将被阻塞 1 秒
     */
    @GetMapping("/greet/sync")
    public Map<String, Object> greetSync(@RequestParam(defaultValue = "World") String name) {
        Instant start = Instant.now();
        String message = greetingService.greetSync(name);
        long elapsed = Duration.between(start, Instant.now()).toMillis();

        Map<String, Object> result = new HashMap<>();
        result.put("message", message);
        result.put("elapsedMs", elapsed);
        result.put("thread", Thread.currentThread().getName());
        return result;
    }

    /**
     * 异步调用演示：立即返回，任务在线程池中异步执行
     */
    @GetMapping("/greet/async")
    public Map<String, Object> greetAsync(@RequestParam(defaultValue = "World") String name) {
        Instant start = Instant.now();
        CompletableFuture<String> future = greetingService.greetAsync(name);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "任务已提交，异步执行中");
        result.put("submittedAtMs", Duration.between(start, Instant.now()).toMillis());
        result.put("thread", Thread.currentThread().getName());
        // 注意：这里无法返回 future 的结果，因为方法已返回
        // 实际项目中可用 CompletableFuture.whenComplete 等异步回调
        return result;
    }

    /**
     * 异步 + 等待结果（演示 CompletableFuture.join）
     */
    @GetMapping("/greet/async-wait")
    public Map<String, Object> greetAsyncWait(@RequestParam(defaultValue = "World") String name) {
        Instant start = Instant.now();
        CompletableFuture<String> future = greetingService.greetAsync(name);
        String message = future.join();  // 等待异步任务完成（不会阻塞主线程池，但会阻塞当前请求线程）

        Map<String, Object> result = new HashMap<>();
        result.put("message", message);
        result.put("elapsedMs", Duration.between(start, Instant.now()).toMillis());
        result.put("thread", Thread.currentThread().getName());
        return result;
    }

    /**
     * 手动切换自定义健康检查状态
     * GET /api/demo/health/status?down=true  → 健康检查变为 DOWN
     * GET /api/demo/health/status?down=false → 健康检查恢复 UP
     */
    @GetMapping("/health/status")
    public Map<String, Object> toggleHealth(@RequestParam boolean down) {
        com.example.advanced.config.CustomHealthIndicator.manualDown = down;

        Map<String, Object> result = new HashMap<>();
        result.put("manualDown", down);
        result.put("hint", "访问 /actuator/health 查看当前健康状态");
        return result;
    }
}