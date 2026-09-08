package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Agent查询菜品时返回的菜品信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDishVO implements Serializable {

    /**
     * 菜品ID
     */
    private Long id;

    /**
     * 菜品名称
     */
    private String name;

    /**
     * 菜品价格
     */
    private BigDecimal price;

    /**
     * 菜品图片
     */
    private String image;

    /**
     * 菜品描述
     */
    private String description;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 菜品口味信息
     */
    private List<AgentDishFlavorVO> flavors;
}
