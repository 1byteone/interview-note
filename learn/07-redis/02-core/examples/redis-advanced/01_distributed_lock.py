"""
Redis 分布式锁 Python 实现
===========================
基于 Lua 脚本 + SET NX PX 实现：
  - 原子加锁（SET key token NX PX expires）
  - 原子释放（GET 校验 token 后 DEL）
  - 支持续期（后台线程）

运行前提：
  1. pip install redis
  2. Redis 服务已启动（默认 localhost:6379）
  3. python 01_distributed_lock.py
"""
import threading
import time
import uuid

import redis

# 锁对象封装类
class RedisDistributedLock:
    def __init__(self, redis_client, lock_key, timeout_ms=30000, renew_interval_ms=10000):
        """
        :param redis_client: redis 客户端（支持集群应使用 redis.cluster）
        :param lock_key: 锁的 key
        :param timeout_ms: 锁默认过期时间（毫秒）
        :param renew_interval_ms: 自动续期间隔（毫秒）
        """
        self.redis = redis_client
        self.lock_key = lock_key
        self.timeout_ms = timeout_ms
        self.renew_interval_ms = renew_interval_ms
        # 唯一标识：UUID 保证全局唯一
        self.token = str(uuid.uuid4())
        self._renew_thread = None
        self._lock_owned = False

    def acquire(self, wait_timeout_ms=0) -> bool:
        """获取锁（支持自旋等待）"""
        deadline = time.time() + wait_timeout_ms / 1000 if wait_timeout_ms > 0 else 0

        script = """
        if redis.call('set', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then
            return 1
        else
            return 0
        end
        """
        while True:
            # 返回 1 表示加锁成功（即使返回 0 也是可能 lock 不存在）
            result = self.redis.eval(script, 1, self.lock_key, self.token, self.timeout_ms)
            if result == 1:
                self._lock_owned = True
                self._start_renew()
                return True
            if deadline and time.time() >= deadline:
                return False
            time.sleep(0.05)  # 50ms 重试间隔（避免自旋打满 CPU）

    def release(self) -> bool:
        """释放锁（仅释放自己持有的锁）"""
        # Lua 脚本保证 检查token + 删除 原子性
        script = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
        else
            return 0
        end
        """
        self._stop_renew()
        self._lock_owned = False
        return self.redis.eval(script, 1, self.lock_key, self.token) == 1

    def _start_renew(self):
        """启动后台续期线程（守护线程）"""
        script = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('pexpire', KEYS[1], ARGV[2])
        else
            return 0
        end
        """
        def renew_loop():
            while self._lock_owned:
                time.sleep(self.renew_interval_ms / 1000)
                try:
                    self.redis.eval(script, 1, self.lock_key, self.token, self.timeout_ms)
                except redis.exceptions.RedisError:
                    pass  # 续期失败不中断锁

        if self._renew_thread is None:
            self._renew_thread = threading.Thread(target=renew_loop, daemon=True)
            self._renew_thread.start()

    def _stop_renew(self):
        self._lock_owned = False

    def __enter__(self):
        self.acquire()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.release()


# ============ 使用示例（模拟库存扣减） ============
def business_demo():
    client = redis.Redis(host="localhost", port=6379, decode_responses=True)

    # 初始化库存
    client.set("stock:1001", 10)

    # 多线程同时扣库存（无锁会有超卖问题）
    def deduct_without_lock():
        # 模拟：检查 + 扣减 非原子操作
        stock = int(client.get("stock:1001"))
        time.sleep(0.01)  # 模拟耗时，放大竞态
        if stock > 0:
            client.set("stock:1001", stock - 1)
            return 1
        return 0

    lock = RedisDistributedLock(client, "lock:stock:1001", timeout_ms=30000)

    def deduct_with_lock():
        # 带锁的扣库存，保证互斥
        with lock:
            stock = int(client.get("stock:1001"))
            time.sleep(0.01)
            if stock > 0:
                client.set("stock:1001", stock - 1)
                return 1
        return 0

    # 单线程测试（加锁/释放锁）
    print("=== 单线程加锁测试 ===")
    if lock.acquire():
        print(f"获取锁成功, token={lock.token}")
        print(f"锁 value: {client.get('lock:stock:1001')}")
        time.sleep(2)  # 模拟业务处理（后台线程续期）
        print(f"2秒后锁 TTL: {client.ttl('lock:stock:1001')}ms（已自动续期）")
    lock.release()
    print(f"释放后锁存在: {client.exists('lock:stock:1001')}")

    print("\n=== 多线程并发扣库存测试（10 个线程各扣 1 件）===")
    threads = []
    for i in range(10):
        t = threading.Thread(target=deduct_with_lock)
        threads.append(t)
        t.start()
    for t in threads:
        t.join()
    print(f"最终库存: {client.get('stock:1001')}（应为 0，无超卖）")

    # 清理
    client.delete("stock:1001", "lock:stock:1001")


if __name__ == "__main__":
    business_demo()
    print("\n演示完成")