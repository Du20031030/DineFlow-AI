package com.sky.region;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;

@Component
public class JsonRegionResolver implements RegionResolver {

    /**
     * 全国省级行政区划
     */
    private List<RegionNode> regions;

    /**
     * Spring Bean 创建完成后加载行政区划文件
     */
    @PostConstruct
    public void init() {

        try {

            // 1. 读取 resources/region/regions.json
            ClassPathResource resource =
                    new ClassPathResource("region/regions.json");

            // 2. 获取文件输入流
            InputStream inputStream =
                    resource.getInputStream();

            // 3. JSON -> List<RegionNode>
            ObjectMapper objectMapper =
                    new ObjectMapper();

            regions = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<RegionNode>>() {
                    }
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "行政区划数据加载失败",
                    e
            );
        }
    }


    @Override
    public RegionResolveResult resolve(
            String provinceName,
            String cityName,
            String districtName) {

        // 1. 查找省
        RegionNode province =
                findRegion(regions, provinceName);

        if (province == null) {
            throw new RuntimeException(
                    "未找到省级行政区划：" + provinceName
            );
        }


        // 2. 在当前省下面查找市
        RegionNode city =
                findRegion(
                        province.getChildren(),
                        cityName
                );

        if (city == null) {
            throw new RuntimeException(
                    "未找到市级行政区划：" + cityName
            );
        }


        // 3. 在当前市下面查找区县
        RegionNode district =
                findRegion(
                        city.getChildren(),
                        districtName
                );

        if (district == null) {
            throw new RuntimeException(
                    "未找到区县行政区划：" + districtName
            );
        }


        // 4. 返回确定性的真实行政区划结果
        return new RegionResolveResult(
                province.getCode(),
                province.getName(),

                city.getCode(),
                city.getName(),

                district.getCode(),
                district.getName()
        );
    }


    /**
     * 根据名称在指定层级查找行政区
     */
    private RegionNode findRegion(
            List<RegionNode> list,
            String name) {

        if (list == null || name == null) {
            return null;
        }

        for (RegionNode region : list) {

            if (name.equals(region.getName())) {
                return region;
            }
        }

        return null;
    }
}