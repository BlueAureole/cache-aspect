package wiki.zsjw.article.cache;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import wiki.zsjw.article.utilites.constant.CommonConstant;
import wiki.zsjw.common.cacheaspect.CacheKeyAssist;
import wiki.zsjw.common.cacheaspect.DaoAspect;

/**
* @author zhanghaiting
* @date 2019年10月29日
*/
@Component
@Aspect
public class DaoCacheAspect extends DaoAspect{

    /**
     * ========================================== 切面定义区 ==========================================
     */

    //需要切入的包名和类型(这组定义可能需要重写)
    static final String POINT_PREFIX=CacheKeyAssist.POINT_PREFIX;
    static final String POINT_MIDDLE=CacheKeyAssist.POINT_MIDDLE_A+CommonConstant.PACKAGE_NAME+CacheKeyAssist.POINT_MIDDLE_B;
    static final String POINT_SUFFIX=CacheKeyAssist.POINT_SUFFIX;
    
    //查询
    static final String POINT_SELECT_BY_PRIMARY=POINT_PREFIX+CacheKeyAssist.METHOD_SELECT_PK+POINT_MIDDLE+CacheKeyAssist.METHOD_SELECT_PK+POINT_SUFFIX;
    static final String POINT_SELECT_IN_PK_LIST=POINT_PREFIX+CacheKeyAssist.METHOD_SELECT_BATCH_PK+POINT_MIDDLE+CacheKeyAssist.METHOD_SELECT_BATCH_PK+POINT_SUFFIX;
    static final String POINT_SELECT_BY_FOREIGN=POINT_PREFIX+CacheKeyAssist.METHOD_SELECT_FK+POINT_MIDDLE+CacheKeyAssist.METHOD_SELECT_FK+POINT_SUFFIX;
    static final String POINT_SELECT_BY_UNIQUE=POINT_PREFIX+CacheKeyAssist.METHOD_SELECT_UK+POINT_MIDDLE+CacheKeyAssist.METHOD_SELECT_UK+POINT_SUFFIX;
    
    
    static final String POINT_SELECT_IN_FK_LIST=POINT_PREFIX+CacheKeyAssist.METHOD_SELECT_BATH_FK+POINT_MIDDLE+CacheKeyAssist.METHOD_SELECT_BATH_FK+POINT_SUFFIX;
    static final String POINT_SELECT_PAGE=POINT_PREFIX+CacheKeyAssist.METHOD_SELECT_PAGE+POINT_MIDDLE+CacheKeyAssist.METHOD_SELECT_PAGE+POINT_SUFFIX;
    //添加
    static final String POINT_INSERT_ONE=POINT_PREFIX+CacheKeyAssist.METHOD_INSERT_ONE+POINT_MIDDLE+CacheKeyAssist.METHOD_INSERT_ONE+POINT_SUFFIX;
    static final String POINT_INSERT_BATCH=POINT_PREFIX+CacheKeyAssist.METHOD_INSERT_BATH+POINT_MIDDLE+CacheKeyAssist.METHOD_INSERT_BATH+POINT_SUFFIX;
    //更新
    static final String POINT_UPDATE_BY_PRIMARY=POINT_PREFIX+CacheKeyAssist.METHOD_UPDATE_PK+POINT_MIDDLE+CacheKeyAssist.METHOD_UPDATE_PK+POINT_SUFFIX;
    static final String POINT_UPDATE_BY_FOREIGN=POINT_PREFIX+CacheKeyAssist.METHOD_UPDATE_FK+POINT_MIDDLE+CacheKeyAssist.METHOD_UPDATE_FK+POINT_SUFFIX;
    static final String POINT_UPDATE_BATCH_BY_PK=POINT_PREFIX+CacheKeyAssist.METHOD_UPDATE_BATH_PK+POINT_MIDDLE+CacheKeyAssist.METHOD_UPDATE_BATH_PK+POINT_SUFFIX;
    //删除
    static final String POINT_DELETE_BY_PRIMARY=POINT_PREFIX+CacheKeyAssist.METHOD_DELETE_PK+POINT_MIDDLE+CacheKeyAssist.METHOD_DELETE_PK+POINT_SUFFIX;
    static final String POINT_DELETE_BY_FOREIGN=POINT_PREFIX+CacheKeyAssist.METHOD_DELETE_FK+POINT_MIDDLE+CacheKeyAssist.METHOD_DELETE_FK+POINT_SUFFIX;
    static final String POINT_DELETE_BATCH_BY_PK=POINT_PREFIX+CacheKeyAssist.METHOD_DELETE_BATH_PK+POINT_MIDDLE+CacheKeyAssist.METHOD_DELETE_BATH_PK+POINT_SUFFIX;
	
    /**
     * ========================================== 查询切面  ==========================================
     */
    

	@Pointcut(POINT_SELECT_BY_PRIMARY)
	private void selectByPrimaryPoint() {}
	
	@Pointcut(POINT_SELECT_IN_PK_LIST)
	private void selectInPkListPoint() {}
	
	@Pointcut(POINT_SELECT_BY_FOREIGN)
	private void selectByForeignPoint() {}
	
	@Pointcut(POINT_SELECT_BY_UNIQUE)
	private void selectByUniquePoint() {}
	
	@Pointcut(POINT_SELECT_IN_FK_LIST)
	private void selectInFkListPoint() {}

	@Pointcut(POINT_SELECT_PAGE)
	private void selectPagePoint() {}
	
    /**
     * ========================================== 新增切面  ==========================================
     */

	@Pointcut(POINT_INSERT_ONE)
	private void insertOnePoint() {}

	@Pointcut(POINT_INSERT_BATCH)
	private void insertBatchPoint() {}
    
    /**
     * ========================================== 更新切面  ==========================================
     */
	
	@Pointcut(POINT_UPDATE_BY_PRIMARY)
	private void updateByPrimaryPoint() {}

	@Pointcut(POINT_UPDATE_BATCH_BY_PK)
	private void updateBatchByPkPoint() {}
	
	@Pointcut(POINT_UPDATE_BY_FOREIGN)
	private void updateByForeignPoint() {}
	
    /**
     * ========================================== 删除切面  ==========================================
     */
	
	@Pointcut(POINT_DELETE_BY_PRIMARY)
	private void deleteByPrimaryPoint() {}

	@Pointcut(POINT_DELETE_BATCH_BY_PK)
	private void deleteBatchByPkPoint() {}
	
	@Pointcut(POINT_DELETE_BY_FOREIGN)
	private void deleteByForeignPoint() {}
	
	
}
