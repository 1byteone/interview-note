package com.example.demo.dto;

import com.example.demo.model.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ProductResponse —— 商品响应 DTO
 *
 * 与 Entity 解耦：不暴露内部字段名，可添加计算字段
 * 推荐使用 DTO 模式，而非直接返回 Entity
 */
public class ProductResponse {

    private Long id;
    private String name;
    private BigDecimal price;
    private String description;
    private LocalDateTime createdAt;

    // ========== 静态工厂方法：从 Entity 创建 DTO ==========

    /**
     * 将 Product 实体转换为 ProductResponse DTO
     * 解耦：即使 Entity 内部结构变化，API 响应格式保持不变
     */
    public static ProductResponse fromEntity(Product product) {
        ProductResponse resp = new ProductResponse();
        resp.setId(product.getId());
        resp.setName(product.getName());
        resp.setPrice(product.getPrice());
        resp.setDescription(product.getDescription());
        resp.setCreatedAt(product.getCreatedAt());
        return resp;
    }

    // ========== Getter / Setter ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}