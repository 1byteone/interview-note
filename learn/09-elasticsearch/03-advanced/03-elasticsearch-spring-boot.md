# Spring Boot 集成 Elasticsearch 实战

## 1. 概述

Spring Data Elasticsearch 是 Spring 官方提供的 ES 集成框架，封装了底层 REST Client，提供声明式 Repository 和模板 API。本文基于 Spring Boot 3.x 和 Spring Data Elasticsearch 5.x，介绍完整的集成步骤与常用操作。

---

## 2. 依赖配置

### 2.1 Maven 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

Spring Boot 3.x 默认使用 Spring Data Elasticsearch 5.x，底层基于 Elasticsearch Java Client（非旧版 Transport Client）。

### 2.2 application.yml 配置

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 10s
    socket-timeout: 30s
    username: elastic
    password: ${ES_PASSWORD}
```

---

## 3. 实体类注解

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;

@Document(indexName = "products", createIndex = true)
@Mapping(mappingPath = "mappings/products.json")
public class EsProduct {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Integer)
    private Integer categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Field(type = FieldType.Integer)
    private Integer stock;

    @Field(type = FieldType.Keyword)
    private String status;
}
```

### 注解说明

| 注解 | 用途 |
|------|------|
| `@Document` | 声明索引名称，`createIndex = true` 可在启动时自动创建 |
| `@Id` | 文档唯一标识，对应 ES 的 `_id` |
| `@Field` | 字段映射，指定类型、分词器 |
| `@Mapping` | 引用外部自定义 mapping 文件 |

---

## 4. ElasticsearchRestTemplate 使用

`ElasticsearchRestTemplate` 是 Spring Data Elasticsearch 的核心模板类，提供底层索引和文档操作。

```java
@Service
public class ProductIndexService {

    @Autowired
    private ElasticsearchRestTemplate template;

    // 创建索引
    public boolean createIndex() {
        return template.indexOps(EsProduct.class).create();
    }

    // 写入文档
    public void save(EsProduct product) {
        template.save(product);
    }

    // 批量写入
    public void saveAll(List<EsProduct> products) {
        template.save(products);
    }

    // 根据 ID 查询
    public EsProduct findById(Long id) {
        return template.get(id.toString(), EsProduct.class);
    }

    // 更新文档
    public void update(EsProduct product) {
        UpdateQuery updateQuery = UpdateQuery.builder(product.getId().toString())
            .withDocument(Document.parse(JsonUtils.toJson(product)))
            .build();
        template.update(updateQuery, IndexCoordinates.of("products"));
    }

    // 删除文档
    public void deleteById(Long id) {
        template.delete(id.toString(), EsProduct.class);
    }
}
```

---

## 5. 自定义 Repository

### 5.1 基础接口

```java
public interface EsProductRepository
        extends ElasticsearchRepository<EsProduct, Long> {

    // 根据名称搜索（自动实现）
    List<EsProduct> findByName(String name);

    // 价格区间查询
    List<EsProduct> findByPriceBetween(BigDecimal min, BigDecimal max);

    // 按分类查询
    Page<EsProduct> findByCategoryId(Integer categoryId, Pageable pageable);

    // 品牌+状态组合查询
    List<EsProduct> findByBrandAndStatus(String brand, String status);
}
```

### 5.2 自定义查询方法

```java
public interface EsProductRepository
        extends ElasticsearchRepository<EsProduct, Long> {

    @Query("{\"match\": {\"name\": \"?0\"}}")
    Page<EsProduct> searchByName(String name, Pageable pageable);

    @Query("{\"bool\": {\"must\": ["
            + "{\"match\": {\"name\": \"?0\"}},"
            + "{\"range\": {\"price\": {\"gte\": \"?1\", \"lte\": \"?2\"}}}"
            + "]}}")
    Page<EsProduct> searchByNameAndPriceRange(
            String name, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
}
```

---

## 6. 高亮、分页与排序

### 6.1 NativeSearchQuery 构建

```java
@Service
public class ProductSearchService {

    @Autowired
    private ElasticsearchRestTemplate template;

    public Page<EsProduct> search(String keyword, int page, int size, String sortField) {
        // 1. 构建查询条件
        NativeQueryBuilder queryBuilder = new NativeQueryBuilder();

        // 2. 全文检索
        queryBuilder.withQuery(QueryBuilders
            .multiMatchQuery(keyword, "name", "title")
            .type(MultiMatchQueryType.BEST_FIELDS));

        // 3. 高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("name").field("title");
        highlightBuilder.preTags("<em>").postTags("</em>");
        queryBuilder.withHighlightBuilder(highlightBuilder);

        // 4. 分页
        Pageable pageable = PageRequest.of(page, size);
        queryBuilder.withPageable(pageable);

        // 5. 排序
        queryBuilder.withSort(Sort.by(Sort.Direction.DESC, sortField));

        // 6. 执行查询
        SearchHits<EsProduct> searchHits = template.search(
                queryBuilder.build(), EsProduct.class);

        // 7. 提取高亮字段
        List<EsProduct> products = searchHits.stream()
            .map(hit -> {
                EsProduct product = hit.getContent();
                Map<String, List<String>> highlightFields = hit.getHighlightFields();
                if (highlightFields.containsKey("name")) {
                    product.setName(highlightFields.get("name").get(0));
                }
                return product;
            })
            .collect(Collectors.toList());

        return new PageImpl<>(products, pageable, searchHits.getTotalHits());
    }
}
```

### 6.2 分页与排序参数

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| `page` | 页码（从 0 开始） | 前端传入 |
| `size` | 每页条数 | 10-20，避免深分页 |
| `sort` | 排序字段 | `score`（相关性）、`price`、`saleCount` |
| `maxScore` | 最高分，用于相关性分析 | 由 ES 计算 |

---

## 7. 完整示例：商品搜索 Service

```java
@Service
@Slf4j
public class ProductSearchService {

    @Autowired
    private EsProductRepository esProductRepository;

    @Autowired
    private ElasticsearchRestTemplate template;

    /**
     * 商品搜索（支持高亮、分页、排序、过滤）
     */
    public Page<ProductSearchVO> search(ProductSearchRequest request) {
        // 1. 构建 Bool 查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 1.1 关键词匹配
        if (StringUtils.hasText(request.getKeyword())) {
            boolQuery.must(QueryBuilders.multiMatchQuery(
                    request.getKeyword(), "name", "title", "brand")
                    .type(MultiMatchQueryType.BEST_FIELDS));
        }

        // 1.2 分类过滤
        if (request.getCategoryId() != null) {
            boolQuery.filter(QueryBuilders.termQuery("categoryId", request.getCategoryId()));
        }

        // 1.3 价格区间
        if (request.getMinPrice() != null && request.getMaxPrice() != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("price")
                    .gte(request.getMinPrice()).lte(request.getMaxPrice()));
        }

        // 1.4 商品状态
        boolQuery.filter(QueryBuilders.termQuery("status", "ON_SHELF"));

        // 2. 构建 NativeQuery
        NativeQueryBuilder queryBuilder = NativeQueryBuilder.builder()
            .withQuery(boolQuery)
            .withPageable(PageRequest.of(request.getPage(), request.getSize()))
            .withSort(Sort.by(Sort.Direction.DESC, request.getSortField() != null
                    ? request.getSortField() : "_score"))
            .withHighlightBuilder(new HighlightBuilder()
                    .field("name").field("title")
                    .preTags("<span class='highlight'>")
                    .postTags("</span>"));

        // 3. 执行搜索
        SearchHits<EsProduct> searchHits = template.search(
                queryBuilder.build(), EsProduct.class);

        // 4. 转换结果
        List<ProductSearchVO> voList = searchHits.stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());

        return new PageImpl<>(voList,
                PageRequest.of(request.getPage(), request.getSize()),
                searchHits.getTotalHits());
    }

    private ProductSearchVO convertToVO(SearchHit<EsProduct> hit) {
        EsProduct product = hit.getContent();
        ProductSearchVO vo = new ProductSearchVO();
        BeanUtils.copyProperties(product, vo);

        // 设置高亮字段
        Map<String, List<String>> highlights = hit.getHighlightFields();
        if (highlights.containsKey("name")) {
            vo.setHighlightName(highlights.get("name").get(0));
        }
        vo.setScore(hit.getScore());
        return vo;
    }
}
```

---

## 8. 测试与验证

```java
@SpringBootTest
class ProductSearchServiceTest {

    @Autowired
    private ProductSearchService searchService;

    @Test
    void testSearch() {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setKeyword("手机");
        request.setCategoryId(101);
        request.setPage(0);
        request.setSize(10);

        Page<ProductSearchVO> result = searchService.search(request);
        assertThat(result.getContent()).isNotEmpty();
        result.getContent().forEach(vo ->
            System.out.println(vo.getHighlightName() + " - " + vo.getPrice()));
    }
}
```

---

## 9. 总结

Spring Data Elasticsearch 提供了从实体映射、Repository 声明式查询到模板 API 的完整能力。实际项目中推荐组合使用：用 `@Document` 注解管理索引映射，用 `ElasticsearchRepository` 处理简单 CRUD，用 `ElasticsearchRestTemplate` 配合 `NativeSearchQuery` 实现复杂搜索、高亮和聚合。注意控制返回字段数量，避免深分页，结合索引生命周期管理保证搜索性能。