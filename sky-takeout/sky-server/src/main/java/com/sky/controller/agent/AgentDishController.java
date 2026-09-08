package com.sky.controller.agent;

import com.sky.dto.AgentDishSearchDTO;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.AgentDishSummaryVO;
import com.sky.vo.AgentDishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 提供给 Python Agent 调用的菜品接口
 */
@RestController
@RequestMapping("/internal/agent/dishes")
@Slf4j
@Api(tags = "Agent端-菜品相关接口")
@RequiredArgsConstructor //这个注解的作用是 自动生成一个构造函数，该构造函数包含所有被声明为 final 的字段作为参数。这样可以确保这些字段在对象创建时被正确初始化，从而实现依赖注入。
//@RequiredArgsConstructor + private final xxx ≈ 自动生成构造器，Spring 再通过构造器完成依赖注入，因此不用写 @Autowired。
public class AgentDishController {

    private final DishService dishService;


    /**
     * Agent 查询菜品
     */
    @PostMapping("/search")
    @ApiOperation("Agent查询菜品")
    public Result<List<AgentDishSummaryVO>> search(@RequestBody AgentDishSearchDTO agentDishSearchDTO) {

        log.info(
                "Agent查询菜品，keyword：{}，categoryId：{}，minPrice：{}，maxPrice：{}，" +
                        "spicyLevel：{}，dislikedIngredients：{}，limit：{}",
                agentDishSearchDTO.getKeyword(),
                agentDishSearchDTO.getCategoryId(),
                agentDishSearchDTO.getMinPrice(),
                agentDishSearchDTO.getMaxPrice(),
                agentDishSearchDTO.getSpicyLevel(),
                agentDishSearchDTO.getDislikedIngredients(),
                agentDishSearchDTO.getLimit()
        );

        // 调用Service查询真实菜品
        List<AgentDishSummaryVO> dishList = dishService.searchForAgent(agentDishSearchDTO);

        return Result.success(dishList);
    }



    /**
     * Agent根据菜品ID查询菜品详情
     */
    @GetMapping("/{dishId}")
    @ApiOperation("Agent根据菜品ID查询菜品详情")
    public Result<AgentDishVO> getDishDetail(@PathVariable Long dishId) {

        log.info("Agent查询菜品详情，dishId：{}", dishId);

        AgentDishVO dish = dishService.getAgentDishById(dishId);

        if (dish == null) {
            return Result.error("菜品不存在或已停售");
        }

        return Result.success(dish);
    }


}