package com.sky.service;

import com.sky.dto.AgentDishSearchDTO;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.AgentDishSummaryVO;
import com.sky.vo.AgentDishVO;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {
    //新增菜品
    public void saveWithFlavor(DishDTO dishDTO);

    //菜品分页查询
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    //菜品批量删除
    void deleteBatch(List<Long> ids);


    //根据id 查询菜品信息和口味数据
    DishVO getByIdWithFlavor(Long id);

    //修改菜品基本信息和口味信息
    void updateWithFlavor(DishDTO dishDTO);

    void startOrStop(Integer status, Long id);

    List<Dish> list(Dish dish);

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    List<DishVO> listWithFlavor(Dish dish);

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
}
