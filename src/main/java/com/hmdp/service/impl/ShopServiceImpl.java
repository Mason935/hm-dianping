package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);

        if (null == shop) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    /**
     * 互斥锁解决缓存击穿
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        //1. 从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //2.存在,直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //3 判断命中的是否是空值
        if (shopJson != null) {
            //返回一个错误信息
            return null;
        }

        //4 未命中，实现缓存重构
        //4.1 获取互斥锁(每个商铺对应一个锁)
        String lockKey = CACHE_SHOP_KEY;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2判断是否获取成功
            if (!isLock) {
                //4.3 失败，则休眠重试
                Thread.sleep(50);
                return queryWithMutex(id);  //递归重试
            }
            //4.4 成功，应再次检测redis缓存是否存在，做DoubleCheck,如果存在则无需重建缓存
            String shopJson2 = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson2)) {
                //4.4.1 存在,直接返回
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            //4.4.2 判断命中的是否是空值
            if (shopJson2 != null) {
                //返回一个错误信息
                return null;
            }
            //4.5若缓存中不存在，缓存重构
            //查询数据库
            shop = getById(id);
            //模拟重建的延时 更容易的发生并发冲突...
            Thread.sleep(200);
            //5. 数据库不存在，返回错误
            if (null == shop) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //数据库中存在,写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (Exception e) {
            //这个异常不用管，往外抛
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unLock(lockKey);
        }
        return shop;
    }

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.不存在，直接返回
            return null;
        }
        // 4.命中，需要先把json反序列化为对象..这里需要注意一下
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1.未过期，直接返回店铺信息
            return shop;
        }
        // 5.2.已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock) {
            //6.3获取锁成功
            String json1 = stringRedisTemplate.opsForValue().get(key);
            // 2.判断是否存在（再次查缓存）
            if (StrUtil.isNotBlank(json1)) {
                // 3.存在，
                RedisData redisData1 = JSONUtil.toBean(json1, RedisData.class);
                Shop shop1 = JSONUtil.toBean((JSONObject) redisData1.getData(), Shop.class);
                //判断是否过期
                LocalDateTime expireTime1 = redisData1.getExpireTime();
                if (expireTime1.isAfter(LocalDateTime.now())) {
                    //5.1.未过期，直接返回店铺信息
                    return shop1;
                }
            }
            //不存在 / 或者过期了
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        //返回过期的店铺信息
        return shop;
    }

    /**
     * 缓存预热
     *
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }


    //尝试获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


    /**
     * 缓存穿透代码
     *
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        //1. 从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            //2.命中,直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断空值
        if (shopJson != null) {
            //返回一个错误信息
            return null;
        }
        // 3.不存在，根据id查询数据库
        Shop shop = getById(id);
        //4.数据库也不存在，返回错误
        if (shop == null) {
            //redis缓存空值
            /**
             * 缓存穿透：不存在时写入空值，查询时判断空值
             */
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //数据库存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;
    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);

        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
