package com.sky.region;

import lombok.Data;

import java.util.List;

@Data
public class RegionNode {

    /**
     * 行政区划编码
     */
    private String code;

    /**
     * 行政区划名称
     */
    private String name;

    /**
     * 下级行政区划
     */
    private List<RegionNode> children;

}