package com.sky.controller.admin;

import com.sky.service.WorkSpaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkSpaceControllerTest {

    private WorkSpaceService workSpaceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WorkSpaceController workSpaceController = new WorkSpaceController();
        workSpaceService = mock(WorkSpaceService.class);
        ReflectionTestUtils.setField(workSpaceController, "workSpaceService", workSpaceService);
        mockMvc = MockMvcBuilders.standaloneSetup(workSpaceController).build();
    }

    @Test
    void exposesWorkspaceEndpoints() throws Exception {
        when(workSpaceService.getBusinessData()).thenReturn(BusinessDataVO.builder().turnover(100.0).build());
        when(workSpaceService.getOrderOverView()).thenReturn(OrderOverViewVO.builder().allOrders(10).build());
        when(workSpaceService.getDishOverView()).thenReturn(DishOverViewVO.builder().sold(8).discontinued(2).build());
        when(workSpaceService.getSetmealOverView()).thenReturn(SetmealOverViewVO.builder().sold(6).discontinued(1).build());

        mockMvc.perform(get("/admin/workspace/businessData"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.turnover").value(100.0));
        mockMvc.perform(get("/admin/workspace/overviewOrders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.allOrders").value(10));
        mockMvc.perform(get("/admin/workspace/overviewDishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.sold").value(8));
        mockMvc.perform(get("/admin/workspace/overviewSetmeals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.sold").value(6));

        verify(workSpaceService).getBusinessData();
        verify(workSpaceService).getOrderOverView();
        verify(workSpaceService).getDishOverView();
        verify(workSpaceService).getSetmealOverView();
    }
}
