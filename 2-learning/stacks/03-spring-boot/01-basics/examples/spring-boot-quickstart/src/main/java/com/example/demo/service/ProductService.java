package com.example.demo.service;

import com.example.demo.dto.ProductRequest;
import com.example.demo.dto.ProductResponse;
import com.example.demo.model.Product;
import com.example.demo.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ProductService —— 商品业务逻辑层
 *
 * @Service：标记为 Spring 服务 Bean
 * @Transactional：声明式事务管理（默认遇到 RuntimeException 回滚）
 *
 * 三层架构：Controller（接收请求）→ Service（业务逻辑）→ Repository（数据访问）
 */
@Service
@Transactional(readOnly = true) // 类级别默认只读事务，提高查询性能
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 构造器注入（推荐方式，Spring 自动注入）
     * 相比 @Autowired 字段注入，优点：
     * - 不可变（final 字段）
     * - 易于测试（可手动构造）
     * - 明确依赖关系
     */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 查询所有商品
     */
    public List<ProductResponse> findAll() {
        return productRepository.findAll()
                .stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 查询商品
     */
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在，id: " + id));
        return ProductResponse.fromEntity(product);
    }

    /**
     * 创建商品
     * @Transactional 写操作需要读写事务
     */
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setDescription(request.getDescription());

        // save() 是 CrudRepository 提供的方法
        // 返回的实体包含自动生成的 id 和 createdAt
        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved);
    }

    /**
     * 更新商品
     */
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在，id: " + id));

        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setDescription(request.getDescription());

        // 在事务中修改实体，JPA 会自动将变更同步到数据库
        // 无需显式调用 save()（但调用也不会错）
        Product updated = productRepository.save(product);
        return ProductResponse.fromEntity(updated);
    }

    /**
     * 删除商品
     */
    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("商品不存在，id: " + id);
        }
        productRepository.deleteById(id);
    }

    // ========== 派生查询测试 ==========

    /**
     * 按名称搜索
     */
    public List<ProductResponse> searchByName(String keyword) {
        return productRepository.findByNameContaining(keyword)
                .stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }
}