package com.sky.controller.admin;


import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

//菜品管理
@RestController
@Api(tags = "菜品管理相关接口")//   @Api注解用于描述整个控制器的功能和用途，tags属性表示接口的分类标签
@Slf4j
@RequestMapping("/admin/dish")
public class DishController {

    @Autowired
    private DishService dishService;

    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO){
        log.info("新增菜品：{}", dishDTO);
        dishService.saveWithFlavor(dishDTO);

//        清理缓存数据
        String key = "dish_" + dishDTO.getCategoryId();
//        redisTemplate.delete(key);
        cleanCahe(key);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
//    这里用query传参
//    @RequestParam注解用于将请求参数绑定到方法的参数上，value属性表示请求参数的名称，required属性表示该参数是否必须提供，默认为true，表示必须提供
//    前端会自动把参数封装进DTO
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
        log.info("菜品分页查询：{}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    //菜品批量删除
    @DeleteMapping
    @ApiOperation("批量删除菜品")
//    采用query传参
    public Result delete(@RequestParam List<Long> ids){
        log.info("删除菜品：{}", ids);
        dishService.deleteBatch(ids);

//        将所有的菜品缓存数据清理
//        Set keys = redisTemplate.keys("dish_*");
//        redisTemplate.delete(keys);
        cleanCahe("dish_*");
        return Result.success();
    }

    //根据id查询菜品信息和口味数据
    @GetMapping("/{id}")
    @ApiOperation("根据id查询菜品信息")
    public Result<DishVO> getById(@PathVariable Long id){
        log.info("根据id查询菜品信息：{}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    //修改菜品基本信息和口味信息
    @PutMapping
    @ApiOperation("修改菜品")
    public Result update(@RequestBody DishDTO dishDTO){
        log.info("修改菜品：{}", dishDTO);
        dishService.updateWithFlavor(dishDTO);

        //        将所有的菜品缓存数据清理
//        Set keys = redisTemplate.keys("dish_*");
//        redisTemplate.delete(keys);
        cleanCahe("dish_*");

        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("菜品起售停售")
//    这里status是路径参数，id是query传参
    public Result<String> startOrStop(@PathVariable("status") Integer status, Long id){

        //        将所有的菜品缓存数据清理
//        Set keys = redisTemplate.keys("dish_*");
//        redisTemplate.delete(keys);
        cleanCahe("dish_*");

        dishService.startOrStop(status,id);
        return Result.success();
    }

    /**
     * 根据分类 id 查询起售中的菜品。
     * 新增套餐选择菜品时，前端会调用这个接口加载某个分类下可选的菜品。
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<Dish>> list(Long categoryId) {
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);

        List<Dish> list = dishService.list(dish);
        return Result.success(list);
    }

    //    定义缓存清理方法
    private void cleanCahe(String pattern){
        Set keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }

}
