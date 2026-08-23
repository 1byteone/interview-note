#!/bin/bash
# ============================================
# Redis 基础数据类型操作演示
# 启动 Redis 后执行：bash 01_basic_ops.sh
# ============================================

# 使用前确保 Redis 已启动
REDIS_CLI="docker exec -i redis-quickstart redis-cli"

echo "===== 1. String（字符串）====="
$REDIS_CLI SET user:1:name "Alice"
$REDIS_CLI SET user:1:email "alice@example.com"
$REDIS_CLI SET user:1:login_count 0
# 原子计数
$REDIS_CLI INCR user:1:login_count
$REDIS_CLI INCRBY user:1:login_count 5
# 设置过期时间（秒）
$REDIS_CLI SETEX session:token:abc 3600 "user_1_session"
echo "Token TTL: $($REDIS_CLI TTL session:token:abc) 秒"
# 批量操作
$REDIS_CLI MGET user:1:name user:1:email user:1:login_count
echo ""

echo "===== 2. List（列表 — 消息队列场景）====="
$REDIS_CLI LPUSH queue:messages "msg1" "msg2" "msg3"
$REDIS_CLI RPUSH queue:messages "msg4"
echo "List 长度: $($REDIS_CLI LLEN queue:messages)"
echo "LRANGE 0 -1: $($REDIS_CLI LRANGE queue:messages 0 -1)"
# 阻塞弹出（模拟消费者）
$REDIS_CLI BLPOP queue:messages 1
echo ""

echo "===== 3. Hash（哈希 — 对象存储）====="
$REDIS_CLI HSET product:1 name "iPhone 15" price 6999 stock 100
$REDIS_CLI HSET product:1 category "手机"
echo "所有字段:"
$REDIS_CLI HGETALL product:1
echo "价格: $($REDIS_CLI HGET product:1 price)"
echo "库存自减: $($REDIS_CLI HINCRBY product:1 stock -1)"
echo ""

echo "===== 4. Set（集合 — 标签/关系）====="
$REDIS_CLI SADD user:1:tags "developer" "java" "spring"
$REDIS_CLI SADD user:2:tags "developer" "python" "fastapi"
# 交集（共同标签）
echo "共同标签: $($REDIS_CLI SINTER user:1:tags user:2:tags)"
# 并集
echo "所有标签: $($REDIS_CLI SUNION user:1:tags user:2:tags)"
# 差集
echo "user:1 独有: $($REDIS_CLI SDIFF user:1:tags user:2:tags)"
echo ""

echo "===== 5. ZSet（有序集合 — 排行榜）====="
$REDIS_CLI ZADD leaderboard:game1 1000 "Alice"
$REDIS_CLI ZADD leaderboard:game1 850 "Bob"
$REDIS_CLI ZADD leaderboard:game1 1200 "Carol"
$REDIS_CLI ZADD leaderboard:game1 950 "Dave"
echo "排行榜（降序）:"
$REDIS_CLI ZREVRANGE leaderboard:game1 0 -1 WITHSCORES
echo "Alice 排名: $($REDIS_CLI ZREVRANK leaderboard:game1 Alice)"
echo ""

echo "===== 6. 过期与键管理 ====="
$REDIS_CLI SET temp:key "will expire" EX 10
echo "temp:key TTL: $($REDIS_CLI TTL temp:key)"
echo "所有键:"
$REDIS_CLI KEYS "*"
echo ""

echo "===== 7. 事务（MULTI/EXEC）====="
$REDIS_CLI MULTI
$REDIS_CLI INCR counter:tx
$REDIS_CLI INCRBY counter:tx 10
$REDIS_CLI GET counter:tx
# 执行事务
$REDIS_CLI EXEC
echo "计数器最终值: $($REDIS_CLI GET counter:tx)"
echo ""

echo "===== 清理演示数据 ====="
$REDIS_CLI FLUSHDB
echo "演示完成"