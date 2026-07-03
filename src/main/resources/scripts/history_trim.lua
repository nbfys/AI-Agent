-- 历史记录滑动窗口原子操作
-- 合规依据：PRD v3.0 分层记忆引擎 - Lua脚本保证RPUSH+LTRIM原子性
-- KEYS[1]: Redis key
-- ARGV[1]: message content
-- ARGV[2]: max history size
-- ARGV[3]: TTL in seconds

redis.call('RPUSH', KEYS[1], ARGV[1])
local size = redis.call('LLEN', KEYS[1])
if size > tonumber(ARGV[2]) then
    redis.call('LTRIM', KEYS[1], size - tonumber(ARGV[2]), -1)
end
redis.call('EXPIRE', KEYS[1], ARGV[3])
return redis.call('LLEN', KEYS[1])
