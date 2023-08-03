package wiki.zsjw.article.dao.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import wiki.zsjw.article.dao.ContributionDao;
import wiki.zsjw.article.model.ContributionModel;
import wiki.zsjw.common.cacheaspect.CacheAbleDao;
import wiki.zsjw.common.cacheaspect.CacheConfigModel;
import wiki.zsjw.common.cacheaspect.CacheKeyAssist;
import wiki.zsjw.common.dbbase.BaseDaoImpl;

/**
 * @author zhanghaiting
 * @date 2016年8月19日
 */
@Repository
public class ContributionDaoImpl extends BaseDaoImpl<ContributionModel>
		implements ContributionDao,CacheAbleDao<ContributionModel> {

	private final String conceptId="conceptId";
	private final String explorerId="explorerId";

    @Override
	public CacheConfigModel getCacheConfig() {
		if(null==this.cacheConfig) {
	    	CacheConfigModel cacheConfigModel=new CacheConfigModel(cacheObjectKey,genericityClazz);

			cacheConfigModel.setForeignKeyArray(new String[] {conceptId,explorerId});
			cacheConfigModel.putKeysInfo("selectByForeign", cacheConfigModel.new KeysInfo(new String[] {conceptId,explorerId}));
			cacheConfigModel.putKeysInfo("selectInFkList", cacheConfigModel.new KeysInfo(new String[] {conceptId}));
			
			cacheConfigModel.putKeysInfo("selectByForeignContributors", cacheConfigModel.new KeysInfo(new String[] {conceptId,explorerId}));
			cacheConfigModel.putKeysInfo("selectByUniqueGradeVerage", cacheConfigModel.new KeysInfo(new String[] {explorerId}));
			
			this.cacheConfig=cacheConfigModel;
		}
		return (CacheConfigModel) cacheConfig;
	}
    
	
    
    
	@Override
    public List<ContributionModel> selectByForeignContributors(ContributionModel model) {

	    Integer passState=model.getPassState();
	    Integer activeType=model.getActiveType();
		
		String tableFieldName=null;
		
	    if(1==passState){
	        switch (activeType){
	            case 0:
	            	tableFieldName="century_is_all";
	                break;
	            case 1:
	            	tableFieldName="century_is_create";
	                break;
	            case 2:
	            	tableFieldName="century_is_edit";
	                break;
	            case 3:
	            	tableFieldName="century_is_discussion";
	                break;
	            default:
	        }
	    }else if(0==passState){
	        switch (activeType){
	            case 0:
	            	tableFieldName="wait_is_all";
	                break;
	            case 1:
	            	tableFieldName="wait_is_create";
	                break;
	            case 2:
	            	tableFieldName="wait_is_edit";
	                break;
	            case 3:
	            	tableFieldName="wait_is_discussion";
	                break;
	            default:
	        }
	    }else if(2==passState){
	        switch (activeType){
	            case 0:
	            	tableFieldName="refuse_is_all";
	                break;
	            case 1:
	            	tableFieldName="refuse_is_create";
	                break;
	            case 2:
	            	tableFieldName="refuse_is_edit";
	                break;
	            case 3:
	            	tableFieldName="refuse_is_discussion";
	                break;
	            default:
	        }
	    }
		
	    model.setTableFieldName(tableFieldName);
	    
		
        String statementName = interfaceName + ".selectByForeignContributors";
        return sqlSessionTemplate.selectList(statementName, model);
    }
    
	
	
    @Override
	public PageInfo<ContributionModel> selectPageContributors(PageInfo<ContributionModel> pageInfo){
    	
		PageHelper.startPage(pageInfo.getPageNum(), pageInfo.getPageSize());
		List<ContributionModel> listInPage=null;
		if(pageInfo.getList().size()==0){
			listInPage=selectByForeign(null);
		}else{
			listInPage=selectByForeignContributors(pageInfo.getList().get(0));
		}
		PageInfo<ContributionModel> page = new PageInfo<ContributionModel>(listInPage,pageInfo.getNavigatePages()==0?8:pageInfo.getNavigatePages());
		
		return page;
	}



	/**
	 * 查询作者综合评分
	 * 
	 * @param {explorerId}
	 * @return
	 */
    @Override
    public ContributionModel selectByUniqueGradeVerage(ContributionModel t){
        String statementName = interfaceName + ".selectByUniqueGradeVerage";
        ContributionModel r=sqlSessionTemplate.selectOne(statementName, t);
        return r;
    }

    
    @Override
    public int updateByPrimaryIncrease(ContributionModel model){
        String statementName = interfaceName + ".updateByPrimaryIncrease";
        return sqlSessionTemplate.update(statementName, model);
    	
    }

    @Override
    public int updateBatchByPkIncrease(List<ContributionModel> list) {
    	return updateBatchTool(list, BaseDaoImpl.DEFAULT_FLUSH_SIZE,".updateByPrimaryIncrease");
    }



	
    

    
    
    /**
     * ===============  缓存辅助区 ===============
     */

	@Override
	public String keyForSelectByForeign(ContributionModel t){
		String allKey="";
		if(null!=t){
			if(null!=t.getConceptId()){allKey+=CacheKeyAssist.getKeyFromValue(conceptId,t.getConceptId());}
			if(null!=t.getExplorerId()){allKey+=CacheKeyAssist.getKeyFromValue(explorerId,t.getExplorerId());}
			if(null!=t.getContribution()){allKey+=CacheKeyAssist.getKeyFromValue("contribution",t.getContribution());}
		}
		
		return allKey;
    }
    
	public String keyForSelectByForeignContributors(ContributionModel t){
		String allKey="-contributors";
		if(null!=t){
			if(null!=t.getConceptId()){allKey+=CacheKeyAssist.getKeyFromValue(conceptId,t.getConceptId());}
			if(null!=t.getTableFieldName()){allKey+=CacheKeyAssist.getKeyFromValue("tableFieldName",t.getTableFieldName());}
		}
		
		return allKey;
    }
    
	public String keyForSelectByUniqueGradeVerage(ContributionModel t){
		String allKey="-OneGradeVerage";
		if(null!=t){
			if(null!=t.getExplorerId()){allKey+=CacheKeyAssist.getKeyFromValue(explorerId,t.getExplorerId());}
		}
		return allKey;
    }
	
    


}
