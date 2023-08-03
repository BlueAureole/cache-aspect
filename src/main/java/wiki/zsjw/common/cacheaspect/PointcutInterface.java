package wiki.zsjw.common.cacheaspect;

/**
* @author zhanghaiting
* @date 2021年3月12日
*/

public interface PointcutInterface {

    /**
     * ====================== 查询切面  ================
     */
	
	public void selectByPrimaryPoint();
	
	public void selectInPkListPoint();
	
	public void selectByForeignPoint();
	
	public void selectByUniquePoint();
	
	public void selectInFkListPoint();

	public void selectPagePoint();
	
    /**
     * ======================= 新增切面  ==================
     */

	public void insertOnePoint();

	public void insertBatchPoint();
    
    /**
     * ======================== 更新切面  ===============
     */
	
	public void updateByPrimaryPoint();

	public void updateBatchByPkPoint();
	
	public void updateByForeignPoint();
	
    /**
     * ======================== 删除切面  =================
     */
	
	public void deleteByPrimaryPoint();

	public void deleteBatchByPkPoint();
	
	public void deleteByForeignPoint();

}
