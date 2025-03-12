package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdCreater;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private CacheClient cacheClient;
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisIdCreater redisIdCreater;
    @Resource
    private RedissonClient redissonClient;

    private ExecutorService es= Executors.newFixedThreadPool(500);
    @Test
    void testSave() throws InterruptedException {
        Shop shop=shopService.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1L,shop,5L, TimeUnit.SECONDS);
    }

    @Test
    void test() throws InterruptedException {
        CountDownLatch latch=new CountDownLatch(300);
        Runnable task=new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    System.out.println("redisIdCreater.createId(\"order\") = " + redisIdCreater.createId("order"));
                }
                latch.countDown();;
            }
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end=System.currentTimeMillis();
        System.out.println(end-begin);
    }

    @Test
    public void testGO(){
        int a=1;
        changeValue(a);
        System.out.println(a);
    }
    private void changeValue(int x){
        x=10;
    }
}
