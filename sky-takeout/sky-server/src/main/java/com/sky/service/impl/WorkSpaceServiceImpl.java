package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkSpaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class WorkSpaceServiceImpl implements WorkSpaceService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    @Override
    public BusinessDataVO getBusinessData() {
        // 工作台今日数据固定统计当天 00:00:00 到 23:59:59.999999999 的数据。
        LocalDateTime begin = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 有效订单和营业额只统计已完成订单；订单总数用于计算完成率。
        Double turnover = zeroIfNull(orderMapper.sumByMap(begin, end, Orders.COMPLETED));
        Integer orderCount = zeroIfNull(orderMapper.countByMap(begin, end, null));
        Integer validOrderCount = zeroIfNull(orderMapper.countByMap(begin, end, Orders.COMPLETED));
        Integer newUsers = zeroIfNull(userMapper.countByMap(begin, end));

        Double orderCompletionRate = 0.0;
        if (orderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / orderCount;
        }

        Double unitPrice = 0.0;
        if (validOrderCount != 0) {
            unitPrice = turnover / validOrderCount;
        }

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }

    @Override
    public OrderOverViewVO getOrderOverView() {
        // 工作台订单概览按订单状态统计，不限制时间范围。
        return OrderOverViewVO.builder()
                .waitingOrders(zeroIfNull(orderMapper.countByStatus(Orders.TO_BE_CONFIRMED)))
                .deliveredOrders(zeroIfNull(orderMapper.countByStatus(Orders.CONFIRMED)))
                .completedOrders(zeroIfNull(orderMapper.countByStatus(Orders.COMPLETED)))
                .cancelledOrders(zeroIfNull(orderMapper.countByStatus(Orders.CANCELLED)))
                .allOrders(zeroIfNull(orderMapper.countByStatus(null)))
                .build();
    }

    @Override
    public DishOverViewVO getDishOverView() {
        // 菜品总览按启售/停售状态统计。
        return DishOverViewVO.builder()
                .sold(zeroIfNull(dishMapper.countByStatus(StatusConstant.ENABLE)))
                .discontinued(zeroIfNull(dishMapper.countByStatus(StatusConstant.DISABLE)))
                .build();
    }

    @Override
    public SetmealOverViewVO getSetmealOverView() {
        // 套餐总览按启售/停售状态统计。
        return SetmealOverViewVO.builder()
                .sold(zeroIfNull(setmealMapper.countByStatus(StatusConstant.ENABLE)))
                .discontinued(zeroIfNull(setmealMapper.countByStatus(StatusConstant.DISABLE)))
                .build();
    }

    private Integer zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private Double zeroIfNull(Double value) {
        return value == null ? 0.0 : value;
    }
}
