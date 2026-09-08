package com.sky.controller.admin;

import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private ReportService reportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReportController reportController = new ReportController();
        reportService = mock(ReportService.class);
        ReflectionTestUtils.setField(reportController, "reportService", reportService);
        mockMvc = MockMvcBuilders.standaloneSetup(reportController).build();
    }

    @Test
    void exposesOfficialReportEndpoints() throws Exception {
        LocalDate begin = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        when(reportService.getTurnoverStatistics(eq(begin), eq(end))).thenReturn(TurnoverReportVO.builder().dateList("2024-01-01,2024-01-02").turnoverList("0,0").build());
        when(reportService.getUserStatistics(eq(begin), eq(end))).thenReturn(UserReportVO.builder().dateList("2024-01-01,2024-01-02").totalUserList("0,0").newUserList("0,0").build());
        when(reportService.getOrderStatistics(eq(begin), eq(end))).thenReturn(OrderReportVO.builder().dateList("2024-01-01,2024-01-02").orderCountList("0,0").validOrderCountList("0,0").build());
        when(reportService.getSalesTop10(eq(begin), eq(end))).thenReturn(SalesTop10ReportVO.builder().nameList("").numberList("").build());

        mockMvc.perform(get("/admin/report/turnoverStatistics").param("begin", "2024-01-01").param("end", "2024-01-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(get("/admin/report/userStatistics").param("begin", "2024-01-01").param("end", "2024-01-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(get("/admin/report/ordersStatistics").param("begin", "2024-01-01").param("end", "2024-01-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(get("/admin/report/top10").param("begin", "2024-01-01").param("end", "2024-01-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        verify(reportService).getTurnoverStatistics(begin, end);
        verify(reportService).getUserStatistics(begin, end);
        verify(reportService).getOrderStatistics(begin, end);
        verify(reportService).getSalesTop10(begin, end);
    }
}
