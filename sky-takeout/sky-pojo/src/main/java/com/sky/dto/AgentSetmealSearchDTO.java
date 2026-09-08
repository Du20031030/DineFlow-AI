package com.sky.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentSetmealSearchDTO {

    /**
     * 套餐名称关键词
     */
    private String keyword;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 最低价格
     */
    private BigDecimal minPrice;

    /**
     * 最高价格
     */
    private BigDecimal maxPrice;
}