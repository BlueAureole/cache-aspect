package wiki.zsjw.common.cacheaspect;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.github.pagehelper.PageInfo;

import wiki.zsjw.common.cacheaspect.CacheConfigModel.KeysInfo;
import wiki.zsjw.common.crudbase.BaseModel;
import wiki.zsjw.common.tools.extension.ObjectReflect;
import wiki.zsjw.common.tools.persistent.FileTool;

/**
* @author zhanghaiting
* @date 2019年10月29日
*/
public class DaoAspect {
	
	Logger logger  =  LoggerFactory.getLogger(DaoAspect.class);

    @Resource(name = "defaultRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;
    

	static final long CACHE_TIME=24*60*60L;//24小时(单位秒)。


    
    /**
     * ===================================================================================================
     * -----------------------------------  QueryAspect start --------------------------------------------
     * ===================================================================================================
     */
    
    /**
     * ==================== 主键查询 ==================== 
     */
	
	@Around("selectByPrimaryPoint()")
	private BaseModel<?> aroundSelectByPrimary(ProceedingJoinPoint pjp) throws Throwable{

		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(pjp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect){
			return (BaseModel<?>) pjp.proceed();
		}
		
		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		BaseModel<?> argModel=(BaseModel<?>) pjp.getArgs()[0];
		if(null==argModel||null==argModel.getId()) {
			return null;
		}
		String objectPk=CacheKeyAssist.getPkKey(daoKey, argModel.getId());
		
		//尝试从缓存取值
		//Object cacheObject=cache.get(objectPk);
		Object cacheObject=redisTemplate.opsForValue().get(objectPk);
		if(null!=cacheObject) {
			return (BaseModel<?>) cacheObject;
		}
		
		//从库取值
		Object dbObject = pjp.proceed();
		if(null==dbObject){
			return null;
		}
		
		//放入缓存
		BaseModel<?> dbModel=(BaseModel<?>) dbObject;
		final String [] foreignKeyArray=cacheConfig.getForeignKeyArray();
		List<BaseModel<?>> dbList=new ArrayList<>();
		dbList.add(dbModel);
		cacheListForPk(daoKey, foreignKeyArray, dbList);
		
		
		return dbModel;
		
	}
	
	
    /**
     * ==================== 主键列表查询 ==================== 
     */
	@SuppressWarnings("unchecked")
	@Around("selectInPkListPoint()")
	private List<BaseModel<?>> aroundSelectInPkList(ProceedingJoinPoint pjp) throws Throwable{

		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(pjp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect){
			return (List<BaseModel<?>>) pjp.proceed();
		}
		

		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		final String [] foreignKeyArray=cacheConfig.getForeignKeyArray();
		final List<BaseModel<?>> modelList=(List<BaseModel<?>>) pjp.getArgs()[0];
		if(null==modelList||0==modelList.size()) {
			return modelList;
		}
		
		//查询缓存记录
	    @SuppressWarnings("rawtypes")
		SessionCallback queryPkListFun = (SessionCallback)(RedisOperations ops)-> {
	    	ValueOperations<String, Object> opsForValue=ops.opsForValue();
			for (int i = 0; i < modelList.size(); i++) {
				BaseModel<?> oneModel=modelList.get(i);
				String objectPk=CacheKeyAssist.getPkKey(daoKey, oneModel.getId());
				opsForValue.get(objectPk);
			}
			return null;
	    }; 
	    List<Object> cacheMixtureList= redisTemplate.executePipelined(queryPkListFun);
		@SuppressWarnings("rawtypes")
		Map<Object,BaseModel> cachedMap =(Map<Object, BaseModel>) cacheMixtureList.stream()
				.filter(o -> null!=o)
				.collect(Collectors.toMap(o -> ((BaseModel)o).getId(), o ->(BaseModel)o, (v1, v2) -> v2));
		

		//过滤出不在缓存里的对象列表
		List<BaseModel<?>> unCachedList=modelList.stream()
				.filter(m -> !cachedMap.containsKey(m.getId()))
				.collect(Collectors.toList());
		
		
		//没有缓存的对象列表查库
		List<BaseModel<?>> dbList=new ArrayList<>();
		if(unCachedList.size()>0) {
			dbList=(List<BaseModel<?>>) pjp.proceed(new Object[] {unCachedList});
			
			//对象列表放入缓存
			if(dbList.size()>0) {
				cacheListForPk(daoKey, foreignKeyArray, dbList);
			}
		}
		
		
		//合并数据
		List<BaseModel<?>> allList=new ArrayList<>();
	    for (Object cacheId : cachedMap.keySet()) {
	    	allList.add(cachedMap.get(cacheId));
		}
	    allList.addAll(dbList);
	    
	    
		return allList;
	}

    /**
     * ==================== 外键条件单个查询 ==================== 
     */
	@Around("selectByUniquePoint()")
	private BaseModel<?> aroundSelectByUnique(ProceedingJoinPoint pjp) throws Throwable{
		
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(pjp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect){
			return (BaseModel<?>) pjp.proceed();
		}
		

		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		Signature sig = pjp.getSignature();
		String methodName=sig.getName();
		Object targetObj= pjp.getTarget();
		BaseModel<?> argModel=(BaseModel<?>) pjp.getArgs()[0];
		String objectFk=CacheKeyAssist.getFkKey(daoKey,targetObj, methodName,argModel);
		if(null==objectFk) {
			return (BaseModel<?>) pjp.proceed();
		}
		
		//尝试从缓存取值
		Object cacheObject=redisTemplate.opsForValue().get(objectFk);
		
		if(null!=cacheObject) {
			return (BaseModel<?>) cacheObject;
		}
		
		
		//从库取值
		Object dbObject = pjp.proceed();
		if(null==dbObject){
			return null;
		}
		

		//放入缓存
		String [] conditionKeys=null;
		KeysInfo keysInfo=cacheConfig.getKeysInfo(methodName);
		if(null==keysInfo) {
			throw new NullPointerException("cacheConfig dos not config for this SelectByUnique.");
		}
		conditionKeys=keysInfo.getConditionKeys();
		
		cacheObjectForFk(daoKey, conditionKeys, argModel, objectFk, dbObject);
		 
		
		return (BaseModel<?>) dbObject; 
	}
	
    /**
     * ==================== 外键条件列表查询 ==================== 
     */
	@SuppressWarnings("unchecked")
	@Around("selectByForeignPoint()")
	private List<BaseModel<?>> aroundSelectByForeign(ProceedingJoinPoint pjp) throws Throwable{
		
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(pjp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect){
			return (List<BaseModel<?>>) pjp.proceed();
		}
		

		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		Signature sig = pjp.getSignature();
		String methodName=sig.getName();
		Object targetObj= pjp.getTarget();
		BaseModel<?> argModel=(BaseModel<?>) pjp.getArgs()[0];
		String objectFk=CacheKeyAssist.getFkKey(daoKey,targetObj, methodName,argModel);
		if(null==objectFk) {
			return (List<BaseModel<?>>) pjp.proceed();
		}
		
		//尝试从缓存取值
		//Object cacheObject=cache.get(objectFk);
		Object cacheObject=redisTemplate.opsForValue().get(objectFk);
		
		if(null!=cacheObject) {
			return (List<BaseModel<?>>) cacheObject;
		}
		
		
		//从库取值
		Object dbObject = pjp.proceed();
		if(null==dbObject){
			return null;
		}
		

		//放入缓存
		String [] conditionKeys=null;
		KeysInfo keysInfo=cacheConfig.getKeysInfo(methodName);
		if(null==keysInfo) {
			throw new NullPointerException("cacheConfig dos not config for this SelectByForeign.");
		}
		conditionKeys=keysInfo.getConditionKeys();
		
		cacheObjectForFk(daoKey, conditionKeys, argModel, objectFk, dbObject);
		 
		
		return (List<BaseModel<?>>) dbObject; 
	}
	
	
    /**
     * ==================== 外键条件组列表查询 ==================== 
     */

	@SuppressWarnings("unchecked")
	@Around("selectInFkListPoint()")
	private List<BaseModel<?>> aroundSelectInFkList(ProceedingJoinPoint pjp) throws Throwable{
		
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(pjp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect){
			return (List<BaseModel<?>>) pjp.proceed();
		}
		
		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		Signature sig = pjp.getSignature();
		String methodName=sig.getName();
		KeysInfo keysInfo=cacheConfig.getKeysInfo(methodName);
		String fkName=keysInfo.getConditionKeys()[0];
		
		final List<BaseModel<?>> modelList=(List<BaseModel<?>>) pjp.getArgs()[0];
		if(null==modelList||0==modelList.size()) {
			return modelList;
		}
		

		//过滤外键值为空的，并建立外键值指向索引
		Map<String,BaseModel<?>> fkObjKeyMap =new HashMap<>();//Map<fkObjKey,BaseModel<?>>
		List<String> fkObjKeyList=new ArrayList<>();
		List<String> fkValueKeyList=new ArrayList<>();
		for (int i = 0; i < modelList.size(); i++) {
			BaseModel<?> oneModel=modelList.get(i);
			String valueKey=CacheKeyAssist.getValueKey(oneModel, fkName);
			String fkObjKey=CacheKeyAssist.getOneFkKey(daoKey, fkName, valueKey);
			if(null==fkObjKey) {continue;}
			if(!fkObjKeyMap.containsKey(fkObjKey)) {
				fkObjKeyMap.put(fkObjKey, oneModel);
				fkObjKeyList.add(fkObjKey);
				fkValueKeyList.add(valueKey);
			}
		}
		
		
		//查询缓存记录
	    @SuppressWarnings("rawtypes")
		SessionCallback queryFkListFun = (SessionCallback)(RedisOperations ops)-> {
	    	ValueOperations<String, Object> opsForValue=ops.opsForValue();
			for (int i = 0; i < fkObjKeyList.size(); i++) {
				opsForValue.get(fkObjKeyList.get(i));
			}
			return null;
	    }; 
	    List<Object> cacheMixtureList= redisTemplate.executePipelined(queryFkListFun);
		
	    //分析出已缓存的和未缓存的
	    List<List<BaseModel<?>>> cachedList=new ArrayList<>();
	    List<BaseModel<?>> unCachedList=new ArrayList<>();
	    List<String> unCachedValueKeyList=new ArrayList<>();
		for (int i = 0; i < fkObjKeyList.size(); i++) {
			String fkObjKey = fkObjKeyList.get(i);
			String fkValueKey = fkValueKeyList.get(i);
			Object fromCacheResult=cacheMixtureList.get(i);
			if(null==fromCacheResult) {
				unCachedList.add(fkObjKeyMap.get(fkObjKey));
				unCachedValueKeyList.add(fkValueKey);
			}else {
				List<BaseModel<?>> oneCachedList=(List<BaseModel<?>>) fromCacheResult;
				cachedList.add(oneCachedList);
			}
		}
		
		
		List<BaseModel<?>> dbList=new ArrayList<>();
		if(unCachedList.size()>0) {
			//没有缓存的对象列表查库
			dbList=(List<BaseModel<?>>) pjp.proceed(new Object[] {unCachedList});
			//数据库数据存入缓存
			cacheListForOneFk(daoKey, fkName, unCachedValueKeyList, dbList);
		}
		

		//合并数据
		List<BaseModel<?>> allList=new ArrayList<>();
		for (int i = 0; i < cachedList.size(); i++) {
			allList.addAll(cachedList.get(i));
		}
	    allList.addAll(dbList);
		
		return allList;
	}
	
	
    /**
     * ==================== 外键条件分页查询 ==================== 
     */
	@SuppressWarnings("unchecked")
	@Around("selectPagePoint()")
	private PageInfo<BaseModel<?>> aroundSelectPage(ProceedingJoinPoint pjp) throws Throwable{
		
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(pjp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect){
			return (PageInfo<BaseModel<?>>) pjp.proceed();
		}
		

		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		Signature sig = pjp.getSignature();
		String methodName=sig.getName();
		Object targetObj= pjp.getTarget();
		PageInfo<Object> pageInfo=(PageInfo<Object>) pjp.getArgs()[0];
		String pageKey=CacheKeyAssist.getPageKey(daoKey, targetObj, methodName, pageInfo);

		BaseModel<?> conditionModel=null;
		if(null!=pageInfo.getList()&&pageInfo.getList().size()>0) {
			conditionModel=(BaseModel<?>) pageInfo.getList().get(0);
		}
		
		if(null==pageKey) {
			return (PageInfo<BaseModel<?>>) pjp.proceed();
		}
		
		//尝试从缓存取值
		//Object cacheObject=cache.get(pageKey);
		Object cacheObject=redisTemplate.opsForValue().get(pageKey);
		if(null!=cacheObject) {
			return (PageInfo<BaseModel<?>>) cacheObject;
		}
		
		
		//从库取值
		Object dbObject = pjp.proceed();
		if(null==dbObject){
			return null;
		}
		

		//放入缓存
		String [] conditionKeys=null;
		KeysInfo keysInfo=cacheConfig.getKeysInfo(CacheKeyAssist.getEqualMethodNameOfPage(methodName));
		
		if(null==keysInfo) {
			throw new NullPointerException("cacheConfig dos not config for this SelectByForeign.");
		}
		conditionKeys=keysInfo.getConditionKeys();
		
		
		//BaseModel<?> conditionModel=(BaseModel<?>) pageInfo.getList().get(0);
		cacheObjectForFk(daoKey, conditionKeys, conditionModel, pageKey, dbObject);
		 
		
		return (PageInfo<BaseModel<?>>) dbObject; 
	}
	
	
	
	

    /**
     * ========================================== 辅助方法区 ==========================================
     */

	
	/**
	 * 把PK标识的对象列表存入缓存
	 * @author zhanghaiting
	 * @date 2019年9月28日
	 *
	 * @param daoKey
	 * @param foreignKeyArray
	 * @param list
	 * @return
	 */
	private boolean cacheListForPk(String daoKey,String [] foreignKeyArray,List<BaseModel<?>> list) {
		

		//分析需要缓存对象的主键结构
		Set<String> PkAllSet=getPkAllSet(daoKey, list);
		
		//分析需要缓存对象的外键结构
		Map<String,Map<String,Set<String>>> fkNameMap=getFkNameMapForPk(daoKey, foreignKeyArray, list);
		
		
		//批量缓存键结构
	    @SuppressWarnings("rawtypes")
		SessionCallback cacheKeysFun = (SessionCallback)(RedisOperations ops)-> {

			@SuppressWarnings("unchecked")
			BoundSetOperations<String, String> pkAllSetOperations=ops.boundSetOps(CacheKeyAssist.getPkAllKey(daoKey));
			for (String pkKey : PkAllSet) {
				pkAllSetOperations.add(pkKey);
			}
			
	    	for (String fkName : fkNameMap.keySet()) {
				String fkNameKey=CacheKeyAssist.getFkNameKey(daoKey, fkName);
				@SuppressWarnings("unchecked")
				BoundSetOperations<String, String> fkNameKeySetOperations=ops.boundSetOps(fkNameKey);
				
	    		Map<String,Set<String>> fkAllMap=fkNameMap.get(fkName);
	    		for (String fkAllKey : fkAllMap.keySet()) {
	    			fkNameKeySetOperations.add(fkAllKey);
					@SuppressWarnings("unchecked")
					BoundSetOperations<String, String> fkAllKeySetOperations=ops.boundSetOps(fkAllKey);
	    			Set<String> keySet=fkAllMap.get(fkAllKey);
	    			for (String key : keySet) {
	    				fkAllKeySetOperations.add(key);
					}
				}
			}
			return null;
	    };
	    
	    redisTemplate.executePipelined(cacheKeysFun);
	    
	    //键结构缓存成功则缓存对象
	    @SuppressWarnings("rawtypes")
		SessionCallback cacheObjectsFun = (SessionCallback)(RedisOperations ops)-> {
	    	@SuppressWarnings("unchecked")
			ValueOperations<String, Object> opsForValue=ops.opsForValue();
	    	for (int i = 0; i < list.size(); i++) {
	    		BaseModel<?> oneDbModel=list.get(i);
	    		String objectPk=CacheKeyAssist.getPkKey(daoKey, oneDbModel.getId());
	    		opsForValue.set(objectPk, oneDbModel, CACHE_TIME, TimeUnit.SECONDS);
			}
			return null;
	    };
	    redisTemplate.executePipelined(cacheObjectsFun);
		
		return true;
	}
	

	/**
	 * 把FK标识的对象列表存入缓存
	 * @author zhanghaiting
	 * @date 2019年9月30日
	 * @param daoKey
	 * @param foreignKeyArray
	 * @param conditionModel
	 * @param objectPk
	 * @param dbObject
	 * @return
	 */
	private boolean cacheObjectForFk(String daoKey,String [] foreignKeyArray,BaseModel<?> conditionModel,String objectFk,Object dbObject) {
		

		//分析需要缓存对象的外键结构
		Map<String,Map<String,Set<String>>> fkNameMap=getFkNameMapForFk(daoKey, foreignKeyArray, conditionModel, objectFk);
		
		
		//批量缓存键结构
	    @SuppressWarnings("rawtypes")
		SessionCallback cacheKeysFun = (SessionCallback)(RedisOperations ops)-> {

	    	for (String fkName : fkNameMap.keySet()) {
				String fkNameKey=CacheKeyAssist.getFkNameKey(daoKey, fkName);
				@SuppressWarnings("unchecked")
				BoundSetOperations<String, String> fkNameKeySetOperations=ops.boundSetOps(fkNameKey);
				
	    		Map<String,Set<String>> fkAllMap=fkNameMap.get(fkName);
	    		for (String fkAllKey : fkAllMap.keySet()) {
	    			fkNameKeySetOperations.add(fkAllKey);
					@SuppressWarnings("unchecked")
					BoundSetOperations<String, String> fkAllKeySetOperations=ops.boundSetOps(fkAllKey);
	    			Set<String> keySet=fkAllMap.get(fkAllKey);
	    			for (String key : keySet) {
	    				fkAllKeySetOperations.add(key);
					}
				}
			}
			return null;
	    };
	    
	    redisTemplate.executePipelined(cacheKeysFun);
	    
	    //键结构缓存成功则缓存对象
	    //cache.set(objectFk, Constant.CACHE_TIME, dbObject);
	    redisTemplate.opsForValue().set(objectFk, dbObject, CACHE_TIME, TimeUnit.SECONDS);
	    
	    
		return true;
	}
	

	/**
	 * 把一个FK标识的各个值对应的列表存入缓存
	 * @author zhanghaiting
	 * @date 2019年10月15日
	 * @param daoKey
	 * @param fkName
	 * @param fkObjListMap
	 * @return
	 */
	private boolean cacheListForOneFk(String daoKey,String fkName,List<String> unCachedValueKeyList,List<BaseModel<?>> list) {

		//按fk值拆分数据库返回的列表，以备按Fk存入缓存
		Map<String,List<BaseModel<?>>> fkObjListMap =new HashMap<>();//Map<fkValue,List<BaseModel<?>>>
		for (int i = 0; i < list.size(); i++) {
			BaseModel<?> oneDbModel=list.get(i);
			String valueKey=CacheKeyAssist.getValueKey(oneDbModel, fkName);
			List<BaseModel<?>> oneFkValueList=fkObjListMap.get(valueKey);
			if(null==oneFkValueList) {
				oneFkValueList=new ArrayList<>();
				fkObjListMap.put(valueKey, oneFkValueList);
			}
			oneFkValueList.add(oneDbModel);
		}
		
		//如果某个条件值的查询结果为空，则缓存空数组
		for (int i = 0; i < unCachedValueKeyList.size(); i++) {
			String valueKey = unCachedValueKeyList.get(i);
			if(!fkObjListMap.containsKey(valueKey)) {
				fkObjListMap.put(valueKey, new ArrayList<>());
			}
		}
		
		//分析需要缓存对象的外键结构
		Map<String,Set<String>> fkAllMap=getFkNameMapForOneFk(daoKey, fkName, fkObjListMap);

		//批量缓存键结构
	    @SuppressWarnings("rawtypes")
		SessionCallback cacheKeysFun = (SessionCallback)(RedisOperations ops)-> {

			String fkNameKey=CacheKeyAssist.getFkNameKey(daoKey, fkName);
			@SuppressWarnings("unchecked")
			BoundSetOperations<String, String> fkNameKeySetOperations=ops.boundSetOps(fkNameKey);
    		for (String fkAllKey : fkAllMap.keySet()) {
    			fkNameKeySetOperations.add(fkAllKey);
				@SuppressWarnings("unchecked")
				BoundSetOperations<String, String> fkAllKeySetOperations=ops.boundSetOps(fkAllKey);
    			Set<String> keySet=fkAllMap.get(fkAllKey);
    			for (String key : keySet) {
    				fkAllKeySetOperations.add(key);
				}
			}
			return null;
	    };
	    redisTemplate.executePipelined(cacheKeysFun);
		
	    //键结构缓存成功则缓存对象
	    @SuppressWarnings("rawtypes")
		SessionCallback cacheObjectsFun = (SessionCallback)(RedisOperations ops)-> {
	    	@SuppressWarnings("unchecked")
			ValueOperations<String, Object> opsForValue=ops.opsForValue();
			for (String valueKey : fkObjListMap.keySet()) {
				String fkObjKey=CacheKeyAssist.getOneFkKey(daoKey, fkName, valueKey);
				List<BaseModel<?>> oneFkValueList=fkObjListMap.get(valueKey);
	    		opsForValue.set(fkObjKey, oneFkValueList, CACHE_TIME, TimeUnit.SECONDS);
			}
			return null;
	    };
	    redisTemplate.executePipelined(cacheObjectsFun);
		
		return true;
	}
	
	
	/**
	 * 分析需要缓存对象的键结构-FK
	 * @author zhanghaiting
	 * @date 2019年9月30日
	 *
	 * @param foreignKeyArray
	 * @param list
	 * @return Map<foreignKey,Map<FK-ALL_K1_V1,Set<FK_K1_V1...>>>
	 * 
	 */
	private Map<String,Map<String,Set<String>>> getFkNameMapForFk(String daoKey,String [] foreignKeyArray,BaseModel<?> conditionModel,String objectFk){

		Map<String,Map<String,Set<String>>> fkNameMap=new HashMap<>();
		for (int i = 0; i < foreignKeyArray.length; i++) {
			fkNameMap.put(foreignKeyArray[i], new HashMap<>());
		}
		
		for (int i = 0; i < foreignKeyArray.length; i++) {
			String foreignKey=foreignKeyArray[i];
			String valueKey=CacheKeyAssist.getValueKey(conditionModel, foreignKey);
			if(null==valueKey) {continue;}
			String fkAllKey=CacheKeyAssist.getFkAllKey(daoKey, foreignKey, valueKey);
			
			Map<String,Set<String>> fkAllMap=fkNameMap.get(foreignKey);
			Set<String> keySet=fkAllMap.get(fkAllKey);
			if(null==keySet) {
				keySet=new HashSet<>();
				fkAllMap.put(fkAllKey, keySet);
			}
			keySet.add(objectFk);
		}
		return fkNameMap;
		
	}
	

	/**
	 * 分析需要缓存对象的键结构-OneFk
	 * @author zhanghaiting
	 * @date 2019年10月15日
	 *
	 * @return Map<FK-ALL_K2_V2,Set<FK_K2_V2>>
	 * 
	 */
	private Map<String,Set<String>> getFkNameMapForOneFk(String daoKey,String fkName,Map<String,List<BaseModel<?>>> fkObjListMap){
		
		Map<String,Set<String>> fkAllMap=new HashMap<>();
		
		for (String valueKey : fkObjListMap.keySet()) {
			String fkAllKey=CacheKeyAssist.getFkAllKey(daoKey, fkName, valueKey);
			String fkObjKey=CacheKeyAssist.getOneFkKey(daoKey, fkName, valueKey);
			Set<String> keySet=fkAllMap.get(fkAllKey);
			if(null==keySet) {
				keySet=new HashSet<>();
				fkAllMap.put(fkAllKey, keySet);
			}
			keySet.add(fkObjKey);
			
		}
		return fkAllMap;
	}
	
	
	
	/**
	 * 分析需要缓存对象的键结构-PK
	 * @author zhanghaiting
	 * @date 2019年9月27日
	 *
	 * @param foreignKeyArray
	 * @param list
	 * @return Map<foreignKey,Map<FK-ALL_K1_V1,Set<PK_V1>>>
	 * 
	 */
	private Map<String,Map<String,Set<String>>> getFkNameMapForPk(String daoKey,String [] foreignKeyArray,List<BaseModel<?>> list){
		

		Map<String,Map<String,Set<String>>> fkNameMap=new HashMap<>();
		for (int i = 0; i < foreignKeyArray.length; i++) {
			fkNameMap.put(foreignKeyArray[i], new HashMap<>());
		}
		
		for (int j = 0; j < list.size(); j++) {
			BaseModel<?> dbModel=list.get(j);
			for (int i = 0; i < foreignKeyArray.length; i++) {
				String foreignKey=foreignKeyArray[i];
				String valueKey=CacheKeyAssist.getValueKey(dbModel, foreignKey);
				if(null==valueKey) {continue;}
				String fkAllKey=CacheKeyAssist.getFkAllKey(daoKey, foreignKey, valueKey);
				String objectPk=CacheKeyAssist.getPkKey(daoKey, dbModel.getId());
				
				Map<String,Set<String>> fkAllMap=fkNameMap.get(foreignKey);
				Set<String> keySet=fkAllMap.get(fkAllKey);
				if(null==keySet) {
					keySet=new HashSet<>();
					fkAllMap.put(fkAllKey, keySet);
				}
				keySet.add(objectPk);
			}
		}
		return fkNameMap;
	}
	
	
	/**
	 * 分析对象列表的主键集
	 * @author zhanghaiting
	 * @date 2019年9月30日
	 * @param daoKey
	 * @param list
	 * @return
	 */
	private Set<String> getPkAllSet(String daoKey,List<BaseModel<?>> list){
		Set<String> pkAllSet=new HashSet<>();
		if(null==list) {return pkAllSet;}
		
		pkAllSet=list.stream()
				.filter(o -> (null!=o && null!=o.getId()))
				.map(m -> CacheKeyAssist.getPkKey(daoKey, m.getId()))
				.collect(Collectors.toSet());
				
		return pkAllSet;
	}
	
    /**
     * ===================================================================================================
     * -----------------------------------  QueryAspect end --------------------------------------------
     * ===================================================================================================
     */
	
    /**
     * ===================================================================================================
     * -----------------------------------  InsertAspect start --------------------------------------------
     * ===================================================================================================
     */
	

	
	@AfterReturning(pointcut = "insertOnePoint()",returning = "cc")
	private void afterInsertOne(JoinPoint jp,int cc){

		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(jp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect||0==cc){
			return;
		}
		
		//缓存配置信息和参数
		final String daoKey=cacheConfig.getCacheObjectKey();
		final String [] foreignKeyArray=cacheConfig.getForeignKeyArray();
		BaseModel<?> argModel=(BaseModel<?>) jp.getArgs()[0];
		List<Object> modelList=new ArrayList<>();
		modelList.add(argModel);
		
		Set<String> fkAllSet=CacheKeyAssist.getFkAllSetForClean(daoKey, foreignKeyArray, modelList);
		String pkPrefix=CacheKeyAssist.getPkKeyPrefix(daoKey);
		cleanKeySetUnionWithoutPk(fkAllSet, pkPrefix);
		
		return;
	}
	

	@AfterReturning(pointcut = "insertBatchPoint()",returning = "cc")
	private void afterInsertBatch(JoinPoint jp,int cc){
		
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(jp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect||0==cc){
			return;
		}

		//缓存配置信息和参数
		final String daoKey=cacheConfig.getCacheObjectKey();
		final String [] foreignKeyArray=cacheConfig.getForeignKeyArray();
		@SuppressWarnings("unchecked")
		final List<Object> modelList=(List<Object>) jp.getArgs()[0];
		if(null==modelList || 0==modelList.size()) {return;}
		
		Set<String> fkAllSet=CacheKeyAssist.getFkAllSetForClean(daoKey, foreignKeyArray, modelList);
		String pkPrefix=CacheKeyAssist.getPkKeyPrefix(daoKey);
		cleanKeySetUnionWithoutPk(fkAllSet, pkPrefix);
		
		return;
	}
	
	
    /**
     * ===================================================================================================
     * -----------------------------------  InsertAspect end --------------------------------------------
     * ===================================================================================================
     */
	
    /**
     * ===================================================================================================
     * -----------------------------------  UpdateAspect start --------------------------------------------
     * ===================================================================================================
     */
	
	@Around("updateByPrimaryPoint()")
	private int aroundUpdateByPrimary(ProceedingJoinPoint pjp) throws Throwable{
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(pjp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect){
			return (int) pjp.proceed();
		}
		
		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		final String [] foreignKeyArray=cacheConfig.getForeignKeyArray();
		Object targetObj=pjp.getTarget();
		BaseModel<?> argModel=(BaseModel<?>) pjp.getArgs()[0];
		

		List<Object> modelList=new ArrayList<>();
		//查询旧值
		Object oldOjbect=ObjectReflect.invokeMethod(targetObj, CacheKeyAssist.METHOD_SELECT_PK, new Class<?>[]{BaseModel.class}, new Object[]{argModel});
		if(null!=oldOjbect) {
			modelList.add((BaseModel<?>)oldOjbect);
		}
		modelList.add(argModel);
		
		//分析缓存键
		Set<String> fkAllSet=CacheKeyAssist.getFkAllSetForClean(daoKey, foreignKeyArray, modelList);
		String pkKey=CacheKeyAssist.getPkKey(daoKey, argModel.getId());
		
		//执行更新
		int upCount=(int) pjp.proceed();
		
		//清除缓存
		if(upCount>0) {
			//并发环境可能会丢失更新（未提交读），但数据库事务规避了这个可能性。
			if(foreignKeyArray.length>0) {
				cleanKeySetUnion(fkAllSet);
			}else {
				//cache.del(pkKey);
				redisTemplate.delete(pkKey);
			}
		}
		
		return upCount;
	}
	
	
	
	@SuppressWarnings("unchecked")
	@Around("updateBatchByPkPoint()")
	private int aroundUpdateBatchByPk(ProceedingJoinPoint pjp) throws Throwable {
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(pjp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect){
			return (int) pjp.proceed();
		}
		
		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		final String [] foreignKeyArray=cacheConfig.getForeignKeyArray();
		Object targetObj= pjp.getTarget();
		final List<BaseModel<?>> argListModel=(List<BaseModel<?>>) pjp.getArgs()[0];
		
		//查询旧值
		List<BaseModel<?>> oldList=(List<BaseModel<?>>) ObjectReflect.invokeMethod(targetObj, 
				CacheKeyAssist.METHOD_SELECT_BATCH_PK, new Class<?>[]{List.class}, new Object[]{argListModel});
		

		//分析缓存键
		List<Object> allModelList=new ArrayList<>();
		allModelList.addAll(oldList);
		allModelList.addAll(argListModel);
		Set<String> fkAllSet=CacheKeyAssist.getFkAllSetForClean(daoKey, foreignKeyArray, allModelList);
		
		List<String> pkKeyList=argListModel.stream()
				.map(oneModel -> CacheKeyAssist.getPkKey(daoKey, oneModel.getId()))
				.collect(Collectors.toList());
		
		//执行更新
		int upCount=(int) pjp.proceed();
		
		//清除缓存
		if(upCount>0) {
			//并发环境可能会丢失更新（未提交读），但数据库事务规避了这个可能性。
			if(foreignKeyArray.length>0) {
				cleanKeySetUnion(fkAllSet);
			}else {
				//cache.del(pkKeyList);
				redisTemplate.delete(pkKeyList);
			}
		}
		
		return upCount;
	}
	
	
	
	@AfterReturning(pointcut = "updateByForeignPoint()",returning = "cc")
	private void afterUpdateByForeign(JoinPoint jp,int cc){
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(jp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect||0==cc){
			return;
		}
		
		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		final String [] foreignKeyArray=cacheConfig.getForeignKeyArray();
		Signature sig = jp.getSignature();
		String methodName=sig.getName();
		BaseModel<?> argModel=(BaseModel<?>) jp.getArgs()[0];
		KeysInfo keysInfo=cacheConfig.getKeysInfo(methodName);
		String [] updateKeys=null;
		String [] conditionKeys=null;
		Set<String> conditionSet=null;
		if(null!=keysInfo) {
			updateKeys=keysInfo.getValueKeys();
			conditionKeys=keysInfo.getConditionKeys();
			conditionSet=new HashSet<>(Arrays.asList(conditionKeys));
		}
		
		
		//分析FK查询条件
		List<String> fkNameToCleanList=new ArrayList<>();
		Set<String> fkAllSet=new HashSet<>();
		CacheKeyAssist.analyzeFkCondition(fkNameToCleanList, fkAllSet, foreignKeyArray, conditionSet, argModel, daoKey);
		
		//分析FK更新值
		if(null!=updateKeys) {
			for (int i = 0; i < updateKeys.length; i++) {
				String oneUpKey=updateKeys[i];
				String valueKey=CacheKeyAssist.getValueKey(argModel, oneUpKey);
				if(null==valueKey) {continue;}
				String fkAllKey=CacheKeyAssist.getFkAllKey(daoKey, oneUpKey, valueKey);
				fkAllSet.add(fkAllKey);
			}
		}
		
		//补充分析PK键
		if(fkNameToCleanList.size()==foreignKeyArray.length) {
			fkAllSet.add(CacheKeyAssist.getPkAllKey(daoKey));
		}
		
		
		//清除缓存
		String[] fkNameToCleanArray=fkNameToCleanList.toArray(new String[fkNameToCleanList.size()]);
		cleanFkList(daoKey, fkNameToCleanArray);
		cleanKeySetUnion(fkAllSet);
		
		return;
		
	}
	
	
	
	
    /**
     * ===================================================================================================
     * -----------------------------------  UpdateAspect end --------------------------------------------
     * ===================================================================================================
     */
	
	
    /**
     * ===================================================================================================
     * -----------------------------------  DeleteAspect start --------------------------------------------
     * ===================================================================================================
     */

	@Around("deleteByPrimaryPoint()")
	private int aroundDeleteByPrimary(ProceedingJoinPoint pjp) throws Throwable{
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(pjp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect){
			return (int) pjp.proceed();
		}
		
		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		final String [] foreignKeyArray=cacheConfig.getForeignKeyArray();
		Object targetObj= pjp.getTarget();
		BaseModel<?> argModel=(BaseModel<?>) pjp.getArgs()[0];
		

		List<Object> modelList=new ArrayList<>();
		//查询旧值
		Object oldOjbect=ObjectReflect.invokeMethod(targetObj, CacheKeyAssist.METHOD_SELECT_PK, new Class<?>[]{BaseModel.class}, new Object[]{argModel});
		if(null!=oldOjbect) {
			modelList.add((BaseModel<?>)oldOjbect);
		}
		
		//分析缓存键
		Set<String> fkAllSet=CacheKeyAssist.getFkAllSetForClean(daoKey, foreignKeyArray, modelList);
		String pkKey=CacheKeyAssist.getPkKey(daoKey, argModel.getId());
		
		//执行更新
		int delCount=(int) pjp.proceed();
		
		//清除缓存
		if(delCount>0) {
			//并发环境可能会丢失更新（未提交读），但数据库事务规避了这个可能性。
			if(foreignKeyArray.length>0) {
				cleanKeySetUnion(fkAllSet);
			}else {
				//cache.del(pkKey);
				redisTemplate.delete(pkKey);
			}
		}
		return delCount;
		
	}
	
	
	
	@Around("deleteBatchByPkPoint()")
	private int aroundDeleteBatchByPk(ProceedingJoinPoint pjp) throws Throwable {
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(pjp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect){
			return (int) pjp.proceed();
		}
		
		

		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		final String [] foreignKeyArray=cacheConfig.getForeignKeyArray();
		Object targetObj=pjp.getTarget();
		@SuppressWarnings("unchecked")
		final List<BaseModel<?>> argListModel=(List<BaseModel<?>>) pjp.getArgs()[0];
		
		//查询旧值
		@SuppressWarnings("unchecked")
		List<BaseModel<?>> oldList=(List<BaseModel<?>>) ObjectReflect.invokeMethod(targetObj, 
				CacheKeyAssist.METHOD_SELECT_BATCH_PK, new Class<?>[]{List.class}, new Object[]{argListModel});
		

		//分析缓存键
		List<Object> allModelList=new ArrayList<>();
		allModelList.addAll(oldList);
		//allModelList.addAll(argListModel);
		Set<String> fkAllSet=CacheKeyAssist.getFkAllSetForClean(daoKey, foreignKeyArray, allModelList);
		
		List<String> pkKeyList=argListModel.stream()
				.map(oneModel -> CacheKeyAssist.getPkKey(daoKey, oneModel.getId()))
				.collect(Collectors.toList());
		
		//执行更新
		int upCount=(int) pjp.proceed();
		
		//清除缓存
		if(upCount>0) {
			//并发环境可能会丢失更新（未提交读），但数据库事务规避了这个可能性。
			if(foreignKeyArray.length>0) {
				cleanKeySetUnion(fkAllSet);
			}else {
				//cache.del(pkKeyList);
				redisTemplate.delete(pkKeyList);
			}
		}
		
		return upCount;
		
	}
	

	
	@AfterReturning(pointcut = "deleteByForeignPoint()",returning = "cc")
	private void afterDeleteByForeign(JoinPoint jp,int cc){
		final CacheConfigModel cacheConfig=CacheKeyAssist.getCacheConfig(jp);
		boolean enableCacheAspect=cacheConfig.isEnableCacheAspect();
		
		//不使用缓存，则直接返回
		if(!enableCacheAspect||0==cc){
			return;
		}
		
		//获取缓存配置，和缓存键
		final String daoKey=cacheConfig.getCacheObjectKey();
		final String [] foreignKeyArray=cacheConfig.getForeignKeyArray();
		Signature sig = jp.getSignature();
		String methodName=sig.getName();
		BaseModel<?> argModel=(BaseModel<?>) jp.getArgs()[0];
		KeysInfo keysInfo=cacheConfig.getKeysInfo(methodName);
		String [] conditionKeys=null;
		Set<String> conditionSet=null;
		if(null!=keysInfo) {
			conditionKeys=keysInfo.getConditionKeys();
			conditionSet=new HashSet<>(Arrays.asList(conditionKeys));
		}
		

		//分析FK查询条件
		List<String> fkNameToCleanList=new ArrayList<>();
		Set<String> fkAllSet=new HashSet<>();
		CacheKeyAssist.analyzeFkCondition(fkNameToCleanList, fkAllSet, foreignKeyArray, conditionSet, argModel, daoKey);

		//补充分析PK键
		if(fkNameToCleanList.size()==foreignKeyArray.length) {
			fkAllSet.add(CacheKeyAssist.getPkAllKey(daoKey));
		}

		//清除缓存
		String[] fkNameToCleanArray=fkNameToCleanList.toArray(new String[fkNameToCleanList.size()]);
		cleanFkList(daoKey, fkNameToCleanArray);
		cleanKeySetUnion(fkAllSet);
		
		return;
			
	}
	
	
    /**
     * ===================================================================================================
     * -----------------------------------  DeleteAspect end --------------------------------------------
     * ===================================================================================================
     */
	
    /**
     * ===================================================================================================
     * -----------------------------------  CacheLua start --------------------------------------------
     * ===================================================================================================
     */
	

    private final String CleanFkList="CleanFkList.lua";
    private final String CleanKeySetUnion="CleanKeySetUnion.lua";
    private final String CleanKeySetUnionWithoutPk="CleanKeySetUnionWithoutPk.lua";
    private final String CleanKeySetIntersection="CleanKeySetIntersection.lua";

	/**
	 * 清空Fk列表
	 * 使用 Lua 实现
	 * @author zhanghaiting
	 * @date 2019年9月30日
	 * @param daoKey
	 * @param foreignKeyArray
	 */
	boolean cleanFkList(String daoKey,String [] foreignKeyArray) {
		if(null==foreignKeyArray||0==foreignKeyArray.length) {
			return true;
		}
		
		List<String> keyList=new ArrayList<>();
		for (int i = 0; i < foreignKeyArray.length; i++) {
			keyList.add(CacheKeyAssist.getFkNameKey(daoKey, foreignKeyArray[i]));
		}

		DefaultRedisScript<Long> rs = getRedisScript(CleanFkList, null);
		
		Long result = (Long) redisTemplate.execute(rs, keyList);
		
		return 1==result;
	}
	

	/**
	 * 清空键指向集合的交集
	 * @author zhanghaiting
	 * @date 2019年9月30日
	 * @param fkAllKeySet
	 * @return
	 */
	boolean cleanKeySetIntersection(Set<String> fkAllKeySet) {
		if(null==fkAllKeySet || fkAllKeySet.size()==0) {
			return true;
		}
		
		List<String> keyList = new ArrayList<>(fkAllKeySet);
		String keys=getKeys(keyList.size());
		
		DefaultRedisScript<Long> rs = getRedisScript(CleanKeySetIntersection, new String[] {keys});
		
		Long result = (Long) redisTemplate.execute(rs, keyList);
		
		return 1==result;
	}
	
	
	/**
	 * 清空键指向集合的并集
	 * @author zhanghaiting
	 * @date 2019年10月10日
	 * @param fkAllKeySet
	 * @return
	 */
	boolean cleanKeySetUnion(Set<String> fkAllKeySet) {

		if(null==fkAllKeySet || fkAllKeySet.size()==0) {
			return true;
		}
		
		List<String> keyList = new ArrayList<>(fkAllKeySet);
		String keys=getKeys(keyList.size());
		
		DefaultRedisScript<Long> rs = getRedisScript(CleanKeySetUnion, new String[] {keys});
		
		Long result = (Long) redisTemplate.execute(rs, keyList);
		
		return 1==result;
		
	}
	
	/**
	 * 清空键指向集合的并集，忽略主键
	 * @author zhanghaiting
	 * @date 2019年10月16日
	 * @param fkAllKeySet
	 * @param pkStart pk前缀
	 * @return
	 */
	boolean cleanKeySetUnionWithoutPk(Set<String> fkAllKeySet,String pkStart) {

		if(null==fkAllKeySet || fkAllKeySet.size()==0) {
			return true;
		}
		
		List<String> keyList = new ArrayList<>(fkAllKeySet);
		String keys=getKeys(keyList.size());
		
		DefaultRedisScript<Long> rs = getRedisScript(CleanKeySetUnionWithoutPk, new String[] {keys});
		
		Long result = (Long) redisTemplate.execute(rs, keyList,pkStart);
		
		return 1==result;
		
	}
	
	/**
	 * 
	 * @author zhanghaiting
	 * @date 2019年10月14日
	 * @param scriptName
	 * @param param
	 * @return
	 */
	private DefaultRedisScript<Long> getRedisScript(String scriptName,String[] param){
		
		String relativePath="lua"+File.separator+scriptName;
		String luaSource=FileTool.readResource(relativePath);
		if(null!=param) {
			for (int i = 0; i < param.length; i++) {
				luaSource = luaSource.replaceFirst("#"+i+"#", param[i]);
			}
		}
		logger.debug(luaSource);
		
		DefaultRedisScript<Long> rs = new DefaultRedisScript<Long>();
		rs.setScriptText(luaSource);
		rs.setResultType(Long.class);
		
		return rs;
	}

	
	/**
	 * redis.call 所使用的键
	 * @author zhanghaiting
	 * @date 2019年10月10日
	 * @param couut
	 * @return
	 */
	private String getKeys(int couut) {
		String keys="";
		for (int i = 0; i < couut; i++) {
			int argNum=i+1;
			keys+=",KEYS["+argNum+"]";
		}
		keys=keys.substring(1);
		return keys;
	}

	
	
    /**
     * ===================================================================================================
     * -----------------------------------  CacheLua end --------------------------------------------
     * ===================================================================================================
     */
	

}
