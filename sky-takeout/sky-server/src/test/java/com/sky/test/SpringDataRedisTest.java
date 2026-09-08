package com.sky.test;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

//@SpringBootTest
public class SpringDataRedisTest {

    @Qualifier("redisTemplate")
    @Autowired
    private RedisTemplate redistemplate;

    @Test
    public void testRedisTemplate(){
        System.out.println(redistemplate);
        ValueOperations valueOperations = redistemplate.opsForValue();
        HashOperations hashOperations = redistemplate.opsForHash();
        ListOperations listOperations = redistemplate.opsForList();
        SetOperations setOperations = redistemplate.opsForSet();
        ZSetOperations zSetOperations = redistemplate.opsForZSet();
    }

//    操作字符串类型的数据
    @Test
    public void testString(){
        redistemplate.opsForValue().set("city","北京");
        String city = (String) redistemplate.opsForValue().get("city");
        System.out.println(city);
        redistemplate.opsForValue().set("code","1234",3, TimeUnit.MINUTES);
        redistemplate.opsForValue().setIfAbsent("lock","1");
        redistemplate.opsForValue().setIfAbsent("lock","2");
    }

//    操作hash类型的数据
    @Test
    public void testHash(){
        HashOperations hashOperations = redistemplate.opsForHash();
        hashOperations.put("100","name","tom");
        hashOperations.put("100","age","20");

        String name = (String)hashOperations.get("100","name");
        System.out.println(name);

        Set keys = hashOperations.keys("100");
        System.out.println(keys);

        List values = hashOperations.values("100");
        System.out.println(values);

        hashOperations.delete("100","name");
    }

//    操作列表类型的数据
    @Test
    public void testList(){
        ListOperations listOperations = redistemplate.opsForList();

        listOperations.leftPushAll("mylist","a","b","c");
        listOperations.leftPush("mylist","d");

        List mylist = listOperations.range("mylist", 0, -1);
        System.out.println(mylist);

        listOperations.rightPop("mylist");
        long size =  listOperations.size("mylist");
        System.out.println(size);
    }


    //操作集合类型的数据
    @Test
    public void testSet(){
        SetOperations setOperations = redistemplate.opsForSet();
        setOperations.add("set1","a","b","c","d");
        setOperations.add("set2","a","b","x","y");

        Set members = setOperations.members("set1");
        System.out.println(members);

        Long size = setOperations.size("set1");
        System.out.println(size);

        Set intersect = setOperations.intersect("set1", "set2");
        System.out.println(intersect);

        Set union = setOperations.union("set1", "set2");
        System.out.println(union);

        setOperations.remove("set1","a","b");
    }

//    操作有序集合
    @Test
    public void testZSet(){
        ZSetOperations zSetOperations = redistemplate.opsForZSet();

        zSetOperations.add("zset1","a",10);
        zSetOperations.add("zset1","b",12);
        zSetOperations.add("zset1","c",9);

        Set zset1 =  zSetOperations.range("zset1",0,-1);
        System.out.println(zset1);

        zSetOperations.incrementScore("zset1","a",10);

        zSetOperations.remove("zset1","a","b");
    }

//    通用命令
    @Test
    public void testCommon(){
        Set keys = redistemplate.keys("*");
        System.out.println(keys);

        Boolean name = redistemplate.hasKey("name");
        boolean age = redistemplate.hasKey("set1");

        for (Object key : keys) {
            DataType type  = redistemplate.type(key);
            System.out.println(type.name());
        }

        redistemplate.delete("set2");
    }

}
