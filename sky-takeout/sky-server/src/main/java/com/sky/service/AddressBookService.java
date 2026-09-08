package com.sky.service;

import com.sky.entity.AddressBook;
import java.util.List;

public interface AddressBookService {

    List<AddressBook> list(AddressBook addressBook);

    void save(AddressBook addressBook);

    AddressBook getById(Long id);

    void update(AddressBook addressBook);

    void setDefault(AddressBook addressBook);

    void deleteById(Long id);

    /**
     * agent根据指定用户设置默认地址
     */
    void setDefault(Long userId, Long addressId);

    void save(Long userId, AddressBook addressBook);
}
