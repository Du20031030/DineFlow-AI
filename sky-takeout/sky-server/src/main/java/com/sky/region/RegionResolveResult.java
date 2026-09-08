package com.sky.region;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegionResolveResult {

    /**
     * 省编码，例如四川省 -> 51
     */
    private String provinceCode;

    /**
     * 省名称
     */
    private String provinceName;

    /**
     * 市编码，例如遂宁市 -> 5109
     */
    private String cityCode;

    /**
     * 市名称
     */
    private String cityName;

    /**
     * 区县编码，例如蓬溪县 -> 510921
     */
    private String districtCode;

    /**
     * 区县名称
     */
    private String districtName;
}