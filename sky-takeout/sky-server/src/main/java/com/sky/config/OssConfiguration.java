package com.sky.config;


import com.sky.properties.AliOssProperties;
import com.sky.utils.AliOssUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;


//配置类，为AliOssUtil赋值
@Configuration
@Slf4j
public class OssConfiguration {

    @Bean
    @ConditionalOnMissingBean(AliOssUtil.class)
    public AliOssUtil aliOssUtil(AliOssProperties  aliOssProperties) {
        log.info("AliOssUtil初始化完成，endpoint：{}，bucketName：{}",
                aliOssProperties.getEndpoint(),
                aliOssProperties.getBucketName());
        return new AliOssUtil(aliOssProperties.getEndpoint(),
                aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret(),
                aliOssProperties.getBucketName());
    }
}
