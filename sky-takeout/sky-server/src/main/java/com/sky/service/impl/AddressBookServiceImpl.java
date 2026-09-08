package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询
     *
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     *
     * @param addressBook
     */
    @Override
    public void save(AddressBook addressBook) {
        Long userId = BaseContext.getCurrentId();
        save(userId, addressBook);
    }

    @Override
    public void save(Long userId, AddressBook addressBook) {
        // 当前用户ID由调用方提供
        addressBook.setUserId(userId);
        // 新增地址默认不是默认地址
        addressBook.setIsDefault(0);
        // 继续复用苍穹外卖原来的 Mapper
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    public AddressBook getById(Long id) {
        AddressBook addressBook = addressBookMapper.getById(id);
        return addressBook;
    }

    /**
     * 根据id修改地址
     *
     * @param addressBook
     */
    public void update(AddressBook addressBook) {
        addressBookMapper.update(addressBook);
    }


    /**
     * 设置默认地址
     *
     * @param addressBook
     */
    @Override
    @Transactional
    public void setDefault(AddressBook addressBook) {

        // 普通用户接口仍然从 ThreadLocal 获取当前用户
        Long userId = BaseContext.getCurrentId();

        setDefault(userId, addressBook.getId());
    }

//    新增一个公共核心方法
    @Override
    @Transactional
    public void setDefault(Long userId, Long addressId) {

        // 1. 校验地址是否属于当前用户
        AddressBook targetAddress =
                addressBookMapper.getById(addressId);

        if (targetAddress == null ||
                !userId.equals(targetAddress.getUserId())) {
            throw new RuntimeException("收货地址不存在或不属于当前用户");
        }

        // 1. 将当前用户的所有地址修改为非默认地址
        AddressBook addressBook = new AddressBook();

        addressBook.setUserId(userId);
        addressBook.setIsDefault(0);

        addressBookMapper.updateIsDefaultByUserId(addressBook);


        // 2. 将指定地址修改为默认地址
        AddressBook defaultAddress = new AddressBook();

        defaultAddress.setId(addressId);
        defaultAddress.setIsDefault(1);

        addressBookMapper.update(defaultAddress);
    }


    /**
     * 根据id删除地址
     *
     * @param id
     */
    public void deleteById(Long id) {
        addressBookMapper.deleteById(id);
    }

}
