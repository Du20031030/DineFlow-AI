package com.sky.controller.admin;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Api(tags = "员工管理相关接口")//   @Api注解用于描述整个控制器的功能和用途，tags属性表示接口的分类标签
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation(value = "员工登录", notes = "员工登录接口")//   @ApiOperation注解用于描述接口的功能和用途，value属性表示接口的名称，notes属性表示接口的详细说明
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录，username：{}", employeeLoginDTO.getUsername());

        Employee employee = employeeService.login(employeeLoginDTO);

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        //将Employee对象转换为EmployeeLoginVO对象，并将token设置到EmployeeLoginVO对象中
        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @PostMapping("/logout")
    @ApiOperation(value = "员工退出", notes = "员工退出接口")
    public Result<String> logout() {
        return Result.success();
    }


//    新增员工
    @PostMapping()
    @ApiOperation(value = "新增员工", notes = "新增员工接口")
    public Result save(@RequestBody EmployeeDTO employeeDTO) {
        log.info("新增员工：{}", employeeDTO);
        employeeService.save(employeeDTO);
        return Result.success();
    }


//    分页查询
    @GetMapping("/page")
    @ApiOperation(value = "分页查询员工", notes = "分页查询员工接口")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO) {
        PageResult pagersult = employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pagersult);
    }

    //启用或者禁用员工
    //路径参数就是  /status/{status}，{status}就是路径参数，表示员工的状态，1表示启用，0表示禁用；地址栏传参（query)就是/status?id=1,问好后面的就是，这种的注解用@requestParam，一般可以省略
    @PostMapping("/status/{status}")
    @ApiOperation(value = "启用或禁用员工", notes = "启用或禁用员工接口")
    public Result startOrStop(@PathVariable Integer status,Long id){
        log.info("启用或禁用员工：{},{}", status, id);
        employeeService.startOrStop(status, id);
        return Result.success();
    }


    //根据id查询员工信息
    @GetMapping("/{id}")
    @ApiOperation(value = "根据id查询员工信息", notes = "根据id查询员工信息接口")
    public Result<Employee> getById(@PathVariable Long id) {
        log.info("根据id查询员工信息：{}", id);
        Employee employee = employeeService.getById(id);
        return Result.success(employee);
    }


    //修改员工信息
    @PutMapping()
    @ApiOperation(value = "修改员工信息", notes = "修改员工信息接口")
    public Result update(@RequestBody EmployeeDTO  employeeDTO) {
        log.info("编辑员工信息：{}", employeeDTO);
        employeeService.update(employeeDTO);
        return Result.success();
    }
}
