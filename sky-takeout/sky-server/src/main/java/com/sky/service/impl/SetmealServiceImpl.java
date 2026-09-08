package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.AgentSetmealSearchDTO;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.AgentSetmealDishVO;
import com.sky.vo.AgentSetmealSummaryVO;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 套餐业务实现。
 */
@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐。
     * 先保存套餐主表，拿到自增套餐 id，再给每个套餐菜品关系设置 setmealId，最后批量插入关系表。
     */
    @Override
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        setmealMapper.insert(setmeal);

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmeal.getId()));
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 套餐分页查询。
     * 使用 PageHelper 对 Mapper 查询自动分页，返回总记录数和当前页数据。
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除套餐。
     * 删除前校验套餐状态，起售中的套餐不允许删除；校验通过后删除套餐主表和套餐菜品关系表。
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal != null && StatusConstant.ENABLE.equals(setmeal.getStatus())) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }

        setmealMapper.deleteByIds(ids);
        setmealDishMapper.deleteBySetmealIds(ids);
    }

    /**
     * 根据套餐 id 查询套餐详情。
     * 查询套餐主表和套餐菜品关系表，并组装成 SetmealVO 返回。
     */
    @Override
    public SetmealVO getByIdWithDish(Long id) {
        Setmeal setmeal = setmealMapper.getById(id);
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);

        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);

        return setmealVO;
    }

    /**
     * 修改套餐。
     * 先更新套餐主表，再删除旧的套餐菜品关系，最后插入新的套餐菜品关系。
     */
    @Override
    @Transactional
    public void updateWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.update(setmeal);

        setmealDishMapper.deleteBySetmealIds(Collections.singletonList(setmealDTO.getId()));

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealDTO.getId()));
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 套餐起售或停售。
     * 起售时先检查套餐内所有菜品是否都已起售；包含停售菜品时不允许起售。
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        if (StatusConstant.ENABLE.equals(status)) {
            List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);
            for (SetmealDish setmealDish : setmealDishes) {
                Dish dish = dishMapper.getById(setmealDish.getDishId());
                if (dish == null || StatusConstant.DISABLE.equals(dish.getStatus())) {
                    throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                }
            }
        }

        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 用户端条件查询套餐。
     * 通常根据分类 id 和起售状态查询可售套餐。
     */
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        return setmealMapper.list(setmeal);
    }

    /**
     * 用户端根据套餐 id 查询套餐内菜品选项。
     */
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }


    /**
     * Agent查询真实在售套餐
     */
    @Override
    public List<AgentSetmealSummaryVO> searchSetmealsForAgent(AgentSetmealSearchDTO agentSetmealSearchDTO) {
        // 1. 先查询符合条件的在售套餐
        List<AgentSetmealSummaryVO> setmealList =
                setmealMapper.searchForAgent(agentSetmealSearchDTO);

        // 2. 给每个套餐查询关联菜品
        for (AgentSetmealSummaryVO setmeal : setmealList) {

            List<AgentSetmealDishVO> dishes =
                    setmealDishMapper.listForAgentBySetmealId(
                            setmeal.getSetmealId()
                    );

            // 3. 把菜品列表放进套餐VO
            setmeal.setDishes(dishes);
        }

        return setmealList;
    }

}
