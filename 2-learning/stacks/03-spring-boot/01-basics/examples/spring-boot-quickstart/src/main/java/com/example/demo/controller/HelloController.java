package com.example.demo.controller;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * HelloController —— 入门示例
 *
 * 演示：
 * - @RestController：相当于 @Controller + @ResponseBody，返回 JSON
 * - GET / POST 请求映射
 * - 路径参数 / 请求参数 / 请求体
 */
@RestController // 标记为 REST 控制器，所有方法返回值自动序列化为 JSON
@RequestMapping("/api/hello") // 类级别 URL 前缀
public class HelloController {

    /**
     * GET /api/hello
     * 最简单的 GET 请求，返回字符串
     */
    @GetMapping
    public String hello() {
        return "Hello from Spring Boot!";
    }

    /**
     * GET /api/hello/greet?name=张三
     * 演示 @RequestParam 绑定请求参数
     * defaultValue 提供默认值，required 设为 false 表示可选
     */
    @GetMapping("/greet")
    public Map<String, Object> greet(
            @RequestParam(value = "name", defaultValue = "World") String name) {

        Map<String, Object> result = new HashMap<>();
        result.put("message", "你好, " + name + "!");
        result.put("time", LocalDateTime.now().toString());
        return result; // 自动转为 JSON
    }

    /**
     * GET /api/hello/{id}
     * 演示 @PathVariable 绑定路径参数
     */
    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("name", "用户 #" + id);
        return result;
    }

    /**
     * POST /api/hello/echo
     * 演示 @RequestBody 接收 JSON 请求体
     * 客户端发送 JSON，Spring 自动反序列化为 Map
     */
    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>(body);
        result.put("echo", true);
        result.put("receivedAt", LocalDateTime.now().toString());
        return result;
    }
}