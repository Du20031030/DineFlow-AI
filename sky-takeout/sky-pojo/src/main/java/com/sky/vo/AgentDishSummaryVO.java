package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Agent搜索菜品时返回的菜品摘要信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDishSummaryVO implements Serializable {

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
}