package com.sky.dto;

import lombok.Data;

@Data
public class AgentAddAddressDTO {

    /**
     * 收货人
     */
    private String consignee;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 性别
     */
    private String sex;

    /**
     * 省名称
     */
    private String provinceName;

    /**
     * 市名称
     */
    private String cityName;

    /**
     * 区县名称
     */
    private String districtName;

    /**
     * 详细地址
     */
    private String detail;

    /**
     * 地址标签
     */
    private String label;
}