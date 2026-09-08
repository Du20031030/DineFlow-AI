package com.sky.controller.user;

import com.sky.result.PageResult;
import com.sky.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private OrderService orderService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController orderController = new OrderController();
        orderService = mock(OrderService.class);
        ReflectionTestUtils.setField(orderController, "orderService", orderService);
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    void historyOrdersReturnsUserOrderPage() throws Exception {
        PageResult pageResult = new PageResult(0, Collections.emptyList());
        when(orderService.pageQuery4User(eq(1), eq(10), eq(5))).thenReturn(pageResult);

        mockMvc.perform(get("/user/order/historyOrders")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("status", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(0));

        verify(orderService).pageQuery4User(1, 10, 5);
    }
}
