package com.example.demo.controller;

import com.example.demo.dto.ProductRequest;
import com.example.demo.model.Product;
import com.example.demo.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProductControllerTest —— MockMvc 控制器测试
 *
 * @AutoConfigureMockMvc：自动配置 MockMvc，模拟 HTTP 请求，无需启动真实服务器
 * @SpringBootTest：加载完整 Spring 上下文
 *
 * 测试策略：
 * 1. 使用 H2 内存数据库，测试数据自动隔离
 * 2. 每个测试方法独立运行，互不干扰
 * 3. 验证 HTTP 状态码 + 响应 JSON 结构
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;  // 模拟 HTTP 请求/响应

    @Autowired
    private ObjectMapper objectMapper;  // Jackson JSON 序列化

    @Autowired
    private ProductRepository productRepository;  // 准备测试数据

    @BeforeEach
    void setUp() {
        // 每个测试之前清空数据，确保测试隔离
        productRepository.deleteAll();
    }

    @Test
    void testCreateProduct() throws Exception {
        // 准备请求数据
        ProductRequest request = new ProductRequest();
        request.setName("测试商品");
        request.setPrice(new BigDecimal("99.99"));
        request.setDescription("这是一个测试商品描述");

        // 发送 POST 请求并验证响应
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())                // 期望 HTTP 201
                .andExpect(jsonPath("$.id").isNumber())          // 检查 id 字段
                .andExpect(jsonPath("$.name").value("测试商品"))
                .andExpect(jsonPath("$.price").value(99.99))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void testGetAllProducts() throws Exception {
        // 先创建一条测试数据
        Product product = new Product("商品A", new BigDecimal("50.00"), "描述A");
        productRepository.save(product);

        // 查询所有，验证列表
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("商品A"));
    }

    @Test
    void testGetProductById() throws Exception {
        // 创建测试数据
        Product product = new Product("商品B", new BigDecimal("150.00"), "描述B");
        product = productRepository.save(product);
        Long id = product.getId();

        // 查询单个
        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("商品B"))
                .andExpect(jsonPath("$.price").value(150.00));
    }

    @Test
    void testGetProductById_NotFound() throws Exception {
        // 查询不存在的 ID
        mockMvc.perform(get("/api/products/{id}", 99999L))
                .andExpect(status().isNotFound());  // 期望 404
    }

    @Test
    void testUpdateProduct() throws Exception {
        // 先创建
        Product product = new Product("旧名称", new BigDecimal("10.00"), "旧描述");
        product = productRepository.save(product);
        Long id = product.getId();

        // 更新请求
        ProductRequest request = new ProductRequest();
        request.setName("新名称");
        request.setPrice(new BigDecimal("20.00"));
        request.setDescription("新描述");

        // 执行更新
        mockMvc.perform(put("/api/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新名称"))
                .andExpect(jsonPath("$.price").value(20.00));
    }

    @Test
    void testDeleteProduct() throws Exception {
        // 先创建
        Product product = new Product("待删除", new BigDecimal("1.00"), "将被删除");
        product = productRepository.save(product);
        Long id = product.getId();

        // 删除
        mockMvc.perform(delete("/api/products/{id}", id))
                .andExpect(status().isNoContent());  // 期望 HTTP 204

        // 验证已删除
        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void testSearchProducts() throws Exception {
        // 创建测试数据
        productRepository.save(new Product("智能手机", new BigDecimal("2999.00"), ""));
        productRepository.save(new Product("智能手表", new BigDecimal("1999.00"), ""));
        productRepository.save(new Product("笔记本电脑", new BigDecimal("5999.00"), ""));

        // 搜索"智能"
        mockMvc.perform(get("/api/products/search")
                        .param("keyword", "智能"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", containsString("智能")));
    }

    @Test
    void testCreateProduct_ValidationError() throws Exception {
        // 缺少必填字段：name 为空
        ProductRequest request = new ProductRequest();
        request.setName("");  // @NotBlank 会触发校验失败
        request.setPrice(new BigDecimal("99.99"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());  // 期望 400
    }
}