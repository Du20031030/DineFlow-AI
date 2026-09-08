package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.Autofill;
import com.sky.dto.AgentDishSearchDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.AgentDishSummaryVO;
import com.sky.vo.AgentDishVO;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);


    //新增菜品
    @Autofill(value = OperationType.INSERT)
    void insert(Dish dish);

    //菜品分页查询
    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    //根据id查询菜品信息
    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);

    //根据id删除菜品
    @Delete("delete from dish where id = #{id}")
    void deleteById(Long id);

    //根据菜品id集合批量删除菜品数据
    void deleteByIds(List<Long> ids);

    //修改菜品的基本信息
    @Autofill(value = OperationType.UPDATE)
    void update(Dish dish);

//    动态条件查询菜品的信息
    List<Dish> list(Dish dish);

    /**
     * Agent根据条件查询菜品
     *
     * @param agentDishSearchDTO 查询条件
     * @return 菜品列表
     */
    List<AgentDishSummaryVO> searchForAgent(AgentDishSearchDTO agentDishSearchDTO);

    /**
     * Agent根据菜品ID查询菜品详情
     *
     * @param dishId 菜品ID
     * @return 菜品详情
     */
    AgentDishVO getAgentDishById(Long dishId);

    /**
     * 根据菜品状态统计数量，工作台菜品总览使用。
     *
     * @param status 菜品状态，1 为启售，0 为停售
     * @return 菜品数量
     */
    Integer countByStatus(@Param("status") Integer status);
}
