package com.sky.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentSetmealSummaryVO {

    /**
     * 套餐ID
     */
    private Long setmealId;

    /**
     * 套餐名称
     */
    private String name;

    /**
     * 套餐价格
     */
    private BigDecimal price;

    /**
     * 套餐包含的菜品
     */
    private List<AgentSetmealDishVO> dishes;
}