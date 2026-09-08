package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Agent查询菜品时传递的查询条件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDishSearchDTO implements Serializable {

    /**
     * 菜品关键词
     * 例如：鸡肉、宫保鸡丁、辣
     */
    private String keyword;

    /**
     * 菜品分类ID
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


    /**
     * 最大返回菜品数量
     */
    private Integer limit;

    /**
     * 用户期望的辣度
     * 例如：中辣
     */
    private String spicyLevel;

    /**
     * 用户不喜欢的食材
     * 例如：["香菜", "葱"]
     *
     * 查询时用于匹配菜品是否支持对应的“不要XX”忌口选项
     */
    private List<String> dislikedIngredients;

}