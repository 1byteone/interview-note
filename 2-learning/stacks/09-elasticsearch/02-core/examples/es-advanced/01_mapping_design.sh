#!/bin/bash
# ============================================
# Elasticsearch Mapping 设计进阶
# 演示：动态映射 / 显式映射 / 嵌套对象 / Join 字段 / 分析器
# ============================================

ES_URL="http://localhost:9200"

echo "===== 1. 动态映射（Dynamic Mapping）====="
# ES 自动推断字段类型（适合快速原型，生产环境建议显式映射）
curl -s -X PUT "$ES_URL/dynamic-demo" -H 'Content-Type: application/json' -d '{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0 }
}'
# 插入文档触发自动映射
curl -s -X POST "$ES_URL/dynamic-demo/_doc/1" -H 'Content-Type: application/json' -d '{
  "name": "测试",
  "age": 25,
  "price": 99.99,
  "created_at": "2026-08-22"
}'
echo "查看自动生成的映射:"
curl -s "$ES_URL/dynamic-demo/_mapping" | python3 -m json.tool
echo ""

echo "===== 2. 显式映射（Explicit Mapping）====="
# 精确控制字段类型，避免默认推断错误（如日期字段被误判为 text）
curl -s -X PUT "$ES_URL/explicit-demo" -H 'Content-Type: application/json' -d '{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
  "mappings": {
    "dynamic": "strict",           # 严格模式：未知字段抛异常
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": { "type": "keyword" }  # 多字段：text 用于搜索，keyword 用于聚合
        }
      },
      "price": {
        "type": "scaled_float",    # 缩放浮点型（省空间，精确）
        "scaling_factor": 100
      },
      "status": {
        "type": "keyword"          # 枚举值用 keyword，不用 text
      },
      "stock": {
        "type": "integer"
      },
      "content": {
        "type": "text",
        "index": false             # 只存不索引（节省存储空间）
      },
      "created_at": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"
      }
    }
  }
}'
echo "显式映射已创建"
echo ""

echo "===== 3. 嵌套对象（Nested Object）====="
# 当数组包含对象时，默认的 object 类型会丢失关系（平铺扁平化）
# nested 类型保持内部对象的独立性
curl -s -X PUT "$ES_URL/orders-nested" -H 'Content-Type: application/json' -d '{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
  "mappings": {
    "properties": {
      "order_no": { "type": "keyword" },
      "items": {
        "type": "nested",          # nested 类型保持数组对象的独立性
        "properties": {
          "product": { "type": "keyword" },
          "quantity": { "type": "integer" },
          "price": { "type": "float" }
        }
      }
    }
  }
}'
# 插入嵌套文档
curl -s -X POST "$ES_URL/orders-nested/_doc/1" -H 'Content-Type: application/json' -d '{
  "order_no": "ORD202608220001",
  "items": [
    { "product": "iPhone 15", "quantity": 1, "price": 6999 },
    { "product": "T恤", "quantity": 2, "price": 99 }
  ]
}'
# 查询嵌套对象（必须用 nested 查询）
echo "搜索同时包含 iPhone 和 T恤 的订单:"
curl -s -X GET "$ES_URL/orders-nested/_search" -H 'Content-Type: application/json' -d '{
  "query": {
    "nested": {
      "path": "items",
      "query": {
        "bool": {
          "must": [
            { "term": { "items.product": "iPhone 15" } },
            { "term": { "items.product": "T恤" } }
          ]
        }
      }
    }
  }
}' | python3 -m json.tool
echo ""

echo "===== 4. Join 字段（父子关系）====="
# 模拟：问答系统（Question → Answer）
curl -s -X PUT "$ES_URL/qa-demo" -H 'Content-Type: application/json' -d '{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
  "mappings": {
    "properties": {
      "title": { "type": "text" },
      "body":  { "type": "text" },
      "my_join": {
        "type": "join",
        "relations": {
          "question": "answer"   # 父类型 question，子类型 answer
        }
      }
    }
  }
}'
# 插入父文档（问题）
curl -s -X POST "$ES_URL/qa-demo/_doc/1" -H 'Content-Type: application/json' -d '{
  "title": "ES 中 nested 和 join 的区别？",
  "body": "请问什么时候用 nested，什么时候用 join？",
  "my_join": { "name": "question" }
}'
echo "Join 父文档:"
curl -s "$ES_URL/qa-demo/_doc/1" | python3 -m json.tool
echo ""

echo "===== 5. 分析器（Analyzer）配置 ====="
# 自定义分析器：standard 分词 + 小写 + 停用词过滤
curl -s -X PUT "$ES_URL/analyzer-demo" -H 'Content-Type: application/json' -d '{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "my_custom_analyzer": {
          "type": "standard",
          "stopwords": "_english_"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "analyzer": "my_custom_analyzer"
      }
    }
  }
}'
echo "分析器测试:"
curl -s -X POST "$ES_URL/analyzer-demo/_analyze" -H 'Content-Type: application/json' -d '{
  "analyzer": "my_custom_analyzer",
  "text": "Elasticsearch is a powerful search engine"
}' | python3 -m json.tool
echo ""

echo "===== 清理 ====="
curl -s -X DELETE "$ES_URL/dynamic-demo" > /dev/null
curl -s -X DELETE "$ES_URL/explicit-demo" > /dev/null
curl -s -X DELETE "$ES_URL/orders-nested" > /dev/null
curl -s -X DELETE "$ES_URL/qa-demo" > /dev/null
curl -s -X DELETE "$ES_URL/analyzer-demo" > /dev/null
echo "临时索引已清理"