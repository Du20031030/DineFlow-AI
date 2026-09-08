package com.sky.aspect;

import com.sky.annotation.Autofill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
public class AutoFillAspect {

    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.Autofill)")
    public void autoFillPointcut() {
    }

    @Before("autoFillPointcut()")
    public void autoFill(JoinPoint joinPoint) throws NoSuchMethodException {
        log.info("开始进行公共字段自动填充...");
        //获取当前被拦截的方法上的数据库操作类型
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        Autofill autofill = method.getAnnotation(Autofill.class);
        OperationType operationType = autofill.value();

        //获取当前被拦截方法的参数对象
        Object[] args = joinPoint.getArgs();
        if (args == null && args.length == 0) {
            return;
        }
        Object entity = args[0];

        //获取时间和ID
        LocalDateTime now = LocalDateTime.now();
        long currentId = BaseContext.getCurrentId();

        if (operationType == OperationType.INSERT) {
            //为四个公共字段赋值
            try {
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME,LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER ,Long.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME,LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER,Long.class);

                setCreateTime.invoke(entity, now);
                setCreateUser.invoke(entity, currentId);
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }else if (operationType == OperationType.UPDATE) {
            //为两个公共字段赋值
            try {
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME,LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER,Long.class);

                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
