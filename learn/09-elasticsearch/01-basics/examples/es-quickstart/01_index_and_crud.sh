#!/bin/bash
# ============================================
# Elasticsearch 索引创建与 CRUD 操作
# 使用 curl 操作 ES REST API
# 前提：docker-compose up -d 启动 ES
# ============================================

ES_URL="http://localhost:9200"

echo "===== 1. 检查 ES 健康状态 ====="
curl -s "$ES_URL/_cat/health?v" | head -5
echo ""

echo "===== 2. 创建索引（带 Mapping 映射）====="
# 索引：products（商品）
# Mapping：定义字段类型，避免默认类型推断
curl -s -X PUT "$ES_URL/products" -H 'Content-Type: application/json' -d '{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "ik_analyzer": {
          "type": "standard"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id":         { "type": "integer" },
      "name":       { "type": "text", "analyzer": "standard" },
      "category":   { "type": "keyword" },
      "price":      { "type": "float" },
      "stock":      { "type": "integer" },
      "description":{"type": "text", "analyzer": "standard" },
      "tags":       { "type": "keyword" },
      "created_at": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" }
    }
  }
}'
echo ""

echo "===== 3. 创建文档（Index）====="
# 单条插入（指定 ID）
curl -s -X POST "$ES_URL/products/_doc/1" -H 'Content-Type: application/json' -d '{
  "id": 1,
  "name": "iPhone 15 Pro Max",
  "category": "手机",
  "price": 9999.00,
  "stock": 100,
  "description": "Apple 最新旗舰手机，A17 Pro 芯片，钛金属设计",
  "tags": ["apple", "手机", "旗舰"],
  "created_at": "2026-08-22 10:00:00"
}'
echo ""

curl -s -X POST "$ES_URL/products/_doc/2" -H 'Content-Type: application/json' -d '{
  "id": 2,
  "name": "MacBook Pro 14英寸",
  "category": "笔记本电脑",
  "price": 14999.00,
  "stock": 30,
  "description": "Apple M3 Pro 芯片，18GB 内存，512GB 存储",
  "tags": ["apple", "笔记本", "M3"],
  "created_at": "2026-08-22 10:01:00"
}'
echo ""

curl -s -X POST "$ES_URL/products/_doc/3" -H 'Content-Type: application/json' -d '{
  "id": 3,
  "name": "华为 Mate 60 Pro",
  "category": "手机",
  "price": 6999.00,
  "stock": 80,
  "description": "华为旗舰手机，卫星通话，昆仑玻璃",
  "tags": ["华为", "手机", "旗舰"],
  "created_at": "2026-08-22 10:02:00"
}'
echo ""

echo "===== 4. 批量插入（Bulk API）====="
# Bulk API 批量写入，性能更高
curl -s -X POST "$ES_URL/_bulk" -H 'Content-Type: application/json' -d '
{ "index": { "_index": "products", "_id": "4" } }
{ "id": 4, "name": "ThinkPad X1 Carbon", "category": "笔记本电脑", "price": 9999.00, "stock": 50, "description": "商务轻薄本，14英寸，Intel i7", "tags": ["联想", "笔记本", "商务"], "created_at": "2026-08-22 10:03:00" }
{ "index": { "_index": "products", "_id": "5" } }
{ "id": 5, "name": "纯棉T恤", "category": "服装", "price": 99.00, "stock": 500, "description": "男士纯棉短袖T恤，舒适透气", "tags": ["服装", "男装", "棉"], "created_at": "2026-08-22 10:04:00" }
{ "index": { "_index": "products", "_id": "6" } }
{ "id": 6, "name": "连衣裙", "category": "服装", "price": 199.00, "stock": 300, "description": "夏季新款碎花连衣裙，优雅时尚", "tags": ["服装", "女装", "裙子"], "created_at": "2026-08-22 10:05:00" }
'
echo ""

echo "===== 5. 查询文档（GET）====="
echo "查询 ID=1:"
curl -s "$ES_URL/products/_doc/1" | python3 -m json.tool 2>/dev/null || curl -s "$ES_URL/products/_doc/1"
echo ""

echo "===== 6. 更新文档（Update）====="
curl -s -X POST "$ES_URL/products/_doc/1/_update" -H 'Content-Type: application/json' -d '{
  "doc": {
    "price": 9499.00,
    "stock": 95
  }
}'
echo "更新后:"
curl -s "$ES_URL/products/_doc/1?_source=id,name,price,stock" | python3 -m json.tool 2>/dev/null || curl -s "$ES_URL/products/_doc/1?_source=id,name,price,stock"
echo ""

echo "===== 7. 删除文档（Delete）====="
curl -s -X DELETE "$ES_URL/products/_doc/6"
echo ""

echo "===== 8. 搜索（Search）====="
# 简单全文搜索
echo "搜索 '手机':"
curl -s -X GET "$ES_URL/products/_search" -H 'Content-Type: application/json' -d '{
  "query": {
    "match": {
      "name": "手机"
    }
  }
}' | python3 -m json.tool 2>/dev/null || curl -s -X GET "$ES_URL/products/_search" -H 'Content-Type: application/json' -d '{"query":{"match":{"name":"手机"}}}'
echo ""

echo "===== 演示完成 ====="