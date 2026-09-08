package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.Autofill;
import com.sky.dto.AgentSetmealSearchDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.enumeration.OperationType;
import com.sky.vo.AgentSetmealSummaryVO;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 套餐数据访问层。
 */
@Mapper
public interface SetmealMapper {

    /**
     * 插入套餐主表数据。
     * 插入成功后会把数据库生成的自增主键回填到 setmeal.id。
     */
    @Autofill(value = OperationType.INSERT)
    void insert(Setmeal setmeal);

    /**
     * 管理端套餐分页查询。
     * 关联 category 表查询分类名称，返回 SetmealVO 给分页接口使用。
     */
    Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 根据套餐 id 查询套餐主表数据。
     */
    @Select("select * from setmeal where id = #{id}")
    Setmeal getById(Long id);

    /**
     * 根据套餐 id 删除单条套餐主表数据。
     */
    @Delete("delete from setmeal where id = #{id}")
    void deleteById(Long id);

    /**
     * 根据套餐 id 集合批量删除套餐主表数据。
     */
    void deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 动态更新套餐主表数据。
     * 只更新 Setmeal 对象中非空字段，修改套餐和起售停售都会复用这个方法。
     */
    @Autofill(value = OperationType.UPDATE)
    void update(Setmeal setmeal);

    /**
     * 根据分类 id 查询套餐数量。
     * 删除分类前用来判断当前分类是否关联套餐。
     */
    @Select("select count(id) from setmeal where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 动态条件查询套餐。
     * 用户端按分类 id 和售卖状态查询套餐列表时使用。
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据套餐 id 查询套餐内菜品选项。
     * 用户端查看套餐详情时使用，返回菜品名称、份数、图片和描述。
     */
    @Select("select sd.name, sd.copies, d.image, d.description " +
            "from setmeal_dish sd left join dish d on sd.dish_id = d.id " +
            "where sd.setmeal_id = #{setmealId}")
    List<DishItemVO> getDishItemBySetmealId(Long setmealId);

    /**
     * 根据套餐状态统计数量，工作台套餐总览使用。
     *
     * @param status 套餐状态，1 为启售，0 为停售
     * @return 套餐数量
     */
    Integer countByStatus(@Param("status") Integer status);


//    agent查询套餐
    List<AgentSetmealSummaryVO> searchForAgent(AgentSetmealSearchDTO agentSetmealSearchDTO);
}
