#!/bin/bash
# ============================================
# Elasticsearch 搜索查询演示
# match / term / range / bool / 聚合 / 排序 / 分页
# 前提：先运行 01_index_and_crud.sh 写入数据
# ============================================

ES_URL="http://localhost:9200"

echo "===== 1. match 全文搜索 ====="
# 全文搜索：ES 会分析查询文本，按相关性打分
echo "搜索 '手机 旗舰':"
curl -s -X GET "$ES_URL/products/_search" -H 'Content-Type: application/json' -d '{
  "query": {
    "match": {
      "description": "手机 旗舰"
    }
  },
  "_source": ["name", "price", "category"],
  "size": 5
}' | python3 -m json.tool 2>/dev/null || curl -s "$ES_URL/products/_search?q=description:手机&pretty"
echo ""

echo "===== 2. term 精确查询 ====="
# term 用于 keyword 类型精确匹配，不分词
echo "查询 category=手机:"
curl -s -X GET "$ES_URL/products/_search" -H 'Content-Type: application/json' -d '{
  "query": {
    "term": {
      "category": "手机"
    }
  }
}' | python3 -m json.tool 2>/dev/null || curl -s "$ES_URL/products/_search?q=category:手机&pretty"
echo ""

echo "===== 3. range 范围查询 ====="
echo "价格 5000~10000 的商品:"
curl -s -X GET "$ES_URL/products/_search" -H 'Content-Type: application/json' -d '{
  "query": {
    "range": {
      "price": {
        "gte": 5000,
        "lte": 10000
      }
    }
  },
  "sort": [{ "price": "asc" }]
}' | python3 -m json.tool 2>/dev/null || curl -s "$ES_URL/products/_search?q=price:[5000 TO 10000]&sort=price:asc&pretty"
echo ""

echo "===== 4. bool 复合查询（must/should/filter）====="
# must = AND, should = OR, filter = AND（不计分，性能更好）
echo "查询 '手机' 类且价格 < 10000 的商品:"
curl -s -X GET "$ES_URL/products/_search" -H 'Content-Type: application/json' -d '{
  "query": {
    "bool": {
      "must": [
        { "match": { "name": "手机" } }
      ],
      "filter": [
        { "range": { "price": { "lte": 10000 } } }
      ]
    }
  }
}' | python3 -m json.tool
echo ""

echo "===== 5. 聚合（Aggregations）====="
# 类似 SQL 的 GROUP BY
echo "按分类统计商品数量:"
curl -s -X GET "$ES_URL/products/_search" -H 'Content-Type: application/json' -d '{
  "size": 0,
  "aggs": {
    "by_category": {
      "terms": { "field": "category", "size": 10 }
    },
    "avg_price": {
      "avg": { "field": "price" }
    }
  }
}' | python3 -m json.tool
echo ""

echo "===== 6. 排序 ====="
echo "按价格降序:"
curl -s -X GET "$ES_URL/products/_search" -H 'Content-Type: application/json' -d '{
  "query": { "match_all": {} },
  "sort": [
    { "price": { "order": "desc" } }
  ],
  "_source": ["name", "price"]
}' | python3 -m json.tool
echo ""

echo "===== 7. 分页 ====="
echo "第 1 页（size=2）:"
curl -s -X GET "$ES_URL/products/_search" -H 'Content-Type: application/json' -d '{
  "query": { "match_all": {} },
  "from": 0,
  "size": 2,
  "_source": ["name"]
}' | python3 -m json.tool
echo ""

echo "===== 8. 高亮显示 ====="
echo "搜索 '手机' 并高亮:"
curl -s -X GET "$ES_URL/products/_search" -H 'Content-Type: application/json' -d '{
  "query": {
    "match": { "name": "手机" }
  },
  "highlight": {
    "fields": {
      "name": {},
      "description": {}
    }
  }
}' | python3 -m json.tool
echo ""

echo "===== 演示完成 ====="