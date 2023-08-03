package wiki.zsjw.article.dao.impl;

import org.springframework.stereotype.Repository;

import wiki.zsjw.article.dao.ArticleDao;
import wiki.zsjw.article.model.ArticleModel;
import wiki.zsjw.common.cacheaspect.CacheAbleDao;
import wiki.zsjw.common.cacheaspect.CacheConfigModel;
import wiki.zsjw.common.dbbase.BaseDaoImpl;

/**
 * @author:Aureole
 * @date:2016年8月18日
 */
@Repository
public class ArticleDaoImpl extends BaseDaoImpl<ArticleModel> implements
		ArticleDao,CacheAbleDao<ArticleModel> {

	@Override
	public CacheConfigModel getCacheConfig() {
		if(null==this.cacheConfig) {
	    	CacheConfigModel cacheConfigModel=new CacheConfigModel(cacheObjectKey,genericityClazz);
			this.cacheConfig=cacheConfigModel;
		}
		return (CacheConfigModel) cacheConfig;
	}

}
