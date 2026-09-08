package com.sky.service;

import com.sky.dto.AgentSetmealSearchDTO;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.vo.AgentSetmealSummaryVO;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;

import java.util.List;

/**
 * 套餐业务接口。
 */
public interface SetmealService {

    /**
     * 新增套餐，并保存套餐和菜品的关联关系。
     *
     * @param setmealDTO 套餐基本信息和套餐内菜品列表
     */
    void saveWithDish(SetmealDTO setmealDTO);

    /**
     * 管理端套餐分页查询。
     *
     * @param setmealPageQueryDTO 分页参数和查询条件
     * @return 分页结果
     */
    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 批量删除套餐。
     *
     * @param ids 套餐 id 集合
     */
    void deleteBatch(List<Long> ids);

    /**
     * 根据 id 查询套餐详情。
     *
     * @param id 套餐 id
     * @return 套餐基本信息和套餐内菜品列表
     */
    SetmealVO getByIdWithDish(Long id);

    /**
     * 修改套餐，并更新套餐和菜品的关联关系。
     *
     * @param setmealDTO 套餐基本信息和套餐内菜品列表
     */
    void updateWithDish(SetmealDTO setmealDTO);

    /**
     * 套餐起售或停售。
     *
     * @param status 售卖状态，1 为起售，0 为停售
     * @param id 套餐 id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 用户端条件查询套餐。
     *
     * @param setmeal 查询条件，通常包含分类 id 和售卖状态
     * @return 套餐列表
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据套餐 id 查询套餐内菜品选项。
     *
     * @param id 套餐 id
     * @return 套餐内菜品列表
     */
    List<DishItemVO> getDishItemById(Long id);


    /**
     * Agent查询真实在售套餐
     */
    List<AgentSetmealSummaryVO> searchSetmealsForAgent(AgentSetmealSearchDTO agentSetmealSearchDTO);
}
