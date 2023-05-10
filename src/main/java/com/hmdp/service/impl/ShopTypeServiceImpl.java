package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

//    使用 redis-string 缓存
//    @Override
//    public List<ShopType> queryTypeList() {
//        /**
//         * 店铺类型缓存
//         * 1. 从redis查询店铺类型缓存
//         * 2. 命中，返回店铺类型信息
//         * 3. 未命中，数据库查询店铺类型信息
//         *  3.1 将店铺类型数据写入redis，返回店铺类型信息
//         */
//        //1. 根据Key值获取Redis中的缓存值
//        String key = CACHE_SHOPTYPE_KEY;
//        String s = stringRedisTemplate.opsForValue().get(key);
//        log.info("redis中获取到的数据是："+s);
//        //2. 判断是否有值，有值则返回
//        if (StrUtil.isNotBlank(s)) {
//            //JSONUtil.toList: 将json数组/json字符串 => 相应的带对象的数组
//            List<ShopType> shopTypes = JSONUtil.toList(s, ShopType.class);
//            return shopTypes;
//        }
//        //3. 没有值则查询数据库
//        List<ShopType> shopTypeList = list();
//        if (null == shopTypeList) {
//            return null;
//        }
//        //4. 将查询到的数据缓存到redis中
//        stringRedisTemplate.opsForValue().set(CACHE_SHOPTYPE_KEY,JSONUtil.toJsonStr(shopTypeList));
//        
//        return shopTypeList;
//    }

    //使用redis-list 缓存
//    @Override
//    public List<ShopType> queryTypeList() {
//        
//        String key = CACHE_SHOPTYPE_KEY;
//        List<String> range = stringRedisTemplate.opsForList().range(key, 0, -1);
//        log.info("redis中获取到的数据是：" + range);
//        //如果redis中有值
//        if (range != null && range.size() > 0) {
//            List<ShopType> shopTypeList = range.stream().map(e -> JSONUtil.toBean(e, ShopType.class)).collect(Collectors.toList());
//            return shopTypeList;
//        }
//        //redis中没值，查询数据库
//        List<ShopType> shopTypes = list();
//        if (null == shopTypes) {
//            return null;
//        }
//        //将查询到的数据缓存到redis中
//        range = shopTypes.stream().map(e -> JSONUtil.toJsonStr(e)).collect(Collectors.toList());
//        
//        stringRedisTemplate.opsForList().rightPushAll(key,range);
//        
//        return shopTypes;
//    }


    //使用redis-hash 缓存
//    @Override
//    public List<ShopType> queryTypeList() {
//        String key = CACHE_SHOPTYPE_KEY;
//        Collection<Object> shopTypes = stringRedisTemplate.opsForHash().entries(key).values();
//        log.info("中Redis中获取到的数据是" + shopTypes);
//        if (!shopTypes.isEmpty()) {
//            List<ShopType> shopTypeList = shopTypes.stream().map(e -> JSONUtil.toBean(e.toString(), ShopType.class)).collect(Collectors.toList());
//            return shopTypeList;
//        }
//        //没有值则查询数据库
//        List<ShopType> list = list();
//        if (null == list) {
//            return null;
//        }
//        //将查询到的数据缓存到redis中
//        list.forEach(
//                shopType -> stringRedisTemplate.opsForHash().put(key,shopType.getId().toString(),JSONUtil.toJsonStr(shopType))
//        );
//        return list;
//    }

    //使用redis-set缓存
//    @Override
//    public List<ShopType> queryTypeList() {
//        String key = CACHE_SHOPTYPE_KEY;
//        Set<String> members = stringRedisTemplate.opsForSet().members(key);
//        log.info("中Redis中获取到的数据是" + members);
//        if (!members.isEmpty()) {
//            List<ShopType> collect = members.stream().map(e -> JSONUtil.toBean(e, ShopType.class)).collect(Collectors.toList());
//            return collect;
//        }
//        //没有值则查询数据库
//        List<ShopType> list = list();
//        if (null == list) {
//            return null;
//        }
//        //将查到的数据缓存到redis中
////        list.forEach(e -> stringRedisTemplate.opsForSet().add(key,JSONUtil.toJsonStr(e)));
//        String[] objects = list.stream().map(e -> JSONUtil.toJsonStr(e)).toArray(String[]::new);
//        stringRedisTemplate.opsForSet().add(key,objects);
//        return list;
//    }

    //使用redis-zset缓存
    @Override
    public List<ShopType> queryTypeList() {
        String key = CACHE_SHOPTYPE_KEY;
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, -1);
        log.info("Redis中获取到的数据是" + range);
        if (!range.isEmpty()) {
            List<ShopType> collect = range.stream().map(e -> JSONUtil.toBean(e, ShopType.class)).collect(Collectors.toList());
            return collect;
        }
        List<ShopType> list = list();
        if (null == list) {
            return null;
        }
        list.forEach(e -> stringRedisTemplate.opsForZSet().add(key, JSONUtil.toJsonStr(e), e.getSort()));

        return list;
    }

}
