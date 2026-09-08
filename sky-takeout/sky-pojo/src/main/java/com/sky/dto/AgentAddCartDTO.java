package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Agent添加商品到购物车
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAddCartDTO implements Serializable {

    /**
     * 菜品ID
     */
    private Long dishId;

    /**
     * 套餐ID
     */
    private Long setmealId;

    /**
     * 菜品口味
     */
    private String dishFlavor;
}