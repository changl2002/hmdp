package com.hmdp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.impl.ShopTypeServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;

@SpringBootTest
public class HutoolTest {
    @Resource
    private IShopTypeService shopTypeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    public void test(){
        List<ShopType> typeListlist = shopTypeService.query().orderByAsc("sort").list();
        System.out.println(typeListlist);
        String json = JSONUtil.toJsonStr(typeListlist);
        stringRedisTemplate.opsForValue().set("key1",json);

        String jsonStr = stringRedisTemplate.opsForValue().get("key1");
        List<ShopType> bean2 = JSONUtil.toList(jsonStr, ShopType.class);
        System.out.println(CollUtil.isEqualList(typeListlist,bean2));
    }
}
