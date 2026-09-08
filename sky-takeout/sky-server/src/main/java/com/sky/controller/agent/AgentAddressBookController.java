package com.sky.controller.agent;

import com.sky.dto.AgentAddAddressDTO;
import com.sky.entity.AddressBook;
import com.sky.region.RegionResolveResult;
import com.sky.region.RegionResolver;
import com.sky.result.Result;
import com.sky.service.AddressBookService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/agent/address-book")
@Slf4j
@Api(tags = "Agent端-收货地址相关接口")
public class AgentAddressBookController {

    @Autowired
    private AddressBookService addressBookService;

    @Autowired
    private RegionResolver regionResolver;

    /**
     * Agent查询当前用户默认地址
     */
    @GetMapping("/{userId}/default")
    public Result<AddressBook> getDefaultAddress(@PathVariable Long userId) {

        // 1. 构造查询条件
        AddressBook addressBook = new AddressBook();
        addressBook.setUserId(userId);
        addressBook.setIsDefault(1);

        // 2. 直接复用苍穹外卖原来的地址查询业务
        List<AddressBook> list = addressBookService.list(addressBook);

        // 3. 当前用户没有默认地址
        if (list == null || list.isEmpty()) {
            return Result.success(null);
        }

        // 4. 正常情况下默认地址只有一个
        return Result.success(list.get(0));
    }


//    获取所有地址
    @GetMapping("/{userId}")
    public Result<List<AddressBook>> getMyAddresses(@PathVariable Long userId) {
        AddressBook addressBook = new AddressBook();
        addressBook.setUserId(userId);
        List<AddressBook> list =
                addressBookService.list(addressBook);
        return Result.success(list);
    }

    /**
     * Agent设置默认收货地址
     */
    @PutMapping("/{userId}/default/{addressId}")
    public Result setDefaultAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        addressBookService.setDefault(userId, addressId);
        return Result.success();
    }


    /**
     * Agent新增收货地址
     */
    @ApiOperation("Agent新增收货地址")
    @PostMapping("/{userId}")
    public Result addAddress(@PathVariable Long userId, @RequestBody AgentAddAddressDTO dto) {

        // 1. 根据省、市、区名称解析真实行政区划编码
        RegionResolveResult region = regionResolver.resolve(
                dto.getProvinceName(),
                dto.getCityName(),
                dto.getDistrictName()
        );

        // 2. 构造原苍穹外卖的 AddressBook 实体
        AddressBook addressBook = new AddressBook();

        addressBook.setConsignee(dto.getConsignee());
        addressBook.setPhone(dto.getPhone());
        addressBook.setSex(dto.getSex());

        // 3. 使用后端解析得到的真实行政区划信息
        addressBook.setProvinceCode(region.getProvinceCode());
        addressBook.setProvinceName(region.getProvinceName());

        addressBook.setCityCode(region.getCityCode());
        addressBook.setCityName(region.getCityName());

        addressBook.setDistrictCode(region.getDistrictCode());
        addressBook.setDistrictName(region.getDistrictName());

        addressBook.setDetail(dto.getDetail());
        addressBook.setLabel(dto.getLabel());

        // 4. 复用原苍穹外卖新增地址业务
        addressBookService.save(userId, addressBook);

        return Result.success();
    }


    /**
     * Agent修改收获地址
     */
    @PutMapping("/{userId}/{addressId}")
    public Result updateAddress(@PathVariable Long userId,
                                @PathVariable Long addressId,
                                @RequestBody AgentAddAddressDTO dto) {
        // 1. 先查询地址，确认确实属于当前用户
        AddressBook oldAddress = addressBookService.getById(addressId);

        if (oldAddress == null || !userId.equals(oldAddress.getUserId())) {
            throw new RuntimeException("地址不存在或无权修改");
        }

        RegionResolveResult region = regionResolver.resolve(
                dto.getProvinceName(),
                dto.getCityName(),
                dto.getDistrictName()
        );

        AddressBook addressBook = new AddressBook();

        addressBook.setId(addressId);
        addressBook.setUserId(userId);

        addressBook.setConsignee(dto.getConsignee());
        addressBook.setPhone(dto.getPhone());

    // 没有传 sex，就保留原来的
        addressBook.setSex(
                dto.getSex() != null
                        ? dto.getSex()
                        : oldAddress.getSex()
        );

        addressBook.setProvinceCode(region.getProvinceCode());
        addressBook.setProvinceName(region.getProvinceName());

        addressBook.setCityCode(region.getCityCode());
        addressBook.setCityName(region.getCityName());

        addressBook.setDistrictCode(region.getDistrictCode());
        addressBook.setDistrictName(region.getDistrictName());

        addressBook.setDetail(dto.getDetail());

    // 没有传 label，就保留原来的
        addressBook.setLabel(
                dto.getLabel() != null
                        ? dto.getLabel()
                        : oldAddress.getLabel()
        );

        addressBookService.update(addressBook);

        return Result.success();
    }


//    删除地址
    @DeleteMapping("/{userId}/{addressId}")
    public Result deleteAddress(@PathVariable Long userId,
                                @PathVariable Long addressId) {

        // 1. 查询地址
        AddressBook addressBook = addressBookService.getById(addressId);

        // 2. 校验这条地址是否属于当前用户
        if (addressBook == null || !userId.equals(addressBook.getUserId())) {
            throw new RuntimeException("地址不存在或无权删除");
        }

        // 3. 复用苍穹外卖原有删除逻辑
        addressBookService.deleteById(addressId);

        return Result.success();
    }
}