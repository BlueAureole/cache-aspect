# Cache-aspect
## 简介：
`Cache-aspect`意为缓存切面，定位于`Mybatis`的一级，二级缓存之后的三级缓存。

`Mybatis`二级缓存：只要执行`insert`,`update`,`delete`语句就会清空该`Mapper`的所有缓存。

`Cache-aspect`缓存：如果`insert`,`update`,`delete`语句中有主键或者外键条件，则只需清空该`Mapper`中部分缓存，亦可保证数据一致性。这样就可以提高缓存命中率。

## 原理：

类似于`HashMap`与`ConcurrentHashMap`，为了保证并发访问安全，`HashMap`访问前需要对整个对象加锁。而`ConcurrentHashMap`则是分成了多个`Segment` ，只需对单个需要操作的`Segment`上加锁。实际上是对`HashMap`里的数据做了更细致的分区管理。

`Cache-aspect`受`ConcurrentHashMap`启发，亦对表内数据按主键或者外键做了更细致的分区管理。当更新语句执行时，只清空受影响的缓存数据。

## 关键实现：
1   `CacheAbleDao`：定义数据操作接口名称规范，以区分数据按主键或者外键操作。

2   `DaoAspect`：通过切面编程，对`Dao`层的操作做缓存的添加，命中，清除等操作。

3   `CacheConfigModel`：缓存配置模型，告知表的所有可作分区用的外键。

4    具体什么情况会清空哪些缓存参阅 [详细设计文档](./doc/缓存切面设计V2.jpg)
## 使用：
1    配置所使用的缓存名称，基于`Redis`。
```
     //文件DaoAspect.java，指定缓存名称
     @Resource(name = "defaultRedisTemplate")
     private RedisTemplate<String, Object> redisTemplate;
```
2    配置全局切面名称
```
	/**
	 * 文件CacheKeyAssist.java，指定全局切面包的名称
	 * (execution(public * wiki.zsjw.common.dbbase.Base*Impl.selectByForeign*(..)) 
	 * 		|| execution(public * wiki.zsjw.article.dao..*.selectByForeign*(..))) 
	 * && target(wiki.zsjw.common.cacheaspect.CacheAbleDao)"
	 */
    public static final String POINT_PREFIX="(execution(public * wiki.zsjw.common.dbbase.Base*Impl.";//扩展实现时，注意此处的包和命名规范
    public static final String POINT_MIDDLE_A="*(..)) || execution(public * ";
    public static final String POINT_MIDDLE_B=".dao..*.";
    public static final String POINT_SUFFIX="*(..))) && target(wiki.zsjw.common.cacheaspect.CacheAbleDao)";

```
3    通过注解启用切面
```
    //文件DaoCacheAspect.java, 通过注解启用切面
    @Component
    @Aspect
    public class DaoCacheAspect extends DaoAspect{
```

4    对表配置指定外键列表

```
    示例1，只有主键的表配置
    @Repository
    //DaoImpl需要实现CacheAbleDao接口。
    public class ArticleDaoImpl extends BaseDaoImpl<ArticleModel> implements
    		ArticleDao,CacheAbleDao<ArticleModel> {
    
    	@Override
	//只有主键的表，使用默认配置即可（无需指定外键）
    	public CacheConfigModel getCacheConfig() {
    		if(null==this.cacheConfig) {
    	    	CacheConfigModel cacheConfigModel=new CacheConfigModel(cacheObjectKey,genericityClazz);
    			this.cacheConfig=cacheConfigModel;
    		}
    		return (CacheConfigModel) cacheConfig;
    	}
    
    }
```

```
    示例2，有外键的表
    @Repository
    //DaoImpl需要实现CacheAbleDao接口。
    public class ContributionDaoImpl extends BaseDaoImpl<ContributionModel>
    		implements ContributionDao,CacheAbleDao<ContributionModel> {
       //有外键的表，可以指定外键
    	private final String conceptId="conceptId";//此为外键1
    	private final String explorerId="explorerId";//此为外键2
    
        @Override
    	public CacheConfigModel getCacheConfig() {
    		if(null==this.cacheConfig) {
    	    	CacheConfigModel cacheConfigModel=new CacheConfigModel(cacheObjectKey,genericityClazz);
			//对方法指定可能使用到的外键（实际外键允许为空，为空则清空全部缓存）
    			cacheConfigModel.setForeignKeyArray(new String[] {conceptId,explorerId});
    			cacheConfigModel.putKeysInfo("selectByForeign", cacheConfigModel.new KeysInfo(new String[] {conceptId,explorerId}));//该查询使用了外键1和2
    			cacheConfigModel.putKeysInfo("selectInFkList", cacheConfigModel.new KeysInfo(new String[] {conceptId}));//该查询使用了外键1
    			
    			cacheConfigModel.putKeysInfo("selectByForeignContributors", cacheConfigModel.new KeysInfo(new String[] {conceptId,explorerId}));//该查询使用了外键1和2
    			cacheConfigModel.putKeysInfo("selectByUniqueGradeVerage", cacheConfigModel.new KeysInfo(new String[] {explorerId}));//该查询使用了外键2
    			
    			this.cacheConfig=cacheConfigModel;
    		}
    		return (CacheConfigModel) cacheConfig;
    	}
    
    }
```
