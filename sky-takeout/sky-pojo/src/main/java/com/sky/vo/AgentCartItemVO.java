package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Agent查询购物车时返回的购物车商品信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCartItemVO implements Serializable {

    /**
     * 购物车记录ID
     */
    private Long id;

    /**
     * 菜品ID
     */
    private Long dishId;

    /**
     * 套餐ID
     */
    private Long setmealId;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 商品图片
     */
    private String image;

    /**
     * 菜品口味
     */
    private String dishFlavor;

    /**
     * 数量
     */
    private Integer number;

    /**
     * 单价
     */
    private BigDecimal amount;
}