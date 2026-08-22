package com.example.demo.repository;

import com.example.demo.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ProductRepository —— JPA 数据访问层
 *
 * 继承 JpaRepository<Product, Long> 自动获得 CRUD 方法：
 * - findAll() / findById(id) / save(entity) / deleteById(id) / count()
 * - 无需手写 SQL！Spring Data JPA 根据方法名自动生成查询
 *
 * JpaRepository 继承体系：
 * Repository (标记接口)
 *   └─ CrudRepository (CRUD 方法)
 *        └─ PagingAndSortingRepository (分页排序)
 *             └─ JpaRepository (JPA 特有方法：flush/batch)
 */
@Repository // 标记为 Spring Bean，允许异常转换为 DataAccessException
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ========== 派生查询（Derived Query）：根据方法名自动生成 SQL ==========

    /**
     * 按名称精确查找
     * HQL: select p from Product p where p.name = ?1
     */
    List<Product> findByName(String name);

    /**
     * 按名称模糊查询
     * HQL: select p from Product p where p.name like %?1%
     */
    List<Product> findByNameContaining(String keyword);

    /**
     * 按价格范围查询
     * HQL: select p from Product p where p.price between ?1 and ?2
     */
    List<Product> findByPriceBetween(Double min, Double max);

    /**
     * 按名称排序（按价格降序）
     * HQL: select p from Product p where p.name = ?1 order by p.price desc
     */
    List<Product> findByNameOrderByPriceDesc(String name);
}