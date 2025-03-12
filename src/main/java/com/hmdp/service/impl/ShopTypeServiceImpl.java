package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 从Redis中查询
        String json = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);
        // 如果有 直接返回
        if (StrUtil.isNotBlank(json)){
            List<ShopType> typeList = JSONUtil.toList(json, ShopType.class);
            return Result.ok(typeList);
        }
        // 没有 从数据库中查询
        List<ShopType> typeListlist = query().orderByAsc("sort").list();
        if (typeListlist==null){
            return Result.fail("数据库中没有数据");
        }
        // 查询以后要放到Redis中
        json=JSONUtil.toJsonStr(typeListlist);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY,json);
        // 并且把结果返回
        return Result.ok(typeListlist);
    }
}
