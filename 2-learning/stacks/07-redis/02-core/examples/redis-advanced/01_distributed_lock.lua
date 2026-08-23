-- ============================================
-- Redis 分布式锁 Lua 脚本（含加锁 + 释放）
-- 特性：
--   1. SET key value NX PX 原子加锁（避免竞态）
--   2. value 使用客户端唯一标识，防止误删他人锁
--   3. Lua 保证"检查 + 删除"原子性（释放锁时对比 value）
-- ============================================

-- ================= 加锁脚本（acquire.lua） =================
-- 执行：
--   EVAL "$(cat acquire.lua)" 1 lock:key unique_token 30000
-- 或（推荐，避免每次编译 Lua）：
--   SCRIPT LOAD "$(cat acquire.lua)"   → 返回 sha
--   EVALSHA <sha> 1 lock:key unique_token 30000
--
-- KEYS[1] = 锁的 key
-- ARGV[1] = 客户端唯一标识（如 UUID + 线程ID）
-- ARGV[2] = 锁自动过期时间（毫秒），防止死锁

local key       = KEYS[1]
local token     = ARGV[1]
local expires   = ARGV[2]

if redis.call('set', key, token, 'NX', 'PX', expires) then
    -- 加锁成功，返回 1
    return 1
else
    -- 锁被其他客户端持有，返回 0
    return 0
end


-- ================= 释放锁脚本（release.lua） =================
-- 执行：
--   EVAL "$(cat release.lua)" 1 lock:key unique_token
--
-- KEYS[1] = 锁的 key
-- ARGV[1] = 客户端唯一标识
-- 只有 value 匹配（锁是自己持有的）才执行 DEL，防止误删他人锁

local key     = KEYS[1]
local token   = ARGV[1]

-- 原子操作：检查当前持有者是否为调用方
if redis.call('get', key) == token then
    -- 是自己的锁，删除并返回 1
    return redis.call('del', key)
else
    -- 锁已过期被他人重新获取或已被释放，返回 0
    return 0
end


-- ================= 续期脚本（renew.lua，可选） =================
-- 用于长时间任务自动续期，防止锁在任务执行中过期
-- KEYS[1] = 锁的 key，ARGV[1] = 唯一标识，ARGV[2] = 追加毫秒数

local key     = KEYS[1]
local token   = ARGV[1]

if redis.call('get', key) == token then
    return redis.call('pexpire', key, ARGV[2])
else
    return 0
end