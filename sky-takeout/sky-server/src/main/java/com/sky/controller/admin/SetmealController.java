package com.sky.controller.admin;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端套餐管理接口。
 */
@RestController
@RequestMapping("/admin/setmeal")
@Api(tags = "套餐管理相关接口")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    /**
     * 新增套餐。
     * 保存套餐基本信息，同时保存套餐和菜品的关联关系。
     */
    @PostMapping
    @ApiOperation("新增套餐")
    @CacheEvict(cacheNames = "setMealCache",key = "#setmealDTO.categoryId")//清理套餐缓存，精确清理
    public Result save(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增套餐：{}", setmealDTO);
        setmealService.saveWithDish(setmealDTO);
        return Result.success();
    }

    /**
     * 套餐分页查询。
     * 根据套餐名称、分类 id、售卖状态查询套餐分页列表。
     */
    @GetMapping("/page")
    @ApiOperation("套餐分页查询")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("套餐分页查询：{}", setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除套餐。
     * 起售中的套餐不能删除，校验通过后删除套餐及其菜品关联关系。
     */
    @DeleteMapping
    @ApiOperation("批量删除套餐")
    @CacheEvict(cacheNames = "setMealCache",allEntries = true)//清理套餐缓存
    public Result delete(@RequestParam List<Long> ids) {
        log.info("批量删除套餐：{}", ids);
        setmealService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 根据 id 查询套餐详情。
     * 用于修改页面回显套餐基本信息和套餐内菜品列表。
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询套餐")
    public Result<SetmealVO> getById(@PathVariable Long id) {
        log.info("根据id查询套餐：{}", id);
        SetmealVO setmealVO = setmealService.getByIdWithDish(id);
        return Result.success(setmealVO);
    }

    /**
     * 修改套餐。
     * 更新套餐基本信息，并重新维护套餐和菜品的关联关系。
     */
    @PutMapping
    @ApiOperation("修改套餐")
    @CacheEvict(cacheNames = "setMealCache",allEntries = true)//清理套餐缓存
    public Result update(@RequestBody SetmealDTO setmealDTO) {
        log.info("修改套餐：{}", setmealDTO);
        setmealService.updateWithDish(setmealDTO);
        return Result.success();
    }

    /**
     * 套餐起售或停售。
     * status 为 1 表示起售，0 表示停售；起售时会校验套餐内菜品是否都已起售。
     */
    @PostMapping("/status/{status}")
    @ApiOperation("套餐起售停售")
//    allEntries的作用是清空缓存中所有的key，避免套餐起售停售后，用户端查询套餐列表时，仍然使用了缓存中的数据，导致数据不一致。
    @CacheEvict(cacheNames = "setMealCache",allEntries = true)//清理套餐缓存
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("套餐起售停售：{},{}", status, id);
        setmealService.startOrStop(status, id);
        return Result.success();
    }
}
