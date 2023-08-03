-- 此脚本删除键对应集合的交集，所指向的对象，入参## 需替换为KEYS[1],KEYS[2]

-- 除去两头的引号
local getKey = function(str)
  return string.sub(str,2,string.len(str)-1)
end

local interSet=redis.call('sunion',#0#)
for k,v in ipairs(interSet) do
  local oneObjKey=getKey(v)
  redis.log(redis.LOG_NOTICE,'del oneObjKey='..oneObjKey)
  redis.call('del',oneObjKey)
end
for keyNum,oneArgKey in ipairs(KEYS) do
  redis.log(redis.LOG_NOTICE,'del oneArgKey='..oneArgKey)
  redis.call('del',oneArgKey)
end
return 1