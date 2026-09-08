package com.sky.mapper;

import com.sky.entity.SetmealDish;
import com.sky.vo.AgentSetmealDishVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 套餐和菜品关系数据访问层。
 */
@Mapper
public interface SetmealDishMapper {

    /**
     * 批量插入套餐和菜品的关联关系。
     */
    void insertBatch(@Param("setmealDishes") List<SetmealDish> setmealDishes);

    /**
     * 根据菜品 id 集合查询关联的套餐 id。
     * 删除菜品前用来判断菜品是否被套餐引用。
     */
    List<Long> getSetmealIdsByDishIds(@Param("ids") List<Long> ids);

    /**
     * 根据套餐 id 集合批量删除套餐和菜品的关联关系。
     * 删除套餐或修改套餐菜品时使用。
     */
    void deleteBySetmealIds(@Param("setmealIds") List<Long> setmealIds);

    /**
     * 根据套餐 id 查询套餐和菜品的关联关系。
     * 套餐详情回显和套餐起售校验时使用。
     */
    @Select("select * from setmeal_dish where setmeal_id = #{setmealId}")
    List<SetmealDish> getBySetmealId(Long setmealId);

    /**
     * agent
     * 根据套餐ID查询套餐关联菜品
     *
     * @param setmealId 套餐ID
     * @return 套餐菜品列表
     */
    List<AgentSetmealDishVO> listForAgentBySetmealId(Long setmealId);
}
