package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkSpaceServiceImplTest {

    private OrderMapper orderMapper;
    private UserMapper userMapper;
    private DishMapper dishMapper;
    private SetmealMapper setmealMapper;
    private WorkSpaceServiceImpl workSpaceService;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        userMapper = mock(UserMapper.class);
        dishMapper = mock(DishMapper.class);
        setmealMapper = mock(SetmealMapper.class);
        workSpaceService = new WorkSpaceServiceImpl();
        ReflectionTestUtils.setField(workSpaceService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(workSpaceService, "userMapper", userMapper);
        ReflectionTestUtils.setField(workSpaceService, "dishMapper", dishMapper);
        ReflectionTestUtils.setField(workSpaceService, "setmealMapper", setmealMapper);
    }

    @Test
    void getBusinessDataCalculatesTodayMetrics() {
        LocalDate today = LocalDate.now();
        LocalDateTime begin = LocalDateTime.of(today, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(today, LocalTime.MAX);

        when(orderMapper.sumByMap(begin, end, Orders.COMPLETED)).thenReturn(200.0);
        when(orderMapper.countByMap(begin, end, null)).thenReturn(5);
        when(orderMapper.countByMap(begin, end, Orders.COMPLETED)).thenReturn(4);
        when(userMapper.countByMap(begin, end)).thenReturn(3);

        BusinessDataVO result = workSpaceService.getBusinessData();

        assertEquals(200.0, result.getTurnover());
        assertEquals(4, result.getValidOrderCount());
        assertEquals(0.8, result.getOrderCompletionRate());
        assertEquals(50.0, result.getUnitPrice());
        assertEquals(3, result.getNewUsers());
    }

    @Test
    void getBusinessDataDefaultsNullMapperResultsToZero() {
        BusinessDataVO result = workSpaceService.getBusinessData();

        assertEquals(0.0, result.getTurnover());
        assertEquals(0, result.getValidOrderCount());
        assertEquals(0.0, result.getOrderCompletionRate());
        assertEquals(0.0, result.getUnitPrice());
        assertEquals(0, result.getNewUsers());
    }

    @Test
    void getOrderOverViewCountsOfficialOrderStatuses() {
        when(orderMapper.countByStatus(Orders.TO_BE_CONFIRMED)).thenReturn(1);
        when(orderMapper.countByStatus(Orders.CONFIRMED)).thenReturn(2);
        when(orderMapper.countByStatus(Orders.COMPLETED)).thenReturn(3);
        when(orderMapper.countByStatus(Orders.CANCELLED)).thenReturn(4);
        when(orderMapper.countByStatus(null)).thenReturn(10);

        OrderOverViewVO result = workSpaceService.getOrderOverView();

        assertEquals(1, result.getWaitingOrders());
        assertEquals(2, result.getDeliveredOrders());
        assertEquals(3, result.getCompletedOrders());
        assertEquals(4, result.getCancelledOrders());
        assertEquals(10, result.getAllOrders());
    }

    @Test
    void getDishOverViewCountsEnabledAndDisabledDishes() {
        when(dishMapper.countByStatus(StatusConstant.ENABLE)).thenReturn(7);
        when(dishMapper.countByStatus(StatusConstant.DISABLE)).thenReturn(2);

        DishOverViewVO result = workSpaceService.getDishOverView();

        assertEquals(7, result.getSold());
        assertEquals(2, result.getDiscontinued());
    }

    @Test
    void getSetmealOverViewCountsEnabledAndDisabledSetmeals() {
        when(setmealMapper.countByStatus(StatusConstant.ENABLE)).thenReturn(5);
        when(setmealMapper.countByStatus(StatusConstant.DISABLE)).thenReturn(1);

        SetmealOverViewVO result = workSpaceService.getSetmealOverView();

        assertEquals(5, result.getSold());
        assertEquals(1, result.getDiscontinued());
    }

    @Test
    void getBusinessDataQueriesOnlyToday() {
        workSpaceService.getBusinessData();

        ArgumentCaptor<LocalDateTime> beginCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderMapper).countByMap(beginCaptor.capture(), endCaptor.capture(), org.mockito.ArgumentMatchers.isNull());

        LocalDate today = LocalDate.now();
        assertEquals(LocalDateTime.of(today, LocalTime.MIN), beginCaptor.getValue());
        assertEquals(LocalDateTime.of(today, LocalTime.MAX), endCaptor.getValue());
    }
}
