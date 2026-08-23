"""
Elasticsearch 数据同步脚本
===========================
功能：从 MySQL 读取数据，批量索引到 Elasticsearch
支持：全量同步 + 增量同步（基于时间戳）

运行前提：
  pip install elasticsearch pymysql
  ES 和 MySQL 已启动
"""
import datetime
import hashlib
import json
import logging
import time
from typing import Any, Generator

import pymysql
from elasticsearch import Elasticsearch, helpers

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger(__name__)

# ==================== 配置 ====================
ES_HOST = "http://localhost:9200"
MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root123",
    "database": "ecommerce",
    "charset": "utf8mb4",
}
SYNC_CONFIG = {
    "index_name": "products_fulltext",
    "batch_size": 500,  # 每批 500 条
    "last_sync_file": "/tmp/es_last_sync.txt",  # 增量同步标记文件
}


# ==================== MySQL 读取 ====================
def fetch_products(cursor, last_sync_time: str | None = None) -> Generator[dict, None, None]:
    """
    从 MySQL 读取商品数据
    :param last_sync_time: 增量同步时间戳，None 表示全量同步
    """
    if last_sync_time:
        sql = """
        SELECT id, name, description, price, stock, sales,
               category_id, image_url, status, created_at, updated_at
        FROM products
        WHERE updated_at > %s
        ORDER BY id
        """
        cursor.execute(sql, (last_sync_time,))
        log.info(f"增量同步: {last_sync_time} 之后更新的数据")
    else:
        sql = """
        SELECT id, name, description, price, stock, sales,
               category_id, image_url, status, created_at, updated_at
        FROM products
        ORDER BY id
        """
        cursor.execute(sql)
        log.info("全量同步: 读取所有数据")

    for row in cursor.fetchall():
        yield {
            "id": row[0],
            "name": row[1],
            "description": row[2],
            "price": float(row[3]) if row[3] else 0,
            "stock": row[4] or 0,
            "sales": row[5] or 0,
            "category_id": row[6],
            "image_url": row[7],
            "status": row[8] or 1,
            "created_at": row[9].strftime("%Y-%m-%d %H:%M:%S") if row[9] else None,
            "updated_at": row[10].strftime("%Y-%m-%d %H:%M:%S") if row[10] else None,
        }


# ==================== ES 批量索引 ====================
def create_index_if_not_exists(es: Elasticsearch, index_name: str):
    """创建索引（含 Mapping 和 Settings）"""
    if es.indices.exists(index=index_name):
        log.info(f"索引已存在: {index_name}")
        return

    # 定义索引 Mapping
    body = {
        "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "analysis": {
                "analyzer": {
                    "ik_smart_analyzer": {
                        "type": "standard"
                    }
                }
            }
        },
        "mappings": {
            "properties": {
                "id": {"type": "integer"},
                "name": {
                    "type": "text",
                    "analyzer": "standard",
                    "fields": {
                        "keyword": {"type": "keyword"}
                    }
                },
                "description": {"type": "text", "analyzer": "standard"},
                "price": {"type": "float"},
                "stock": {"type": "integer"},
                "sales": {"type": "integer"},
                "category_id": {"type": "integer"},
                "image_url": {"type": "keyword", "index": False},
                "status": {"type": "byte"},
                "created_at": {"type": "date", "format": "yyyy-MM-dd HH:mm:ss"},
                "updated_at": {"type": "date", "format": "yyyy-MM-dd HH:mm:ss"},
            }
        }
    }
    es.indices.create(index=index_name, body=body)
    log.info(f"索引已创建: {index_name}")


def bulk_index(es: Elasticsearch, index_name: str, products: list[dict]) -> int:
    """
    批量索引到 ES
    :return: 成功索引的数量
    """
    def generate_actions():
        for product in products:
            doc_id = product["id"]
            # 可以在这里做数据转换/增强
            doc = {
                "_index": index_name,
                "_id": doc_id,
                "_source": product,
            }
            yield doc

    success, errors = helpers.bulk(
        es,
        generate_actions(),
        chunk_size=500,
        raise_on_error=False,
        raise_on_exception=False,
    )
    if errors:
        log.warning(f"索引时出现 {len(errors)} 个错误")
        for err in errors[:3]:  # 只打印前 3 个错误
            log.warning(f"  {err}")

    return success


# ==================== 增量同步标记 ====================
def read_last_sync_time(filepath: str) -> str | None:
    """读取上次同步时间"""
    try:
        with open(filepath, "r") as f:
            return f.read().strip()
    except FileNotFoundError:
        return None


def write_last_sync_time(filepath: str, time_str: str):
    """写入本次同步时间"""
    with open(filepath, "w") as f:
        f.write(time_str)


# ==================== 主流程 ====================
def sync_data():
    log.info("===== 数据同步开始 =====")

    # 1. 连接 ES
    es = Elasticsearch(ES_HOST, request_timeout=30)
    if not es.ping():
        log.error("ES 无法连接")
        return
    log.info(f"ES 连接成功: {ES_HOST}")

    # 2. 创建索引
    create_index_if_not_exists(es, SYNC_CONFIG["index_name"])

    # 3. 连接 MySQL
    conn = pymysql.connect(**MYSQL_CONFIG)
    cursor = conn.cursor()

    try:
        # 4. 读取上次同步时间
        last_sync = read_last_sync_time(SYNC_CONFIG["last_sync_file"])
        if last_sync:
            log.info(f"上次同步时间: {last_sync}")
        else:
            log.info("首次同步: 执行全量同步")

        # 5. 分批读取并索引
        total_indexed = 0
        batch = []
        now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        for product in fetch_products(cursor, last_sync_time=last_sync):
            batch.append(product)
            if len(batch) >= SYNC_CONFIG["batch_size"]:
                count = bulk_index(es, SYNC_CONFIG["index_name"], batch)
                total_indexed += count
                log.info(f"  已同步 {total_indexed} 条...")
                batch = []

        # 处理最后一批
        if batch:
            count = bulk_index(es, SYNC_CONFIG["index_name"], batch)
            total_indexed += count

        log.info(f"===== 同步完成，共 {total_indexed} 条 =====")

        # 6. 保存同步时间
        write_last_sync_time(SYNC_CONFIG["last_sync_file"], now)
        log.info(f"同步时间戳已保存: {now}")

        # 7. 验证
        es_count = es.count(index=SYNC_CONFIG["index_name"])["count"]
        log.info(f"ES 索引文档数: {es_count}")

    finally:
        cursor.close()
        conn.close()


# ==================== 主入口 ====================
if __name__ == "__main__":
    sync_data()

    # 设置定时任务（crontab 示例，每 5 分钟同步一次）
    # crontab 配置:
    #   */5 * * * * cd /path/to/ && python3 03_data_sync.py >> /var/log/es-sync.log 2>&1