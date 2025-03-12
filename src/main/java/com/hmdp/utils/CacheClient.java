package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {// 参数类型 返回值类型
        // 尝试从Redis中查商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 若有 直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null) {
            // 返回错误信息
            return null;
        }
        // 若没有 从数据库中查
        R r = dbFallBack.apply(id);
        // 数据库中没查到 将空值写入Redis 并返回error
        if (r == null) {
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 数据库中查到了 写入Redis
        this.set(key, r, time, unit);
        // 并且返回数据
        return r;
    }
    // 构建线程池来完成重建缓存
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private boolean tryLock(String key) {
        // 获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id ,Class<R> type , Function<ID,R> dbFallback,Long time, TimeUnit unit) {
        // 尝试从Redis中查商铺缓存 这个时候每次都能查到 要校验逻辑时间
        String key=keyPrefix+id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 如果未命中说明压根就不是热点数据 因为热点数据我们已经提前存进去了
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 存在 要判断是否过期
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);

        JSONObject data = (JSONObject) redisData.getData();
        R r= JSONUtil.toBean(data, type);

        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期 直接返回店铺信息
            return r;
        }
        // 已过期 需要开启一个新的线程缓存重建
        // 缓存重建 需要获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 判断是否获取锁成功
            // 若成功 开启独立线程 缓存重建
            // 获取锁成功还需要再次检测Redis缓存是否过期 做double check 如果存在无序重建缓存
            // TODO 解释一下上面的原因
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    // 查数据库
                    R r1 = dbFallback.apply(id);
                    // 写入Redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        // 不管成功与否都要返回旧信息
        return r;
    }

}
