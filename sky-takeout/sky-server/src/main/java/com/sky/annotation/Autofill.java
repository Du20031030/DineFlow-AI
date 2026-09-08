package com.sky.annotation;


import com.sky.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)//表明这个注解只能用于方法
@Retention(RetentionPolicy.RUNTIME)//表示这个注解会一直保留到程序运行阶段
public @interface Autofill {
    //数据库操作类型:update,insert
    OperationType value();
}
