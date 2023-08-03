package wiki.zsjw.common.cacheaspect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aspectj.lang.JoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.pagehelper.PageInfo;

import wiki.zsjw.common.tools.extension.ObjectExtend;
import wiki.zsjw.common.tools.extension.ObjectReflect;

/**
 * 缓存键辅助方法
 * 
* @author zhanghaiting
* @date 2019年9月25日
*/

public class CacheKeyAssist {
	static Logger logger  =  LoggerFactory.getLogger(CacheKeyAssist.class);

	private static String ConfigMethodName="getCacheConfig";
	
    /**
     * ========================================== 缓存键及前缀定义 ==========================================
     */
    static final String KEY_PK_ALL="_PK-ALL";
    //static final String KEY_NK_ALL="_NK-ALL";

    static final String KEY_PREFIX_PK="_PK_";
    static final String KEY_PREFIX_FK_NAME="_FK-NAME_";
    static final String KEY_PREFIX_FK_ALL="_FK-ALL_";
    static final String KEY_PREFIX_FK="_FK";
    
    static final String KEY_DATE_FORMAT="yyyyMMdd";

    static final String KEY_SEPARATOR="_";
    
    static final String KEY_METHOD_PREFIX="keyFor";

    /**
     * ========================================== 切面方法定义 ==========================================
     */
    
    public static final String METHOD_SELECT_PK="selectByPrimary";
    public static final String METHOD_SELECT_BATCH_PK="selectInPkList";
    public static final String METHOD_SELECT_FK="selectByForeign";
    public static final String METHOD_SELECT_UK="selectByUnique";
    public static final String METHOD_SELECT_BATH_FK="selectInFkList";
    public static final String METHOD_SELECT_PAGE="selectPage";

    public static final String METHOD_INSERT_ONE="insertOne";
    public static final String METHOD_INSERT_BATH="insertBatch";
    
    
    public static final String METHOD_UPDATE_PK="updateByPrimary";
    public static final String METHOD_UPDATE_FK="updateByForeign";
    public static final String METHOD_UPDATE_BATH_PK="updateBatchByPk";

    public static final String METHOD_DELETE_PK="deleteByPrimary";
    public static final String METHOD_DELETE_FK="deleteByForeign";
    public static final String METHOD_DELETE_BATH_PK="deleteBatchByPk";
    
    
    public static final String SELECT_CONDITION_PREFIX=METHOD_SELECT_FK;
    public static final String SELECT_PAGE_PREFIX=METHOD_SELECT_PAGE;
	
    /**
     * ========================================== 切面字符串定义 ==========================================
     */
    
	/**
	 * (execution(public * wiki.zsjw.common.dbbase.Base*Impl.selectByForeign*(..)) 
	 * 		|| execution(public * wiki.zsjw.article.dao..*.selectByForeign*(..))) 
	 * && target(wiki.zsjw.common.cacheaspect.CacheAbleDao)"
	 */
    public static final String POINT_PREFIX="(execution(public * wiki.zsjw.common.dbbase.Base*Impl.";//扩展实现时，注意此处的包和命名规范
    public static final String POINT_MIDDLE_A="*(..)) || execution(public * ";
    public static final String POINT_MIDDLE_B=".dao..*.";
    public static final String POINT_SUFFIX="*(..))) && target(wiki.zsjw.common.cacheaspect.CacheAbleDao)";
    
	
	
	
	
	/**
	 * 获取对象的缓存配置
	 * @author zhanghaiting
	 * @date 2019年9月24日
	 *
	 * @param targetObj
	 * @return
	 */
	static CacheConfigModel getCacheConfig(JoinPoint jp) {
		Object targetObj= jp.getTarget();
		CacheConfigModel cacheConfig=(CacheConfigModel) ObjectReflect.getModelMethodReturn(targetObj, ConfigMethodName);
		return cacheConfig;
	}
	
	/**
	 * 获取属性的外键值，目前仅支持三种数据类型： int,str,date 
	 * @author zhanghaiting
	 * @date 2019年9月24日
	 *
	 * @param model
	 * @param attributeName
	 * @return
	 */
	static String getValueKey(Object model,String attributeName) {
		String valKey=null;
		
		String getMethodName="get"+ObjectExtend.upperCase(attributeName);
		Object valueObj=ObjectReflect.getModelMethodReturn(model, getMethodName);
		if(null==valueObj) {return valKey;}
		
		if(valueObj instanceof String){
			valKey= (String) valueObj;
		}else if(valueObj instanceof Integer) {
			valKey= String.valueOf(valueObj);
		}else if(valueObj instanceof Date) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(KEY_DATE_FORMAT);
			valKey=simpleDateFormat.format(valueObj);
		}
		
		return valKey;
	}
	
	
	
	
	
	
	
	
    /**
     * ==================== 键，键集 的快捷获取  ==================== 
     */
	
	/**
	 * 条件查询时，该查询需要提供的键方法名
	 * @author zhanghaiting
	 * @date 2019年9月30日
	 * @param methodName
	 * @return
	 */
	static String getKeyForMethodName(String methodName) {
		String keyForMethodName=KEY_METHOD_PREFIX+ObjectExtend.upperCase(methodName);
		
		if(methodName.startsWith(SELECT_PAGE_PREFIX)) {
			keyForMethodName=keyForMethodName.replaceFirst(
					ObjectExtend.upperCase(SELECT_PAGE_PREFIX), 
					ObjectExtend.upperCase(SELECT_CONDITION_PREFIX));
		}
		return keyForMethodName;
	}
	//获取分页方法等效的外键方法名
	static String getEqualMethodNameOfPage(String pageMethodName) {
		String equalMethodName=pageMethodName.replaceFirst(SELECT_PAGE_PREFIX, SELECT_CONDITION_PREFIX);
		return equalMethodName;
	}
	
	
	
	static String getPkAllKey(String daoKey) {
		return daoKey+KEY_PK_ALL;
	}
	
	//查询主键
	static String getPkKey(String daoKey,Object id) {
		return daoKey+KEY_PREFIX_PK+id;
	}
	static String getPkKeyPrefix(String daoKey) {
		return daoKey+KEY_PREFIX_PK;
	}
	
	//条件查询键
	static String getFkKey(String daoKey,Object targetObj,String methodName,Object argModel) {

		String keyMethodName=getKeyForMethodName(methodName);
		Method getKeyMethod=ObjectReflect.getDeclaredMethod(targetObj, keyMethodName,new Class<?>[]{argModel.getClass()});
		if(null==getKeyMethod) {
			logger.warn("KeyMethod is null! will not cache the result.");
			return null;
		}
		
		String objectFk=null;
		try {
			String keys=(String)getKeyMethod.invoke(targetObj,argModel);
			if("".equals(keys)) {
				return null;
			}
			objectFk=daoKey+KEY_PREFIX_FK+keys;
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		
		return objectFk;
	}
	
	//单个Fk条件查询键
	static String getOneFkKey(String daoKey,Object oneModel,String fkName) {
		String valueKey=getValueKey(oneModel, fkName);
		String oneFkKey=getOneFkKey(daoKey, fkName, valueKey);
		return oneFkKey;
	}
	//单个Fk条件查询键
	static String getOneFkKey(String daoKey,String fkName,String valueKey) {
		String oneFkKey=null;
		if(null==valueKey) {return null;}
		String keyForValue=getKeyFromValue(fkName, valueKey);
		oneFkKey=daoKey+KEY_PREFIX_FK+keyForValue;
		return oneFkKey;
	}
	
	//分页标识键
	static String getPageKey(String daoKey,Object targetObj,String methodName,PageInfo<Object> pageInfo) {
		Object argModel=null;
		if(null!=pageInfo.getList()&&pageInfo.getList().size()>0) {
			argModel=pageInfo.getList().get(0);
		}
		String objectFk=getFkKey(daoKey, targetObj, methodName,argModel);
		if(null==objectFk) {
			return null;
		}
		String pageKey=objectFk+"-no-"+pageInfo.getPageNum()+"-size-"+pageInfo.getPageSize();
		return pageKey;
	}
	
	
	static String getFkNameKey(String daoKey,String foreignKey) {
		return daoKey+KEY_PREFIX_FK_NAME+foreignKey;
	}
	
	static String getFkAllKey(String daoKey,String foreignKey,String valueKey) {
		return daoKey+KEY_PREFIX_FK_ALL+foreignKey+KEY_SEPARATOR+valueKey;
	}
	
	public static String getKeyFromValue(String keyName,String Value) {
		return KEY_SEPARATOR+keyName+KEY_SEPARATOR+Value;
	}
	public static String getKeyFromValue(String keyName,Integer Value) {
		return KEY_SEPARATOR+keyName+KEY_SEPARATOR+Value;
	}

	
	
    /**
     * ==================== 键分析  ==================== 
     */
	

	/**
	 * 分析需要清空的外键值集合
	 * @author zhanghaiting
	 * @date 2019年10月14日
	 * @param daoKey
	 * @param foreignKeyArray or conditionKey
	 * @param modelList
	 * @return
	 */
	static Set<String> getFkAllSetForClean(String daoKey,String [] foreignKeyArray,List<Object> modelList){

		Set<String> fkAllSet=new HashSet<>();
		if(null==foreignKeyArray||0==foreignKeyArray.length||null==modelList||0==modelList.size()) {
			return fkAllSet;
		}
		
		for (int j = 0; j < modelList.size(); j++) {
			Object argModel=modelList.get(j);
			for (int i = 0; i < foreignKeyArray.length; i++) {
				String foreignKey=foreignKeyArray[i];
				String valueKey=CacheKeyAssist.getValueKey(argModel, foreignKey);
				if(null==valueKey) {continue;}
				String fkAllKey=CacheKeyAssist.getFkAllKey(daoKey, foreignKey, valueKey);
				fkAllSet.add(fkAllKey);
			}
		}
		
		return fkAllSet;
	}
	
	

	
	/**
	 * 分析需要清除的fk键和值
	 * @author zhanghaiting
	 * @date 2019年10月14日
	 * @param fkNameToCleanList 入参为空，被填充对象
	 * @param fkAllSet 入参为空，被填充对象
	 * @param foreignKeyArray
	 * @param conditionSet
	 * @param argModel
	 * @param daoKey
	 */
	static void analyzeFkCondition(List<String> fkNameToCleanList,Set<String> fkAllSet,
			String [] foreignKeyArray,Set<String> conditionSet,Object argModel,String daoKey) {
		for (int i = 0; i < foreignKeyArray.length; i++) {
			String oneFk=foreignKeyArray[i];
			if(null!=conditionSet && conditionSet.contains(oneFk)) {
				String valueKey=CacheKeyAssist.getValueKey(argModel, oneFk);
				if(null==valueKey) {
					fkNameToCleanList.add(oneFk);
				}else {
					String fkAllKey=CacheKeyAssist.getFkAllKey(daoKey, oneFk, valueKey);
					fkAllSet.add(fkAllKey);
				}
			}else {
				fkNameToCleanList.add(oneFk);
			}
		}
		
	}
	
}
