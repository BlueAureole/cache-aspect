-- 此脚本删除一个Dao下的所有缓存，入参为KeyList

-- 除去两头的引号
local getKey = function(str)
  return string.sub(str,2,string.len(str)-1)
end

for fkNum,oneFkName in ipairs(KEYS) do
-- 一个外键
  redis.log(redis.LOG_NOTICE,'fkNum='..fkNum..' oneFkName='..oneFkName)
  local oneFkNameValuesSet=redis.call('smembers',oneFkName)
  for fkValueNum,oneFkValue in ipairs(oneFkNameValuesSet) do
    --  外键的一个值
    oneFkValue=getKey(oneFkValue)
    redis.log(redis.LOG_NOTICE,'fkValueNum='..fkValueNum..' oneFkValue='..oneFkValue)
    local fkNameValueKeysSet=redis.call('smembers',oneFkValue)
    for oneFkValueObjNum,oneFkValueObjKey in ipairs(fkNameValueKeysSet) do
      -- 外键值的一个存储对象
      oneFkValueObjKey=getKey(oneFkValueObjKey)
      redis.log(redis.LOG_NOTICE,'del oneFkValueObjKey='..oneFkValueObjKey)
      redis.call('del',oneFkValueObjKey)
    end 
    redis.log(redis.LOG_NOTICE,'del oneFkValue='..oneFkValue)
    redis.call('del',oneFkValue)
  end
  redis.log(redis.LOG_NOTICE,'del oneFkName='..oneFkName)
  redis.call('del',oneFkName)
end
return 1