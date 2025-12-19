-- KEYS[1] = token key
-- ARGV[1] = max tokens
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = current timestamp (seconds)

local bucket = redis.call("HMGET", KEYS[1], "tokens", "timestamp")

local tokens = tonumber(bucket[1])
local last_ts = tonumber(bucket[2])

if tokens == nil then
    tokens = tonumber(ARGV[1])
    last_ts = tonumber(ARGV[3])
end

local now = tonumber(ARGV[3])
local delta = math.max(0, now - last_ts)
local refill = delta * tonumber(ARGV[2])

tokens = math.min(tonumber(ARGV[1]), tokens + refill)

if tokens < 1 then
    return 0
else
    tokens = tokens - 1
    redis.call("HMSET", KEYS[1], "tokens", tokens, "timestamp", now)
    redis.call("EXPIRE", KEYS[1], 3600)
    return 1
end
