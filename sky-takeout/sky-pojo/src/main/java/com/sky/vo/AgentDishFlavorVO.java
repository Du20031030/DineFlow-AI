package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Agent查询菜品详情时返回的菜品口味信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDishFlavorVO implements Serializable {

    /**
     * 口味类型，例如：辣度、甜度
     */
    private String name;

    /**
     * 具体口味，例如：微辣、中辣、重辣
     */
    private List<String> options;

}
