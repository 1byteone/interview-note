#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
AI Mall — MySQL 商品数据同步到 Elasticsearch 脚本
=============================================================================
功能：
  1. 从 MySQL 读取 products 表数据
  2. 在 Elasticsearch 中创建索引（若不存在）
  3. 批量（bulk）索引文档到 ES
  4. 支持增量同步（--since 参数）

用法：
  # 全量同步（首次）
  python sync-products-to-es.py

  # 指定 MySQL 和 ES 连接参数
  python sync-products-to-es.py --mysql-host 127.0.0.1 --mysql-user mall_user --mysql-password mall_pass_2024 --es-host http://localhost:9200

  # 增量同步（只同步最近变更）
  python sync-products-to-es.py --since "2026-08-01 00:00:00"

  # 从容器内执行
  docker compose exec ai-search-service python /app/sync-products-to-es.py

依赖：
  pip install pymysql elasticsearch
=============================================================================
"""

import argparse
import json
import logging
import sys
from datetime import datetime, timezone
from typing import Any, Dict, Generator, List, Optional, Tuple

import pymysql
from elasticsearch import Elasticsearch, helpers

# ----------------------------------------------------------------------------
# 日志配置
# ----------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("sync-products")

# ----------------------------------------------------------------------------
# 默认连接参数
# ----------------------------------------------------------------------------
DEFAULT_MYSQL_HOST = "127.0.0.1"
DEFAULT_MYSQL_PORT = 3306
DEFAULT_MYSQL_USER = "mall_user"
DEFAULT_MYSQL_PASSWORD = "mall_pass_2024"
DEFAULT_MYSQL_DATABASE = "ai_mall"

DEFAULT_ES_HOST = "http://localhost:9200"
DEFAULT_ES_INDEX = "ai_mall_products"

# 每次批量写入的文档数
BULK_BATCH_SIZE = 500


# ============================================================================
# ES 索引映射定义 (Mapping)
# 说明：默认使用标准分析器，开箱即用，无需安装 IK 分词插件。
#       如需中文 IK 分词，请取消下方 "analysis" 段落注释，并在 ES 中
#       安装 analysis-ik 插件（elasticsearch-plugin install https://...ik.zip）。
# ============================================================================
PRODUCT_INDEX_MAPPING = {
    "settings": {
        "index": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "refresh_interval": "30s",
        },
        # 如需 IK 中文分词，启用此段，并将下方 name/subtitle/description 的
        # analyzer 改为 "ik_max_word" / "ik_smart"
        # "analysis": {
        #     "analyzer": {
        #         "ik_smart_analyzer": {"type": "custom", "tokenizer": "ik_smart"},
        #         "ik_max_word_analyzer": {"type": "custom", "tokenizer": "ik_max_word"},
        #     },
        # },
    },
    "mappings": {
        "dynamic": "strict",
        "properties": {
            # 商品基础信息
            "id": {"type": "long"},
            "category_id": {"type": "long"},
            "category_name": {"type": "keyword"},
            "name": {
                "type": "text",
                # "analyzer": "ik_max_word",
                # "search_analyzer": "ik_smart",
                "fields": {
                    "keyword": {"type": "keyword", "ignore_above": 256},
                },
            },
            "subtitle": {
                "type": "text",
                # "analyzer": "ik_max_word",
                # "search_analyzer": "ik_smart",
            },
            "description": {
                "type": "text",
                # "analyzer": "ik_max_word",
                # "search_analyzer": "ik_smart",
            },
            "brand": {
                "type": "keyword",
                "fields": {
                    "text": {"type": "text"},
                },
            },
            # 价格与库存
            "price": {"type": "double"},
            "discount_price": {"type": "double"},
            "stock": {"type": "integer"},
            "sales": {"type": "integer"},
            "rating": {"type": "float"},
            # 状态与标签
            "status": {"type": "byte"},
            "is_new": {"type": "boolean"},
            "is_hot": {"type": "boolean"},
            "is_recommended": {"type": "boolean"},
            "tags": {"type": "keyword"},
            # 图片
            "thumbnail": {"type": "keyword", "index": False},
            "images": {"type": "keyword", "index": False},
            # 商品属性（扁平化为 key-value 对）
            "attributes": {"type": "flattened"},
            # 时间戳
            "created_at": {"type": "date", "format": "yyyy-MM-dd HH:mm:ss"},
            "updated_at": {"type": "date", "format": "yyyy-MM-dd HH:mm:ss"},
            # 向量字段（预留，后续可用 embedding 模型填充）
            "embedding": {
                "type": "dense_vector",
                "dims": 768,
                "index": True,
                "similarity": "cosine",
            },
        },
    },
}


def parse_args() -> argparse.Namespace:
    """解析命令行参数"""
    parser = argparse.ArgumentParser(
        description="AI Mall - 商品数据从 MySQL 同步到 Elasticsearch",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  %(prog)s                                          # 全量同步，默认连接
  %(prog)s --since "2026-08-01 00:00:00"            # 增量同步
  %(prog)s --mysql-host 192.168.1.100 --es-host http://192.168.1.200:9200
  %(prog)s --rebuild                                 # 重建索引
        """,
    )

    # MySQL 连接参数
    parser.add_argument("--mysql-host", default=DEFAULT_MYSQL_HOST, help=f"MySQL 主机地址 (默认: {DEFAULT_MYSQL_HOST})")
    parser.add_argument("--mysql-port", type=int, default=DEFAULT_MYSQL_PORT, help=f"MySQL 端口 (默认: {DEFAULT_MYSQL_PORT})")
    parser.add_argument("--mysql-user", default=DEFAULT_MYSQL_USER, help=f"MySQL 用户名 (默认: {DEFAULT_MYSQL_USER})")
    parser.add_argument("--mysql-password", default=DEFAULT_MYSQL_PASSWORD, help=f"MySQL 密码 (默认: {DEFAULT_MYSQL_PASSWORD})")
    parser.add_argument("--mysql-database", default=DEFAULT_MYSQL_DATABASE, help=f"MySQL 数据库名 (默认: {DEFAULT_MYSQL_DATABASE})")

    # ES 连接参数
    parser.add_argument("--es-host", default=DEFAULT_ES_HOST, help=f"Elasticsearch 地址 (默认: {DEFAULT_ES_HOST})")
    parser.add_argument("--es-index", default=DEFAULT_ES_INDEX, help=f"ES 索引名 (默认: {DEFAULT_ES_INDEX})")

    # 同步选项
    parser.add_argument("--since", default=None, help="增量同步：只同步 updated_at >= 此时间的数据 (格式: YYYY-MM-DD HH:mm:ss)")
    parser.add_argument("--rebuild", action="store_true", help="重建索引（删除旧索引后重新创建）")
    parser.add_argument("--batch-size", type=int, default=BULK_BATCH_SIZE, help=f"每批写入文档数 (默认: {BULK_BATCH_SIZE})")
    parser.add_argument("--dry-run", action="store_true", help="仅打印 SQL 查询，不实际写入 ES")

    return parser.parse_args()


def get_db_connection(args: argparse.Namespace) -> pymysql.Connection:
    """建立 MySQL 数据库连接"""
    try:
        conn = pymysql.connect(
            host=args.mysql_host,
            port=args.mysql_port,
            user=args.mysql_user,
            password=args.mysql_password,
            database=args.mysql_database,
            charset="utf8mb4",
            cursorclass=pymysql.cursors.DictCursor,
            # 连接池参数
            connect_timeout=10,
            read_timeout=30,
        )
        logger.info("✅ MySQL 连接成功: %s:%s/%s", args.mysql_host, args.mysql_port, args.mysql_database)
        return conn
    except pymysql.Error as e:
        logger.error("❌ MySQL 连接失败: %s", e)
        sys.exit(1)


def get_es_client(args: argparse.Namespace) -> Elasticsearch:
    """建立 Elasticsearch 客户端连接"""
    try:
        es = Elasticsearch(
            [args.es_host],
            request_timeout=30,
            max_retries=3,
            retry_on_timeout=True,
        )
        # 测试连接
        info = es.info()
        cluster_name = info.get("cluster_name", "unknown")
        es_version = info.get("version", {}).get("number", "unknown")
        logger.info("✅ Elasticsearch 连接成功: %s (v%s, cluster: %s)", args.es_host, es_version, cluster_name)
        return es
    except Exception as e:
        logger.error("❌ Elasticsearch 连接失败: %s", e)
        sys.exit(1)


def ensure_index(es: Elasticsearch, index_name: str, rebuild: bool = False):
    """确保 ES 索引存在，若不存在则创建"""
    if rebuild:
        if es.indices.exists(index=index_name):
            es.indices.delete(index=index_name)
            logger.info("🗑️  删除旧索引: %s", index_name)
        es.indices.create(index=index_name, body=PRODUCT_INDEX_MAPPING)
        logger.info("📦 创建索引: %s (shards=%s, replicas=%s)",
                     index_name,
                     PRODUCT_INDEX_MAPPING["settings"]["index"]["number_of_shards"],
                     PRODUCT_INDEX_MAPPING["settings"]["index"]["number_of_replicas"])
        return

    if not es.indices.exists(index=index_name):
        es.indices.create(index=index_name, body=PRODUCT_INDEX_MAPPING)
        logger.info("📦 创建索引: %s", index_name)
    else:
        logger.info("📦 索引已存在: %s", index_name)


def fetch_products(conn: pymysql.Connection, since: Optional[str] = None) -> Generator[Dict[str, Any], None, None]:
    """从 MySQL 读取商品数据，使用生成器逐批返回"""
    # 加载分类名称映射
    category_map = fetch_category_map(conn)

    query = """
        SELECT
            p.id,
            p.category_id,
            p.name,
            p.subtitle,
            p.description,
            p.brand,
            p.price,
            p.discount_price,
            p.stock,
            p.sales,
            p.rating,
            p.status,
            p.is_new,
            p.is_hot,
            p.is_recommended,
            p.tags,
            p.thumbnail,
            p.images,
            p.attributes,
            p.created_at,
            p.updated_at
        FROM products p
        WHERE p.status = 1
    """
    params: List[Any] = []

    if since:
        query += " AND p.updated_at >= %s"
        params.append(since)
        logger.info("🔍 增量同步模式: updated_at >= %s", since)

    query += " ORDER BY p.id ASC"

    with conn.cursor() as cursor:
        cursor.execute(query, params)
        logger.info("📊 SQL 查询执行完成, 准备读取结果...")

        while True:
            rows = cursor.fetchmany(size=100)
            if not rows:
                break

            for row in rows:
                product = _transform_product(row, category_map)
                yield product


def fetch_category_map(conn: pymysql.Connection) -> Dict[int, str]:
    """获取分类 ID → 名称的映射"""
    with conn.cursor() as cursor:
        cursor.execute("SELECT id, name FROM categories")
        return {row["id"]: row["name"] for row in cursor.fetchall()}


def _transform_product(row: Dict[str, Any], category_map: Dict[int, str]) -> Dict[str, Any]:
    """将 MySQL 行数据转换为 ES 文档格式"""
    product = {
        "id": row["id"],
        "category_id": row["category_id"],
        "category_name": category_map.get(row["category_id"], ""),
        "name": row["name"],
        "subtitle": row["subtitle"] or "",
        "description": row["description"] or "",
        "brand": row["brand"] or "",
        "price": float(row["price"]) if row["price"] else 0.0,
        "discount_price": float(row["discount_price"]) if row["discount_price"] else None,
        "stock": row["stock"] or 0,
        "sales": row["sales"] or 0,
        "rating": float(row["rating"]) if row["rating"] else 5.0,
        "status": row["status"],
        "is_new": bool(row["is_new"]),
        "is_hot": bool(row["is_hot"]),
        "is_recommended": bool(row["is_recommended"]),
        "tags": json.loads(row["tags"]) if row["tags"] else [],
        "thumbnail": row["thumbnail"] or "",
        "images": json.loads(row["images"]) if row["images"] else [],
        "attributes": json.loads(row["attributes"]) if row["attributes"] else {},
        "created_at": row["created_at"].strftime("%Y-%m-%d %H:%M:%S") if row["created_at"] else None,
        "updated_at": row["updated_at"].strftime("%Y-%m-%d %H:%M:%S") if row["updated_at"] else None,
    }
    return product


def generate_actions(products: Generator[Dict[str, Any], None, None],
                     index_name: str) -> Generator[Dict[str, Any], None, None]:
    """生成 ES bulk API 所需的 action 列表"""
    for product in products:
        yield {
            "_index": index_name,
            "_id": product["id"],
            "_source": product,
        }


def bulk_index(es: Elasticsearch, actions: Generator[Dict[str, Any], None, None],
               batch_size: int) -> Tuple[int, int]:
    """批量写入 ES，返回 (成功数, 失败数)"""
    success_count = 0
    error_count = 0

    try:
        success, errors = helpers.bulk(
            es,
            actions,
            chunk_size=batch_size,
            raise_on_error=False,
            request_timeout=60,
        )
        success_count = success
        if errors:
            error_count = len(errors)
            # 只记录前 5 个错误
            for i, err in enumerate(errors[:5]):
                logger.error("  ❌ 写入失败: %s", err.get("index", {}).get("error", err))
    except Exception as e:
        logger.error("❌ Bulk 写入异常: %s", e)
        error_count = -1

    return success_count, error_count


def print_summary(success: int, errors: int, elapsed: float):
    """打印同步结果摘要"""
    logger.info("=" * 60)
    logger.info("📋 同步完成摘要")
    logger.info("   ⏱  耗时: %.2f 秒", elapsed)
    logger.info("   ✅ 成功: %d 条", success)
    if errors > 0:
        logger.info("   ❌ 失败: %d 条", errors)
    logger.info("   📈 速率: %.0f 条/秒", success / elapsed if elapsed > 0 else 0)
    logger.info("=" * 60)


def main():
    """主函数"""
    args = parse_args()
    start_time = datetime.now()

    logger.info("=" * 60)
    logger.info("🚀 AI Mall 商品数据同步开始")
    logger.info("   MySQL: %s:%s/%s", args.mysql_host, args.mysql_port, args.mysql_database)
    logger.info("   ES:    %s/%s", args.es_host, args.es_index)
    if args.dry_run:
        logger.info("   🏃  模拟运行模式 (dry-run)")
    logger.info("=" * 60)

    # 1. 建立数据库连接
    db_conn = get_db_connection(args)
    es_client = get_es_client(args)

    try:
        # 2. 确保 ES 索引存在
        if not args.dry_run:
            ensure_index(es_client, args.es_index, rebuild=args.rebuild)

        # 3. 读取商品数据
        products = fetch_products(db_conn, since=args.since)

        if args.dry_run:
            # 模拟模式：只统计数量
            count = 0
            for _ in products:
                count += 1
            elapsed = (datetime.now() - start_time).total_seconds()
            logger.info("🏃 [DRY-RUN] 共读取 %d 条商品数据（未写入 ES）", count)
            print_summary(count, 0, elapsed)
            return

        # 4. 批量写入 ES
        actions = generate_actions(products, args.es_index)
        success, errors = bulk_index(es_client, actions, args.batch_size)

        # 5. 刷新索引（使文档立即可搜索）
        if success > 0:
            es_client.indices.refresh(index=args.es_index)
            # 获取索引文档数
            count = es_client.count(index=args.es_index)
            logger.info("📊 索引 %s 当前文档数: %s", args.es_index, count.get("count", "?"))

        elapsed = (datetime.now() - start_time).total_seconds()
        print_summary(success, errors, elapsed)

        if errors > 0:
            sys.exit(1)

    except Exception as e:
        logger.error("❌ 同步过程中发生异常: %s", e, exc_info=True)
        sys.exit(1)
    finally:
        db_conn.close()
        logger.info("🔌 数据库连接已关闭")


if __name__ == "__main__":
    main()