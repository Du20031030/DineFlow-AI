package com.sky.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.AgentDishSearchDTO;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.AgentDishFlavorVO;
import com.sky.vo.AgentDishSummaryVO;
import com.sky.vo.AgentDishVO;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 新增菜品，同时保存菜品口味。
     */
    @Override
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.insert(dish);

        Long dishId = dish.getId();
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach(flavor -> flavor.setDishId(dishId));
            dishFlavorMapper.insertBatch(flavors, dishId);
        }
    }

    /**
     * 菜品分页查询。
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除菜品。
     * 起售中的菜品不能删除；被套餐关联的菜品不能删除。
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (dish != null && StatusConstant.ENABLE.equals(dish.getStatus())) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }

        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && !setmealIds.isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        dishMapper.deleteByIds(ids);
        dishFlavorMapper.deleteByDishIds(ids);
    }

    /**
     * 根据 id 查询菜品和口味信息。
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        Dish dish = dishMapper.getById(id);
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);
        return dishVO;
    }

    /**
     * 修改菜品基本信息和口味信息。
     */
    @Override
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);

        dishFlavorMapper.deleteByDishId(dish.getId());

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach(flavor -> flavor.setDishId(dish.getId()));
            dishFlavorMapper.insertBatch(flavors, dish.getId());
        }
    }

    /**
     * 菜品起售停售。
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();

        dishMapper.update(dish);
    }

    /**
     * 条件查询菜品。
     * 管理端新增套餐选择菜品时使用，只查询菜品基本信息。
     */
    @Override
    public List<Dish> list(Dish dish) {
        return dishMapper.list(dish);
    }

    /**
     * 条件查询菜品和口味。
     * 用户端浏览菜品时使用，需要一起返回口味信息。
     */
    @Override
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);
        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d, dishVO);

            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());
            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }

    /**
     * Agent根据条件查询菜品
     *
     * @param agentDishSearchDTO 查询条件
     * @return 菜品列表
     */
    @Override
    public List<AgentDishSummaryVO> searchForAgent(AgentDishSearchDTO agentDishSearchDTO) {

        // Agent菜品搜索默认最多返回10条，最大不超过20条
        Integer limit = agentDishSearchDTO.getLimit();

        if (limit == null) {
            limit = 10;
        } else if (limit < 1) {
            limit = 1;
        } else if (limit > 20) {
            limit = 20;
        }

        agentDishSearchDTO.setLimit(limit);

        // 查询结果仅用于Agent展示，Mapper直接封装为VO
        return dishMapper.searchForAgent(agentDishSearchDTO);
    }

    /**
     * Agent根据菜品ID查询菜品详情
     *
     * @param dishId 菜品ID
     * @return 菜品详情
     */
    @Override
    public AgentDishVO getAgentDishById(Long dishId) {
        AgentDishVO dish = dishMapper.getAgentDishById(dishId);
        if (dish == null) {
            return null;
        }

        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(dishId);
        List<AgentDishFlavorVO> agentDishFlavors = new ArrayList<>();
        if (dishFlavors != null && !dishFlavors.isEmpty()) {
            for (DishFlavor dishFlavor : dishFlavors) {


                List<String> options = new ArrayList<>();


                try {

                    options = objectMapper.readValue(
                            dishFlavor.getValue(),
                            new TypeReference<List<String>>() {}
                    );


                } catch (Exception e) {

                    log.warn(
                            "菜品口味解析失败，dishId:{}, value:{}",
                            dishId,
                            dishFlavor.getValue()
                    );

                }


                AgentDishFlavorVO agentDishFlavor =
                        AgentDishFlavorVO.builder()
                                .name(dishFlavor.getName())
                                .options(options)
                                .build();


                agentDishFlavors.add(agentDishFlavor);
            }
        }
        dish.setFlavors(agentDishFlavors);

        return dish;
    }
}
