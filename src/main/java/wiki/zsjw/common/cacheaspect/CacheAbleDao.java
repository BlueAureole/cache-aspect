package wiki.zsjw.common.cacheaspect;

import java.util.List;

import com.github.pagehelper.PageInfo;

/**
 * 可缓存接口定义
 * @updateBy zhanghaiting
 * @date: 2019年9月20日
 * @param <T>
 */
public interface CacheAbleDao<T>{

	CacheConfigModel getCacheConfig();
	
	//==============查询===============

    public T selectByPrimary(T model);
    public List<T> selectInPkList(List<T> list);

    public T selectByUnique(T model);
    public List<T> selectByForeign(T model);
    public List<T> selectInFkList(List<T> list);
    
	public PageInfo<T> selectPage(PageInfo<T> pageInfo);

	
	
    //==============添加================
	
    public int insertOneWithKey(T model);
    public int insertOneAndGetKey(T model);
    
    public int insertBatchWithKey(List<T> list);
    public int insertBatchAndGetKey(List<T> list);
    public int insertBatchOrGetKey(List<T> list);

    
    
	//==============更新===============

    public int updateByPrimary(T model);
    public int updateByForeign(T model);
    public int updateBatchByPk(List<T> list);

	//==============删除===============
    
    public int deleteByPrimary(T model);
    public int deleteByForeign(T model);
    public int deleteBatchByPk(List<T> list);
    
    
  //==============缓存辅助===============
    
    default String keyForSelectByForeign(T t){
		String allKey="";
//		if(null!=t){
//			if(null!=t.getName()){allKey+="_name_"+t.getName();}
//		}
		
		return allKey;
    }
}
