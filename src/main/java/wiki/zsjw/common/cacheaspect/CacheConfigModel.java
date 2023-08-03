package wiki.zsjw.common.cacheaspect;

import java.util.HashMap;
import java.util.Map;

/**
 * Dao层缓存配置模型
* @author zhanghaiting
* @date 2019年9月24日
*/

public class CacheConfigModel {
	private boolean enableCacheAspect=true;
	private String cacheObjectKey;
	private Class<?> genericityClazz;
	private String [] foreignKeyArray;
	
	//Map<methodName,KeysInfo>, 方法键信息，用于update,及delete 方法
	private Map<String,KeysInfo> methodKeysInfoMap=new HashMap<>();
	
	
//	public CacheConfigModel(String interfaceName,Class<?> genericityClazz) {
//    	String cacheObjectKey=interfaceName.substring(interfaceName.indexOf(".dao.")+1);
//		this.cacheObjectKey=cacheObjectKey;
//		this.genericityClazz=genericityClazz;
//	}
	public CacheConfigModel(boolean enableCacheAspect) {
		this.enableCacheAspect=enableCacheAspect;
	}
	
	public CacheConfigModel(String cacheObjectKey,Class<?> genericityClazz) {
		this.cacheObjectKey=cacheObjectKey;
		this.genericityClazz=genericityClazz;
		this.foreignKeyArray=new String[] {};
	}

	
	
	public class KeysInfo{
		private String [] valueKeys;//值字段
		private String [] conditionKeys;//条件字段
		
		
		public KeysInfo(String [] conditionKeys) {
			this.conditionKeys=conditionKeys;
		}
		
		public KeysInfo(String [] valueKeys,String [] conditionKeys) {
			this.valueKeys=valueKeys;
			this.conditionKeys=conditionKeys;
		}
		
		public String [] getValueKeys() {
			return valueKeys;
		}
		public void setValueKeys(String [] valueKeys) {
			this.valueKeys = valueKeys;
		}
		public String [] getConditionKeys() {
			return conditionKeys;
		}
		public void setConditionKeys(String [] conditionKeys) {
			this.conditionKeys = conditionKeys;
		}
		
	}
	
	
	
	/**
	 * ==========================getter setter==========================
	 */
	public boolean isEnableCacheAspect() {
		return enableCacheAspect;
	}

	public void setEnableCacheAspect(boolean enableCacheAspect) {
		this.enableCacheAspect = enableCacheAspect;
	}

	public String getCacheObjectKey() {
		return cacheObjectKey;
	}


	public Class<?> getGenericityClazz() {
		return genericityClazz;
	}


	public String [] getForeignKeyArray() {
		return foreignKeyArray;
	}

	public void setForeignKeyArray(String [] foreignKeyArray) {
		this.foreignKeyArray = foreignKeyArray;
	}

	public void putKeysInfo(String methodName,KeysInfo keysInfo) {
		methodKeysInfoMap.put(methodName, keysInfo);
	}
	
    public KeysInfo getKeysInfo(String methodName) {
    	return methodKeysInfoMap.get(methodName);
    }

	
	
}
