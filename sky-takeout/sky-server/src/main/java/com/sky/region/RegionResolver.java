package com.sky.region;

public interface RegionResolver {

    /**
     * 根据省、市、区县名称解析真实行政区划编码
     *
     * @param provinceName 省名称，例如：陕西省
     * @param cityName     市名称，例如：西安市
     * @param districtName 区县名称，例如：长安区
     * @return 行政区划解析结果
     */
    RegionResolveResult resolve(
            String provinceName,
            String cityName,
            String districtName
    );
}