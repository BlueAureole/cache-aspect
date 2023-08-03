-- 此脚本删除键对应集合的交集，所指向的对象，入参## 需替换为KEYS[1],KEYS[2]

-- 除去两头的引号
local getKey = function(str)
  return string.sub(str,2,string.len(str)-1)
end
-- 传入的PK前缀
local startPkKey=getKey(ARGV[1])
local pkLength=string.len(startPkKey)
-- 求并集
local interSet=redis.call('sunion',#0#)
for k,v in ipairs(interSet) do
  -- 判断是否PK键，如果不是，则删除
  redis.log(redis.LOG_NOTICE,'k='..k..' v='..v)
  local oneKey=getKey(v)
  local oneStart=string.sub(oneKey,1,pkLength)
  redis.log(redis.LOG_NOTICE,'oneKey='..oneKey..' oneStart='..oneStart)
  
  if startPkKey ~= oneStart then
    redis.log(redis.LOG_NOTICE,'del : oneKey='..oneKey)
  	redis.call('del',oneKey)
  end
end
return 1