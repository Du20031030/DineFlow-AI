package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderDetailMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceimplTest {

    private OrderDetailMapper orderDetailMapper;
    private ReportServiceimpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceimpl();
        orderDetailMapper = mock(OrderDetailMapper.class);
        ReflectionTestUtils.setField(reportService, "orderDetailMapper", orderDetailMapper);
    }

    @Test
    void getSalesTop10UsesCompletedOrderStatusConstant() {
        LocalDate begin = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        when(orderDetailMapper.getSalesTop10(beginTime, endTime, Orders.COMPLETED))
                .thenReturn(Collections.singletonList(GoodsSalesDTO.builder().name("宫保鸡丁").number(3).build()));

        reportService.getSalesTop10(begin, end);

        verify(orderDetailMapper).getSalesTop10(beginTime, endTime, Orders.COMPLETED);
    }
}
