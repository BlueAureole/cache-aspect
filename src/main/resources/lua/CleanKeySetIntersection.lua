-- 此脚本删除键对应集合的交集，所指向的对象，入参## 需替换为KEYS[1],KEYS[2]
local interSet=redis.call('sinter',#0#)
for k,v in ipairs(interSet) do
redis.call('del',v)
end
return 1