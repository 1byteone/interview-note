-- ============================================
-- Redis 滑动窗口限流器（Lua 脚本实现）
-- 原理：使用 ZSet 记录每个请求的时间戳，过期窗口外的自动清理
-- 用法：
--   EVAL "$(cat rate_limiter.lua)" 1 rate_limit:api:user1 10 1000
--   key = rate_limit:api:<user_id>
--   ARGV[1] = 窗口内最大请求数（limit）
--   ARGV[2] = 窗口大小（毫秒）
-- 返回：1=允许通过，0=限流拒绝
-- ============================================

-- 参数
local key       = KEYS[1]           -- 限流 key，如 "rate_limit:api:user1"
local limit     = tonumber(ARGV[1]) -- 窗口内最大请求次数
local window_ms = tonumber(ARGV[2]) -- 窗口大小（毫秒）

-- 当前时间戳（毫秒）
local now = redis.call('TIME')       -- Redis 返回 [秒, 微秒]
local now_ms = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)

-- 窗口起始时间
local window_start = now_ms - window_ms

-- 1. 清理窗口外过期的记录（防止 ZSet 无限增长）
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- 2. 统计当前窗口内的请求数
local current_count = redis.call('ZCARD', key)

-- 3. 判断是否超过限制
if current_count >= limit then
    -- 限流：拒绝请求
    return 0
end

-- 4. 记录当前请求
redis.call('ZADD', key, now_ms, now_ms)

-- 5. 设置 key 的过期时间（避免内存泄漏）
--   过期时间 = 窗口大小 + 10 秒缓冲
redis.call('PEXPIRE', key, window_ms + 10000)

-- 允许通过
return 1


-- ============================================
-- 测试示例（Redis CLI 中执行）：
-- ============================================
-- # 允许每秒最多 3 次请求（窗口 1000ms）
-- EVALSHA <sha> 1 rate_limit:api:alice 3 1000
-- # 连续执行 5 次，前 3 次返回 1，后 2 次返回 0
--
-- # 查看当前窗口内的请求
-- ZRANGE rate_limit:api:alice 0 -1 WITHSCORES
--
-- # 删除限流记录
-- DEL rate_limit:api:alice