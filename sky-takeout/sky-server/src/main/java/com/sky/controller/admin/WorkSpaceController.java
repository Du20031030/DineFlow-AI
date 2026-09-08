package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.WorkSpaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/workspace")
@Api(tags = "工作台相关接口")
@Slf4j
public class WorkSpaceController {

    @Autowired
    private WorkSpaceService workSpaceService;

    @GetMapping("/businessData")
    @ApiOperation("今日数据查询")
    public Result<BusinessDataVO> businessData() {
        // 查询工作台顶部的今日经营数据：营业额、有效订单、完成率、客单价和新增用户。
        log.info("今日数据查询");
        return Result.success(workSpaceService.getBusinessData());
    }

    @GetMapping("/overviewOrders")
    @ApiOperation("订单管理数据查询")
    public Result<OrderOverViewVO> overviewOrders() {
        // 查询订单管理概览，包含待接单、待派送、已完成、已取消和全部订单数量。
        log.info("订单管理数据查询");
        return Result.success(workSpaceService.getOrderOverView());
    }

    @GetMapping("/overviewDishes")
    @ApiOperation("菜品总览查询")
    public Result<DishOverViewVO> overviewDishes() {
        // 查询菜品启售和停售数量，用于工作台菜品总览。
        log.info("菜品总览查询");
        return Result.success(workSpaceService.getDishOverView());
    }

    @GetMapping("/overviewSetmeals")
    @ApiOperation("套餐总览查询")
    public Result<SetmealOverViewVO> overviewSetmeals() {
        // 查询套餐启售和停售数量，用于工作台套餐总览。
        log.info("套餐总览查询");
        return Result.success(workSpaceService.getSetmealOverView());
    }
}
