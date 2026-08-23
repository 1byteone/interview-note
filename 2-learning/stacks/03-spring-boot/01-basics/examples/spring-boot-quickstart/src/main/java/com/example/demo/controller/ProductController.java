package com.example.demo.controller;

import com.example.demo.dto.ProductRequest;
import com.example.demo.dto.ProductResponse;
import com.example.demo.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ProductController —— 商品 REST API
 *
 * RESTful 规范：
 * GET    /api/products      查询所有
 * GET    /api/products/{id} 查询单个
 * POST   /api/products      创建
 * PUT    /api/products/{id} 更新
 * DELETE /api/products/{id} 删除
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 查询所有商品
     */
    @GetMapping
    public List<ProductResponse> getAll() {
        return productService.findAll();
    }

    /**
     * 查询单个商品
     */
    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.findById(id);
    }

    /**
     * 创建商品
     * @Valid：触发 ProductRequest 上的参数校验注解
     * 校验失败时抛出 MethodArgumentNotValidException
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    /**
     * 更新商品
     */
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    /**
     * 删除商品
     * @ResponseStatus(HttpStatus.NO_CONTENT)：HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    /**
     * 按名称搜索
     * GET /api/products/search?keyword=手机
     */
    @GetMapping("/search")
    public List<ProductResponse> search(@RequestParam String keyword) {
        return productService.searchByName(keyword);
    }
}