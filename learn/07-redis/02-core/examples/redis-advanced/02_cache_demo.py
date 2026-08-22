"""
Redis 缓存模式演示：Cache-Aside（旁路缓存）
============================================
流程：
  1. 读请求 → 查缓存（存在则直接返回）
  2. 缓存未命中 → 查数据库 → 写入缓存 → 返回
  3. 写请求 → 更新数据库 → 删除缓存（延迟双删可选）

运行前提：
  pip install redis pymysql
"""
import json
import time
import threading

import redis


class CacheAsidePattern:
    """缓存旁路模式实现"""

    def __init__(self, redis_client, cache_ttl=300):
        self.redis = redis_client
        self.cache_ttl = cache_ttl  # 缓存过期时间（秒）

    # ---------- 读操作：缓存优先 ----------
    def get_product(self, product_id: int) -> dict:
        """查询商品：先查缓存，未命中则查数据库并回填"""
        cache_key = f"product:{product_id}"

        # 1. 查缓存
        cached = self.redis.get(cache_key)
        if cached is not None:
            print(f"[缓存命中] key={cache_key}")
            return json.loads(cached)

        # 2. 缓存未命中，查数据库（模拟）
        print(f"[缓存未命中] 查询数据库 product_id={product_id}")
        product = self._query_db(product_id)
        if product is None:
            return None

        # 3. 回填缓存（设置过期时间，防止缓存雪崩）
        self.redis.setex(cache_key, self.cache_ttl, json.dumps(product, ensure_ascii=False))
        print(f"[缓存回填] key={cache_key}, ttl={self.cache_ttl}s")
        return product

    # ---------- 写操作：先更新数据库，再删除缓存 ----------
    def update_product(self, product_id: int, update_data: dict) -> bool:
        """更新商品：先更新数据库，再删除缓存"""
        cache_key = f"product:{product_id}"

        # 1. 更新数据库（模拟）
        print(f"[更新数据库] product_id={product_id}, data={update_data}")
        self._update_db(product_id, update_data)

        # 2. 删除缓存（让下次读取时回填新数据）
        # 为什么不更新缓存？因为并发写可能导致缓存与数据库不一致
        # 删除缓存后，下次读会重新从 DB 加载
        self.redis.delete(cache_key)
        print(f"[删除缓存] key={cache_key}")
        return True

    # ---------- 延迟双删（处理极端并发场景） ----------
    def update_product_with_double_delete(self, product_id: int, update_data: dict) -> bool:
        """
        延迟双删策略：
        1. 先删除缓存（让旧数据尽快失效）
        2. 更新数据库
        3. 延迟一段时间后再删除缓存（处理读请求在步骤1-2之间回填了旧数据）
        """
        cache_key = f"product:{product_id}"

        # 第 1 次删除
        self.redis.delete(cache_key)
        print(f"[第1次删除] key={cache_key}")

        # 更新数据库
        self._update_db(product_id, update_data)
        print(f"[更新数据库] product_id={product_id}")

        # 延迟 500ms 后第 2 次删除（模拟异步）
        def delayed_delete():
            time.sleep(0.5)
            self.redis.delete(cache_key)
            print(f"[第2次删除-延迟] key={cache_key}")

        threading.Thread(target=delayed_delete, daemon=True).start()
        return True

    # ---------- 模拟数据库操作 ----------
    def _query_db(self, product_id: int) -> dict | None:
        """模拟数据库查询"""
        fake_db = {
            1: {"id": 1, "name": "iPhone 15", "price": 6999, "stock": 100},
            2: {"id": 2, "name": "MacBook Pro", "price": 14999, "stock": 30},
        }
        return fake_db.get(product_id)

    def _update_db(self, product_id: int, data: dict):
        """模拟数据库更新"""
        # 实际项目中这里执行 SQL UPDATE
        pass


# ============ 缓存穿透/雪崩/击穿 防护 ============

def prevent_cache_penetration(client, key: str, db_lookup_func, ttl: int = 300):
    """
    缓存穿透防护：当查询不存在的数据时，缓存空值（短 TTL）
    问题：大量请求查询一个不存在的 key，永远打穿缓存到 DB
    解决：缓存空值（如 None），TTL 设为 60 秒
    """
    cached = client.get(key)
    if cached is not None:
        return json.loads(cached) if cached != "NULL" else None

    # 查数据库
    result = db_lookup_func()
    if result is None:
        # 缓存空值，TTL 短一些
        client.setex(key, 60, "NULL")
        return None

    client.setex(key, ttl, json.dumps(result))
    return result


# ============ 使用示例 ============
def demo():
    client = redis.Redis(host="localhost", port=6379, decode_responses=True)
    cache = CacheAsidePattern(client, cache_ttl=300)

    print("=== 第1次查询（缓存未命中）===")
    p1 = cache.get_product(1)
    print(f"结果: {p1}\n")

    print("=== 第2次查询（缓存命中）===")
    p2 = cache.get_product(1)
    print(f"结果: {p2}\n")

    print("=== 更新商品（删除缓存）===")
    cache.update_product(1, {"price": 6999, "stock": 99})

    print("=== 第3次查询（缓存已删除，重新加载）===")
    p3 = cache.get_product(1)
    print(f"结果: {p3}\n")

    # 清理
    client.delete("product:1")


if __name__ == "__main__":
    demo()