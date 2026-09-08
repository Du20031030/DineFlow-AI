package com.sky.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentSetmealDishVO {

    /**
     * 菜品ID
     */
    private Long dishId;

    /**
     * 菜品名称
     */
    private String name;

    /**
     * 菜品价格
     */
    private BigDecimal price;

    /**
     * 套餐中该菜品的份数
     */
    private Integer copies;
}