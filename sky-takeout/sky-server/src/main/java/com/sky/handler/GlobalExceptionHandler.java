package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    //  捕获SQL异常
    // SQLIntegrityConstraintViolationException是MySQL数据库中用于表示违反完整性约束的异常类。完整性约束是指数据库中数据的有效性和一致性规则，例如唯一性约束、外键约束等。当执行插入、更新或删除操作时，如果违反了这些约束，就会抛出SQLIntegrityConstraintViolationException异常。
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        String message = ex.getMessage();
        if(message.contains("Duplicate entry")){
            String [] split = message.split(" ");
            String username = split[2];
            String msg = username+ MessageConstant.ALREADY_EXISTS;
            return Result.error(msg);
        }else{
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }
    }

}
